package com.dev.monitor.entity.server;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Host-level storage rollup: the payload's "server" object, one row per
 * server per scrape.
 *
 * A separate entity from {@link ServerStorage} because the grain differs.
 * The byte columns are verifiably the sum of the non-error mounts and could
 * be derived with SUM(); {@link #partial} could not. It is a per-scrape fact
 * about whether the rollup covered everything, and no aggregate over the
 * mount rows can reconstruct it -- which is what makes this a table rather
 * than a view.
 */
@Entity
@Table(name = "server_storage_summary")
@IdClass(ServerStorageSummaryId.class)
public class ServerStorageSummary {

    @Id
    @Column(name = "server_id", length = 36, nullable = false)
    private String serverId;

    @Id
    @Column(name = "record_timestamp", nullable = false)
    private Instant recordTimestamp;

    @Column(name = "total_bytes")
    private Long totalBytes;

    @Column(name = "used_bytes")
    private Long usedBytes;

    @Column(name = "free_bytes")
    private Long freeBytes;

    @Column(name = "reserved_bytes")
    private Long reservedBytes;

    @Column(name = "used_pct")
    private Float usedPct;

    /** Whether the rollup covered every mount the agent attempted. */
    @Column(name = "partial")
    private Boolean partial;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extra")
    private String extra;

    public ServerStorageSummary() {
    }

    public ServerStorageSummary(String serverId, Instant recordTimestamp) {
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

    public Long getTotalBytes() {
        return totalBytes;
    }

    public void setTotalBytes(Long totalBytes) {
        this.totalBytes = totalBytes;
    }

    public Long getUsedBytes() {
        return usedBytes;
    }

    public void setUsedBytes(Long usedBytes) {
        this.usedBytes = usedBytes;
    }

    public Long getFreeBytes() {
        return freeBytes;
    }

    public void setFreeBytes(Long freeBytes) {
        this.freeBytes = freeBytes;
    }

    public Long getReservedBytes() {
        return reservedBytes;
    }

    public void setReservedBytes(Long reservedBytes) {
        this.reservedBytes = reservedBytes;
    }

    public Float getUsedPct() {
        return usedPct;
    }

    public void setUsedPct(Float usedPct) {
        this.usedPct = usedPct;
    }

    public Boolean getPartial() {
        return partial;
    }

    public void setPartial(Boolean partial) {
        this.partial = partial;
    }

    public String getExtra() {
        return extra;
    }

    public void setExtra(String extra) {
        this.extra = extra;
    }
}
