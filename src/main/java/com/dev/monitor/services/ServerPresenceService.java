package com.dev.monitor.services;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Tracks which servers are currently reporting.
 *
 * Presence is derived, never stored as a boolean: a flag in the database goes
 * stale the moment an agent dies, because nothing is left running to flip it
 * back to false. Instead each received scrape writes a Redis key with a TTL,
 * so "online" is simply "the key has not expired yet" -- expiry is Redis's
 * job, and there is no timestamp arithmetic or clock-skew handling.
 *
 * The stored value is the hub's receive time, not the agent's own timestamp.
 * The agent's clock may drift; receive time is the honest measure of "we can
 * still hear it". That is also why lastSeenAt can differ slightly from the
 * record_timestamp of the row the same message produced.
 *
 * The TTL must comfortably exceed the agent's publish interval or normal
 * jitter shows up as flapping -- roughly 3x. At a 3-5s scrape, 15s.
 */
@Service
public class ServerPresenceService {

    private static final Logger log = LoggerFactory.getLogger(ServerPresenceService.class);
    private static final String KEY_PREFIX = "server:lastseen:";

    private final StringRedisTemplate redis;
    private final Duration ttl;

    private final AtomicLong touchFailures = new AtomicLong();
    private final AtomicLong lookupFailures = new AtomicLong();

    public ServerPresenceService(StringRedisTemplate redis,
                                 @Value("${monitor.presence.ttl-seconds:15}") long ttlSeconds) {
        this.redis = redis;
        this.ttl = Duration.ofSeconds(ttlSeconds);
        log.info("Server presence TTL set to {}s", ttlSeconds);
    }

    /** Records that we just heard from this server. Called once per received scrape. */
    public void touch(String serverId) {
        try {
            redis.opsForValue().set(
                    KEY_PREFIX + serverId,
                    Long.toString(System.currentTimeMillis()),
                    ttl);
        } catch (RuntimeException e) {
            // Presence is best-effort: losing a heartbeat write must never
            // interfere with ingesting the metric row itself.
            long n = touchFailures.incrementAndGet();
            if (n % 100 == 1) {
                log.warn("Presence touch failed for [{}] ({} failures so far): {}",
                        serverId, n, e.getMessage());
            }
        }
    }

    /**
     * Current presence for a server.
     *
     * Three outcomes, distinguished because they mean different things:
     * online=true with a timestamp, online=false when the key is absent (we
     * know it is not reporting), and online=null when Redis could not be
     * reached (we genuinely do not know).
     */
    public Presence lookup(String serverId) {
        try {
            String value = redis.opsForValue().get(KEY_PREFIX + serverId);
            if (value == null) {
                return Presence.offline();
            }
            return new Presence(true, Instant.ofEpochMilli(Long.parseLong(value)));
        } catch (RuntimeException e) {
            // Covers both an unreachable Redis and a malformed stored value
            // (NumberFormatException is itself a RuntimeException).
            // Degrade rather than fail the whole endpoint: a presence lookup
            // should not be able to take down GET /api/v1/servers/{uuid}.
            long n = lookupFailures.incrementAndGet();
            if (n % 100 == 1) {
                log.warn("Presence lookup failed for [{}] ({} failures so far): {}",
                        serverId, n, e.getMessage());
            }
            return Presence.unknown();
        }
    }

    /**
     * Presence for many servers in a single round trip.
     *
     * Deliberately MGET rather than a lookup per server: the list endpoint
     * renders every server at once, and N sequential round trips to Redis
     * would put network latency on the critical path of every page load.
     */
    public Map<String, Presence> lookup(Collection<String> serverIds) {
        if (serverIds == null || serverIds.isEmpty()) {
            return Map.of();
        }

        List<String> ids = List.copyOf(serverIds);
        List<String> keys = ids.stream().map(id -> KEY_PREFIX + id).toList();

        Map<String, Presence> result = new HashMap<>(ids.size());
        try {
            List<String> values = redis.opsForValue().multiGet(keys);
            for (int i = 0; i < ids.size(); i++) {
                String value = values == null ? null : values.get(i);
                result.put(ids.get(i), value == null
                        ? Presence.offline()
                        : new Presence(true, Instant.ofEpochMilli(Long.parseLong(value))));
            }
        } catch (RuntimeException e) {
            long n = lookupFailures.incrementAndGet();
            if (n % 100 == 1) {
                log.warn("Batch presence lookup failed for {} servers ({} failures so far): {}",
                        ids.size(), n, e.getMessage());
            }
            // Unknown, not offline -- see lookup(String).
            ids.forEach(id -> result.put(id, Presence.unknown()));
        }
        return result;
    }

    /** online is nullable: null means "could not determine", not "offline". */
    public record Presence(Boolean online, Instant lastSeenAt) {

        private static final Presence OFFLINE = new Presence(false, null);
        private static final Presence UNKNOWN = new Presence(null, null);

        public static Presence offline() {
            return OFFLINE;
        }

        public static Presence unknown() {
            return UNKNOWN;
        }
    }
}
