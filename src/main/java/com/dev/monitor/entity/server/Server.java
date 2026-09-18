package com.dev.monitor.entity.server;

import com.dev.monitor.entity.system.SystemEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "servers",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_servers_system_name", columnNames = {"system_id", "name"})
    },
    indexes = {
        @Index(name = "idx_servers_system_id", columnList = "system_id"),
        @Index(name = "idx_servers_ip", columnList = "ip"),
        @Index(name = "idx_servers_type", columnList = "server_type")
    }
)
public class Server {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "uuid", length = 36, nullable = false, updatable = false)
    private String uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "system_id", nullable = false)
    private SystemEntity system;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "ip", nullable = false, length = 45)
    private String ip;

    @Enumerated(EnumType.STRING)
    @Column(name = "server_type", nullable = false, length = 20)
    private ServerType serverType;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "server", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ServerLog> logs = new ArrayList<>();

    @OneToMany(mappedBy = "server", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ServerResource> resources = new ArrayList<>();

    @OneToMany(mappedBy = "server", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ServerStorage> storages = new ArrayList<>();

    public Server() {
    }

    public Server(SystemEntity system, String name, String ip, ServerType serverType) {
        this.system = system;
        this.name = name;
        this.ip = ip;
        this.serverType = serverType;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public SystemEntity getSystem() {
        return system;
    }

    public void setSystem(SystemEntity system) {
        this.system = system;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public ServerType getServerType() {
        return serverType;
    }

    public void setServerType(ServerType serverType) {
        this.serverType = serverType;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<ServerLog> getLogs() {
        return logs;
    }

    public void setLogs(List<ServerLog> logs) {
        this.logs = logs;
    }

    public List<ServerResource> getResources() {
        return resources;
    }

    public void setResources(List<ServerResource> resources) {
        this.resources = resources;
    }

    public List<ServerStorage> getStorages() {
        return storages;
    }

    public void setStorages(List<ServerStorage> storages) {
        this.storages = storages;
    }
}
