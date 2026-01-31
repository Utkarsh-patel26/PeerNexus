package com.example.jtorrent.automation;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

public final class ScheduleManager implements AutoCloseable {
    private static final Duration CHECK_INTERVAL = Duration.ofMinutes(1);

    private final Map<String, BandwidthSchedule> schedules = new ConcurrentHashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Path persistPath;
    private final ScheduledExecutorService executor;
    private final AtomicReference<ActiveSchedule> currentActive = new AtomicReference<>();
    private final List<Consumer<ScheduleEvent>> listeners = new CopyOnWriteArrayList<>();
    private volatile boolean running;

    public ScheduleManager(Path persistPath) {
        this.persistPath = persistPath;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "schedule-manager");
            t.setDaemon(true);
            return t;
        });
        load();
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        executor.scheduleAtFixedRate(this::checkSchedules,
                0, CHECK_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
    }

    public void stop() {
        running = false;
    }

    public void addListener(Consumer<ScheduleEvent> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<ScheduleEvent> listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(ScheduleEvent event) {
        for (Consumer<ScheduleEvent> listener : listeners) {
            try {
                listener.accept(event);
            } catch (Exception ignored) {
            }
        }
    }

    public void addSchedule(BandwidthSchedule schedule) {
        lock.writeLock().lock();
        try {
            schedules.put(schedule.getId(), schedule);
            persist();
            notifyListeners(new ScheduleAddedEvent(schedule));
            checkSchedules();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void removeSchedule(String scheduleId) {
        lock.writeLock().lock();
        try {
            BandwidthSchedule removed = schedules.remove(scheduleId);
            if (removed != null) {
                persist();
                notifyListeners(new ScheduleRemovedEvent(removed));
                checkSchedules();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void updateSchedule(BandwidthSchedule schedule) {
        lock.writeLock().lock();
        try {
            if (schedules.containsKey(schedule.getId())) {
                schedules.put(schedule.getId(), schedule);
                persist();
                notifyListeners(new ScheduleUpdatedEvent(schedule));
                checkSchedules();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Optional<BandwidthSchedule> getSchedule(String scheduleId) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(schedules.get(scheduleId));
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<BandwidthSchedule> getAllSchedules() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(schedules.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    public Optional<ActiveSchedule> getCurrentActiveSchedule() {
        return Optional.ofNullable(currentActive.get());
    }

    public long getCurrentDownloadLimit() {
        ActiveSchedule active = currentActive.get();
        return active != null ? active.schedule().getDownloadLimitBps() : -1;
    }

    public long getCurrentUploadLimit() {
        ActiveSchedule active = currentActive.get();
        return active != null ? active.schedule().getUploadLimitBps() : -1;
    }

    private void checkSchedules() {
        if (!running) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        DayOfWeek day = now.getDayOfWeek();
        LocalTime time = now.toLocalTime();

        lock.readLock().lock();
        try {
            Optional<BandwidthSchedule> active = schedules.values().stream()
                    .filter(s -> s.isActiveAt(day, time))
                    .max(Comparator.comparingInt(BandwidthSchedule::getPriority));

            ActiveSchedule previous = currentActive.get();
            if (active.isPresent()) {
                BandwidthSchedule schedule = active.get();
                if (previous == null || !previous.schedule().getId().equals(schedule.getId())) {
                    ActiveSchedule newActive = new ActiveSchedule(schedule, now);
                    currentActive.set(newActive);
                    notifyListeners(new ScheduleActivatedEvent(newActive));
                }
            } else if (previous != null) {
                currentActive.set(null);
                notifyListeners(new ScheduleDeactivatedEvent(previous));
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    private void persist() {
        try {
            Files.createDirectories(persistPath.getParent());
            try (ObjectOutputStream oos = new ObjectOutputStream(
                    Files.newOutputStream(persistPath))) {
                oos.writeObject(new ArrayList<>(schedules.values()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private void load() {
        if (!Files.exists(persistPath)) {
            return;
        }
        try (ObjectInputStream ois = new ObjectInputStream(
                Files.newInputStream(persistPath))) {
            List<BandwidthSchedule> loaded = (List<BandwidthSchedule>) ois.readObject();
            loaded.forEach(s -> schedules.put(s.getId(), s));
        } catch (IOException | ClassNotFoundException e) {
            // Ignore
        }
    }

    @Override
    public void close() {
        stop();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public sealed interface ScheduleEvent permits ScheduleAddedEvent, ScheduleRemovedEvent,
            ScheduleUpdatedEvent, ScheduleActivatedEvent, ScheduleDeactivatedEvent {
    }

    public record ActiveSchedule(BandwidthSchedule schedule, LocalDateTime activatedAt) {
    }

    public record ScheduleAddedEvent(BandwidthSchedule schedule) implements ScheduleEvent {
    }

    public record ScheduleRemovedEvent(BandwidthSchedule schedule) implements ScheduleEvent {
    }

    public record ScheduleUpdatedEvent(BandwidthSchedule schedule) implements ScheduleEvent {
    }

    public record ScheduleActivatedEvent(ActiveSchedule active) implements ScheduleEvent {
    }

    public record ScheduleDeactivatedEvent(ActiveSchedule previous) implements ScheduleEvent {
    }
}
