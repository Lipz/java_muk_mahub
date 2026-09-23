package com.dev.monitor.repository.server;

import com.dev.monitor.entity.server.ServerStorageSummary;
import com.dev.monitor.entity.server.ServerStorageSummaryId;
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
 * Read-side access to the server_storage_summary hypertable. See
 * {@link ServerResourceRepository} for why the write path bypasses this.
 */
@Repository
public interface ServerStorageSummaryRepository
        extends JpaRepository<ServerStorageSummary, ServerStorageSummaryId> {

    List<ServerStorageSummary> findByServerIdOrderByRecordTimestampDesc(String serverId);

    Page<ServerStorageSummary> findByServerIdOrderByRecordTimestampDesc(String serverId, Pageable pageable);

    List<ServerStorageSummary> findByServerIdAndRecordTimestampBetween(
            String serverId, Instant start, Instant end);

    /**
     * The most recent summary for each of the given servers, in one query.
     *
     * DISTINCT ON is PostgreSQL-specific and has no JPQL equivalent, hence
     * the native query. The alternative -- a lookup per server -- would turn
     * the list endpoint into N queries.
     *
     * ORDER BY must lead with server_id for DISTINCT ON to be valid; the
     * record_timestamp DESC that follows is what makes it pick the latest.
     */
    @Query(value = """
            SELECT DISTINCT ON (server_id) *
            FROM server_storage_summary
            WHERE server_id IN (:serverIds)
            ORDER BY server_id, record_timestamp DESC
            """, nativeQuery = true)
    List<ServerStorageSummary> findLatestPerServer(
            @Param("serverIds") Collection<String> serverIds);
}
