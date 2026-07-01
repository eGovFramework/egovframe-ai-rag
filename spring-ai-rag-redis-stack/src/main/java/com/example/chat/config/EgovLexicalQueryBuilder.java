package com.example.chat.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * RediSearch 어휘(lexical) 질의 문자열 빌더
 *
 * <p>사용자 질의를 공백 기준 어절로 분해해 RediSearch {@code FT.SEARCH} 질의를
 * 단계적으로 생성한다. RediSearch 기본 토크나이저는 한글을 형태소가 아닌
 * 공백/문장부호 단위 어절로 색인하므로, 다음 순서로 회수율을 높인다.</p>
 *
 * <ol>
 *   <li>정확매칭: {@code @content:(어절1 어절2 ...)} — 어절 전체 일치</li>
 *   <li>접두매칭: {@code @content:(어절1* | 어절2* | ...)} — 어절 시작 일치</li>
 *   <li>중위매칭: {@code @content:(*어절1* | *어절2* | ...)} — 어절 부분 포함</li>
 * </ol>
 *
 * <p>외부 의존성이 없는 순수 함수 모음으로, 동일 입력에 대해 항상 동일한
 * 질의 문자열을 반환한다. RediSearch 특수문자는 이스케이프한다.</p>
 */
public final class EgovLexicalQueryBuilder {

    /** RediSearch 질의 구문에서 의미를 갖는 특수문자(이스케이프 대상). */
    private static final String SPECIAL_CHARS = ",.<>{}[]\"':;!@#$%^&*()-+=~|/\\ ";

    /** 어절로 인정할 최소 길이. 값 1은 빈 문자열(0글자)만 제외하고 1글자 이상을 허용한다. */
    private static final int MIN_TERM_LENGTH = 1;

    private EgovLexicalQueryBuilder() {
    }

    /**
     * 질의를 공백 기준 어절로 분해한다. 빈 토큰은 제거하고 등장 순서를 보존하며
     * 중복은 제거한다.
     *
     * @param query 사용자 질의
     * @return 어절 리스트(순서 보존·중복 제거). 입력이 비어 있으면 빈 리스트
     */
    public static List<String> tokenize(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        Set<String> terms = new LinkedHashSet<>();
        for (String raw : query.trim().split("\\s+")) {
            String term = raw.trim();
            if (term.length() >= MIN_TERM_LENGTH) {
                terms.add(term);
            }
        }
        return new ArrayList<>(terms);
    }

    /**
     * 폴백 단계를 모두 포함한 질의 리스트를 순서대로 생성한다.
     *
     * <p>호출 측은 첫 단계부터 차례로 검색해, 회수 결과가 충분하면 멈추고
     * 부족하면 다음 단계로 폴백한다. 어절이 없으면 빈 리스트를 반환한다.</p>
     *
     * @param query 사용자 질의
     * @return [정확, 접두, 중위] 순의 질의 문자열(어절이 없으면 빈 리스트)
     */
    public static List<String> buildStagedQueries(String query) {
        List<String> terms = tokenize(query);
        if (terms.isEmpty()) {
            return List.of();
        }
        List<String> staged = new ArrayList<>(3);
        staged.add(exactQuery(terms));
        staged.add(prefixQuery(terms));
        staged.add(infixQuery(terms));
        return staged;
    }

    /** 정확매칭 질의: {@code @content:(t1 t2 ...)} (어절 모두 포함, AND). */
    static String exactQuery(List<String> terms) {
        StringBuilder sb = new StringBuilder("@content:(");
        for (int i = 0; i < terms.size(); i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(escape(terms.get(i)));
        }
        return sb.append(')').toString();
    }

    /** 접두매칭 질의: {@code @content:(t1* | t2* | ...)} (어절 시작 일치, OR). */
    static String prefixQuery(List<String> terms) {
        return wildcardQuery(terms, "", "*");
    }

    /** 중위매칭 질의: {@code @content:(*t1* | *t2* | ...)} (어절 부분 포함, OR). */
    static String infixQuery(List<String> terms) {
        return wildcardQuery(terms, "*", "*");
    }

    private static String wildcardQuery(List<String> terms, String pre, String post) {
        StringBuilder sb = new StringBuilder("@content:(");
        for (int i = 0; i < terms.size(); i++) {
            if (i > 0) {
                sb.append(" | ");
            }
            sb.append(pre).append(escape(terms.get(i))).append(post);
        }
        return sb.append(')').toString();
    }

    /**
     * RediSearch 질의 특수문자를 백슬래시로 이스케이프한다. 와일드카드({@code *})는
     * 호출 측에서 별도로 부착하므로 어절 본문에는 포함되지 않는다.
     */
    static String escape(String term) {
        StringBuilder sb = new StringBuilder(term.length());
        for (int i = 0; i < term.length(); i++) {
            char c = term.charAt(i);
            if (SPECIAL_CHARS.indexOf(c) >= 0) {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
