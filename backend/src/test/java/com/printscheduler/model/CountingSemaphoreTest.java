package com.printscheduler.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link CountingSemaphore}.
 *
 * <p>Dev 2 – Synchronization: verifies permit accounting, blocking/unblocking
 * behaviour, and safe concurrent producer-consumer usage.
 */
@DisplayName("CountingSemaphore")
class CountingSemaphoreTest {

    // ── Construction ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("initialises with the specified number of permits")
        void initialPermits() {
            CountingSemaphore sem = new CountingSemaphore(5);
            assertThat(sem.getPermits()).isEqualTo(5);
        }

        @Test
        @DisplayName("initialises with zero permits")
        void zeroPermits() {
            CountingSemaphore sem = new CountingSemaphore(0);
            assertThat(sem.getPermits()).isEqualTo(0);
        }

        @Test
        @DisplayName("throws when initial permits are negative")
        void negativePermitsThrows() {
            assertThatThrownBy(() -> new CountingSemaphore(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
        }
    }

    // ── Acquire / Release basic mechanics ────────────────────────────────

    @Nested
    @DisplayName("Acquire / Release")
    class AcquireRelease {

        @Test
        @DisplayName("acquire decrements permit count by 1")
        void acquireDecrementsPermit() throws InterruptedException {
            CountingSemaphore sem = new CountingSemaphore(3);
            sem.acquire();
            assertThat(sem.getPermits()).isEqualTo(2);
        }

        @Test
        @DisplayName("release increments permit count by 1")
        void releaseIncrementsPermit() throws InterruptedException {
            CountingSemaphore sem = new CountingSemaphore(1);
            sem.acquire();
            sem.release();
            assertThat(sem.getPermits()).isEqualTo(1);
        }

        @Test
        @DisplayName("multiple acquires drain all permits")
        void multipleAcquiresDrainPermits() throws InterruptedException {
            CountingSemaphore sem = new CountingSemaphore(3);
            sem.acquire();
            sem.acquire();
            sem.acquire();
            assertThat(sem.getPermits()).isEqualTo(0);
        }

        @Test
        @DisplayName("release beyond initial count increments permits above initial")
        void releaseAboveInitial() {
            CountingSemaphore sem = new CountingSemaphore(0);
            sem.release();
            sem.release();
            assertThat(sem.getPermits()).isEqualTo(2);
        }

        @Test
        @DisplayName("acquire then release returns to original count")
        void acquireReleaseCycle() throws InterruptedException {
            CountingSemaphore sem = new CountingSemaphore(2);
            sem.acquire();
            sem.acquire();
            sem.release();
            sem.release();
            assertThat(sem.getPermits()).isEqualTo(2);
        }
    }

    // ── Blocking behaviour ───────────────────────────────────────────────

    @Nested
    @DisplayName("Blocking behaviour")
    class Blocking {

        @Test
        @DisplayName("acquire blocks when no permits are available")
        @Timeout(value = 3)
        void acquireBlocksWhenEmpty() throws InterruptedException {
            CountingSemaphore sem = new CountingSemaphore(0);
            AtomicBoolean acquired = new AtomicBoolean(false);
            CountDownLatch ready = new CountDownLatch(1);

            Thread consumer = new Thread(() -> {
                try {
                    ready.countDown();
                    sem.acquire();
                    acquired.set(true);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            consumer.setDaemon(true);
            consumer.start();

            // wait for thread to start then confirm it is blocked
            ready.await();
            Thread.sleep(100);
            assertThat(acquired).isFalse();

            // unblock by releasing a permit
            sem.release();
            consumer.join(1_000);
            assertThat(acquired).isTrue();
        }

        @Test
        @DisplayName("acquire unblocks after release from another thread")
        @Timeout(value = 3)
        void acquireUnblocksOnRelease() throws InterruptedException {
            CountingSemaphore sem = new CountingSemaphore(0);
            CountDownLatch done = new CountDownLatch(1);

            Thread t = new Thread(() -> {
                try {
                    sem.acquire();
                    done.countDown();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            t.setDaemon(true);
            t.start();

            Thread.sleep(50);
            sem.release();

            assertThat(done.await(2, TimeUnit.SECONDS)).isTrue();
        }

        @Test
        @DisplayName("interrupted thread exits acquire with InterruptedException")
        @Timeout(value = 3)
        void interruptedAcquireThrows() throws InterruptedException {
            CountingSemaphore sem = new CountingSemaphore(0);
            AtomicBoolean interrupted = new AtomicBoolean(false);
            CountDownLatch ready = new CountDownLatch(1);

            Thread t = new Thread(() -> {
                try {
                    ready.countDown();
                    sem.acquire();
                } catch (InterruptedException e) {
                    interrupted.set(true);
                }
            });
            t.setDaemon(true);
            t.start();
            ready.await();
            Thread.sleep(50);
            t.interrupt();
            t.join(1_000);
            assertThat(interrupted).isTrue();
        }
    }

    // ── Concurrency: producer-consumer pattern ────────────────────────────

    @Nested
    @DisplayName("Concurrent producer-consumer")
    class ConcurrentProducerConsumer {

        /**
         * Simulates the exact pattern used by UserThread / PrinterThread:
         * N producers release, N consumers acquire – verifying no permits are lost.
         */
        @Test
        @DisplayName("N producers + N consumers yields zero permits at the end")
        @Timeout(value = 5)
        void producerConsumerBalanced() throws InterruptedException {
            int N = 50;
            CountingSemaphore sem = new CountingSemaphore(0);
            CountDownLatch produced = new CountDownLatch(N);
            CountDownLatch consumed = new CountDownLatch(N);

            // Producers
            for (int i = 0; i < N; i++) {
                Thread p = new Thread(() -> {
                    sem.release();
                    produced.countDown();
                });
                p.setDaemon(true);
                p.start();
            }

            // Consumers
            for (int i = 0; i < N; i++) {
                Thread c = new Thread(() -> {
                    try {
                        sem.acquire();
                        consumed.countDown();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
                c.setDaemon(true);
                c.start();
            }

            produced.await(3, TimeUnit.SECONDS);
            consumed.await(3, TimeUnit.SECONDS);
            assertThat(sem.getPermits()).isEqualTo(0);
        }

        @Test
        @DisplayName("concurrent releases do not lose permit increments")
        @Timeout(value = 5)
        void concurrentReleasesAreSafe() throws InterruptedException {
            CountingSemaphore sem = new CountingSemaphore(0);
            int N = 100;
            CountDownLatch latch = new CountDownLatch(N);

            for (int i = 0; i < N; i++) {
                Thread t = new Thread(() -> {
                    sem.release();
                    latch.countDown();
                });
                t.setDaemon(true);
                t.start();
            }
            latch.await(3, TimeUnit.SECONDS);
            assertThat(sem.getPermits()).isEqualTo(N);
        }

        @Test
        @DisplayName("getPermits is accurate under concurrent load")
        @Timeout(value = 5)
        void permitsAccurateUnderLoad() throws InterruptedException {
            CountingSemaphore sem = new CountingSemaphore(10);
            AtomicInteger completed = new AtomicInteger(0);
            int threads = 10;
            CountDownLatch done = new CountDownLatch(threads);

            for (int i = 0; i < threads; i++) {
                Thread t = new Thread(() -> {
                    try {
                        sem.acquire();
                        Thread.sleep(5);
                        sem.release();
                        completed.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
                t.setDaemon(true);
                t.start();
            }
            done.await(4, TimeUnit.SECONDS);
            assertThat(completed.get()).isEqualTo(threads);
            assertThat(sem.getPermits()).isEqualTo(10); // fully restored
        }
    }
}
