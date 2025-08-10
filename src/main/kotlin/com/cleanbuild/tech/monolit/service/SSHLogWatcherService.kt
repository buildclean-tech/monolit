package com.cleanbuild.tech.monolit.service

import com.cleanbuild.tech.monolit.DbRecord.SSHConfig
import com.cleanbuild.tech.monolit.DbRecord.SSHLogWatcher
import com.cleanbuild.tech.monolit.DbRecord.SSHLogWatcherRecord
import com.cleanbuild.tech.monolit.repository.CRUDOperation
import com.cleanbuild.tech.monolit.ssh.SSHCommandRunner
import org.jetbrains.kotlin.util.Time
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.sql.Timestamp
import javax.sql.DataSource
import java.io.InputStream

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
                processFile(watcher, sshConfig, file)
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
    private fun processFile(watcher: SSHLogWatcher, sshConfig: SSHConfig, file: SSHCommandRunner.FileMetadata) {
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

                 // Calculate file identity hash (MD5 of first 1KB)
                 val fileIdentityHash = calculateFileIdentityHash(sshConfig, file.filepath)

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
                    noOfIndexedDocuments = 0,
                    fileIdentityHash = fileIdentityHash
                )
                sshLogWatcherRecordCrud.insert(listOf(record))
                logger.info("Created new record for file: ${file.filepath}")
            } else {
                 // Update existing record time and fileIdentityHash if needed
                val updatedRecord = existingRecord.copy(
                    updatedTime = Timestamp(System.currentTimeMillis())
                )
                sshLogWatcherRecordCrud.update(listOf(updatedRecord))
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
    
    /**
     * Calculate an MD5 hash of the first 1KB of file content
     * @param sshConfig SSH configuration to connect to the server
     * @param filepath Full path to the file on the remote server
     * @return MD5 hash of the first 1MB of file content, or null if there was an error
     */
    private fun calculateFileIdentityHash(sshConfig: SSHConfig, filepath: String): String {

        // Readstream
        val inputStream = sshCommandRunner.getFileStreamFromOffset(sshConfig, filepath, 0, 1024*1024)
        try {
            logger.debug("Calculating file identity hash for: $filepath")
            


            val buffer = ByteArray(1024 * 1024)
            var bytesReadTotal = 0
            while (bytesReadTotal < buffer.size) {
                val bytesRead = inputStream.read(buffer, bytesReadTotal, buffer.size - bytesReadTotal)
                if (bytesRead < 0) break // EOF
                bytesReadTotal += bytesRead
            }
            
            if (bytesReadTotal <= 0) {
                logger.warn("No data read from file: $filepath")
                throw Exception("No data read from file: $filepath")
            }
            
            // Calculate MD5 hash of the read data
            val md = MessageDigest.getInstance("MD5")
            md.update(buffer, 0, bytesReadTotal)
            val hashBytes = md.digest()
            
            // Convert to hex string
            val hashString = hashBytes.joinToString("") { "%02x".format(it) }
            logger.debug("File identity hash for $filepath: $hashString")
            
            return hashString
        } catch (e: Exception) {
            logger.error("Error calculating file identity hash for $filepath: ${e.message}", e)
            throw e
        } finally {
            inputStream.close()
        }
    }
}
