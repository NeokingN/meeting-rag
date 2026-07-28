package com.meeting.rag.service;

import com.meeting.rag.model.ChatRequest;
import com.meeting.rag.model.ChatResponse;
import com.meeting.rag.model.ChatResponse.Citation;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * RAG核心流水线 - Retrieve → Augment → Generate
 *
 * 这是整个项目的灵魂，面试时重点讲这块：
 * "用户问 → 向量检索TopK → 拼进Prompt → LLM生成 → 带引用返回"
 */
@Service
public class RagChatService {

    private static final Logger log = LoggerFactory.getLogger(RagChatService.class);

    private final ChatClient chatClient;
    private final VectorStoreService vectorStore;
    private final RedisTemplate<String, String> redisTemplate;
    private final Timer ragTimer;

    @Value("${meeting.rag.retrieval.top-k:5}")
    private int topK;

    @Value("${meeting.rag.cache.enabled:true}")
    private boolean cacheEnabled;

    @Value("${meeting.rag.cache.ttl:3600}")
    private long cacheTtl;

    // RAG增强Prompt模板
    private static final String RAG_PROMPT = """
            你是一位企业会议知识助手。请基于以下【会议资料】回答用户问题。
            
            【会议资料】（共 {context_count} 条相关片段）：
            ---
            {context}
            ---
            
            回答要求：
            1. 只基于上述资料回答，不要编造
            2. 如果资料不足以回答，明确说"资料中未提及"
            3. 回答时标注引用编号 [1][2] 对应资料序号
            4. 用中文回答，简洁专业
            
            用户问题：{question}
            """;

    public RagChatService(ChatModel chatModel,
                          VectorStoreService vectorStore,
                          RedisTemplate<String, String> redisTemplate,
                          MeterRegistry meterRegistry) {
        this.vectorStore = vectorStore;
        this.redisTemplate = redisTemplate;

        this.chatClient = ChatClient.builder(chatModel)
                .defaultSystem("你是企业会议知识库AI助手，回答必须基于提供的会议资料，不编造。")
                .build();

        this.ragTimer = Timer.builder("meeting.rag.chat")
                .description("RAG问答耗时")
                .register(meterRegistry);
    }

    /**
     * 核心方法：RAG问答
     */
    public ChatResponse chat(ChatRequest request) {
        long start = System.currentTimeMillis();

        try {
            // 0. 缓存检查
            String cacheKey = "rag:answer:" + request.getQuestion().hashCode();
            if (cacheEnabled) {
                String cached = redisTemplate.opsForValue().get(cacheKey);
                if (cached != null) {
                    log.debug("命中缓存: {}", request.getQuestion());
                    // 简化：实际应缓存完整ChatResponse
                }
            }

            log.info("RAG问答开始: question='{}', department={}, meetingId={}",
                    request.getQuestion(), request.getDepartment(), request.getMeetingId());

            // 1. RETRIEVE - 向量检索
            List<VectorStoreService.RetrievedChunk> chunks = vectorStore.search(
                    request.getQuestion(),
                    request.getDepartment(),
                    request.getMeetingId(),
                    topK
            );

            if (chunks.isEmpty()) {
                return ChatResponse.fail(request.getQuestion(), "未找到相关会议资料，请确认检索范围或上传会议纪要");
            }

            // 2. AUGMENT - 组装上下文
            String context = buildContext(chunks);

            // 3. GENERATE - 调用LLM生成回答
            PromptTemplate promptTemplate = new PromptTemplate(RAG_PROMPT);
            String prompt = promptTemplate.create(Map.of(
                    "context_count", chunks.size(),
                    "context", context,
                    "question", request.getQuestion()
            )).getContents();

            String answer = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            // 4. 构建引用列表
            List<Citation> citations = buildCitations(chunks);

            long costMs = System.currentTimeMillis() - start;
            log.info("RAG问答完成: costMs={}, chunks={}, answerLen={}",
                    costMs, chunks.size(), answer.length());

            // 5. 写缓存
            if (cacheEnabled) {
                redisTemplate.opsForValue().set(cacheKey, answer, cacheTtl, TimeUnit.SECONDS);
            }

            ragTimer.record(costMs, TimeUnit.MILLISECONDS);

            return ChatResponse.ok(request.getQuestion(), answer, citations, costMs, estimateTokens(prompt, answer), "deepseek-chat");

        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - start;
            log.error("RAG问答异常: {}", e.getMessage(), e);
            return ChatResponse.fail(request.getQuestion(), "服务异常: " + e.getMessage());
        }
    }

    /**
     * 将检索到的Chunk组装为Prompt上下文
     */
    private String buildContext(List<VectorStoreService.RetrievedChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            VectorStoreService.RetrievedChunk c = chunks.get(i);
            sb.append("[").append(i + 1).append("] ");
            sb.append("会议: ").append(c.getTitle()).append(" (").append(c.getMeetingDate()).append(")\n");
            sb.append(c.getContent()).append("\n\n");
        }
        return sb.toString();
    }

    /**
     * 构建引用信息（前端展示"来自哪场会议"）
     */
    private List<Citation> buildCitations(List<VectorStoreService.RetrievedChunk> chunks) {
        List<Citation> citations = new ArrayList<>();
        for (VectorStoreService.RetrievedChunk c : chunks) {
            // 截取前100字作为摘要
            String snippet = c.getContent().length() > 100
                    ? c.getContent().substring(0, 100) + "..."
                    : c.getContent();
            citations.add(Citation.builder()
                    .meetingId(c.getMeetingId())
                    .meetingTitle(c.getTitle())
                    .snippet(snippet)
                    .score(c.getScore())
                    .meetingDate(c.getMeetingDate())
                    .build());
        }
        return citations;
    }

    /**
     * 粗略估算Token数（中文约1.5字符/token）
     */
    private Integer estimateTokens(String prompt, String answer) {
        return (int) ((prompt.length() + answer.length()) / 1.5);
    }
}
