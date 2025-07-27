package com.cleanbuild.tech.monolit.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * Data Transfer Object for SSH server configuration.
 * This class is used for transferring SSH server configuration data between
 * the controller and the view, and for form validation.
 */
data class SshServerConfigDto(
    /**
     * The ID of the configuration. Null for new configurations.
     */
    val id: Long? = null,
    
    /**
     * The hostname or IP address of the SSH server.
     * Cannot be blank and must be between 2 and 255 characters.
     */
    @field:NotBlank(message = "Server host cannot be empty")
    @field:Size(min = 2, max = 255, message = "Server host must be between 2 and 255 characters")
    val serverHost: String = "",
    
    /**
     * The port number of the SSH server.
     * Must be between 1 and 65535.
     */
    @field:Min(value = 1, message = "Port must be at least 1")
    @field:Max(value = 65535, message = "Port must be at most 65535")
    val port: Int = 22,
    
    /**
     * The password for the SSH server.
     * Cannot be blank and must be between 4 and 100 characters.
     */
    @field:NotBlank(message = "Password cannot be empty")
    @field:Size(min = 4, max = 100, message = "Password must be between 4 and 100 characters")
    val password: String = ""
) {
    /**
     * Convert this DTO to an entity object.
     * This method is used when creating or updating an entity from form data.
     *
     * @return a new SshServerConfig entity with data from this DTO
     */
    fun toEntity(): com.cleanbuild.tech.monolit.model.SshServerConfig {
        return com.cleanbuild.tech.monolit.model.SshServerConfig(
            id = this.id,
            serverHost = this.serverHost,
            port = this.port,
            password = this.password
        )
    }
    
    companion object {
        /**
         * Create a DTO from an entity object.
         * This method is used when preparing entity data for display in the view.
         *
         * @param entity the entity to convert
         * @return a new DTO with data from the entity
         */
        fun fromEntity(entity: com.cleanbuild.tech.monolit.model.SshServerConfig): SshServerConfigDto {
            return SshServerConfigDto(
                id = entity.id,
                serverHost = entity.serverHost,
                port = entity.port,
                password = entity.password
            )
        }
    }
}