package com.dev.monitor.redis;

import com.dev.monitor.services.LogFileWriterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class RedisMessageSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisMessageSubscriber.class);

    public static final String LOG_CHANNEL_PREFIX = "log-";
    public static final String STORAGE_CHANNEL_PREFIX = "storage-";
    public static final String RESOURCE_CHANNEL_PREFIX = "resource-";
    public static final String DOCKER_CHANNEL_PREFIX = "docker-";

    private final LogFileWriterService logFileWriterService;

    public RedisMessageSubscriber(LogFileWriterService logFileWriterService) {
        this.logFileWriterService = logFileWriterService;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        String matchedPattern = pattern != null ? new String(pattern, StandardCharsets.UTF_8) : "";

        log.info("[REDIS SUB] Channel: [{}], Pattern: [{}], Payload : [{}]", channel, matchedPattern, body );

        if (channel.startsWith(LOG_CHANNEL_PREFIX)) {
            // Asynchronously append log line to /logs/{server}/{channel}/{yyyy-MM-dd.log}
            logFileWriterService.writeLogAsync(channel, body);
        } else if (channel.startsWith(STORAGE_CHANNEL_PREFIX)) {
            // Placeholder for storage metrics handler
            log.debug("Received storage channel message on [{}], handler pending implementation", channel);
        } else if (channel.startsWith(RESOURCE_CHANNEL_PREFIX)) {
            // Placeholder for resource metrics handler
            log.debug("Received resource channel message on [{}], handler pending implementation", channel);
        } else if (channel.startsWith(DOCKER_CHANNEL_PREFIX)) {
            // Placeholder for docker metrics handler
            log.debug("Received docker channel message on [{}], handler pending implementation", channel);
        } else {
            log.debug("Channel [{}] does not match any supported prefix, ignoring for verify only :D", channel);
        }
    }
}
