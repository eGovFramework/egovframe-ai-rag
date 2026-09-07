package com.example.chat.config.etl.readers;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import dev.langchain4j.data.document.Document;

/**
 * 하위 폴더에 같은 이름의 파일이 있을 때 리더가 서로 다른 문서 id 를 부여하는지 검증한다.
 * <p>
 * 스캔 경로가 재귀 글로브인데 id 를 파일명만으로 만들면 두 파일이 같은 id 를 받는다.
 * 벡터 저장소 갱신이 metadata 의 id 단위로 기존 청크를 지우고 다시 넣기 때문에, 같은 id 를
 * 쓰면 재색인마다 한쪽 문서의 청크가 지워진 뒤 채워지지 않는다.
 */
class EgovMarkdownReaderNestedIdTest {

    @TempDir
    Path baseDir;

    @Test
    @DisplayName("하위 폴더의 동명 파일이 서로 다른 문서 id 를 받는다")
    void sameFilenameInDifferentFoldersGetsDistinctDocumentIds() throws IOException {
        write("부서A/운영지침.md", "# 부서A\n부서A 운영지침 본문\n");
        write("부서B/운영지침.md", "# 부서B\n부서B 운영지침 본문\n");

        List<Document> documents = read("**/*.md");

        assertThat(documents).hasSize(2);
        assertThat(documents).extracting(document -> document.metadata().getString("id"))
                .containsExactlyInAnyOrder("doc-부서A/운영지침.md", "doc-부서B/운영지침.md");
        assertThat(documents).extracting(document -> document.metadata().getString("id"))
                .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("기본 디렉터리 바로 밑의 파일은 기존 id 를 그대로 유지한다")
    void flatFileKeepsExistingDocumentId() throws IOException {
        write("운영지침.md", "# 제목\n본문\n");

        List<Document> documents = read("*.md");

        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).metadata().getString("id")).isEqualTo("doc-운영지침.md");
    }

    private List<Document> read(String glob) {
        EgovMarkdownReader reader = new EgovMarkdownReader();
        String pattern = "file:" + baseDir.toAbsolutePath().toString().replace('\\', '/') + "/" + glob;
        ReflectionTestUtils.setField(reader, "documentPath", pattern);
        return reader.read();
    }

    private void write(String relativePath, String content) throws IOException {
        Path target = baseDir.resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
    }
}
