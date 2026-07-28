package com.meeting.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * RAG问答响应 - 带引用溯源
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    private boolean success;
    private String question;
    private String answer;            // AI生成的回答
    private List<Citation> citations; // 引用来源（溯源用）
    private long costMs;
    private Integer tokensUsed;
    private String model;

    /**
     * 引用来源 - 告诉用户答案来自哪场会议的哪段话
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Citation {
        private String meetingId;
        private String meetingTitle;
        private String snippet;        // 原文片段
        private Double score;          // 相似度分数
        private String meetingDate;
    }

    public static ChatResponse ok(String question, String answer, List<Citation> citations,
                                   long costMs, Integer tokens, String model) {
        return ChatResponse.builder()
                .success(true)
                .question(question)
                .answer(answer)
                .citations(citations)
                .costMs(costMs)
                .tokensUsed(tokens)
                .model(model)
                .build();
    }

    public static ChatResponse fail(String question, String error) {
        return ChatResponse.builder()
                .success(false)
                .question(question)
                .answer(error)
                .costMs(0)
                .build();
    }
}
