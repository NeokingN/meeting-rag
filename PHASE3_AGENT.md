# 第3个月：Agent 进阶路线图

> 当你跑通 Phase 1（纪要抽取）+ Phase 2（RAG问答），下一步就是让AI从"被动回答"变成"主动干活"

## 🎯 Agent 能力蓝图

```
Phase 1 ✅  结构化抽取    "读了会议纪要，输出JSON"
Phase 2 ✅  RAG问答       "问了会议知识库，给你答案"
Phase 3 🎯  Agent工具调用   "你说一句，它自动查+写+发"
```

## Agent 场景示例

### 场景1：会后自动跟进
```
用户："帮我跟进昨天的会"
Agent自动执行：
  ① 查日历 → 找到昨天会议
  ② 读纪要 → 提取action_items
  ③ 调日历API → 给每个责任人创建日程
  ④ 发企业微信/邮件 → 提醒待办
```

### 场景2：智能会议助手
```
用户："下周三产品评审会需要准备什么？"
Agent：
  ① RAG检索 → 上次评审会的议题模板
  ② 查参会人日历 → 确认可用时间
  ③ 生成 → 会议议程草案 + 材料清单
```

### 场景3：跨会议洞察
```
用户："这个月各部门的重点项目有哪些？"
Agent：
  ① 检索本月所有会议纪要（按部门）
  ② 汇总每个部门的decisions和action_items
  ③ 生成 → 月度会议洞察报告
```

## 技术实现（Spring AI Tool Calling）

```java
// 定义工具
@Tool(description = "查询某场会议的详细信息和待办列表")
public MeetingDetail getMeetingDetail(@ToolParam(description = "会议ID") String meetingId) {
    return meetingRepository.findById(meetingId);
}

@Tool(description = "在会议知识库中搜索相关内容")
public List<SearchResult> searchKnowledgeBase(@ToolParam(description = "搜索关键词") String keyword) {
    return vectorStore.search(keyword, 5);
}

@Tool(description = "创建日历提醒")
public void createCalendarReminder(String title, String assignee, String dueDate) {
    calendarClient.create(title, assignee, dueDate);
}

// Agent配置
ChatClient agentClient = ChatClient.builder(chatModel)
    .defaultTools(getMeetingDetail, searchKnowledgeBase, createCalendarReminder)
    .build();

// 调用
agentClient.prompt()
    .user("帮我跟进昨天的Q3规划会，给每个责任人创建日程提醒")
    .call()
    .content();
```

## 3个月总览回顾

| 月份 | 主题 | 产出 | 面试定位 |
|------|------|------|----------|
| 第1月 | Spring AI + 纪要抽取 | MVP可运行Demo | "会接LLM到Java系统" |
| 第2月 | RAG会议知识库 | PGVector + 权限隔离 + 引用溯源 | "做过完整RAG落地" |
| 第3月 | Agent + 工程化 | Tool Calling + 流式 + 监控 | "能做AI应用后端/架构" |

## 简历最终版定位

> **资深Java后端（AI应用方向）** | 10年后端经验 + Spring AI工程化落地
> 
> - 主导会议系统AI化改造：Spring AI + RAG + Agent技术栈
> - 构建企业会议知识库：PGVector向量检索 + 部门权限隔离 + 引用溯源
> - 设计会议纪要AI抽取Pipeline：Prompt工程 + JSON Schema约束 + 降级兜底
> - 规划Agent工具链：Tool Calling实现会后自动跟进闭环
> - 工程化能力：Redis缓存、Micrometer监控、Docker部署、流式SSE
