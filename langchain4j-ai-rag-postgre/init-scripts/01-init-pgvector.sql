-- PGVector 확장 설치
CREATE EXTENSION IF NOT EXISTS vector;

-- 하이브리드 lexical 검색용 pg_trgm 확장 설치
-- 컨테이너 초기화 시점에 슈퍼유저로 설치하므로, 애플리케이션 계정에 CREATE EXTENSION
-- 권한(슈퍼유저)을 부여할 필요가 없다. GIN trigram 인덱스는 document_embeddings 테이블이
-- 애플리케이션 기동 후 생성되므로 EgovHybridIndexInitializer에서 계속 담당한다.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 문서 해시 저장 테이블 (변경 감지용)
CREATE TABLE IF NOT EXISTS document_hashes (
    doc_id VARCHAR(500) PRIMARY KEY,
    hash VARCHAR(32) NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 채팅 세션 테이블
CREATE TABLE IF NOT EXISTS chat_sessions (
    session_id VARCHAR(36) PRIMARY KEY,
    title VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 채팅 메모리 테이블
CREATE TABLE IF NOT EXISTS chat_memory (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    message_type VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (session_id) REFERENCES chat_sessions(session_id) ON DELETE CASCADE
);

-- 인덱스 생성
CREATE INDEX IF NOT EXISTS idx_chat_memory_session_id ON chat_memory(session_id);
CREATE INDEX IF NOT EXISTS idx_chat_memory_created_at ON chat_memory(created_at);
CREATE INDEX IF NOT EXISTS idx_chat_sessions_updated_at ON chat_sessions(updated_at DESC);
