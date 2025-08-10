package com.cleanbuild.tech.monolit.service

import com.cleanbuild.tech.monolit.DbRecord.SSHConfig
import com.cleanbuild.tech.monolit.DbRecord.SSHLogWatcher
import com.cleanbuild.tech.monolit.DbRecord.SSHLogWatcherRecord
import com.cleanbuild.tech.monolit.repository.CRUDOperation
import com.cleanbuild.tech.monolit.ssh.SSHCommandRunner
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.*
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.Term
import org.apache.lucene.store.FSDirectory
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPInputStream
import javax.sql.DataSource
import kotlin.math.max
import kotlinx.coroutines.*
import org.apache.lucene.analysis.CharArraySet
import org.apache.lucene.codecs.Codec
import org.apache.lucene.codecs.StoredFieldsFormat
import org.apache.lucene.codecs.lucene95.Lucene95Codec
import kotlin.math.min

@Service
open class LuceneIngestionService(
    private val dataSource: DataSource,
    private val sshCommandRunner: SSHCommandRunner
) {
    private val logger = LoggerFactory.getLogger(LuceneIngestionService::class.java)
    
    // CRUD operations for database entities
    private val sshLogWatcherRecordCrud = CRUDOperation(dataSource, SSHLogWatcherRecord::class)
    private val sshLogWatcherCrud = CRUDOperation(dataSource, SSHLogWatcher::class)
    private val sshConfigCrud = CRUDOperation(dataSource, SSHConfig::class)
    private val timestampRegex = Regex("^\\d{4}[-/]\\d{2}[-/]\\d{2}\\s\\d{2}:\\d{2}:\\d{2}\\.\\d{3}.*")


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
     * Each watcher's records are processed in parallel using coroutines
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun ingestRecords(useDeflateCompression: Boolean = true) {
        logger.info("Starting ingestion of SSHLogWatcherRecords")
        
        try {
            // Get all records with consumption status "NEW"
            val newRecords = sshLogWatcherRecordCrud.findByColumnValues(
                mapOf(SSHLogWatcherRecord::consumptionStatus to "NEW")
            )
            
            logger.info("Found ${newRecords.size} new records to ingest")
            
            // Group records by watcher name
            val recordsByWatcher = newRecords.groupBy { it.sshLogWatcherName }
            
            logger.info("Processing ${recordsByWatcher.size} watchers in parallel")
            
            // Use a coroutine dispatcher with a fixed thread pool size
            val dispatcher = Dispatchers.Default.limitedParallelism(
                // Use the smaller of: number of watchers or available processors
                max(recordsByWatcher.size, 1)
            )
            
            // Run in a coroutine scope with SupervisorJob to prevent child failures from cancelling parent
            runBlocking {
                // Create a supervisor scope that won't cancel children when one fails
                supervisorScope {
                    // Create a list to track all deferred results
                    val watcherJobs = mutableListOf<Job>()
                    
                    // Launch each watcher's records for parallel processing
                    recordsByWatcher.forEach { (watcherName, records) ->
                        val job = launch(dispatcher) {
                            try {
                                processRecordsForWatcher(watcherName, records, useDeflateCompression)
                            } catch (e: Exception) {
                                logger.error("Error processing records for watcher $watcherName: ${e.message}", e)
                            }
                        }
                        watcherJobs.add(job)
                    }
                    
                    // Wait for all watcher tasks to complete
                    watcherJobs.forEach { job ->
                        try {
                            job.join()
                        } catch (e: Exception) {
                            logger.error("Error waiting for watcher task: ${e.message}", e)
                        }
                    }
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
     * Process records for a specific watcher using coroutines for parallelization
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun processRecordsForWatcher(watcherName: String, records: List<SSHLogWatcherRecord>, useDeflateCompression: Boolean = false) {
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
        val indexWriter = getIndexWriter(watcher.name, useDeflateCompression)
        
        // Counter for successful updates (using atomic to handle concurrent updates)
        val successfulUpdates = AtomicInteger(0)
        // Counter for total documents processed (using atomic to handle concurrent updates)
        val totalDocsProcessed = AtomicInteger(0)
        
        // Create a coroutine dispatcher with a fixed thread pool size
        val dispatcher = Dispatchers.Default.limitedParallelism(
            min(records.size, Runtime.getRuntime().availableProcessors()))
        
        // Process records in parallel using coroutines
        coroutineScope {
            // Launch a coroutine for each record
            val jobs = records.map { record ->
                launch(dispatcher) {
                    try {
                        val docsProcessed = processRecord(record, watcher, sshConfig, indexWriter, useDeflateCompression)
                        
                        // Create updated record but don't update DB yet
                        val updatedRecord = record.copy(
                            consumptionStatus = "INDEXED",
                            fileName = record.fileName,
                            noOfIndexedDocuments = docsProcessed.toLong()
                        )

                        indexWriter.commit()

                        // Add to records to update
                        sshLogWatcherRecordCrud.update(listOf(updatedRecord))
                        
                        // Update counters
                        successfulUpdates.incrementAndGet()
                        totalDocsProcessed.addAndGet(docsProcessed)
                    } catch (e: Exception) {
                        logger.error("Error processing record ${record.id} for watcher $watcherName: ${e.message}", e)
                        
                        // Create error record but don't update DB yet
                        val updatedRecord = record.copy(
                            consumptionStatus = "ERROR",
                            fileName = record.fileName,
                            noOfIndexedDocuments = 0L
                        )
                        
                        // Add to records to update
                        sshLogWatcherRecordCrud.update(listOf(updatedRecord))
                    }
                }
            }
            
            // Wait for all coroutines to complete
            jobs.forEach { it.join() }
        }
        
        // Log the number of successful updates and documents processed for this watcher
        logger.info("Completed processing for watcher: $watcherName - ${successfulUpdates.get()} successful updates out of ${records.size} records, ${totalDocsProcessed.get()} log documents inserted/updated")
    }
    
    /**
     * Process a single record
     * @return the number of documents processed
     */
    private fun processRecord(
        record: SSHLogWatcherRecord,
        watcher: SSHLogWatcher,
        sshConfig: SSHConfig,
        indexWriter: IndexWriter,
        useDeflateCompression: Boolean = false
    ): Int {
        logger.debug("Processing record: ${record.id}, file: ${record.fullFilePath}")
        
        try {
            // Process file content as a stream
            val docsProcessed = processFileStream(sshConfig, record.fullFilePath, record, sshConfig, indexWriter, watcher, useDeflateCompression)
            
            logger.debug("Indexed record: ${record.id}, file: ${record.fullFilePath}, documents processed: $docsProcessed")
            return docsProcessed
        } catch (e: Exception) {
            logger.error("Error indexing record ${record.id}: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Process file content as a stream, treating each log line as a separate document
     * Handles multi-line logs by identifying timestamp patterns
     * @return the number of documents processed
     */
    private fun processFileStream(
        sshConfig: SSHConfig,
        filePath: String,
        record: SSHLogWatcherRecord,
        config: SSHConfig,
        indexWriter: IndexWriter,
        watcher: SSHLogWatcher,
        useDeflateCompression: Boolean = false
    ): Int {
        try {
            // Get file stream via SSH
            val inputStream = sshCommandRunner.getFileStream(sshConfig, filePath)
            
            // Check if the file is gzipped and wrap with GZIPInputStream if needed
            val processedInputStream = if (filePath.endsWith(".gz")) {
                GZIPInputStream(inputStream)
            } else {
                inputStream
            }
            
            val reader = BufferedReader(InputStreamReader(processedInputStream))
            
            // Regex to match timestamp at the beginning of a log line
            // Format: YYYY-MM-DD HH:MM:SS.SSS
            
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
                                currentTimestamp,
                                watcher
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
                    currentTimestamp,
                    watcher
                )
                documentCount++
            }
            
            // Close the reader
            reader.close()
            
            return documentCount
            
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
        timestamp: String,
        watcher: SSHLogWatcher
    ) {
        // Create content string for hashing (including timestamp and server)
        val contentForHash = "${sshConfig.serverHost}|${sshConfig.name}${record.fileName}$logEntry|$timestamp|"
        
        // Generate MD5 hash as unique identifier
        val contentMD5Hash = generateMD5Hash(contentForHash)
        
        // Create Lucene document
        val doc = Document()
        
        // Parse timestamp to long if it has the expected format
        val timestampLong = parseTimestampToLong(timestamp, watcher.javaTimeZoneId)
        
        // Add fields - only index md5Id, logStrTimestamp, logLongTimestamp, logPath, and content
        // Store original values for retrieval but index lowercase versions for case-insensitive search
        doc.add(StringField("md5Id", contentMD5Hash, Field.Store.YES))
        doc.add(StringField("logStrTimestamp", timestamp, Field.Store.YES))

        doc.add(LongPoint("logLongTimestamp", timestampLong))                // for range/exact queries
        doc.add(NumericDocValuesField("logLongTimestamp", timestampLong))   // for sorting
        doc.add(StoredField("logLongTimestamp", timestampLong))             // optional: for retrieval

        // Store original values but index lowercase versions for case-insensitive search
        doc.add(StringField("logPath", filePath, Field.Store.YES))
        doc.add(TextField("content", logEntry, Field.Store.YES))

        // Add the document to the index
        // Use contentMD5Hash as the unique identifier to avoid duplicates
        indexWriter.updateDocument(Term("md5Id", contentMD5Hash), doc)
    }
    
    /**
     * Get or create an IndexWriter for a watcher
     */
    private fun getIndexWriter(watcherName: String, useDeflateCompression: Boolean = false): IndexWriter {
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
            val analyzer = getAnalyzer()
            
            // Create config
            val config = IndexWriterConfig(analyzer)
            config.ramBufferSizeMB = 512.0
            config.openMode = IndexWriterConfig.OpenMode.CREATE_OR_APPEND
            
            // Apply compression settings based on configuration
            if (useDeflateCompression) {
                // Use Lucene's built-in best compression codec
                config.codec =  Lucene95Codec(Lucene95Codec.Mode.BEST_COMPRESSION)
                logger.info("Using enhanced compression settings for watcher $watcherName")
            } else {
                // Use default compression
                logger.info("Using default compression for watcher $watcherName")
            }
            
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
     * Get the analyzer used by this service for query parsing
     * @return StandardAnalyzer with empty stop words set
     */
    fun getAnalyzer(): StandardAnalyzer {
        return StandardAnalyzer(CharArraySet.EMPTY_SET)
    }
    
    /**
     * Generate MD5 hash from input string
     */
    private fun generateMD5Hash(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        
        // Optimized version using StringBuilder and bit operations
        val hexChars = "0123456789abcdef"
        val result = StringBuilder(digest.size * 2)
        for (byte in digest) {
            val i = byte.toInt() and 0xFF
            result.append(hexChars[i shr 4])
            result.append(hexChars[i and 0x0F])
        }
        return result.toString()
    }
    
    /**
     * Parse timestamp string to long value
     * Format: YYYY-MM-DD HH:MM:SS.SSS
     * @param timestamp The timestamp string to parse
     * @param timezoneId The Java timezone ID to use for parsing (e.g., "UTC", "America/New_York")
     */
    private fun parseTimestampToLong(timestamp: String, timezoneId: String = "UTC"): Long {
        if (!timestamp.matches(timestampRegex)) {
            throw Exception("Timestamp regex does not match")
        }
        
        try {
            // Parse timestamp in format "YYYY-MM-DD HH:MM:SS.SSS"
            val parts = timestamp.split("\t", " ", ".", ":", "-", "/")
            
            val year = parts[0].toInt()
            val month = parts[1].toInt() - 1 // Month is 0-based in java.util.Calendar
            val day = parts[2].toInt()
            val hour = parts[3].toInt()
            val minute = parts[4].toInt()
            val second = parts[5].toInt()
            val millisecond = parts[6].toInt()
            
            // Create calendar with the specified timezone
            val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone(timezoneId))
            calendar.set(year, month, day, hour, minute, second)
            calendar.set(java.util.Calendar.MILLISECOND, millisecond)
            
            return calendar.timeInMillis
        } catch (e: Exception) {
            logger.warn("Failed to parse timestamp: $timestamp with timezone: $timezoneId", e)
            throw e
        }
    }
}
