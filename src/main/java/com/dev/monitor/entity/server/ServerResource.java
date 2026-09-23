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
 * One row per resource scrape, published on the resource-info Redis channel
 * roughly every 3-5 seconds.
 *
 * Mirrors the agent payload, which nests under cpu/memory/swap/network:
 *
 * <pre>
 * {"serverId":"be91...","timestamp":"2026-09-22T04:06:23Z","uptimeSeconds":6044623,
 *  "cpu":{"count":16,"usedPercent":1.928,"load1":1.04,"load5":0.9,"load15":0.81},
 *  "memory":{"totalBytes":33719185408,"usedBytes":11708960768,
 *            "availableBytes":22010224640,"usedPercent":34.72,"estimated":false},
 *  "swap":{"totalBytes":20971515904,"usedBytes":0,"usedPercent":0},
 *  "network":{"rxBytesPerSec":103669.046,"txBytesPerSec":239812.334}}
 * </pre>
 *
 * Backed by a TimescaleDB hypertable (see database/init.sql). Notes that matter
 * when changing this class:
 *
 *  - No surrogate uuid and no @ManyToOne Server. The key is natural and flat,
 *    which keeps the write path free of lazy proxies and cascade semantics.
 *    Server deletion is handled by ON DELETE CASCADE in the database.
 *  - Wrapper types are chosen to match the SQL types exactly, because
 *    ddl-auto=validate compares JDBC type codes: real -> Float (NOT Double),
 *    bigint -> Long, smallint -> Short, boolean -> Boolean.
 *  - The network rates arrive as fractional doubles and are rounded to
 *    bigint at ingest; sub-byte-per-second precision is meaningless.
 *  - All metric fields are nullable so a partial payload still writes.
 *  - Indexes are not declared here. init.sql owns them; under validate
 *    Hibernate neither creates nor verifies indexes.
 */
@Entity
@Table(name = "server_resources")
@IdClass(ServerResourceId.class)
public class ServerResource {

    @Id
    @Column(name = "server_id", length = 36, nullable = false)
    private String serverId;

    @Id
    @Column(name = "record_timestamp", nullable = false)
    private Instant recordTimestamp;

    // --- Host: uptimeSeconds ---

    @Column(name = "uptime_seconds")
    private Long uptimeSeconds;

    // --- CPU: cpu.* ---

    @Column(name = "cpu_count")
    private Short cpuCount;

    @Column(name = "cpu_usage_pct")
    private Float cpuUsagePct;

    @Column(name = "load_avg_1m")
    private Float loadAvg1m;

    @Column(name = "load_avg_5m")
    private Float loadAvg5m;

    @Column(name = "load_avg_15m")
    private Float loadAvg15m;

    // --- Memory: memory.* ---

    @Column(name = "mem_total_bytes")
    private Long memTotalBytes;

    @Column(name = "mem_used_bytes")
    private Long memUsedBytes;

    @Column(name = "mem_available_bytes")
    private Long memAvailableBytes;

    /**
     * Stored rather than derived from the byte counts: the agent reports
     * {@link #memEstimated}, so its percentage carries estimation semantics
     * we cannot faithfully recompute.
     */
    @Column(name = "mem_used_pct")
    private Float memUsedPct;

    @Column(name = "mem_estimated")
    private Boolean memEstimated;

    // --- Swap: swap.* ---

    @Column(name = "swap_total_bytes")
    private Long swapTotalBytes;

    @Column(name = "swap_used_bytes")
    private Long swapUsedBytes;

    @Column(name = "swap_used_pct")
    private Float swapUsedPct;

    // --- Network: network.* (rounded from fractional bytes/sec) ---

    @Column(name = "net_rx_bps")
    private Long netRxBps;

    @Column(name = "net_tx_bps")
    private Long netTxBps;

    /** Unmapped agent fields, so a new metric is not a schema migration. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extra")
    private String extra;

    public ServerResource() {
    }

    public ServerResource(String serverId, Instant recordTimestamp) {
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

    public Long getUptimeSeconds() {
        return uptimeSeconds;
    }

    public void setUptimeSeconds(Long uptimeSeconds) {
        this.uptimeSeconds = uptimeSeconds;
    }

    public Short getCpuCount() {
        return cpuCount;
    }

    public void setCpuCount(Short cpuCount) {
        this.cpuCount = cpuCount;
    }

    public Float getCpuUsagePct() {
        return cpuUsagePct;
    }

    public void setCpuUsagePct(Float cpuUsagePct) {
        this.cpuUsagePct = cpuUsagePct;
    }

    public Float getLoadAvg1m() {
        return loadAvg1m;
    }

    public void setLoadAvg1m(Float loadAvg1m) {
        this.loadAvg1m = loadAvg1m;
    }

    public Float getLoadAvg5m() {
        return loadAvg5m;
    }

    public void setLoadAvg5m(Float loadAvg5m) {
        this.loadAvg5m = loadAvg5m;
    }

    public Float getLoadAvg15m() {
        return loadAvg15m;
    }

    public void setLoadAvg15m(Float loadAvg15m) {
        this.loadAvg15m = loadAvg15m;
    }

    public Long getMemTotalBytes() {
        return memTotalBytes;
    }

    public void setMemTotalBytes(Long memTotalBytes) {
        this.memTotalBytes = memTotalBytes;
    }

    public Long getMemUsedBytes() {
        return memUsedBytes;
    }

    public void setMemUsedBytes(Long memUsedBytes) {
        this.memUsedBytes = memUsedBytes;
    }

    public Long getMemAvailableBytes() {
        return memAvailableBytes;
    }

    public void setMemAvailableBytes(Long memAvailableBytes) {
        this.memAvailableBytes = memAvailableBytes;
    }

    public Float getMemUsedPct() {
        return memUsedPct;
    }

    public void setMemUsedPct(Float memUsedPct) {
        this.memUsedPct = memUsedPct;
    }

    public Boolean getMemEstimated() {
        return memEstimated;
    }

    public void setMemEstimated(Boolean memEstimated) {
        this.memEstimated = memEstimated;
    }

    public Long getSwapTotalBytes() {
        return swapTotalBytes;
    }

    public void setSwapTotalBytes(Long swapTotalBytes) {
        this.swapTotalBytes = swapTotalBytes;
    }

    public Long getSwapUsedBytes() {
        return swapUsedBytes;
    }

    public void setSwapUsedBytes(Long swapUsedBytes) {
        this.swapUsedBytes = swapUsedBytes;
    }

    public Float getSwapUsedPct() {
        return swapUsedPct;
    }

    public void setSwapUsedPct(Float swapUsedPct) {
        this.swapUsedPct = swapUsedPct;
    }

    public Long getNetRxBps() {
        return netRxBps;
    }

    public void setNetRxBps(Long netRxBps) {
        this.netRxBps = netRxBps;
    }

    public Long getNetTxBps() {
        return netTxBps;
    }

    public void setNetTxBps(Long netTxBps) {
        this.netTxBps = netTxBps;
    }

    public String getExtra() {
        return extra;
    }

    public void setExtra(String extra) {
        this.extra = extra;
    }
}
