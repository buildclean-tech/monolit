package com.cleanbuild.tech.monolit.config

import com.cleanbuild.tech.monolit.service.LuceneIngestionService
import com.cleanbuild.tech.monolit.service.SSHLogWatcherService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.Mockito
import org.mockito.kotlin.verify
import org.slf4j.LoggerFactory

/**
 * Tests for the Lucene ingestion scheduling functionality
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LuceneIngestionSchedulerTest {
    private val logger = LoggerFactory.getLogger(LuceneIngestionSchedulerTest::class.java)
    
    // Services
    private lateinit var sshLogWatcherService: SSHLogWatcherService
    private lateinit var luceneIngestionService: LuceneIngestionService
    
    // Config under test
    private lateinit var schedulerConfig: SchedulerConfig
    
    @BeforeEach
    fun setUp() {
        // Create mock services
        sshLogWatcherService = Mockito.mock(SSHLogWatcherService::class.java)
        luceneIngestionService = Mockito.mock(LuceneIngestionService::class.java)
        
        // Create the scheduler config with the mock services
        schedulerConfig = SchedulerConfig(sshLogWatcherService, luceneIngestionService)
    }
    
    @Test
    fun `test runLuceneIngestionProcessing calls ingestRecords`() {
        // Execute the scheduled method
        schedulerConfig.runLuceneIngestionProcessing()
        
        // Verify that ingestRecords was called
        verify(luceneIngestionService).ingestRecords()
    }
    
    @Test
    fun `test runLuceneIngestionProcessing handles exceptions`() {
        // Configure the mock to throw an exception
        Mockito.`when`(luceneIngestionService.ingestRecords()).thenThrow(RuntimeException("Test exception"))
        
        // Execute the scheduled method - should not throw an exception
        schedulerConfig.runLuceneIngestionProcessing()
        
        // Verify that ingestRecords was called
        verify(luceneIngestionService).ingestRecords()
    }
    
    @Test
    fun `test concurrent execution prevention`() {
        // Create a mock that simulates a long-running operation
        val slowLuceneService = Mockito.mock(LuceneIngestionService::class.java)
        Mockito.`when`(slowLuceneService.ingestRecords()).thenAnswer { 
            // Sleep to simulate long-running operation
            Thread.sleep(100)
            null
        }
        
        // Create a scheduler with the slow service
        val schedulerWithSlowService = SchedulerConfig(sshLogWatcherService, slowLuceneService)
        
        // Start a thread that runs the scheduled method
        val thread1 = Thread {
            schedulerWithSlowService.runLuceneIngestionProcessing()
        }
        thread1.start()
        
        // Give the first thread a chance to start
        Thread.sleep(10)
        
        // Try to run the scheduled method again from the main thread
        schedulerWithSlowService.runLuceneIngestionProcessing()
        
        // Wait for the first thread to complete
        thread1.join()
        
        // Verify that ingestRecords was called exactly once
        // This verifies that the second call was skipped due to the concurrent execution prevention
        verify(slowLuceneService, Mockito.times(1)).ingestRecords()
    }
}