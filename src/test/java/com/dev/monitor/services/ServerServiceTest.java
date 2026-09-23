package com.dev.monitor.services;

import com.dev.monitor.dto.server.ServerGroupResponse;
import com.dev.monitor.entity.server.Server;
import com.dev.monitor.entity.server.ServerType;
import com.dev.monitor.entity.system.SystemEntity;
import com.dev.monitor.exception.server.ServerNotFoundException;
import com.dev.monitor.exception.system.SystemNotFoundException;
import com.dev.monitor.repository.server.ServerRepository;
import com.dev.monitor.repository.server.ServerResourceRepository;
import com.dev.monitor.repository.server.ServerStorageRepository;
import com.dev.monitor.repository.server.ServerStorageSummaryRepository;
import com.dev.monitor.repository.system.SystemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServerServiceTest {

    @Mock
    private ServerRepository serverRepository;

    @Mock
    private SystemRepository systemRepository;

    @Mock
    private ServerPresenceService presenceService;

    @Mock
    private ServerStorageSummaryRepository storageSummaryRepository;

    @Mock
    private ServerStorageRepository storageRepository;

    @Mock
    private ServerResourceRepository resourceRepository;

    @InjectMocks
    private ServerService serverService;

    private SystemEntity system1;
    private SystemEntity system2;
    private Server server1;
    private Server server2;
    private Server server3;

    @BeforeEach
    void setUp() {
        system1 = new SystemEntity("ecustoms");
        system1.setUuid("sys-01");

        system2 = new SystemEntity("asycuda");
        system2.setUuid("sys-02");

        server1 = new Server(system1, "ecustoms-Oracle-Dev", "10.0.9.16", ServerType.DATABASE);
        server1.setUuid("srv-01");

        server2 = new Server(system1, "ecustoms-App-Dev", "10.0.9.17", ServerType.APP);
        server2.setUuid("srv-02");

        server3 = new Server(system2, "asycuda-Web", "10.0.9.18", ServerType.WEB);
        server3.setUuid("srv-03");
    }

    @Test
    void shouldGetAllServersGroupedBySystem() {
        when(serverRepository.findAllWithSystem()).thenReturn(List.of(server1, server2, server3));

        List<ServerGroupResponse> result = serverService.getAllServers(null);

        assertEquals(2, result.size());

        // First group: ecustoms with 2 servers
        ServerGroupResponse group1 = result.get(0);
        assertEquals("sys-01", group1.systemId());
        assertEquals("ecustoms", group1.systemName());
        assertEquals(2, group1.server().size());
        assertEquals("srv-01", group1.server().get(0).uuid());
        assertEquals("ecustoms-Oracle-Dev", group1.server().get(0).name());
        assertEquals("10.0.9.16", group1.server().get(0).ip());
        assertEquals(ServerType.DATABASE, group1.server().get(0).serverType());
        assertEquals("srv-02", group1.server().get(1).uuid());
        assertEquals("ecustoms-App-Dev", group1.server().get(1).name());

        // Second group: asycuda with 1 server
        ServerGroupResponse group2 = result.get(1);
        assertEquals("sys-02", group2.systemId());
        assertEquals("asycuda", group2.systemName());
        assertEquals(1, group2.server().size());
        assertEquals("srv-03", group2.server().get(0).uuid());
        assertEquals("asycuda-Web", group2.server().get(0).name());
    }

    @Test
    void shouldGetServersFilteredBySystemId() {
        when(systemRepository.findById("sys-01")).thenReturn(Optional.of(system1));
        when(serverRepository.findBySystemUuid("sys-01")).thenReturn(List.of(server1, server2));

        List<ServerGroupResponse> result = serverService.getAllServers("sys-01");

        assertEquals(1, result.size());
        ServerGroupResponse group = result.get(0);
        assertEquals("sys-01", group.systemId());
        assertEquals("ecustoms", group.systemName());
        assertEquals(2, group.server().size());
        assertEquals("srv-01", group.server().get(0).uuid());
        assertEquals("srv-02", group.server().get(1).uuid());
    }

    @Test
    void shouldReturnEmptyServerListWhenSystemHasNoServers() {
        when(systemRepository.findById("sys-01")).thenReturn(Optional.of(system1));
        when(serverRepository.findBySystemUuid("sys-01")).thenReturn(List.of());

        List<ServerGroupResponse> result = serverService.getAllServers("sys-01");

        assertEquals(1, result.size());
        ServerGroupResponse group = result.get(0);
        assertEquals("sys-01", group.systemId());
        assertEquals("ecustoms", group.systemName());
        assertTrue(group.server().isEmpty());
    }

    @Test
    void shouldThrowExceptionWhenSystemNotFound() {
        when(systemRepository.findById("invalid-id")).thenReturn(Optional.empty());

        assertThrows(SystemNotFoundException.class, () -> serverService.getAllServers("invalid-id"));
    }

    @Test
    void shouldBucketHistoryIntoThirtyPoints() {
        when(serverRepository.existsById("srv-01")).thenReturn(true);
        when(resourceRepository.findHistory(eq("srv-01"), any(), anyInt())).thenReturn(List.of());

        serverService.getResourceHistory("srv-01", 60);

        verify(resourceRepository).findHistory(eq("srv-01"), any(), eq(120));
    }

    @Test
    void shouldClampHistoryWindow() {
        when(serverRepository.existsById("srv-01")).thenReturn(true);
        when(resourceRepository.findHistory(eq("srv-01"), any(), anyInt())).thenReturn(List.of());

        serverService.getResourceHistory("srv-01", 1);
        serverService.getResourceHistory("srv-01", 1_000_000);

        // 5 minutes minimum -> 10s buckets; 24 hours maximum -> 48m buckets
        verify(resourceRepository).findHistory(eq("srv-01"), any(), eq(10));
        verify(resourceRepository).findHistory(eq("srv-01"), any(), eq(2880));
    }

    @Test
    void shouldThrowWhenHistoryServerNotFound() {
        when(serverRepository.existsById("missing")).thenReturn(false);

        assertThrows(ServerNotFoundException.class, () -> serverService.getResourceHistory("missing", 60));
        verifyNoInteractions(resourceRepository);
    }
}
