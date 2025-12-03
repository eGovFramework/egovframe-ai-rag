package com.example.chat.service.impl;

import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.converter.StructuredOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.chat.context.SessionContext;
import com.example.chat.config.EgovRagConfig;
import com.example.chat.config.rag.transformers.EgovCompressionQueryTransformer;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import com.example.chat.response.TechnologyResponse;
import com.example.chat.service.EgovSessionAwareChatService;
import com.example.chat.util.EgovThinkTagOutputConverter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@Service
@RequiredArgsConstructor
public class EgovSessionAwareChatServiceImpl extends EgovAbstractServiceImpl implements EgovSessionAwareChatService {

    private final ChatClient ollamaChatClient;
    private final MessageChatMemoryAdvisor messageChatMemoryAdvisor;
    private final EgovCompressionQueryTransformer compressionTransformer;
    private final VectorStoreDocumentRetriever vectorStoreDocumentRetriever;

    @Value("${rag.enable-query-compression:true}")
    private boolean enableQueryCompression;
    
    // StructuredOutputConverter 인스턴스들 (<think> 태그 처리)
    private final StructuredOutputConverter<TechnologyResponse> technologyOutputConverter = 
        EgovThinkTagOutputConverter.of(TechnologyResponse.class);

    /**
     * 세션별 RAG 기반 스트리밍 응답 생성
     */
    @Override
    public Flux<ChatResponse> streamRagResponse(String query, String model) {
        String sessionId = SessionContext.getCurrentSessionId();
        log.info("세션별 RAG 기반 스트리밍 질의 수신: {}, 모델: {}, 세션: {}", query, model, sessionId);

        try {
            log.debug("세션 {} RAG 응답 생성 시작", sessionId);
            validateSessionId(sessionId);

            // 원본 질문으로 ChatClient RequestSpec 생성 (사용자 메시지로 저장)
            ChatClientRequestSpec requestSpec = createRequestSpec(query, model);

            // RAG 어드바이저 생성 (질문 압축 설정값에 따라 동작 결정)
            log.info("RAG 어드바이저 생성 시작 - 세션: {}, 원본 질문: '{}', 질문 압축: {}", sessionId, query, enableQueryCompression);
            Advisor ragAdvisor = EgovRagConfig.createRagAdvisor(sessionId, compressionTransformer, vectorStoreDocumentRetriever, enableQueryCompression);
            log.info("RAG 어드바이저 생성 완료 - 세션: {}", sessionId);

            log.info("RAG 스트리밍 시작 - 세션: {}, 원본 질문: '{}'", sessionId, query);

            // ChatMemory 어드바이저와 RAG 어드바이저 적용
            // - MessageChatMemoryAdvisor: 원본 질문을 사용자 메시지로 저장
            // - RAG Advisor: 내부 QueryTransformer에서 히스토리 압축 후 문서 검색
            return requestSpec
                    .advisors(messageChatMemoryAdvisor, ragAdvisor)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                    .stream()
                    .chatResponse();

        } catch (Exception e) {
            log.error("세션별 RAG 스트리밍 응답 생성 중 오류 발생 - 세션: {}", sessionId, e);
            return Flux.error(e);
        }
    }

    /**
     * 세션별 일반 스트리밍 응답 생성
     */
    @Override
    public Flux<ChatResponse> streamSimpleResponse(String query, String model) {
        String sessionId = SessionContext.getCurrentSessionId();
        log.info("세션별 일반 스트리밍 질의 수신: {}, 모델: {}, 세션: {}", query, model, sessionId);

        try {
            log.debug("세션 {} 일반 응답 생성 시작", sessionId);

            // 원본 질문으로 ChatClient RequestSpec 생성 (RAG 없으므로 압축 불필요)
            ChatClient.ChatClientRequestSpec requestSpec = createRequestSpec(query, model);

            // ChatMemory 어드바이저만 적용 (RAG 없음)
            // MessageChatMemoryAdvisor가 자동으로 히스토리를 제공하므로 별도 압축 불필요
            return requestSpec
                    .advisors(messageChatMemoryAdvisor)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                    .stream()
                    .chatResponse();

        } catch (Exception e) {
            log.error("세션별 일반 스트리밍 응답 생성 중 오류 발생 - 세션: {}", sessionId, e);
            return Flux.error(e);
        }
    }
    
    /**
     * JSON 구조화된 출력 - 기술 정보
     *
     * @param query 사용자 질의
     * @return JSON 구조화된 기술 정보 응답
     */
    @Override
    public TechnologyResponse getTechnologyInfoAsJson(String query) {
        log.info("기술 정보 JSON 응답 생성: {}", query);

        try {
            // 커스텀 StructuredOutputConverter 사용하여 <think> 태그 처리
            return ollamaChatClient.prompt()
                    .user(u -> u.text("다음 질문에 대해 기술 정보를 제공해주세요: {query}")
                               .param("query", query))
                    .call()
                    .entity(technologyOutputConverter);

        } catch (Exception e) {
            log.error("기술 정보 JSON 응답 생성 중 오류 발생", e);
            return new TechnologyResponse("알 수 없음", "알 수 없음", "오류가 발생했습니다", null, null);
        }
    }

    /**
     * ChatClient RequestSpec을 생성하는 공통 메서드
     *
     * @param query 사용자 질문
     * @param model 모델 이름 (null 가능)
     * @return 구성된 ChatClient.ChatClientRequestSpec
     */
    private ChatClient.ChatClientRequestSpec createRequestSpec(String query, String model) {
        ChatClient.ChatClientRequestSpec requestSpec = ollamaChatClient.prompt().user(query);

        if (model != null && !model.trim().isEmpty()) {
            requestSpec = requestSpec.options(ChatOptions.builder()
                .model(model)
                .temperature(0.3)
                .build());
            log.debug("지정된 모델로 응답 생성: {}", model);
        } else {
            log.debug("기본 모델로 응답 생성");
        }

        return requestSpec;
    }

    /**
     * 세션 ID 검증 및 경고 로깅
     *
     * @param sessionId 검증할 세션 ID
     */
    private void validateSessionId(String sessionId) {
        if ("default".equals(sessionId)) {
            log.warn("세션 ID가 'default'로 설정됨 - 세션 관리에 문제가 있을 수 있습니다");
        }
    }
}
