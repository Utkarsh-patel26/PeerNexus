package com.example.jtorrent.core;

import com.example.jtorrent.logging.Logger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class PeerScorer {

    private static final Logger logger = Logger.getLogger(PeerScorer.class);

    private final Map<String, PeerScore> scores = new ConcurrentHashMap<>();
    private volatile ScoreWeights weights = new ScoreWeights();

    public PeerScore getOrCreate(String peerId) {
        return scores.computeIfAbsent(peerId, PeerScore::new);
    }

    public PeerScore get(String peerId) {
        return scores.get(peerId);
    }

    public void remove(String peerId) {
        PeerScore score = scores.remove(peerId);
        if (score != null) {
            score.recordDisconnect();
        }
    }

    public List<String> getTopPeers(int count) {
        return scores.entrySet().stream()
                .sorted(Comparator.comparingDouble(e -> -e.getValue().calculateScore()))
                .limit(count)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    public List<String> getSnubbedPeers() {
        return scores.entrySet().stream()
                .filter(e -> e.getValue().isSnubbed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    public List<String> getPeersToUnchoke(int slots) {
        List<String> result = new ArrayList<>();

        List<Map.Entry<String, PeerScore>> sorted = scores.entrySet().stream()
                .filter(e -> !e.getValue().isSnubbed())
                .sorted(Comparator.comparingDouble(e -> -e.getValue().calculateScore()))
                .collect(Collectors.toList());

        for (int i = 0; i < Math.min(slots, sorted.size()); i++) {
            result.add(sorted.get(i).getKey());
        }

        return result;
    }

    public String selectOptimisticUnchoke(List<String> excludePeers) {
        return scores.entrySet().stream()
                .filter(e -> !excludePeers.contains(e.getKey()))
                .filter(e -> !e.getValue().isSnubbed())
                .min(Comparator.comparingLong(e -> e.getValue().getConnectionDuration()))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    public void updateWeights(ScoreWeights newWeights) {
        this.weights = newWeights;
    }

    public Map<String, Double> getAllScores() {
        return scores.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().calculateScore()));
    }

    public void updateAllSnubStatus() {
        scores.values().forEach(PeerScore::checkSnubbed);
    }

    public int getPeerCount() {
        return scores.size();
    }

    public int getSnubbedCount() {
        return (int) scores.values().stream().filter(PeerScore::isSnubbed).count();
    }

    public double getAverageScore() {
        if (scores.isEmpty())
            return 0.0;
        return scores.values().stream()
                .mapToDouble(PeerScore::calculateScore)
                .average()
                .orElse(0.0);
    }

    public PeerStats getAggregateStats() {
        long totalDownload = 0;
        long totalUpload = 0;
        long totalDownloadRate = 0;
        long totalUploadRate = 0;
        long avgLatency = 0;
        int count = 0;

        for (PeerScore score : scores.values()) {
            totalDownload += score.getDownloadedBytes();
            totalUpload += score.getUploadedBytes();
            totalDownloadRate += score.getDownloadRate();
            totalUploadRate += score.getUploadRate();
            avgLatency += score.getLatencyMs();
            count++;
        }

        if (count > 0) {
            avgLatency /= count;
        }

        return new PeerStats(totalDownload, totalUpload, totalDownloadRate, totalUploadRate, avgLatency, count);
    }

    public record PeerStats(
            long totalDownloaded,
            long totalUploaded,
            long downloadRate,
            long uploadRate,
            long avgLatency,
            int peerCount) {
    }

    public static class ScoreWeights {
        public double downloadRate = 0.35;
        public double uploadRate = 0.20;
        public double latency = 0.15;
        public double reliability = 0.15;
        public double stability = 0.15;
    }
}
