package com.example.chat.config.etl;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import java.util.stream.Collectors;

import com.example.chat.config.etl.transformers.EgovEnhancedDocumentTransformer;
import com.example.chat.config.etl.transformers.EgovKoreanSentenceSplitter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 한국어 문장경계 인지 청킹 토글 동작을 검증한다.
 */
class EgovKoreanChunkingToggleTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(EgovKoreanChunkingConfig.class);

    @Test
    @DisplayName("토글 off(기본): 한국어 문장경계 splitter 빈 미등록")
    void koreanSentenceSplitterAbsentWhenDisabled() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean("koreanSentenceTextSplitter");
            assertThat(context).doesNotHaveBean(EgovKoreanSentenceSplitter.class);
        });
    }

    @Test
    @DisplayName("토글 on: 한국어 문장경계 splitter 빈 등록")
    void koreanSentenceSplitterPresentWhenEnabled() {
        runner.withPropertyValues("spring.ai.document.chunking.korean-sentence.enabled=true").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasBean("koreanSentenceTextSplitter");
            assertThat(context).hasSingleBean(EgovKoreanSentenceSplitter.class);
            assertThat(context).getBean("koreanSentenceTextSplitter").isInstanceOf(EgovKoreanSentenceSplitter.class);
        });
    }

    @Test
    @DisplayName("한국어 splitter 빈 부재 시 기존 TokenTextSplitter 경로로 폴백한다")
    void enhancedTransformerFallsBackToTokenTextSplitterWhenBeanAbsent() {
        EgovEnhancedDocumentTransformer transformer = new EgovEnhancedDocumentTransformer(
                ollamaChatModel(), emptyKoreanSentenceSplitterProvider());
        setDocumentOptions(transformer, 30, 10, 0, 100);
        ReflectionTestUtils.setField(transformer, "enableSummary", false);
        ReflectionTestUtils.setField(transformer, "summaryMinChunks", 2);
        ReflectionTestUtils.setField(transformer, "enableKeywords", false);
        ReflectionTestUtils.setField(transformer, "keywordCount", 5);

        Document document = new Document(
                "전자정부 표준프레임워크 문서입니다. 기존 TokenTextSplitter 경로를 검증합니다. 폴백 결과가 같아야 합니다.");
        List<Document> documents = List.of(document);
        TokenTextSplitter tokenTextSplitter = TokenTextSplitter.builder()
                .withChunkSize(30)
                .withMinChunkSizeChars(10)
                .withMinChunkLengthToEmbed(0)
                .withMaxNumChunks(100)
                .withKeepSeparator(true)
                .build();

        List<String> expected = tokenTextSplitter.apply(documents).stream()
                .map(Document::getText)
                .collect(Collectors.toList());
        List<String> actual = transformer.apply(documents).stream()
                .map(Document::getText)
                .collect(Collectors.toList());

        assertThat(actual).isEqualTo(expected);
    }

    private ObjectProvider<EgovKoreanSentenceSplitter> emptyKoreanSentenceSplitterProvider() {
        return new ObjectProvider<>() {
            @Override
            public EgovKoreanSentenceSplitter getObject() {
                return null;
            }

            @Override
            public EgovKoreanSentenceSplitter getIfAvailable() {
                return null;
            }
        };
    }

    private OllamaChatModel ollamaChatModel() {
        return OllamaChatModel.builder()
                .ollamaApi(OllamaApi.builder().baseUrl("http://localhost:1").build())
                .build();
    }

    private void setDocumentOptions(EgovEnhancedDocumentTransformer transformer, int chunkSize,
            int minChunkSizeChars, int minChunkLengthToEmbed, int maxNumChunks) {
        ReflectionTestUtils.setField(transformer, "chunkSize", chunkSize);
        ReflectionTestUtils.setField(transformer, "minChunkSizeChars", minChunkSizeChars);
        ReflectionTestUtils.setField(transformer, "minChunkLengthToEmbed", minChunkLengthToEmbed);
        ReflectionTestUtils.setField(transformer, "maxNumChunks", maxNumChunks);
    }
}
