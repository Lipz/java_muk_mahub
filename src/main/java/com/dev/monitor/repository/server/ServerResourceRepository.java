package com.dev.monitor.repository.server;

import com.dev.monitor.entity.server.ServerResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ServerResourceRepository extends JpaRepository<ServerResource, String> {
    List<ServerResource> findByServerUuid(String serverUuid);
    List<ServerResource> findByServerUuidOrderByRecordTimestampDesc(String serverUuid);
    Page<ServerResource> findByServerUuidOrderByRecordTimestampDesc(String serverUuid, Pageable pageable);
    List<ServerResource> findByChannel(String channel);
    List<ServerResource> findByServerUuidAndRecordTimestampBetween(String serverUuid, LocalDateTime start, LocalDateTime end);
    List<ServerResource> findByChannelAndRecordTimestampBetween(String channel, LocalDateTime start, LocalDateTime end);
}
