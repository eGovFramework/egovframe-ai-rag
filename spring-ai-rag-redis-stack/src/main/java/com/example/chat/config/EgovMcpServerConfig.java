package com.example.chat.config;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.chat.tool.EgovRagToolService;

/**
 * MCP(Model Context Protocol) 서버 설정.
 *
 * <p>{@link EgovRagToolService}의 {@code @Tool} 메서드들을 MCP 도구로 등록한다.
 * Spring AI MCP Server 스타터가 이 {@link ToolCallbackProvider}를 감지하여 MCP 클라이언트에
 * 도구 목록(egov_rag_ask, egov_doc_search, egov_index_status, egov_models)을 노출한다.</p>
 *
 * <p>전송 방식은 {@code application.yml}의 {@code spring.ai.mcp.server} 설정을 따른다
 * (기본: WebMVC 기반 SSE).</p>
 */
@Configuration
public class EgovMcpServerConfig {

    @Bean
    public ToolCallbackProvider egovRagTools(EgovRagToolService egovRagToolService) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(egovRagToolService)
                .build();
    }

}
