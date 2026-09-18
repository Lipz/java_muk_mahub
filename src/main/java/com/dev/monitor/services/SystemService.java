package com.dev.monitor.services;

import com.dev.monitor.dto.system.SystemCreateRequest;
import com.dev.monitor.dto.system.SystemResponse;
import com.dev.monitor.entity.system.SystemEntity;
import com.dev.monitor.exception.system.SystemAlreadyExistsException;
import com.dev.monitor.exception.system.SystemNotFoundException;
import com.dev.monitor.repository.system.SystemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SystemService {

    private final SystemRepository systemRepository;

    public SystemService(SystemRepository systemRepository) {
        this.systemRepository = systemRepository;
    }

    @Transactional
    public SystemResponse createSystem(SystemCreateRequest request) {
        if (systemRepository.existsByName(request.name())) {
            throw new SystemAlreadyExistsException(request.name());
        }
        SystemEntity system = new SystemEntity(request.name());
        SystemEntity saved = systemRepository.save(system);
        return SystemResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<SystemResponse> getAllSystems() {
        return systemRepository.findAll()
                .stream()
                .map(SystemResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public SystemResponse getSystemById(String uuid) {
        SystemEntity system = systemRepository.findById(uuid)
                .orElseThrow(() -> new SystemNotFoundException(uuid));
        return SystemResponse.fromEntity(system);
    }
}
