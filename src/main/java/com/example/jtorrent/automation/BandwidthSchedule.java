package com.example.jtorrent.automation;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public final class BandwidthSchedule {
    private final String id;
    private final String name;
    private final Set<DayOfWeek> days;
    private final LocalTime startTime;
    private final LocalTime endTime;
    private final long downloadLimitBps;
    private final long uploadLimitBps;
    private final boolean enabled;
    private final int priority;

    private BandwidthSchedule(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.days = EnumSet.copyOf(builder.days);
        this.startTime = builder.startTime;
        this.endTime = builder.endTime;
        this.downloadLimitBps = builder.downloadLimitBps;
        this.uploadLimitBps = builder.uploadLimitBps;
        this.enabled = builder.enabled;
        this.priority = builder.priority;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Set<DayOfWeek> getDays() {
        return EnumSet.copyOf(days);
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public long getDownloadLimitBps() {
        return downloadLimitBps;
    }

    public long getUploadLimitBps() {
        return uploadLimitBps;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isActiveAt(DayOfWeek day, LocalTime time) {
        if (!enabled || !days.contains(day)) {
            return false;
        }
        if (startTime.isBefore(endTime)) {
            return !time.isBefore(startTime) && time.isBefore(endTime);
        }
        return !time.isBefore(startTime) || time.isBefore(endTime);
    }

    public Builder toBuilder() {
        return new Builder()
                .id(id)
                .name(name)
                .days(days)
                .startTime(startTime)
                .endTime(endTime)
                .downloadLimitBps(downloadLimitBps)
                .uploadLimitBps(uploadLimitBps)
                .enabled(enabled)
                .priority(priority);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof BandwidthSchedule that))
            return false;
        return Objects.equals(id, that.id);
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
        private Set<DayOfWeek> days = EnumSet.allOf(DayOfWeek.class);
        private LocalTime startTime = LocalTime.MIN;
        private LocalTime endTime = LocalTime.MAX;
        private long downloadLimitBps = -1;
        private long uploadLimitBps = -1;
        private boolean enabled = true;
        private int priority = 0;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder days(Set<DayOfWeek> days) {
            this.days = EnumSet.copyOf(days);
            return this;
        }

        public Builder startTime(LocalTime startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder endTime(LocalTime endTime) {
            this.endTime = endTime;
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

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public BandwidthSchedule build() {
            Objects.requireNonNull(id, "id required");
            Objects.requireNonNull(name, "name required");
            return new BandwidthSchedule(this);
        }
    }
}
