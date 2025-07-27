package com.cleanbuild.tech.monolit.model

import jakarta.persistence.*
import java.time.LocalDateTime


@Table(name = "ssh_configs")
data class SSHConfig(
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
    val createdAt: LocalDateTime,
    
    @Column(name = "updated_at")
    var updatedAt: LocalDateTime? = null
) {
    override fun toString(): String {
        return "SSHConfig(id=$id, serverHost='$serverHost', port=$port, createdAt=$createdAt, updatedAt=$updatedAt)"
    }
}