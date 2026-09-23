package com.dev.monitor.repository.server;

import com.dev.monitor.entity.server.Server;
import com.dev.monitor.entity.server.ServerType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface ServerRepository extends JpaRepository<Server, String> {
    @Query("SELECT s FROM Server s JOIN FETCH s.system WHERE s.system.uuid = :systemUuid")
    List<Server> findBySystemUuid(@Param("systemUuid") String systemUuid);

    @Query("SELECT s FROM Server s JOIN FETCH s.system")
    List<Server> findAllWithSystem();

    @Query("SELECT s FROM Server s JOIN FETCH s.system WHERE s.name = :name")
    List<Server> findByName(@Param("name") String name);

    Optional<Server> findBySystemUuidAndName(String systemUuid, String name);
    Optional<Server> findByIp(String ip);
    List<Server> findByServerType(ServerType serverType);
    boolean existsBySystemUuidAndName(String systemUuid, String name);

    @Query("SELECT s FROM Server s JOIN FETCH s.system LEFT JOIN FETCH s.logs WHERE s.uuid = :uuid")
    Optional<Server> findByIdWithLogs(@Param("uuid") String uuid);
}
