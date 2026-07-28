# 会议知识库 RAG 

> Spring AI + PGVector + DeepSeek → 上传会议纪要 → 语义检索 → AI问答（带引用溯源）

## 📐 架构总览

```
┌──────────────────────────────────────────────────────────────────┐
│                        用户 / 前端                              │
│              "Q3产品会定了哪些排期？"                            │
└────────────────────┬─────────────────────────────────────────────┘
                     │  POST /api/v1/rag/chat
                     ▼
┌──────────────────────────────────────────────────────────────────┐
│                    RagController (API层)                         │
└────────┬─────────────────────────────────────┬───────────────────┘
         │                                     │
         ▼                                     ▼
┌────────────────────┐          ┌────────────────────────────┐
│  IngestionService  │          │     RagChatService         │
│  (文档摄入管道)     │          │   ★ RAG核心：Retrieve→Augment→Generate │
└────────┬───────────┘          └────────┬───────────────────┘
         │                               │
         ▼                               ▼
┌────────────────────┐          ┌────────────────────────────┐
│  DocumentChunker    │          │   VectorStoreService       │
│  滑动窗口切分       │          │   PGVector 相似度检索       │
│  (500字/块,80重叠)  │          │   + 部门权限过滤           │
└────────┬───────────┘          └────────┬───────────────────┘
         │                               │
         ▼                               ▼
┌──────────────────────────────────────────────────────────────────┐
│                     EmbeddingService                            │
│         Ollama (nomic-embed-text) / 通义 text-embedding-v3      │
│                    文本 → 768维向量                              │
└────────────────────┬─────────────────────────────────────────────┘
                     ▼
┌──────────────────────────────────────────────────────────────────┐
│                    PostgreSQL + pgvector                        │
│            vector_store 表 (HNSW索引, 余弦相似度)                │
└──────────────────────────────────────────────────────────────────┘
                     │
                     ▼ (检索TopK结果)
┌──────────────────────────────────────────────────────────────────┐
│              DeepSeek Chat API (生成回答)                        │
│          Prompt = 系统指令 + 检索上下文 + 用户问题               │
└────────────────────┬─────────────────────────────────────────────┘
                     ▼
┌──────────────────────────────────────────────────────────────────┐
│              返回 ChatResponse                                  │
│         answer + citations[] (引用溯源)                         │
└──────────────────────────────────────────────────────────────────┘
```

## 🚀 快速启动

### 方式一：Docker一键起（推荐）

```bash
cd meeting-rag
docker-compose up -d

# 等待Ollama拉取模型（约2分钟）之后执行以下命令确定模型是否拉取完毕
docker logs meeting-rag-ollama -f

curl http://localhost:11434
```

### 方式二：手动安装

```bash
# 1. 安装PostgreSQL + pgvector
# macOS
brew install postgresql@16 && brew install pgvector
psql -d meeting_rag -c "CREATE EXTENSION vector;"

# Ubuntu
sudo apt install postgresql-16-pgvector

# 2. 安装Ollama + 拉取Embedding模型
curl -fsSL https://ollama.com/install.sh | sh
ollama pull nomic-embed-text

# 3. 创建数据库表
psql -d meeting_rag -f src/main/resources/schema.sql
```

### 设置环境变量

```bash
export OPENAI_API_KEY=sk-1fd2723140334d399a93df80644c689d
export OPENAI_BASE_URL=https://api.deepseek.com
export DB_URL=jdbc:postgresql://localhost:5432/meeting_rag
export DB_USER=postgres
export DB_PASSWORD=postgres
```
```bash
$env:OPENAI_API_KEY="sk-1fd2723140334d399a93df80644c689d"
$env:OPENAI_BASE_URL="https://api.deepseek.com"
$env:DB_URL="jdbc:postgresql://localhost:5432/meeting_rag"
$env:DB_USER="postgres"
$env:DB_PASSWORD="postgres"
```


### 启动应用

```bash
cd meeting-rag
mvn spring-boot:run
```

## 📝 API 使用

### 1. 摄入会议纪要

```bash
curl -X POST http://localhost:8081/api/v1/rag/ingest \
  -H "Content-Type: application/json" \
  -d '{
    "meetingId": "MTG-2026-0720",
    "title": "Q3产品规划评审会",
    "content": "会议主题：Q3产品规划\n参会人：张伟、李娜、王强\n张伟：Q3核心目标是将DAU提升到50万，主要靠智能纪要功能拉动。\n李娜：智能纪要后端已就绪，RAG方案测试准确率92%。\n王强：UI设计稿下周三交付。\n决议：智能纪要8月5日灰度，8月20日全量。\n待办：李娜负责后端部署，王强负责设计，张伟协调运营资源。",
    "organizer": "张伟",
    "attendees": "张伟,李娜,王强",
    "department": "产品部",
    "meetingDate": "2026-07-20T14:00:00"
  }'
```

### 2. AI问答

```bash
curl -X POST http://localhost:8081/api/v1/rag/chat \
  -H "Content-Type: application/json" \
  -d '{
    "question": "Q3产品会定了哪些排期和责任人？",
    "userId": "user-001",
    "department": "产品部"
  }'
```

```
curl -X POST http://localhost:8081/api/v1/rag/chat -H "Content-Type: application/json" -d "{\"question\":\"Q3产品会定了哪些排期和责任人？\",\"userId\":\"user-001\",\"department\":\"产品部\"}"
```

### 3. 预期返回

```json
{
  "success": true,
  "question": "Q3产品会定了哪些排期和责任人？",
  "answer": "根据Q3产品规划评审会：\n1. 智能纪要功能8月5日灰度，8月20日全量 [1]\n2. 责任人：李娜（后端部署）、王强（设计稿下周三交付）、张伟（协调运营资源）[2]\n3. Q3目标DAU 50万 [1]",
  "citations": [
    {
      "meetingId": "MTG-2026-0720",
      "meetingTitle": "Q3产品规划评审会",
      "snippet": "决议：智能纪要8月5日灰度，8月20日全量...",
      "score": 0.87,
      "meetingDate": "2026-07-20"
    }
  ],
  "costMs": 2100,
  "tokensUsed": 1200,
  "model": "deepseek-chat"
}
```


### 1. 为什么用PGVector而不是Milvus？
- **PGVector**：已有PostgreSQL运维经验，JSONB存metadata天然适合权限过滤，中小规模（百万级向量）性能足够
- **Milvus**：千万级以上、需要GPU加速时再切，Spring AI也支持

### 2. Embedding模型怎么选？

| 模型 | 维度 | 费用 | 效果 | 适用场景 |
|------|------|------|------|----------|
| nomic-embed-text (Ollama) | 768 | 免费本地 | 中 | 本地开发/离线环境 |
| text-embedding-v3 (通义) | 1536 | ¥0.0007/千次 | 高 | 生产推荐 |
| bge-m3 | 1024 | 免费/自部署 | 高 | 开源SOTA |

### 3. 权限隔离怎么做？
- 每条Chunk的metadata存 `department` + `meeting_id`
- 检索时拼接 `filterExpression`：`department == '产品部'`
- 结合原有会议系统RBAC，用户只能检索自己部门的会议

### 4. 降本策略
- Redis缓存相同问题的回答（1小时TTL）
- 短问题用小模型Embedding（nomic），长文本才上大模型
- TopK控制在3-5，减少Prompt长度 → 省Token

## 📅 接入原有会议系统

```java
// 会议系统，会议结束后自动调用：
@Autowired
private IngestionService ingestionService;

// 会议结束回调
@EventListener
public void onMeetingEnded(MeetingEndedEvent event) {
    // 1. 从ASR服务获取转写文本
    String transcript = asrService.getTranscript(event.getMeetingId());
    
    // 2. 摄入RAG
    ingestionService.ingest(
        event.getMeetingId(),
        event.getTitle(),
        transcript,
        event.getOrganizer(),
        event.getAttendees(),
        event.getDepartment(),
        event.getEndTime()
    );
}
```

## 

> "企业会议知识库RAG系统，架构是 Spring AI + PGVector + DeepSeek。
> 流程是：会议转写文本进来 → 滑动窗口切分（500字/块，80字重叠）→ Ollama本地Embedding → PGVector存储（HNSW索引）。
> 用户提问时：问题Embedding → 余弦相似度Top5检索 → 拼进Prompt → DeepSeek生成回答 → 带引用溯源返回。
> 做了权限隔离（按部门过滤metadata）、Redis缓存热点问题、Token消耗监控。
> 准确率比直接问大模型高30%，因为限制了检索范围不会瞎编。"

## 📂 项目结构

```
meeting-rag/
├── pom.xml
├── docker-compose.yml          # PG + Redis + Ollama 一键起
├── README.md
├── schema.sql                  # 数据库建表
└── src/main/java/com/meeting/rag/
    ├── MeetingRagApplication.java
    ├── controller/
    │   └── RagController.java           # REST API
    ├── service/
    │   ├── IngestionService.java        # 文档摄入管道
    │   ├── DocumentChunker.java         # 滑动窗口切分
    │   ├── EmbeddingService.java        # 文本→向量
    │   ├── VectorStoreService.java      # PGVector读写+权限过滤
    │   └── RagChatService.java          # ★ RAG核心流水线
    └── model/
        ├── MeetingDocument.java
        ├── ChatRequest.java
        └── ChatResponse.java
```
