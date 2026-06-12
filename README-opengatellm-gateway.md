# OpenGateLLM AI Gateway 연계 가이드

이 문서는 전자정부 표준프레임워크 AI RAG 샘플에서 OpenGateLLM을 AI Gateway로 연계하는 방법을 설명한다.

OpenGateLLM은 LLM 모델 자체가 아니라 생성형 AI API Gateway이다. 표준프레임워크 애플리케이션은 Spring AI 또는 LangChain4j를 통해 OpenAI 호환 API를 호출하고, OpenGateLLM은 그 뒤에서 로컬 모델, 기관 내부 모델, 외부 provider를 정책에 따라 라우팅한다.

## 적용 목적

공공 정보시스템에서 생성형 AI를 사용할 때는 단순히 모델을 호출하는 것만으로 충분하지 않다. 다음 항목을 함께 통제해야 한다.

- 어떤 모델이 호출되었는지
- 로컬 처리인지 외부 provider 전송인지
- fallback이 발생했는지
- RAG 근거 문서가 무엇인지
- 호출 지연 시간과 실패 사유가 무엇인지
- prompt/response 원문을 저장할지, metadata만 저장할지

OpenGateLLM을 사용하면 RAG 애플리케이션의 모델 호출 endpoint를 하나로 통합하고, 기관 정책에 맞는 라우팅과 감사 체계를 구성할 수 있다.

## 권장 아키텍처

```text
사용자 질문
  -> eGovFrame Controller
  -> eGovFrame Service
  -> Spring AI / LangChain4j RAG
  -> OpenGateLLM Gateway
  -> 로컬 LLM / 기관 내부 모델 / 외부 provider
```

이 구조에서 RAG 검색, 세션, 문서 인덱싱 등 기존 애플리케이션 책임은 유지한다. OpenGateLLM은 모델 호출 정책, provider 전환, gateway-level 로그를 담당한다.

## Spring AI 샘플 연계

`spring-ai-rag-redis-stack` 샘플에서는 OpenGateLLM을 OpenAI 호환 endpoint로 사용할 수 있다.

### 1. 의존성 확인

Spring AI OpenAI starter가 필요하다. `spring-ai-rag-redis-stack/pom.xml`에 다음 의존성이 포함되어 있는지 확인한다.

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-openai</artifactId>
</dependency>
```

### 2. profile 설정

`spring-ai-rag-redis-stack/src/main/resources/application-opengatellm.yml` 예시는 다음 값을 사용한다.

```yaml
spring:
  ai:
    model:
      chat: openai
    openai:
      base-url: ${OPENGATELLM_BASE_URL:http://localhost:8000}
      api-key: ${OPENGATELLM_API_KEY:change-me}
      chat:
        completions-path: /v1/chat/completions
        options:
          model: ${OPENGATELLM_MODEL:kr-gov-local-general}
          temperature: 0.2
```

OpenGateLLM이 `/v1/chat/completions`를 제공하는 OpenAI 호환 gateway라면, 애플리케이션은 모델 provider의 실제 위치를 알 필요가 없다.

샘플 코드는 `OllamaChatModel` 직접 의존 대신 Spring AI의 공통 `ChatModel`을 사용한다. 기본 profile은 Ollama를 유지하고, `opengatellm` profile을 켜면 같은 `/ai/rag/stream`, `/ai/simple/stream` 엔드포인트가 OpenGateLLM을 경유한다.

### 3. 실행

```bash
java -jar target/spring-ai-rag-redis-stack-1.0.0.jar \
  --spring.profiles.active=opengatellm \
  --OPENGATELLM_BASE_URL=http://localhost:8000 \
  --OPENGATELLM_API_KEY=change-me \
  --OPENGATELLM_MODEL=kr-gov-local-general
```

### 4. 실행 검증

OpenGateLLM profile로 실행한 뒤 다음 순서로 gateway 적용 여부를 확인한다.

```bash
# 1) 현재 AI Gateway 설정과 감사 정책 확인
curl http://localhost:8080/api/ai-gateway/status

# 2) OpenGateLLM /v1/models를 통해 라우터 목록 확인
curl http://localhost:8080/api/ai-gateway/models

# 3) 기존 RAG/일반 채팅 API를 그대로 호출
curl "http://localhost:8080/ai/simple/stream?message=OpenGateLLM%EC%9D%98%20%EC%97%AD%ED%95%A0%EC%9D%80%3F"

# 4) 애플리케이션 레이어의 metadata-only 감사 이벤트 확인
curl http://localhost:8080/api/ai-gateway/audit/events
```

`/api/ai-gateway/audit/events`는 prompt/response 원문을 저장하지 않고 `promptHash`, `requestedModel`, `providerProfile`, `route`, `sessionId`, `latencyMs`, `terminalSignal` 같은 metadata만 반환한다.

## OpenGateLLM 라우팅 예시

```yaml
models:
  - name: kr-gov-local-general
    type: text-generation
    providers:
      - type: openai
        url: http://ollama:11434/v1
        key: ollama
        model_name: llama32-ko:latest
        model_hosting_zone: KOR

  - name: kr-gov-policy-auto
    type: text-generation
    providers:
      - type: openai
        url: http://ollama:11434/v1
        key: ollama
        model_name: llama32-ko:latest
        model_hosting_zone: KOR
      - type: openai
        url: https://external-provider.example/v1
        key: ${EXTERNAL_PROVIDER_API_KEY}
        model_name: fallback-model
        model_hosting_zone: WOR
```

## 표준프레임워크 관점의 확장 포인트

초기 단계에서는 runtime 핵심 모듈에 직접 포함하기보다 다음 형태가 적합하다.

1. `egovframe-ai-rag` 샘플 profile로 제공
2. 개발 가이드에 AI Gateway 연계 절차 추가
3. VS Code Initializr에서 AI Gateway 옵션 제공
4. 안정화 후 공통 모듈 또는 starter 검토

## 감사 로그 권장 항목

애플리케이션 또는 gateway에서 다음 metadata를 남기는 것을 권장한다.

| 항목 | 설명 |
| --- | --- |
| requestId | AI 호출 추적 ID |
| systemId | 업무 시스템 식별자 |
| route | OpenGateLLM 라우트명 |
| provider | 실제 호출 provider |
| model | 실제 호출 모델 |
| hostingZone | 로컬/기관 내부/외부 cloud 구분 |
| ragDocumentIds | RAG 근거 문서 ID |
| latencyMs | 호출 지연 시간 |
| fallbackReason | fallback 발생 시 사유 |
| promptStored | prompt 원문 저장 여부 |
| responseStored | response 원문 저장 여부 |

개인정보 또는 비공개 행정정보가 포함될 수 있는 환경에서는 prompt/response 원문 저장을 기본 비활성화하고, hash 및 metadata 중심으로 감사 체계를 구성하는 것이 안전하다.

## 기대 효과

- 기존 RAG 샘플의 구조를 유지하면서 AI 호출 통제 계층을 추가할 수 있다.
- 로컬 LLM 우선 정책과 외부 provider fallback을 분리해 운영할 수 있다.
- 공공기관의 데이터 주권, 보안, 감사 요구사항을 gateway 수준에서 설명할 수 있다.
- 모델 교체 시 애플리케이션 코드 변경을 최소화할 수 있다.
