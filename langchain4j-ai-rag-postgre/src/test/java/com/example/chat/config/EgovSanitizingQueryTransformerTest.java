package com.example.chat.config;

import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link EgovSanitizingQueryTransformer} 의 후처리 동작을 검증한다.
 *
 * <p>위임 대상은 고정 문자열을 돌려주는 스텁이라 Ollama·네트워크 없이 실행된다.
 * 검증 대상은 {@code <think>} 블록 제거와 답변형 결과의 원본 복원 두 가지다.</p>
 */
class EgovSanitizingQueryTransformerTest {

    private static final String ORIGINAL = "세 번째 사항의 예시를 알려 줘";

    /** 재작성 결과를 그대로 돌려주는 스텁. */
    private static QueryTransformer stub(String rewritten) {
        return query -> List.of(Query.from(rewritten));
    }

    private static Query transformOne(String rewritten) {
        Collection<Query> out = new EgovSanitizingQueryTransformer(stub(rewritten))
                .transform(Query.from(ORIGINAL));
        assertThat(out).hasSize(1);
        return out.iterator().next();
    }

    @Test
    @DisplayName("정상 재작성 결과는 그대로 통과한다")
    void passesThroughCleanRewrite() {
        String rewritten = "전자정부 실행환경 AOP의 Pointcut 표현식 예시";
        assertThat(transformOne(rewritten).text()).isEqualTo(rewritten);
    }

    @Test
    @DisplayName("<think>...</think> 블록이 있으면 제거하고 뒤쪽 텍스트를 쓴다")
    void stripsThinkBlock() {
        String rewritten = "<think>사용자가 세 번째 항목을 물었다. 히스토리에서 찾자.</think>\n"
                + "전자정부 실행환경 AOP의 Pointcut 표현식";
        assertThat(transformOne(rewritten).text())
                .isEqualTo("전자정부 실행환경 AOP의 Pointcut 표현식")
                .doesNotContain("<think>");
    }

    @Test
    @DisplayName("닫는 </think> 가 없어도 여는 태그 이후를 사용한다")
    void stripsUnclosedThinkBlock() {
        String rewritten = "<think>이건 후속 질문이다 전자정부 실행환경 AOP의 Pointcut 표현식";
        assertThat(transformOne(rewritten).text())
                .isEqualTo("이건 후속 질문이다 전자정부 실행환경 AOP의 Pointcut 표현식");
    }

    @Test
    @DisplayName("</think> 가 여러 번 나오면 마지막 블록 뒤를 쓴다")
    void stripsNestedThinkBlocks() {
        String rewritten = "<think>a</think><think>b</think>실제 질의";
        assertThat(transformOne(rewritten).text()).isEqualTo("실제 질의");
    }

    @Test
    @DisplayName("코드 블록이 섞인 답변형 결과는 원본 질의로 복원한다")
    void fallsBackWhenAnswerContainsCodeBlock() {
        String rewritten = "다음처럼 쓰면 됩니다.\n```java\n@Around(\"execution(* *(..))\")\n```";
        assertThat(transformOne(rewritten).text()).isEqualTo(ORIGINAL);
    }

    @Test
    @DisplayName("200자를 넘는 결과는 답변으로 보고 원본 질의로 복원한다")
    void fallsBackWhenTooLong() {
        String rewritten = "가".repeat(EgovSanitizingQueryTransformer.MAX_REWRITTEN_LENGTH + 1);
        assertThat(transformOne(rewritten).text()).isEqualTo(ORIGINAL);
    }

    @Test
    @DisplayName("200자 이하 경계값은 답변으로 보지 않는다")
    void keepsRewriteAtLengthBoundary() {
        String rewritten = "가".repeat(EgovSanitizingQueryTransformer.MAX_REWRITTEN_LENGTH);
        assertThat(transformOne(rewritten).text()).isEqualTo(rewritten);
    }

    @Test
    @DisplayName("여러 줄 설명형 결과도 원본 질의로 복원한다")
    void fallsBackOnMultilineExplanation() {
        String rewritten = "AOP는 관점 지향 프로그래밍입니다.\n예시는 아래와 같습니다.";
        assertThat(transformOne(rewritten).text()).isEqualTo(ORIGINAL);
    }

    @Test
    @DisplayName("<think> 제거 후 남는 텍스트가 없으면 원본 질의로 복원한다")
    void fallsBackWhenNothingLeftAfterStrip() {
        assertThat(transformOne("<think>고민만 하고 끝났다</think>   ").text()).isEqualTo(ORIGINAL);
    }

    @Test
    @DisplayName("위임 결과가 비어 있으면 원본 질의를 사용한다")
    void fallsBackOnEmptyDelegateResult() {
        Collection<Query> out = new EgovSanitizingQueryTransformer(query -> List.of())
                .transform(Query.from(ORIGINAL));
        assertThat(out).extracting(Query::text).containsExactly(ORIGINAL);
    }

    @Test
    @DisplayName("위임 결과가 여러 건이면 각각을 정리한다")
    void sanitizesEachOfMultipleQueries() {
        QueryTransformer multi = query -> List.of(
                Query.from("<think>t</think>정상 재작성 질의"),
                Query.from("설명: 이건 답변입니다"));
        Collection<Query> out = new EgovSanitizingQueryTransformer(multi).transform(Query.from(ORIGINAL));
        assertThat(out).extracting(Query::text).containsExactly("정상 재작성 질의", ORIGINAL);
    }

    @Test
    @DisplayName("delegate 가 null 이면 생성 시점에 거부한다")
    void rejectsNullDelegate() {
        assertThatThrownBy(() -> new EgovSanitizingQueryTransformer(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

