package com.example.chat.config.etl.transformers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link EgovContentFormatTransformer}의 내용 정규화 동작을 검증한다.
 *
 * <p>Spring 컨텍스트 없이 @Value 필드를 직접 주입해 결정적으로 동작한다.
 */
class EgovContentFormatTransformerTest {

    /** @Value 설정을 직접 주입한 표준 인스턴스. */
    private EgovContentFormatTransformer transformer(boolean enabled, boolean normalizeWhitespace,
            boolean normalizeNewlines) {
        EgovContentFormatTransformer transformer = new EgovContentFormatTransformer();
        ReflectionTestUtils.setField(transformer, "normalizationEnabled", enabled);
        ReflectionTestUtils.setField(transformer, "removeHtmlTags", false);
        ReflectionTestUtils.setField(transformer, "normalizeWhitespace", normalizeWhitespace);
        ReflectionTestUtils.setField(transformer, "normalizeNewlines", normalizeNewlines);
        ReflectionTestUtils.setField(transformer, "removeCodeBlocks", false);
        ReflectionTestUtils.setField(transformer, "cleanSpecialChars", false);
        return transformer;
    }

    @Test
    @DisplayName("줄바꿈 정규화는 빈 줄을 단일 개행으로 축약하고 문자 n을 삽입하지 않는다")
    void normalizeNewlinesDoesNotInsertLiteralN() {
        EgovContentFormatTransformer transformer = transformer(true, false, true);
        Document doc = new Document("1문단 첫 줄\n\n2문단 첫 줄\n\n\n3문단");

        List<Document> result = transformer.apply(List.of(doc));

        assertThat(result.get(0).getText()).isEqualTo("1문단 첫 줄\n2문단 첫 줄\n3문단");
        assertThat(result.get(0).getText()).doesNotContain("줄n2문단");
    }

    @Test
    @DisplayName("공백 정규화는 연속 스페이스와 탭만 축약하고 개행은 보존한다")
    void normalizeWhitespacePreservesNewlines() {
        EgovContentFormatTransformer transformer = transformer(true, true, false);
        Document doc = new Document("가  나\t\t다\n라   마\t바");

        List<Document> result = transformer.apply(List.of(doc));

        assertThat(result.get(0).getText()).isEqualTo("가 나 다\n라 마 바");
    }

    @Test
    @DisplayName("기본 정규화 설정은 문단 구조를 보존하고 문단 내 연속 공백을 축약한다")
    void defaultNormalizationPreservesParagraphStructure() {
        EgovContentFormatTransformer transformer = transformer(true, true, true);
        Document doc = new Document("1문단  첫 줄\n\n2문단\t\t첫 줄\n\n\n3문단   첫 줄");

        List<Document> result = transformer.apply(List.of(doc));

        assertThat(result.get(0).getText()).isEqualTo("1문단 첫 줄\n2문단 첫 줄\n3문단 첫 줄");
    }

    @Test
    @DisplayName("CRLF 입력도 LF 기준으로 줄바꿈 정규화한다")
    void normalizeCrLfToLf() {
        EgovContentFormatTransformer transformer = transformer(true, false, true);
        Document doc = new Document("가\r\n\r\n나");

        List<Document> result = transformer.apply(List.of(doc));

        assertThat(result.get(0).getText()).isEqualTo("가\n나");
    }

    @Test
    @DisplayName("비활성화 시 원본 문서를 동일 인스턴스로 반환한다")
    void disabledReturnsSameDocument() {
        EgovContentFormatTransformer transformer = transformer(false, true, true);
        Document doc = new Document("가  나\n\n다");

        List<Document> result = transformer.apply(List.of(doc));

        assertThat(result).containsExactly(doc);
        assertThat(result.get(0)).isSameAs(doc);
    }

    @Test
    @DisplayName("정규화로 내용이 바뀌면 기존 메타데이터와 정규화 정보를 보존한다")
    void changedContentKeepsMetadataAndAddsNormalizationMetadata() {
        EgovContentFormatTransformer transformer = transformer(true, true, false);
        Document doc = new Document("가  나", new HashMap<>(Map.of("source", "manual")));

        List<Document> result = transformer.apply(List.of(doc));

        assertThat(result.get(0)).isNotSameAs(doc);
        assertThat(result.get(0).getText()).isEqualTo("가 나");
        assertThat(result.get(0).getMetadata()).containsEntry("source", "manual");
        assertThat(result.get(0).getMetadata()).containsEntry("original_length", 4);
        assertThat(result.get(0).getMetadata()).containsEntry("normalized_length", 3);
        assertThat(result.get(0).getMetadata()).containsEntry("custom_normalization_applied", true);
    }

    @Test
    @DisplayName("공백 정규화는 비줄바꿈 공백과 전각 공백도 일반 공백으로 바꾼다")
    void normalizeWhitespaceHandlesUnicodeSpaces() {
        EgovContentFormatTransformer transformer = transformer(true, true, false);

        // U+00A0 비줄바꿈 공백, U+3000 전각 공백, U+202F 좁은 비줄바꿈 공백
        assertThat(text(transformer, "기관\u00A0문서")).isEqualTo("기관 문서");
        assertThat(text(transformer, "제1조\u3000목적")).isEqualTo("제1조 목적");
        assertThat(text(transformer, "담당자\u202F연락처")).isEqualTo("담당자 연락처");
    }

    @Test
    @DisplayName("공백 정규화가 꺼져 있으면 유니코드 공백도 그대로 남는다")
    void unicodeSpacesAreKeptWhenNormalizationIsOff() {
        EgovContentFormatTransformer transformer = transformer(true, false, false);

        assertThat(text(transformer, "기관\u00A0문서")).isEqualTo("기관\u00A0문서");
    }

    @Test
    @DisplayName("운영 기본 설정에서 유니코드 공백이 삭제되지 않고 낱말이 분리된 채 남는다")
    void unicodeSpacesSurviveAsSeparatorUnderProductionDefaults() {
        EgovContentFormatTransformer transformer = productionDefaults();

        // 공백 정규화가 잡지 못하면 뒤이은 특수문자 정리가 이 문자들을 지워 앞뒤 낱말이 붙는다
        assertThat(text(transformer, "기관\u00A0문서")).isEqualTo("기관 문서");
        assertThat(text(transformer, "제1조\u3000목적")).isEqualTo("제1조 목적");
        assertThat(text(transformer, "담당자\u202F연락처")).isEqualTo("담당자 연락처");
    }

    /** 운영 기본 설정 그대로. 특수문자 정리까지 켠 상태다. */
    private EgovContentFormatTransformer productionDefaults() {
        EgovContentFormatTransformer transformer = new EgovContentFormatTransformer();
        ReflectionTestUtils.setField(transformer, "normalizationEnabled", true);
        ReflectionTestUtils.setField(transformer, "removeHtmlTags", true);
        ReflectionTestUtils.setField(transformer, "normalizeWhitespace", true);
        ReflectionTestUtils.setField(transformer, "normalizeNewlines", true);
        ReflectionTestUtils.setField(transformer, "removeCodeBlocks", false);
        ReflectionTestUtils.setField(transformer, "cleanSpecialChars", true);
        return transformer;
    }

    private String text(EgovContentFormatTransformer transformer, String content) {
        return transformer.apply(List.of(new Document(content))).get(0).getText();
    }
}
