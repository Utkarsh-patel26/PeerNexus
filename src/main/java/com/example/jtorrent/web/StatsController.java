package com.example.jtorrent.web;

import com.example.jtorrent.core.TorrentSessionManager;
import com.example.jtorrent.core.TorrentSession;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.Map;

public class StatsController {

    private final TorrentSessionManager sessionManager;
    private final long startTime;

    public StatsController(TorrentSessionManager sessionManager) {
        this.sessionManager = sessionManager;
        this.startTime = System.currentTimeMillis();
    }

    public String getStats() {
        try {
            var sessions = sessionManager.getAllSessions();

            long totalDownloaded = 0;
            long totalUploaded = 0;
            long downloadRate = 0;
            long uploadRate = 0;
            int activeTorrents = 0;
            int seedingTorrents = 0;
            int totalPeers = 0;

            for (TorrentSession session : sessions) {
                var downloadState = session.getDownloadState();
                if (downloadState != null) {
                    totalDownloaded += downloadState.getDownloadedBytes();
                }
                totalPeers += session.getConnectedPeerCount();

                if (session.isRunning()) {
                    if (session.isComplete()) {
                        seedingTorrents++;
                    } else {
                        activeTorrents++;
                    }
                }
            }

            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            long heapUsed = memoryBean.getHeapMemoryUsage().getUsed();
            long heapMax = memoryBean.getHeapMemoryUsage().getMax();

            double ratio = totalDownloaded > 0 ? (double) totalUploaded / totalDownloaded : 0.0;

            Map<String, Object> stats = Map.ofEntries(
                    Map.entry("totalTorrents", sessions.size()),
                    Map.entry("activeTorrents", activeTorrents),
                    Map.entry("seedingTorrents", seedingTorrents),
                    Map.entry("totalDownloaded", totalDownloaded),
                    Map.entry("totalUploaded", totalUploaded),
                    Map.entry("downloadRate", downloadRate),
                    Map.entry("uploadRate", uploadRate),
                    Map.entry("totalPeers", totalPeers),
                    Map.entry("shareRatio", Math.round(ratio * 100.0) / 100.0),
                    Map.entry("uptime", System.currentTimeMillis() - startTime),
                    Map.entry("memoryUsed", heapUsed),
                    Map.entry("memoryMax", heapMax),
                    Map.entry("freeSpace", getFreeSpace()));

            return JsonResponse.success(stats);
        } catch (Exception e) {
            return JsonResponse.error(e.getMessage());
        }
    }

    private long getFreeSpace() {
        try {
            return java.nio.file.FileSystems.getDefault()
                    .getPath(sessionManager.getDownloadDirectory())
                    .toFile()
                    .getFreeSpace();
        } catch (Exception e) {
            return -1;
        }
    }
}
