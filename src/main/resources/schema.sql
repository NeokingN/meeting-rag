-- ============================================
-- 会议知识库 RAG - 数据库初始化脚本
-- PostgreSQL 14+  +  pgvector 扩展
-- ============================================

-- 1. 安装pgvector扩展（需PostgreSQL超级用户执行一次）
CREATE EXTENSION IF NOT EXISTS vector;

-- 2. 会议文档元数据表（可选，PGVector自带metadata列也可）
CREATE TABLE IF NOT EXISTS meeting_documents (
    id BIGSERIAL PRIMARY KEY,
    meeting_id VARCHAR(128) UNIQUE NOT NULL,
    title VARCHAR(512) NOT NULL,
    content TEXT,
    file_path VARCHAR(1024),
    file_type VARCHAR(20),
    meeting_date TIMESTAMP,
    organizer VARCHAR(128),
    attendees TEXT,
    department VARCHAR(128),
    uploaded_at TIMESTAMP DEFAULT NOW(),
    chunk_count INTEGER DEFAULT 0,
    indexed BOOLEAN DEFAULT FALSE
);

-- 3. PGVector 向量表（Spring AI PgVectorStore 自动建表名: vector_store）
-- 手动建表语句（维度需与Embedding模型一致）：
-- nomic-embed-text → 768维
-- bge-m3 → 1024维
-- text-embedding-v3 → 1536维

CREATE TABLE IF NOT EXISTS vector_store (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content TEXT NOT NULL,
    metadata JSONB,
    embedding vector(768)  -- ⚠️ 根据所选模型修改维度
);

-- HNSW索引（加速ANN检索）
CREATE INDEX IF NOT EXISTS vector_store_embedding_idx
    ON vector_store
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- 按部门过滤的辅助索引
CREATE INDEX IF NOT EXISTS idx_vector_metadata_dept
    ON vector_store USING GIN (metadata jsonb_path_ops);

-- 4. 初始化示例数据（可选，方便测试）
-- INSERT INTO meeting_documents (meeting_id, title, content, organizer, department, meeting_date)
-- VALUES ('MTG-2026-001', 'Q3产品规划会', '...会议内容...', '张伟', '产品部', '2026-07-20 14:00:00');
