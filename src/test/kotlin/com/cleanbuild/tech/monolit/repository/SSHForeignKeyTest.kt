package com.cleanbuild.tech.monolit.repository

import com.cleanbuild.tech.monolit.DbRecord.SSHConfig
import com.cleanbuild.tech.monolit.DbRecord.SSHLogWatcher
import com.cleanbuild.tech.monolit.com.cleanbuild.tech.monolit.repository.CRUDOperation
import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.sql.Connection
import java.sql.SQLException
import java.sql.Timestamp
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test class for verifying the foreign key constraint between SSHConfig and SSHLogWatcher
 */
class SSHForeignKeyTest {
    private lateinit var dataSource: JdbcDataSource
    private lateinit var connection: Connection
    private lateinit var sshConfigCrud: CRUDOperation<SSHConfig>
    private lateinit var sshLogWatcherCrud: CRUDOperation<SSHLogWatcher>

    @BeforeEach
    fun setUp() {
        // Set up in-memory H2 database
        dataSource = JdbcDataSource()
        dataSource.setURL("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1")
        dataSource.setUser("sa")
        dataSource.setPassword("")

        // Create connection
        connection = dataSource.connection
        
        // Create tables with foreign key constraint
        val createSshConfigTable = """
            CREATE TABLE IF NOT EXISTS sshConfig (
                name VARCHAR(255) PRIMARY KEY,
                serverHost VARCHAR(255) NOT NULL,
                port INT NOT NULL,
                password VARCHAR(255) NOT NULL,
                createdAt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updatedAt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
        """.trimIndent()
        
        val createSshLogWatcherTable = """
            CREATE TABLE IF NOT EXISTS SSHLogWatcher (
                name VARCHAR(255) PRIMARY KEY,
                sshConfigName VARCHAR(255) NOT NULL,
                watchDir VARCHAR(255) NOT NULL,
                recurDepth INT NOT NULL,
                filePrefix VARCHAR(255) NOT NULL,
                fileContains VARCHAR(255) NOT NULL,
                filePostfix VARCHAR(255) NOT NULL,
                activeFiles VARCHAR(50) NOT NULL,
                enabled BOOLEAN NOT NULL DEFAULT TRUE,
                createdAt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updatedAt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (sshConfigName) REFERENCES sshConfig(name)
            )
        """.trimIndent()
        
        connection.createStatement().use { statement ->
            statement.execute(createSshConfigTable)
            statement.execute(createSshLogWatcherTable)
        }
        
        // Initialize CRUD operations
        sshConfigCrud = CRUDOperation(dataSource, SSHConfig::class)
        sshLogWatcherCrud = CRUDOperation(dataSource, SSHLogWatcher::class)
    }

    @AfterEach
    fun tearDown() {
        // Drop test tables
        connection.createStatement().use { statement ->
            statement.execute("DROP TABLE IF EXISTS SSHLogWatcher")
            statement.execute("DROP TABLE IF EXISTS sshConfig")
        }
        
        // Close connection
        if (!connection.isClosed) {
            connection.close()
        }
    }

    @Test
    fun testValidForeignKeyRelationship() {
        // Create an SSHConfig
        val sshConfig = SSHConfig(
            name = "test-config",
            serverHost = "localhost",
            port = 22,
            password = "password",
            createdAt = Timestamp(System.currentTimeMillis()),
            updatedAt = Timestamp(System.currentTimeMillis())
        )
        
        sshConfigCrud.insert(listOf(sshConfig))
        
        // Create an SSHLogWatcher that references the SSHConfig
        val sshLogWatcher = SSHLogWatcher(
            name = "test-watcher",
            sshConfigName = "test-config", // Valid reference
            watchDir = "/var/log",
            recurDepth = 1,
            filePrefix = "app",
            fileContains = "log",
            filePostfix = "txt",
            activeFiles = "5",
            enabled = true,
            createdAt = Timestamp(System.currentTimeMillis()),
            updatedAt = Timestamp(System.currentTimeMillis())
        )
        
        // This should succeed because the foreign key reference is valid
        sshLogWatcherCrud.insert(listOf(sshLogWatcher))
        
        // Verify the SSHLogWatcher was inserted
        val foundWatcher = sshLogWatcherCrud.findByPrimaryKey("test-watcher")
        assertTrue(foundWatcher != null)
        assertEquals("test-config", foundWatcher?.sshConfigName)
    }
    
    @Test
    fun testInvalidForeignKeyReference() {
        // Create an SSHLogWatcher that references a non-existent SSHConfig
        val sshLogWatcher = SSHLogWatcher(
            name = "test-watcher",
            sshConfigName = "non-existent-config", // Invalid reference
            watchDir = "/var/log",
            recurDepth = 1,
            filePrefix = "app",
            fileContains = "log",
            filePostfix = "txt",
            activeFiles = "5",
            enabled = true,
            createdAt = Timestamp(System.currentTimeMillis()),
            updatedAt = Timestamp(System.currentTimeMillis())
        )
        
        // This should fail because the foreign key reference is invalid
        val exception = assertThrows<Exception> {
            sshLogWatcherCrud.insert(listOf(sshLogWatcher))
        }
        
        // Verify the exception is related to a foreign key violation
        assertTrue(exception.message?.contains("foreign key", ignoreCase = true) == true ||
                  exception.cause?.message?.contains("foreign key", ignoreCase = true) == true)
    }
    
    @Test
    fun testDeleteReferencedSSHConfig() {
        // Create an SSHConfig
        val sshConfig = SSHConfig(
            name = "test-config",
            serverHost = "localhost",
            port = 22,
            password = "password",
            createdAt = Timestamp(System.currentTimeMillis()),
            updatedAt = Timestamp(System.currentTimeMillis())
        )
        
        sshConfigCrud.insert(listOf(sshConfig))
        
        // Create an SSHLogWatcher that references the SSHConfig
        val sshLogWatcher = SSHLogWatcher(
            name = "test-watcher",
            sshConfigName = "test-config",
            watchDir = "/var/log",
            recurDepth = 1,
            filePrefix = "app",
            fileContains = "log",
            filePostfix = "txt",
            activeFiles = "5",
            enabled = true,
            createdAt = Timestamp(System.currentTimeMillis()),
            updatedAt = Timestamp(System.currentTimeMillis())
        )
        
        sshLogWatcherCrud.insert(listOf(sshLogWatcher))
        
        // Try to delete the SSHConfig that is referenced by the SSHLogWatcher
        // This should fail because of the foreign key constraint
        val exception = assertThrows<Exception> {
            sshConfigCrud.delete(listOf(sshConfig))
        }
        
        // Verify the exception is related to a foreign key violation
        assertTrue(exception.message?.contains("foreign key", ignoreCase = true) == true ||
                  exception.cause?.message?.contains("foreign key", ignoreCase = true) == true)
    }
}