package com.dev.monitor.repository;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dev.monitor.repository.server.ServerLogRepository;
import com.dev.monitor.repository.server.ServerRepository;
import com.dev.monitor.repository.server.ServerResourceRepository;
import com.dev.monitor.repository.server.ServerStorageRepository;
import com.dev.monitor.repository.server.ServerStorageSummaryRepository;
import com.dev.monitor.repository.system.SystemRepository;
import com.dev.monitor.repository.user.UserRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

/**
 * Phase 2 gate: proves the repository layer talks to the real PostgreSQL
 * instance described by init.sql.
 *
 * A JPA slice rather than @SpringBootTest on purpose -- it skips Redis
 * autoconfiguration, so it runs without a reachable Redis broker.
 *
 * Replace.NONE keeps the configured datasource instead of substituting an
 * embedded database, which is the whole point: these queries must compile
 * and execute on PostgreSQL, not H2.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PostgresConnectivityTest {

    @Autowired
    private ServerRepository serverRepository;

    @Autowired
    private SystemRepository systemRepository;

    @Autowired
    private ServerLogRepository serverLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ServerResourceRepository serverResourceRepository;

    @Autowired
    private ServerStorageRepository serverStorageRepository;

    @Autowired
    private ServerStorageSummaryRepository serverStorageSummaryRepository;

    @Test
    void findByIdWithLogsExecutesOnPostgres() {
        // Exercises the LEFT JOIN FETCH s.logs query added for the
        // GET /api/v1/servers/{uuid} endpoint.
        assertTrue(serverRepository.findByIdWithLogs("no-such-uuid").isEmpty());
    }

    @Test
    void fetchJoinQueriesExecuteOnPostgres() {
        assertNotNull(serverRepository.findAllWithSystem());
        assertNotNull(serverRepository.findBySystemUuid("no-such-system"));
        assertNotNull(serverLogRepository.findAllWithServer());
        assertNotNull(serverLogRepository.findByServerUuid("no-such-server"));
    }

    @Test
    void derivedQueriesExecuteOnPostgres() {
        assertTrue(serverRepository.findByIp("0.0.0.0").isEmpty());
        assertTrue(serverRepository.existsBySystemUuidAndName("nope", "nope") == false);
        assertTrue(systemRepository.findById("no-such-system").isEmpty());
        assertTrue(userRepository.findByEmailIgnoreCase("nobody@example.com").isEmpty());
    }

    @Test
    void hypertableQueriesBindCompositeKeysAndInstants() {
        // Exercises the @IdClass mapping and the Instant <-> timestamptz
        // binding on both hypertables. Under ddl-auto=validate, simply
        // reaching these queries proves every mapped column in
        // server_resources and server_storage matches init.sql.
        Instant end = Instant.now();
        Instant start = end.minusSeconds(3600);

        assertNotNull(serverResourceRepository.findByServerIdOrderByRecordTimestampDesc("nope"));
        assertNotNull(serverResourceRepository.findByServerIdAndRecordTimestampBetween(
                "nope", start, end));

        assertNotNull(serverStorageRepository.findByServerIdOrderByRecordTimestampDesc("nope"));
        assertNotNull(serverStorageRepository.findByServerIdAndMountPointOrderByRecordTimestampDesc(
                "nope", "/"));
        assertNotNull(serverStorageSummaryRepository.findByServerIdOrderByRecordTimestampDesc("nope"));
        assertNotNull(serverStorageSummaryRepository.findByServerIdAndRecordTimestampBetween(
                "nope", start, end));
    }
}
