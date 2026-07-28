package com.meeting.rag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meeting.rag.model.ChatResponse;
import com.meeting.rag.model.MeetingDocument;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Types;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 向量存储服务 - 封装PGVector的读写
 *
 * 核心能力：
 * 1. 写入：MeetingDocument → Chunk → Embedding → PGVector
 * 2. 检索：用户问题 → Embedding → 相似度搜索 → TopK
 * 3. 权限过滤：按部门/参会人隔离检索范围
 */
@Service
public class VectorStoreService {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreService.class);

    private final PgVectorStore vectorStore;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final EmbeddingService embeddingService;

    // PGVector metadata字段名常量
    private static final String META_MEETING_ID = "meeting_id";
    private static final String META_TITLE = "title";
    private static final String META_DATE = "meeting_date";
    private static final String META_DEPT = "department";
    private static final String META_INDEX = "chunk_index";
    private static final String META_ORGANIZER = "organizer";

    public VectorStoreService(PgVectorStore vectorStore,
                              JdbcTemplate jdbc,
                              ObjectMapper objectMapper,
                              EmbeddingService embeddingService) {
        this.vectorStore = vectorStore;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.embeddingService = embeddingService;
    }

    @PostConstruct
    public void init() {
        log.info("PGVector Store 初始化完成");
    }

    /**
     * 写入一条会议纪要：切分 → Embedding → 入库
     */
    public int ingest(MeetingDocument doc, List<DocumentChunker.TextChunk> chunks) {
        List<Document> documents = new LinkedHashMap<Integer, Document>() {{
            // 保持顺序
        }}.values().stream().toList(); // placeholder, replaced below

        // 构建Spring AI Document列表
        List<Document> docs = new ArrayList<>();
        for (DocumentChunker.TextChunk chunk : chunks) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put(META_MEETING_ID, doc.getMeetingId());
            metadata.put(META_TITLE, doc.getTitle());
            metadata.put(META_DATE, doc.getMeetingDate() != null ? doc.getMeetingDate().toString() : "");
            metadata.put(META_DEPT, doc.getDepartment());
            metadata.put(META_INDEX, chunk.getIndex());
            metadata.put(META_ORGANIZER, doc.getOrganizer());

            // Spring AI Document 要求 ID 为合法 UUID，使用 nameUUID 生成确定性 UUID
            String docId = java.util.UUID.nameUUIDFromBytes(
                    (doc.getMeetingId() + "_" + chunk.getIndex()).getBytes(java.nio.charset.StandardCharsets.UTF_8)
            ).toString();
            Document aiDoc = new Document(
                    docId,
                    chunk.getContent(),
                    metadata
            );
            docs.add(aiDoc);
        }

        // 批量写入PGVector（内部自动调Embedding）
        vectorStore.add(docs);

        log.info("会议纪要 {} 已入库，共 {} 个Chunk", doc.getMeetingId(), docs.size());
        return docs.size();
    }

    /**
     * 语义检索 + 权限过滤
     *
     * @param query       用户问题
     * @param department  部门过滤（null=不限）
     * @param meetingId   指定会议（null=不限）
     * @param topK        召回数量
     * @return 相关文档列表
     */
    public List<RetrievedChunk> search(String query, String department, String meetingId, int topK) {
        // PGVector的metadata过滤表达式
        String filterExpr = buildFilter(department, meetingId);

        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .filterExpression(filterExpr.isEmpty() ? null : filterExpr)
                .build();

        List<Document> results = vectorStore.similaritySearch(request);

        return results.stream().map(doc -> {
            Map<String, Object> meta = doc.getMetadata();
            return new RetrievedChunk(
                    (String) meta.getOrDefault(META_MEETING_ID, ""),
                    (String) meta.getOrDefault(META_TITLE, ""),
                    doc.getText(),
                    (String) meta.getOrDefault(META_DATE, ""),
                    (String) meta.getOrDefault(META_DEPT, ""),
                    (Integer) meta.getOrDefault(META_INDEX, 0),
                    // score需要Spring AI 1.0+ 支持，低版本用null
                    null
            );
        }).collect(Collectors.toList());
    }

    /**
     * 构建PGVector metadata过滤表达式
     * Spring AI PGVector支持: eq/and/or/in 等
     */
    private String buildFilter(String department, String meetingId) {
        List<String> conditions = new ArrayList<>();
        if (department != null && !department.isEmpty()) {
            conditions.add("department == '" + department + "'");
        }
        if (meetingId != null && !meetingId.isEmpty()) {
            conditions.add("meeting_id == '" + meetingId + "'");
        }
        return String.join(" && ", conditions);
    }

    /**
     * 删除某场会议的所有Chunk
     */
    public void deleteByMeetingId(String meetingId) {
        // Spring AI PgVectorStore 按ID前缀删除需要自定义SQL
        String sql = "DELETE FROM vector_store WHERE metadata->>'meeting_id' = ?";
        int deleted = jdbc.update(sql, new Object[]{meetingId}, new int[]{Types.VARCHAR});
        log.info("删除会议 {} 的向量数据: {} 条", meetingId, deleted);
    }

    /**
     * 检索结果载体
     */
    public static class RetrievedChunk {
        private final String meetingId;
        private final String title;
        private final String content;
        private final String meetingDate;
        private final String department;
        private final int chunkIndex;
        private final Double score;

        public RetrievedChunk(String meetingId, String title, String content,
                              String meetingDate, String department, int chunkIndex, Double score) {
            this.meetingId = meetingId;
            this.title = title;
            this.content = content;
            this.meetingDate = meetingDate;
            this.department = department;
            this.chunkIndex = chunkIndex;
            this.score = score;
        }

        // Getters
        public String getMeetingId() { return meetingId; }
        public String getTitle() { return title; }
        public String getContent() { return content; }
        public String getMeetingDate() { return meetingDate; }
        public String getDepartment() { return department; }
        public int getChunkIndex() { return chunkIndex; }
        public Double getScore() { return score; }
    }
}
