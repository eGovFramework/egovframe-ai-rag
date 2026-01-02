# Spring AI와 Redis Stack을 사용한 RAG(Retrieval-Augmented Generation) 샘플

## 환경 설정

| 프로그램 명 | 버전 명 |
| :--------- | :------ |
| java | 17 이상 |
| maven | 3.8.4 |
| spring boot | 3.5.6 |
| Spring AI | 1.0.1 |

## 사용 기술

1. Java
2. Spring Boot (Maven)
3. Spring AI
4. Redis Stack
5. Ollama

## 라이선스 주의사항

- 해당 프로젝트는 Redis Stack을 사용하며, Redis Stack은 Apache 2.0 라이선스가 **아닌** 다음 라이선스 하에 배포되므로 상용 서비스 제공 시 제약이 존재함

| 기술 스택 | 라이선스 | 상용 사용 가능 여부 | 비고 |
| :------- | :------ | :----------------- | :--- |
| **Spring AI** | Apache 2.0 | 가능 | 제약 없음 |
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

1. [Ollama](https://ollama.com/download) 설치 및 사용할 LLM 모델을 설치한다. 폐쇄망의 경우에는 `폐쇄망에서의 Ollama 설치` 항목을 참고한다.
2. 기본적인 임베딩 모델은 `\src\main\resources\model` 내에 설정하여 주도록 한다. 설정 방법은 `Onnx 모델 익스포트`, `사용 모델 소개` 항목을 참고한다.
3. Redis Stack에 인덱싱 될 문서의 경로는 `application.yml`의 `spring.ai.document.path` 및 `spring.ai.document.pdf-path` 속성에 설정되어 있으므로 확인 후 환경에 맞추어 변경하도록 한다.
4. `docker-compose.yml` 을 사용해 `docker compose up -d`로 docker container 기반의 Redis 설정을 해 둔다. Redis Insight의 기본 포트는 `8001`이다.

## 폐쇄망에서의 Ollama

- 폐쇄망에서 Ollama 및 LLM 모델을 사용하기 위해서는 미리 인터넷이 가능한 환경에서 필요한 파일들을 준비하여 둘 필요가 있다.
- `OllamaSetup.exe` (인스톨러) : [Ollama](https://ollama.com/download) 에서 다운로드
- LLM 모델 : `gguf` 형식으로 다운로드 받아 둘 필요가 있다. [Ollama 모델 페이지](https://ollama.com/search) 및 [Huggingface](https://huggingface.co/) 에서 다양한 모델을 제공하고 있으므로 현재 하드웨어 사양에 맞추어 준비하여 둔다.
- Modelfile : 모델 설정을 사용자 지정할 경우에 사용되는 파일이며 폐쇄망 환경에서는 단순히 `gguf`만 복사해서는 모델 명을 인식하지 못하므로 해당 파일로 등록을 따로 진행할 필요가 있다. 등록은 GGUF 및 modelfile 이 있는 경로에서 `ollama create [사용할 이름] –f [modelfile 명]` 으로 등록하면 된다.​
- 동일한 LLM 모델이라도 Modelfile을 얼마나 잘 작성하냐에 따라 응답의 퀄리티는 달라질 수 있으므로 커스텀 용도로도 사용 가능하다.
- 기본적인 작성에 관련된 사항은 [공식 문서](https://github.com/ollama/ollama/blob/main/docs/modelfile.md) 를 참고 가능하다. 다음은 기본적인 예시이다.

```
FROM hyperclova.gguf​

TEMPLATE """Answer the user's questions below concisely and clearly in Korean. Do not repeat the same information.

### Question:​

{{ .Prompt }}​

### Answer:​

"""​

PARAMETER temperature 0.3
```

## Onnx 모델 익스포트

- `Onnx (Open Neural Network Exchange)`는 기계학습 모델을 다른 딥러닝 프레임워크 환경 (ex. Tensorflow, PyTorch, etc..)에서 서로 호환되게 사용할 수 있도록 만들어진 공유 플랫폼이다.
- 다양한 프레임워크 간의 호환성 문제를 해결하고, 모델 배포 및 활용에 유연성을 제공할 수 있다.
- 로컬 환경에서 Embedding 작업을 수행하기 위해서는 Embedding 모델을 onnx로 익스포트하여 사용하여야 할 필요가 있으므로 그 과정을 소개한다.

### 사용 모델 소개

- Huggingface 에 배포되는 여러 모델 중 적합한 모델을 취사 선택하여 `Onnx (Open Neural Network Exchange)`로 변환 후, 로컬에서 사용하는 것이 가능하다.
- 본 프로젝트에서는 [ko-sroberta-multitask](https://huggingface.co/jhgan/ko-sroberta-multitask) 모델을 사용하는 방법을 소개한다.
- Python에서 가상 환경을 설정하고 필요한 패키지를 설치하며, 모델을 `Onnx` 포맷으로 익스포트한다.

```bash
# 패키지 버전 충돌을 방지하기 위해 venv 라는 이름으로 가상 환경을 생성한다
python3 -m venv venv

# 윈도우 환경에서 가상환경을 활성화 한다
.\venv\Scripts\activate

# 리눅스, macOS 환경에서 가상환경을 활성화 한다
source ./venv/bin/activate //

# 가상 환경이 활성화 된 상태에서 pip를 최신 버전으로 업그레이드한다
python -m pip install --upgrade pip

# 필요한 패키지들을 설치한다
pip install optimum onnx onnxruntime sentence-transformers

# 현재 경로에 jhgan/ko-sroberta-multitask 모델을 onnx 포맷으로 내보낸다
optimum-cli export onnx -m jhgan/ko-sroberta-multitask .
```

- 익스포트가 완료되면 Embedding에 사용되는 `tokenizer.json` 및 `model.onnx` 파일이 생성된다.
- 해당 파일들은 `\src\main\resources\model` 내에 설정하도록 한다.
- 상세 정보는 Spring에서 제공하는 [공식 문서](https://docs.spring.io/spring-ai/reference/api/embeddings/onnx.html#_prerequisites) 를 참조 가능하다.

## 문서 인덱싱

- 현재 인덱싱 가능한 문서의 종류는 마크다운 파일과 PDF 파일로 구성되어 있다.
- `application.yml` 의 `spring.ai.document.path` 및 `spring.ai.document.pdf-path` 에서 확인 가능하다.

## 실행

1. 애플리케이션 실행 후 도큐먼트 생성 및 임베딩, 적재가 실행된다. 수동으로 실행하려면 메인 화면의 `문서 재인덱싱` 버튼을 클릭한다. 
2. `문서 업로드` 버튼은 파일을 `spring.ai.document.path`에 지정된 경로로 옮긴다. 현재는 마크다운만 가능하다.
3. `http://localhost:8001/` 에서 인덱싱된 데이터 확인이 가능하다. 이 데이터는 RAG를 적용한 답변 생성 시 LLM이 참고할 문서로 사용된다.
4. 메인 화면의 `RAG 채팅 모드`, `일반 채팅 모드` 버튼으로 RAG가 적용된 질의 답변, 일반적인 질의 답변을 받을 수 있다.
