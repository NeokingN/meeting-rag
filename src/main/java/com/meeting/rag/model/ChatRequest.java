package com.meeting.rag.model;

import lombok.Data;

/**
 * RAG问答请求
 */
@Data
public class ChatRequest {

    private String question;          // 用户问题
    private String userId;            // 提问者ID（用于权限过滤）
    private String department;        // 所属部门（可选，不传则查全部有权限的）
    private String meetingId;         // 指定某场会议（可选）
    private Boolean stream;           // 是否流式返回（SSE）
}
