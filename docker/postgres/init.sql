-- 创建pgvector扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 验证扩展
SELECT extname, extversion FROM pg_extension WHERE extname = 'vector';
