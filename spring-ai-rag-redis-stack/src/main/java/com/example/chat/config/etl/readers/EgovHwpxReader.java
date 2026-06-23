package com.example.chat.config.etl.readers;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import kr.dogfoot.hwpxlib.object.HWPXFile;
import kr.dogfoot.hwpxlib.reader.HWPXReader;
import kr.dogfoot.hwpxlib.tool.textextractor.TextExtractMethod;
import kr.dogfoot.hwpxlib.tool.textextractor.TextExtractor;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class EgovHwpxReader implements DocumentReader {

    @Value("${spring.ai.document.hwpx-path:#{null}}")
    private String hwpxDocumentPath;

    @Override
    public List<Document> get() {
        if (hwpxDocumentPath == null || hwpxDocumentPath.isBlank()) {
            log.info("HWPX 문서 경로가 설정되지 않아 건너뜁니다.");
            return List.of();
        }

        log.info("HWPX 문서 읽기 시작 - 경로: {}", hwpxDocumentPath);

        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(hwpxDocumentPath);

            if (resources.length == 0) {
                log.warn("HWPX 파일을 찾을 수 없습니다: {}", hwpxDocumentPath);
                return List.of();
            }

            log.info("{}개의 HWPX 파일을 찾았습니다.", resources.length);

            List<Document> allDocuments = new ArrayList<>();

            for (Resource resource : resources) {
                log.info("HWPX 파일 처리 중: {}", resource.getFilename());
                try {
                    Document doc = readHwpxResource(resource);
                    if (doc != null) {
                        allDocuments.add(doc);
                    }
                } catch (Exception e) {
                    log.error("HWPX 파일 '{}' 처리 중 오류 발생: {}", resource.getFilename(), e.getMessage());
                }
            }

            log.info("총 {}개의 HWPX 문서를 읽었습니다.", allDocuments.size());
            return allDocuments;

        } catch (Exception e) {
            log.error("HWPX 문서 읽기 중 오류 발생", e);
            return List.of();
        }
    }

    private Document readHwpxResource(Resource resource) throws Exception {
        String filename = resource.getFilename();
        if (filename == null) {
            log.warn("파일명이 null입니다: {}", resource.getDescription());
            return null;
        }

        File file = resource.getFile();
        HWPXFile hwpxFile = HWPXReader.fromFile(file);

        String content = TextExtractor.extract(
                hwpxFile,
                TextExtractMethod.InsertControlTextBetweenParagraphText,
                false,
                null
        );

        if (content == null || content.trim().isEmpty()) {
            log.warn("빈 HWPX 파일 건너뜀: {}", filename);
            return null;
        }

        String baseFilename = filename.replaceAll("\\.hwpx$", "");
        String safeFilename = baseFilename.replaceAll("[\\/:*?\"<>|]", "").replaceAll("\\s+", "-");
        String customId = String.format("hwpx-%s", safeFilename);

        java.util.Map<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("file_name", filename);
        metadata.put("source", filename);
        metadata.put("type", "hwpx");
        metadata.put("content_length", content.length());

        log.info("HWPX 문서 로드 완료: {}, 크기: {}자", filename, content.length());
        return new Document(customId, content, metadata);
    }
}
