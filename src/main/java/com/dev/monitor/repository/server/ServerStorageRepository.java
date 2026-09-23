package com.dev.monitor.repository.server;

import com.dev.monitor.entity.server.ServerStorage;
import com.dev.monitor.entity.server.ServerStorageId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Read-side access to the server_storage hypertable. See
 * {@link ServerResourceRepository} for why the write path bypasses this.
 */
@Repository
public interface ServerStorageRepository extends JpaRepository<ServerStorage, ServerStorageId> {

    List<ServerStorage> findByServerIdOrderByRecordTimestampDesc(String serverId);

    Page<ServerStorage> findByServerIdOrderByRecordTimestampDesc(String serverId, Pageable pageable);

    List<ServerStorage> findByServerIdAndRecordTimestampBetween(
            String serverId, Instant start, Instant end);

    /** Per-mount history is the natural read pattern for a filesystem series. */
    List<ServerStorage> findByServerIdAndMountPointOrderByRecordTimestampDesc(
            String serverId, String mountPoint);

    /**
     * Every mount from this server's most recent scrape.
     *
     * Pinned to a single record_timestamp rather than DISTINCT ON
     * (mount_point): the result must be one coherent snapshot. Taking the
     * newest row per mount independently would silently combine filesystems
     * observed at different times, and would resurrect a mount that has
     * since disappeared from the agent's output.
     */
    @Query(value = """
            SELECT * FROM server_storage
            WHERE server_id = :serverId
              AND record_timestamp = (
                  SELECT max(record_timestamp) FROM server_storage
                  WHERE server_id = :serverId)
            ORDER BY mount_point
            """, nativeQuery = true)
    List<ServerStorage> findLatestMounts(@Param("serverId") String serverId);
}
