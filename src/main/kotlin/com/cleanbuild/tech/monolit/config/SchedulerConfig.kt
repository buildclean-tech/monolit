package com.cleanbuild.tech.monolit.config

import com.cleanbuild.tech.monolit.service.SSHLogWatcherService
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled

/**
 * Configuration class for scheduling tasks
 */
@Configuration
@EnableScheduling
class SchedulerConfig(
    private val sshLogWatcherService: SSHLogWatcherService
) {
    private val logger = LoggerFactory.getLogger(SchedulerConfig::class.java)
    
    /**
     * Schedule the SSH log watcher processing to run every 15 minutes
     * fixedRate = 15 minutes in milliseconds
     */
    @Scheduled(fixedRate = 15 * 60 * 1000)
    fun runSSHLogWatcherProcessing() {
        logger.info("Scheduled SSH log watcher processing started")
        try {
            sshLogWatcherService.processLogWatchers()
        } catch (e: Exception) {
            logger.error("Error during scheduled SSH log watcher processing: ${e.message}", e)
        }
        logger.info("Scheduled SSH log watcher processing completed")
    }
}