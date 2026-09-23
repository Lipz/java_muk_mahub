package com.dev.monitor.repository.server;

import com.dev.monitor.dto.server.ResourcePoint;
import com.dev.monitor.entity.server.ServerResource;
import com.dev.monitor.entity.server.ServerResourceId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Read-side access to the server_resources hypertable.
 *
 * The write path does NOT go through here: at a 3-5s scrape interval the
 * ingest handler batches inserts via JdbcTemplate with ON CONFLICT DO NOTHING
 * rather than persisting one entity per message.
 *
 * Queries are keyed on serverId (a flat column now, not a server.uuid path)
 * and take Instant, matching the timestamptz column.
 */
@Repository
public interface ServerResourceRepository extends JpaRepository<ServerResource, ServerResourceId> {

    List<ServerResource> findByServerIdOrderByRecordTimestampDesc(String serverId);

    Page<ServerResource> findByServerIdOrderByRecordTimestampDesc(String serverId, Pageable pageable);

    List<ServerResource> findByServerIdAndRecordTimestampBetween(
            String serverId, Instant start, Instant end);

    /**
     * The most recent scrape for each of the given servers, in one query.
     *
     * DISTINCT ON is PostgreSQL-specific with no JPQL equivalent; the
     * alternative is a lookup per server, which would make the list endpoint
     * scale with fleet size. ORDER BY must lead with server_id for
     * DISTINCT ON to be valid, and record_timestamp DESC is what picks the
     * latest row within each server.
     */
    @Query(value = """
            SELECT DISTINCT ON (server_id) *
            FROM server_resources
            WHERE server_id IN (:serverIds)
            ORDER BY server_id, record_timestamp DESC
            """, nativeQuery = true)
    List<ServerResource> findLatestPerServer(@Param("serverIds") Collection<String> serverIds);

    /**
     * Averaged history since a point in time, one row per bucket.
     *
     * Aggregated in the database with time_bucket: at a 3-5s scrape an hour
     * is ~1000 rows, and a sparkline needs a few dozen points. The bucket is
     * returned as epoch seconds and the averages as double precision so the
     * projection needs no timestamptz or real conversion. Aliases are quoted
     * because PostgreSQL folds unquoted ones to lower case.
     */
    @Query(value = """
            SELECT extract(epoch FROM time_bucket(:bucketSeconds * interval '1 second', record_timestamp))::bigint AS "epoch",
                   avg(cpu_usage_pct)::double precision AS "cpuPct",
                   avg(mem_used_pct)::double precision  AS "memPct",
                   avg(net_rx_bps)::bigint              AS "netRxBps",
                   avg(net_tx_bps)::bigint              AS "netTxBps"
            FROM server_resources
            WHERE server_id = :serverId AND record_timestamp >= :since
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<ResourcePoint.ResourceBucket> findHistory(@Param("serverId") String serverId,
                                                   @Param("since") Instant since,
                                                   @Param("bucketSeconds") int bucketSeconds);
}
