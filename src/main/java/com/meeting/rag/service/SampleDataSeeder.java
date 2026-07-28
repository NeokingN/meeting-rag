package com.meeting.rag.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 示例数据种子器 - 启动时自动摄入示例会议纪要
 * 方便你一键跑通整个RAG流程
 */
@Component
public class SampleDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(SampleDataSeeder.class);

    private final IngestionService ingestionService;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    @Value("${meeting.rag.cache.enabled:true}")
    private boolean seedOnStartup;

    public SampleDataSeeder(IngestionService ingestionService, JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.ingestionService = ingestionService;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void seedIfEmpty() {
        try {
            // 检查是否已有数据
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM meeting_documents", Integer.class);
            if (count != null && count > 0) {
                log.info("已有 {} 条会议纪要，跳过种子数据", count);
                return;
            }

            // 读取示例数据
            ClassPathResource resource = new ClassPathResource("sample-meetings.json");
            try (InputStream is = resource.getInputStream()) {
                List<Map<String, Object>> meetings = objectMapper.readValue(is, new TypeReference<>() {});

                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

                for (Map<String, Object> m : meetings) {
                    LocalDateTime date = LocalDateTime.parse((String) m.get("meetingDate"), fmt);

                    var result = ingestionService.ingest(
                            (String) m.get("meetingId"),
                            (String) m.get("title"),
                            (String) m.get("content"),
                            (String) m.getOrDefault("organizer", ""),
                            (String) m.getOrDefault("attendees", ""),
                            (String) m.getOrDefault("department", ""),
                            date
                    );
                    log.info("🌱 种子数据: {} - {} ({} chunks)", result.getMeetingId(), result.getTitle(), result.getChunkCount());
                }

                log.info("✅ 示例数据摄入完成，共 {} 场会议", meetings.size());
                log.info("   试试提问: 'Q3产品会定了哪些排期？' 或 '方言转写选了哪家供应商？'");
            }
        } catch (Exception e) {
            log.warn("种子数据加载跳过: {}", e.getMessage());
        }
    }
}
