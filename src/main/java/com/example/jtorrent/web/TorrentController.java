package com.example.jtorrent.web;

import com.example.jtorrent.core.TorrentSession;
import com.example.jtorrent.core.TorrentSessionManager;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TorrentController {

    private final TorrentSessionManager sessionManager;

    public TorrentController(TorrentSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    public String list() {
        List<Map<String, Object>> torrents = sessionManager.getAllSessions().stream()
                .map(this::toJson)
                .collect(Collectors.toList());
        return JsonResponse.success(torrents);
    }

    public String add(String body) {
        try {
            Map<String, Object> request = JsonResponse.parse(body);
            String input = (String) request.get("input");
            String outputDir = (String) request.getOrDefault("outputDir", "./downloads");

            if (input == null || input.isEmpty()) {
                return JsonResponse.error("Missing input parameter");
            }

            String hash = sessionManager.addTorrent(input, outputDir);
            return JsonResponse.success(Map.of("hash", hash, "status", "added"));
        } catch (Exception e) {
            return JsonResponse.error(e.getMessage());
        }
    }

    public String remove(String hash) {
        try {
            sessionManager.removeTorrent(hash);
            return JsonResponse.success(Map.of("hash", hash, "status", "removed"));
        } catch (Exception e) {
            return JsonResponse.error(e.getMessage());
        }
    }

    public String start(String hash) {
        try {
            sessionManager.startTorrent(hash);
            return JsonResponse.success(Map.of("hash", hash, "status", "started"));
        } catch (Exception e) {
            return JsonResponse.error(e.getMessage());
        }
    }

    public String stop(String hash) {
        try {
            sessionManager.stopTorrent(hash);
            return JsonResponse.success(Map.of("hash", hash, "status", "stopped"));
        } catch (Exception e) {
            return JsonResponse.error(e.getMessage());
        }
    }

    private Map<String, Object> toJson(TorrentSession session) {
        var downloadState = session.getDownloadState();
        var torrentFile = session.getTorrentFile();
        long totalSize = torrentFile.totalLength();
        long downloaded = downloadState.getDownloadedBytes();
        double progress = totalSize > 0 ? (double) downloaded / totalSize : 0.0;
        String state = session.isComplete() ? "COMPLETED" : (session.isRunning() ? "DOWNLOADING" : "STOPPED");

        return Map.of(
                "hash", torrentFile.infoHashHex(),
                "name", session.getName(),
                "size", totalSize,
                "downloaded", downloaded,
                "uploaded", downloadState.getUploadedBytes(),
                "downloadRate", 0L, // Rate tracking not available from DownloadState
                "uploadRate", 0L, // Rate tracking not available from DownloadState
                "progress", progress,
                "state", state,
                "peers", session.getConnectedPeerCount());
    }
}
