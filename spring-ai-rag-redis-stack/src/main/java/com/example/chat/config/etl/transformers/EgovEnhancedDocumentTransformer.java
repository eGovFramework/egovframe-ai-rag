package com.example.chat.config.etl.transformers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.ai.model.transformer.SummaryMetadataEnricher;
import org.springframework.ai.model.transformer.SummaryMetadataEnricher.SummaryType;
import org.springframework.ai.model.transformer.KeywordMetadataEnricher;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class EgovEnhancedDocumentTransformer implements DocumentTransformer {

    private final OllamaChatModel ollamaChatModel;
    private final ObjectProvider<EgovKoreanSentenceSplitter> koreanSentenceSplitterProvider;
    private final SummaryMetadataEnricher summaryEnricher;
    private KeywordMetadataEnricher keywordEnricher;
    
    // 요약 생성 여부를 제어하는 설정
    @Value("${spring.ai.document.enable-summary}")
    private boolean enableSummary;
    
    // 요약을 생성할 최소 청크 수
    @Value("${spring.ai.document.summary-min-chunks}")
    private int summaryMinChunks;
    
    // 청크 크기 설정
    @Value("${spring.ai.document.chunk-size}")
    private int chunkSize;
    
    // 최소 청크 크기 (문자)
    @Value("${spring.ai.document.min-chunk-size-chars}")
    private int minChunkSizeChars;
    
    // 최대 청크 수
    @Value("${spring.ai.document.max-num-chunks}")
    private int maxNumChunks;
    
    // 임베딩할 최소 청크 길이
    @Value("${spring.ai.document.min-chunk-length-to-embed}")
    private int minChunkLengthToEmbed;
    
    // 키워드 추출 활성화 여부
    @Value("${spring.ai.document.enable-keywords}")
    private boolean enableKeywords;
    
    // 추출할 키워드 개수
    @Value("${spring.ai.document.keyword-count}")
    private int keywordCount;

    public EgovEnhancedDocumentTransformer(OllamaChatModel ollamaChatModel,
            ObjectProvider<EgovKoreanSentenceSplitter> koreanSentenceSplitterProvider) {
        this.ollamaChatModel = ollamaChatModel;
        this.koreanSentenceSplitterProvider = koreanSentenceSplitterProvider;
        
        // 요약 생성기 초기화 (사용 여부는 설정에 따라 결정)
        this.summaryEnricher = new SummaryMetadataEnricher(
            ollamaChatModel,
            //List.of(SummaryType.CURRENT)
            List.of(SummaryType.PREVIOUS, SummaryType.CURRENT, SummaryType.NEXT)
        );
        
        // 키워드 추출기는 @Value 주입 후 동적으로 초기화
    }

    @Override
    public List<Document> apply(List<Document> documents) {
        log.info("문서 변환 시작: {}개 문서", documents.size());
        
        // 문서별 토큰 수 로깅 추가
        for (Document doc : documents) {
            String content = doc.getText();
            if (content != null) {
                // 대략적인 토큰 수 추정 (1토큰 ≈ 4바이트)
                int estimatedTokens = content.length() / 4;
                log.info("문서 '{}' - 크기: {}바이트, 추정 토큰 수: {}", 
                        doc.getId(), content.length(), estimatedTokens);
            }
        }
        
        // 1단계: 문서 분할
        log.info("TokenTextSplitter 설정 - chunkSize: {}, minChunkSizeChars: {}, minChunkLengthToEmbed: {}, maxNumChunks: {}", 
                chunkSize, minChunkSizeChars, minChunkLengthToEmbed, maxNumChunks);
        
        TextSplitter textSplitter;
        EgovKoreanSentenceSplitter koreanSentenceSplitter = koreanSentenceSplitterProvider.getIfAvailable();
        if (koreanSentenceSplitter != null) {
            textSplitter = koreanSentenceSplitter;
            log.info("문서 분할기 선택 - {}", EgovKoreanSentenceSplitter.class.getSimpleName());
        } else {
            // 동적으로 TokenTextSplitter 생성
            textSplitter = TokenTextSplitter.builder()
                .withChunkSize(chunkSize)                         // chunkSize: 설정에서 가져온 청크 크기
                .withMinChunkSizeChars(minChunkSizeChars)         // minChunkSizeChars: 설정에서 가져온 최소 청크 크기
                .withMinChunkLengthToEmbed(minChunkLengthToEmbed) // minChunkLengthToEmbed: 임베딩할 최소 청크 길이
                .withMaxNumChunks(maxNumChunks)                   // maxNumChunks: 설정에서 가져온 최대 청크 수
                .withKeepSeparator(true)                          // keepSeparator: 구분자 유지 여부
                .build();
        }
        
        List<Document> splitDocs = textSplitter.apply(documents);
        log.info("문서 분할 완료: {}개 청크 생성 (청크 크기: {} 토큰)", splitDocs.size(), chunkSize);

        warnIfChunkLimitReached(splitDocs);
        
        // 분할된 청크들의 크기 로깅
        for (int i = 0; i < splitDocs.size(); i++) {
            Document chunk = splitDocs.get(i);
            String content = chunk.getText();
            if (content != null) {
                int estimatedTokens = content.length() / 4;
                log.info("청크 {} - 크기: {}바이트, 추정 토큰 수: {}", 
                        i + 1, content.length(), estimatedTokens);
            }
        }
        
        // 2단계: 키워드 추출
        List<Document> docsWithKeywords = splitDocs;
        if (enableKeywords) {
            log.info("키워드 추출 활성화: {}개 청크에 대해 키워드 추출 (키워드 개수: {})", splitDocs.size(), keywordCount);
            
            // 동적으로 KeywordMetadataEnricher 초기화
            if (keywordEnricher == null) {
                keywordEnricher = new KeywordMetadataEnricher(ollamaChatModel, keywordCount);
            }
            
            docsWithKeywords = keywordEnricher.apply(splitDocs);
            log.info("키워드 추출 완료: {}개 청크", docsWithKeywords.size());
        } else {
            log.info("키워드 추출 비활성화: {}개 청크 (설정: enableKeywords={})", 
                    splitDocs.size(), enableKeywords);
        }
        
        // 3단계: 요약 생성
        if (enableSummary && docsWithKeywords.size() >= summaryMinChunks) {
            log.info("요약 생성 활성화: {}개 청크에 대해 요약 생성", docsWithKeywords.size());
            List<Document> enrichedDocs = summaryEnricher.apply(docsWithKeywords);
            log.info("메타데이터 엔리치먼트 완료: {}개 청크", enrichedDocs.size());
            return enrichedDocs;
        } else {
            log.info("요약 생성 비활성화: {}개 청크 (설정: enableSummary={}, minChunks={})",
                    docsWithKeywords.size(), enableSummary, summaryMinChunks);
            return docsWithKeywords;
        }
    }

    /**
     * 청크 수 한도에 도달한 원본 문서를 경고한다.
     *
     * maxNumChunks를 넘는 청크는 버려진다. 그 문서는 청크가 0개가 아니므로
     * 해시가 저장되고, 결과적으로 파일을 수정하기 전까지 버려진 뒷부분은 색인되지 않는다.
     * 아무 흔적도 남지 않으면 알아챌 방법이 없으므로 여기서 알린다.
     *
     * 분할은 Spring AI의 TokenTextSplitter 안에서 일어나 버려진 개수를 알 수 없다.
     * 그래서 '한도와 같은 수가 나왔다'는 사실로 추정한다. 정확히 한도만큼 나온 정상 문서도
     * 경고에 걸릴 수 있어 문구를 '도달'로 두었다.
     */
    private void warnIfChunkLimitReached(List<Document> splitDocs) {
        Map<String, Integer> chunksPerOriginal = new LinkedHashMap<>();
        for (Document chunk : splitDocs) {
            Object originalId = chunk.getMetadata().get("original_id");
            if (originalId != null) {
                chunksPerOriginal.merge(originalId.toString(), 1, Integer::sum);
            }
        }
        chunksPerOriginal.forEach((originalId, count) -> {
            if (count >= maxNumChunks) {
                log.warn("문서 '{}' — max-num-chunks({}) 한도에 도달했습니다. 한도를 넘는 청크는 버려지며, "
                                + "이 문서는 '색인 완료'로 기록되므로 파일을 수정하기 전까지 버려진 부분은 "
                                + "다시 처리되지 않습니다. 한도를 올리거나 문서를 나누십시오.",
                        originalId, maxNumChunks);
            }
        });
    }
}
