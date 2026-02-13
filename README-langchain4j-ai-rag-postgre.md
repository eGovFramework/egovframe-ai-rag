# LangChain4j와 PostgreSQL(PGVector)을 사용한 RAG(Retrieval-Augmented Generation) 샘플

## 환경 설정

### 표준프레임워크 실행환경 5.0 (Boot 적용)

| 항목 | 버전 |
| :--- | :--- |
| JDK | 17 |
| Jakarta EE | 10 |
| Servlet | 6.0 |
| Spring Framework | 6.2.11 |
| Spring Boot | 3.5.6 |
| LangChain4j | 1.8.0 |

### 개발 및 빌드 도구

| 항목 | 버전 |
| :--- | :--- |
| Maven | 3.9.9 |
| Docker | 28.0.4 |

### 외부 서비스

| 항목 | 버전 | 비고 |
| :--- | :--- | :--- |
| Ollama | 0.16.0 | LLM 모델 서빙 |
| PostgreSQL (PGVector) | 17 | Docker 이미지: `pgvector/pgvector:pg17` |

## 사용 기술

1. Java 17
2. Spring Boot 3.5.6 (Maven)
3. LangChain4j 1.8.0 (AiServices 패턴)
4. PostgreSQL + PGVector
5. Ollama
6. ONNX Runtime (로컬 임베딩)

## 라이선스 주의사항

- 해당 프로젝트에서 사용하는 기술 스택의 라이선스 현황은 다음과 같다.

| 기술 스택 | 라이선스 | 상용 사용 가능 여부 | 비고 |
| :------- | :------ | :----------------- | :--- |
| **LangChain4j** | Apache 2.0 | 가능 | 제약 없음 |
| **Spring Boot** | Apache 2.0 | 가능 | 제약 없음 |
| **Ollama** | MIT | 가능 | 제약 없음 (단, 사용 모델의 라이선스는 별도 확인 필요) |
| **PostgreSQL** | PostgreSQL License | 가능 | MIT/BSD 유사 라이선스, 제약 없음 |
| **PGVector** | PostgreSQL License | 가능 | 제약 없음 |
| **ONNX Runtime** | MIT | 가능 | 제약 없음 |

### 주의사항

- **Ollama**는 MIT 라이선스이지만, 사용하는 **LLM 모델의 라이선스는 별도로 확인**하여야 한다.
- **PostgreSQL**과 **PGVector**는 모두 PostgreSQL License 하에 배포되며, 상용 사용에 제약이 없다.

### 참고 링크

- [LangChain4j 라이선스](https://github.com/langchain4j/langchain4j/blob/main/LICENSE)
- [PostgreSQL 라이선스](https://www.postgresql.org/about/licence/)
- [Ollama 라이선스](https://github.com/ollama/ollama/blob/main/LICENSE)

## 사전 준비

1. [Ollama](https://ollama.com/download) 설치 및 사용할 LLM 모델을 설치한다. Ollama 설치 및 ONNX 모델 익스포트 방법은 [루트 README](./README.md)의 `공통 사전 준비`, `폐쇄망에서의 Ollama`, `Onnx 모델 익스포트` 항목을 참고한다.

```bash
ollama pull qwen3-4b:Q4_K_M
ollama list
```

2. 임베딩 모델 파일(`model.onnx`, `tokenizer.json`)은 `${user.home}/EgovSearch-Config/Config/model` 디렉토리에 위치시켜야 한다.

3. `docker-compose.yml`을 사용해 `docker compose up -d`로 docker container 기반의 PostgreSQL 설정을 해 둔다.

4. 외부 설정 파일을 준비한다. 임베딩 모델과 토크나이저의 경로를 `searchConfig.json`에 설정한다.

```
위치: ${user.home}/EgovSearch-Config/Config/searchConfig.json
```

```json
{
  "modelPath": "${HOME}/EgovSearch-Config/Config/model/model.onnx",
  "tokenizerPath": "${HOME}/EgovSearch-Config/Config/model/tokenizer.json"
}
```

## 아키텍처

해당 프로젝트는 LangChain4j의 **AiServices 패턴**을 사용하여 선언적이고 간결한 RAG 시스템을 구현한다.

### 주요 컴포넌트

#### 1. AiServices 인터페이스

```java
// RagChatbot.java - RAG 기반 챗봇
public interface RagChatbot {
    @SystemMessage("당신은 지식 기반 질의응답 시스템입니다...")
    Flux<String> streamChat(@UserMessage String query);  // langchain4j-reactor
}

// SimpleChatbot.java - 일반 챗봇 (RAG 없음)
public interface SimpleChatbot {
    @SystemMessage("당신은 도움이 되는 AI 어시스턴트입니다...")
    Flux<String> streamChat(@UserMessage String query);  // langchain4j-reactor
}
```

#### 2. ChatbotFactory (동적 모델 선택 + ChatMemory 통합)

```java
public RagChatbot createRagChatbot(String modelName, String sessionId) {
    return AiServices.builder(RagChatbot.class)
        .streamingChatModel(streamingModel)
        .contentRetriever(contentRetriever)    // 자동 RAG 검색
        .chatMemory(createChatMemory(sessionId)) // 자동 히스토리 관리
        .build();
}
```

#### 3. PersistentChatMemoryStore (PostgreSQL 기반 채팅 메모리)

```java
@Component
public class PersistentChatMemoryStore implements ChatMemoryStore {
    // ChatMemoryStore 인터페이스 구현
    // PostgreSQL에 채팅 히스토리 자동 저장/조회
}
```

#### 4. ContentRetriever (RAG 자동 통합)

```java
@Bean
public ContentRetriever contentRetriever(...) {
    return EmbeddingStoreContentRetriever.builder()
        .embeddingStore(embeddingStore)
        .embeddingModel(embeddingModel)
        .maxResults(topK)
        .minScore(similarityThreshold)
        .build();
}
```

### 데이터 흐름

```
사용자 질문
    │
    ▼
┌──────────────────┐
│ ContentRetriever │ → PGVector 벡터 검색
│  (자동 RAG)      │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│   RagChatbot     │ → Ollama LLM 호출
│  (AiServices)    │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│  ChatMemory      │ → PostgreSQL 저장
│ (자동 저장)      │
└────────┬─────────┘
         │
         ▼
Flux<String> 스트리밍 응답
```

## 문서 인덱싱

- 현재 인덱싱 가능한 문서의 종류는 마크다운 파일과 PDF 파일로 구성되어 있다.
- `application.yml`의 문서 경로 관련 속성에서 확인 가능하다.

## 실행

1. 애플리케이션을 실행하면 도큐먼트 생성 및 임베딩, 적재가 실행된다. 수동으로 실행하려면 메인 화면의 `문서 재인덱싱` 버튼을 클릭한다.
2. `문서 업로드` 버튼으로 Markdown/PDF 파일을 업로드할 수 있다.
3. 메인 화면의 `RAG 채팅 모드`, `일반 채팅 모드` 버튼으로 RAG가 적용된 질의 답변, 일반적인 질의 답변을 받을 수 있다.
4. 기본 접속 주소: `http://localhost:8080`

## API 명세

### 세션 관리 API

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/sessions` | 새 세션 생성 |
| GET | `/api/sessions` | 전체 세션 목록 |
| GET | `/api/sessions/{sessionId}/messages` | 세션 메시지 조회 |
| PUT | `/api/sessions/{sessionId}/title` | 세션 제목 변경 |
| DELETE | `/api/sessions/{sessionId}` | 세션 삭제 |

### 채팅 API

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/chat/stream/rag` | RAG 기반 스트리밍 채팅 |
| GET | `/api/chat/stream/simple` | 일반 스트리밍 채팅 |

**파라미터:**
- `query`: 사용자 질문
- `model`: 모델명 (선택, 기본값: application.yml 설정)
- `sessionId`: 세션 ID (헤더)

### 문서 관리 API

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/documents/reindex` | 문서 재인덱싱 |
| GET | `/api/documents/status` | 인덱싱 상태 조회 |

### 모델 관리 API

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/ollama/models` | Ollama 모델 목록 |

## 프로젝트 구조

```
langchain4j-ai-rag-postgre/
├── src/main/java/com/example/chat/
│   ├── config/                      # 설정 클래스
│   │   ├── LangChain4jConfig.java   # LLM, 임베딩, 벡터스토어 설정
│   │   └── RagConfig.java           # ContentRetriever 설정
│   │
│   ├── service/                     # 서비스 계층
│   │   ├── RagChatbot.java          # RAG 챗봇 인터페이스 (AiServices)
│   │   ├── SimpleChatbot.java       # 일반 챗봇 인터페이스 (AiServices)
│   │   ├── ChatbotFactory.java      # 챗봇 팩토리 (동적 모델 + 메모리)
│   │   ├── ChatService.java         # 채팅 서비스 인터페이스
│   │   └── impl/
│   │       └── ChatServiceImpl.java # 채팅 서비스 구현체
│   │
│   ├── repository/                  # 데이터 접근 계층
│   │   ├── ChatMemoryRepository.java        # JPA Repository
│   │   └── PersistentChatMemoryStore.java   # ChatMemoryStore 구현
│   │
│   ├── entity/                      # JPA 엔티티
│   │   ├── ChatMemoryEntity.java    # 채팅 메모리 엔티티
│   │   └── ChatSessionEntity.java   # 세션 엔티티
│   │
│   ├── controller/                  # REST 컨트롤러
│   │   ├── ChatController.java      # 채팅 API
│   │   ├── ChatSessionController.java # 세션 API
│   │   └── DocumentController.java  # 문서 API
│   │
│   ├── context/                     # 세션 컨텍스트
│   │   └── SessionContext.java      # ThreadLocal 세션 관리
│   │
│   ├── dto/                         # DTO
│   └── util/                        # 유틸리티
│
├── src/main/resources/
│   ├── application.yml              # 애플리케이션 설정
│   └── templates/
│       └── chat.html                # 채팅 UI
│
├── pom.xml                          # Maven 설정
└── docker-compose.yml               # PostgreSQL Docker 설정
```

## 설정 가이드

### application.yml 주요 설정

```yaml
# LangChain4j Ollama 설정
langchain4j:
  ollama:
    base-url: http://localhost:11434
    chat-model:
      model-name: qwen3-4b:Q4_K_M
      temperature: 0.4
      timeout: 60s

# RAG 설정
rag:
  similarity:
    threshold: 0.20               # 유사도 임계값
  top-k: 3                        # 검색 결과 개수

# 채팅 메모리 설정
chat:
  memory:
    max-messages: 20              # 최대 메시지 수

# PGVector 설정
pgvector:
  host: localhost
  port: 5432
  database: ragdb
  username: postgres
  password: postgres
  table-name: document_embeddings
  dimension: 768
  create-table: true
```

## 문제 해결

### Ollama 연결 실패

```bash
# 연결 테스트
curl http://localhost:11434/api/tags

# Ollama 서비스 시작
ollama serve  # Linux/macOS
# Windows: Ollama 앱 실행
```

### PostgreSQL 연결 실패

```bash
# Docker 컨테이너 확인
docker ps

# 서비스 상태 확인
sudo systemctl status postgresql  # Linux
brew services list                 # macOS
```

### ONNX Runtime 초기화 실패

- **윈도우**: [Visual C++ Redistributable](https://aka.ms/vs/17/release/vc_redist.x64.exe) 설치가 필요하다.
- **리눅스**: `sudo apt-get install -y libgomp1` 명령으로 필요한 라이브러리를 설치한다.

### 메모리 부족

```bash
# 힙 메모리 증가
java -Xmx4g -jar target/langchain4j-ai-rag-postgre-1.0.0.jar
```

## 참고 자료

- [LangChain4j 공식 문서](https://docs.langchain4j.dev/)
- [LangChain4j AiServices](https://docs.langchain4j.dev/tutorials/ai-services)
- [LangChain4j RAG](https://docs.langchain4j.dev/tutorials/rag)
- [PGVector 문서](https://github.com/pgvector/pgvector)
- [Ollama 문서](https://github.com/ollama/ollama)
- [ONNX Runtime 문서](https://onnxruntime.ai/)
