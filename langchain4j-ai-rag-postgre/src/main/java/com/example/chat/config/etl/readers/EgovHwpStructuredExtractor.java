package com.example.chat.config.etl.readers;

import java.io.UnsupportedEncodingException;

import kr.dogfoot.hwplib.object.HWPFile;
import kr.dogfoot.hwplib.object.bodytext.Section;
import kr.dogfoot.hwplib.object.bodytext.control.Control;
import kr.dogfoot.hwplib.object.bodytext.control.ControlTable;
import kr.dogfoot.hwplib.object.bodytext.control.ControlType;
import kr.dogfoot.hwplib.object.bodytext.control.table.Cell;
import kr.dogfoot.hwplib.object.bodytext.control.table.Row;
import kr.dogfoot.hwplib.object.bodytext.paragraph.Paragraph;
import kr.dogfoot.hwplib.object.bodytext.ParagraphListInterface;

/**
 * HWP 문서에서 문단과 표 구조를 남기며 텍스트를 추출한다.
 *
 * <p>hwplib의 {@code TextExtractor}는 표를 순회하며 셀 텍스트만 이어 붙이고 행·셀 구분자를
 * 넣지 않는다. {@code TextExtractOption}에도 구분자를 지정하는 항목이 없어, 어느 값이 어느 행에
 * 속하는지 복원할 수 없다. 표를 직접 순회해 구분자를 넣는다.</p>
 *
 * <p>출력 형식은 HWPX 쪽과 같다. 문단과 표의 행은 개행으로 나누고 셀은 구분자로 잇는다.
 * 구분자는 셀 사이에만 들어가며 행 맨 앞에는 붙지 않는다.</p>
 */
public final class EgovHwpStructuredExtractor {

    /** 표의 셀 사이에 넣는 구분자. HWPX 추출과 같은 값을 쓴다. */
    private static final String CELL_SEPARATOR = " | ";

    private EgovHwpStructuredExtractor() {
    }

    /**
     * 문단과 표 구조를 유지한 텍스트를 만든다.
     *
     * @param hwpFile 추출 대상 문서
     * @return 문단은 개행으로, 표는 행마다 개행·셀마다 구분자로 구분한 텍스트
     * @throws UnsupportedEncodingException 문단 텍스트를 문자열로 바꾸지 못한 경우
     */
    public static String extract(HWPFile hwpFile) throws UnsupportedEncodingException {
        StringBuilder text = new StringBuilder();
        for (Section section : hwpFile.getBodyText().getSectionList()) {
            appendParagraphList(text, section);
        }
        return text.toString();
    }

    private static void appendParagraphList(StringBuilder text, ParagraphListInterface paragraphList)
            throws UnsupportedEncodingException {
        for (int i = 0; i < paragraphList.getParagraphCount(); i++) {
            appendParagraph(text, paragraphList.getParagraph(i));
        }
    }

    private static void appendParagraph(StringBuilder text, Paragraph paragraph)
            throws UnsupportedEncodingException {
        if (paragraph == null) {
            return;
        }

        if (paragraph.getText() != null) {
            String paragraphText = clean(paragraph.getText().getNormalString(0));
            if (!paragraphText.isEmpty()) {
                appendLine(text, paragraphText);
            }
        }

        if (paragraph.getControlList() == null) {
            return;
        }
        for (Control control : paragraph.getControlList()) {
            if (control != null && control.getType() == ControlType.Table) {
                appendTable(text, (ControlTable) control);
            }
        }
    }

    private static void appendTable(StringBuilder text, ControlTable table)
            throws UnsupportedEncodingException {
        for (Row row : table.getRowList()) {
            StringBuilder line = new StringBuilder();
            for (Cell cell : row.getCellList()) {
                String cellText = cellText(cell);
                if (line.length() > 0) {
                    line.append(CELL_SEPARATOR);
                }
                line.append(cellText);
            }
            appendLine(text, line.toString());
        }
    }

    /** 셀 안의 문단을 공백으로 이어 한 줄로 만든다. */
    private static String cellText(Cell cell) throws UnsupportedEncodingException {
        StringBuilder cellText = new StringBuilder();
        ParagraphListInterface paragraphList = cell.getParagraphList();
        for (int i = 0; i < paragraphList.getParagraphCount(); i++) {
            Paragraph paragraph = paragraphList.getParagraph(i);
            if (paragraph == null || paragraph.getText() == null) {
                continue;
            }
            String paragraphText = clean(paragraph.getText().getNormalString(0));
            if (paragraphText.isEmpty()) {
                continue;
            }
            if (cellText.length() > 0) {
                cellText.append(' ');
            }
            cellText.append(paragraphText);
        }
        return cellText.toString();
    }

    /**
     * 문단 텍스트에서 제어 문자를 걷어낸다.
     *
     * <p>{@code getNormalString()}은 문단 끝 표시인 캐리지 리턴을 그대로 포함한다. 그대로 두면
     * 이 클래스가 넣는 개행과 겹쳐 빈 줄이 생긴다.</p>
     */
    private static String clean(String rawText) {
        if (rawText == null) {
            return "";
        }
        StringBuilder cleaned = new StringBuilder(rawText.length());
        for (int i = 0; i < rawText.length(); i++) {
            char ch = rawText.charAt(i);
            if (ch >= ' ' || ch == '\t') {
                cleaned.append(ch);
            }
        }
        return cleaned.toString().trim();
    }

    private static void appendLine(StringBuilder text, String line) {
        if (text.length() > 0) {
            text.append('\n');
        }
        text.append(line);
    }
}
