package com.dev.monitor.repository.server;

import com.dev.monitor.entity.server.ServerStorage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ServerStorageRepository extends JpaRepository<ServerStorage, String> {
    List<ServerStorage> findByServerUuid(String serverUuid);
    List<ServerStorage> findByServerUuidOrderByRecordTimestampDesc(String serverUuid);
    Page<ServerStorage> findByServerUuidOrderByRecordTimestampDesc(String serverUuid, Pageable pageable);
    List<ServerStorage> findByChannel(String channel);
    List<ServerStorage> findByServerUuidAndRecordTimestampBetween(String serverUuid, LocalDateTime start, LocalDateTime end);
    List<ServerStorage> findByChannelAndRecordTimestampBetween(String channel, LocalDateTime start, LocalDateTime end);
}
