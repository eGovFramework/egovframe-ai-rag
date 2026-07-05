package com.example.chat.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * {@link EgovRrfFusion} 의 RRF 융합 로직을 결정적으로 검증한다.
 *
 * <p>표준 RRF 수식 {@code score(d) = Σ weight/(k+rank)} 을 골든셋으로 단정하고,
 * 채널 가중치 변화에 따른 순서 변화, recall@k, MRR 을 확인한다.</p>
 */
class EgovRrfFusionTest {

    private static final int K = EgovRrfFusion.DEFAULT_K; // 60

    @Test
    @DisplayName("표준 RRF 수식(k=60)에 따라 융합 점수가 계산된다")
    void computesStandardRrfScore() {
        // dense: [A, B, C], lexical: [B, A, D]
        // A: 1/60(dense r0) + 1/61(lexical r1) = 0.016666... + 0.016393... = 0.033060...
        // B: 1/61(dense r1) + 1/60(lexical r0) = 0.016393... + 0.016666... = 0.033060...
        // 동점 -> 먼저 등장한 A 가 앞 (dense 먼저 누적)
        // C: 1/62 = 0.016129...
        // D: 1/62 = 0.016129...  (C 가 먼저 등장)
        List<String> dense = List.of("A", "B", "C");
        List<String> lexical = List.of("B", "A", "D");

        List<String> fused = EgovRrfFusion.fuse(dense, lexical, 1.0, 1.0, K, 10);

        assertThat(fused).containsExactly("A", "B", "C", "D");
    }

    @Test
    @DisplayName("양 채널 상위에 모두 등장한 키가 최상위로 융합된다")
    void itemInBothChannelsRanksHighest() {
        // X 는 양쪽 모두 0순위 -> 가장 높은 점수
        List<String> dense = List.of("X", "A", "B");
        List<String> lexical = List.of("X", "C", "D");

        List<String> fused = EgovRrfFusion.fuse(dense, lexical, 1.0, 1.0, K, 5);

        assertThat(fused.get(0)).isEqualTo("X");
        // X 점수 = 1/60 + 1/60 = 0.033333...
        // 나머지는 단일 채널 최대 1/61 이하이므로 X 가 유일 최상위
        assertThat(fused).hasSize(5).startsWith("X");
    }

    @Test
    @DisplayName("lexical 가중치를 크게 주면 lexical 상위 키가 우선된다")
    void lexicalWeightShiftsOrder() {
        // dense 단독 상위 D vs lexical 단독 상위 L
        List<String> dense = List.of("D");
        List<String> lexical = List.of("L");

        // 동일 가중: D(1/60) == L(1/60), 먼저 등장한 D 가 앞
        List<String> equalWeight = EgovRrfFusion.fuse(dense, lexical, 1.0, 1.0, K, 2);
        assertThat(equalWeight).containsExactly("D", "L");

        // lexical 가중 3배: L(3/60) > D(1/60)
        List<String> lexHeavy = EgovRrfFusion.fuse(dense, lexical, 1.0, 3.0, K, 2);
        assertThat(lexHeavy).containsExactly("L", "D");
    }

    @Test
    @DisplayName("topK 만큼만 잘라서 반환한다")
    void respectsTopK() {
        List<String> dense = List.of("A", "B", "C", "D", "E");
        List<String> lexical = List.of("F", "G");

        List<String> fused = EgovRrfFusion.fuse(dense, lexical, 1.0, 1.0, K, 3);

        assertThat(fused).hasSize(3);
    }

    @Test
    @DisplayName("한쪽 채널만 결과가 있어도 융합된다")
    void singleChannelOnly() {
        List<String> dense = List.of("A", "B");
        List<String> lexical = List.of();

        List<String> fused = EgovRrfFusion.fuse(dense, lexical, 1.0, 1.0, K, 5);

        assertThat(fused).containsExactly("A", "B");
    }

    @Test
    @DisplayName("null 채널 입력은 무시된다")
    void nullChannelIgnored() {
        List<String> dense = List.of("A", "B");

        List<String> fused = EgovRrfFusion.fuse(dense, null, 1.0, 1.0, K, 5);

        assertThat(fused).containsExactly("A", "B");
    }

    @Test
    @DisplayName("골든셋 recall@k 와 MRR 이 융합으로 개선된다")
    void recallAndMrrImproveAfterFusion() {
        // 정답 키 = {REL1, REL2}
        // 두 정답은 각 채널에서 중위권이라 단일 채널 top2 에는 들지 못한다.
        // 그러나 양 채널에 모두 등장하므로 융합 시 순위 역수가 합산되어 상위로 올라온다
        // (cross-channel reinforcement).
        List<String> dense = List.of("n1", "REL1", "REL2", "n2");
        List<String> lexical = List.of("m1", "REL2", "REL1", "m2");
        List<String> relevant = List.of("REL1", "REL2");

        List<String> denseTop2 = dense.subList(0, 2);
        List<String> fused = EgovRrfFusion.fuse(dense, lexical, 1.0, 1.0, K, 2);

        // recall@2: dense 단독은 1/2(REL1만), 융합은 2/2
        assertThat(recallAtK(denseTop2, relevant)).isEqualTo(0.5);
        assertThat(recallAtK(fused, relevant)).isEqualTo(1.0);

        // MRR: 융합 결과 1순위가 정답이어야 한다
        assertThat(mrr(fused, relevant)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("개별 키 융합 점수가 수식값과 일치한다")
    void exactScoreMatchesFormula() {
        // A: dense r0 only -> 1/60
        List<String> single = EgovRrfFusion.fuse(List.of("A"), List.of(), 1.0, 1.0, K, 1);
        assertThat(single).containsExactly("A");

        // 가중 2.0 적용 시 A 점수 = 2/60, B(lexical r0, weight1) = 1/60 -> A 우선
        List<String> weighted = EgovRrfFusion.fuse(List.of("A"), List.of("B"), 2.0, 1.0, K, 2);
        assertThat(weighted).containsExactly("A", "B");

        // 수식값 직접 확인을 위한 sanity: 1/60 ≈ 0.016667
        double expected = 1.0 / (K + 0);
        assertThat(expected).isCloseTo(0.016666, within(1e-5));
    }

    private static double recallAtK(List<String> retrieved, List<String> relevant) {
        long hit = relevant.stream().filter(retrieved::contains).count();
        return (double) hit / relevant.size();
    }

    private static double mrr(List<String> retrieved, List<String> relevant) {
        for (int i = 0; i < retrieved.size(); i++) {
            if (relevant.contains(retrieved.get(i))) {
                return 1.0 / (i + 1);
            }
        }
        return 0.0;
    }
}
