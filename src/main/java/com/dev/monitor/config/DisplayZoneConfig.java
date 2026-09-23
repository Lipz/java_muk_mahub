package com.dev.monitor.config;

import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The zone API responses are rendered in.
 *
 * Storage stays absolute -- every timestamp column is timestamptz and every
 * entity field an Instant. This is purely a presentation choice: responses
 * carry an explicit offset (…T15:57:46+07:00) instead of UTC (…T08:57:46Z),
 * so they read as local time while remaining unambiguous instants.
 *
 * Deliberately a named zone from configuration, never ZoneId.systemDefault().
 * The default would differ between a developer laptop and a deployed server,
 * silently changing every timestamp in every response with nothing to
 * indicate it had happened.
 */
@Configuration
public class DisplayZoneConfig {

    private static final Logger log = LoggerFactory.getLogger(DisplayZoneConfig.class);

    @Bean
    public ZoneId displayZone(@Value("${monitor.display-zone:Asia/Phnom_Penh}") String zone) {
        ZoneId zoneId = ZoneId.of(zone);
        log.info("API timestamps will be rendered in zone [{}]", zoneId);
        return zoneId;
    }
}
