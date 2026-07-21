package com.example.chat.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RRF(Reciprocal Rank Fusion) 융합 유틸리티
 *
 * <p>여러 검색 채널(예: dense 벡터 검색, lexical 키워드 검색)이 각각 반환한
 * 순위 리스트를 하나의 순위로 융합한다. 각 키의 융합 점수는 채널별 순위의
 * 역수 합으로 계산한다.</p>
 *
 * <pre>
 *   score(d) = Σ_channel  weight / (k + rank)
 * </pre>
 *
 * <p>여기서 {@code rank}는 해당 채널에서 키가 등장한 0-based 순위이며,
 * {@code k}는 상위권 항목의 영향력을 완화하는 상수(표준값 60)이다.
 * 외부 의존성이 없는 순수 함수로, 동일 입력에 대해 항상 동일한 순서를 반환한다.</p>
 */
public final class EgovRrfFusion {

    /** RRF 표준 상수. 상위 순위 항목의 가중치를 완화한다. */
    public static final int DEFAULT_K = 60;

    private EgovRrfFusion() {
    }

    /**
     * 두 채널(dense, lexical)의 순위 리스트를 RRF로 융합한다.
     *
     * @param denseRanking   dense 채널이 반환한 키 순서(상위가 앞)
     * @param lexicalRanking lexical 채널이 반환한 키 순서(상위가 앞)
     * @param denseWeight    dense 채널 가중치
     * @param lexicalWeight  lexical 채널 가중치
     * @param k              RRF 상수(일반적으로 {@link #DEFAULT_K})
     * @param topK           반환할 상위 키 개수
     * @param <T>            융합 키 타입
     * @return 융합 점수 내림차순으로 정렬된 상위 {@code topK} 키
     */
    public static <T> List<T> fuse(List<T> denseRanking,
                                   List<T> lexicalRanking,
                                   double denseWeight,
                                   double lexicalWeight,
                                   int k,
                                   int topK) {
        // 키 등장 순서를 보존해 동점 시 결정적 정렬을 보장한다.
        Map<T, Double> scores = new LinkedHashMap<>();
        accumulate(scores, denseRanking, denseWeight, k);
        accumulate(scores, lexicalRanking, lexicalWeight, k);

        return scores.entrySet().stream()
                // 점수 내림차순. 동점은 LinkedHashMap의 삽입 순서(=등장 순서)를 유지한다.
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(Math.max(topK, 0))
                .map(Map.Entry::getKey)
                .toList();
    }

    private static <T> void accumulate(Map<T, Double> scores, List<T> ranking, double weight, int k) {
        if (ranking == null) {
            return;
        }
        for (int rank = 0; rank < ranking.size(); rank++) {
            T key = ranking.get(rank);
            if (key == null) {
                continue;
            }
            double contribution = weight / (k + rank);
            scores.merge(key, contribution, Double::sum);
        }
    }
}
