package com.cleanbuild.tech.monolit.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.h2.tools.Server
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import java.sql.SQLException
import javax.sql.DataSource

/**
 * Configuration class for database.
 * This configuration sets up the database connection pool and H2 database servers.
 */
@Configuration
open class DatabaseConfig {

    @Value("\${spring.datasource.url}")
    private lateinit var jdbcUrl: String

    @Value("\${spring.datasource.username}")
    private lateinit var username: String

    @Value("\${spring.datasource.password}")
    private lateinit var password: String

    @Value("\${spring.datasource.driverClassName}")
    private lateinit var driverClassName: String

    @Value("\${spring.datasource.hikari.maximum-pool-size:10}")
    private var maximumPoolSize: Int = 10

    @Value("\${spring.datasource.hikari.minimum-idle:5}")
    private var minimumIdle: Int = 5

    @Value("\${spring.datasource.hikari.idle-timeout:30000}")
    private var idleTimeout: Long = 30000

    @Value("\${spring.datasource.hikari.pool-name:MonolitHikariCP}")
    private lateinit var poolName: String

    /**
     * Creates and configures a HikariCP data source.
     *
     * @return the configured DataSource
     */
    @Bean
    @Primary
    open fun dataSource(): DataSource {
        val config = HikariConfig()
        config.jdbcUrl = jdbcUrl
        config.username = username
        config.password = password
        config.driverClassName = driverClassName
        config.maximumPoolSize = maximumPoolSize
        config.minimumIdle = minimumIdle
        config.idleTimeout = idleTimeout
        config.poolName = poolName
        config.connectionTestQuery = "SELECT 1"
        config.isAutoCommit = true
        
        // Additional HikariCP settings
        config.addDataSourceProperty("cachePrepStmts", "true")
        config.addDataSourceProperty("prepStmtCacheSize", "250")
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
        config.addDataSourceProperty("useServerPrepStmts", "true")
        
        return HikariDataSource(config)
    }

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