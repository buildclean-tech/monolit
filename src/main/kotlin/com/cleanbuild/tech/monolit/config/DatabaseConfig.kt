package com.cleanbuild.tech.monolit.config

import org.h2.tools.Server
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.sql.SQLException

/**
 * Configuration class for H2 database.
 * This configuration sets up H2 database to use file-based storage.
 */
@Configuration
open class DatabaseConfig {

    /**
     * Creates and starts an H2 TCP server to allow external connections to the database.
     * This is useful for connecting to the database using external tools.
     * Only active in dev profile.
     *
     * @return the H2 TCP server instance
     * @throws SQLException if there is an error starting the server
     */
    @Bean(initMethod = "start", destroyMethod = "stop")
    @Profile("dev")
    @Throws(SQLException::class)
    open fun h2TcpServer(): Server {
        return Server.createTcpServer("-tcp", "-tcpAllowOthers", "-tcpPort", "9092")
    }

    /**
     * Creates and starts an H2 web console server.
     * This provides a web interface to interact with the database.
     * Only active in dev profile.
     *
     * @return the H2 web server instance
     * @throws SQLException if there is an error starting the server
     */
    @Bean(initMethod = "start", destroyMethod = "stop")
    @Profile("dev")
    @Throws(SQLException::class)
    open fun h2WebServer(): Server {
        return Server.createWebServer("-web", "-webAllowOthers", "-webPort", "8082")
    }
}