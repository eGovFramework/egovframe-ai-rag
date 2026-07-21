package com.example.chat.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EgovJsonPromptTemplatesTest {

    @Test
    @DisplayName("createTechnologyInfoPrompt: 질의 문자열이 프롬프트에 포함된다")
    void createTechnologyInfoPrompt_containsQuery() {
        String query = "Spring Boot란 무엇인가?";
        String prompt = EgovJsonPromptTemplates.createTechnologyInfoPrompt(query);

        assertThat(prompt).contains(query);
    }

    @Test
    @DisplayName("createTechnologyInfoPrompt: JSON 응답 형식 키가 모두 포함된다")
    void createTechnologyInfoPrompt_containsJsonFields() {
        String prompt = EgovJsonPromptTemplates.createTechnologyInfoPrompt("any");

        assertThat(prompt)
                .contains("\"name\"")
                .contains("\"category\"")
                .contains("\"description\"")
                .contains("\"features\"")
                .contains("\"useCases\"")
                .contains("\"complexity\"");
    }

    @Test
    @DisplayName("createTechnologyInfoPrompt: 빈 문자열 질의도 처리된다")
    void createTechnologyInfoPrompt_emptyQuery() {
        String prompt = EgovJsonPromptTemplates.createTechnologyInfoPrompt("");

        assertThat(prompt).isNotBlank();
    }

    @Test
    @DisplayName("createTechnologyInfoPrompt: 서로 다른 질의는 서로 다른 프롬프트를 반환한다")
    void createTechnologyInfoPrompt_differentQueriesProduceDifferentPrompts() {
        String prompt1 = EgovJsonPromptTemplates.createTechnologyInfoPrompt("Kubernetes");
        String prompt2 = EgovJsonPromptTemplates.createTechnologyInfoPrompt("Docker");

        assertThat(prompt1).isNotEqualTo(prompt2);
    }

    @Test
    @DisplayName("createTechnologyInfoPrompt: 동일 질의는 동일 프롬프트를 반환한다")
    void createTechnologyInfoPrompt_sameQueryProducesSamePrompt() {
        String query = "eGovFrame";
        String prompt1 = EgovJsonPromptTemplates.createTechnologyInfoPrompt(query);
        String prompt2 = EgovJsonPromptTemplates.createTechnologyInfoPrompt(query);

        assertThat(prompt1).isEqualTo(prompt2);
    }
}
