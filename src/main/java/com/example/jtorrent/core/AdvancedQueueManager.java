package com.example.jtorrent.core;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class AdvancedQueueManager implements AutoCloseable {
    private final Map<String, QueueEntry> entries = new ConcurrentHashMap<>();
    private final PriorityBlockingQueue<QueueEntry> downloadQueue;
    private final PriorityBlockingQueue<QueueEntry> seedQueue;
    private final TorrentSessionManager sessionManager;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final List<Consumer<QueueEvent>> listeners = new CopyOnWriteArrayList<>();

    private volatile int maxActiveDownloads = 3;
    private volatile int maxActiveSeeds = 5;
    private volatile double targetSeedRatio = 1.0;
    private volatile Duration targetSeedTime = Duration.ZERO;
    private volatile boolean autoManageEnabled = true;

    public AdvancedQueueManager(TorrentSessionManager sessionManager) {
        this.sessionManager = sessionManager;

        Comparator<QueueEntry> priorityComparator = Comparator
                .comparingInt(QueueEntry::getPriority).reversed()
                .thenComparing(QueueEntry::getQueuedAt);

        this.downloadQueue = new PriorityBlockingQueue<>(11, priorityComparator);
        this.seedQueue = new PriorityBlockingQueue<>(11, priorityComparator);

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "advanced-queue-manager");
            t.setDaemon(true);
            return t;
        });
    }

    public void addListener(Consumer<QueueEvent> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<QueueEvent> listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(QueueEvent event) {
        for (Consumer<QueueEvent> listener : listeners) {
            try {
                listener.accept(event);
            } catch (Exception ignored) {
            }
        }
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            scheduler.scheduleAtFixedRate(this::processQueues, 1, 5, TimeUnit.SECONDS);
        }
    }

    public void stop() {
        running.set(false);
    }

    public void add(String infoHash, int priority, boolean autoStart) {
        QueueEntry entry = new QueueEntry(infoHash, priority);
        entries.put(infoHash, entry);
        downloadQueue.offer(entry);
        notifyListeners(new QueueEntryAddedEvent(infoHash, priority));

        if (autoStart && autoManageEnabled) {
            processQueues();
        }
    }

    public void remove(String infoHash) {
        QueueEntry entry = entries.remove(infoHash);
        if (entry != null) {
            downloadQueue.remove(entry);
            seedQueue.remove(entry);
            if (entry.getState() == QueueState.ACTIVE) {
                try {
                    sessionManager.stopTorrent(infoHash);
                } catch (Exception ignored) {
                }
            }
            notifyListeners(new QueueEntryRemovedEvent(infoHash));
        }
    }

    public void setPriority(String infoHash, int priority) {
        QueueEntry entry = entries.get(infoHash);
        if (entry != null) {
            entry.setPriority(priority);
            requeue(entry);
            notifyListeners(new PriorityChangedEvent(infoHash, priority));
        }
    }

    public void forceStart(String infoHash) {
        QueueEntry entry = entries.get(infoHash);
        if (entry != null && entry.getState() == QueueState.QUEUED) {
            startEntry(entry);
            notifyListeners(new ForceStartEvent(infoHash));
        }
    }

    public void pause(String infoHash) {
        QueueEntry entry = entries.get(infoHash);
        if (entry != null && entry.getState() == QueueState.ACTIVE) {
            try {
                sessionManager.stopTorrent(infoHash);
            } catch (Exception ignored) {
            }
            entry.setState(QueueState.PAUSED);
            notifyListeners(new PausedEvent(infoHash));
            processQueues();
        }
    }

    public void resume(String infoHash) {
        QueueEntry entry = entries.get(infoHash);
        if (entry != null && entry.getState() == QueueState.PAUSED) {
            entry.setState(QueueState.QUEUED);
            requeue(entry);
            notifyListeners(new ResumedEvent(infoHash));
            processQueues();
        }
    }

    public void moveToTop(String infoHash) {
        int maxPriority = entries.values().stream()
                .mapToInt(QueueEntry::getPriority)
                .max().orElse(0);
        setPriority(infoHash, maxPriority + 1);
    }

    public void moveToBottom(String infoHash) {
        setPriority(infoHash, 0);
    }

    public void moveUp(String infoHash) {
        QueueEntry entry = entries.get(infoHash);
        if (entry != null) {
            setPriority(infoHash, entry.getPriority() + 1);
        }
    }

    public void moveDown(String infoHash) {
        QueueEntry entry = entries.get(infoHash);
        if (entry != null) {
            setPriority(infoHash, Math.max(0, entry.getPriority() - 1));
        }
    }

    public void markCompleted(String infoHash) {
        QueueEntry entry = entries.get(infoHash);
        if (entry != null) {
            downloadQueue.remove(entry);
            entry.setIsSeeding(true);
            entry.setSeedingStartedAt(Instant.now());
            seedQueue.offer(entry);
            notifyListeners(new DownloadCompletedEvent(infoHash));
            processQueues();
        }
    }

    public Optional<QueueEntry> getEntry(String infoHash) {
        return Optional.ofNullable(entries.get(infoHash));
    }

    public List<QueueEntry> getDownloadQueue() {
        return downloadQueue.stream()
                .sorted(Comparator.comparingInt(QueueEntry::getPriority).reversed())
                .collect(Collectors.toList());
    }

    public List<QueueEntry> getSeedQueue() {
        return seedQueue.stream()
                .sorted(Comparator.comparingInt(QueueEntry::getPriority).reversed())
                .collect(Collectors.toList());
    }

    public List<QueueEntry> getActive() {
        return entries.values().stream()
                .filter(e -> e.getState() == QueueState.ACTIVE)
                .collect(Collectors.toList());
    }

    public QueueStats getStats() {
        int downloading = 0, seeding = 0, queued = 0, paused = 0;
        for (QueueEntry entry : entries.values()) {
            switch (entry.getState()) {
                case ACTIVE -> {
                    if (entry.isSeeding())
                        seeding++;
                    else
                        downloading++;
                }
                case QUEUED -> queued++;
                case PAUSED -> paused++;
                case STOPPED -> {
                    /* Not counted in active stats */ }
            }
        }
        return new QueueStats(downloading, seeding, queued, paused,
                maxActiveDownloads, maxActiveSeeds);
    }

    public void setMaxActiveDownloads(int max) {
        this.maxActiveDownloads = max;
        processQueues();
    }

    public void setMaxActiveSeeds(int max) {
        this.maxActiveSeeds = max;
        processQueues();
    }

    public void setTargetSeedRatio(double ratio) {
        this.targetSeedRatio = ratio;
    }

    public void setTargetSeedTime(Duration duration) {
        this.targetSeedTime = duration;
    }

    public void setAutoManageEnabled(boolean enabled) {
        this.autoManageEnabled = enabled;
        if (enabled) {
            processQueues();
        }
    }

    private void processQueues() {
        if (!running.get() || !autoManageEnabled) {
            return;
        }

        checkSeedingGoals();
        startQueuedDownloads();
        startQueuedSeeds();
    }

    private void checkSeedingGoals() {
        for (QueueEntry entry : seedQueue) {
            if (entry.getState() != QueueState.ACTIVE) {
                continue;
            }

            if (targetSeedRatio > 0) {
                TorrentSession session = sessionManager.getSession(entry.getInfoHash());
                if (session != null && session.getSeedRatio() >= targetSeedRatio) {
                    stopEntry(entry);
                    continue;
                }
            }

            if (!targetSeedTime.isZero() && entry.getSeedingStartedAt() != null) {
                Duration seedingDuration = Duration.between(
                        entry.getSeedingStartedAt(), Instant.now());
                if (seedingDuration.compareTo(targetSeedTime) >= 0) {
                    stopEntry(entry);
                }
            }
        }
    }

    private void startQueuedDownloads() {
        long activeDownloads = countActive(e -> !e.isSeeding());
        while (activeDownloads < maxActiveDownloads) {
            QueueEntry next = downloadQueue.stream()
                    .filter(e -> e.getState() == QueueState.QUEUED)
                    .findFirst()
                    .orElse(null);

            if (next == null) {
                break;
            }

            startEntry(next);
            activeDownloads++;
        }
    }

    private void startQueuedSeeds() {
        long activeSeeds = countActive(QueueEntry::isSeeding);
        while (activeSeeds < maxActiveSeeds) {
            QueueEntry next = seedQueue.stream()
                    .filter(e -> e.getState() == QueueState.QUEUED)
                    .findFirst()
                    .orElse(null);

            if (next == null) {
                break;
            }

            startEntry(next);
            activeSeeds++;
        }
    }

    private void startEntry(QueueEntry entry) {
        try {
            sessionManager.startTorrent(entry.getInfoHash());
            entry.setState(QueueState.ACTIVE);
            entry.setStartedAt(Instant.now());
        } catch (Exception ignored) {
        }
    }

    private void stopEntry(QueueEntry entry) {
        try {
            sessionManager.stopTorrent(entry.getInfoHash());
            entry.setState(QueueState.STOPPED);
        } catch (Exception ignored) {
        }
    }

    private void requeue(QueueEntry entry) {
        if (entry.isSeeding()) {
            seedQueue.remove(entry);
            seedQueue.offer(entry);
        } else {
            downloadQueue.remove(entry);
            downloadQueue.offer(entry);
        }
    }

    private long countActive(Predicate<QueueEntry> filter) {
        return entries.values().stream()
                .filter(e -> e.getState() == QueueState.ACTIVE)
                .filter(filter)
                .count();
    }

    @Override
    public void close() {
        stop();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public enum QueueState {
        QUEUED, ACTIVE, PAUSED, STOPPED
    }

    public static final class QueueEntry {
        private final String infoHash;
        private final Instant queuedAt;
        private volatile int priority;
        private volatile QueueState state = QueueState.QUEUED;
        private volatile Instant startedAt;
        private volatile Instant seedingStartedAt;
        private volatile boolean isSeeding = false;

        public QueueEntry(String infoHash, int priority) {
            this.infoHash = infoHash;
            this.priority = priority;
            this.queuedAt = Instant.now();
        }

        public String getInfoHash() {
            return infoHash;
        }

        public int getPriority() {
            return priority;
        }

        public void setPriority(int priority) {
            this.priority = priority;
        }

        public QueueState getState() {
            return state;
        }

        public void setState(QueueState state) {
            this.state = state;
        }

        public Instant getQueuedAt() {
            return queuedAt;
        }

        public Instant getStartedAt() {
            return startedAt;
        }

        public void setStartedAt(Instant startedAt) {
            this.startedAt = startedAt;
        }

        public Instant getSeedingStartedAt() {
            return seedingStartedAt;
        }

        public void setSeedingStartedAt(Instant seedingStartedAt) {
            this.seedingStartedAt = seedingStartedAt;
        }

        public boolean isSeeding() {
            return isSeeding;
        }

        public void setIsSeeding(boolean seeding) {
            isSeeding = seeding;
        }
    }

    public record QueueStats(int downloading, int seeding, int queued, int paused,
            int maxDownloads, int maxSeeds) {
    }

    public sealed interface QueueEvent permits QueueEntryAddedEvent, QueueEntryRemovedEvent,
            PriorityChangedEvent, ForceStartEvent, PausedEvent, ResumedEvent, DownloadCompletedEvent {
    }

    public record QueueEntryAddedEvent(String infoHash, int priority) implements QueueEvent {
    }

    public record QueueEntryRemovedEvent(String infoHash) implements QueueEvent {
    }

    public record PriorityChangedEvent(String infoHash, int priority) implements QueueEvent {
    }

    public record ForceStartEvent(String infoHash) implements QueueEvent {
    }

    public record PausedEvent(String infoHash) implements QueueEvent {
    }

    public record ResumedEvent(String infoHash) implements QueueEvent {
    }

    public record DownloadCompletedEvent(String infoHash) implements QueueEvent {
    }
}
