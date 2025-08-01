package com.cleanbuild.tech.monolit.service

import com.cleanbuild.tech.monolit.DbRecord.SSHConfig
import com.cleanbuild.tech.monolit.DbRecord.SSHLogWatcher
import com.cleanbuild.tech.monolit.DbRecord.SSHLogWatcherRecord
import com.cleanbuild.tech.monolit.com.cleanbuild.tech.monolit.repository.CRUDOperation
import com.cleanbuild.tech.monolit.ssh.SSHCommandRunner
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.*
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.Term
import org.apache.lucene.store.Directory
import org.apache.lucene.store.FSDirectory
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.sql.DataSource

@Service
class LuceneIngestionService(
    private val dataSource: DataSource,
    private val sshCommandRunner: SSHCommandRunner
) {
    private val logger = LoggerFactory.getLogger(LuceneIngestionService::class.java)
    
    // CRUD operations for database entities
    private val sshLogWatcherRecordCrud = CRUDOperation(dataSource, SSHLogWatcherRecord::class)
    private val sshLogWatcherCrud = CRUDOperation(dataSource, SSHLogWatcher::class)
    private val sshConfigCrud = CRUDOperation(dataSource, SSHConfig::class)
    
    // Map to store IndexWriters for each watcher config
    private val indexWriters = ConcurrentHashMap<String, IndexWriter>()
    
    // Base directory for Lucene indexes
    private val baseIndexDir = Paths.get(System.getProperty("lucene.index.dir", "lucene-indexes"))
    
    // Getters for testing
    internal open fun getSshLogWatcherRecordCrud() = sshLogWatcherRecordCrud
    internal open fun getSshLogWatcherCrud() = sshLogWatcherCrud
    internal open fun getSshConfigCrud() = sshConfigCrud
    internal open fun getBaseIndexDir() = baseIndexDir
    
    init {
        // Create base directory if it doesn't exist
        if (!Files.exists(baseIndexDir)) {
            Files.createDirectories(baseIndexDir)
            logger.info("Created base directory for Lucene indexes: $baseIndexDir")
        }
    }
    
    /**
     * Ingest records from SSHLogWatcherRecords table
     * This method will process all records with consumption status "NEW"
     */
    fun ingestRecords() {
        logger.info("Starting ingestion of SSHLogWatcherRecords")
        
        try {
            // Get all records with consumption status "NEW"
            val newRecords = sshLogWatcherRecordCrud.findByColumnValues(
                mapOf(SSHLogWatcherRecord::consumptionStatus to "NEW")
            )
            
            logger.info("Found ${newRecords.size} new records to ingest")
            
            // Group records by watcher name
            val recordsByWatcher = newRecords.groupBy { it.sshLogWatcherName }
            
            // Process each group
            recordsByWatcher.forEach { (watcherName, records) ->
                try {
                    processRecordsForWatcher(watcherName, records)
                } catch (e: Exception) {
                    logger.error("Error processing records for watcher $watcherName: ${e.message}", e)
                }
            }
            
            logger.info("Completed ingestion of SSHLogWatcherRecords")
        } catch (e: Exception) {
            logger.error("Error during SSHLogWatcherRecords ingestion: ${e.message}", e)
        } finally {
            // Close all index writers
            closeAllIndexWriters()
        }
    }
    
    /**
     * Process records for a specific watcher
     */
    private fun processRecordsForWatcher(watcherName: String, records: List<SSHLogWatcherRecord>) {
        logger.info("Processing ${records.size} records for watcher: $watcherName")
        
        // Get the watcher configuration
        val watcher = sshLogWatcherCrud.findByPrimaryKey(watcherName)
        if (watcher == null) {
            logger.error("Watcher not found: $watcherName")
            return
        }
        
        // Get the SSH config for this watcher
        val sshConfig = sshConfigCrud.findByPrimaryKey(watcher.sshConfigName)
        if (sshConfig == null) {
            logger.error("SSH config not found for watcher $watcherName: ${watcher.sshConfigName}")
            return
        }
        
        // Get or create index writer for this watcher
        val indexWriter = getIndexWriter(watcher.name)
        
        // Process each record
        records.forEach { record ->
            try {
                processRecord(record, watcher, sshConfig, indexWriter)
                
                // Update record status to "INDEXED"
                val updatedRecord = record.copy(consumptionStatus = "INDEXED")
                // Commit changes to the index
                indexWriter.commit()
                sshLogWatcherRecordCrud.update(listOf(updatedRecord))
                
            } catch (e: Exception) {
                logger.error("Error processing record ${record.id} for watcher $watcherName: ${e.message}", e)
                
                // Update record status to "ERROR"
                val updatedRecord = record.copy(consumptionStatus = "ERROR")
                sshLogWatcherRecordCrud.update(listOf(updatedRecord))
            }
        }
    }
    
    /**
     * Process a single record
     */
    private fun processRecord(
        record: SSHLogWatcherRecord,
        watcher: SSHLogWatcher,
        sshConfig: SSHConfig,
        indexWriter: IndexWriter
    ) {
        logger.debug("Processing record: ${record.id}, file: ${record.fullFilePath}")
        
        try {
            // Process file content as a stream
            processFileStream(sshConfig, record.fullFilePath, record, sshConfig, indexWriter)
            
            logger.debug("Indexed record: ${record.id}, file: ${record.fullFilePath}")
        } catch (e: Exception) {
            logger.error("Error indexing record ${record.id}: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Process file content as a stream, treating each log line as a separate document
     * Handles multi-line logs by identifying timestamp patterns
     */
    private fun processFileStream(
        sshConfig: SSHConfig,
        filePath: String,
        record: SSHLogWatcherRecord,
        config: SSHConfig,
        indexWriter: IndexWriter
    ) {
        try {
            // Get file stream via SSH
            val inputStream = sshCommandRunner.getFileStream(sshConfig, filePath)
            val reader = BufferedReader(InputStreamReader(inputStream))
            
            // Regex to match timestamp at the beginning of a log line
            // Format: YYYY-MM-DD HH:MM:SS.SSS
            val timestampRegex = Regex("^\\d{4}-\\d{2}-\\d{2}\\s\\d{2}:\\d{2}:\\d{2}\\.\\d{3}.*")
            
            var currentLogEntry = StringBuilder()
            var currentTimestamp = ""
            var documentCount = 0
            
            // Process the file line by line
            reader.useLines { lines ->
                lines.forEach { line ->
                    // Check if this line starts a new log entry (has a timestamp)
                    if (line.matches(timestampRegex)) {
                        // If we have collected a previous log entry, index it
                        if (currentLogEntry.isNotEmpty()) {
                            indexLogEntry(
                                currentLogEntry.toString(),
                                record,
                                config,
                                filePath,
                                indexWriter,
                                currentTimestamp
                            )
                            documentCount++
                        }
                        
                        // Start a new log entry
                        currentLogEntry = StringBuilder(line)
                        // Extract timestamp for document ID
                        currentTimestamp = line.substring(0, 23) // YYYY-MM-DD HH:MM:SS.SSS
                    } else if (currentLogEntry.isNotEmpty()) {
                        // This is a continuation of the current log entry (multi-line log)
                        currentLogEntry.append("\n").append(line)
                    } else {
                        // This is a line without a timestamp and no current log entry
                        // Treat it as its own log entry
                        currentLogEntry.append(line)
                        currentTimestamp = ""
                    }
                }
            }
            
            // Index the last log entry if there is one
            if (currentLogEntry.isNotEmpty()) {
                indexLogEntry(
                    currentLogEntry.toString(),
                    record,
                    config,
                    filePath,
                    indexWriter,
                    currentTimestamp
                )
            }
            
            // Close the reader
            reader.close()
            
        } catch (e: Exception) {
            logger.error("Error processing file stream for $filePath: ${e.message}", e)
            throw e
        }
    }
   
    
    /**
     * Index a single log entry as a document
     */
    private fun indexLogEntry(
        logEntry: String,
        record: SSHLogWatcherRecord,
        sshConfig: SSHConfig,
        filePath: String,
        indexWriter: IndexWriter,
        timestamp: String
    ) {
        // Create content string for hashing (including timestamp and server)
        val contentForHash = "${sshConfig.serverHost}|${sshConfig.name}$logEntry|$timestamp|"
        
        // Generate MD5 hash as unique identifier
        val contentMD5Hash = generateMD5Hash(contentForHash)
        
        // Create Lucene document
        val doc = Document()
        
        // Parse timestamp to long if it has the expected format
        val timestampLong = parseTimestampToLong(timestamp)
        
        // Add fields - only index md5Id, logStrTimestamp, logLongTimestamp, logPath, and content
        doc.add(StringField("md5Id", contentMD5Hash, Field.Store.YES))
        doc.add(StringField("logStrTimestamp", timestamp, Field.Store.YES))
        // Add the parsed timestamp as a long field
        doc.add(LongField("logLongTimestamp", timestampLong ?: 0L, Field.Store.YES))
        doc.add(LongField("ingestionLongTimestamp", System.currentTimeMillis(), Field.Store.NO))
        doc.add(StringField("sshConfigServer", sshConfig.serverHost, Field.Store.NO))
        doc.add(StringField("sshConfigName", sshConfig.name, Field.Store.NO))
        doc.add(StringField("sshWatcherName", record.sshLogWatcherName, Field.Store.NO))
        doc.add(LongField("sshWatcherRecordId", record.id!!, Field.Store.NO))
        doc.add(TextField("logPath", filePath, Field.Store.YES))
        doc.add(TextField("content", logEntry, Field.Store.YES))

        // Add the document to the index
        // Use contentMD5Hash as the unique identifier to avoid duplicates
        indexWriter.updateDocument(Term("md5Id", contentMD5Hash), doc)
    }
    
    /**
     * Get or create an IndexWriter for a watcher
     */
    private fun getIndexWriter(watcherName: String): IndexWriter {
        return indexWriters.computeIfAbsent(watcherName) {
            // Create directory for this watcher
            val indexDir = baseIndexDir.resolve(watcherName)
            if (!Files.exists(indexDir)) {
                Files.createDirectories(indexDir)
                logger.info("Created directory for watcher $watcherName: $indexDir")
            }
            
            // Create directory
            val directory = FSDirectory.open(indexDir)
            
            // Create analyzer
            val analyzer = StandardAnalyzer()
            
            // Create config
            val config = IndexWriterConfig(analyzer)
            config.openMode = IndexWriterConfig.OpenMode.CREATE_OR_APPEND
            
            // Create writer
            val writer = IndexWriter(directory, config)
            logger.info("Created IndexWriter for watcher $watcherName")
            
            writer
        }
    }
    
    /**
     * Close all index writers
     */
    private fun closeAllIndexWriters() {
        indexWriters.forEach { (watcherName, writer) ->
            try {
                writer.close()
                logger.info("Closed IndexWriter for watcher $watcherName")
            } catch (e: Exception) {
                logger.error("Error closing IndexWriter for watcher $watcherName: ${e.message}", e)
            }
        }
        indexWriters.clear()
    }
    
    /**
     * Close the service and release resources
     */
    fun close() {
        closeAllIndexWriters()
    }
    
    /**
     * Generate MD5 hash from input string
     */
    private fun generateMD5Hash(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Parse timestamp string to long value
     * Format: YYYY-MM-DD HH:MM:SS.SSS
     */
    private fun parseTimestampToLong(timestamp: String): Long? {
        if (!timestamp.matches(Regex("\\d{4}-\\d{2}-\\d{2}\\s\\d{2}:\\d{2}:\\d{2}\\.\\d{3}"))) {
            return null
        }
        
        try {
            // Parse timestamp in format "YYYY-MM-DD HH:MM:SS.SSS"
            val parts = timestamp.split("\t", " ", ".", ":", "-")
            
            val year = parts[0].toInt()
            val month = parts[1].toInt() - 1 // Month is 0-based in java.util.Calendar
            val day = parts[2].toInt()
            val hour = parts[3].toInt()
            val minute = parts[4].toInt()
            val second = parts[5].toInt()
            val millisecond = parts[6].toInt()
            
            val calendar = java.util.Calendar.getInstance()
            calendar.set(year, month, day, hour, minute, second)
            calendar.set(java.util.Calendar.MILLISECOND, millisecond)
            
            return calendar.timeInMillis
        } catch (e: Exception) {
            logger.warn("Failed to parse timestamp: $timestamp", e)
            return null
        }
    }
}