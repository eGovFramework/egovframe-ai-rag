package com.example.chat.config.etl.readers;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

/**
 * {@link EgovPdfReader} 의 빈 페이지 처리(createDocumentsWithCustomIds)를 검증한다.
 *
 * <p>빈 내용 페이지는 로깅만 하고 흐름을 멈추지 않아 이후 content.length() 등에서
 * 문제가 발생할 수 있었다. 빈 페이지는 건너뛰고 정상 페이지만 변환됨을 확인한다.</p>
 */
class EgovPdfReaderEmptyPageTest {

    @SuppressWarnings("unchecked")
    private List<Document> invoke(List<Document> documents) throws Exception {
        Method method = EgovPdfReader.class.getDeclaredMethod("createDocumentsWithCustomIds", List.class, String.class);
        method.setAccessible(true);
        return (List<Document>) method.invoke(new EgovPdfReader(), documents, "sample.pdf");
    }

    @Test
    @DisplayName("빈 내용 페이지는 건너뛰고 정상 페이지만 변환된다")
    void emptyPagesAreSkipped() throws Exception {
        List<Document> input = new ArrayList<>();
        Map<String, Object> meta = new HashMap<>();
        input.add(new Document("   ", meta));
        input.add(new Document("유효한 본문 내용입니다.", new HashMap<>()));

        List<Document> result = invoke(input);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getText()).isEqualTo("유효한 본문 내용입니다.");
        assertThat(result.get(0).getId()).isEqualTo("pdf-sample_2");
    }
}
