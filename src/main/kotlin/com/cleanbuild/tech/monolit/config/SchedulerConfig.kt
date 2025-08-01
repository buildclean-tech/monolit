package com.cleanbuild.tech.monolit.config

import com.cleanbuild.tech.monolit.service.LuceneIngestionService
import com.cleanbuild.tech.monolit.service.SSHLogWatcherService
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Configuration class for scheduling tasks
 */
@Configuration
@EnableScheduling
open class SchedulerConfig(
    private val sshLogWatcherService: SSHLogWatcherService,
    private val luceneIngestionService: LuceneIngestionService
) {
    private val logger = LoggerFactory.getLogger(SchedulerConfig::class.java)
    
    // Flags to prevent concurrent execution
    private val sshLogWatcherRunning = AtomicBoolean(false)
    private val luceneIngestionRunning = AtomicBoolean(false)
    
    /**
     * Schedule the SSH log watcher processing to run every 15 minutes
     * fixedRate = 15 minutes in milliseconds
     */
    @Scheduled(fixedRate = 15*60*1000)
    open fun runSSHLogWatcherProcessing() {
        // Skip if already running to prevent concurrent execution
        if (!sshLogWatcherRunning.compareAndSet(false, true)) {
            logger.info("Skipping scheduled SSH log watcher processing as previous execution is still running")
            return
        }
        
        logger.info("Scheduled SSH log watcher processing started")
        try {
            sshLogWatcherService.processLogWatchers()
        } catch (e: Exception) {
            logger.error("Error during scheduled SSH log watcher processing: ${e.message}", e)
        } finally {
            // Reset the flag when done
            sshLogWatcherRunning.set(false)
            logger.info("Scheduled SSH log watcher processing completed")
        }
    }
    
    /**
     * Schedule the Lucene ingestion processing to run every 15 minutes
     * fixedRate = 15 minutes in milliseconds
     */
    @Scheduled(fixedRate = 15*60*1000)
    open fun runLuceneIngestionProcessing() {
        // Skip if already running to prevent concurrent execution
        if (!luceneIngestionRunning.compareAndSet(false, true)) {
            logger.info("Skipping scheduled Lucene ingestion processing as previous execution is still running")
            return
        }
        
        logger.info("Scheduled Lucene ingestion processing started")
        try {
            luceneIngestionService.ingestRecords()
        } catch (e: Exception) {
            logger.error("Error during scheduled Lucene ingestion processing: ${e.message}", e)
        } finally {
            // Reset the flag when done
            luceneIngestionRunning.set(false)
            logger.info("Scheduled Lucene ingestion processing completed")
        }
    }
}