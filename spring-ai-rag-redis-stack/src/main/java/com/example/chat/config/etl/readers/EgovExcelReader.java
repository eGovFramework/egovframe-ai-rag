package com.example.chat.config.etl.readers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Excel(.xlsx) 문서 리더.
 * Apache POI XSSFWorkbook을 이용해 시트별로 행/셀 텍스트를 추출하고,
 * Spring AI Document 형태로 변환한다.
 */
@Slf4j
@Component
public class EgovExcelReader implements DocumentReader {

    @Value("${spring.ai.document.xlsx-path:#{null}}")
    private String xlsxDocumentPath;

    @Override
    public List<Document> get() {
        if (xlsxDocumentPath == null || xlsxDocumentPath.isBlank()) {
            log.info("Excel 문서 경로가 설정되지 않아 건너뜁니다.");
            return List.of();
        }
        log.info("Excel 문서 읽기 시작 - 경로: {}", xlsxDocumentPath);

        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(xlsxDocumentPath);

            if (resources.length == 0) {
                log.warn("Excel 파일을 찾을 수 없습니다: {}", xlsxDocumentPath);
                return List.of();
            }

            log.info("{}개의 Excel 파일을 찾았습니다.", resources.length);

            List<Document> allDocuments = new ArrayList<>();

            for (Resource resource : resources) {
                log.info("Excel 파일 처리 중: {}", resource.getFilename());
                try {
                    List<Document> docs = processExcelResource(resource);
                    allDocuments.addAll(docs);
                } catch (Exception e) {
                    log.error("Excel 파일 '{}' 처리 중 오류 발생: {}", resource.getFilename(), e.getMessage());
                }
            }

            log.info("총 {}개의 Excel 문서를 읽었습니다.", allDocuments.size());
            return allDocuments;

        } catch (Exception e) {
            log.error("Excel 문서 읽기 중 오류 발생", e);
            return List.of();
        }
    }

    private List<Document> processExcelResource(Resource resource) throws IOException {
        List<Document> documents = new ArrayList<>();
        String filename = resource.getFilename();

        try (XSSFWorkbook workbook = new XSSFWorkbook(resource.getInputStream())) {
            int sheetCount = workbook.getNumberOfSheets();
            log.info("Excel 파일 '{}': {}개 시트", filename, sheetCount);

            for (int sheetIdx = 0; sheetIdx < sheetCount; sheetIdx++) {
                Sheet sheet = workbook.getSheetAt(sheetIdx);
                String sheetName = sheet.getSheetName();

                String content = extractSheetContent(sheet);
                if (content == null || content.trim().isEmpty()) {
                    log.debug("Excel 시트 '{}/{}': 내용 없음, 건너뜀", filename, sheetName);
                    continue;
                }

                Map<String, Object> metadata = createMetadata(filename, sheetName, sheetIdx, content);
                String docId = buildDocId(filename, sheetName, sheetIdx);

                documents.add(new Document(docId, content, metadata));
                log.info("Excel 시트 '{}/{}' 로드 완료, 크기: {}바이트", filename, sheetName, content.length());
            }
        }

        return documents;
    }

    /**
     * 시트의 모든 행을 순회하여 탭 구분 행 텍스트를 줄바꿈으로 이어 붙인다.
     */
    private String extractSheetContent(Sheet sheet) {
        StringBuilder sb = new StringBuilder();

        for (Row row : sheet) {
            StringBuilder rowSb = new StringBuilder();
            boolean hasContent = false;

            for (Cell cell : row) {
                String cellValue = getCellValueAsString(cell);
                if (!cellValue.isEmpty()) {
                    hasContent = true;
                }
                if (rowSb.length() > 0) {
                    rowSb.append('\t');
                }
                rowSb.append(cellValue);
            }

            if (hasContent) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(rowSb);
            }
        }

        return sb.toString();
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }
        CellType cellType = cell.getCellType();
        if (cellType == CellType.FORMULA) {
            cellType = cell.getCachedFormulaResultType();
        }
        switch (cellType) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                double numVal = cell.getNumericCellValue();
                if (numVal == Math.floor(numVal) && !Double.isInfinite(numVal)) {
                    return String.valueOf((long) numVal);
                }
                return String.valueOf(numVal);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case BLANK:
                return "";
            default:
                return "";
        }
    }

    private Map<String, Object> createMetadata(String filename, String sheetName, int sheetIdx, String content) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", filename);
        metadata.put("type", "xlsx");
        metadata.put("sheet_name", sheetName);
        metadata.put("sheet_index", sheetIdx);
        metadata.put("content_length", content.length());
        metadata.put("row_count", content.split("\n").length);
        return metadata;
    }

    private String buildDocId(String filename, String sheetName, int sheetIdx) {
        String baseFilename = filename.replaceAll("\\.xlsx$", "");
        String safeFilename = baseFilename.replaceAll("[\\/:*?\"<>|]", "").replaceAll("\\s+", "-");
        String safeSheet = sheetName.replaceAll("[\\/:*?\"<>|]", "").replaceAll("\\s+", "-");
        return String.format("xlsx-%s_%d-%s", safeFilename, sheetIdx, safeSheet);
    }
}
