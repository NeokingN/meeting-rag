package com.meeting.rag.controller;

import com.meeting.rag.model.ChatRequest;
import com.meeting.rag.model.ChatResponse;
import com.meeting.rag.service.IngestionService;
import com.meeting.rag.service.RagChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * RAG 会议知识库 API
 */
@Tag(name = "会议知识库RAG", description = "会议纪要入库 + AI问答")
@RestController
@RequestMapping("/api/v1/rag")
public class RagController {

    private final IngestionService ingestionService;
    private final RagChatService ragChatService;

    public RagController(IngestionService ingestionService, RagChatService ragChatService) {
        this.ingestionService = ingestionService;
        this.ragChatService = ragChatService;
    }

    /**
     * POST /api/v1/rag/ingest
     * 上传会议纪要文本，向量化入库
     */
    @Operation(summary = "摄入会议纪要", description = "将会议纪要文本切分、Embedding后存入PGVector")
    @PostMapping("/ingest")
    public ResponseEntity<?> ingest(@RequestBody IngestionRequest request) {
        var result = ingestionService.ingest(
                request.getMeetingId(),
                request.getTitle(),
                request.getContent(),
                request.getOrganizer(),
                request.getAttendees(),
                request.getDepartment(),
                request.getMeetingDate()
        );
        return ResponseEntity.ok(result);
    }

    /**
     * POST /api/v1/rag/chat
     * AI问答 - 基于已入库的会议纪要
     */
    @Operation(summary = "RAG问答", description = "基于会议知识库的AI问答，带引用溯源")
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        ChatResponse response = ragChatService.chat(request);
        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /api/v1/rag/meeting/{meetingId}
     * 删除某场会议
     */
    @DeleteMapping("/meeting/{meetingId}")
    public ResponseEntity<?> deleteMeeting(@PathVariable String meetingId) {
        ingestionService.deleteMeeting(meetingId);
        return ResponseEntity.ok().body("{\"deleted\":\"" + meetingId + "\"}");
    }

    /**
     * GET /api/v1/rag/health
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("{\"status\":\"UP\",\"service\":\"meeting-rag\"}");
    }

    /**
     * 摄入请求体
     */
    public static class IngestionRequest {
        private String meetingId;
        private String title;
        private String content;
        private String organizer;
        private String attendees;
        private String department;
        private java.time.LocalDateTime meetingDate;

        // Getters & Setters
        public String getMeetingId() { return meetingId; }
        public void setMeetingId(String meetingId) { this.meetingId = meetingId; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }

        public String getOrganizer() { return organizer; }
        public void setOrganizer(String organizer) { this.organizer = organizer; }

        public String getAttendees() { return attendees; }
        public void setAttendees(String attendees) { this.attendees = attendees; }

        public String getDepartment() { return department; }
        public void setDepartment(String department) { this.department = department; }

        public java.time.LocalDateTime getMeetingDate() { return meetingDate; }
        public void setMeetingDate(java.time.LocalDateTime meetingDate) { this.meetingDate = meetingDate; }
    }
}
