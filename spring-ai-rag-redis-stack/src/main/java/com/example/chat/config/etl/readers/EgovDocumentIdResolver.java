package com.example.chat.config.etl.readers;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.springframework.core.io.Resource;

/**
 * 문서 id 에 쓸 키를 만든다.
 * <p>
 * 리더가 스캔하는 경로 패턴은 {@code file:.../data/**}{@code /*.md} 처럼 재귀 글로브라
 * 하위 폴더에 같은 이름의 파일을 둘 수 있다. 파일명만으로 id 를 만들면 그 파일들이 같은 id 를
 * 받아 서로의 청크를 지우게 되므로, 글로브 이전의 기본 디렉터리를 기준으로 한 상대 경로를 쓴다.
 * <p>
 * 기본 디렉터리 바로 밑에 있는 파일은 상대 경로가 파일명과 같으므로 기존 id 가 그대로 유지된다.
 */
public final class EgovDocumentIdResolver {

    /** 파일명이나 경로 조각에서 id 로 쓸 수 없는 문자를 제거한다. */
    private static final String ILLEGAL_CHARS = "[\\\\/:*?\"<>|]";

    private EgovDocumentIdResolver() {
    }

    /**
     * 경로 패턴에서 글로브 이전의 기본 디렉터리를 얻는다.
     *
     * @param pathPattern 리더가 사용하는 경로 패턴
     * @return 기본 디렉터리. 패턴이 비었거나 파일 경로가 아니면 {@code null}
     */
    public static String resolveBaseDir(String pathPattern) {
        if (pathPattern == null || pathPattern.isBlank()) {
            return null;
        }
        String path = pathPattern.trim();
        if (path.startsWith("file:")) {
            path = path.substring("file:".length());
        } else if (path.indexOf(':') > 1) {
            // file: 이외의 프로토콜(classpath: 등)은 파일 시스템 경로가 아니다. (드라이브 문자 "C:" 는 허용)
            return null;
        }
        int star = path.indexOf('*');
        int question = path.indexOf('?');
        int wildcard = (star < 0) ? question : (question < 0 ? star : Math.min(star, question));
        if (wildcard >= 0) {
            int lastSlash = Math.max(path.lastIndexOf('/', wildcard), path.lastIndexOf('\\', wildcard));
            if (lastSlash < 0) {
                return null;
            }
            path = path.substring(0, lastSlash);
        }
        return path.isBlank() ? null : path;
    }

    /**
     * 문서 id 에 쓸 키를 만든다.
     * <p>
     * 기본 디렉터리를 알 수 없거나 파일 시스템 자원이 아니면 파일명으로 되돌아간다.
     *
     * @param resource    읽고 있는 자원
     * @param pathPattern 그 자원을 찾은 경로 패턴
     * @return id 에 쓸 키. 파일명을 알 수 없으면 {@code null}
     */
    public static String resolveKey(Resource resource, String pathPattern) {
        if (resource == null) {
            return null;
        }
        String filename = resource.getFilename();
        if (filename == null) {
            return null;
        }
        String relativePath = relativePathOf(resource, pathPattern);
        return sanitizePath(relativePath == null ? filename : relativePath);
    }

    /**
     * 확장자를 뺀 키를 만든다. 파일명 대신 상대 경로를 쓰는 것 외에는 호출부의 기존 규칙과 같다.
     *
     * @param resource    읽고 있는 자원
     * @param pathPattern 그 자원을 찾은 경로 패턴
     * @return 확장자를 뺀 키. 파일명을 알 수 없으면 {@code null}
     */
    public static String resolveKeyWithoutExtension(Resource resource, String pathPattern) {
        String key = resolveKey(resource, pathPattern);
        if (key == null) {
            return null;
        }
        int dot = key.lastIndexOf('.');
        int separator = key.lastIndexOf('/');
        return (dot > separator + 1) ? key.substring(0, dot) : key;
    }

    /**
     * 경로 없이 주어진 이름을 그대로 키로 정리한다. 기본 디렉터리를 알 수 없는 호출부가 쓴다.
     *
     * @param name 파일명 또는 경로
     * @return id 에 쓸 키
     */
    public static String sanitizeKey(String name) {
        return name == null ? null : sanitizePath(name);
    }

    /** 기본 디렉터리 기준 상대 경로. 기준을 잡을 수 없으면 {@code null}. */
    private static String relativePathOf(Resource resource, String pathPattern) {
        String baseDir = resolveBaseDir(pathPattern);
        if (baseDir == null) {
            return null;
        }
        try {
            String base = normalize(new File(baseDir).getCanonicalPath());
            String file = normalize(resource.getFile().getCanonicalPath());
            if (file.startsWith(base + "/")) {
                return file.substring(base.length() + 1);
            }
        } catch (IOException | RuntimeException e) {
            // 파일 시스템 자원이 아니거나 경로를 확정할 수 없으면 파일명으로 되돌아간다
            return null;
        }
        return null;
    }

    private static String normalize(String path) {
        return path.replace('\\', '/');
    }

    /** 경로 구분자는 유지하고 각 조각만 정리한다. 조각이 하나면 기존 파일명 규칙과 결과가 같다. */
    private static String sanitizePath(String path) {
        return Arrays.stream(normalize(path).split("/"))
                .filter(segment -> !segment.isEmpty())
                .map(EgovDocumentIdResolver::sanitizeSegment)
                .collect(Collectors.joining("/"));
    }

    private static String sanitizeSegment(String segment) {
        return segment.replaceAll(ILLEGAL_CHARS, "").replaceAll("\\s+", "-");
    }
}
