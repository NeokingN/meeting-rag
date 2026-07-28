package com.meeting.rag.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档切分器 - 将长文本切为适合Embedding的片段
 *
 * 策略：滑动窗口 + 按句子边界切分
 * - chunkSize: 每块目标长度（字符）
 * - overlap: 相邻块重叠长度（避免切断关键语义）
 */
@Slf4j
@Component
public class DocumentChunker {

    @Value("${meeting.rag.chunk.size:500}")
    private int chunkSize;

    @Value("${meeting.rag.chunk.overlap:80}")
    private int overlap;

    /**
     * 切分文档为多个Chunk
     */
    public List<TextChunk> chunk(String documentId, String content) {
        List<TextChunk> chunks = new ArrayList<>();

        // 先按段落/句子粗分
        String[] paragraphs = content.split("\n+");

        StringBuilder buffer = new StringBuilder();
        int chunkIndex = 0;
        int totalLength = 0;

        for (String para : paragraphs) {
            para = para.trim();
            if (para.isEmpty()) continue;

            // 如果单段就超长，硬切
            if (para.length() > chunkSize) {
                // 先把buffer flush
                if (buffer.length() > 0) {
                    chunks.add(new TextChunk(documentId, chunkIndex++, buffer.toString().trim(), totalLength));
                    totalLength += buffer.length();
                    buffer = new StringBuilder();
                }
                // 硬切长段
                chunks.addAll(hardSplit(documentId, para, chunkIndex, totalLength));
                chunkIndex = chunks.size();
                totalLength += para.length();
                continue;
            }

            buffer.append(para).append("\n");

            if (buffer.length() >= chunkSize) {
                String chunkText = buffer.toString().trim();
                chunks.add(new TextChunk(documentId, chunkIndex++, chunkText, totalLength));
                totalLength += chunkText.length();

                // 保留overlap部分
                String tail = getTail(buffer.toString(), overlap);
                buffer = new StringBuilder(tail);
            }
        }

        // flush剩余
        if (buffer.length() > 50) { // 太短的不算
            chunks.add(new TextChunk(documentId, chunkIndex, buffer.toString().trim(), totalLength));
        }

        log.info("文档 {} 切分为 {} 个Chunk (chunkSize={}, overlap={})", documentId, chunks.size(), chunkSize, overlap);
        return chunks;
    }

    /**
     * 硬切超长段落 - 按字符数切
     */
    private List<TextChunk> hardSplit(String docId, String text, int startIdx, int offset) {
        List<TextChunk> result = new ArrayList<>();
        int idx = startIdx;
        int pos = 0;
        while (pos < text.length()) {
            int end = Math.min(pos + chunkSize, text.length());
            // 尽量在句号/问号处断开
            while (end < text.length() && !isSentenceEnd(text.charAt(end))) {
                end++;
            }
            if (end == pos + chunkSize) end = pos + chunkSize; // 没找到就硬切
            String piece = text.substring(pos, Math.min(end + 1, text.length())).trim();
            if (piece.length() > 50) {
                result.add(new TextChunk(docId, idx++, piece, offset + pos));
            }
            pos = end + 1;
        }
        return result;
    }

    private boolean isSentenceEnd(char c) {
        return c == '。' || c == '.' || c == '！' || c == '？' || c == '!' || c == '?';
    }

    private String getTail(String text, int len) {
        if (text.length() <= len) return text;
        return text.substring(text.length() - len);
    }

    /**
     * Chunk数据载体
     */
    public static class TextChunk {
        private final String documentId;
        private final int index;
        private final String content;
        private final int charOffset;

        public TextChunk(String documentId, int index, String content, int charOffset) {
            this.documentId = documentId;
            this.index = index;
            this.content = content;
            this.charOffset = charOffset;
        }

        public String getDocumentId() { return documentId; }
        public int getIndex() { return index; }
        public String getContent() { return content; }
        public int getCharOffset() { return charOffset; }
    }
}
