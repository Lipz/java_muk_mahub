package com.dev.monitor.redis;

import com.dev.monitor.services.LogFileWriterService;
import com.dev.monitor.services.ServerResourceWriterService;
import com.dev.monitor.services.ServerStorageWriterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisMessageSubscriberTest {

    @Mock
    private LogFileWriterService logFileWriterService;

    @Mock
    private ServerResourceWriterService serverResourceWriterService;

    @Mock
    private ServerStorageWriterService serverStorageWriterService;

    private RedisMessageSubscriber subscriber;

    @BeforeEach
    void setUp() {
        subscriber = new RedisMessageSubscriber(
                logFileWriterService, serverResourceWriterService, serverStorageWriterService);
    }

    @Test
    void shouldRouteLogChannelToLogFileWriterService() {
        String channel = "log-app-server";
        String body = "{\"serverName\":\"app-01\",\"message\":\"test log\"}";
        Message message = new DefaultMessage(
                channel.getBytes(StandardCharsets.UTF_8),
                body.getBytes(StandardCharsets.UTF_8)
        );

        when(logFileWriterService.writeLogAsync(channel, body))
                .thenReturn(CompletableFuture.completedFuture(null));

        subscriber.onMessage(message, null);

        verify(logFileWriterService).writeLogAsync(channel, body);
        verify(serverResourceWriterService, never()).writeResourceAsync(anyString(), anyString());
        verify(serverStorageWriterService, never()).writeStorageAsync(anyString(), anyString());
    }

    @Test
    void shouldRouteResourceChannelToResourceWriterService() {
        String channel = "resource-info";
        String body = "{\"serverId\":\"srv-01\",\"timestamp\":\"2026-09-22T04:06:23Z\"}";
        Message message = new DefaultMessage(
                channel.getBytes(StandardCharsets.UTF_8),
                body.getBytes(StandardCharsets.UTF_8)
        );

        when(serverResourceWriterService.writeResourceAsync(channel, body))
                .thenReturn(CompletableFuture.completedFuture(null));

        subscriber.onMessage(message, null);

        verify(serverResourceWriterService).writeResourceAsync(channel, body);
        verify(logFileWriterService, never()).writeLogAsync(anyString(), anyString());
        verify(serverStorageWriterService, never()).writeStorageAsync(anyString(), anyString());
    }

    @Test
    void shouldRouteStorageChannelToStorageWriterService() {
        String channel = "storage-info";
        String body = "{\"serverId\":\"srv-01\",\"mounts\":[{\"path\":\"/\"}]}";
        Message message = new DefaultMessage(
                channel.getBytes(StandardCharsets.UTF_8),
                body.getBytes(StandardCharsets.UTF_8)
        );

        when(serverStorageWriterService.writeStorageAsync(channel, body))
                .thenReturn(CompletableFuture.completedFuture(null));

        subscriber.onMessage(message, null);

        verify(serverStorageWriterService).writeStorageAsync(channel, body);
        verify(logFileWriterService, never()).writeLogAsync(anyString(), anyString());
        verify(serverResourceWriterService, never()).writeResourceAsync(anyString(), anyString());
    }

    @Test
    void shouldNotRouteDockerOrUnknownChannelsToAnyWriter() {
        // docker- has no handler yet; system-alert and logs:app match no
        // prefix at all.
        String[] unhandledChannels = {
                "docker-container-01",
                "system-alert",
                "logs:app"
        };

        for (String channel : unhandledChannels) {
            Message message = new DefaultMessage(
                    channel.getBytes(StandardCharsets.UTF_8),
                    "test-body".getBytes(StandardCharsets.UTF_8)
            );
            subscriber.onMessage(message, null);
        }

        verify(logFileWriterService, never()).writeLogAsync(anyString(), anyString());
        verify(serverResourceWriterService, never()).writeResourceAsync(anyString(), anyString());
        verify(serverStorageWriterService, never()).writeStorageAsync(anyString(), anyString());
    }
}
