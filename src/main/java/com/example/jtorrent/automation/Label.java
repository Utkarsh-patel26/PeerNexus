package com.example.jtorrent.automation;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public final class Label {
    private final String id;
    private final String name;
    private final String color;
    private final Path downloadDirectory;
    private final Path moveOnCompleteDirectory;
    private final long downloadLimitBps;
    private final long uploadLimitBps;
    private final int maxConnections;
    private final boolean autoStart;
    private final boolean applyToExisting;

    private Label(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.color = builder.color;
        this.downloadDirectory = builder.downloadDirectory;
        this.moveOnCompleteDirectory = builder.moveOnCompleteDirectory;
        this.downloadLimitBps = builder.downloadLimitBps;
        this.uploadLimitBps = builder.uploadLimitBps;
        this.maxConnections = builder.maxConnections;
        this.autoStart = builder.autoStart;
        this.applyToExisting = builder.applyToExisting;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getColor() {
        return color;
    }

    public Optional<Path> getDownloadDirectory() {
        return Optional.ofNullable(downloadDirectory);
    }

    public Optional<Path> getMoveOnCompleteDirectory() {
        return Optional.ofNullable(moveOnCompleteDirectory);
    }

    public long getDownloadLimitBps() {
        return downloadLimitBps;
    }

    public long getUploadLimitBps() {
        return uploadLimitBps;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public boolean isAutoStart() {
        return autoStart;
    }

    public boolean isApplyToExisting() {
        return applyToExisting;
    }

    public Builder toBuilder() {
        return new Builder()
                .id(id)
                .name(name)
                .color(color)
                .downloadDirectory(downloadDirectory)
                .moveOnCompleteDirectory(moveOnCompleteDirectory)
                .downloadLimitBps(downloadLimitBps)
                .uploadLimitBps(uploadLimitBps)
                .maxConnections(maxConnections)
                .autoStart(autoStart)
                .applyToExisting(applyToExisting);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof Label label))
            return false;
        return Objects.equals(id, label.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private String name;
        private String color = "#808080";
        private Path downloadDirectory;
        private Path moveOnCompleteDirectory;
        private long downloadLimitBps = -1;
        private long uploadLimitBps = -1;
        private int maxConnections = -1;
        private boolean autoStart = true;
        private boolean applyToExisting = false;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder color(String color) {
            this.color = color;
            return this;
        }

        public Builder downloadDirectory(Path downloadDirectory) {
            this.downloadDirectory = downloadDirectory;
            return this;
        }

        public Builder moveOnCompleteDirectory(Path moveOnCompleteDirectory) {
            this.moveOnCompleteDirectory = moveOnCompleteDirectory;
            return this;
        }

        public Builder downloadLimitBps(long downloadLimitBps) {
            this.downloadLimitBps = downloadLimitBps;
            return this;
        }

        public Builder uploadLimitBps(long uploadLimitBps) {
            this.uploadLimitBps = uploadLimitBps;
            return this;
        }

        public Builder maxConnections(int maxConnections) {
            this.maxConnections = maxConnections;
            return this;
        }

        public Builder autoStart(boolean autoStart) {
            this.autoStart = autoStart;
            return this;
        }

        public Builder applyToExisting(boolean applyToExisting) {
            this.applyToExisting = applyToExisting;
            return this;
        }

        public Label build() {
            Objects.requireNonNull(id, "id required");
            Objects.requireNonNull(name, "name required");
            return new Label(this);
        }
    }
}
