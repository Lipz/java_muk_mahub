package com.dev.monitor.dto.server;

import java.util.List;

/**
 * First event of a live resource stream: the node's latest scrapes, oldest first. A client
 * replaces whatever it shows with these; {@code sample} events follow.
 *
 * {@code skewMs} is how far the agent's clock runs behind the hub's (negative when ahead),
 * measured on the newest scrape the hub received. Null when the node is not reporting right
 * now. Scrape timestamps are the agent's own, so without it a client cannot place them on
 * its clock until a live sample arrives.
 */
public record ResourceStreamBackfill(Long skewMs, List<ServerResponse.Resources> samples) {}
