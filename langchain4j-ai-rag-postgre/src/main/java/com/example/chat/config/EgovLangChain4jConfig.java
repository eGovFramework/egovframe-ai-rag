package com.example.chat.config;

import com.example.chat.util.ConfigUtils;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.OnnxEmbeddingModel;
import dev.langchain4j.model.embedding.onnx.PoolingMode;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;
import java.time.Duration;

/**
 * LangChain4j 설정 클래스
 * - ONNX 임베딩 모델 (외부 파일 시스템 경로 사용)
 * - Ollama 채팅 모델
 * - PGVector 임베딩 저장소
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class EgovLangChain4jConfig implements InitializingBean {

    private final ConfigUtils configUtils;

    private String modelPath;
    private String tokenizerPath;
    private EmbeddingModel embeddingModel;

    @Value("${langchain4j.ollama.base-url}")
    private String ollamaBaseUrl;

    @Value("${langchain4j.ollama.chat-model.model-name}")
    private String ollamaModelName;

    @Value("${langchain4j.ollama.chat-model.temperature}")
    private Double ollamaTemperature;

    @Value("${langchain4j.ollama.chat-model.timeout:60s}")
    private Duration ollamaTimeout;

    @Value("${pgvector.host:localhost}")
    private String pgvectorHost;

    @Value("${pgvector.port:5432}")
    private Integer pgvectorPort;

    @Value("${pgvector.database:ragdb}")
    private String pgvectorDatabase;

    @Value("${pgvector.username:postgres}")
    private String pgvectorUsername;

    @Value("${pgvector.password:postgres}")
    private String pgvectorPassword;

    @Value("${pgvector.table-name:document_embeddings}")
    private String pgvectorTableName;

    @Value("${pgvector.dimension:768}")
    private Integer pgvectorDimension;

    @Value("${pgvector.create-table:true}")
    private Boolean createTable;

    @Override
    public void afterPropertiesSet() {
        // 외부 설정 파일에서 모델 경로 로드
        EgovEmbeddingConfig config = configUtils.loadConfig();
        if (config != null) {
            this.modelPath = config.getModelPath();
            this.tokenizerPath = config.getTokenizerPath();

            log.info("Initializing ONNX Embedding Model...");
            log.info("Model path: {}", modelPath);
            log.info("Tokenizer path: {}", tokenizerPath);
            log.info("OS: {} / Architecture: {}", System.getProperty("os.name"), System.getProperty("os.arch"));

            try {
                // 외부 파일 시스템 경로를 직접 사용 (임시 파일 복사 불필요)
                log.info("Creating OnnxEmbeddingModel instance...");
                this.embeddingModel = new OnnxEmbeddingModel(
                        Paths.get(modelPath),
                        Paths.get(tokenizerPath),
                        PoolingMode.MEAN);

                log.info("ONNX Embedding Model initialized successfully");
                log.info("Embedding dimension: {}", embeddingModel.dimension());

            } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
                handleNativeLibraryError(e);
            } catch (Exception e) {
                log.error("Failed to initialize ONNX Embedding Model", e);
                throw new RuntimeException("Failed to initialize ONNX Embedding Model: " + e.getMessage(), e);
            }
        } else {
            throw new RuntimeException("Failed to load embedding configuration");
        }
    }

    /**
     * 네이티브 라이브러리 오류 처리
     * ONNX Runtime 초기화 실패 시 OS별 해결 방법 안내
     */
    private void handleNativeLibraryError(Throwable e) {
        String osName = System.getProperty("os.name").toLowerCase();
        String osArch = System.getProperty("os.arch").toLowerCase();
        String javaVersion = System.getProperty("java.version");

        String errorMessage = buildErrorMessage(osName, osArch, javaVersion, e.getMessage());
        log.error(errorMessage);

        throw new RuntimeException("ONNX Runtime 초기화 실패: " + e.getMessage(), e);
    }

    /**
     * OS별 오류 해결 안내 메시지 생성
     */
    private String buildErrorMessage(String osName, String osArch, String javaVersion, String errorDetail) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n================================================\n");
        sb.append("ONNX Runtime 초기화 실패\n");
        sb.append("================================================\n");
        sb.append("운영체제: ").append(System.getProperty("os.name")).append(" (").append(osArch).append(")\n");
        sb.append("Java 버전: ").append(javaVersion).append("\n");
        sb.append("오류: ").append(errorDetail).append("\n\n");

        if (osName.contains("windows")) {
            sb.append(getWindowsSolution(osArch));
        } else if (osName.contains("linux")) {
            sb.append(getLinuxSolution());
        } else if (osName.contains("mac")) {
            sb.append(getMacSolution());
        } else {
            sb.append(getDefaultSolution());
        }

        sb.append("\n추가 정보:\n");
        sb.append("- 지원 플랫폼: Windows x64, Linux x64, macOS x64/ARM64\n");
        sb.append("================================================\n");

        return sb.toString();
    }

    private String getWindowsSolution(String osArch) {
        StringBuilder sb = new StringBuilder();
        sb.append("해결 방법:\n");
        sb.append("1. Visual C++ Redistributable (최신 버전)을 설치하세요.\n");

        if (osArch.contains("aarch64") || osArch.contains("arm64")) {
            sb.append("   ARM64: https://aka.ms/vs/17/release/vc_redist.arm64.exe\n");
        } else if (osArch.contains("x86") && !osArch.contains("64")) {
            sb.append("   x86: https://aka.ms/vs/17/release/vc_redist.x86.exe\n");
        } else {
            sb.append("   x64: https://aka.ms/vs/17/release/vc_redist.x64.exe\n");
        }

        sb.append("   참고: https://learn.microsoft.com/ko-kr/cpp/windows/latest-supported-vc-redist\n");
        sb.append("2. 설치 후 시스템을 재시작하세요.\n");
        sb.append("3. 애플리케이션을 다시 실행하세요.\n");

        return sb.toString();
    }

    private String getLinuxSolution() {
        return """
                해결 방법:
                1. 필요한 시스템 라이브러리를 설치하세요:
                   Ubuntu/Debian: sudo apt-get update && sudo apt-get install -y libgomp1
                   CentOS/RHEL: sudo yum install -y libgomp
                2. 애플리케이션을 다시 실행하세요.
                """;
    }

    private String getMacSolution() {
        return """
                해결 방법:
                1. Xcode Command Line Tools를 설치하세요:
                   xcode-select --install
                2. 애플리케이션을 다시 실행하세요.
                """;
    }

    private String getDefaultSolution() {
        return """
                해결 방법:
                1. ONNX Runtime이 지원하는 플랫폼인지 확인하세요.
                2. 필요한 시스템 라이브러리가 설치되어 있는지 확인하세요.
                """;
    }

    /**
     * ONNX 임베딩 모델 빈
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        return this.embeddingModel;
    }

    /**
     * Ollama 채팅 모델 빈 (비스트리밍)
     */
    @Bean
    public OllamaChatModel chatLanguageModel() {
        log.info("Initializing Ollama Chat Model...");
        log.info("Base URL: {}", ollamaBaseUrl);
        log.info("Model name: {}", ollamaModelName);
        log.info("Temperature: {}", ollamaTemperature);

        return OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(ollamaModelName)
                .temperature(ollamaTemperature)
                .timeout(ollamaTimeout)
                .build();
    }

    /**
     * Ollama 스트리밍 채팅 모델 빈
     */
    @Bean
    public OllamaStreamingChatModel streamingChatLanguageModel() {
        log.info("Initializing Ollama Streaming Chat Model...");

        return OllamaStreamingChatModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(ollamaModelName)
                .temperature(ollamaTemperature)
                .timeout(ollamaTimeout)
                .build();
    }

    /**
     * PGVector 임베딩 저장소 빈
     */
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        log.info("Initializing PGVector Embedding Store...");
        log.info("Host: {}:{}/{}", pgvectorHost, pgvectorPort, pgvectorDatabase);
        log.info("Table name: {}", pgvectorTableName);
        log.info("Dimension: {}", pgvectorDimension);
        log.info("Create table: {}", createTable);

        return PgVectorEmbeddingStore.builder()
                .host(pgvectorHost)
                .port(pgvectorPort)
                .database(pgvectorDatabase)
                .user(pgvectorUsername)
                .password(pgvectorPassword)
                .table(pgvectorTableName)
                .dimension(pgvectorDimension)
                .createTable(createTable)
                .build();
    }
}
