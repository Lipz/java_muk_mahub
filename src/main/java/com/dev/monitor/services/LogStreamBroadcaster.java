package com.dev.monitor.services;

import com.dev.monitor.dto.server.LogStreamBackfill;
import com.dev.monitor.dto.server.LogStreamLine;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Fans live log lines out to SSE subscribers, in memory. The one Redis subscription
 * feeds every viewer, so opening a tail never touches Redis.
 *
 * <p>Each subscriber gets a bounded queue drained by its own virtual thread, so a slow
 * browser can never stall the ingest lane that publishes into it. A subscriber that
 * falls {@value #QUEUE_CAPACITY} lines behind is closed rather than silently skipping
 * lines; the client reconnects and starts over.
 */
@Service
public class LogStreamBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(LogStreamBroadcaster.class);

    static final int QUEUE_CAPACITY = 1_000;
    /** Sent before closing a stream the user replaced; the client must not reconnect. */
    static final String EVICTED_EVENT = "evicted";

    private final int maxPerUser;
    private final long heartbeatMillis;

    // "serverUuid|channel" -> subscribers tailing it
    private final Map<String, Set<Tail>> byKey = new ConcurrentHashMap<>();
    // username -> their open streams, oldest first
    private final Map<String, Deque<Tail>> byUser = new ConcurrentHashMap<>();
    // "serverUuid|channel" -> last sequence number handed out
    private final Map<String, AtomicLong> sequences = new ConcurrentHashMap<>();

    public LogStreamBroadcaster(@Value("${log.stream.max-per-user:3}") int maxPerUser,
                                @Value("${log.stream.heartbeat-seconds:15}") long heartbeatSeconds) {
        this.maxPerUser = maxPerUser;
        this.heartbeatMillis = TimeUnit.SECONDS.toMillis(heartbeatSeconds);
    }

    public static String streamKey(String serverUuid, String channel) {
        return serverUuid + "|" + channel;
    }

    /**
     * Open a stream for {@code username}. It receives nothing until {@link Tail#attach} is
     * called. When this pushes the user over the cap, their oldest streams are evicted: a
     * refreshed tab often leaves a dead connection behind that would otherwise lock the
     * user out until the next heartbeat notices it.
     */
    public Tail open(String username, String key) {
        // No timeout: dead peers are found by the heartbeat write failing
        Tail sub = new Tail(username, key, new SseEmitter(0L));

        List<Tail> evicted = new ArrayList<>();
        byUser.compute(username, (u, subs) -> {
            Deque<Tail> deque = subs != null ? subs : new ArrayDeque<>();
            deque.addLast(sub);
            while (deque.size() > maxPerUser) {
                evicted.add(deque.pollFirst());
            }
            return deque;
        });

        // Outside compute(): close() removes from the same maps
        evicted.forEach(s -> s.close(EVICTED_EVENT));
        sub.start();
        log.debug("User [{}] tails [{}], {} open stream(s)", username, key, streamCount(username));
        return sub;
    }

    /**
     * Push one line to every subscriber of {@code key}. Called from the ingest lane of the
     * channel, so lines of one key arrive here, and are numbered, in publish order.
     */
    public void publish(String key, String timestamp, String line) {
        long seq = sequences.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
        Set<Tail> subs = byKey.get(key);
        if (subs == null) {
            return;
        }
        LogStreamLine event = new LogStreamLine(seq, timestamp, line);
        for (Tail sub : subs) {
            if (!sub.queue.offer(event)) {
                log.warn("Tail of [{}] for user [{}] fell {} lines behind, closing it",
                        key, sub.username, QUEUE_CAPACITY);
                sub.close(null);
            }
        }
    }

    public boolean hasSubscribers(String key) {
        return byKey.containsKey(key);
    }

    public int streamCount(String username) {
        Deque<Tail> subs = byUser.get(username);
        return subs == null ? 0 : subs.size();
    }

    @PreDestroy
    void shutdown() {
        // byUser, not byKey: it also holds tails still waiting for their backfill
        for (String username : byUser.keySet()) {
            List<Tail> open = new ArrayList<>();
            byUser.computeIfPresent(username, (u, subs) -> {
                open.addAll(subs);
                return subs;
            });
            open.forEach(s -> s.close(null));
        }
    }

    private void unregister(Tail sub) {
        byKey.computeIfPresent(sub.key, (k, subs) -> {
            subs.remove(sub);
            return subs.isEmpty() ? null : subs;
        });
        byUser.computeIfPresent(sub.username, (u, subs) -> {
            subs.remove(sub);
            return subs.isEmpty() ? null : subs;
        });
    }

    /** One open stream. */
    public final class Tail {

        final String username;
        final String key;
        final SseEmitter emitter;
        // LogStreamBackfill first, then LogStreamLine
        final BlockingQueue<Object> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
        final AtomicBoolean closed = new AtomicBoolean();
        volatile Thread sender;

        Tail(String username, String key, SseEmitter emitter) {
            this.username = username;
            this.key = key;
            this.emitter = emitter;
            emitter.onCompletion(() -> close(null));
            emitter.onTimeout(() -> close(null));
            emitter.onError(e -> close(null));
        }

        public SseEmitter emitter() {
            return emitter;
        }

        /**
         * Send {@code backfill}, then go live. Call it on the ingest lane of the channel: no
         * line can be published between the file read and this, so there is no gap and no
         * duplicate between the two.
         */
        public void attach(List<String> backfill) {
            long seq = sequences.computeIfAbsent(key, k -> new AtomicLong()).get();
            queue.offer(new LogStreamBackfill(seq, backfill));
            byKey.compute(key, (k, subs) -> {
                // Checked under the same lock close() unregisters with: an evicted tail
                // must not be left behind in byKey
                if (closed.get()) {
                    return subs;
                }
                Set<Tail> set = subs != null ? subs : new CopyOnWriteArraySet<>();
                set.add(this);
                return set;
            });
        }

        void start() {
            sender = Thread.ofVirtual().name("log-tail-" + key).start(this::drain);
        }

        private void drain() {
            try {
                // Commits the response headers right away, so proxies see the stream open
                emitter.send(SseEmitter.event().comment("connected"));
                while (!closed.get()) {
                    Object next = queue.poll(heartbeatMillis, TimeUnit.MILLISECONDS);
                    switch (next) {
                        case null -> emitter.send(SseEmitter.event().comment("ping"));
                        case LogStreamBackfill backfill -> emitter.send(SseEmitter.event()
                                .name("backfill")
                                .data(backfill, MediaType.APPLICATION_JSON));
                        case LogStreamLine line -> emitter.send(SseEmitter.event()
                                .id(String.valueOf(line.seq()))
                                .name("line")
                                .data(line, MediaType.APPLICATION_JSON));
                        default -> throw new IllegalStateException("Unexpected event " + next);
                    }
                }
            } catch (InterruptedException e) {
                // close() woke us up
            } catch (IOException | IllegalStateException e) {
                log.debug("Tail of [{}] for user [{}] disconnected: {}", key, username, e.getMessage());
                close(null);
            }
        }

        /** Idempotent. {@code event}, when given, is sent as a last named event. */
        void close(String event) {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            unregister(this);
            try {
                if (event != null) {
                    emitter.send(SseEmitter.event().name(event).data(event));
                }
                emitter.complete();
            } catch (IOException | IllegalStateException ignored) {
                // Client already gone
            }
            Thread t = sender;
            if (t != null) {
                t.interrupt();
            }
        }
    }
}
