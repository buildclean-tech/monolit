package com.cleanbuild.tech.monolit.model

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * Entity class representing SSH server configuration.
 * This class stores configuration parameters for SSH server connections.
 */
@Entity
@Table(name = "ssh_server_config")
data class SshServerConfig(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    
    @Column(nullable = false)
    var serverHost: String,
    
    @Column(nullable = false)
    var port: Int,
    
    @Column(nullable = false)
    var password: String,
    
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "updated_at")
    var updatedAt: LocalDateTime? = null
) {
    // No-args constructor required by JPA
    constructor() : this(
        id = null,
        serverHost = "",
        port = 22,
        password = "",
        createdAt = LocalDateTime.now(),
        updatedAt = null
    )
    
    // Update the updatedAt timestamp before persisting changes
    @PreUpdate
    fun onUpdate() {
        updatedAt = LocalDateTime.now()
    }
    
    override fun toString(): String {
        return "SshServerConfig(id=$id, serverHost='$serverHost', port=$port, createdAt=$createdAt, updatedAt=$updatedAt)"
    }
}