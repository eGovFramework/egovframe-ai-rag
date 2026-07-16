package com.example.chat.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ConfigUtils#resolvePath(String)} 단위테스트.
 * ${HOME} 치환 및 경로 정규화 동작을 검증한다(설정 파일/Spring 컨텍스트 불필요).
 */
class ConfigUtilsResolvePathTest {

    private final ConfigUtils configUtils = new ConfigUtils();

    @Test
    @DisplayName("null 입력은 null 그대로 반환한다")
    void nullInput() {
        assertNull(configUtils.resolvePath(null));
    }

    @Test
    @DisplayName("빈 문자열은 빈 문자열 그대로 반환한다")
    void emptyInput() {
        assertEquals("", configUtils.resolvePath(""));
    }

    @Test
    @DisplayName("${HOME}이 실제 홈 경로로 치환된다")
    void resolvesHomePlaceholder() {
        String resolved = configUtils.resolvePath("${HOME}/models/onnx");
        assertFalse(resolved.contains("${HOME}"), "${HOME} 플레이스홀더가 남아있으면 안 된다");
        assertTrue(resolved.contains("models"), "치환 후에도 하위 경로는 유지되어야 한다");
    }

    @Test
    @DisplayName("${HOME}이 없는 경로는 정규화되어 반환된다")
    void plainPathNormalized() {
        String resolved = configUtils.resolvePath("/tmp/data/config");
        assertFalse(resolved.contains("${HOME}"));
        assertTrue(resolved.contains("data"));
    }
}
