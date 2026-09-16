package com.example.chat.config.etl.readers;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

/**
 * 문서 id 키가 하위 폴더의 동명 파일을 구분하는지, 그리고 평면 배치의 기존 id 를 그대로 두는지 검증한다.
 */
class EgovDocumentIdResolverTest {

    @TempDir
    Path baseDir;

    @Test
    @DisplayName("기본 디렉터리 바로 밑의 파일은 파일명이 그대로 키가 된다")
    void flatFileKeepsFilenameAsKey() throws IOException {
        Resource resource = write("운영지침.md");

        assertThat(EgovDocumentIdResolver.resolveKey(resource, pattern("*.md")))
                .isEqualTo("운영지침.md");
    }

    @Test
    @DisplayName("하위 폴더의 동명 파일은 서로 다른 키를 받는다")
    void sameFilenameInDifferentFoldersGetsDistinctKeys() throws IOException {
        Resource a = write("부서A/운영지침.md");
        Resource b = write("부서B/운영지침.md");
        String glob = pattern("**/*.md");

        String keyA = EgovDocumentIdResolver.resolveKey(a, glob);
        String keyB = EgovDocumentIdResolver.resolveKey(b, glob);

        assertThat(keyA).isEqualTo("부서A/운영지침.md");
        assertThat(keyB).isEqualTo("부서B/운영지침.md");
        assertThat(keyA).isNotEqualTo(keyB);
    }

    @Test
    @DisplayName("여러 단계 하위 폴더도 경로가 유지된다")
    void nestedFoldersArePreserved() throws IOException {
        Resource resource = write("본청/기획과/2026/운영지침.md");

        assertThat(EgovDocumentIdResolver.resolveKey(resource, pattern("**/*.md")))
                .isEqualTo("본청/기획과/2026/운영지침.md");
    }

    @Test
    @DisplayName("공백은 기존 규칙대로 붙임표로 바뀌고 경로 구분자는 남는다")
    void whitespaceIsReplacedPerSegment() throws IOException {
        Resource resource = write("부서 A/운영 지침.md");

        assertThat(EgovDocumentIdResolver.resolveKey(resource, pattern("**/*.md")))
                .isEqualTo("부서-A/운영-지침.md");
    }

    @Test
    @DisplayName("확장자를 뺀 키는 마지막 확장자만 제거한다")
    void keyWithoutExtensionDropsOnlyTheLastSuffix() throws IOException {
        Resource nested = write("부서A/2026.운영지침.hwp");

        assertThat(EgovDocumentIdResolver.resolveKeyWithoutExtension(nested, pattern("**/*.hwp")))
                .isEqualTo("부서A/2026.운영지침");
    }

    @Test
    @DisplayName("확장자가 없는 파일은 키가 그대로 유지된다")
    void keyWithoutExtensionKeepsNameWhenNoSuffix() throws IOException {
        Resource resource = write("부서A/운영지침");

        assertThat(EgovDocumentIdResolver.resolveKeyWithoutExtension(resource, pattern("**/*")))
                .isEqualTo("부서A/운영지침");
    }

    @Test
    @DisplayName("기본 디렉터리를 알 수 없으면 파일명으로 되돌아간다")
    void unknownBaseDirFallsBackToFilename() throws IOException {
        Resource resource = write("부서A/운영지침.md");

        assertThat(EgovDocumentIdResolver.resolveKey(resource, "classpath:/data/**/*.md"))
                .isEqualTo("운영지침.md");
        assertThat(EgovDocumentIdResolver.resolveKey(resource, null))
                .isEqualTo("운영지침.md");
    }

    @Test
    @DisplayName("스캔 경로 밖의 파일은 파일명으로 되돌아간다")
    void resourceOutsideBaseDirFallsBackToFilename() throws IOException {
        Resource outside = new FileSystemResource(
                Files.writeString(Files.createTempFile("outside-", ".md"), "본문"));

        assertThat(EgovDocumentIdResolver.resolveKey(outside, pattern("**/*.md")))
                .isEqualTo(outside.getFilename());
    }

    @Test
    @DisplayName("글로브 이전까지가 기본 디렉터리다")
    void baseDirIsThePartBeforeTheGlob() {
        assertThat(EgovDocumentIdResolver.resolveBaseDir("file:/srv/upload/data/**/*.md"))
                .isEqualTo("/srv/upload/data");
        assertThat(EgovDocumentIdResolver.resolveBaseDir("file:C:/workspace-test/upload/data/**/*.pdf"))
                .isEqualTo("C:/workspace-test/upload/data");
        assertThat(EgovDocumentIdResolver.resolveBaseDir("classpath:/data/**/*.md")).isNull();
        assertThat(EgovDocumentIdResolver.resolveBaseDir("   ")).isNull();
    }

    /** 기본 디렉터리 밑에 파일을 만들고 자원으로 돌려준다. */
    private Resource write(String relativePath) throws IOException {
        Path target = baseDir.resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.writeString(target, "본문");
        return new FileSystemResource(target);
    }

    /** 기본 디렉터리에 글로브를 붙인 스캔 패턴. */
    private String pattern(String glob) {
        return "file:" + baseDir.toAbsolutePath().toString().replace('\\', '/') + "/" + glob;
    }
}
