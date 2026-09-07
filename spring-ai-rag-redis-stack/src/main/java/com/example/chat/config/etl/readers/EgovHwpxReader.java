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
import kr.dogfoot.hwpxlib.tool.textextractor.TextMarks;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class EgovHwpxReader implements DocumentReader {

    /**
     * 문단·표 구조를 남기기 위한 추출 표식.
     *
     * <p>hwpxlib의 {@code TextExtractor}는 {@code TextMarks}가 {@code null}이면 구분자를 하나도 넣지
     * 않는다({@code TextBuilder.paraSeparator()} 등이 즉시 반환). 그 결과 문서 전체가 개행 없는 한 줄이
     * 되고, 표는 셀 값이 이어 붙어 행과 열의 관계가 사라진다.</p>
     *
     * <p>문단·표 행·표 셀 세 가지만 지정한다. 컨테이너·도형 등 나머지 표식은 지정하지 않아 기존과 같은
     * 텍스트가 나온다. 셀 구분자는 셀 사이에만 들어가며 행 맨 앞에는 붙지 않는다. 줄 맨 앞의 파이프는
     * 문장 분할기가 마크다운 블록 시작으로 인식해 의도하지 않은 경계를 만든다.</p>
     *
     * <p>지정 후에는 값을 바꾸지 않으므로 인스턴스를 공유한다.</p>
     */
    private static final TextMarks STRUCTURE_MARKS = new TextMarks()
            .paraSeparatorAnd("\n")
            .tableRowSeparatorAnd("\n")
            .tableCellSeparatorAnd(" | ");

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
                STRUCTURE_MARKS
        );

        if (content == null || content.trim().isEmpty()) {
            log.warn("빈 HWPX 파일 건너뜀: {}", filename);
            return null;
        }

        // 하위 폴더의 동명 파일이 같은 id 를 받지 않도록 기본 디렉터리 기준 상대 경로를 쓴다
        String safeFilename = EgovDocumentIdResolver.resolveKeyWithoutExtension(resource, hwpxDocumentPath);
        String customId = String.format("hwpx-%s", safeFilename);

        java.util.Map<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("file_name", filename);
        metadata.put("source", filename);
        metadata.put("type", "hwpx");
        metadata.put("content_length", content.length());
        // 분할 이후에도 원본 문서를 식별할 수 있도록 문서 id 를 메타데이터에도 담는다
        metadata.put("original_id", customId);

        log.info("HWPX 문서 로드 완료: {}, 크기: {}자", filename, content.length());
        return new Document(customId, content, metadata);
    }
}
