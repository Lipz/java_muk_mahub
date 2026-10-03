package com.dev.monitor.services;

import com.dev.monitor.dto.server.ResourceStreamBackfill;
import com.dev.monitor.dto.server.ServerResponse;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Fans live resource scrapes out to SSE subscribers, per server, in memory. Feeds the node
 * page's CPU / memory / network tiles.
 *
 * <p>Gap-free join of history and live: every server keeps the scrapes of the last
 * {@link #RECENT_WINDOW} in memory. The database lags ingest by up to one batch flush, so
 * a backfill read from it alone could miss the newest scrapes; merged with this window it
 * cannot. {@link #publish} and {@link Stream#attach} take the same per-server lock, so a
 * scrape is either in the window a subscriber attached with, or queued to it live -- never
 * both, never neither.
 *
 * <p>Streams count against their own per-user cap, separate from the log tail's: opening a
 * log must not freeze a sparkline in another tab. The oldest stream is evicted past the cap,
 * as with log tails.
 */
@Service
public class ResourceStreamBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(ResourceStreamBroadcaster.class);

    /** Comfortably longer than the writer's batch flush (1s) plus a slow backfill query. */
    static final Duration RECENT_WINDOW = Duration.ofSeconds(30);
    /** At a 3-5s scrape a healthy stream holds one or two; 100 behind means a dead reader. */
    static final int QUEUE_CAPACITY = 100;
    /** Sent before closing a stream the user replaced; the client must not reconnect. */
    static final String EVICTED_EVENT = "evicted";

    private final int maxPerUser;
    private final long heartbeatMillis;

    // serverId -> the per-server state, created on first publish or subscribe
    private final Map<String, Channel> channels = new ConcurrentHashMap<>();
    // username -> their open streams, oldest first
    private final Map<String, Deque<Stream>> byUser = new ConcurrentHashMap<>();

    public ResourceStreamBroadcaster(@Value("${resource.stream.max-per-user:3}") int maxPerUser,
                                     @Value("${resource.stream.heartbeat-seconds:15}") long heartbeatSeconds) {
        this.maxPerUser = maxPerUser;
        this.heartbeatMillis = TimeUnit.SECONDS.toMillis(heartbeatSeconds);
    }

    /** One scrape, keyed by the instant it was taken so backfill and live can be merged. */
    public record Sample(Instant at, ServerResponse.Resources resources) {
    }

    /** Subscribers and recent scrapes of one server. Guarded by its own monitor. */
    private static final class Channel {
        final Set<Stream> subscribers = ConcurrentHashMap.newKeySet();
        final Deque<Sample> recent = new ArrayDeque<>();
        /** Hub clock minus agent clock on the newest scrape, and when that scrape arrived. */
        long skewMs;
        Instant receivedAt;
    }

    /**
     * Open a stream for {@code username}. It receives nothing until {@link Stream#attach} is
     * called. Past the cap, the user's oldest streams are evicted.
     */
    public Stream open(String username, String serverId) {
        // No timeout: dead peers are found by the heartbeat write failing
        Stream sub = new Stream(username, serverId, new SseEmitter(0L));

        List<Stream> evicted = new ArrayList<>();
        byUser.compute(username, (u, subs) -> {
            Deque<Stream> deque = subs != null ? subs : new ArrayDeque<>();
            deque.addLast(sub);
            while (deque.size() > maxPerUser) {
                evicted.add(deque.pollFirst());
            }
            return deque;
        });

        // Outside compute(): close() removes from the same map
        evicted.forEach(s -> s.close(EVICTED_EVENT));
        sub.start();
        log.debug("User [{}] streams resources of [{}], {} open stream(s)", username, serverId, streamCount(username));
        return sub;
    }

    /** Record one scrape and push it to every subscriber of its server. Called on ingest. */
    public void publish(String serverId, Sample sample) {
        Channel ch = channels.computeIfAbsent(serverId, k -> new Channel());
        Instant received = Instant.now();
        synchronized (ch) {
            ch.receivedAt = received;
            ch.skewMs = Duration.between(sample.at(), received).toMillis();
            ch.recent.addLast(sample);
            Instant horizon = sample.at().minus(RECENT_WINDOW);
            while (!ch.recent.isEmpty() && ch.recent.peekFirst().at().isBefore(horizon)) {
                ch.recent.pollFirst();
            }
            for (Stream sub : ch.subscribers) {
                if (!sub.queue.offer(sample)) {
                    log.warn("Resource stream of [{}] for user [{}] fell {} samples behind, closing it",
                            serverId, sub.username, QUEUE_CAPACITY);
                    sub.close(null);
                }
            }
        }
    }

    public int streamCount(String username) {
        Deque<Stream> subs = byUser.get(username);
        return subs == null ? 0 : subs.size();
    }

    @PreDestroy
    void shutdown() {
        for (String username : byUser.keySet()) {
            List<Stream> open = new ArrayList<>();
            byUser.computeIfPresent(username, (u, subs) -> {
                open.addAll(subs);
                return subs;
            });
            open.forEach(s -> s.close(null));
        }
    }

    private void unregister(Stream sub) {
        Channel ch = channels.get(sub.serverId);
        if (ch != null) {
            // Same lock as attach(), so its closed check and this removal cannot interleave
            synchronized (ch) {
                ch.subscribers.remove(sub);
            }
        }
        byUser.computeIfPresent(sub.username, (u, subs) -> {
            subs.remove(sub);
            return subs.isEmpty() ? null : subs;
        });
    }

    /** One open stream. */
    public final class Stream {

        final String username;
        final String serverId;
        final SseEmitter emitter;
        // ResourceStreamBackfill first, then Sample
        final BlockingQueue<Object> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
        final AtomicBoolean closed = new AtomicBoolean();
        volatile Thread sender;

        Stream(String username, String serverId, SseEmitter emitter) {
            this.username = username;
            this.serverId = serverId;
            this.emitter = emitter;
            emitter.onCompletion(() -> close(null));
            emitter.onTimeout(() -> close(null));
            emitter.onError(e -> close(null));
        }

        public SseEmitter emitter() {
            return emitter;
        }

        /**
         * Send {@code history} merged with the in-memory window as one backfill, oldest first,
         * then go live. {@code history} may overlap the window; each instant is sent once.
         */
        public void attach(List<Sample> history) {
            Channel ch = channels.computeIfAbsent(serverId, k -> new Channel());
            synchronized (ch) {
                // Checked under the lock publish() fans out with: an evicted stream must
                // not be left behind as a subscriber
                if (closed.get()) {
                    return;
                }
                TreeMap<Instant, ServerResponse.Resources> merged = new TreeMap<>();
                history.forEach(s -> merged.put(s.at(), s.resources()));
                ch.recent.forEach(s -> merged.put(s.at(), s.resources()));
                // Only a current measurement: a node gone quiet may come back with another clock
                boolean reporting = ch.receivedAt != null
                        && ch.receivedAt.isAfter(Instant.now().minus(RECENT_WINDOW));
                queue.offer(new ResourceStreamBackfill(reporting ? ch.skewMs : null,
                        new ArrayList<>(merged.values())));
                ch.subscribers.add(this);
            }
        }

        void start() {
            sender = Thread.ofVirtual().name("resource-stream-" + serverId).start(this::drain);
        }

        private void drain() {
            try {
                // Commits the response headers right away, so proxies see the stream open
                emitter.send(SseEmitter.event().comment("connected"));
                while (!closed.get()) {
                    Object next = queue.poll(heartbeatMillis, TimeUnit.MILLISECONDS);
                    switch (next) {
                        case null -> emitter.send(SseEmitter.event().comment("ping"));
                        case ResourceStreamBackfill backfill -> emitter.send(SseEmitter.event()
                                .name("backfill")
                                .data(backfill, MediaType.APPLICATION_JSON));
                        case Sample sample -> emitter.send(SseEmitter.event()
                                .name("sample")
                                .data(sample.resources(), MediaType.APPLICATION_JSON));
                        default -> throw new IllegalStateException("Unexpected event " + next);
                    }
                }
            } catch (InterruptedException e) {
                // close() woke us up
            } catch (IOException | IllegalStateException e) {
                log.debug("Resource stream of [{}] for user [{}] disconnected: {}", serverId, username, e.getMessage());
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
