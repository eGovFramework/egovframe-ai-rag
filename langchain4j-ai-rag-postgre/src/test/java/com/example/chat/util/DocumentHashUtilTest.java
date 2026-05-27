package com.example.chat.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DocumentHashUtil 단위 테스트")
class DocumentHashUtilTest {

    @Test
    @DisplayName("일반 문자열에 대해 32자리 MD5 해시를 반환한다")
    void calculateHash_normalString_returns32CharMd5() {
        String result = DocumentHashUtil.calculateHash("hello world");

        assertThat(result).hasSize(32);
        assertThat(result).matches("[0-9a-f]+");
    }

    @Test
    @DisplayName("동일한 내용에 대해 항상 같은 해시를 반환한다")
    void calculateHash_sameContent_returnsSameHash() {
        String content = "eGovFrame RAG 문서 내용";

        String hash1 = DocumentHashUtil.calculateHash(content);
        String hash2 = DocumentHashUtil.calculateHash(content);

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("내용이 다르면 다른 해시를 반환한다")
    void calculateHash_differentContent_returnsDifferentHash() {
        String hash1 = DocumentHashUtil.calculateHash("문서 A");
        String hash2 = DocumentHashUtil.calculateHash("문서 B");

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    @DisplayName("null 입력 시 빈 문자열을 반환한다")
    void calculateHash_nullContent_returnsEmpty() {
        String result = DocumentHashUtil.calculateHash(null);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("빈 문자열 입력 시 빈 문자열을 반환한다")
    void calculateHash_emptyContent_returnsEmpty() {
        String result = DocumentHashUtil.calculateHash("");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("알려진 입력에 대해 올바른 MD5 해시값을 반환한다")
    void calculateHash_knownInput_returnsKnownMd5() {
        // echo -n "abc" | md5sum = 900150983cd24fb0d6963f7d28e17f72
        String result = DocumentHashUtil.calculateHash("abc");

        assertThat(result).isEqualTo("900150983cd24fb0d6963f7d28e17f72");
    }

    @Test
    @DisplayName("한글 문자열에 대해서도 32자리 해시를 반환한다")
    void calculateHash_koreanString_returns32CharHash() {
        String result = DocumentHashUtil.calculateHash("전자정부 표준프레임워크");

        assertThat(result).hasSize(32);
    }
}
