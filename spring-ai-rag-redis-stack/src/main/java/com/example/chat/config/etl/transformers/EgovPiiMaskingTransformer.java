package com.example.chat.config.etl.transformers;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;

import lombok.extern.slf4j.Slf4j;

/**
 * 색인 저장 단계에서 민감정보(PII)를 선택적으로 마스킹하는 문서 변환기.
 *
 * <p>RAG 색인 파이프라인은 원문을 벡터 저장소에 적재하므로, 원문에 포함된 주민등록번호·
 * 카드번호 등 고위험 식별자가 그대로 색인·검색 응답에 노출될 수 있다. 본 변환기는 그러한
 * 식별자만 선택적으로 토큰으로 치환한다.
 *
 * <p>설계 원칙(노이즈·검색 품질 보호):
 * <ul>
 *   <li><b>기본 비활성화</b>: {@code enabled}가 false면 원본을 그대로 반환한다.</li>
 *   <li><b>선택적 적용</b>: 유출 위험이 크고 검색 기여가 낮은 항목(주민등록번호·카드번호·
 *       인증키)만 대상으로 한다. 전화번호·이메일·담당자명·주소처럼 업무 검색에 필요할 수
 *       있는 정보는 일괄 마스킹하지 않는다.</li>
 *   <li><b>오탐 억제</b>: 카드번호는 Luhn 검사를 통과한 경우에만 치환한다. 계좌번호는
 *       기관별 형식이 제각각이라 내장 패턴의 오탐이 크므로, 운영자가 정규식을 지정한 경우
 *       에만 동작한다(기본 미지정).</li>
 * </ul>
 *
 * <p>본 클래스는 Spring 컨텍스트 없이도 단위 테스트가 가능하도록 모든 설정을 생성자로
 * 주입받는다. 빈 등록은 {@code EgovETLPipelineConfig}에서 {@code @Value}로 해석한 값을
 * 전달한다.
 */
@Slf4j
public class EgovPiiMaskingTransformer implements DocumentTransformer {

    /** 주민등록번호/외국인등록번호: 생년월일 6자리 + 성별 1자리(1~8) + 6자리. */
    private static final Pattern RRN_PATTERN =
            Pattern.compile("(?<![0-9])(\\d{6})[- ]?([1-8]\\d{6})(?![0-9])");

    /** 카드번호 후보: 13~19자리 숫자(자리 사이에만 공백/하이픈 허용). Luhn 검사로 최종 확정. */
    private static final Pattern CARD_CANDIDATE_PATTERN =
            Pattern.compile("(?<![0-9])\\d(?:[ -]?\\d){12,18}(?![0-9])");

    /** 인증키/비밀키: key=value 또는 key: value 형태의 값만 치환. */
    private static final Pattern SECRET_PATTERN = Pattern.compile(
            "(?i)\\b(api[_-]?key|secret(?:[_-]?key)?|access[_-]?key|private[_-]?key|"
                    + "password|passwd|pwd|token|authorization)\\b\\s*[:=]\\s*[\"']?([^\\s\"',]+)");

    private final boolean enabled;
    private final boolean maskRrn;
    private final boolean maskCard;
    private final boolean maskSecret;
    private final String rrnToken;
    private final String cardToken;
    private final String secretToken;
    private final String accountToken;
    private final Pattern accountPattern;

    /**
     * @param enabled        마스킹 활성화 여부(false면 원본 그대로 반환)
     * @param maskRrn        주민등록번호 마스킹 여부
     * @param maskCard       카드번호(Luhn 통과) 마스킹 여부
     * @param maskSecret     인증키/비밀키 값 마스킹 여부
     * @param rrnToken       주민등록번호 치환 토큰
     * @param cardToken      카드번호 치환 토큰
     * @param secretToken    인증키 값 치환 토큰
     * @param accountToken   계좌번호 치환 토큰
     * @param accountRegex   계좌번호 정규식(비어 있으면 계좌 마스킹 비활성)
     */
    public EgovPiiMaskingTransformer(boolean enabled, boolean maskRrn, boolean maskCard,
            boolean maskSecret, String rrnToken, String cardToken, String secretToken,
            String accountToken, String accountRegex) {
        this.enabled = enabled;
        this.maskRrn = maskRrn;
        this.maskCard = maskCard;
        this.maskSecret = maskSecret;
        this.rrnToken = rrnToken;
        this.cardToken = cardToken;
        this.secretToken = secretToken;
        this.accountToken = accountToken;
        this.accountPattern = (accountRegex == null || accountRegex.isBlank())
                ? null : Pattern.compile(accountRegex);
    }

    @Override
    public List<Document> apply(List<Document> documents) {
        if (!enabled) {
            // 기본 비활성이 정상 운영 경로이므로 매 배치 INFO 로그 노이즈를 피한다.
            log.debug("PII 마스킹이 비활성화되어 있습니다. 원본 문서를 그대로 반환합니다.");
            return documents;
        }

        log.info("PII 마스킹 시작: {}개 문서 (주민번호: {}, 카드: {}, 인증키: {}, 계좌: {})",
                documents.size(), maskRrn, maskCard, maskSecret, accountPattern != null);

        return documents.stream().map(this::maskDocument).toList();
    }

    private Document maskDocument(Document document) {
        String original = document.getText();
        String masked = mask(original);

        if (original.equals(masked)) {
            return document;
        }

        document.getMetadata().put("pii_masking_applied", true);
        return new Document(document.getId(), masked, document.getMetadata());
    }

    /**
     * 문자열에 활성화된 PII 규칙을 순서대로 적용한다.
     * 패키지 가시성으로 노출하여 단위 테스트가 직접 호출한다.
     */
    String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String result = text;
        if (maskRrn) {
            result = RRN_PATTERN.matcher(result).replaceAll(Matcher.quoteReplacement(rrnToken));
        }
        if (maskCard) {
            result = maskCardNumbers(result);
        }
        if (accountPattern != null) {
            result = accountPattern.matcher(result).replaceAll(Matcher.quoteReplacement(accountToken));
        }
        if (maskSecret) {
            result = maskSecrets(result);
        }
        return result;
    }

    /** 카드번호 후보 중 Luhn 검사를 통과한 것만 토큰으로 치환한다. */
    private String maskCardNumbers(String text) {
        Matcher matcher = CARD_CANDIDATE_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String candidate = matcher.group();
            String digits = candidate.replaceAll("[ -]", "");
            String replacement = isLuhnValid(digits) ? cardToken : candidate;
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /** key=value / key: value 형태에서 값 부분만 토큰으로 치환(키 이름은 보존). */
    private String maskSecrets(String text) {
        Matcher matcher = SECRET_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String keyPart = text.substring(matcher.start(), matcher.start(2));
            matcher.appendReplacement(sb,
                    Matcher.quoteReplacement(keyPart + secretToken));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /** Luhn(모듈러스 10) 검사. 13~19자리 카드번호의 오탐을 줄인다. */
    private boolean isLuhnValid(String digits) {
        int len = digits.length();
        if (len < 13 || len > 19) {
            return false;
        }
        int sum = 0;
        boolean doubleDigit = false;
        for (int i = len - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (doubleDigit) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            doubleDigit = !doubleDigit;
        }
        return sum % 10 == 0;
    }
}
