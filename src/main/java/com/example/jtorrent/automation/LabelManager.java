package com.example.jtorrent.automation;

import com.example.jtorrent.events.EventBus;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

public final class LabelManager {
    private final Map<String, Label> labels = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> torrentLabels = new ConcurrentHashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Path persistPath;
    @SuppressWarnings("unused") // Reserved for future event publishing
    private final EventBus eventBus;

    public LabelManager(Path persistPath, EventBus eventBus) {
        this.persistPath = persistPath;
        this.eventBus = eventBus;
        load();
    }

    public void addLabel(Label label) {
        lock.writeLock().lock();
        try {
            labels.put(label.getId(), label);
            persist();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void removeLabel(String labelId) {
        lock.writeLock().lock();
        try {
            Label removed = labels.remove(labelId);
            if (removed != null) {
                torrentLabels.values().forEach(set -> set.remove(labelId));
                persist();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void updateLabel(Label label) {
        lock.writeLock().lock();
        try {
            if (labels.containsKey(label.getId())) {
                labels.put(label.getId(), label);
                persist();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Optional<Label> getLabel(String labelId) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(labels.get(labelId));
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<Label> getAllLabels() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(labels.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    public void assignLabel(String infoHash, String labelId) {
        lock.writeLock().lock();
        try {
            if (!labels.containsKey(labelId)) {
                return;
            }
            torrentLabels.computeIfAbsent(infoHash, k -> ConcurrentHashMap.newKeySet())
                    .add(labelId);
            persist();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void unassignLabel(String infoHash, String labelId) {
        lock.writeLock().lock();
        try {
            Set<String> labelIds = torrentLabels.get(infoHash);
            if (labelIds != null && labelIds.remove(labelId)) {
                persist();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Set<Label> getLabelsForTorrent(String infoHash) {
        lock.readLock().lock();
        try {
            Set<String> labelIds = torrentLabels.getOrDefault(infoHash, Set.of());
            return labelIds.stream()
                    .map(labels::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        } finally {
            lock.readLock().unlock();
        }
    }

    public Set<String> getTorrentsWithLabel(String labelId) {
        lock.readLock().lock();
        try {
            return torrentLabels.entrySet().stream()
                    .filter(e -> e.getValue().contains(labelId))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
        } finally {
            lock.readLock().unlock();
        }
    }

    public long getEffectiveDownloadLimit(String infoHash) {
        return getLabelsForTorrent(infoHash).stream()
                .mapToLong(Label::getDownloadLimitBps)
                .filter(l -> l > 0)
                .min()
                .orElse(-1);
    }

    public long getEffectiveUploadLimit(String infoHash) {
        return getLabelsForTorrent(infoHash).stream()
                .mapToLong(Label::getUploadLimitBps)
                .filter(l -> l > 0)
                .min()
                .orElse(-1);
    }

    public Optional<Path> getEffectiveDownloadDirectory(String infoHash) {
        return getLabelsForTorrent(infoHash).stream()
                .map(Label::getDownloadDirectory)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    private void persist() {
        try {
            Files.createDirectories(persistPath.getParent());
            try (ObjectOutputStream oos = new ObjectOutputStream(
                    Files.newOutputStream(persistPath))) {
                oos.writeObject(new PersistData(
                        new HashMap<>(labels),
                        new HashMap<>(torrentLabels)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void load() {
        if (!Files.exists(persistPath)) {
            return;
        }
        try (ObjectInputStream ois = new ObjectInputStream(
                Files.newInputStream(persistPath))) {
            PersistData data = (PersistData) ois.readObject();
            labels.putAll(data.labels());
            torrentLabels.putAll(data.torrentLabels());
        } catch (IOException | ClassNotFoundException e) {
            // Ignore
        }
    }

    private record PersistData(Map<String, Label> labels,
            Map<String, Set<String>> torrentLabels) implements Serializable {
    }
}
