package com.dev.monitor.entity.server;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "server_resources",
    indexes = {
        @Index(name = "idx_resources_server_timestamp", columnList = "server_id, record_timestamp"),
        @Index(name = "idx_resources_channel_timestamp", columnList = "channel, record_timestamp"),
        @Index(name = "idx_resources_timestamp", columnList = "record_timestamp")
    }
)
public class ServerResource {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "uuid", length = 36, nullable = false, updatable = false)
    private String uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "server_id", nullable = false)
    private Server server;

    @Column(name = "channel", nullable = false, length = 100)
    private String channel;

    @Column(name = "record_timestamp", nullable = false)
    private LocalDateTime recordTimestamp;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "json", nullable = false)
    private String payload;

    public ServerResource() {
    }

    public ServerResource(Server server, String channel, LocalDateTime recordTimestamp, String payload) {
        this.server = server;
        this.channel = channel;
        this.recordTimestamp = recordTimestamp;
        this.payload = payload;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public Server getServer() {
        return server;
    }

    public void setServer(Server server) {
        this.server = server;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public LocalDateTime getRecordTimestamp() {
        return recordTimestamp;
    }

    public void setRecordTimestamp(LocalDateTime recordTimestamp) {
        this.recordTimestamp = recordTimestamp;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }
}
