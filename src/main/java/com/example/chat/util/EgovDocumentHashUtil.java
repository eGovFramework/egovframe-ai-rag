package com.example.chat.util;

import java.nio.charset.StandardCharsets;

import org.apache.commons.codec.digest.DigestUtils;

/**
 * 문서 해시 계산을 위한 유틸리티 클래스
 */
public class EgovDocumentHashUtil {

    /**
     * 문서 내용의 MD5 해시를 계산
     * 
     * @param content 해시를 계산할 문서 내용
     * @return MD5 해시값
     */
    public static String calculateHash(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        return DigestUtils.md5Hex(content.getBytes(StandardCharsets.UTF_8));
    }
}
