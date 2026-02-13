# 전자정부 표준프레임워크 AI RAG 샘플

RAG(Retrieval-Augmented Generation) 기반의 AI 질의응답 시스템 샘플 프로젝트이다.
동일한 목적을 서로 다른 기술 스택으로 구현한 두 가지 샘플을 제공한다.

## 프로젝트 구성

| 프로젝트 | AI 프레임워크 | 벡터 저장소 | 상세 문서 |
| :------- | :----------- | :---------- | :-------- |
| [spring-ai-rag-redis-stack](./spring-ai-rag-redis-stack) | Spring AI 1.0.1 | Redis Stack | [README](./README-spring-ai-rag-redis-stack.md) |
| [langchain4j-ai-rag-postgre](./langchain4j-ai-rag-postgre) | LangChain4j 1.8.0 | PostgreSQL (PGVector) | [README](./README-langchain4j-ai-rag-postgre.md) |

## 공통 환경

| 항목 | 버전 |
| :--- | :--- |
| JDK | 17 |
| Spring Boot | 3.5.6 |
| Maven | 3.9.9 |
| Ollama | 0.16.0 |
| Docker | 28.0.4 |

## 공통 사전 준비

1. [Ollama](https://ollama.com/download) 설치 및 사용할 LLM 모델을 설치한다. 폐쇄망의 경우에는 아래 `폐쇄망에서의 Ollama` 항목을 참고한다.
2. ONNX 임베딩 모델을 생성한다. 아래 `Onnx 모델 익스포트` 항목을 참고한다. 모델 파일의 배치 경로는 프로젝트마다 다르므로 각 프로젝트 README를 확인한다.
3. Docker 기반 벡터 저장소를 실행한다. (`docker-compose.yml`이 각 프로젝트에 포함되어 있다.)

## 폐쇄망에서의 Ollama

- 폐쇄망에서 Ollama 및 LLM 모델을 사용하기 위해서는 미리 인터넷이 가능한 환경에서 필요한 파일들을 준비하여 둘 필요가 있다.
- `OllamaSetup.exe` (인스톨러) : [Ollama](https://ollama.com/download) 에서 다운로드
- LLM 모델 : `gguf` 형식으로 다운로드 받아 둘 필요가 있다. [Ollama 모델 페이지](https://ollama.com/search) 및 [Huggingface](https://huggingface.co/) 에서 다양한 모델을 제공하고 있으므로 현재 하드웨어 사양에 맞추어 준비하여 둔다.
- Modelfile : 모델 설정을 사용자 지정할 경우에 사용되는 파일이며 폐쇄망 환경에서는 단순히 `gguf`만 복사해서는 모델 명을 인식하지 못하므로 해당 파일로 등록을 따로 진행할 필요가 있다. 등록은 GGUF 및 modelfile 이 있는 경로에서 `ollama create [사용할 이름] –f [modelfile 명]` 으로 등록하면 된다.
- 동일한 LLM 모델이라도 Modelfile을 얼마나 잘 작성하냐에 따라 응답의 퀄리티는 달라질 수 있으므로 커스텀 용도로도 사용 가능하다.
- 기본적인 작성에 관련된 사항은 [공식 문서](https://github.com/ollama/ollama/blob/main/docs/modelfile.md) 를 참고 가능하다. 다음은 기본적인 예시이다.

```
FROM hyperclova.gguf

TEMPLATE """Answer the user's questions below concisely and clearly in Korean. Do not repeat the same information.

### Question:

{{ .Prompt }}

### Answer:

"""

PARAMETER temperature 0.3
```

## Onnx 모델 익스포트

### 개요

- `Onnx (Open Neural Network Exchange)`는 기계학습 모델을 다른 딥러닝 프레임워크 환경 (ex. Tensorflow, PyTorch, etc..)에서 서로 호환되게 사용할 수 있도록 만들어진 공유 플랫폼이다.
- 다양한 프레임워크 간의 호환성 문제를 해결하고, 모델 배포 및 활용에 유연성을 제공할 수 있다.
- 로컬 환경에서 Embedding 작업을 수행하기 위해서는 Embedding 모델을 ONNX로 익스포트하여 사용하여야 한다.
- Huggingface에 배포되는 여러 모델 중 적합한 모델을 취사 선택하여 `Onnx`로 변환 후, 로컬에서 사용하는 것이 가능하다.

### 사용 모델 소개

- 본 프로젝트에서는 [ko-sroberta-multitask](https://huggingface.co/jhgan/ko-sroberta-multitask) 모델을 기본으로 사용한다.
- Python에서 가상 환경을 설정하고 필요한 패키지를 설치하며, 모델을 `Onnx` 포맷으로 익스포트한다.
- 익스포트가 완료되면 Embedding에 사용되는 `tokenizer.json` 및 `model.onnx` 파일이 생성된다.
- 생성된 파일의 배치 경로는 프로젝트마다 다르므로 각 프로젝트 README의 `사전 준비` 항목을 참고한다.

### 방법 1: 기본 익스포트 방법 (권장)

대부분의 HuggingFace 임베딩 모델에 적용 가능한 방법이다. **[ko-sroberta-multitask](https://huggingface.co/jhgan/ko-sroberta-multitask)** 모델에서 테스트 완료되었다.

#### 윈도우 환경

```bash
# 1. 가상 환경 생성 (패키지 버전 충돌 방지)
python -m venv venv

# 2. 가상 환경 활성화
.\venv\Scripts\activate

# 3. pip 업그레이드
python -m pip install --upgrade pip

# 4. 필요한 패키지 설치
pip install optimum onnx onnxruntime sentence-transformers

# 5. 모델을 ONNX 포맷으로 익스포트 (현재 경로에 생성됨)
optimum-cli export onnx -m jhgan/ko-sroberta-multitask .

# 6. 생성된 model.onnx와 tokenizer.json을 각 프로젝트의 모델 경로로 이동
```

#### 리눅스/macOS 환경

```bash
# 1. 가상 환경 생성 (패키지 버전 충돌 방지)
python3 -m venv venv

# 2. 가상 환경 활성화
source ./venv/bin/activate

# 3. pip 업그레이드
python -m pip install --upgrade pip

# 4. 필요한 패키지 설치
pip install optimum onnx onnxruntime sentence-transformers

# 5. 모델을 ONNX 포맷으로 익스포트 (현재 경로에 생성됨)
optimum-cli export onnx -m jhgan/ko-sroberta-multitask .

# 6. 생성된 model.onnx와 tokenizer.json을 각 프로젝트의 모델 경로로 이동
```

### 방법 2: 대체 익스포트 방법

방법 1로 정상적으로 익스포트되지 않는 경우 다음 방법을 시도한다. **패키지를 묶어서 설치하는 것**이 차이점이며, **[google/embeddinggemma-300m](https://huggingface.co/google/embeddinggemma-300m)** 같은 일부 모델에서 필요하다.

#### 윈도우 환경

```bash
# 1. 가상 환경 생성
python -m venv venv

# 2. 가상 환경 활성화
.\venv\Scripts\activate

# 3. pip 업그레이드
pip install --upgrade pip

# 4. 필요한 패키지를 묶어서 설치 (중요: 순서 및 묶음 설치)
pip install -U huggingface-hub transformers optimum[onnx] onnxruntime sentence-transformers

# 5. (선택사항) 설치된 패키지 버전 확인
pip show huggingface-hub transformers optimum onnxruntime sentence-transformers

# 6. (선택사항) HuggingFace 로그인 (일부 모델에서 필요할 수 있음)
hf auth login

# 7. 모델을 ONNX 포맷으로 익스포트
optimum-cli export onnx --model [모델명] [export 경로]
# 예: optimum-cli export onnx --model google/embeddinggemma-300m .

# 8. 생성된 파일들을 각 프로젝트의 모델 경로로 이동
```

#### 리눅스/macOS 환경

```bash
# 1. 가상 환경 생성
python3 -m venv venv

# 2. 가상 환경 활성화
source ./venv/bin/activate

# 3. pip 업그레이드
pip install --upgrade pip

# 4. 필요한 패키지를 묶어서 설치 (중요: 순서 및 묶음 설치)
pip install -U huggingface-hub transformers optimum[onnx] onnxruntime sentence-transformers

# 5. (선택사항) 설치된 패키지 버전 확인
pip show huggingface-hub transformers optimum onnxruntime sentence-transformers

# 6. (선택사항) HuggingFace 로그인 (일부 모델에서 필요할 수 있음)
hf auth login

# 7. 모델을 ONNX 포맷으로 익스포트
optimum-cli export onnx --model [모델명] [export 경로]
# 예: optimum-cli export onnx --model google/embeddinggemma-300m .

# 8. 생성된 파일들을 각 프로젝트의 모델 경로로 이동
```

### 트러블슈팅

#### 문제 1: 익스포트 시 패키지 의존성 오류 발생
- **증상**: `ModuleNotFoundError`, `ImportError`, 또는 패키지 버전 충돌
- **해결**: 방법 1 대신 방법 2를 사용한다. 패키지를 묶어서 설치(`pip install -U huggingface-hub transformers optimum[onnx] sentence-transformers`)하면 의존성 충돌이 해결된다.

#### 문제 2: 일부 모델에서 403 Forbidden 또는 인증 오류
- **증상**: `HTTPError: 403 Client Error: Forbidden`
- **해결**: `huggingface-cli login` 명령으로 HuggingFace에 로그인한 후 다시 시도한다.

#### 문제 3: 가상 환경 활성화가 안 됨 (윈도우)
- **증상**: `activate : 이 시스템에서 스크립트를 실행할 수 없으므로...`
- **해결**: PowerShell 실행 정책 확인. 관리자 권한으로 PowerShell 실행 후 `Set-ExecutionPolicy RemoteSigned` 명령을 실행한다.

#### 문제 4: python 명령을 찾을 수 없음
- **윈도우**: Python 설치 시 "Add Python to PATH" 옵션을 선택했는지 확인한다.
- **리눅스/macOS**: `python3` 명령을 사용한다. 또는 `python-is-python3` 패키지를 설치한다.

### 참고 자료

- [Spring AI 공식 문서 - ONNX Embeddings](https://docs.spring.io/spring-ai/reference/api/embeddings/onnx.html#_prerequisites)
- [HuggingFace Optimum CLI 문서](https://huggingface.co/docs/optimum/exporters/onnx/usage_guides/export_a_model)

## 라이선스

[LICENSE.txt](./LICENSE.txt) 참고
각 기술 스택별 라이선스 상세는 프로젝트별 README를 참고하시기 바랍니다.
