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
            // Extract path and filename
            val pathParts = record.fullFilePath.split("/")
            val filename = pathParts.lastOrNull() ?: ""
            val path = pathParts.dropLast(1).joinToString("/")
            
            // Process file content as a stream
            processFileStream(sshConfig, record.fullFilePath, record, sshConfig, path, filename, indexWriter)
            
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
        path: String,
        filename: String,
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
                                path,
                                filename,
                                indexWriter,
                                documentCount,
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
                        currentTimestamp = "unknown-${documentCount}"
                    }
                }
            }
            
            // Index the last log entry if there is one
            if (currentLogEntry.isNotEmpty()) {
                indexLogEntry(
                    currentLogEntry.toString(),
                    record,
                    config,
                    path,
                    filename,
                    indexWriter,
                    documentCount,
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
        path: String,
        filename: String,
        indexWriter: IndexWriter,
        documentCount: Int,
        timestamp: String
    ) {
        // Create a unique ID for this log entry
        val logEntryId = "${record.id}-${timestamp}-${documentCount}"
        
        // Create Lucene document
        val doc = Document()
        
        // Add fields
        doc.add(StringField("id", logEntryId, Field.Store.YES))
        doc.add(StringField("recordId", record.id.toString(), Field.Store.YES))
        doc.add(StringField("logHash", "${record.fileHash}-${documentCount}", Field.Store.YES))
        doc.add(LongField("timestamp", record.cTime.time, Field.Store.YES))
        doc.add(StringField("logTimestamp", timestamp, Field.Store.YES))
        doc.add(StringField("server", sshConfig.serverHost, Field.Store.YES))
        doc.add(StringField("path", path, Field.Store.YES))
        doc.add(StringField("file", filename, Field.Store.YES))
        doc.add(TextField("content", logEntry, Field.Store.YES))

        // Add the document to the index
        // Use logEntryId as the unique identifier to avoid duplicates
        indexWriter.updateDocument(Term("id", logEntryId), doc)
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
}