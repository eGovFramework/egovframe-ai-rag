package com.example.chat.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link EgovDocumentHashUtil} 단위테스트.
 * 문서 내용의 MD5 해시 계산 동작을 검증한다.
 */
class EgovDocumentHashUtilTest {

    @Test
    @DisplayName("null/빈 문자열은 빈 문자열을 반환한다")
    void nullOrEmptyReturnsEmpty() {
        assertEquals("", EgovDocumentHashUtil.calculateHash(null));
        assertEquals("", EgovDocumentHashUtil.calculateHash(""));
    }

    @Test
    @DisplayName("알려진 입력에 대해 알려진 MD5 해시를 반환한다")
    void knownMd5() {
        // MD5("test") = 098f6bcd4621d373cade4e832627b4f6
        assertEquals("098f6bcd4621d373cade4e832627b4f6", EgovDocumentHashUtil.calculateHash("test"));
    }

    @Test
    @DisplayName("동일 입력은 항상 동일 해시를 반환한다(결정적)")
    void deterministic() {
        String content = "전자정부 표준프레임워크 RAG 문서";
        assertEquals(EgovDocumentHashUtil.calculateHash(content), EgovDocumentHashUtil.calculateHash(content));
    }

    @Test
    @DisplayName("다른 입력은 다른 해시를 반환한다")
    void differentInputDifferentHash() {
        assertNotEquals(
            EgovDocumentHashUtil.calculateHash("문서 A"),
            EgovDocumentHashUtil.calculateHash("문서 B"));
    }

    @Test
    @DisplayName("MD5 해시는 32자리 16진수 문자열이다")
    void hashLengthAndFormat() {
        String hash = EgovDocumentHashUtil.calculateHash("한글 UTF-8 콘텐츠");
        assertEquals(32, hash.length());
        org.junit.jupiter.api.Assertions.assertTrue(hash.matches("[0-9a-f]{32}"));
    }
}
