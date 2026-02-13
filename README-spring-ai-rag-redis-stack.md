# Spring AI와 Redis Stack을 사용한 RAG(Retrieval-Augmented Generation) 샘플

## 환경 설정

### 표준프레임워크 실행환경 5.0 (Boot 적용)

| 항목 | 버전 |
| :--- | :--- |
| JDK | 17 |
| Jakarta EE | 10 |
| Servlet | 6.0 |
| Spring Framework | 6.2.11 |
| Spring Boot | 3.5.6 |
| Spring AI | 1.0.1 |

### 개발 및 빌드 도구

| 항목 | 버전 |
| :--- | :--- |
| Maven | 3.9.9 |
| Docker | 28.0.4 |

### 외부 서비스

| 항목 | 버전 | 비고 |
| :--- | :--- | :--- |
| Ollama | 0.16.0 | LLM 모델 서빙 |
| Redis Stack | 7.4.0-v3 (Redis Server 7.4.2) | Docker 이미지: `redis/redis-stack:7.4.0-v3` |

## 사용 기술

1. Java 17
2. Spring Boot 3.5.6 (Maven)
3. Spring AI 1.0.1
4. Redis Stack
5. Ollama
6. ONNX Runtime (로컬 임베딩)

## 라이선스 주의사항

- 해당 프로젝트는 Redis Stack을 사용하며, Redis Stack은 Apache 2.0 라이선스가 **아닌** 다음 라이선스 하에 배포되므로 상용 서비스 제공 시 제약이 존재함

| 기술 스택 | 라이선스 | 상용 사용 가능 여부 | 비고 |
| :------- | :------ | :----------------- | :--- |
| **Spring AI** | Apache 2.0 | 가능 | 제약 없음 |
| **Spring Boot** | Apache 2.0 | 가능 | 제약 없음 |
| **Ollama** | MIT | 가능 | 제약 없음 (단, 사용 모델의 라이선스는 별도 확인 필요) |
| **Redis Stack Server** | RSALv2 / SSPLv1 듀얼 라이선스 | 조건부 | **Apache 2.0 아님**, SaaS 제공 시 제약 가능 |
| **RedisInsight** | SSPL | 조건부 | SaaS 제공 시 소스 공개 의무 |
| **ONNX Runtime** | MIT | 가능 | 제약 없음 |

### 주의사항

- **Redis Stack**은 Apache 2.0 라이선스가 **아니므로** 상용 서비스 제공 시 라이선스 조항 확인이 필요함.
- **Ollama**는 MIT 라이선스이지만, 사용하는 **LLM 모델의 라이선스는 별도로 확인**하여야 함.

### 참고 링크

- [Redis 라이선스 정보](https://redis.io/legal/licenses/)
- [RSALv2 라이선스 전문](https://redis.com/legal/rsalv2-agreement/)
- [SSPL 라이선스 전문](https://www.mongodb.com/licensing/server-side-public-license)
- [Ollama 라이선스](https://github.com/ollama/ollama/blob/main/LICENSE)
- [Spring AI 라이선스](https://github.com/spring-projects/spring-ai/blob/main/LICENSE.txt)

## 사전 준비

1. [Ollama](https://ollama.com/download) 설치 및 사용할 LLM 모델을 설치한다. Ollama 설치 및 ONNX 모델 익스포트 방법은 [루트 README](./README.md)의 `공통 사전 준비`, `폐쇄망에서의 Ollama`, `Onnx 모델 익스포트` 항목을 참고한다.
2. 임베딩 모델 파일(`model.onnx`, `tokenizer.json`)은 `src\main\resources\model` (윈도우) 또는 `src/main/resources/model` (리눅스/macOS) 디렉토리에 위치시켜야 한다.
3. Redis Stack에 인덱싱 될 문서의 경로는 `application.yml`의 `spring.ai.document.path` 및 `spring.ai.document.pdf-path` 속성에 설정되어 있으므로 확인 후 환경에 맞추어 변경하도록 한다.
4. `docker-compose.yml` 을 사용해 `docker compose up -d`로 docker container 기반의 Redis 설정을 해 둔다. Redis Insight의 기본 포트는 `8001`이다.

## Spring AI ONNX 모델 주의사항

- ONNX 모델이 **2GB 이상**인 경우 `model.onnx` (그래프)와 `model.onnx_data` (가중치)로 분리되어 저장된다.
- Spring AI는 현재 외부 데이터 파일(`model.onnx_data`)을 자동으로 처리하지 못한다.
- **해결 방법**: `optimum-cli`로 직접 변환하면 대부분 단일 파일로 생성되어 호환성 문제가 없다.
- 해당 오류 발생 시 증상: `ORT_RUNTIME_EXCEPTION: Exception during initialization: file_size...`

## 아키텍처

해당 프로젝트는 Spring AI의 **ChatClient + Advisor 패턴**을 사용하여 RAG 시스템을 구현한다. Advisor를 조합하여 채팅 메모리, RAG 검색, 질의 압축 등의 기능을 선언적으로 구성한다.

### 주요 컴포넌트

#### 1. ChatClient + Advisor 기반 RAG 응답

```java
@Override
public Flux<ChatResponse> streamRagResponse(String query, String model) {
    String sessionId = SessionContext.getCurrentSessionId();

    ChatClientRequestSpec requestSpec = createRequestSpec(query, model);

    Advisor ragAdvisor = EgovRagConfig.createRagAdvisor(
        sessionId,
        compressionTransformer,
        vectorStoreDocumentRetriever,
        enableQueryCompression
    );

    return requestSpec
            .advisors(messageChatMemoryAdvisor, ragAdvisor)
            .advisors(a -> a.param(
                ChatMemory.CONVERSATION_ID, sessionId))
            .stream()
            .chatResponse();
}
```

#### 2. RAG Advisor 생성 (질의 압축 + 벡터 검색)

```java
public static Advisor createRagAdvisor(
        String sessionId,
        EgovCompressionQueryTransformer compressionTransformer,
        VectorStoreDocumentRetriever documentRetriever,
        boolean enableQueryCompression) {

    LoggingDocumentRetriever loggingRetriever =
        new LoggingDocumentRetriever(documentRetriever);

    if (enableQueryCompression) {
        SessionAwareQueryTransformer sessionAwareTransformer =
            new SessionAwareQueryTransformer(
                compressionTransformer, sessionId);

        return RetrievalAugmentationAdvisor.builder()
                .queryTransformers(sessionAwareTransformer)
                .documentRetriever(loggingRetriever)
                .build();
    } else {
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(loggingRetriever)
                .build();
    }
}
```

#### 3. EgovRedisChatMemoryRepository (Redis 기반 채팅 메모리)

```java
@Component
public class EgovRedisChatMemoryRepository implements ChatMemoryRepository {

    private static final String CHAT_MEMORY_KEY_PREFIX = "chat:memory:";

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        String key = CHAT_MEMORY_KEY_PREFIX + conversationId;
        List<Map<String, Object>> simpleMessages = messages.stream()
            .map(this::messageToMap)
            .collect(Collectors.toList());
        String messagesJson = objectMapper.writeValueAsString(simpleMessages);
        redisTemplate.opsForValue().set(key, messagesJson);
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        String key = CHAT_MEMORY_KEY_PREFIX + conversationId;
        Object value = redisTemplate.opsForValue().get(key);
        // JSON 역직렬화 후 Message 객체로 변환
    }
}
```

#### 4. VectorStoreDocumentRetriever (벡터 검색)

```java
@Bean
public VectorStoreDocumentRetriever vectorStoreDocumentRetriever(
        RedisVectorStore redisVectorStore) {
    return VectorStoreDocumentRetriever.builder()
            .similarityThreshold(similarityThreshold)
            .topK(topK)
            .vectorStore(redisVectorStore)
            .build();
}
```

### 데이터 흐름

```
사용자 질문
    │
    ▼
┌──────────────────────────────┐
│ EgovCompressionQuery         │ → 대화 히스토리 기반 질의 압축
│    Transformer (선택적)      │
└────────────┬─────────────────┘
             │
             ▼
┌──────────────────────────────┐
│ VectorStoreDocumentRetriever │ → Redis 벡터 검색
│  (유사도 임계값 + Top K)     │
└────────────┬─────────────────┘
             │
             ▼
┌──────────────────────────────┐
│ ChatClient + Advisors        │ → Ollama LLM 호출
│  (RAG + ChatMemory)          │
└────────────┬─────────────────┘
             │
             ▼
┌──────────────────────────────┐
│ EgovRedisChatMemoryRepository│ → Redis 저장
│  (대화 히스토리 자동 저장)    │
└────────────┬─────────────────┘
             │
             ▼
Flux<ChatResponse> 스트리밍 응답
```

## 문서 인덱싱

- 현재 인덱싱 가능한 문서의 종류는 마크다운 파일과 PDF 파일로 구성되어 있다.
- `application.yml` 의 `spring.ai.document.path` 및 `spring.ai.document.pdf-path` 에서 확인 가능하다.

## 실행

1. 애플리케이션 실행 후 도큐먼트 생성 및 임베딩, 적재가 실행된다. 수동으로 실행하려면 메인 화면의 `문서 재인덱싱` 버튼을 클릭한다.
2. `문서 업로드` 버튼은 파일을 `spring.ai.document.path`에 지정된 경로로 옮긴다. 현재는 마크다운만 가능하다.
3. `http://localhost:8001/` 에서 인덱싱된 데이터 확인이 가능하다. 이 데이터는 RAG를 적용한 답변 생성 시 LLM이 참고할 문서로 사용된다.
4. 메인 화면의 `RAG 채팅 모드`, `일반 채팅 모드` 버튼으로 RAG가 적용된 질의 답변, 일반적인 질의 답변을 받을 수 있다.

## API 명세

### 세션 관리 API

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/chat/sessions` | 새 세션 생성 |
| GET | `/api/chat/sessions` | 전체 세션 목록 |
| GET | `/api/chat/sessions/{sessionId}/messages` | 세션 메시지 조회 |
| PUT | `/api/chat/sessions/{sessionId}/title` | 세션 제목 변경 |
| DELETE | `/api/chat/sessions/{sessionId}` | 세션 삭제 |

### 채팅 API

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/ai/rag/stream` | RAG 기반 스트리밍 채팅 |
| GET | `/ai/simple/stream` | 일반 스트리밍 채팅 |

**파라미터:**
- `message`: 사용자 질문
- `model`: 모델명 (선택, 기본값: application.yml 설정)
- `sessionId`: 세션 ID (선택)

### 문서 관리 API

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/documents/reindex` | 문서 재인덱싱 |
| GET | `/api/documents/status` | 인덱싱 상태 조회 |
| POST | `/api/documents/upload` | 마크다운 파일 업로드 (최대 5개, 5MB/파일) |

### 모델 관리 API

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/ollama/models` | Ollama 모델 목록 |

## 프로젝트 구조

```
spring-ai-rag-redis-stack/
├── src/main/java/com/example/chat/
│   ├── config/                        # 설정 클래스
│   │   ├── EgovRagConfig.java         # RAG Advisor, 벡터 검색 설정
│   │   ├── EgovChatMemoryConfig.java  # ChatMemory, MessageWindowChatMemory 설정
│   │   ├── EgovRedisConfig.java       # RedisTemplate 설정
│   │   ├── EgovAsyncConfig.java       # 비동기 처리 설정
│   │   ├── EgovCommonConfig.java      # 공통 설정
│   │   ├── etl/                       # ETL 파이프라인
│   │   │   ├── EgovETLPipelineConfig.java          # ETL 빈 구성
│   │   │   ├── readers/
│   │   │   │   ├── EgovMarkdownReader.java         # 마크다운 리더
│   │   │   │   └── EgovPdfReader.java              # PDF 리더
│   │   │   ├── transformers/
│   │   │   │   ├── EgovContentFormatTransformer.java    # 문서 정규화
│   │   │   │   └── EgovEnhancedDocumentTransformer.java # 청킹, 메타데이터
│   │   │   └── writers/
│   │   │       └── EgovVectorStoreWriter.java      # Redis 벡터 저장
│   │   └── rag/transformers/
│   │       └── EgovCompressionQueryTransformer.java # 질의 압축
│   │
│   ├── service/                       # 서비스 계층
│   │   ├── EgovSessionAwareChatService.java         # 채팅 서비스 인터페이스
│   │   ├── EgovChatSessionService.java              # 세션 서비스 인터페이스
│   │   ├── EgovDocumentService.java                 # 문서 서비스 인터페이스
│   │   ├── EgovOllamaModelService.java              # 모델 서비스 인터페이스
│   │   └── impl/
│   │       ├── EgovSessionAwareChatServiceImpl.java # 채팅 서비스 구현체
│   │       ├── EgovChatSessionServiceImpl.java      # 세션 서비스 구현체
│   │       ├── EgovDocumentServiceImpl.java         # 문서 서비스 구현체
│   │       └── EgovOllamaModelServiceImpl.java      # 모델 서비스 구현체
│   │
│   ├── repository/                    # 데이터 접근 계층
│   │   └── EgovRedisChatMemoryRepository.java       # ChatMemoryRepository 구현 (Redis)
│   │
│   ├── controller/                    # REST 컨트롤러
│   │   ├── EgovOllamaChatController.java            # 채팅 API
│   │   ├── EgovChatSessionController.java           # 세션 API
│   │   ├── EgovDocumentController.java              # 문서 API
│   │   ├── EgovOllamaModelController.java           # 모델 API
│   │   └── EgovWebController.java                   # 웹 뷰 컨트롤러
│   │
│   ├── context/                       # 세션 컨텍스트
│   │   └── SessionContext.java        # ThreadLocal 세션 관리
│   │
│   ├── dto/                           # DTO
│   ├── response/                      # 응답 객체
│   └── util/                          # 유틸리티
│       ├── EgovPromptEngineeringUtil.java           # 프롬프트 엔지니어링
│       ├── EgovJsonPromptTemplates.java             # JSON 프롬프트 템플릿
│       ├── EgovResponseCleanerUtil.java             # 응답 정리
│       ├── EgovThinkTagOutputConverter.java         # think 태그 처리
│       └── EgovDocumentHashUtil.java                # 문서 해시 (변경 감지)
│
├── src/main/resources/
│   ├── application.yml                # 애플리케이션 설정
│   ├── model/                         # ONNX 임베딩 모델 (gitignore 대상)
│   ├── static/js/
│   │   └── marked.min.js             # 마크다운 렌더링
│   └── templates/
│       └── chat.html                  # 채팅 UI
│
├── pom.xml                            # Maven 설정
└── docker-compose.yml                 # Redis Stack Docker 설정
```

## 설정 가이드

### application.yml 주요 설정

```yaml
spring:
  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        model: qwen3-4b:Q4_K_M
        options:
          temperature: 0.4

    # 로컬 ONNX 임베딩 모델 설정
    embedding:
      transformer:
        onnx:
          modelUri: classpath:model/model.onnx
        tokenizer:
          uri: classpath:model/tokenizer.json

    # Redis 벡터 저장소 설정
    vectorstore:
      redis:
        initialize-schema: true       # 첫 실행 시 true, 이후 false
        index-name: document-index

    # 문서 경로 설정
    document:
      path: file:C:/workspace-test/upload/data/**/*.md
      pdf-path: file:C:/workspace-test/upload/data/**/*.pdf
      chunk-size: 4000                # 청크 크기 (토큰 단위)
      min-chunk-size-chars: 350       # 최소 청크 크기 (문자 단위)

  # Redis 연결 설정
  data:
    redis:
      host: localhost
      port: 6379

# RAG 설정
rag:
  enable-query-compression: true      # 대화 히스토리 기반 질의 압축
  similarity:
    threshold: 0.20                   # 유사도 임계값
  top-k: 3                            # 검색 결과 개수

# 채팅 메모리 설정
chat:
  memory:
    max-messages: 20                  # 최대 메시지 수
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

### Redis 연결 실패

```bash
# Docker 컨테이너 확인
docker ps

# Redis Stack 시작
docker compose up -d

# Redis 연결 테스트
redis-cli ping
```

### ONNX Runtime 초기화 실패

- **윈도우**: [Visual C++ Redistributable](https://aka.ms/vs/17/release/vc_redist.x64.exe) 설치가 필요하다.
- **리눅스**: `sudo apt-get install -y libgomp1` 명령으로 필요한 라이브러리를 설치한다.

### 메모리 부족

```bash
# 힙 메모리 증가
java -Xmx4g -jar target/spring-ai-rag-redis-stack-1.0.0.jar
```

## 참고 자료

- [Spring AI 공식 문서](https://docs.spring.io/spring-ai/reference/)
- [Spring AI Ollama](https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html)
- [Spring AI RAG](https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html)
- [Spring AI Vector Store - Redis](https://docs.spring.io/spring-ai/reference/api/vectordbs/redis.html)
- [Redis Stack 문서](https://redis.io/docs/stack/)
- [Ollama 문서](https://github.com/ollama/ollama)
- [ONNX Runtime 문서](https://onnxruntime.ai/)
