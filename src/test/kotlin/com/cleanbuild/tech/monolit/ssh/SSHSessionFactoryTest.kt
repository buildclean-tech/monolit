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
import kotlin.test.assertNotEquals
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
    fun `getSession should create a new session each time`() {
        // Act
        val session1 = sshSessionFactory.getSession(testConfig)
        
        // Assert
        assertNotNull(session1)
        assertTrue(session1.isOpen)
        assertTrue(session1.isAuthenticated)
        
        // Get another session
        val session2 = sshSessionFactory.getSession(testConfig)
        
        // Verify the sessions are different instances (not cached)
        assertNotEquals(session1, session2)
        assertTrue(session2.isOpen)
        assertTrue(session2.isAuthenticated)
        
        // Clean up
        session1.close()
        session2.close()
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
        
        // Clean up
        session.close()
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
    
    @Test
    fun `close should stop the client`() {
        // Arrange - Create a session first
        val session = sshSessionFactory.getSession(testConfig)
        assertTrue(session.isOpen)
        
        // Create another session
        val session2 = sshSessionFactory.getSession(testConfig)
        assertTrue(session2.isOpen)
        
        // Act - Close the factory
        sshSessionFactory.close()
        
        // Create a new factory for other tests
        sshSessionFactory = SSHSessionFactory()
    }
}