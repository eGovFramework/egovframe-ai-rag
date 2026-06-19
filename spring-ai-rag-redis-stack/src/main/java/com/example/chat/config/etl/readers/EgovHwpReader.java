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

import kr.dogfoot.hwplib.object.HWPFile;
import kr.dogfoot.hwplib.reader.HWPReader;
import kr.dogfoot.hwplib.tool.textextractor.TextExtractMethod;
import kr.dogfoot.hwplib.tool.textextractor.TextExtractor;
import lombok.extern.slf4j.Slf4j;

/**
 * HWP(한글) 문서 리더
 * hwplib 라이브러리를 사용하여 .hwp 파일에서 텍스트를 추출하고
 * Spring AI Document 형태로 반환한다.
 */
@Slf4j
@Component
public class EgovHwpReader implements DocumentReader {

    @Value("${spring.ai.document.hwp-path:#{null}}")
    private String hwpDocumentPath;

    @Override
    public List<Document> get() {
        if (hwpDocumentPath == null || hwpDocumentPath.isBlank()) {
            log.info("HWP 문서 경로가 설정되지 않아 건너뜁니다.");
            return List.of();
        }
        log.info("HWP 문서 읽기 시작 - 경로: {}", hwpDocumentPath);

        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(hwpDocumentPath);

            if (resources.length == 0) {
                log.warn("HWP 파일을 찾을 수 없습니다: {}", hwpDocumentPath);
                return List.of();
            }

            log.info("{}개의 HWP 파일을 찾았습니다.", resources.length);

            List<Document> allDocuments = new ArrayList<>();

            for (Resource resource : resources) {
                log.info("HWP 파일 처리 중: {}", resource.getFilename());
                try {
                    Document doc = parseHwpDocument(resource);
                    if (doc != null) {
                        allDocuments.add(doc);
                    }
                } catch (Exception e) {
                    log.error("HWP 파일 '{}' 처리 중 오류 발생: {}", resource.getFilename(), e.getMessage());
                    // 개별 파일 오류는 무시하고 계속 진행
                }
            }

            log.info("총 {}개의 HWP 문서를 읽었습니다.", allDocuments.size());
            return allDocuments;

        } catch (Exception e) {
            log.error("HWP 문서 읽기 중 오류 발생", e);
            return List.of();
        }
    }

    private Document parseHwpDocument(Resource resource) throws Exception {
        String filename = resource.getFilename();
        if (filename == null) {
            filename = "unknown.hwp";
        }

        File file = resource.getFile();
        HWPFile hwpFile = HWPReader.fromFile(file);

        // 본문 + 표/각주 등 컨트롤 텍스트까지 포함하여 추출
        String content = TextExtractor.extract(hwpFile, TextExtractMethod.AppendControlTextAfterParagraphText);

        if (content == null || content.trim().isEmpty()) {
            log.warn("HWP 파일 '{}': 추출된 텍스트가 없습니다.", filename);
            return null;
        }

        String baseFilename = filename.replaceAll("\\.hwp$", "");
        String safeFilename = baseFilename.replaceAll("[\\/:*?\"<>|]", "").replaceAll("\\s+", "-");
        String customId = String.format("hwp-%s_1", safeFilename);

        java.util.Map<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("file_name", filename);
        metadata.put("source", filename);
        metadata.put("type", "hwp");
        metadata.put("content_length", content.length());
        metadata.put("page_number", 1);

        log.debug("HWP Document ID: {} (길이: {})", customId, content.length());

        return new Document(customId, content, metadata);
    }
}
