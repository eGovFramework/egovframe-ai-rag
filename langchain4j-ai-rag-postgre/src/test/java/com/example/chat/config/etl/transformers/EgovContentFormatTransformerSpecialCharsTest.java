package com.example.chat.config.etl.transformers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import dev.langchain4j.data.document.Document;

/**
 * 특수문자 정리가 한국어 행정문서의 본문 문자를 지우지 않는지 검증한다.
 *
 * <p>허용 문자표가 한글과 ASCII 만 남기므로 한자, 단위 기호, 원문자 번호, 가운뎃점, 낫표,
 * 통화 기호가 삭제됐다. 수치에서 단위가 사라진 채 색인되면 답변 근거의 의미가 달라진다.</p>
 */
class EgovContentFormatTransformerSpecialCharsTest {

    /** 특수문자 정리만 켠 인스턴스. */
    private EgovContentFormatTransformer cleanSpecialCharsOnly() {
        return build(false, false, true);
    }

    /** 운영 기본 설정 그대로. */
    private EgovContentFormatTransformer productionDefaults() {
        return build(true, true, true);
    }

    private EgovContentFormatTransformer build(boolean normalizeWhitespace, boolean normalizeNewlines,
            boolean cleanSpecialChars) {
        EgovContentFormatTransformer transformer = new EgovContentFormatTransformer();
        ReflectionTestUtils.setField(transformer, "normalizationEnabled", true);
        ReflectionTestUtils.setField(transformer, "removeHtmlTags", true);
        ReflectionTestUtils.setField(transformer, "normalizeWhitespace", normalizeWhitespace);
        ReflectionTestUtils.setField(transformer, "normalizeNewlines", normalizeNewlines);
        ReflectionTestUtils.setField(transformer, "removeCodeBlocks", false);
        ReflectionTestUtils.setField(transformer, "cleanSpecialChars", cleanSpecialChars);
        return transformer;
    }

    @Test
    @DisplayName("특수문자 정리가 단위 기호와 한자와 원문자를 지우지 않는다")
    void cleanSpecialCharsKeepsAdministrativeDocumentCharacters() {
        EgovContentFormatTransformer transformer = cleanSpecialCharsOnly();

        assertThat(text(transformer, "건축면적 100\u33A1 이하, 온도 25\u2103 유지"))
                .isEqualTo("건축면적 100\u33A1 이하, 온도 25\u2103 유지");
        assertThat(text(transformer, "조세(\u79DF\u7A05) 감면 대상"))
                .isEqualTo("조세(\u79DF\u7A05) 감면 대상");
        assertThat(text(transformer, "\u2460 첫째 \u2461 둘째"))
                .isEqualTo("\u2460 첫째 \u2461 둘째");
        assertThat(text(transformer, "담당자\u00B7연락처"))
                .isEqualTo("담당자\u00B7연락처");
        assertThat(text(transformer, "\u300C전자정부법\u300D 제2조"))
                .isEqualTo("\u300C전자정부법\u300D 제2조");
    }

    @Test
    @DisplayName("운영 기본 설정에서도 단위 기호와 한자가 본문에 남는다")
    void administrativeCharactersSurviveUnderProductionDefaults() {
        EgovContentFormatTransformer transformer = productionDefaults();

        assertThat(text(transformer, "건축면적 100\u33A1 이하, 온도 25\u2103 유지"))
                .isEqualTo("건축면적 100\u33A1 이하, 온도 25\u2103 유지");
        assertThat(text(transformer, "조세(\u79DF\u7A05) 감면 대상"))
                .isEqualTo("조세(\u79DF\u7A05) 감면 대상");
        assertThat(text(transformer, "수수료 1,000\u20A9")).isEqualTo("수수료 1,000\u20A9");
    }

    @Test
    @DisplayName("특수문자 정리는 본문이 아닌 문자를 계속 지운다")
    void cleanSpecialCharsStillRemovesNonContentCharacters() {
        EgovContentFormatTransformer transformer = cleanSpecialCharsOnly();

        // 이모지와 사적 사용 영역은 본문이 아니므로 그대로 제거된다
        assertThat(text(transformer, "회의\uD83D\uDE00 결과")).isEqualTo("회의 결과");
        assertThat(text(transformer, "표시\uE000 없음")).isEqualTo("표시 없음");
    }

    private String text(EgovContentFormatTransformer transformer, String content) {
        return transformer.transform(Document.from(content)).text();
    }
}
