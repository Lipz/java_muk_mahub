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
 * One row per filesystem per scrape, from the hourly storage- channel.
 *
 * A single message carries a host rollup plus an array of mounts, so it fans
 * out to N rows here plus one {@link ServerStorageSummary}.
 *
 * Notes that matter when changing this class:
 *
 *  - free_bytes, not available_bytes: the agent reports freeBytes and
 *    reservedBytes separately, and on Unix "free" and "available" differ by
 *    exactly those reserved blocks.
 *  - usedPct is stored, not derived. The agent computes used/(used+free),
 *    excluding reserved blocks from the denominator -- not the obvious
 *    used/total. For a real sample mount those give 25.70% and 24.41%.
 *  - A mount that could not be measured arrives with an error and no
 *    metrics, sometimes without device or fsType. Such rows are stored
 *    deliberately: an unreadable mount is a monitoring signal, not missing
 *    data. Hence every metric field is nullable.
 *  - See {@link ServerResource} for the flat-natural-key and wrapper-type
 *    reasoning, which applies here identically.
 */
@Entity
@Table(name = "server_storage")
@IdClass(ServerStorageId.class)
public class ServerStorage {

    @Id
    @Column(name = "server_id", length = 36, nullable = false)
    private String serverId;

    @Id
    @Column(name = "mount_point", length = 255, nullable = false)
    private String mountPoint;

    @Id
    @Column(name = "record_timestamp", nullable = false)
    private Instant recordTimestamp;

    @Column(name = "device", length = 255)
    private String device;

    @Column(name = "fstype", length = 50)
    private String fstype;

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

    /** Null means the mount was measured successfully. */
    @Column(name = "error", length = 500)
    private String error;

    /** Raw mount object, so no unmapped agent field is silently lost. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extra")
    private String extra;

    public ServerStorage() {
    }

    public ServerStorage(String serverId, String mountPoint, Instant recordTimestamp) {
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

    public String getDevice() {
        return device;
    }

    public void setDevice(String device) {
        this.device = device;
    }

    public String getFstype() {
        return fstype;
    }

    public void setFstype(String fstype) {
        this.fstype = fstype;
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

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getExtra() {
        return extra;
    }

    public void setExtra(String extra) {
        this.extra = extra;
    }
}
