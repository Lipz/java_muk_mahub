package com.dev.monitor.repository.system;

import com.dev.monitor.entity.system.SystemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SystemRepository extends JpaRepository<SystemEntity, String> {
    Optional<SystemEntity> findByName(String name);
    boolean existsByName(String name);
}
