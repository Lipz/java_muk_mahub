package com.dev.monitor.entity.server;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Composite primary key for {@link ServerResource}: (server_id, record_timestamp).
 *
 * TimescaleDB requires the partition column to appear in every unique index,
 * so the surrogate uuid was dropped in favour of this natural key. It also
 * makes ingest idempotent -- a redelivered Redis message collides and can be
 * skipped with ON CONFLICT DO NOTHING.
 *
 * Deliberately a plain class, not a record: JPA requires an @IdClass to have a
 * public no-arg constructor, which records do not provide.
 */
public class ServerResourceId implements Serializable {

    private String serverId;
    private Instant recordTimestamp;

    public ServerResourceId() {
    }

    public ServerResourceId(String serverId, Instant recordTimestamp) {
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
        if (!(o instanceof ServerResourceId other)) {
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
