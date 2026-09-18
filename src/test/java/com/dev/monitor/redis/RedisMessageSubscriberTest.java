package com.dev.monitor.redis;

import com.dev.monitor.services.LogFileWriterService;
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

    private RedisMessageSubscriber subscriber;

    @BeforeEach
    void setUp() {
        subscriber = new RedisMessageSubscriber(logFileWriterService);
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
    }

    @Test
    void shouldNotRouteStorageResourceDockerOrUnknownChannelsToLogFileWriter() {
        String[] nonLogChannels = {
                "storage-disk-01",
                "resource-cpu-01",
                "docker-container-01",
                "system-alert",
                "logs:app"
        };

        for (String channel : nonLogChannels) {
            Message message = new DefaultMessage(
                    channel.getBytes(StandardCharsets.UTF_8),
                    "test-body".getBytes(StandardCharsets.UTF_8)
            );
            subscriber.onMessage(message, null);
        }

        verify(logFileWriterService, never()).writeLogAsync(anyString(), anyString());
    }
}
