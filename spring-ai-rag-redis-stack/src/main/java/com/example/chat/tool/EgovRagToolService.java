package com.example.chat.tool;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import com.example.chat.config.EgovRagConfig;
import com.example.chat.response.DocumentStatusResponse;
import com.example.chat.service.EgovDocumentService;
import com.example.chat.service.EgovOllamaModelService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 표준프레임워크 RAG 기능을 MCP(Model Context Protocol) 도구로 노출하는 서비스.
 *
 * <p>외부 AI 클라이언트(Claude Desktop, IDE 에이전트, egovframe-vscode-initializr 등)가
 * 표준프레임워크 지식베이스를 표준 도구 호출로 사용할 수 있도록, 기존 RAG/문서/모델 서비스를
 * {@link Tool} 메서드로 래핑한다. 각 메서드는 동기 응답(String)을 반환하여 MCP 도구 규약에 맞춘다.</p>
 *
 * <p>이 클래스는 기존 빈({@link ChatClient}, {@link VectorStoreDocumentRetriever},
 * {@link EgovDocumentService}, {@link EgovOllamaModelService})을 재사용하며 별도 상태를 두지 않는다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EgovRagToolService {

    private final ChatClient chatClient;
    private final VectorStoreDocumentRetriever documentRetriever;
    private final EgovDocumentService documentService;
    private final EgovOllamaModelService modelService;

    /**
     * 표준프레임워크 지식베이스를 근거로 질문에 답한다(RAG 증강 질의).
     */
    @Tool(name = "egov_rag_ask",
            description = "전자정부 표준프레임워크(eGovFrame) 지식베이스(인덱싱된 문서)를 검색해 근거로 삼아 질문에 한국어로 답한다. "
                    + "표준프레임워크 실행환경/공통컴포넌트/사용법 등 프레임워크 관련 질문에 사용한다.")
    public String egovRagAsk(
            @ToolParam(description = "표준프레임워크에 대한 자연어 질문") String question) {
        log.info("[MCP] egov_rag_ask: {}", question);
        Advisor ragAdvisor = EgovRagConfig.createRagAdvisor(documentRetriever);
        String answer = chatClient.prompt()
                .user(question)
                .advisors(ragAdvisor)
                .call()
                .content();
        return answer != null ? answer : "(빈 응답)";
    }

    /**
     * 질문과 의미적으로 유사한 표준프레임워크 문서 청크를 검색해 반환한다(검색 전용, 답변 생성 없음).
     */
    @Tool(name = "egov_doc_search",
            description = "전자정부 표준프레임워크 지식베이스에서 질문과 의미적으로 가장 유사한 문서 청크를 검색해 원문을 반환한다. "
                    + "LLM 답변 없이 근거 원문만 필요할 때(에이전트 grounding, 인용) 사용한다.")
    public String egovDocSearch(
            @ToolParam(description = "검색할 질의문") String query) {
        log.info("[MCP] egov_doc_search: {}", query);
        List<Document> documents = documentRetriever.retrieve(new Query(query));
        if (documents.isEmpty()) {
            return "검색 결과 없음(유사도 임계값 미달 또는 관련 문서 없음).";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("검색된 문서 청크 ").append(documents.size()).append("건:\n\n");
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            Object source = doc.getMetadata().get("source");
            sb.append("## #").append(i + 1);
            if (source != null) {
                sb.append(" (").append(source).append(")");
            }
            sb.append("\n").append(doc.getText()).append("\n\n");
        }
        return sb.toString();
    }

    /**
     * 지식베이스 인덱싱 현황(처리/문서 수 등)을 반환한다.
     */
    @Tool(name = "egov_index_status",
            description = "표준프레임워크 지식베이스의 인덱싱 현황(처리 중 여부, 처리된 청크 수, 총 문서 수, 변경 문서 수, 문서 보유 여부)을 반환한다.")
    public String egovIndexStatus() {
        log.info("[MCP] egov_index_status");
        DocumentStatusResponse s = documentService.getStatusResponse();
        return String.format(
                "처리중=%b, 처리된청크=%d, 총문서=%d, 변경문서=%d, 문서보유=%b",
                s.processing(), s.processedCount(), s.totalCount(), s.changedCount(), s.hasDocuments());
    }

    /**
     * 사용 가능한 LLM(Ollama) 모델 목록을 반환한다.
     */
    @Tool(name = "egov_models",
            description = "현재 사용 가능한 LLM(Ollama) 모델 목록과 가용 여부를 반환한다. egov_rag_ask 호출 시 어떤 모델을 쓸 수 있는지 확인할 때 사용한다.")
    public String egovModels() {
        log.info("[MCP] egov_models");
        if (!modelService.isOllamaAvailable()) {
            return "Ollama 사용 불가(서비스 미기동 또는 연결 실패).";
        }
        List<String> models = modelService.getInstalledModels();
        return models.isEmpty() ? "설치된 모델 없음." : "사용 가능 모델: " + String.join(", ", models);
    }

}
