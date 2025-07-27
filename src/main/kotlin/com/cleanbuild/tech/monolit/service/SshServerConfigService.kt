package com.cleanbuild.tech.monolit.service

import com.cleanbuild.tech.monolit.model.SshServerConfig
import com.cleanbuild.tech.monolit.repository.SshServerConfigRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Optional

/**
 * Service class for managing SSH server configurations.
 * Provides business logic and transaction management for SSH server configuration operations.
 */
@Service
open class SshServerConfigService constructor(
    private val sshServerConfigRepository: SshServerConfigRepository
) {
    /**
     * Find all SSH server configurations.
     *
     * @return a list of all SSH server configurations
     */
    fun findAll(): List<SshServerConfig> {
        return sshServerConfigRepository.findAll()
    }
    
    /**
     * Find SSH server configuration by ID.
     *
     * @param id the ID of the configuration to find
     * @return an Optional containing the found configuration or empty if not found
     */
    fun findById(id: Long): Optional<SshServerConfig> {
        return sshServerConfigRepository.findById(id)
    }
    
    /**
     * Find SSH server configuration by server host.
     *
     * @param serverHost the server host to search for
     * @return an Optional containing the found configuration or empty if not found
     */
    fun findByServerHost(serverHost: String): Optional<SshServerConfig> {
        return sshServerConfigRepository.findByServerHost(serverHost)
    }
    
    /**
     * Find SSH server configuration by server host and port.
     *
     * @param serverHost the server host to search for
     * @param port the port to search for
     * @return an Optional containing the found configuration or empty if not found
     */
    fun findByServerHostAndPort(serverHost: String, port: Int): Optional<SshServerConfig> {
        return sshServerConfigRepository.findByServerHostAndPort(serverHost, port)
    }
    
    /**
     * Save a new SSH server configuration or update an existing one.
     *
     * @param config the configuration to save or update
     * @return the saved or updated configuration
     */
    @Transactional
    open fun save(config: SshServerConfig): SshServerConfig {
        // Validate configuration
        validateConfig(config)
        return sshServerConfigRepository.save(config)
    }
    
    /**
     * Delete a SSH server configuration by ID.
     *
     * @param id the ID of the configuration to delete
     */
    @Transactional
    open fun deleteById(id: Long) {
        sshServerConfigRepository.deleteById(id)
    }
    
    /**
     * Check if a configuration exists for the given server host.
     *
     * @param serverHost the server host to check
     * @return true if a configuration exists, false otherwise
     */
    fun existsByServerHost(serverHost: String): Boolean {
        return sshServerConfigRepository.existsByServerHost(serverHost)
    }
    
    /**
     * Validate SSH server configuration.
     *
     * @param config the configuration to validate
     * @throws IllegalArgumentException if the configuration is invalid
     */
    private fun validateConfig(config: SshServerConfig) {
        if (config.serverHost.isBlank()) {
            throw IllegalArgumentException("Server host cannot be empty")
        }
        
        if (config.port <= 0 || config.port > 65535) {
            throw IllegalArgumentException("Port must be between 1 and 65535")
        }
        
        if (config.password.isBlank()) {
            throw IllegalArgumentException("Password cannot be empty")
        }
    }
}