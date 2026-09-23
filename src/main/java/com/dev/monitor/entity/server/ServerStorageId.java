package com.dev.monitor.entity.server;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Composite primary key for {@link ServerStorage}:
 * (server_id, mount_point, record_timestamp).
 *
 * The grain is per-filesystem, so mount_point joins the key. See
 * {@link ServerResourceId} for why this is a plain class rather than a record.
 */
public class ServerStorageId implements Serializable {

    private String serverId;
    private String mountPoint;
    private Instant recordTimestamp;

    public ServerStorageId() {
    }

    public ServerStorageId(String serverId, String mountPoint, Instant recordTimestamp) {
        this.serverId = serverId;
        this.mountPoint = mountPoint;
        this.recordTimestamp = recordTimestamp;
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

    public String getMountPoint() {
        return mountPoint;
    }

    public void setMountPoint(String mountPoint) {
        this.mountPoint = mountPoint;
    }

    public Instant getRecordTimestamp() {
        return recordTimestamp;
    }

    public void setRecordTimestamp(Instant recordTimestamp) {
        this.recordTimestamp = recordTimestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ServerStorageId other)) {
            return false;
        }
        return Objects.equals(serverId, other.serverId)
                && Objects.equals(mountPoint, other.mountPoint)
                && Objects.equals(recordTimestamp, other.recordTimestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serverId, mountPoint, recordTimestamp);
    }
}
