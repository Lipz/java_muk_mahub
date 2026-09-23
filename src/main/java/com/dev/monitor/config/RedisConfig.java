package com.dev.monitor.config;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.dev.monitor.redis.RedisMessageSubscriber;

@Configuration
public class RedisConfig {

    // Deliberately not a bean: an Executor bean would make Spring Boot back off
    // its default applicationTaskExecutor (used by @Async and MVC async/SSE).
    private final ExecutorService dispatchExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "redis-dispatch");
        t.setDaemon(true);
        return t;
    });

    @Bean
        public PatternTopic patternTopic() {
            return new PatternTopic("*");
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisMessageSubscriber subscriber,
            PatternTopic patternTopic) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        // The default executor starts a new thread per message, so two lines published
        // back to back could reach the listener in either order. A single dispatch thread
        // keeps Redis publish order; listeners must only enqueue and return.
        container.setTaskExecutor(dispatchExecutor);
        container.addMessageListener(subscriber, patternTopic);
        return container;
    }

    @PreDestroy
    void shutdownDispatchExecutor() {
        dispatchExecutor.shutdown();
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
}
