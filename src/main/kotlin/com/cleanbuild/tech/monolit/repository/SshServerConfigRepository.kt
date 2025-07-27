package com.cleanbuild.tech.monolit.repository

import com.cleanbuild.tech.monolit.model.SshServerConfig
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

/**
 * Repository interface for SSH server configuration.
 * Provides methods to perform CRUD operations on SshServerConfig entities.
 */
@Repository
interface SshServerConfigRepository : JpaRepository<SshServerConfig, Long> {
    
    /**
     * Find SSH server configuration by server host.
     *
     * @param serverHost the server host to search for
     * @return an Optional containing the found configuration or empty if not found
     */
    fun findByServerHost(serverHost: String): Optional<SshServerConfig>
    
    /**
     * Find SSH server configuration by server host and port.
     *
     * @param serverHost the server host to search for
     * @param port the port to search for
     * @return an Optional containing the found configuration or empty if not found
     */
    fun findByServerHostAndPort(serverHost: String, port: Int): Optional<SshServerConfig>
    
    /**
     * Check if a configuration exists for the given server host.
     *
     * @param serverHost the server host to check
     * @return true if a configuration exists, false otherwise
     */
    fun existsByServerHost(serverHost: String): Boolean
}