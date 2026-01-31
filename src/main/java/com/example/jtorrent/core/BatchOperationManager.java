package com.example.jtorrent.core;

import com.example.jtorrent.logging.Logger;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Batch operations manager for performing operations on multiple torrents.
 *
 * <p>
 * Features:
 * <ul>
 * <li>Pause/Resume multiple torrents
 * <li>Remove multiple torrents (with optional data deletion)
 * <li>Set priority for multiple torrents
 * <li>Apply labels to multiple torrents
 * <li>Move multiple torrents to new location
 * <li>Recheck hash for multiple torrents
 * <li>Progress tracking and cancellation
 * </ul>
 */
public class BatchOperationManager {

    private static final Logger logger = Logger.getLogger(BatchOperationManager.class);

    /** Executor for batch operations. */
    private final ExecutorService executor;

    /** Active operations. */
    private final Map<String, BatchOperation> activeOperations;

    /**
     * Create batch operation manager.
     */
    public BatchOperationManager() {
        this.executor = Executors.newFixedThreadPool(4);
        this.activeOperations = new LinkedHashMap<>();
    }

    /**
     * Pause multiple torrents.
     *
     * @param infoHashes  list of torrent info hashes
     * @param pauseAction action to pause a single torrent
     * @return batch operation for tracking progress
     */
    public BatchOperation pauseTorrents(
            List<String> infoHashes,
            Consumer<String> pauseAction) {
        return createBatchOperation("Pause Torrents", infoHashes, pauseAction);
    }

    /**
     * Resume multiple torrents.
     *
     * @param infoHashes   list of torrent info hashes
     * @param resumeAction action to resume a single torrent
     * @return batch operation for tracking progress
     */
    public BatchOperation resumeTorrents(
            List<String> infoHashes,
            Consumer<String> resumeAction) {
        return createBatchOperation("Resume Torrents", infoHashes, resumeAction);
    }

    /**
     * Remove multiple torrents.
     *
     * @param infoHashes   list of torrent info hashes
     * @param deleteData   whether to delete downloaded data
     * @param removeAction action to remove a single torrent (hash, deleteData)
     * @return batch operation for tracking progress
     */
    public BatchOperation removeTorrents(
            List<String> infoHashes,
            boolean deleteData,
            java.util.function.BiConsumer<String, Boolean> removeAction) {
        String opName = deleteData ? "Remove Torrents (delete data)" : "Remove Torrents";
        BatchOperation op = new BatchOperation(opName, infoHashes.size());
        activeOperations.put(op.getId(), op);

        CompletableFuture.runAsync(() -> {
            try {
                for (int i = 0; i < infoHashes.size() && !op.isCancelled(); i++) {
                    String hash = infoHashes.get(i);
                    try {
                        removeAction.accept(hash, deleteData);
                        op.incrementCompleted();
                        logger.debug("Batch removed torrent %d/%d: %s",
                                i + 1, infoHashes.size(), hash.substring(0, 8));
                    } catch (Exception e) {
                        op.addError(hash, e.getMessage());
                        logger.warn("Batch remove failed for %s: %s", hash.substring(0, 8), e.getMessage());
                    }
                }
                op.markComplete();
                logger.info("Batch remove completed: %d/%d successful",
                        op.getCompletedCount(), op.getTotalCount());
            } catch (Exception e) {
                op.markFailed(e.getMessage());
                logger.error("Batch remove failed: %s", e.getMessage());
            }
        }, executor);

        return op;
    }

    /**
     * Set priority for multiple torrents.
     *
     * @param infoHashes     list of torrent info hashes
     * @param priority       priority level (1=low, 2=normal, 3=high)
     * @param priorityAction action to set priority for a single torrent
     * @return batch operation for tracking progress
     */
    public BatchOperation setPriority(
            List<String> infoHashes,
            int priority,
            java.util.function.BiConsumer<String, Integer> priorityAction) {
        String opName = "Set Priority (" + (priority == 1 ? "Low" : priority == 2 ? "Normal" : "High") + ")";
        BatchOperation op = new BatchOperation(opName, infoHashes.size());
        activeOperations.put(op.getId(), op);

        CompletableFuture.runAsync(() -> {
            try {
                for (int i = 0; i < infoHashes.size() && !op.isCancelled(); i++) {
                    String hash = infoHashes.get(i);
                    try {
                        priorityAction.accept(hash, priority);
                        op.incrementCompleted();
                    } catch (Exception e) {
                        op.addError(hash, e.getMessage());
                    }
                }
                op.markComplete();
            } catch (Exception e) {
                op.markFailed(e.getMessage());
            }
        }, executor);

        return op;
    }

    /**
     * Apply labels to multiple torrents.
     *
     * @param infoHashes list of torrent info hashes
     * @param labels     labels to apply
     * @return batch operation for tracking progress
     */
    public BatchOperation applyLabels(List<String> infoHashes, List<String> labels) {
        LabelManager labelManager = LabelManager.getInstance();
        BatchOperation op = new BatchOperation("Apply Labels", infoHashes.size());
        activeOperations.put(op.getId(), op);

        CompletableFuture.runAsync(() -> {
            try {
                for (int i = 0; i < infoHashes.size() && !op.isCancelled(); i++) {
                    String hash = infoHashes.get(i);
                    try {
                        for (String label : labels) {
                            labelManager.addLabelToTorrent(hash, label);
                        }
                        op.incrementCompleted();
                    } catch (Exception e) {
                        op.addError(hash, e.getMessage());
                    }
                }
                op.markComplete();
            } catch (Exception e) {
                op.markFailed(e.getMessage());
            }
        }, executor);

        return op;
    }

    /**
     * Remove labels from multiple torrents.
     *
     * @param infoHashes list of torrent info hashes
     * @param labels     labels to remove
     * @return batch operation for tracking progress
     */
    public BatchOperation removeLabels(List<String> infoHashes, List<String> labels) {
        LabelManager labelManager = LabelManager.getInstance();
        BatchOperation op = new BatchOperation("Remove Labels", infoHashes.size());
        activeOperations.put(op.getId(), op);

        CompletableFuture.runAsync(() -> {
            try {
                for (int i = 0; i < infoHashes.size() && !op.isCancelled(); i++) {
                    String hash = infoHashes.get(i);
                    try {
                        for (String label : labels) {
                            labelManager.removeLabelFromTorrent(hash, label);
                        }
                        op.incrementCompleted();
                    } catch (Exception e) {
                        op.addError(hash, e.getMessage());
                    }
                }
                op.markComplete();
            } catch (Exception e) {
                op.markFailed(e.getMessage());
            }
        }, executor);

        return op;
    }

    /**
     * Force recheck hash for multiple torrents.
     *
     * @param infoHashes    list of torrent info hashes
     * @param recheckAction action to trigger recheck for a single torrent
     * @return batch operation for tracking progress
     */
    public BatchOperation recheckTorrents(
            List<String> infoHashes,
            Consumer<String> recheckAction) {
        return createBatchOperation("Force Recheck", infoHashes, recheckAction);
    }

    /**
     * Move multiple torrents to new location.
     *
     * @param infoHashes list of torrent info hashes
     * @param newPath    new save location
     * @param moveAction action to move a single torrent
     * @return batch operation for tracking progress
     */
    public BatchOperation moveTorrents(
            List<String> infoHashes,
            String newPath,
            java.util.function.BiConsumer<String, String> moveAction) {
        BatchOperation op = new BatchOperation("Move Torrents", infoHashes.size());
        activeOperations.put(op.getId(), op);

        CompletableFuture.runAsync(() -> {
            try {
                for (int i = 0; i < infoHashes.size() && !op.isCancelled(); i++) {
                    String hash = infoHashes.get(i);
                    try {
                        moveAction.accept(hash, newPath);
                        op.incrementCompleted();
                    } catch (Exception e) {
                        op.addError(hash, e.getMessage());
                    }
                }
                op.markComplete();
            } catch (Exception e) {
                op.markFailed(e.getMessage());
            }
        }, executor);

        return op;
    }

    /**
     * Create a generic batch operation.
     */
    private BatchOperation createBatchOperation(
            String name,
            List<String> infoHashes,
            Consumer<String> action) {
        BatchOperation op = new BatchOperation(name, infoHashes.size());
        activeOperations.put(op.getId(), op);

        CompletableFuture.runAsync(() -> {
            try {
                for (int i = 0; i < infoHashes.size() && !op.isCancelled(); i++) {
                    String hash = infoHashes.get(i);
                    try {
                        action.accept(hash);
                        op.incrementCompleted();
                    } catch (Exception e) {
                        op.addError(hash, e.getMessage());
                    }
                }
                op.markComplete();
            } catch (Exception e) {
                op.markFailed(e.getMessage());
            }
        }, executor);

        return op;
    }

    /**
     * Get active operations.
     *
     * @return list of active operations
     */
    public List<BatchOperation> getActiveOperations() {
        return new ArrayList<>(activeOperations.values());
    }

    /**
     * Cancel an operation.
     *
     * @param operationId operation ID
     */
    public void cancelOperation(String operationId) {
        BatchOperation op = activeOperations.get(operationId);
        if (op != null) {
            op.cancel();
        }
    }

    /**
     * Shutdown the manager.
     */
    public void shutdown() {
        executor.shutdownNow();
    }

    /**
     * Represents a batch operation with progress tracking.
     */
    public static class BatchOperation {
        private final String id;
        private final String name;
        private final int totalCount;
        private final long startTime;
        private final Map<String, String> errors;

        private volatile int completedCount = 0;
        private volatile boolean cancelled = false;
        private volatile boolean complete = false;
        private volatile String failureReason = null;
        private volatile long endTime = 0;

        public BatchOperation(String name, int totalCount) {
            this.id = UUID.randomUUID().toString();
            this.name = name;
            this.totalCount = totalCount;
            this.startTime = System.currentTimeMillis();
            this.errors = new LinkedHashMap<>();
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public int getTotalCount() {
            return totalCount;
        }

        public int getCompletedCount() {
            return completedCount;
        }

        public synchronized void incrementCompleted() {
            completedCount++;
        }

        public double getProgress() {
            return totalCount > 0 ? (double) completedCount / totalCount : 0;
        }

        public int getProgressPercent() {
            return (int) (getProgress() * 100);
        }

        public boolean isCancelled() {
            return cancelled;
        }

        public void cancel() {
            cancelled = true;
        }

        public boolean isComplete() {
            return complete;
        }

        void markComplete() {
            complete = true;
            endTime = System.currentTimeMillis();
        }

        void markFailed(String reason) {
            complete = true;
            failureReason = reason;
            endTime = System.currentTimeMillis();
        }

        public boolean isFailed() {
            return failureReason != null;
        }

        public String getFailureReason() {
            return failureReason;
        }

        void addError(String hash, String error) {
            errors.put(hash, error);
        }

        public Map<String, String> getErrors() {
            return Collections.unmodifiableMap(errors);
        }

        public int getErrorCount() {
            return errors.size();
        }

        public long getElapsedTimeMs() {
            if (complete) {
                return endTime - startTime;
            }
            return System.currentTimeMillis() - startTime;
        }

        public String getStatus() {
            if (cancelled) {
                return "Cancelled";
            }
            if (failureReason != null) {
                return "Failed: " + failureReason;
            }
            if (complete) {
                return errors.isEmpty() ? "Completed" : "Completed with " + errors.size() + " errors";
            }
            return String.format("Processing %d/%d (%.0f%%)",
                    completedCount, totalCount, getProgress() * 100);
        }

        @Override
        public String toString() {
            return String.format("BatchOperation{name='%s', progress=%d/%d, status='%s'}",
                    name, completedCount, totalCount, getStatus());
        }
    }
}
