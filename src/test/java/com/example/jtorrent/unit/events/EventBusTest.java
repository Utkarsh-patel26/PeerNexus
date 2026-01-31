package com.example.jtorrent.unit.events;

import com.example.jtorrent.events.EventBus;
import com.example.jtorrent.events.TorrentEvent;
import com.example.jtorrent.events.PieceCompletedEvent;
import com.example.jtorrent.events.PeerConnectedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.*;

class EventBusTest {

    private EventBus eventBus;

    @BeforeEach
    void setUp() {
        eventBus = EventBus.getInstance();
    }

    @Nested
    class SubscribeTests {

        @Test
        void subscribeAddsHandlerToEventType() {
            AtomicBoolean called = new AtomicBoolean(false);
            Consumer<PieceCompletedEvent> handler = e -> called.set(true);

            int initialCount = eventBus.getSubscriberCount(PieceCompletedEvent.class);
            eventBus.subscribe(PieceCompletedEvent.class, handler);

            assertThat(eventBus.getSubscriberCount(PieceCompletedEvent.class))
                    .isEqualTo(initialCount + 1);

            eventBus.unsubscribe(PieceCompletedEvent.class, handler);
        }

        @Test
        void subscribeMultipleHandlersForSameEventType() {
            AtomicInteger callCount = new AtomicInteger(0);
            Consumer<PieceCompletedEvent> handler1 = e -> callCount.incrementAndGet();
            Consumer<PieceCompletedEvent> handler2 = e -> callCount.incrementAndGet();

            int initialCount = eventBus.getSubscriberCount(PieceCompletedEvent.class);
            eventBus.subscribe(PieceCompletedEvent.class, handler1);
            eventBus.subscribe(PieceCompletedEvent.class, handler2);

            assertThat(eventBus.getSubscriberCount(PieceCompletedEvent.class))
                    .isEqualTo(initialCount + 2);

            eventBus.unsubscribe(PieceCompletedEvent.class, handler1);
            eventBus.unsubscribe(PieceCompletedEvent.class, handler2);
        }

        @Test
        void subscribeDifferentEventTypes() {
            Consumer<PieceCompletedEvent> pieceHandler = e -> {
            };
            Consumer<PeerConnectedEvent> peerHandler = e -> {
            };

            int pieceCount = eventBus.getSubscriberCount(PieceCompletedEvent.class);
            int peerCount = eventBus.getSubscriberCount(PeerConnectedEvent.class);

            eventBus.subscribe(PieceCompletedEvent.class, pieceHandler);
            eventBus.subscribe(PeerConnectedEvent.class, peerHandler);

            assertThat(eventBus.getSubscriberCount(PieceCompletedEvent.class))
                    .isEqualTo(pieceCount + 1);
            assertThat(eventBus.getSubscriberCount(PeerConnectedEvent.class))
                    .isEqualTo(peerCount + 1);

            eventBus.unsubscribe(PieceCompletedEvent.class, pieceHandler);
            eventBus.unsubscribe(PeerConnectedEvent.class, peerHandler);
        }
    }

    @Nested
    class UnsubscribeTests {

        @Test
        void unsubscribeRemovesHandler() {
            Consumer<PieceCompletedEvent> handler = e -> {
            };

            eventBus.subscribe(PieceCompletedEvent.class, handler);
            int afterSubscribe = eventBus.getSubscriberCount(PieceCompletedEvent.class);

            eventBus.unsubscribe(PieceCompletedEvent.class, handler);

            assertThat(eventBus.getSubscriberCount(PieceCompletedEvent.class))
                    .isEqualTo(afterSubscribe - 1);
        }

        @Test
        void unsubscribeNonExistentHandlerDoesNotThrow() {
            Consumer<PieceCompletedEvent> handler = e -> {
            };

            assertThatCode(() -> eventBus.unsubscribe(PieceCompletedEvent.class, handler))
                    .doesNotThrowAnyException();
        }

        @Test
        void unsubscribeFromNonExistentEventTypeDoesNotThrow() {
            Consumer<PieceCompletedEvent> handler = e -> {
            };

            assertThatCode(() -> eventBus.unsubscribe(PieceCompletedEvent.class, handler))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    class PublishTests {

        @Test
        void publishDeliversEventToSubscribedHandlers() {
            AtomicBoolean received = new AtomicBoolean(false);
            byte[] testHash = new byte[20];
            Consumer<PieceCompletedEvent> handler = e -> received.set(true);

            eventBus.subscribe(PieceCompletedEvent.class, handler);
            eventBus.publish(new PieceCompletedEvent(testHash, 0, 16384L, true));

            assertThat(received.get()).isTrue();

            eventBus.unsubscribe(PieceCompletedEvent.class, handler);
        }

        @Test
        void publishToMultipleHandlers() {
            AtomicInteger callCount = new AtomicInteger(0);
            byte[] testHash = new byte[20];
            Consumer<PieceCompletedEvent> handler1 = e -> callCount.incrementAndGet();
            Consumer<PieceCompletedEvent> handler2 = e -> callCount.incrementAndGet();
            Consumer<PieceCompletedEvent> handler3 = e -> callCount.incrementAndGet();

            eventBus.subscribe(PieceCompletedEvent.class, handler1);
            eventBus.subscribe(PieceCompletedEvent.class, handler2);
            eventBus.subscribe(PieceCompletedEvent.class, handler3);

            eventBus.publish(new PieceCompletedEvent(testHash, 0, 16384L, true));

            assertThat(callCount.get()).isEqualTo(3);

            eventBus.unsubscribe(PieceCompletedEvent.class, handler1);
            eventBus.unsubscribe(PieceCompletedEvent.class, handler2);
            eventBus.unsubscribe(PieceCompletedEvent.class, handler3);
        }

        @Test
        void publishDoesNotAffectOtherEventTypes() {
            AtomicBoolean pieceHandlerCalled = new AtomicBoolean(false);
            AtomicBoolean peerHandlerCalled = new AtomicBoolean(false);
            byte[] testHash = new byte[20];

            Consumer<PieceCompletedEvent> pieceHandler = e -> pieceHandlerCalled.set(true);
            Consumer<PeerConnectedEvent> peerHandler = e -> peerHandlerCalled.set(true);

            eventBus.subscribe(PieceCompletedEvent.class, pieceHandler);
            eventBus.subscribe(PeerConnectedEvent.class, peerHandler);

            eventBus.publish(new PieceCompletedEvent(testHash, 0, 16384L, true));

            assertThat(pieceHandlerCalled.get()).isTrue();
            assertThat(peerHandlerCalled.get()).isFalse();

            eventBus.unsubscribe(PieceCompletedEvent.class, pieceHandler);
            eventBus.unsubscribe(PeerConnectedEvent.class, peerHandler);
        }

        @Test
        void publishWithNoSubscribersDoesNotThrow() {
            byte[] testHash = new byte[20];

            assertThatCode(() -> eventBus.publish(new PieceCompletedEvent(testHash, 0, 16384L, true)))
                    .doesNotThrowAnyException();
        }

        @Test
        void handlerExceptionDoesNotStopOtherHandlers() {
            AtomicBoolean firstHandlerCalled = new AtomicBoolean(false);
            AtomicBoolean thirdHandlerCalled = new AtomicBoolean(false);
            byte[] testHash = new byte[20];

            Consumer<PieceCompletedEvent> handler1 = e -> firstHandlerCalled.set(true);
            Consumer<PieceCompletedEvent> handler2 = e -> {
                throw new RuntimeException("Test exception");
            };
            Consumer<PieceCompletedEvent> handler3 = e -> thirdHandlerCalled.set(true);

            eventBus.subscribe(PieceCompletedEvent.class, handler1);
            eventBus.subscribe(PieceCompletedEvent.class, handler2);
            eventBus.subscribe(PieceCompletedEvent.class, handler3);

            assertThatCode(() -> eventBus.publish(new PieceCompletedEvent(testHash, 0, 16384L, true)))
                    .doesNotThrowAnyException();
            assertThat(firstHandlerCalled.get()).isTrue();

            eventBus.unsubscribe(PieceCompletedEvent.class, handler1);
            eventBus.unsubscribe(PieceCompletedEvent.class, handler2);
            eventBus.unsubscribe(PieceCompletedEvent.class, handler3);
        }
    }

    @Nested
    class AsyncPublishTests {

        @Test
        @Timeout(5)
        void publishAsyncDeliversEventAsynchronously() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(1);
            byte[] testHash = new byte[20];
            Consumer<PieceCompletedEvent> handler = e -> latch.countDown();

            eventBus.subscribe(PieceCompletedEvent.class, handler);
            eventBus.publishAsync(new PieceCompletedEvent(testHash, 0, 16384L, true));

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();

            eventBus.unsubscribe(PieceCompletedEvent.class, handler);
        }

        @Test
        @Timeout(5)
        void publishAsyncDoesNotBlockCaller() throws InterruptedException {
            CountDownLatch handlerStarted = new CountDownLatch(1);
            CountDownLatch handlerComplete = new CountDownLatch(1);
            byte[] testHash = new byte[20];

            Consumer<PieceCompletedEvent> slowHandler = e -> {
                handlerStarted.countDown();
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {
                }
                handlerComplete.countDown();
            };

            eventBus.subscribe(PieceCompletedEvent.class, slowHandler);

            long startTime = System.currentTimeMillis();
            eventBus.publishAsync(new PieceCompletedEvent(testHash, 0, 16384L, true));
            long publishTime = System.currentTimeMillis() - startTime;

            assertThat(publishTime).isLessThan(100);
            assertThat(handlerComplete.await(2, TimeUnit.SECONDS)).isTrue();

            eventBus.unsubscribe(PieceCompletedEvent.class, slowHandler);
        }
    }

    @Nested
    class ConcurrencyTests {

        @RepeatedTest(5)
        @Timeout(10)
        void concurrentSubscribeAndPublish() throws InterruptedException {
            int threadCount = 10;
            int eventsPerThread = 100;
            AtomicInteger receiveCount = new AtomicInteger(0);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch completeLatch = new CountDownLatch(threadCount);
            byte[] testHash = new byte[20];

            Consumer<PieceCompletedEvent> handler = e -> receiveCount.incrementAndGet();
            eventBus.subscribe(PieceCompletedEvent.class, handler);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int j = 0; j < eventsPerThread; j++) {
                            eventBus.publish(new PieceCompletedEvent(testHash, j, 16384L, true));
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

            assertThat(receiveCount.get()).isEqualTo(threadCount * eventsPerThread);

            eventBus.unsubscribe(PieceCompletedEvent.class, handler);
        }

        @RepeatedTest(3)
        @Timeout(10)
        void concurrentSubscribeUnsubscribe() throws InterruptedException {
            int threadCount = 20;
            CyclicBarrier barrier = new CyclicBarrier(threadCount);
            List<Consumer<PieceCompletedEvent>> handlers = Collections.synchronizedList(new ArrayList<>());
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        barrier.await();
                        Consumer<PieceCompletedEvent> handler = e -> {
                        };
                        handlers.add(handler);

                        if (index % 2 == 0) {
                            eventBus.subscribe(PieceCompletedEvent.class, handler);
                            Thread.sleep(10);
                            eventBus.unsubscribe(PieceCompletedEvent.class, handler);
                        } else {
                            eventBus.subscribe(PieceCompletedEvent.class, handler);
                        }
                    } catch (Exception e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);

            for (Consumer<PieceCompletedEvent> handler : handlers) {
                eventBus.unsubscribe(PieceCompletedEvent.class, handler);
            }
        }

        @Test
        @Timeout(10)
        void publishDuringSubscription() throws InterruptedException {
            int iterations = 1000;
            AtomicInteger publishCount = new AtomicInteger(0);
            byte[] testHash = new byte[20];

            Consumer<PieceCompletedEvent> baseHandler = e -> {
            };
            eventBus.subscribe(PieceCompletedEvent.class, baseHandler);

            ExecutorService executor = Executors.newFixedThreadPool(2);

            executor.submit(() -> {
                for (int i = 0; i < iterations; i++) {
                    eventBus.publish(new PieceCompletedEvent(testHash, i, 16384L, true));
                    publishCount.incrementAndGet();
                }
            });

            executor.submit(() -> {
                for (int i = 0; i < iterations; i++) {
                    Consumer<PieceCompletedEvent> tempHandler = e -> {
                    };
                    eventBus.subscribe(PieceCompletedEvent.class, tempHandler);
                    eventBus.unsubscribe(PieceCompletedEvent.class, tempHandler);
                }
            });

            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);

            assertThat(publishCount.get()).isEqualTo(iterations);

            eventBus.unsubscribe(PieceCompletedEvent.class, baseHandler);
        }
    }

    @Nested
    class SingletonTests {

        @Test
        void getInstanceReturnsSameInstance() {
            EventBus instance1 = EventBus.getInstance();
            EventBus instance2 = EventBus.getInstance();

            assertThat(instance1).isSameAs(instance2);
        }
    }

    @Nested
    class SubscriberCountTests {

        @Test
        void getSubscriberCountReturnsZeroForUnknownEventType() {
            assertThat(eventBus.getSubscriberCount(TestEvent.class))
                    .isGreaterThanOrEqualTo(0);
        }

        @ParameterizedTest
        @ValueSource(ints = { 1, 5, 10, 50 })
        void getSubscriberCountReflectsActualSubscribers(int count) {
            List<Consumer<TestEvent>> handlers = new ArrayList<>();
            int initialCount = eventBus.getSubscriberCount(TestEvent.class);

            for (int i = 0; i < count; i++) {
                Consumer<TestEvent> handler = e -> {
                };
                handlers.add(handler);
                eventBus.subscribe(TestEvent.class, handler);
            }

            assertThat(eventBus.getSubscriberCount(TestEvent.class))
                    .isEqualTo(initialCount + count);

            for (Consumer<TestEvent> handler : handlers) {
                eventBus.unsubscribe(TestEvent.class, handler);
            }
        }
    }

    private static class TestEvent extends TorrentEvent {
        @Override
        public byte[] getInfoHash() {
            return new byte[20];
        }
    }
}
