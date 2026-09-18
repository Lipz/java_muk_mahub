package com.dev.monitor.repository.server;

import com.dev.monitor.entity.server.ServerLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServerLogRepository extends JpaRepository<ServerLog, String> {
    @Query("SELECT sl FROM ServerLog sl JOIN FETCH sl.server WHERE sl.server.uuid = :serverUuid")
    List<ServerLog> findByServerUuid(@Param("serverUuid") String serverUuid);

    @Query("SELECT sl FROM ServerLog sl JOIN FETCH sl.server")
    List<ServerLog> findAllWithServer();

    Optional<ServerLog> findByServerUuidAndChannel(String serverUuid, String channel);
    List<ServerLog> findByChannel(String channel);
    boolean existsByServerUuidAndChannel(String serverUuid, String channel);
}
