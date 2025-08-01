package com.cleanbuild.tech.monolit.config

import com.cleanbuild.tech.monolit.DbRecord.SSHConfig
import com.cleanbuild.tech.monolit.DbRecord.SSHLogWatcher
import com.cleanbuild.tech.monolit.com.cleanbuild.tech.monolit.repository.CRUDOperation
import com.cleanbuild.tech.monolit.service.LuceneIngestionService
import com.cleanbuild.tech.monolit.service.SSHLogWatcherService
import com.cleanbuild.tech.monolit.ssh.SSHCommandRunner
import com.cleanbuild.tech.monolit.ssh.SSHSessionFactory
import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.Mockito
import org.mockito.kotlin.verify
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.Timestamp
import javax.sql.DataSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SchedulerConfigRealTest {
    private val logger = LoggerFactory.getLogger(SchedulerConfigRealTest::class.java)
    
    // Database components
    private lateinit var dataSource: JdbcDataSource
    private lateinit var connection: Connection
    
    // SSH components
    private lateinit var sshSessionFactory: SSHSessionFactory
    private lateinit var sshCommandRunner: SSHCommandRunner
    
    // Service and config under test
    private lateinit var sshLogWatcherService: SSHLogWatcherService
    private lateinit var luceneIngestionService: LuceneIngestionService
    private lateinit var schedulerConfig: SchedulerConfig
    
    // Spy for the service to verify method calls
    private lateinit var sshLogWatcherServiceSpy: SSHLogWatcherService
    private lateinit var luceneIngestionServiceMock: LuceneIngestionService
    
    @BeforeEach
    fun setUp() {
        // Set up database
        setupDatabase()
        
        // Set up SSH components
        sshSessionFactory = SSHSessionFactory()
        sshCommandRunner = SSHCommandRunner(sshSessionFactory)
        
        // Create the services
        sshLogWatcherService = SSHLogWatcherService(dataSource, sshCommandRunner)
        luceneIngestionService = LuceneIngestionService(dataSource, sshCommandRunner)
        
        // Create a spy/mock for the services to verify method calls
        sshLogWatcherServiceSpy = Mockito.spy(sshLogWatcherService)
        luceneIngestionServiceMock = Mockito.mock(LuceneIngestionService::class.java)
        
        // Create the scheduler config with the spy/mock services
        schedulerConfig = SchedulerConfig(sshLogWatcherServiceSpy, luceneIngestionServiceMock)
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
    }
    
    @AfterEach
    fun tearDown() {
        // Close database connection
        if (!connection.isClosed) {
            connection.close()
        }
        
        // Close SSH session factory
        sshSessionFactory.close()
    }
    
    @Test
    fun `test runSSHLogWatcherProcessing calls processLogWatchers`() {
        // Execute the scheduled method
        schedulerConfig.runSSHLogWatcherProcessing()
        
        // Verify that processLogWatchers was called
        verify(sshLogWatcherServiceSpy).processLogWatchers()
    }
    
    @Test
    fun `test runSSHLogWatcherProcessing handles exceptions`() {
        // Create mock services that throw exceptions
        val mockSshService = Mockito.mock(SSHLogWatcherService::class.java)
        val mockLuceneService = Mockito.mock(LuceneIngestionService::class.java)
        Mockito.`when`(mockSshService.processLogWatchers()).thenThrow(RuntimeException("Test exception"))
        
        // Create a scheduler config with the mock services
        val schedulerWithMockService = SchedulerConfig(mockSshService, mockLuceneService)
        
        // Execute the scheduled method - should not throw an exception
        schedulerWithMockService.runSSHLogWatcherProcessing()
        
        // Verify that processLogWatchers was called
        verify(mockSshService).processLogWatchers()
    }
    
    @Test
    fun `test runSSHLogWatcherProcessing with real data`() {
        // Create a test SSH config
        val sshConfig = SSHConfig(
            name = "test-config",
            serverHost = "localhost",
            port = 22,
            username = "testuser",
            password = "testpass",
            createdAt = Timestamp(System.currentTimeMillis()),
            updatedAt = Timestamp(System.currentTimeMillis())
        )
        
        // Create a test SSH log watcher
        val sshLogWatcher = SSHLogWatcher(
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
        
        // Insert the test data into the database
        val sshConfigCrud = CRUDOperation(dataSource, SSHConfig::class)
        val sshLogWatcherCrud = CRUDOperation(dataSource, SSHLogWatcher::class)
        
        sshConfigCrud.insert(listOf(sshConfig))
        sshLogWatcherCrud.insert(listOf(sshLogWatcher))
        
        // Execute the scheduled method
        schedulerConfig.runSSHLogWatcherProcessing()
        
        // Verify that processLogWatchers was called
        verify(sshLogWatcherServiceSpy).processLogWatchers()
    }
}