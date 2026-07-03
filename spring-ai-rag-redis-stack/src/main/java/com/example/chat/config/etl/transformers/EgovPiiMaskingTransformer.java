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

    /** 주민등록번호 검증자리(13번째) 가중치. 앞 12자리에 순서대로 곱한다. */
    private static final int[] RRN_WEIGHTS = {2, 3, 4, 5, 6, 7, 8, 9, 2, 3, 4, 5};

    /** 인증키 값 판별용 한글(음절·자모) 탐지 패턴. */
    private static final Pattern HANGUL_PATTERN = Pattern.compile("[가-힣ㄱ-ㅎㅏ-ㅣ]");

    /** 영문자 이외(숫자·기호) 문자 탐지. 자격증명은 숫자·기호를 포함하는 경향이 있다. */
    private static final Pattern NON_LETTER_PATTERN = Pattern.compile("[^A-Za-z]");

    /** 인증키로 인정할 값의 최소 길이. */
    private static final int MIN_SECRET_VALUE_LENGTH = 6;

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
            result = maskRrnNumbers(result);
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

    /** 주민등록번호 후보 중 검증자리(체크섬)까지 통과한 것만 토큰으로 치환한다. */
    private String maskRrnNumbers(String text) {
        Matcher matcher = RRN_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String digits = matcher.group(1) + matcher.group(2); // 13자리(구분자 제외)
            String replacement = isValidRrn(digits) ? rrnToken : matcher.group();
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 카드번호 후보 중 Luhn 검사를 통과한 것만 토큰으로 치환한다.
     *
     * <p>주민등록번호 형태(6-7자리)인 13자리 숫자는 카드로 취급하지 않는다. 이 단계는 RRN
     * 마스킹 이후 실행되므로, 남아 있는 RRN 형태 숫자는 검증자리를 통과하지 못한 값(행정 문서의
     * 관리코드·기안번호 등)이다. 이를 카드로 재분류하면 다시 오탐되므로 제외한다(13자리 카드
     * 오탐 방지 우선).</p>
     */
    private String maskCardNumbers(String text) {
        Matcher matcher = CARD_CANDIDATE_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String candidate = matcher.group();
            String replacement;
            if (RRN_PATTERN.matcher(candidate).matches()) {
                replacement = candidate; // 주민번호 형태 → 카드 오탐 제외
            } else {
                String digits = candidate.replaceAll("[ -]", "");
                replacement = isLuhnValid(digits) ? cardToken : candidate;
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * key=value / key: value 형태에서 값 부분만 토큰으로 치환(키 이름은 보존).
     *
     * <p>값이 실제 자격증명처럼 보일 때만 치환한다. 설명 문서에서 {@code "password: 8자
     * 이상으로 설정하세요"}, {@code "token: JWT 형식으로 발급됩니다"}, {@code "token:
     * production 환경"}처럼 키워드 뒤에 한글 안내문·짧은 단어·순수 영문 단어가 오는 경우를
     * 오탐하지 않도록, 값에 한글이 없고 최소 길이(6자) 이상이며 숫자나 기호를 하나 이상
     * 포함할 때만 마스킹한다.</p>
     */
    private String maskSecrets(String text) {
        Matcher matcher = SECRET_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String replacement;
            if (isSecretLike(matcher.group(2))) {
                String keyPart = text.substring(matcher.start(), matcher.start(2));
                replacement = keyPart + secretToken;
            } else {
                replacement = matcher.group(); // 자격증명으로 보이지 않으면 원문 유지
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 값이 자격증명처럼 보이는지 판정한다. 한글이 없고, 최소 길이 이상이며, 숫자·기호를 하나
     * 이상 포함해야 한다(순수 영문 단어인 안내문 {@code production}·{@code JWT} 등을 배제).
     */
    private boolean isSecretLike(String value) {
        return value.length() >= MIN_SECRET_VALUE_LENGTH
                && !HANGUL_PATTERN.matcher(value).find()
                && NON_LETTER_PATTERN.matcher(value).find();
    }

    /**
     * 주민등록번호 검증자리(체크섬) 검사. 형태만 맞는 임의의 13자리(예: 행정 문서의 관리코드·
     * 기안번호)가 마스킹되는 오탐을 줄인다.
     *
     * <p>앞 12자리에 가중치를 곱해 합한 뒤 {@code (11 - sum % 11) % 10}이 13번째 자리와
     * 같은지 확인한다. 2020년 10월 이후 발급된 무작위 외국인등록번호는 이 검증을 따르지
     * 않으므로 마스킹되지 않을 수 있다.</p>
     */
    private boolean isValidRrn(String digits) {
        if (digits.length() != 13) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < RRN_WEIGHTS.length; i++) {
            sum += (digits.charAt(i) - '0') * RRN_WEIGHTS[i];
        }
        int check = (11 - (sum % 11)) % 10;
        return check == (digits.charAt(12) - '0');
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
