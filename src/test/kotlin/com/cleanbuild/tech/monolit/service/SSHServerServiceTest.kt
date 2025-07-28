package com.cleanbuild.tech.monolit.service

import com.cleanbuild.tech.monolit.DbRecord.SSHConfig
import com.cleanbuild.tech.monolit.com.cleanbuild.tech.monolit.config.LocalSSHServer
import com.cleanbuild.tech.monolit.com.cleanbuild.tech.monolit.repository.CRUDOperation
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import java.net.Socket
import java.sql.Timestamp
import javax.sql.DataSource

@SpringBootTest
class SSHServerServiceTest {

    @Autowired
    private lateinit var sshServerService: LocalSSHServer

    @MockBean
    private lateinit var dataSource: DataSource

    @MockBean
    private lateinit var crudOperation: CRUDOperation<SSHConfig>

    private val testConfig = SSHConfig(
        name = "test-config",
        serverHost = "localhost",
        port = 2222,
        password = "testpassword",
        createdAt = Timestamp(System.currentTimeMillis()),
        updatedAt = Timestamp(System.currentTimeMillis())
    )

    @BeforeEach
    fun setUp() {
        // Mock the CRUDOperation to return our test config
        whenever(crudOperation.findByPrimaryKey("test-config")).thenReturn(testConfig)
        whenever(crudOperation.findByPrimaryKey("localhost-test")).thenReturn(testConfig)
        whenever(crudOperation.insert(any())).thenReturn(listOf(testConfig))
        
        // Set the crudOperation field in SSHServerService using reflection
        val field = LocalSSHServer::class.java.getDeclaredField("crudOperation")
        field.isAccessible = true
        field.set(sshServerService, crudOperation)
    }

    @AfterEach
    fun tearDown() {
        // Make sure the SSH server is stopped after each test
        sshServerService.stopServer()
    }

    @Test
    fun `test create default config`() {
        val configName = sshServerService.createDefaultConfig()
        assertEquals("localhost-test", configName)
        verify(crudOperation).findByPrimaryKey("localhost-test")
    }

    @Test
    fun `test start server with valid config`() {
        val result = sshServerService.startServer("test-config")
        assertTrue(result)
        assertTrue(sshServerService.isServerRunning())
    }

    @Test
    fun `test start server with invalid config`() {
        whenever(crudOperation.findByPrimaryKey("invalid-config")).thenReturn(null)
        val result = sshServerService.startServer("invalid-config")
        assertFalse(result)
        assertFalse(sshServerService.isServerRunning())
    }

    @Test
    fun `test stop server when running`() {
        // First start the server
        sshServerService.startServer("test-config")
        assertTrue(sshServerService.isServerRunning())
        
        // Then stop it
        val result = sshServerService.stopServer()
        assertTrue(result)
        assertFalse(sshServerService.isServerRunning())
    }

    @Test
    fun `test stop server when not running`() {
        // Make sure server is not running
        assertFalse(sshServerService.isServerRunning())
        
        // Try to stop it
        val result = sshServerService.stopServer()
        assertFalse(result)
    }

    @Test
    fun `test server can be connected to`() {
        // Start the server
        sshServerService.startServer("test-config")
        assertTrue(sshServerService.isServerRunning())
        
        // Try to connect to the server (just testing if port is open)
        var socket: Socket? = null
        try {
            socket = Socket("localhost", 2222)
            assertTrue(socket.isConnected)
        } catch (e: Exception) {
            fail("Failed to connect to SSH server: ${e.message}")
        } finally {
            socket?.close()
        }
    }
}