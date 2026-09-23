package com.dev.monitor.entity.server;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Composite primary key for {@link ServerStorageSummary}:
 * (server_id, record_timestamp).
 *
 * See {@link ServerResourceId} for why this is a plain class rather than a
 * record, and why the key is natural rather than surrogate.
 */
public class ServerStorageSummaryId implements Serializable {

    private String serverId;
    private Instant recordTimestamp;

    public ServerStorageSummaryId() {
    }

    public ServerStorageSummaryId(String serverId, Instant recordTimestamp) {
        this.serverId = serverId;
        this.recordTimestamp = recordTimestamp;
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
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
        if (!(o instanceof ServerStorageSummaryId other)) {
            return false;
        }
        return Objects.equals(serverId, other.serverId)
                && Objects.equals(recordTimestamp, other.recordTimestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serverId, recordTimestamp);
    }
}
