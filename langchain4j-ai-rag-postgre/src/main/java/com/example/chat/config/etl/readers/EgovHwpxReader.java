package com.example.chat.config.etl.readers;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import kr.dogfoot.hwpxlib.object.HWPXFile;
import kr.dogfoot.hwpxlib.reader.HWPXReader;
import kr.dogfoot.hwpxlib.tool.textextractor.TextExtractMethod;
import kr.dogfoot.hwpxlib.tool.textextractor.TextExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * HWPX 문서 로더
 * hwpxlib(kr.dogfoot:hwpxlib)를 사용하여 .hwpx 파일에서 텍스트를 추출한다.
 */
@Slf4j
@Component
public class EgovHwpxReader {

    @Value("${document.hwpx-path:#{null}}")
    private String hwpxDocumentPath;

    /**
     * HWPX 문서 로드
     */
    public List<Document> read() {
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
                    Document document = parseHwpxDocument(resource);
                    if (document != null) {
                        allDocuments.add(document);
                        log.info("HWPX 파일 '{}' 처리 완료.", resource.getFilename());
                    }
                } catch (Exception e) {
                    log.error("HWPX 파일 '{}' 처리 중 오류 발생: {}", resource.getFilename(), e.getMessage());
                    // 개별 파일 오류는 무시하고 계속 진행
                }
            }

            log.info("총 {}개의 HWPX 문서를 읽었습니다.", allDocuments.size());
            return allDocuments;

        } catch (Exception e) {
            log.error("HWPX 문서 읽기 중 오류 발생", e);
            return List.of();
        }
    }

    /**
     * HWPX 파일을 파싱하여 Document 생성
     * HWPXReader는 파일 경로 기반으로 동작하므로 스트림을 임시 파일에 복사한다.
     */
    private Document parseHwpxDocument(Resource resource) throws Exception {
        String filename = resource.getFilename();
        if (filename == null) {
            filename = "unknown.hwpx";
        }

        File tempFile = null;
        try {
            tempFile = Files.createTempFile("hwpx-", ".hwpx").toFile();

            try (InputStream inputStream = resource.getInputStream()) {
                Files.copy(inputStream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            HWPXFile hwpxFile = HWPXReader.fromFile(tempFile);
            String content = TextExtractor.extract(
                    hwpxFile,
                    TextExtractMethod.InsertControlTextBetweenParagraphText,
                    false,
                    null);

            if (content == null || content.trim().isEmpty()) {
                log.warn("HWPX 파일 '{}'에서 추출된 텍스트가 없습니다.", filename);
                return null;
            }

            String baseFilename = filename.replaceAll("\\.hwpx$", "");
            String safeFilename = baseFilename.replaceAll("[\\/:*?\"<>|]", "")
                    .replaceAll("\\s+", "-");
            String customId = String.format("hwpx-%s_1", safeFilename);

            Metadata metadata = Metadata.from("id", customId);
            metadata.put("file_name", filename);
            metadata.put("source", filename);
            metadata.put("type", "hwpx");
            metadata.put("content_length", String.valueOf(content.length()));

            log.debug("HWPX Document ID: {} (길이: {})", customId, content.length());

            return Document.from(content, metadata);

        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }
}
