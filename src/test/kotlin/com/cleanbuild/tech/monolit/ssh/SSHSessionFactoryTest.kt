package com.cleanbuild.tech.monolit.ssh

import com.cleanbuild.tech.monolit.DbRecord.SSHConfig
import com.cleanbuild.tech.monolit.config.LocalSSHServer
import org.apache.sshd.server.SshServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Path
import java.sql.Timestamp
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SSHSessionFactoryTest {
    private val logger = LoggerFactory.getLogger(SSHSessionFactoryTest::class.java)
    
    // Use a non-privileged port for testing
    private val TEST_SSH_PORT = 2222
    
    private lateinit var sshServer: SshServer
    private lateinit var sshSessionFactory: SSHSessionFactory
    private lateinit var testConfig: SSHConfig
    
    @TempDir
    lateinit var tempDir: Path
    
    @BeforeAll
    fun setupServer() {
        
        // Create and start a local SSH server for testing using the LocalSSHServer class
        val localSSHServer = LocalSSHServer(
            port = TEST_SSH_PORT,
            password = "testpass"
        )
        
        // Start the server
        sshServer = localSSHServer.startServer()
        logger.info("Test SSH server started on localhost:{}", TEST_SSH_PORT)
        
        // Create the SSH session factory
        sshSessionFactory = SSHSessionFactory()
    }
    
    @AfterAll
    fun tearDownServer() {
        // Stop the SSH server
        sshServer.stop(true)
        logger.info("Test SSH server stopped")
        
        // Close the SSH session factory
        sshSessionFactory.close()
    }
    
    @BeforeEach
    fun setUp() {
        // Create a test SSH config that points to our test server
        testConfig = SSHConfig(
            name = "test-config",
            serverHost = "localhost",
            port = TEST_SSH_PORT,
            username = "testuser",
            password = "testpass",
            createdAt = Timestamp(System.currentTimeMillis()),
            updatedAt = Timestamp(System.currentTimeMillis())
        )
    }
    
    @Test
    fun `getSession should create and cache a new session when none exists`() {
        // Act
        val session = sshSessionFactory.getSession(testConfig)
        
        // Assert
        assertNotNull(session)
        assertTrue(session.isOpen)
        assertTrue(session.isAuthenticated)
        
        // Verify the session is cached by getting it again
        val cachedSession = sshSessionFactory.getSession(testConfig)
        
        // Verify the session is the same instance (cached)
        assertEquals(session, cachedSession)
    }
    
    @Test
    fun `getSession should return cached session when it exists and is healthy`() {
        // Arrange - Create a session first
        val session1 = sshSessionFactory.getSession(testConfig)
        
        // Act - Get the session again
        val session2 = sshSessionFactory.getSession(testConfig)
        
        // Assert - Verify the sessions are the same instance
        assertEquals(session1, session2)
        assertTrue(session2.isOpen)
        assertTrue(session2.isAuthenticated)
    }
    
    @Test
    fun `removeSession should remove and close the session`() {
        // Arrange - Create a session first
        val session = sshSessionFactory.getSession(testConfig)
        assertTrue(session.isOpen)
        
        // Act - Remove the session
        sshSessionFactory.removeSession(testConfig.name)
        
        // Assert - Verify the session is closed
        assertFalse(session.isOpen)
        
        // Verify getting the session again creates a new one
        val newSession = sshSessionFactory.getSession(testConfig)
        assertNotNull(newSession)
        assertTrue(newSession.isOpen)
        assertTrue(newSession.isAuthenticated)
    }
    
    @Test
    fun `close should close all sessions and stop the client`() {
        // Arrange - Create a session first
        val session = sshSessionFactory.getSession(testConfig)
        assertTrue(session.isOpen)
        
        // Create another config and session
        val testConfig2 = testConfig.copy(name = "test-config-2")
        val session2 = sshSessionFactory.getSession(testConfig2)
        assertTrue(session2.isOpen)
        
        // Act - Close the factory
        sshSessionFactory.close()
        
        // Assert - Verify the sessions are closed
        assertFalse(session.isOpen)
        assertFalse(session2.isOpen)
        
        // Create a new factory for other tests
        sshSessionFactory = SSHSessionFactory()
    }
    
    @Test
    fun `getSession should execute commands successfully`() {
        // Arrange
        val session = sshSessionFactory.getSession(testConfig)
        
        // Act - Execute a test command
        val channel = session.createExecChannel("echo \"test command\"")
        channel.open().verify(5, TimeUnit.SECONDS)
        
        // Read the command output
        val outputStream = channel.getInvertedOut()
        val buffer = ByteArray(1024)
        val bytesRead = outputStream.read(buffer)
        val output = if (bytesRead > 0) String(buffer, 0, bytesRead) else ""
        
        // Wait for command to complete
        channel.waitFor(java.util.EnumSet.of(org.apache.sshd.client.channel.ClientChannelEvent.CLOSED), 5000)
        channel.close(false)
        
        // Assert
        assertEquals("test command\n", output)
    }
    
    @Test
    fun `getSession should throw exception when authentication fails`() {
        // Arrange - Create a config with wrong password
        val badConfig = testConfig.copy(
            name = "bad-config",
            password = "wrongpass"
        )
        
        // Act & Assert - Verify that getSession throws an exception
        val exception = org.junit.jupiter.api.assertThrows<IOException> {
            sshSessionFactory.getSession(badConfig)
        }
        
        // Verify the exception message
        assertTrue(exception.message?.contains("Authentication failed") == true)
    }
}