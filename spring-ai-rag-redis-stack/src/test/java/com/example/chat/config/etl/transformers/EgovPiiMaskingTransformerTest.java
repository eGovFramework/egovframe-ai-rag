package com.example.chat.config.etl.transformers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

/**
 * {@link EgovPiiMaskingTransformer}의 선택적 마스킹 동작을 검증한다.
 *
 * <p>Spring 컨텍스트 없이 생성자 주입만으로 결정적으로 동작한다.
 */
class EgovPiiMaskingTransformerTest {

    /** 기본 토큰을 사용하고 계좌 정규식은 미지정(비활성)한 표준 인스턴스. */
    private EgovPiiMaskingTransformer transformer(boolean enabled) {
        return new EgovPiiMaskingTransformer(enabled, true, true, true,
                "[RRN]", "[CARD]", "[SECRET]", "[ACCOUNT]", "");
    }

    @Test
    @DisplayName("비활성화 시 원본 문서를 그대로 반환한다")
    void disabled_returnsOriginal() {
        EgovPiiMaskingTransformer t = transformer(false);
        Document doc = new Document("주민번호 900101-1234567 입니다.");

        List<Document> result = t.apply(List.of(doc));

        assertThat(result).containsExactly(doc);
        assertThat(result.get(0).getText()).contains("900101-1234567");
    }

    @Test
    @DisplayName("검증자리(체크섬)를 통과한 주민등록번호를 토큰으로 치환한다")
    void masksResidentRegistrationNumber() {
        EgovPiiMaskingTransformer t = transformer(true);

        // 900101-1234568 은 검증자리까지 유효한 주민등록번호
        assertThat(t.mask("문의자 900101-1234568 확인")).isEqualTo("문의자 [RRN] 확인");
        assertThat(t.mask("구분자 없는 9001011234568 도")).isEqualTo("구분자 없는 [RRN] 도");
    }

    @Test
    @DisplayName("검증자리가 틀린 13자리(관리코드 등)는 기본 설정에서 RRN·CARD 어느 쪽으로도 마스킹하지 않는다")
    void doesNotMaskInvalidChecksumRrn() {
        // 전 규칙 활성(RRN·CARD 포함) 기본 설정으로 end-to-end 검증.
        // 850302-1234567 은 형태는 주민번호와 같으나 검증자리가 틀리며(관리코드·기안번호),
        // 주민번호 형태이므로 카드(Luhn) 규칙에서도 제외되어 원문이 그대로 유지된다.
        EgovPiiMaskingTransformer t = transformer(true);

        assertThat(t.mask("관리코드 850302-1234567 참조")).isEqualTo("관리코드 850302-1234567 참조");
    }

    @Test
    @DisplayName("Luhn 검사를 통과한 카드번호만 마스킹한다")
    void masksOnlyLuhnValidCardNumbers() {
        EgovPiiMaskingTransformer t = transformer(true);

        // 4111 1111 1111 1111 = 유효한 Luhn
        assertThat(t.mask("카드 4111-1111-1111-1111 결제")).isEqualTo("카드 [CARD] 결제");
        // 마지막 자리만 바꾼 무효 Luhn → 치환하지 않음
        assertThat(t.mask("카드 4111-1111-1111-1112 결제")).contains("4111-1111-1111-1112");
    }

    @Test
    @DisplayName("인증키/비밀키는 값만 치환하고 키 이름은 보존한다")
    void masksSecretValueKeepsKey() {
        EgovPiiMaskingTransformer t = transformer(true);

        assertThat(t.mask("api_key=AKIA1234567890")).isEqualTo("api_key=[SECRET]");
        assertThat(t.mask("password: s3cr3tP@ss")).isEqualTo("password: [SECRET]");
        // 6자·숫자 포함 → 자격증명으로 인정
        assertThat(t.mask("pwd=abc123")).isEqualTo("pwd=[SECRET]");
    }

    @Test
    @DisplayName("설명 문서의 키워드 뒤 한글 안내문·짧은 단어·순수 영문 단어는 인증키로 오탐하지 않는다")
    void doesNotMaskSecretLikeProse() {
        EgovPiiMaskingTransformer t = transformer(true);

        // 값이 한글 안내문 → 마스킹하지 않음
        assertThat(t.mask("password: 대소문자를 포함한 8자 이상으로 설정하세요"))
                .isEqualTo("password: 대소문자를 포함한 8자 이상으로 설정하세요");
        assertThat(t.mask("api_key: 개발자 포털에서 신청 후 발급"))
                .isEqualTo("api_key: 개발자 포털에서 신청 후 발급");
        // 값이 짧은 단어 → 마스킹하지 않음
        assertThat(t.mask("token: JWT 형식으로 발급됩니다"))
                .isEqualTo("token: JWT 형식으로 발급됩니다");
        // 값이 숫자·기호 없는 순수 영문 단어 → 마스킹하지 않음
        assertThat(t.mask("token: production 환경에 배포"))
                .isEqualTo("token: production 환경에 배포");
    }

    @Test
    @DisplayName("전화번호·이메일은 마스킹하지 않는다(검색 품질 보존)")
    void doesNotMaskPhoneOrEmail() {
        EgovPiiMaskingTransformer t = transformer(true);
        String text = "담당자 010-1234-5678, hong@example.go.kr 로 문의";

        assertThat(t.mask(text)).isEqualTo(text);
    }

    @Test
    @DisplayName("계좌 정규식 미지정 시 계좌 마스킹은 비활성, 지정 시 동작한다")
    void accountMaskingDrivenByRegex() {
        EgovPiiMaskingTransformer off = transformer(true);
        assertThat(off.mask("계좌 123-456-789012")).contains("123-456-789012");

        EgovPiiMaskingTransformer on = new EgovPiiMaskingTransformer(true, false, false, false,
                "[RRN]", "[CARD]", "[SECRET]", "[ACCOUNT]", "\\b\\d{3}-\\d{3}-\\d{6}\\b");
        assertThat(on.mask("계좌 123-456-789012")).isEqualTo("계좌 [ACCOUNT]");
    }

    @Test
    @DisplayName("변경이 없으면 동일 Document 인스턴스를 반환하고, 변경 시 메타데이터 플래그를 남긴다")
    void metadataFlagOnlyWhenChanged() {
        EgovPiiMaskingTransformer t = transformer(true);

        Document clean = new Document("민감정보 없는 일반 문서");
        Document masked = new Document("주민번호 900101-1234568");

        List<Document> result = t.apply(List.of(clean, masked));

        assertThat(result.get(0)).isSameAs(clean);
        assertThat(result.get(0).getMetadata()).doesNotContainKey("pii_masking_applied");
        assertThat(result.get(1).getText()).isEqualTo("주민번호 [RRN]");
        assertThat(result.get(1).getMetadata()).containsEntry("pii_masking_applied", true);
    }

    @Test
    @DisplayName("null/빈 문자열에도 안전하다")
    void nullAndEmptySafe() {
        EgovPiiMaskingTransformer t = transformer(true);

        assertThat(t.mask(null)).isNull();
        assertThat(t.mask("")).isEmpty();
    }
}
