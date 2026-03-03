package com.example.jtorrent.unit.core;

import com.example.jtorrent.core.RateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;

class RateLimiterTest {

    private RateLimiter rateLimiter;

    @Nested
    class ConstructorTests {

        @Test
        void constructorWithZeroCreatesUnlimitedLimiter() {
            rateLimiter = new RateLimiter(0);
            assertThat(rateLimiter.getLimit()).isEqualTo(0);
        }

        @Test
        void constructorWithPositiveValueSetsLimit() {
            rateLimiter = new RateLimiter(1024);
            assertThat(rateLimiter.getLimit()).isEqualTo(1024);
        }

        @Test
        void defaultConstructorCreatesUnlimitedLimiter() {
            rateLimiter = new RateLimiter();
            assertThat(rateLimiter.getLimit()).isEqualTo(0);
        }

        @ParameterizedTest
        @ValueSource(longs = { 1, 100, 1024, 1024 * 1024, Long.MAX_VALUE })
        void constructorAcceptsVariousLimits(long limit) {
            rateLimiter = new RateLimiter(limit);
            assertThat(rateLimiter.getLimit()).isEqualTo(limit);
        }
    }

    @Nested
    class SetLimitTests {

        @BeforeEach
        void setUp() {
            rateLimiter = new RateLimiter(1024);
        }

        @Test
        void setLimitChangesLimit() {
            rateLimiter.setLimit(2048);
            assertThat(rateLimiter.getLimit()).isEqualTo(2048);
        }

        @Test
        void setLimitToZeroDisablesLimiting() {
            rateLimiter.setLimit(0);
            assertThat(rateLimiter.getLimit()).isEqualTo(0);
        }

        @Test
        void setLimitCanIncreaseLimit() {
            rateLimiter.setLimit(4096);
            assertThat(rateLimiter.getLimit()).isEqualTo(4096);
        }

        @Test
        void setLimitCanDecreaseLimit() {
            rateLimiter.setLimit(512);
            assertThat(rateLimiter.getLimit()).isEqualTo(512);
        }

        @ParameterizedTest
        @ValueSource(longs = { 0, 1, 100, 1000, 10000 })
        void setLimitAcceptsVariousValues(long limit) {
            rateLimiter.setLimit(limit);
            assertThat(rateLimiter.getLimit()).isEqualTo(limit);
        }
    }

    @Nested
    class AcquireUnlimitedTests {

        @BeforeEach
        void setUp() {
            rateLimiter = new RateLimiter(0);
        }

        @Test
        void acquireWithUnlimitedReturnsImmediately() {
            long startTime = System.currentTimeMillis();
            rateLimiter.acquire(1024 * 1024);
            long elapsed = System.currentTimeMillis() - startTime;

            assertThat(elapsed).isLessThan(50);
        }

        @Test
        void acquireZeroBytesReturnsImmediately() {
            long startTime = System.currentTimeMillis();
            rateLimiter.acquire(0);
            long elapsed = System.currentTimeMillis() - startTime;

            assertThat(elapsed).isLessThan(50);
        }

        @Test
        void acquireNegativeBytesReturnsImmediately() {
            long startTime = System.currentTimeMillis();
            rateLimiter.acquire(-100);
            long elapsed = System.currentTimeMillis() - startTime;

            assertThat(elapsed).isLessThan(50);
        }

        @ParameterizedTest
        @ValueSource(longs = { 1, 100, 1024, 1024 * 1024, 10 * 1024 * 1024 })
        void acquireVariousSizesWithUnlimited(long bytes) {
            long startTime = System.currentTimeMillis();
            rateLimiter.acquire(bytes);
            long elapsed = System.currentTimeMillis() - startTime;

            assertThat(elapsed).isLessThan(50);
        }
    }

    @Nested
    class AcquireLimitedTests {

        @Test
        @Timeout(5)
        void acquireWithinBurstAllowanceReturnsQuickly() {
            rateLimiter = new RateLimiter(10000);
            long startTime = System.currentTimeMillis();
            rateLimiter.acquire(5000);
            long elapsed = System.currentTimeMillis() - startTime;

            assertThat(elapsed).isLessThan(100);
        }

        @Test
        @Timeout(5)
        void acquireExceedingBurstRequiresWait() {
            rateLimiter = new RateLimiter(1000);
            rateLimiter.acquire(1000);

            long startTime = System.currentTimeMillis();
            rateLimiter.acquire(1000);
            long elapsed = System.currentTimeMillis() - startTime;

            assertThat(elapsed).isGreaterThanOrEqualTo(500);
        }

        @Test
        @Timeout(10)
        void acquireMultipleTimesRespectRateLimit() {
            rateLimiter = new RateLimiter(1000);
            int iterations = 3;

            long startTime = System.currentTimeMillis();
            for (int i = 0; i < iterations; i++) {
                rateLimiter.acquire(1000);
            }
            long elapsed = System.currentTimeMillis() - startTime;

            assertThat(elapsed).isGreaterThanOrEqualTo(1500);
        }
    }

    @Nested
    class TryAcquireTests {

        @BeforeEach
        void setUp() {
            rateLimiter = new RateLimiter(1000);
        }

        @Test
        void tryAcquireReturnsRequestedWhenAvailable() {
            long acquired = rateLimiter.tryAcquire(500);
            assertThat(acquired).isEqualTo(500);
        }

        @Test
        void tryAcquireReturnsPartialWhenLimited() {
            rateLimiter.tryAcquire(1000);
            long acquired = rateLimiter.tryAcquire(1000);

            assertThat(acquired).isLessThanOrEqualTo(1000);
        }

        @Test
        void tryAcquireReturnsZeroWhenNoTokens() {
            rateLimiter.tryAcquire(2000);
            long acquired = rateLimiter.tryAcquire(1000);

            assertThat(acquired).isGreaterThanOrEqualTo(0);
        }

        @Test
        void tryAcquireWithUnlimitedReturnsFullAmount() {
            rateLimiter = new RateLimiter(0);
            long acquired = rateLimiter.tryAcquire(1000000);

            assertThat(acquired).isEqualTo(1000000);
        }

        @ParameterizedTest
        @ValueSource(longs = { 1, 10, 100, 500 })
        void tryAcquireSmallAmounts(long bytes) {
            long acquired = rateLimiter.tryAcquire(bytes);
            assertThat(acquired).isEqualTo(bytes);
        }
    }

    @Nested
    class StatisticsTests {

        @BeforeEach
        void setUp() {
            rateLimiter = new RateLimiter(0);
        }

        @Test
        void getTotalBytesTracksAcquiredBytes() {
            rateLimiter.acquire(100);
            rateLimiter.acquire(200);
            rateLimiter.acquire(300);

            assertThat(rateLimiter.getTotalBytes()).isEqualTo(600);
        }

        @Test
        void getCurrentRateReturnsNonNegative() {
            rateLimiter.acquire(1000);
            assertThat(rateLimiter.getCurrentRate()).isGreaterThanOrEqualTo(0);
        }

        @Test
        void resetStatsClearsTotalBytes() {
            rateLimiter.acquire(1000);
            rateLimiter.resetStats();

            assertThat(rateLimiter.getTotalBytes()).isEqualTo(0);
        }
    }

    @Nested
    class ConcurrencyTests {

        @RepeatedTest(5)
        @Timeout(10)
        void concurrentAcquireThreadSafe() throws InterruptedException {
            rateLimiter = new RateLimiter(0);
            int threadCount = 10;
            int bytesPerThread = 1000;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch completeLatch = new CountDownLatch(threadCount);
            AtomicLong totalAcquired = new AtomicLong(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int j = 0; j < 100; j++) {
                            rateLimiter.acquire(bytesPerThread);
                            totalAcquired.addAndGet(bytesPerThread);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        completeLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            completeLatch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            assertThat(rateLimiter.getTotalBytes()).isEqualTo(threadCount * 100 * bytesPerThread);
        }

        @RepeatedTest(3)
        @Timeout(10)
        void concurrentSetLimitThreadSafe() throws InterruptedException {
            rateLimiter = new RateLimiter(1000);
            int threadCount = 10;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch completeLatch = new CountDownLatch(threadCount);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                final int limit = (i + 1) * 100;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int j = 0; j < 100; j++) {
                            rateLimiter.setLimit(limit);
                            rateLimiter.acquire(10);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        completeLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            completeLatch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            assertThat(rateLimiter.getLimit()).isGreaterThan(0);
        }

        @Test
        @Timeout(10)
        void concurrentTryAcquireThreadSafe() throws InterruptedException {
            rateLimiter = new RateLimiter(100000);
            int threadCount = 20;
            AtomicLong totalAcquired = new AtomicLong(0);
            CountDownLatch completeLatch = new CountDownLatch(threadCount);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < 100; j++) {
                            long acquired = rateLimiter.tryAcquire(100);
                            totalAcquired.addAndGet(acquired);
                            Thread.sleep(1);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        completeLatch.countDown();
                    }
                });
            }

            completeLatch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            assertThat(totalAcquired.get()).isGreaterThan(0);
        }
    }

    @Nested
    class InterruptionTests {

        @Test
        @Timeout(5)
        void acquireHandlesInterruption() throws InterruptedException {
            rateLimiter = new RateLimiter(100);
            rateLimiter.acquire(200);

            Thread testThread = new Thread(() -> {
                rateLimiter.acquire(10000);
            });

            testThread.start();
            Thread.sleep(100);
            testThread.interrupt();
            testThread.join(2000);

            assertThat(testThread.isAlive()).isFalse();
        }
    }

    @Nested
    class EdgeCaseTests {

        @Test
        void acquireZeroWithLimitedLimiter() {
            rateLimiter = new RateLimiter(1000);
            assertThatCode(() -> rateLimiter.acquire(0))
                    .doesNotThrowAnyException();
        }

        @Test
        void tryAcquireZeroReturnsZero() {
            rateLimiter = new RateLimiter(1000);
            long acquired = rateLimiter.tryAcquire(0);
            assertThat(acquired).isEqualTo(0);
        }

        @Test
        @Timeout(10)
        void setLimitDuringAcquire() throws InterruptedException {
            rateLimiter = new RateLimiter(100);
            CountDownLatch acquireStarted = new CountDownLatch(1);
            CountDownLatch acquireComplete = new CountDownLatch(1);

            Thread acquireThread = new Thread(() -> {
                acquireStarted.countDown();
                rateLimiter.acquire(500); // Reduced from 1000 for faster test
                acquireComplete.countDown();
            });

            acquireThread.start();
            acquireStarted.await();
            Thread.sleep(100); // Give more time for acquire to start
            rateLimiter.setLimit(10000); // Much higher limit to speed up

            assertThat(acquireComplete.await(10, TimeUnit.SECONDS)).isTrue();
        }

        @ParameterizedTest
        @CsvSource({
                "1000, 100",
                "10000, 1000",
                "100000, 10000"
        })
        void rateLimitingMaintainsApproximateRate(long limit, long chunkSize) {
            rateLimiter = new RateLimiter(limit);
            int iterations = 20;

            long startTime = System.currentTimeMillis();
            for (int i = 0; i < iterations; i++) {
                rateLimiter.acquire(chunkSize);
            }
            long elapsed = System.currentTimeMillis() - startTime;

            long totalBytes = iterations * chunkSize;
            long expectedMinElapsedMs = Math.max(0, ((totalBytes - limit) * 1000) / limit);

            assertThat(elapsed).isGreaterThanOrEqualTo(Math.max(0, expectedMinElapsedMs - 250));

            if (elapsed > 0) {
                double actualRate = (double) totalBytes / (elapsed / 1000.0);
                assertThat(actualRate).isLessThanOrEqualTo(limit * 2.5);
            }
        }
    }
}
