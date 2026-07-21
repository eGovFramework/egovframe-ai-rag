package com.example.chat.util;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 프롬프트 인젝션 탐지를 위한 문자열 처리 유틸리티입니다.
 */
public final class EgovInjectionGuardSupport {

    private static final Pattern INTER_CHARACTER_WHITESPACE =
            Pattern.compile("(?<=[\\p{L}\\p{N}])\\s+(?=[\\p{L}\\p{N}])");
    private static final Pattern REPEATED_CHARACTER = Pattern.compile("(.)\\1+");

    private EgovInjectionGuardSupport() {
    }

    /**
     * 원본 질의를 변경하지 않고 판정용 사본에서 인젝션 패턴을 검사합니다.
     *
     * @param rawQuery 원본 질의
     * @param lexicon 탐지 패턴 목록
     * @return 탐지 패턴이 포함되어 있으면 {@code true}
     */
    public static boolean isInjectionAttempt(String rawQuery, List<String> lexicon) {
        String normalizedQuery = normalizeForDetection(rawQuery);
        if (normalizedQuery.isEmpty() || lexicon == null) {
            return false;
        }

        return lexicon.stream()
                .map(EgovInjectionGuardSupport::normalizeForDetection)
                .filter(pattern -> !pattern.isEmpty())
                .anyMatch(normalizedQuery::contains);
    }

    /**
     * 탐지에 사용할 판정용 문자열을 정규화합니다.
     *
     * @param s 정규화할 문자열
     * @return 정규화된 판정용 문자열
     */
    public static String normalizeForDetection(String s) {
        if (s == null) {
            return "";
        }

        StringBuilder filtered = new StringBuilder(s.length());
        s.codePoints()
                .filter(codePoint -> !isRemovedCharacter(codePoint))
                .forEach(filtered::appendCodePoint);

        String normalized = Normalizer.normalize(filtered, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        normalized = INTER_CHARACTER_WHITESPACE.matcher(normalized).replaceAll("");
        return REPEATED_CHARACTER.matcher(normalized).replaceAll("$1");
    }

    private static boolean isRemovedCharacter(int codePoint) {
        // 유니코드 Cf(FORMAT) 부류 전체를 제거해 제로폭 공백(U+200B~U+200D)·BOM(U+FEFF)뿐
        // 아니라 WORD JOINER(U+2060)·방향 제어문자(U+200E/U+200F) 등을 이용한 우회를 흡수한다.
        return Character.getType(codePoint) == Character.FORMAT
                || Character.isISOControl(codePoint);
    }
}
