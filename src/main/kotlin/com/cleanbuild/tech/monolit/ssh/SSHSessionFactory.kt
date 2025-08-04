package com.cleanbuild.tech.monolit.ssh

import com.cleanbuild.tech.monolit.DbRecord.SSHConfig
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.channel.ClientChannelEvent
import org.apache.sshd.client.session.ClientSession
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.IOException
import java.util.EnumSet
import java.util.concurrent.TimeUnit

/**
 * Factory for creating SSH client sessions.
 * 
 * This factory creates new SSH sessions based on SSHConfig and checks their health.
 * Each call to getSession() returns a new session.
 */
@Component
open class SSHSessionFactory {
    private val logger = LoggerFactory.getLogger(SSHSessionFactory::class.java)
    
    // Global SSH client instance
    private val sshClient: SshClient by lazy {
        createSshClient()
    }
    
    /**
     * Creates and starts an SSH client.
     * This method is extracted to make the class more testable.
     * 
     * @return A started SSH client
     */
    protected open fun createSshClient(): SshClient {
        return SshClient.setUpDefaultClient().apply {
            start()
        }
    }
    
    /**
     * Gets a new SSH session for the given SSH configuration.
     * 
     * A new session will be created, checked for health, and returned.
     * 
     * @param sshConfig The SSH configuration to use for creating the session
     * @return A healthy SSH client session
     * @throws IOException If the session cannot be created or is not healthy
     */
    @Throws(IOException::class)
    fun getSession(sshConfig: SSHConfig): ClientSession {
        // Create a new session
        logger.info("Creating new SSH session for config: {}", sshConfig.name)
        val session = createSession(sshConfig)
        
        // Check if the session is healthy
        if (!isSessionHealthy(session)) {
            try {
                session.close()
            } catch (e: IOException) {
                logger.warn("Error closing unhealthy SSH session: {}", e.message)
                e.printStackTrace()
            }
            throw IOException("Failed to create a healthy SSH session for config: ${sshConfig.name}")
        }
        
        return session
    }
    
    /**
     * Creates a new SSH session based on the given SSH configuration.
     * 
     * @param sshConfig The SSH configuration to use for creating the session
     * @return A new SSH client session
     * @throws IOException If the session cannot be created
     */
    @Throws(IOException::class)
    private fun createSession(sshConfig: SSHConfig): ClientSession {
        try {
            // Create a new session
            val username = if (sshConfig.username.isBlank()) "" else sshConfig.username
            val future = sshClient.connect(username, sshConfig.serverHost, sshConfig.port)
            future.await(CONNECTION_TIMEOUT, TimeUnit.SECONDS)
            if (!future.isConnected) {
                throw IOException("Failed to connect to ${sshConfig.serverHost}:${sshConfig.port}")
            }
            
            val session = future.session
            
            // Authenticate with password
            session.addPasswordIdentity(sshConfig.password)
            val authFuture = session.auth()
            authFuture.await(AUTH_TIMEOUT, TimeUnit.SECONDS)
            if (!authFuture.isSuccess) {
                throw IOException("Authentication failed for ${sshConfig.username}@${sshConfig.serverHost}:${sshConfig.port}")
            }
            
            return session
        } catch (e: Exception) {
            logger.error("Error creating SSH session: {}", e.message)
            throw IOException("Failed to create SSH session: ${e.message}", e)
        }
    }
    
    /**
     * Checks if the given SSH session is healthy.
     * 
     * A healthy session is one that is open, authenticated, and can execute a simple command.
     * 
     * @param session The SSH session to check
     * @return true if the session is healthy, false otherwise
     */
    private fun isSessionHealthy(session: ClientSession): Boolean {
        if (!session.isOpen || !session.isAuthenticated) {
            return false
        }
        
        try {
            // Try to execute a simple command to check if the session is working
            val command = session.createExecChannel("echo hello")
            val openFuture = command.open()
            if(!openFuture.await(COMMAND_TIMEOUT, TimeUnit.SECONDS))
                throw IOException("Command execution failed.")

            
            if (!openFuture.isOpened) {
                logger.warn("Failed to open command channel for health check")
                return false
            }
            
            // Read the command output
            val outputStream = command.getInvertedOut()
            val buffer = ByteArray(1024)
            val bytesRead = outputStream.read(buffer)
            val output = if (bytesRead > 0) String(buffer, 0, bytesRead) else ""
            
            // Wait for command to complete
            val exitStatus = command.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), COMMAND_TIMEOUT * 1000)
            command.close(false)
            
            return output.trim() == "hello" && exitStatus.contains(ClientChannelEvent.CLOSED)
        } catch (e: Exception) {
            logger.warn("Health check failed for SSH session: {}", e.message)
            e.printStackTrace()
            return false
        }
    }
    
    /**
     * Closes the SSH client.
     */
    fun close() {
        // Stop the SSH client
        try {
            sshClient.stop()
        } catch (e: IOException) {
            logger.warn("Error stopping SSH client: {}", e.message)
        }
    }
    
    companion object {
        // Timeout values in seconds
        private const val CONNECTION_TIMEOUT = 10L
        private const val AUTH_TIMEOUT = 10L
        private const val COMMAND_TIMEOUT = 5L
    }
}