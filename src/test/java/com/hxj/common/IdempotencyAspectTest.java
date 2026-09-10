package com.hxj.common;

import com.hxj.exception.BusinessException;
import com.hxj.security.AuthenticatedUserResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 幂等性切面测试：使用内存存储（Redis 不可用时降级路径）。
 */
class IdempotencyAspectTest {

    private IdempotencyAspect aspect;
    private MockHttpServletRequest request;
    private InMemoryIdempotencyStore memoryStore;

    @BeforeEach
    void setUp() {
        // 模拟 Redis 不可用（抛异常），验证降级到内存实现
        // 使用匿名子类而非 mock（Java 25 下 Mockito 无法 mock 具体类）
        RedisIdempotencyStore redisStore = new RedisIdempotencyStore(null) {
            @Override public boolean tryAcquire(String key, long ttlMillis) { throw new RuntimeException("Redis down"); }
            @Override public void release(String key) { throw new RuntimeException("Redis down"); }
        };
        memoryStore = new InMemoryIdempotencyStore();
        aspect = new IdempotencyAspect(new com.fasterxml.jackson.databind.ObjectMapper(), redisStore, memoryStore);

        request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        // 模拟已登录用户
        AuthenticatedUserResponse user = new AuthenticatedUserResponse(1L, "zhangsan", "张三", "财务部", "员工",
                List.of(), List.of(), List.of("OWN"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldRejectDuplicateRequestWithSameIdempotencyKey() throws Throwable {
        AtomicInteger executions = new AtomicInteger();
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenAnswer(invocation -> {
            executions.incrementAndGet();
            return "result-" + executions.get();
        });

        request.addHeader("Idempotency-Key", "key-1");

        Object first = aspect.around(joinPoint);
        assertThat(first).isEqualTo("result-1");

        // 相同 key 的重复请求被拒绝
        assertThatThrownBy(() -> aspect.around(joinPoint))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("重复请求");
        assertThat(executions.get()).isEqualTo(1);
    }

    @Test
    void shouldAutoDeduplicateSameArgumentsWithoutIdempotencyKey() throws Throwable {
        // 前端零配合：相同用户 + 相同方法 + 相同参数 → 自动 key，短窗口内去重
        AtomicInteger executions = new AtomicInteger();
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(
                org.aspectj.lang.reflect.MethodSignature.class.cast(
                        org.mockito.Mockito.mock(org.aspectj.lang.reflect.MethodSignature.class)));
        when(joinPoint.getSignature().toShortString()).thenReturn("Controller.submit(..)");
        when(joinPoint.getArgs()).thenReturn(new Object[]{"doc-1", BigDecimal.ONE});
        when(joinPoint.proceed()).thenAnswer(invocation -> {
            executions.incrementAndGet();
            return "result-" + executions.get();
        });

        Object first = aspect.around(joinPoint);
        assertThat(first).isEqualTo("result-1");

        // 相同参数自动去重
        assertThatThrownBy(() -> aspect.around(joinPoint))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("重复请求");
        assertThat(executions.get()).isEqualTo(1);
    }

    @Test
    void shouldAllowDifferentKeys() throws Throwable {
        AtomicInteger executions = new AtomicInteger();
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenAnswer(invocation -> {
            executions.incrementAndGet();
            return "result-" + executions.get();
        });

        request.addHeader("Idempotency-Key", "key-a");
        aspect.around(joinPoint);

        request = new MockHttpServletRequest();
        request.addHeader("Idempotency-Key", "key-b");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        Object result = aspect.around(joinPoint);
        assertThat(result).isEqualTo("result-2");
        assertThat(executions.get()).isEqualTo(2);
    }

    @Test
    void shouldExecuteOnlyOnceUnderConcurrentSameKey() throws Throwable {
        AtomicInteger executions = new AtomicInteger();
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenAnswer(invocation -> {
            executions.incrementAndGet();
            Thread.sleep(50); // 模拟业务耗时，放大竞态窗口
            return "result-" + executions.get();
        });

        request.addHeader("Idempotency-Key", "concurrent-key");

        int threads = 5;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = java.util.stream.IntStream.range(0, threads)
                    .mapToObj(i -> pool.submit(() -> {
                        // RequestContextHolder 是 ThreadLocal，每个线程需自行绑定
                        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
                        ready.countDown();
                        start.await();
                        try {
                            return aspect.around(joinPoint);
                        } catch (RuntimeException e) {
                            throw e;  // BusinessException 是 RuntimeException，直接抛出不被包装
                        } catch (Throwable t) {
                            throw new RuntimeException(t);
                        }
                    }))
                    .toList();

            ready.await();
            start.countDown();

            int successCount = 0;
            for (Future<Object> future : futures) {
                try {
                    future.get(5, TimeUnit.SECONDS);
                    successCount++;
                } catch (java.util.concurrent.ExecutionException ex) {
                    // 幂等拒绝的请求抛 BusinessException
                    assertThat(ex.getCause()).isInstanceOf(BusinessException.class);
                }
            }
            // 只有 1 个请求成功执行，其余被幂等拒绝
            assertThat(successCount).isEqualTo(1);
            assertThat(executions.get()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void shouldReleaseLockWhenBusinessFailsAndAllowRetry() throws Throwable {
        AtomicInteger executions = new AtomicInteger();
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenAnswer(invocation -> {
            if (executions.incrementAndGet() == 1) {
                throw new IllegalStateException("first attempt fails");
            }
            return "result-" + executions.get();
        });

        request.addHeader("Idempotency-Key", "retry-key");

        assertThatThrownBy(() -> aspect.around(joinPoint))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("first attempt fails");

        // 失败后占位已释放，重试可再次执行
        Object retry = aspect.around(joinPoint);
        assertThat(retry).isEqualTo("result-2");
        assertThat(executions.get()).isEqualTo(2);
    }
}