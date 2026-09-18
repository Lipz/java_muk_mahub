-- ============================================================
-- Server Monitoring / Log Management Database
-- MySQL 8.x
-- ============================================================

-- Optional: create database
-- CREATE DATABASE IF NOT EXISTS server_monitoring
--     CHARACTER SET utf8mb4
--     COLLATE utf8mb4_unicode_ci;

USE mhub_db;


-- ============================================================
-- 1. SYSTEMS
-- ============================================================

CREATE TABLE IF NOT EXISTS systems (
    uuid CHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (uuid),

    UNIQUE KEY uk_systems_name (name)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- 2. SERVERS
-- ============================================================

CREATE TABLE IF NOT EXISTS servers (
    uuid CHAR(36) NOT NULL,
    system_id CHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    ip VARCHAR(45) NOT NULL,
    server_type ENUM('DATABASE', 'APP', 'WEB') NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (uuid),

    CONSTRAINT fk_servers_system
        FOREIGN KEY (system_id)
        REFERENCES systems(uuid)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    UNIQUE KEY uk_servers_system_name (system_id, name),

    KEY idx_servers_system_id (system_id),
    KEY idx_servers_ip (ip),
    KEY idx_servers_type (server_type)

) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- 3. SERVER LOGS
-- ============================================================

CREATE TABLE IF NOT EXISTS server_logs (
    uuid CHAR(36) NOT NULL,
    server_id CHAR(36) NOT NULL,
    channel VARCHAR(100) NOT NULL,
    pub_path VARCHAR(500) NOT NULL,
    save_path VARCHAR(500) NOT NULL,

    PRIMARY KEY (uuid),

    CONSTRAINT fk_server_logs_server
        FOREIGN KEY (server_id)
        REFERENCES servers(uuid)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    UNIQUE KEY uk_server_logs_channel (
        server_id,
        channel
    ),

    KEY idx_server_logs_server_id (server_id),
    KEY idx_server_logs_channel (channel)

) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- 4. SERVER RESOURCES
-- ============================================================

CREATE TABLE IF NOT EXISTS server_resources (
    uuid CHAR(36) NOT NULL,
    server_id CHAR(36) NOT NULL,
    channel VARCHAR(100) NOT NULL,
    record_timestamp DATETIME(3) NOT NULL,
    payload JSON NOT NULL,

    PRIMARY KEY (uuid),

    CONSTRAINT fk_server_resources_server
        FOREIGN KEY (server_id)
        REFERENCES servers(uuid)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    KEY idx_resources_server_timestamp (
        server_id,
        record_timestamp
    ),

    KEY idx_resources_channel_timestamp (
        channel,
        record_timestamp
    ),

    KEY idx_resources_timestamp (
        record_timestamp
    )

) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- 5. SERVER STORAGE
-- ============================================================

CREATE TABLE IF NOT EXISTS server_storage (
    uuid CHAR(36) NOT NULL,
    server_id CHAR(36) NOT NULL,
    channel VARCHAR(100) NOT NULL,
    record_timestamp DATETIME(3) NOT NULL,
    payload JSON NOT NULL,

    PRIMARY KEY (uuid),

    CONSTRAINT fk_server_storage_server
        FOREIGN KEY (server_id)
        REFERENCES servers(uuid)
        ON UPDATE CASCADE
        ON DELETE CASCADE,

    KEY idx_storage_server_timestamp (
        server_id,
        record_timestamp
    ),

    KEY idx_storage_channel_timestamp (
        channel,
        record_timestamp
    ),

    KEY idx_storage_timestamp (
        record_timestamp
    )

) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- END
-- ============================================================