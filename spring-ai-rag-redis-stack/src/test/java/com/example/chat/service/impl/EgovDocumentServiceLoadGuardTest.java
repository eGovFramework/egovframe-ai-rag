package com.example.chat.service.impl;

import java.lang.reflect.Constructor;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@link EgovDocumentServiceImpl#loadDocumentsAsync()} 의 동시 진입 가드를 검증한다.
 *
 * <p>이전 구현은 {@code isProcessing.get()} 으로 검사한 뒤 별도로 {@code set(true)} 를
 * 호출하여 검사와 설정이 분리(TOCTOU)되어 있었다. 다수 스레드가 동시에 진입하면 모두
 * 가드를 통과해 중복 임베딩·벡터스토어 동시 쓰기가 발생할 수 있다. 본 테스트는 N개의
 * 스레드가 동시에 호출했을 때 단 하나만 처리에 진입함을 확인한다.</p>
 *
 * <p>비동기 본문은 실행하지 않는 {@code Executor}(작업을 버림)를 주입하여 외부 의존성
 * 호출 없이 진입 가드만 결정적으로 검증한다. 진입 가드는 {@code supplyAsync} 호출 전
 * 요청 스레드에서 동기적으로 평가되므로 본문 미실행과 무관하게 동작한다.</p>
 */
class EgovDocumentServiceLoadGuardTest {

    // 생성자 파라미터를 위치로 나열하지 않고 실제 생성자에서 읽어 채운다.
    // @RequiredArgsConstructor에 리더가 추가돼도(예: HWP/HWPX/DOCX) 영향을 받지 않는다.
    // Executor 인자만 비동기 본문을 실행하지 않는 noop으로 주입하고 나머지는 mock으로 채운다.
    private EgovDocumentServiceImpl newService() throws Exception {
        Constructor<?> ctor = EgovDocumentServiceImpl.class.getDeclaredConstructors()[0];
        Class<?>[] types = ctor.getParameterTypes();
        Object[] args = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            args[i] = types[i].equals(Executor.class) ? (Executor) command -> { /* 실행하지 않음 */ } : mock(types[i]);
        }
        ctor.setAccessible(true);
        EgovDocumentServiceImpl service = (EgovDocumentServiceImpl) ctor.newInstance(args);
        ReflectionTestUtils.setField(service, "documentPath", "classpath:/docs");
        return service;
    }

    @Test
    @DisplayName("다수 스레드가 동시 호출해도 처리 진입은 한 번만 허용된다")
    void onlyOneThreadAcquiresProcessing() throws Exception {
        EgovDocumentServiceImpl service = newService();

        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger entered = new AtomicInteger(0);

        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        // 본문이 실행되지 않으므로 가드를 통과한 호출은 0이 아닌
                        // (=처리 진입) 빈 Future 를 반환하지 않는다. completedFuture(0)
                        // 은 가드에 막힌 호출이다. 가드를 통과한 호출은 join 시 미완료
                        // 상태이므로 isDone() 으로 진입 여부를 구분한다.
                        CompletableFuture<Integer> future = service.loadDocumentsAsync();
                        if (!future.isDone()) {
                            entered.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(entered.get()).isEqualTo(1);
        assertThat(service.isProcessing()).isTrue();
    }

    @Test
    @DisplayName("이미 처리 중이면 호출은 즉시 완료된 0 Future 를 반환한다")
    void secondCallIsRejectedWhileProcessing() throws Exception {
        EgovDocumentServiceImpl service = newService();

        CompletableFuture<Integer> first = service.loadDocumentsAsync();
        CompletableFuture<Integer> second = service.loadDocumentsAsync();

        // 첫 호출은 처리에 진입(본문 미실행으로 미완료), 둘째 호출은 가드에 막혀 즉시 완료.
        assertThat(first.isDone()).isFalse();
        assertThat(second.isDone()).isTrue();
        assertThat(second.join()).isZero();
    }

    /** EgovAbstractServiceImpl 상속 확인용(컴파일 가드). */
    @SuppressWarnings("unused")
    private static final Class<? extends EgovAbstractServiceImpl> TYPE = EgovDocumentServiceImpl.class;
}
