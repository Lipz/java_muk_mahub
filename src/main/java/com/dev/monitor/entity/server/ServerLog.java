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
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "server_logs",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_server_logs_channel", columnNames = {"server_id", "channel"})
    },
    indexes = {
        @Index(name = "idx_server_logs_server_id", columnList = "server_id"),
        @Index(name = "idx_server_logs_channel", columnList = "channel")
    }
)
public class ServerLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "uuid", length = 36, nullable = false, updatable = false)
    private String uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "server_id", nullable = false)
    private Server server;

    @Column(name = "channel", nullable = false, length = 100)
    private String channel;

    @Column(name = "pub_path", nullable = false, length = 500)
    private String pubPath;

    @Column(name = "save_path", nullable = false, length = 500)
    private String savePath;

    public ServerLog() {
    }

    public ServerLog(Server server, String channel, String pubPath, String savePath) {
        this.server = server;
        this.channel = channel;
        this.pubPath = pubPath;
        this.savePath = savePath;
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

    public String getPubPath() {
        return pubPath;
    }

    public void setPubPath(String pubPath) {
        this.pubPath = pubPath;
    }

    public String getSavePath() {
        return savePath;
    }

    public void setSavePath(String savePath) {
        this.savePath = savePath;
    }
}
