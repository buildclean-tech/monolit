package com.cleanbuild.tech.monolit.service

import com.cleanbuild.tech.monolit.DbRecord.SSHConfig
import com.cleanbuild.tech.monolit.DbRecord.SSHLogWatcher
import com.cleanbuild.tech.monolit.DbRecord.SSHLogWatcherRecord
import com.cleanbuild.tech.monolit.com.cleanbuild.tech.monolit.repository.CRUDOperation
import com.cleanbuild.tech.monolit.config.LocalSSHServer
import com.cleanbuild.tech.monolit.ssh.SSHCommandRunner
import com.cleanbuild.tech.monolit.ssh.SSHSessionFactory
import org.apache.sshd.server.SshServer
import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.Timestamp
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SSHLogWatcherServiceTest {
    private val logger = LoggerFactory.getLogger(SSHLogWatcherServiceTest::class.java)
    
    // Use a non-privileged port for testing
    private val TEST_SSH_PORT = 2224
    
    // Database components
    private lateinit var dataSource: JdbcDataSource
    private lateinit var connection: Connection
    private lateinit var sshConfigCrud: CRUDOperation<SSHConfig>
    private lateinit var sshLogWatcherCrud: CRUDOperation<SSHLogWatcher>
    private lateinit var sshLogWatcherRecordCrud: CRUDOperation<SSHLogWatcherRecord>
    
    // SSH components
    private lateinit var sshServer: SshServer
    private lateinit var sshSessionFactory: SSHSessionFactory
    private lateinit var sshCommandRunner: SSHCommandRunner
    private lateinit var testConfig: SSHConfig
    
    // Test directory structure
    private lateinit var tempDir: Path
    private lateinit var logsDir: Path
    private lateinit var subDir1: Path
    
    // Test files
    private lateinit var logFile1: Path
    private lateinit var logFile2: Path
    private lateinit var logFileInSubDir: Path
    
    // Service under test
    private lateinit var sshLogWatcherService: SSHLogWatcherService
    
    @BeforeAll
    fun setupAll(@TempDir tempDirParam: Path) {
        // Initialize tempDir
        tempDir = tempDirParam
        
        // Set up SSH server
        setupSshServer()
        
        // Set up database
        setupDatabase()
        
        // Create test directory structure
        setupTestDirectories()
        
        // Create test files
        createTestFiles()
        
        // Initialize the service under test
        sshLogWatcherService = SSHLogWatcherService(dataSource, sshCommandRunner)
    }
    
    private fun setupTestDirectories() {
        // Create test directory structure
        logsDir = tempDir.resolve("logs")
        subDir1 = logsDir.resolve("subdir1")
        
        Files.createDirectories(logsDir)
        Files.createDirectories(subDir1)
        
        logger.info("Created test directories: {}, {}", logsDir, subDir1)
    }
    
    private fun createTestFiles() {
        // Create test files
        logFile1 = logsDir.resolve("app-log1.txt")
        logFile2 = logsDir.resolve("app-log2.txt")
        logFileInSubDir = subDir1.resolve("app-log3.txt")
        
        // Write content to files
        Files.write(logFile1, "This is test log file 1".toByteArray())
        Files.write(logFile2, "This is test log file 2".toByteArray())
        Files.write(logFileInSubDir, "This is test log file 3 in a subdirectory".toByteArray())
        
        logger.info("Created test files: {}, {}, {}", logFile1, logFile2, logFileInSubDir)
    }
    
    /**
     * Helper method to convert Windows path to Unix path for SSH commands
     */
    private fun toUnixPath(path: Path): String {
        // Convert Windows path (C:\path\to\file) to Unix path (/path/to/file)
        return path.toString().replace("\\", "/")
    }
    
    private fun setupSshServer() {
        // Create and start a local SSH server for testing
        val localSSHServer = LocalSSHServer(
            port = TEST_SSH_PORT,
            password = "testpass"
        )
        
        // Start the server
        sshServer = localSSHServer.startServer()
        logger.info("Test SSH server started on localhost:{}", TEST_SSH_PORT)
        
        // Create the SSH session factory
        sshSessionFactory = SSHSessionFactory()
        
        // Create the command runner
        sshCommandRunner = SSHCommandRunner(sshSessionFactory)
    }
    
    private fun setupDatabase() {
        // Set up in-memory H2 database
        dataSource = JdbcDataSource()
        dataSource.setURL("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1")
        dataSource.setUser("sa")
        dataSource.setPassword("")
        
        // Create connection
        connection = dataSource.connection
        
        // Execute schema.sql to create tables with foreign key constraints
        val schemaResource = javaClass.classLoader.getResourceAsStream("schema.sql")
        requireNotNull(schemaResource) { "Could not find schema.sql in classpath" }
        
        val schemaScript = schemaResource.bufferedReader().use { it.readText() }
        connection.createStatement().use { statement ->
            statement.execute(schemaScript)
        }
        
        // Initialize CRUD operations
        sshConfigCrud = CRUDOperation(dataSource, SSHConfig::class)
        sshLogWatcherCrud = CRUDOperation(dataSource, SSHLogWatcher::class)
        sshLogWatcherRecordCrud = CRUDOperation(dataSource, SSHLogWatcherRecord::class)
    }
    
    @BeforeEach
    fun setUp() {
        // Clear any existing data
        clearDatabase()
        
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
        
        // Insert the test config into the database
        sshConfigCrud.insert(listOf(testConfig))
    }
    
    @AfterEach
    fun tearDown() {
        // Clear database after each test
        clearDatabase()
    }
    
    @AfterAll
    fun tearDownAll() {
        // Stop the SSH server
        sshServer.stop(true)
        logger.info("Test SSH server stopped")
        
        // Close the SSH session factory
        sshSessionFactory.close()
        
        // Close database connection
        if (!connection.isClosed) {
            connection.close()
        }
    }
    
    private fun clearDatabase() {
        try {
            // Delete all records from tables in reverse order of dependencies
            connection.createStatement().use { statement ->
                statement.execute("DELETE FROM SSHLogWatcherRecord")
                statement.execute("DELETE FROM SSHLogWatcher")
                statement.execute("DELETE FROM sshConfig")
            }
        } catch (e: Exception) {
            logger.error("Error clearing database: ${e.message}", e)
        }
    }

    @Test
    fun `test buildFilePattern with all components`() {
        // This is a simple test that doesn't require mocking the database
        // We can test the pattern building logic directly
        
        // Create a test watcher with all pattern components
        val watcher = SSHLogWatcher(
            name = "test-watcher",
            sshConfigName = "test-config",
            watchDir = "/logs",
            recurDepth = 1,
            filePrefix = "app-",
            fileContains = "log",
            filePostfix = ".txt",
            archivedLogs = true,
            enabled = true,
            createdAt = Timestamp(System.currentTimeMillis()),
            updatedAt = Timestamp(System.currentTimeMillis())
        )
        
        // Use reflection to access the private method
        val buildFilePatternMethod = SSHLogWatcherService::class.java.getDeclaredMethod(
            "buildFilePattern", SSHLogWatcher::class.java
        )
        buildFilePatternMethod.isAccessible = true
        
        // Call the method
        val pattern = buildFilePatternMethod.invoke(sshLogWatcherService, watcher) as String
        
        // Verify the pattern
        assertEquals("app-*log*.txt", pattern)
    }
    
    @Test
    fun `test buildFilePattern with only prefix`() {
        // Create a test watcher with only prefix
        val watcher = SSHLogWatcher(
            name = "test-watcher",
            sshConfigName = "test-config",
            watchDir = "/logs",
            recurDepth = 1,
            filePrefix = "app-",
            fileContains = "",
            filePostfix = "",
            archivedLogs = true,
            enabled = true,
            createdAt = Timestamp(System.currentTimeMillis()),
            updatedAt = Timestamp(System.currentTimeMillis())
        )
        
        // Use reflection to access the private method
        val buildFilePatternMethod = SSHLogWatcherService::class.java.getDeclaredMethod(
            "buildFilePattern", SSHLogWatcher::class.java
        )
        buildFilePatternMethod.isAccessible = true
        
        // Call the method
        val pattern = buildFilePatternMethod.invoke(sshLogWatcherService, watcher) as String
        
        // Verify the pattern
        assertEquals("app-*", pattern)
    }
    
    @Test
    fun `test buildFilePattern with only contains`() {
        // Create a test watcher with only contains
        val watcher = SSHLogWatcher(
            name = "test-watcher",
            sshConfigName = "test-config",
            watchDir = "/logs",
            recurDepth = 1,
            filePrefix = "",
            fileContains = "log",
            filePostfix = "",
            archivedLogs = true,
            enabled = true,
            createdAt = Timestamp(System.currentTimeMillis()),
            updatedAt = Timestamp(System.currentTimeMillis())
        )
        
        // Use reflection to access the private method
        val buildFilePatternMethod = SSHLogWatcherService::class.java.getDeclaredMethod(
            "buildFilePattern", SSHLogWatcher::class.java
        )
        buildFilePatternMethod.isAccessible = true
        
        // Call the method
        val pattern = buildFilePatternMethod.invoke(sshLogWatcherService, watcher) as String
        
        // Verify the pattern
        assertEquals("*log*", pattern)
    }
    
    @Test
    fun `test buildFilePattern with only postfix`() {
        // Create a test watcher with only postfix
        val watcher = SSHLogWatcher(
            name = "test-watcher",
            sshConfigName = "test-config",
            watchDir = "/logs",
            recurDepth = 1,
            filePrefix = "",
            fileContains = "",
            filePostfix = ".txt",
            archivedLogs = true,
            enabled = true,
            createdAt = Timestamp(System.currentTimeMillis()),
            updatedAt = Timestamp(System.currentTimeMillis())
        )
        
        // Use reflection to access the private method
        val buildFilePatternMethod = SSHLogWatcherService::class.java.getDeclaredMethod(
            "buildFilePattern", SSHLogWatcher::class.java
        )
        buildFilePatternMethod.isAccessible = true
        
        // Call the method
        val pattern = buildFilePatternMethod.invoke(sshLogWatcherService, watcher) as String
        
        // Verify the pattern
        assertEquals("*.txt", pattern)
    }
    
    @Test
    fun `test calculateFileHash produces consistent results`() {
        // Use reflection to access the private method
        val calculateFileHashMethod = SSHLogWatcherService::class.java.getDeclaredMethod(
            "calculateFileHash", String::class.java, Long::class.java, Long::class.java
        )
        calculateFileHashMethod.isAccessible = true
        
        // Call the method with the same inputs multiple times
        val hash1 = calculateFileHashMethod.invoke(
            sshLogWatcherService, "test.txt", 1024L, 1625097600000L
        ) as String
        
        val hash2 = calculateFileHashMethod.invoke(
            sshLogWatcherService, "test.txt", 1024L, 1625097600000L
        ) as String
        
        // Verify the hashes are the same
        assertEquals(hash1, hash2, "Hash should be consistent for the same inputs")
        
        // Call the method with different inputs
        val hash3 = calculateFileHashMethod.invoke(
            sshLogWatcherService, "different.txt", 1024L, 1625097600000L
        ) as String
        
        // Verify the hash is different
        assert(hash1 != hash3) { "Hash should be different for different inputs" }
    }
    
    @Test
    fun `test processLogWatchers creates records for new files`() {
        // Create a test watcher that points to our test directory
        val watcher = SSHLogWatcher(
            name = "test-watcher",
            sshConfigName = "test-config",
            watchDir = toUnixPath(logsDir),
            recurDepth = 1,
            filePrefix = "app-",
            fileContains = "log",
            filePostfix = ".txt",
            archivedLogs = true,
            enabled = true,
            createdAt = Timestamp(System.currentTimeMillis()),
            updatedAt = Timestamp(System.currentTimeMillis())
        )
        
        // Insert the watcher into the database
        sshLogWatcherCrud.insert(listOf(watcher))
        
        // Process log watchers
        sshLogWatcherService.processLogWatchers()
        
        // Verify records were created
        val records = sshLogWatcherRecordCrud.findAll()
        
        // Should find 2 files in the logs directory (not including subdirectory)
        assertEquals(2, records.size, "Should create 2 records for files in the logs directory")
        
        // Verify record details
        records.forEach { record ->
            assertEquals("test-watcher", record.sshLogWatcherName, "Record should reference the correct watcher")
            assertTrue(record.fullFilePath.contains("app-log"), "Record should reference a log file")
            assertTrue(record.fileSize > 0, "File size should be greater than 0")
            assertNotNull(record.cTime, "Creation time should not be null")
            assertEquals("NEW", record.consumptionStatus, "Status should be NEW for new files")
            assertEquals(null, record.duplicatedFile, "Duplicated file should be null for new files")
        }
    }
    
    @Test
    fun `test processLogWatchers with recurDepth finds files in subdirectories`() {
        // Create a test watcher with recurDepth=2 to find files in subdirectories
        val watcher = SSHLogWatcher(
            name = "test-watcher-recursive",
            sshConfigName = "test-config",
            watchDir = toUnixPath(logsDir),
            recurDepth = 2, // Include subdirectories
            filePrefix = "app-",
            fileContains = "log",
            filePostfix = ".txt",
            archivedLogs = true,
            enabled = true,
            createdAt = Timestamp(System.currentTimeMillis()),
            updatedAt = Timestamp(System.currentTimeMillis())
        )
        
        // Insert the watcher into the database
        sshLogWatcherCrud.insert(listOf(watcher))
        
        // Process log watchers
        sshLogWatcherService.processLogWatchers()
        
        // Verify records were created
        val records = sshLogWatcherRecordCrud.findAll()
        
        // Should find 3 files (2 in logs directory + 1 in subdirectory)
        assertEquals(3, records.size, "Should create 3 records including file in subdirectory")
        
        // Verify that one of the records is for the file in the subdirectory
        val subDirRecord = records.find { it.fullFilePath.contains("subdir1") }
        assertNotNull(subDirRecord, "Should find a record for the file in the subdirectory")
    }
    
    @Test
    fun `test processLogWatchers ignores disabled watchers`() {
        // Create a disabled test watcher
        val watcher = SSHLogWatcher(
            name = "test-watcher-disabled",
            sshConfigName = "test-config",
            watchDir = toUnixPath(logsDir),
            recurDepth = 1,
            filePrefix = "app-",
            fileContains = "log",
            filePostfix = ".txt",
            archivedLogs = true,
            enabled = false, // Disabled
            createdAt = Timestamp(System.currentTimeMillis()),
            updatedAt = Timestamp(System.currentTimeMillis())
        )
        
        // Insert the watcher into the database
        sshLogWatcherCrud.insert(listOf(watcher))
        
        // Process log watchers
        sshLogWatcherService.processLogWatchers()
        
        // Verify no records were created
        val records = sshLogWatcherRecordCrud.findAll()
        assertEquals(0, records.size, "Should not create any records for disabled watchers")
    }
    
    @Test
    fun `test processLogWatchers creates duplicate records`() {
        // Create a test watcher
        val watcher = SSHLogWatcher(
            name = "test-watcher-duplicates",
            sshConfigName = "test-config",
            watchDir = toUnixPath(logsDir),
            recurDepth = 1,
            filePrefix = "app-",
            fileContains = "log",
            filePostfix = ".txt",
            archivedLogs = true,
            enabled = true,
            createdAt = Timestamp(System.currentTimeMillis()),
            updatedAt = Timestamp(System.currentTimeMillis())
        )
        
        // Insert the watcher into the database
        sshLogWatcherCrud.insert(listOf(watcher))
        
        // Process log watchers first time
        sshLogWatcherService.processLogWatchers()
        
        // Verify records were created
        val firstRunRecords = sshLogWatcherRecordCrud.findAll()
        val firstRunCount = firstRunRecords.size
        assertTrue(firstRunCount > 0, "Should create records on first run")
        
        // Process log watchers second time
        sshLogWatcherService.processLogWatchers()
        
        // Verify duplicate records were created
        val secondRunRecords = sshLogWatcherRecordCrud.findAll()
        assertTrue(secondRunRecords.size > firstRunCount, "Should create duplicate records on second run")
    }
}