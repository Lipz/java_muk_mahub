package com.dev.mhub.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class RedisMessageSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisMessageSubscriber.class);

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        String matchedPattern = pattern != null ? new String(pattern, StandardCharsets.UTF_8) : "";
        log.info("==================================================");
        log.info("[REDIS SUB] Message Received!");
        log.info("Pattern : {}", matchedPattern);
        log.info("Channel : {}", channel);
        log.info("Payload : {}", body);
        log.info("==================================================");
    }
}
