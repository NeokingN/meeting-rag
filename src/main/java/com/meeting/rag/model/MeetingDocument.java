package com.meeting.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会议文档实体 - 对应数据库表 meeting_documents
 * 一条记录 = 一次上传的会议纪要文件
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingDocument {

    private Long id;
    private String meetingId;        // 会议唯一ID
    private String title;            // 会议主题
    private String content;          // 原文内容
    private String filePath;         // 原始文件路径
    private String fileType;         // pdf / docx / txt / vtt(转写)
    private LocalDateTime meetingDate;
    private String organizer;        // 组织者
    private String attendees;        // 参会人（逗号分隔）
    private String department;       // 所属部门（用于权限隔离）
    private LocalDateTime uploadedAt;
    private Integer chunkCount;      // 切分后的块数
    private Boolean indexed;         // 是否已向量化入库
}
