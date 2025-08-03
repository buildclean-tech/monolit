package com.cleanbuild.tech.monolit.service

import com.cleanbuild.tech.monolit.DbRecord.SSHConfig
import com.cleanbuild.tech.monolit.DbRecord.SSHLogWatcher
import com.cleanbuild.tech.monolit.DbRecord.SSHLogWatcherRecord
import com.cleanbuild.tech.monolit.com.cleanbuild.tech.monolit.repository.CRUDOperation
import com.cleanbuild.tech.monolit.ssh.SSHCommandRunner
import org.jetbrains.kotlin.util.Time
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.sql.Timestamp
import javax.sql.DataSource

@Service
class SSHLogWatcherService(
    private val dataSource: DataSource,
    private val sshCommandRunner: SSHCommandRunner
) {
    private val logger = LoggerFactory.getLogger(SSHLogWatcherService::class.java)
    
    // CRUD operations for database entities
    private val sshLogWatcherCrud = CRUDOperation(dataSource, SSHLogWatcher::class)
    private val sshConfigCrud = CRUDOperation(dataSource, SSHConfig::class)
    private val sshLogWatcherRecordCrud = CRUDOperation(dataSource, SSHLogWatcherRecord::class)
    
    // Getters for testing
    internal fun getSshLogWatcherCrud() = sshLogWatcherCrud
    internal fun getSshConfigCrud() = sshConfigCrud
    internal fun getSshLogWatcherRecordCrud() = sshLogWatcherRecordCrud
    
    /**
     * Process all enabled SSH log watchers
     * This method will be called by the scheduler
     */
    fun processLogWatchers() {
        logger.info("Starting SSH log watcher processing")
        
        try {
            // Get all enabled SSH log watchers
            val allWatchers = sshLogWatcherCrud.findAll()
            val enabledWatchers = allWatchers.filter { it.enabled }
            
            logger.info("Found ${enabledWatchers.size} enabled SSH log watchers out of ${allWatchers.size} total")
            
            // Process each enabled watcher
            enabledWatchers.forEach { watcher ->
                try {
                    processWatcher(watcher)
                } catch (e: Exception) {
                    logger.error("Error processing watcher ${watcher.name}: ${e.message}", e)
                }
            }
            
            logger.info("Completed SSH log watcher processing")
        } catch (e: Exception) {
            logger.error("Error during SSH log watcher processing: ${e.message}", e)
        }
    }
    
    /**
     * Process a single SSH log watcher
     */
    private fun processWatcher(watcher: SSHLogWatcher) {
        logger.info("Processing watcher: ${watcher.name}")
        
        // Get the SSH config for this watcher
        val sshConfig = sshConfigCrud.findByPrimaryKey(watcher.sshConfigName)
        if (sshConfig == null) {
            logger.error("SSH config not found for watcher ${watcher.name}: ${watcher.sshConfigName}")
            return
        }
        
        // Build the file pattern
        val filePattern = buildFilePattern(watcher)
        
        try {
            // Find files matching the pattern
            val files = sshCommandRunner.findFiles(
                sshConfig = sshConfig,
                directory = watcher.watchDir,
                pattern = filePattern,
                maxDepth = watcher.recurDepth
            )
            
            logger.info("Found ${files.size} files matching pattern for watcher ${watcher.name}")
            
            // Process each file
            files.forEach { file ->
                processFile(watcher, file)
            }
        } catch (e: Exception) {
            logger.error("Error finding files for watcher ${watcher.name}: ${e.message}", e)
        }
    }
    
    /**
     * Build a file pattern for the find command based on watcher configuration
     */
    private fun buildFilePattern(watcher: SSHLogWatcher): String {
        val pattern = StringBuilder()
        
        // Add prefix if specified
        if (watcher.filePrefix.isNotEmpty()) {
            pattern.append(watcher.filePrefix)
        }
        
        // Add contains if specified
        if (watcher.fileContains.isNotEmpty()) {
            pattern.append("*${watcher.fileContains}*")
        } else {
            pattern.append("*")
        }
        
        // Add postfix if specified
        if (watcher.filePostfix.isNotEmpty()) {
            pattern.append(watcher.filePostfix)
        }
        
        return pattern.toString()
    }
    
    /**
     * Process a single file found by the watcher
     */
    private fun processFile(watcher: SSHLogWatcher, file: SSHCommandRunner.FileMetadata) {
        logger.debug("Processing file: ${file.filepath}")
        
        // Calculate file hash
        val fileHash = calculateFileHash(watcher.name, file.filename, file.size, file.ctime)

        //Check for any changes to file size or ctime
        val existingRecord = sshLogWatcherRecordCrud.findByColumnValues(mapOf(SSHLogWatcherRecord::fileHash to fileHash))
            .firstOrNull { it.fileHash == fileHash && it.sshLogWatcherName == watcher.name && it.fullFilePath == file.filepath }

        
        // Save the record
        try {
             if(existingRecord==null) {
                // Check for duplicates based on hash
                val duplicateRecords = sshLogWatcherRecordCrud.findByColumnValues(mapOf(SSHLogWatcherRecord::fileHash to fileHash))
                    .filter { it.fileHash == fileHash && it.sshLogWatcherName == watcher.name && it.fullFilePath != file.filepath }

                val duplicatedFile = if (duplicateRecords.isNotEmpty()) {
                    duplicateRecords.first().fullFilePath
                } else {
                    null
                }

                // Create a new record
                val record = SSHLogWatcherRecord(
                    id = null, // Auto-generated
                    sshLogWatcherName = watcher.name,
                    fullFilePath = file.filepath,
                    fileSize = file.size,
                    cTime = Timestamp(file.ctime),
                    fileHash = fileHash,
                    consumptionStatus = if (duplicatedFile != null) "DUPLICATED" else "NEW",
                    duplicatedFile = duplicatedFile,
                    fileName = file.filename,
                    noOfIndexedDocuments = null
                )
                sshLogWatcherRecordCrud.insert(listOf(record))
                logger.info("Created new record for file: ${file.filepath}")
            } else {
                 // Update existing record time
                sshLogWatcherRecordCrud.update(listOf(existingRecord.copy(updatedTime = Timestamp(System.currentTimeMillis()))))
             }

        } catch (e: Exception) {
            logger.error("Error creating record for file ${file.filepath}: ${e.message}", e)
        }
    }
    
    /**
     * Calculate a hash for the file based on its name, size, and creation time
     */
    private fun calculateFileHash(sshWatcherName:String, filename: String, size: Long, ctime: Long): String {
        val input = "$sshWatcherName$filename-$size-$ctime"
        val md = MessageDigest.getInstance("SHA-256")
        val hashBytes = md.digest(input.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}