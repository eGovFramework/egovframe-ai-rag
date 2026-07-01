package com.example.chat.config.etl.readers;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link EgovMarkdownReader} 의 마크다운 메타데이터 추출을 검증한다.
 *
 * <p>이전 구현은 {@code content.matches(".*#{1,6}\\s.*")} 처럼 DOTALL 없이 전체 매칭을
 * 수행해, 여러 줄로 이루어진 실제 마크다운 문서에서는 {@code .} 이 줄바꿈을 포함하지 못해
 * has_headers/has_links/has_images 가 항상 false 로 기록됐다. 본 테스트는 여러 줄 문서에서
 * 각 플래그가 올바르게 true 가 되는지, 해당 요소가 없으면 false 가 되는지 확인한다.</p>
 */
class EgovMarkdownReaderMetadataTest {

    @SuppressWarnings("unchecked")
    private Map<String, Object> metadata(String content) throws Exception {
        Method method = EgovMarkdownReader.class.getDeclaredMethod("createEnhancedMetadata", String.class, String.class);
        method.setAccessible(true);
        return (Map<String, Object>) method.invoke(new EgovMarkdownReader(), "sample.md", content);
    }

    @Test
    @DisplayName("여러 줄 마크다운에서 헤더·링크·이미지 플래그가 true 로 기록된다")
    void multilineMarkdownFlagsAreTrue() throws Exception {
        String content = "# 제목\n\n본문 문단입니다.\n자세한 내용은 [안내](https://example.com) 를 참고하세요.\n\n![다이어그램](https://example.com/a.png)\n";

        Map<String, Object> metadata = metadata(content);

        assertThat(metadata.get("has_headers")).isEqualTo(Boolean.TRUE);
        assertThat(metadata.get("has_links")).isEqualTo(Boolean.TRUE);
        assertThat(metadata.get("has_images")).isEqualTo(Boolean.TRUE);
    }

    @Test
    @DisplayName("해당 요소가 없는 마크다운에서는 플래그가 false 로 기록된다")
    void plainMultilineMarkdownFlagsAreFalse() throws Exception {
        String content = "일반 문단 첫째 줄입니다.\n특수 마크다운 요소가 없는 둘째 줄입니다.\n";

        Map<String, Object> metadata = metadata(content);

        assertThat(metadata.get("has_headers")).isEqualTo(Boolean.FALSE);
        assertThat(metadata.get("has_links")).isEqualTo(Boolean.FALSE);
        assertThat(metadata.get("has_images")).isEqualTo(Boolean.FALSE);
    }
}
