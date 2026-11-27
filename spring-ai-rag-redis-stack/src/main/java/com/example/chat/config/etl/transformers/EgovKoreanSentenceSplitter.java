package com.example.chat.config.etl.transformers;

import java.util.ArrayList;
import java.util.List;

import com.example.chat.util.EgovKoreanSentenceSupport;
import com.example.chat.util.EgovKoreanSentenceSupport.SentenceSpan;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;

import org.springframework.ai.transformer.splitter.TokenTextSplitter;

/**
 * 한국어 문장 경계를 우선 보존하는 Spring AI 문서 분할기.
 *
 * <p>{@link EgovKoreanSentenceSupport} 로 문장 경계를 먼저 나눈 뒤, Spring AI
 * {@link TokenTextSplitter} 와 동일한 CL100K_BASE 토크나이저로 청크 크기를 판정한다.
 * 청크는 원문 부분문자열이며 개행·표 구조가 보존된다.
 * 단일 문장이 청크 한도를 넘으면 원문 offset 창으로 잘라 나눠 모델 입력 한도를 지키면서도
 * 청크가 원문 부분문자열로 남게 한다. 보충면 문자 하나가 한도를 넘는 극단 설정(청크 크기 1~2)에서는
 * 어떤 창도 한도에 맞지 않으므로 코드포인트 하나를 그대로 내보내 분할이 끝나도록 한다. 기존 {@link TokenTextSplitter} 와의 정합을 위해
 * 오버랩은 두지 않는다.</p>
 */
public class EgovKoreanSentenceSplitter extends TokenTextSplitter {

    private final Encoding encoding;
    private final int chunkSize;
    private final int minChunkLengthToEmbed;
    private final int maxNumChunks;

    public EgovKoreanSentenceSplitter(int chunkSize, int minChunkSizeChars,
            int minChunkLengthToEmbed, int maxNumChunks, boolean keepSeparator) {
        super(chunkSize, minChunkSizeChars, minChunkLengthToEmbed, maxNumChunks, keepSeparator,
                List.of('.', '?', '!', '\n'));
        this.encoding = Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);
        this.chunkSize = chunkSize;
        this.minChunkLengthToEmbed = minChunkLengthToEmbed;
        this.maxNumChunks = maxNumChunks;
    }

    @Override
    protected List<String> splitText(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<SentenceSpan> spans = EgovKoreanSentenceSupport.splitSentenceSpans(text);
        List<String> chunks = new ArrayList<>();
        int bufferStart = -1;
        int bufferEnd = -1;

        for (int i = 0; i < spans.size(); i++) {
            SentenceSpan span = spans.get(i);
            String sentence = text.substring(span.start(), span.end());
            if (countTokens(sentence) > chunkSize) {
                if (bufferStart >= 0) {
                    addSpanChunk(chunks, text, spans, bufferStart, bufferEnd);
                    bufferStart = -1;
                    bufferEnd = -1;
                }
                if (chunks.size() >= maxNumChunks) {
                    break;
                }
                addOversizedSpanChunks(chunks, text, span);
                if (chunks.size() >= maxNumChunks) {
                    break;
                }
                continue;
            }

            if (bufferStart < 0) {
                bufferStart = i;
                bufferEnd = i;
                continue;
            }

            String candidate = text.substring(spans.get(bufferStart).start(), span.end());
            if (countTokens(candidate) <= chunkSize) {
                bufferEnd = i;
                continue;
            }

            addSpanChunk(chunks, text, spans, bufferStart, bufferEnd);
            if (chunks.size() >= maxNumChunks) {
                break;
            }
            bufferStart = i;
            bufferEnd = i;
        }

        if (chunks.size() < maxNumChunks && bufferStart >= 0) {
            addSpanChunk(chunks, text, spans, bufferStart, bufferEnd);
        }
        return List.copyOf(chunks);
    }

    // 한 문장이 토큰 한도를 넘으면 원문 offset 창으로 잘라 나눈다. 토큰 한도에 맞는 가장 긴 창을
    // 이진 탐색으로 찾고 가능하면 공백 위치로 물러나 자른다. 어느 경우에도 청크는 원문 부분문자열로 남는다.
    private void addOversizedSpanChunks(List<String> chunks, String text, SentenceSpan span) {
        int cursor = span.start();
        while (cursor < span.end()) {
            int cut = longestCutWithinTokenLimit(text, cursor, span.end());
            if (cut < span.end()) {
                int candidate = cut;
                while (candidate > cursor && !Character.isWhitespace(text.charAt(candidate))) {
                    candidate--;
                }
                if (candidate > cursor) {
                    cut = candidate;
                }
            }

            cut = alignToCodePointBoundary(text, cursor, cut);
            if (cut <= cursor) {
                // 토큰 한도에 맞는 창이 없거나 보정으로 창이 비면 한 코드포인트만큼 나아가 진행을 보장한다.
                cut = nextCodePointIndex(text, cursor);
            }

            addChunk(chunks, text.substring(cursor, cut).strip());
            if (chunks.size() >= maxNumChunks || cut >= span.end()) {
                return;
            }
            cursor = cut;
            while (cursor < span.end() && Character.isWhitespace(text.charAt(cursor))) {
                cursor++;
            }
        }
    }

    private static int nextCodePointIndex(String text, int index) {
        return index + Character.charCount(text.codePointAt(index));
    }

    // 서로게이트 페어 중간에서 자르면 짝 잃은 문자가 남으므로 코드포인트 경계로 물러난다.
    private static int alignToCodePointBoundary(String text, int start, int cut) {
        if (cut > start && cut < text.length() && Character.isLowSurrogate(text.charAt(cut))
                && Character.isHighSurrogate(text.charAt(cut - 1))) {
            return cut - 1;
        }
        return cut;
    }

    private int longestCutWithinTokenLimit(String text, int start, int end) {
        int low = start + 1;
        int high = end;
        int best = start;
        while (low <= high) {
            int mid = low + (high - low) / 2;
            // 토큰 계산기는 짝 잃은 서로게이트를 거부하므로 후보 위치를 먼저 코드포인트 경계로 맞춘다.
            int candidate = alignToCodePointBoundary(text, start, mid);
            if (candidate > start && countTokens(text.substring(start, candidate)) <= chunkSize) {
                best = candidate;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return best;
    }

    private void addSpanChunk(List<String> chunks, String text, List<SentenceSpan> spans,
            int startIndex, int endIndex) {
        addChunk(chunks, text.substring(spans.get(startIndex).start(), spans.get(endIndex).end()));
    }

    private void addChunk(List<String> chunks, String chunk) {
        if (chunks.size() >= maxNumChunks || chunk == null) {
            return;
        }

        String trimmed = chunk.trim();
        if (trimmed.length() > minChunkLengthToEmbed) {
            chunks.add(trimmed);
        }
    }

    private int countTokens(String text) {
        return encoding.countTokens(text);
    }
}
