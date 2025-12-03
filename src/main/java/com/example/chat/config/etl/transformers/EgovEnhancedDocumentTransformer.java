package com.example.chat.config.etl.transformers;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.ai.model.transformer.SummaryMetadataEnricher;
import org.springframework.ai.model.transformer.SummaryMetadataEnricher.SummaryType;
import org.springframework.ai.model.transformer.KeywordMetadataEnricher;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class EgovEnhancedDocumentTransformer implements DocumentTransformer {

    private final OllamaChatModel ollamaChatModel;
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

    public EgovEnhancedDocumentTransformer(OllamaChatModel ollamaChatModel) {
        this.ollamaChatModel = ollamaChatModel;
        
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
        
        // 동적으로 TokenTextSplitter 생성
        TokenTextSplitter textSplitter = new TokenTextSplitter(
            chunkSize,           // chunkSize: 설정에서 가져온 청크 크기
            minChunkSizeChars,   // minChunkSizeChars: 설정에서 가져온 최소 청크 크기
            minChunkLengthToEmbed, // minChunkLengthToEmbed: 임베딩할 최소 청크 길이
            maxNumChunks,        // maxNumChunks: 설정에서 가져온 최대 청크 수
            true                 // keepSeparator: 구분자 유지 여부
        );
        
        List<Document> splitDocs = textSplitter.apply(documents);
        log.info("문서 분할 완료: {}개 청크 생성 (청크 크기: {} 토큰)", splitDocs.size(), chunkSize);
        
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
} 