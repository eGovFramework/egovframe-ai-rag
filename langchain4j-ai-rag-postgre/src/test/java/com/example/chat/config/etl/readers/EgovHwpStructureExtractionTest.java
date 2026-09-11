package com.example.chat.config.etl.readers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import kr.dogfoot.hwplib.object.HWPFile;
import kr.dogfoot.hwplib.object.bodytext.ParagraphListInterface;
import kr.dogfoot.hwplib.object.bodytext.Section;
import kr.dogfoot.hwplib.object.bodytext.control.ControlTable;
import kr.dogfoot.hwplib.object.bodytext.control.ControlType;
import kr.dogfoot.hwplib.object.bodytext.control.table.Cell;
import kr.dogfoot.hwplib.object.bodytext.control.table.Row;
import kr.dogfoot.hwplib.object.bodytext.paragraph.Paragraph;
import kr.dogfoot.hwplib.tool.blankfilemaker.BlankFileMaker;
import kr.dogfoot.hwplib.tool.textextractor.TextExtractMethod;
import kr.dogfoot.hwplib.tool.textextractor.TextExtractor;

/**
 * HWP 추출이 문단과 표 구조를 남기는지 검증한다.
 *
 * <p>문서를 메모리에서 만들어 확인한다. 샘플 파일을 저장소에 두지 않고도 결정적으로
 * 검증할 수 있다.</p>
 */
class EgovHwpStructureExtractionTest {

    /** 비교를 위해 제어 문자와 개행을 걷어낸다. */
    private String stripControlChars(String text) {
        StringBuilder stripped = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch >= ' ') {
                stripped.append(ch);
            }
        }
        return stripped.toString();
    }

    private void addParagraph(ParagraphListInterface list, String text) throws Exception {
        Paragraph paragraph = list.addNewParagraph();
        paragraph.createText();
        paragraph.getText().addString(text);
    }

    /** 문단 2개와 3×3 표 하나를 담은 문서. */
    private HWPFile sampleDocument() throws Exception {
        HWPFile file = BlankFileMaker.make();
        Section section = file.getBodyText().getSectionList().get(0);
        addParagraph(section, "제1조(목적) 이 규정은 수수료 기준을 정함을 목적으로 한다");
        addParagraph(section, "제2조(수수료) 수수료는 별표와 같다");

        Paragraph tableParagraph = section.addNewParagraph();
        tableParagraph.createText();
        // 제어 문자를 넣어야 표 컨트롤이 문단에 연결된다
        tableParagraph.getText().addExtendCharForTable();
        ControlTable table = (ControlTable) tableParagraph.addNewControl(ControlType.Table);

        String[][] rows = {
                {"민원종류", "수수료", "처리기간"},
                {"주민등록등본", "400원", "즉시"},
                {"가족관계증명서", "1000원", "3일"}};
        for (String[] row : rows) {
            Row tableRow = table.addNewRow();
            for (String cell : row) {
                Cell tableCell = tableRow.addNewCell();
                addParagraph(tableCell.getParagraphList(), cell);
            }
        }
        return file;
    }

    @Test
    @DisplayName("문단 사이에 개행이 들어간다")
    void paragraphsAreSeparated() throws Exception {
        String extracted = EgovHwpStructuredExtractor.extract(sampleDocument());

        assertThat(extracted).contains("정함을 목적으로 한다\n제2조(수수료)");
    }

    @Test
    @DisplayName("표는 행마다 줄이 나뉘고 셀은 구분자로 이어진다")
    void tableRowsAndCellsAreSeparated() throws Exception {
        String extracted = EgovHwpStructuredExtractor.extract(sampleDocument());

        assertThat(extracted)
                .contains("민원종류 | 수수료 | 처리기간")
                .contains("주민등록등본 | 400원 | 즉시")
                .contains("가족관계증명서 | 1000원 | 3일");
    }

    @Test
    @DisplayName("셀 구분자는 행 맨 앞에 붙지 않는다")
    void cellSeparatorDoesNotStartALine() throws Exception {
        String extracted = EgovHwpStructuredExtractor.extract(sampleDocument());

        for (String line : extracted.split("\n")) {
            assertThat(line.trim()).doesNotStartWith("|");
        }
    }

    @Test
    @DisplayName("기본 추출은 표의 행 관계를 남기지 않는다")
    void defaultExtractionLosesTableStructure() throws Exception {
        // 기본 추출은 문단 끝 캐리지 리턴을 포함하므로 비교 전에 걷어낸다
        String extracted = stripControlChars(TextExtractor.extract(sampleDocument(),
                TextExtractMethod.AppendControlTextAfterParagraphText));

        // 셀 값이 구분자 없이 이어 붙어 어느 값이 어느 행인지 알 수 없다
        assertThat(extracted).contains("주민등록등본400원즉시");
        assertThat(extracted).doesNotContain("주민등록등본 | 400원");
    }

    @Test
    @DisplayName("본문 글자는 기본 추출과 같다")
    void textContentIsUnchanged() throws Exception {
        String defaultText = stripControlChars(TextExtractor.extract(sampleDocument(),
                TextExtractMethod.AppendControlTextAfterParagraphText));
        String structured = EgovHwpStructuredExtractor.extract(sampleDocument());

        assertThat(structured.replace("\n", "").replace(" | ", ""))
                .isEqualTo(defaultText);
    }
}
