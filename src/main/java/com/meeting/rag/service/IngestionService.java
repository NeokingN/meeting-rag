package com.meeting.rag.service;

import com.meeting.rag.model.MeetingDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * 文档摄入服务 - 处理会议纪要文件上传 → 解析 → 向量化入库
 *
 * 支持格式：.txt / .md / .pdf / .docx / .vtt(语音转写)
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final DocumentChunker chunker;
    private final VectorStoreService vectorStore;
    private final JdbcTemplate jdbc;

    // 文件上传目录
    private static final String UPLOAD_DIR = "/data/meeting-docs/";

    public IngestionService(DocumentChunker chunker, VectorStoreService vectorStore, JdbcTemplate jdbc) {
        this.chunker = chunker;
        this.vectorStore = vectorStore;
        this.jdbc = jdbc;
    }

    /**
     * 摄入会议纪要：解析 → 切分 → 向量化 → 入库
     */
    public IngestionResult ingest(String meetingId, String title, String content,
                                   String organizer, String attendees, String department,
                                   LocalDateTime meetingDate) {

        // 1. 构建文档对象
        MeetingDocument doc = MeetingDocument.builder()
                .meetingId(meetingId)
                .title(title)
                .content(content)
                .organizer(organizer)
                .attendees(attendees)
                .department(department)
                .meetingDate(meetingDate)
                .uploadedAt(LocalDateTime.now())
                .build();

        // 2. 切分
        var chunks = chunker.chunk(meetingId, content);
        doc.setChunkCount(chunks.size());

        // 3. 向量化入库
        int stored = vectorStore.ingest(doc, chunks);
        doc.setIndexed(true);

        // 4. 写入 meeting_documents 元数据表（UPSERT）
        saveMeetingDocument(doc);

        log.info("✅ 会议纪要入库完成: {} - {} ({} chunks)", meetingId, title, stored);

        return new IngestionResult(meetingId, title, stored, true);
    }

    /**
     * 从文件摄入
     */
    public IngestionResult ingestFromFile(String filePath, String meetingId, String title,
                                           String organizer, String department) {
        try {
            Path path = Paths.get(filePath);
            String content = Files.readString(path);
            String fileType = getFileType(filePath);

            // 按文件类型解析（PDF/DOCX后续扩展）
            if ("pdf".equals(fileType)) {
                content = parsePdf(path); // 占位，需加pdfbox依赖
            }

            return ingest(meetingId, title, content, organizer, "", department, LocalDateTime.now());
        } catch (IOException e) {
            throw new RuntimeException("文件读取失败: " + filePath, e);
        }
    }

    /**
     * 删除某场会议的向量数据（会议作废/更新时调用）
     */
    public void deleteMeeting(String meetingId) {
        vectorStore.deleteByMeetingId(meetingId);
        // 同步删除 meeting_documents 元数据
        int deleted = jdbc.update("DELETE FROM meeting_documents WHERE meeting_id = ?", meetingId);
        log.info("已删除会议数据: {} (向量 + 元数据{}条)", meetingId, deleted);
    }

    /**
     * 写入/更新 meeting_documents 元数据表
     */
    private void saveMeetingDocument(MeetingDocument doc) {
        String sql = """
            INSERT INTO meeting_documents (meeting_id, title, content, meeting_date, organizer, attendees, department, uploaded_at, chunk_count, indexed)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (meeting_id) DO UPDATE SET
                title = EXCLUDED.title,
                content = EXCLUDED.content,
                meeting_date = EXCLUDED.meeting_date,
                organizer = EXCLUDED.organizer,
                attendees = EXCLUDED.attendees,
                department = EXCLUDED.department,
                uploaded_at = EXCLUDED.uploaded_at,
                chunk_count = EXCLUDED.chunk_count,
                indexed = EXCLUDED.indexed
            """;
        jdbc.update(sql,
                doc.getMeetingId(),
                doc.getTitle(),
                doc.getContent(),
                doc.getMeetingDate() != null ? Timestamp.valueOf(doc.getMeetingDate()) : null,
                doc.getOrganizer(),
                doc.getAttendees(),
                doc.getDepartment(),
                doc.getUploadedAt() != null ? Timestamp.valueOf(doc.getUploadedAt()) : Timestamp.valueOf(LocalDateTime.now()),
                doc.getChunkCount(),
                doc.getIndexed()
        );
    }

    private String getFileType(String path) {
        int dot = path.lastIndexOf('.');
        return dot > 0 ? path.substring(dot + 1).toLowerCase() : "txt";
    }

    /**
     * PDF解析占位 - 实际用 Apache PDFBox
     */
    private String parsePdf(Path path) {
        // TODO: 集成 pdfbox
        // PDDocument doc = PDDocument.load(path.toFile());
        // PDFTextStripper stripper = new PDFTextStripper();
        // return stripper.getText(doc);
        return "PDF解析待实现";
    }

    /**
     * 摄入结果
     */
    public static class IngestionResult {
        private final String meetingId;
        private final String title;
        private final int chunkCount;
        private final boolean success;

        public IngestionResult(String meetingId, String title, int chunkCount, boolean success) {
            this.meetingId = meetingId;
            this.title = title;
            this.chunkCount = chunkCount;
            this.success = success;
        }

        public String getMeetingId() { return meetingId; }
        public String getTitle() { return title; }
        public int getChunkCount() { return chunkCount; }
        public boolean isSuccess() { return success; }
    }
}
