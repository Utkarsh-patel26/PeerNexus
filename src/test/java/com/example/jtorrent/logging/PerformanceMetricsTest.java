package com.example.jtorrent.logging;

import org.junit.jupiter.api.*;

import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for PerformanceMetrics.
 */
@DisplayName("PerformanceMetrics Tests")
class PerformanceMetricsTest {

    private PerformanceMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = new PerformanceMetrics(0); // Disable periodic logging
    }

    @AfterEach
    void tearDown() {
        if (metrics != null) {
            metrics.close();
        }
    }

    @Nested
    @DisplayName("Construction Tests")
    class ConstructionTests {

        @Test
        @DisplayName("Should create metrics with default interval")
        void shouldCreateMetricsWithDefaultInterval() {
            PerformanceMetrics m = new PerformanceMetrics();

            assertNotNull(m);
            m.close();
        }

        @Test
        @DisplayName("Should create metrics with custom interval")
        void shouldCreateMetricsWithCustomInterval() {
            PerformanceMetrics m = new PerformanceMetrics(30);

            assertNotNull(m);
            m.close();
        }

        @Test
        @DisplayName("Should create metrics with disabled logging")
        void shouldCreateMetricsWithDisabledLogging() {
            PerformanceMetrics m = new PerformanceMetrics(0);

            assertNotNull(m);
            m.close();
        }
    }

    @Nested
    @DisplayName("Counter Tests")
    class CounterTests {

        @Test
        @DisplayName("Should increment counter")
        void shouldIncrementCounter() {
            metrics.incrementCounter("test");

            assertEquals(1, metrics.getCounter("test"));
        }

        @Test
        @DisplayName("Should increment counter multiple times")
        void shouldIncrementCounterMultipleTimes() {
            metrics.incrementCounter("test");
            metrics.incrementCounter("test");
            metrics.incrementCounter("test");

            assertEquals(3, metrics.getCounter("test"));
        }

        @Test
        @DisplayName("Should increment counter by amount")
        void shouldIncrementCounterByAmount() {
            metrics.incrementCounter("test", 10);
            metrics.incrementCounter("test", 5);

            assertEquals(15, metrics.getCounter("test"));
        }

        @Test
        @DisplayName("Should return zero for unknown counter")
        void shouldReturnZeroForUnknownCounter() {
            assertEquals(0, metrics.getCounter("nonexistent"));
        }

        @Test
        @DisplayName("Should track multiple counters")
        void shouldTrackMultipleCounters() {
            metrics.incrementCounter("downloads");
            metrics.incrementCounter("uploads");
            metrics.incrementCounter("downloads");

            assertEquals(2, metrics.getCounter("downloads"));
            assertEquals(1, metrics.getCounter("uploads"));
        }
    }

    @Nested
    @DisplayName("Gauge Tests")
    class GaugeTests {

        @Test
        @DisplayName("Should set gauge value")
        void shouldSetGaugeValue() {
            metrics.setGauge("connections", 42);

            assertEquals(42, metrics.getGauge("connections"));
        }

        @Test
        @DisplayName("Should update gauge value")
        void shouldUpdateGaugeValue() {
            metrics.setGauge("connections", 10);
            metrics.setGauge("connections", 20);

            assertEquals(20, metrics.getGauge("connections"));
        }

        @Test
        @DisplayName("Should update gauge by delta")
        void shouldUpdateGaugeByDelta() {
            metrics.setGauge("peers", 100);
            metrics.updateGauge("peers", 50);
            metrics.updateGauge("peers", -20);

            assertEquals(130, metrics.getGauge("peers"));
        }

        @Test
        @DisplayName("Should return zero for unknown gauge")
        void shouldReturnZeroForUnknownGauge() {
            assertEquals(0, metrics.getGauge("nonexistent"));
        }

        @Test
        @DisplayName("Should track multiple gauges")
        void shouldTrackMultipleGauges() {
            metrics.setGauge("memory", 1024);
            metrics.setGauge("cpu", 75);

            assertEquals(1024, metrics.getGauge("memory"));
            assertEquals(75, metrics.getGauge("cpu"));
        }
    }

    @Nested
    @DisplayName("Histogram Tests")
    class HistogramTests {

        @Test
        @DisplayName("Should record duration")
        void shouldRecordDuration() {
            metrics.recordDuration("request_time", 100);

            PerformanceMetrics.Histogram histogram = metrics.getHistogram("request_time");
            assertNotNull(histogram);
            assertEquals(1, histogram.getCount());
        }

        @Test
        @DisplayName("Should record multiple durations")
        void shouldRecordMultipleDurations() {
            metrics.recordDuration("latency", 50);
            metrics.recordDuration("latency", 100);
            metrics.recordDuration("latency", 150);

            PerformanceMetrics.Histogram histogram = metrics.getHistogram("latency");
            assertEquals(3, histogram.getCount());
        }

        @Test
        @DisplayName("Should calculate min value")
        void shouldCalculateMinValue() {
            metrics.recordDuration("time", 100);
            metrics.recordDuration("time", 50);
            metrics.recordDuration("time", 150);

            PerformanceMetrics.Histogram histogram = metrics.getHistogram("time");
            assertEquals(50, histogram.getMin());
        }

        @Test
        @DisplayName("Should calculate max value")
        void shouldCalculateMaxValue() {
            metrics.recordDuration("time", 100);
            metrics.recordDuration("time", 50);
            metrics.recordDuration("time", 150);

            PerformanceMetrics.Histogram histogram = metrics.getHistogram("time");
            assertEquals(150, histogram.getMax());
        }

        @Test
        @DisplayName("Should calculate average")
        void shouldCalculateAverage() {
            metrics.recordDuration("time", 100);
            metrics.recordDuration("time", 200);
            metrics.recordDuration("time", 300);

            PerformanceMetrics.Histogram histogram = metrics.getHistogram("time");
            assertEquals(200.0, histogram.getAverage(), 0.01);
        }

        @Test
        @DisplayName("Should calculate percentiles")
        void shouldCalculatePercentiles() {
            for (int i = 1; i <= 100; i++) {
                metrics.recordDuration("percentile_test", i);
            }

            PerformanceMetrics.Histogram histogram = metrics.getHistogram("percentile_test");

            long p50 = histogram.getPercentile(50);
            long p99 = histogram.getPercentile(99);

            assertTrue(p50 >= 40 && p50 <= 60);
            assertTrue(p99 >= 90 && p99 <= 100);
        }

        @Test
        @DisplayName("Should return null for unknown histogram")
        void shouldReturnNullForUnknownHistogram() {
            assertNull(metrics.getHistogram("nonexistent"));
        }
    }

    @Nested
    @DisplayName("Reset Tests")
    class ResetTests {

        @Test
        @DisplayName("Should reset all metrics")
        void shouldResetAllMetrics() {
            metrics.incrementCounter("test");
            metrics.setGauge("gauge", 100);
            metrics.recordDuration("time", 50);

            metrics.reset();

            assertEquals(0, metrics.getCounter("test"));
            assertEquals(0, metrics.getGauge("gauge"));
            assertNull(metrics.getHistogram("time"));
        }
    }

    @Nested
    @DisplayName("Logging Tests")
    class LoggingTests {

        @Test
        @DisplayName("Should log metrics")
        void shouldLogMetrics() {
            metrics.incrementCounter("requests", 100);
            metrics.setGauge("connections", 50);
            metrics.recordDuration("latency", 25);

            assertDoesNotThrow(() -> metrics.logMetrics());
        }

        @Test
        @DisplayName("Should log empty metrics")
        void shouldLogEmptyMetrics() {
            assertDoesNotThrow(() -> metrics.logMetrics());
        }
    }

    @Nested
    @DisplayName("Periodic Logging Tests")
    class PeriodicLoggingTests {

        @Test
        @DisplayName("Should schedule periodic logging")
        void shouldSchedulePeriodicLogging() throws InterruptedException {
            PerformanceMetrics m = new PerformanceMetrics(1); // 1 second interval

            m.incrementCounter("test");

            // Wait for at least one log cycle
            Thread.sleep(1500);

            m.close();
        }
    }

    @Nested
    @DisplayName("Close Tests")
    class CloseTests {

        @Test
        @DisplayName("Should close cleanly")
        void shouldCloseCleanly() {
            assertDoesNotThrow(() -> {
                metrics.close();
                metrics = null;
            });
        }

        @Test
        @DisplayName("Should handle double close")
        void shouldHandleDoubleClose() {
            assertDoesNotThrow(() -> {
                metrics.close();
                metrics.close();
                metrics = null;
            });
        }

        @Test
        @DisplayName("Should stop periodic logging on close")
        void shouldStopPeriodicLoggingOnClose() throws InterruptedException {
            PerformanceMetrics m = new PerformanceMetrics(1);

            m.close();

            // Should shutdown quickly
            Thread.sleep(100);
        }
    }

    @Nested
    @DisplayName("Thread Safety Tests")
    class ThreadSafetyTests {

        @Test
        @DisplayName("Should handle concurrent counter updates")
        void shouldHandleConcurrentCounterUpdates() throws InterruptedException {
            int threadCount = 10;
            int incrementsPerThread = 1000;
            CountDownLatch latch = new CountDownLatch(threadCount);

            for (int t = 0; t < threadCount; t++) {
                new Thread(() -> {
                    for (int i = 0; i < incrementsPerThread; i++) {
                        metrics.incrementCounter("concurrent");
                    }
                    latch.countDown();
                }).start();
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS));

            assertEquals(threadCount * incrementsPerThread, metrics.getCounter("concurrent"));
        }

        @Test
        @DisplayName("Should handle concurrent gauge updates")
        void shouldHandleConcurrentGaugeUpdates() throws InterruptedException {
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount);

            metrics.setGauge("concurrent_gauge", 0);

            for (int t = 0; t < threadCount; t++) {
                new Thread(() -> {
                    for (int i = 0; i < 100; i++) {
                        metrics.updateGauge("concurrent_gauge", 1);
                    }
                    latch.countDown();
                }).start();
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS));

            assertEquals(1000, metrics.getGauge("concurrent_gauge"));
        }

        @Test
        @DisplayName("Should handle concurrent histogram updates")
        void shouldHandleConcurrentHistogramUpdates() throws InterruptedException {
            int threadCount = 10;
            int recordsPerThread = 100;
            CountDownLatch latch = new CountDownLatch(threadCount);

            for (int t = 0; t < threadCount; t++) {
                new Thread(() -> {
                    for (int i = 0; i < recordsPerThread; i++) {
                        metrics.recordDuration("concurrent_hist", i);
                    }
                    latch.countDown();
                }).start();
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS));

            PerformanceMetrics.Histogram histogram = metrics.getHistogram("concurrent_hist");
            assertEquals(threadCount * recordsPerThread, histogram.getCount());
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Should handle zero durations")
        void shouldHandleZeroDurations() {
            metrics.recordDuration("zero", 0);

            PerformanceMetrics.Histogram histogram = metrics.getHistogram("zero");
            assertEquals(0, histogram.getMin());
        }

        @Test
        @DisplayName("Should handle negative gauge deltas")
        void shouldHandleNegativeGaugeDeltas() {
            metrics.setGauge("test", 100);
            metrics.updateGauge("test", -50);

            assertEquals(50, metrics.getGauge("test"));
        }

        @Test
        @DisplayName("Should handle large counter values")
        void shouldHandleLargeCounterValues() {
            metrics.incrementCounter("large", Long.MAX_VALUE / 2);

            assertTrue(metrics.getCounter("large") > 0);
        }

        @Test
        @DisplayName("Should handle many different metrics")
        void shouldHandleManyDifferentMetrics() {
            for (int i = 0; i < 1000; i++) {
                metrics.incrementCounter("counter_" + i);
                metrics.setGauge("gauge_" + i, i);
                metrics.recordDuration("hist_" + i, i);
            }

            assertEquals(1, metrics.getCounter("counter_500"));
            assertEquals(500, metrics.getGauge("gauge_500"));
            assertNotNull(metrics.getHistogram("hist_500"));
        }
    }
}
