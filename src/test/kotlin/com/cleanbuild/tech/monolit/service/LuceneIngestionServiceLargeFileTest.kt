package com.cleanbuild.tech.monolit.service

import com.cleanbuild.tech.monolit.DbRecord.SSHConfig
import com.cleanbuild.tech.monolit.DbRecord.SSHLogWatcher
import com.cleanbuild.tech.monolit.DbRecord.SSHLogWatcherRecord
import com.cleanbuild.tech.monolit.com.cleanbuild.tech.monolit.repository.CRUDOperation
import com.cleanbuild.tech.monolit.ssh.SSHCommandRunner
import org.apache.lucene.document.Document
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.Term
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TermQuery
import org.apache.lucene.store.FSDirectory
import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Statement
import java.sql.Timestamp
import java.util.concurrent.TimeUnit
import java.util.UUID
import java.util.zip.GZIPOutputStream
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Performance test for LuceneIngestionService with large files
 * 
 * This test class is based on LuceneIngestionServiceEnhancedTest and focuses on measuring
 * the performance of ingesting a large (1GB) log file.
 */
class LuceneIngestionServiceLargeFileTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var indexDir: Path
    private lateinit var dataSource: DataSource
    private lateinit var sshCommandRunner: SSHCommandRunner
    private lateinit var service: LuceneIngestionService
    
    // Real CRUD operations
    private lateinit var sshLogWatcherRecordCrud: CRUDOperation<SSHLogWatcherRecord>
    private lateinit var sshLogWatcherCrud: CRUDOperation<SSHLogWatcher>
    private lateinit var sshConfigCrud: CRUDOperation<SSHConfig>

    // Test data
    private val testWatcherName = "large-file-test-watcher-${UUID.randomUUID()}"
    private val testConfigName = "large-file-test-ssh-config-${UUID.randomUUID()}"
    private val testFilePath = "/var/log/large-test.log"
    private val testFileHash = "large-file-hash-${UUID.randomUUID()}"
    private val testServerHost = "localhost"
    
    // Size constants
    private val ONE_GB = 100L * 1024L * 1024L // 1GB in bytes
    private val LOG_LINE_SIZE = 200 // Approximate size of a log line in bytes

    @BeforeEach
    fun setup() {
        // Create a custom index directory path inside the temp directory
        indexDir = tempDir.resolve("lucene-indexes")

        // Set system property to override the default index directory
        System.setProperty("lucene.index.dir", indexDir.toString())

        // Create an in-memory H2 database
        dataSource = createInMemoryDatabase()
        
        // Create real CRUD operations
        sshLogWatcherRecordCrud = CRUDOperation(dataSource, SSHLogWatcherRecord::class)
        sshLogWatcherCrud = CRUDOperation(dataSource, SSHLogWatcher::class)
        sshConfigCrud = CRUDOperation(dataSource, SSHConfig::class)
        
        // Create a mocked SSHCommandRunner that will return our generated large file content
        sshCommandRunner = mock()
        
        // Create service with real dependencies where possible
        service = LuceneIngestionService(dataSource, sshCommandRunner)
        
        // Insert test data into the database
        setupTestData()
    }

    @AfterEach
    fun cleanup() {
        // Clean up
        service.close()
        
        // Clean up test data
        cleanupTestData()

        // Reset system property
        System.clearProperty("lucene.index.dir")
    }
    
    /**
     * Create an in-memory H2 database with the required schema
     */
    private fun createInMemoryDatabase(): DataSource {
        val dataSource = JdbcDataSource()
        dataSource.setURL("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1")
        dataSource.user = "sa"
        dataSource.password = ""
        
        // Create tables
        val connection = dataSource.connection
        connection.use { conn ->
            conn.createStatement().use { stmt ->
                // Create SSHConfig table
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS sshConfig (
                        name VARCHAR(255) PRIMARY KEY,
                        serverHost VARCHAR(255) NOT NULL,
                        port INT NOT NULL,
                        username VARCHAR(255) NOT NULL,
                        password VARCHAR(255) NOT NULL,
                        createdAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        updatedAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                """)
                
                // Create SSHLogWatcher table
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS SSHLogWatcher (
                        name VARCHAR(255) PRIMARY KEY,
                        sshConfigName VARCHAR(255) NOT NULL,
                        watchDir VARCHAR(255) NOT NULL,
                        recurDepth INT NOT NULL,
                        filePrefix VARCHAR(255) NOT NULL,
                        fileContains VARCHAR(255) NOT NULL,
                        filePostfix VARCHAR(255) NOT NULL,
                        archivedLogs BOOLEAN DEFAULT TRUE,
                        enabled BOOLEAN DEFAULT TRUE,
                        createdAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        updatedAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                """)
                
                // Create SSHLogWatcherRecord table
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS SSHLogWatcherRecord (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        sshLogWatcherName VARCHAR(255) NOT NULL,
                        fullFilePath VARCHAR(255) NOT NULL,
                        fileSize BIGINT NOT NULL,
                        cTime TIMESTAMP NOT NULL,
                        fileHash VARCHAR(255) NOT NULL,
                        createdTime TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        updatedTime TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        consumptionStatus VARCHAR(50) NOT NULL,
                        duplicatedFile VARCHAR(255)
                    )
                """)
            }
        }
        
        return dataSource
    }
    
    /**
     * Set up test data in the database
     */
    private fun setupTestData() {
        // Insert SSH config
        val sshConfig = SSHConfig(
            name = testConfigName,
            serverHost = testServerHost,
            port = 22,
            username = "testuser",
            password = "testpass"
        )
        sshConfigCrud.insert(listOf(sshConfig))
        
        // Insert SSH log watcher
        val sshLogWatcher = SSHLogWatcher(
            name = testWatcherName,
            sshConfigName = testConfigName,
            watchDir = "/var/log",
            recurDepth = 1,
            filePrefix = "",
            fileContains = "large-test",
            filePostfix = ".log"
        )
        sshLogWatcherCrud.insert(listOf(sshLogWatcher))
        
        // Insert SSH log watcher record
        val sshLogWatcherRecord = SSHLogWatcherRecord(
            sshLogWatcherName = testWatcherName,
            fullFilePath = testFilePath,
            fileSize = ONE_GB,
            cTime = Timestamp(System.currentTimeMillis()),
            fileHash = testFileHash,
            consumptionStatus = "NEW",
            fileName = "large-test.log",
            noOfIndexedDocuments = null
        )
        sshLogWatcherRecordCrud.insert(listOf(sshLogWatcherRecord))
    }
    
    /**
     * Clean up test data from the database
     */
    private fun cleanupTestData() {
        // Delete SSH log watcher records
        val records = sshLogWatcherRecordCrud.findByColumnValues(
            mapOf(SSHLogWatcherRecord::sshLogWatcherName to testWatcherName)
        )
        if (records.isNotEmpty()) {
            sshLogWatcherRecordCrud.delete(records)
        }
        
        // Delete SSH log watcher
        val watcher = sshLogWatcherCrud.findByPrimaryKey(testWatcherName)
        if (watcher != null) {
            sshLogWatcherCrud.delete(listOf(watcher))
        }
        
        // Delete SSH config
        val config = sshConfigCrud.findByPrimaryKey(testConfigName)
        if (config != null) {
            sshConfigCrud.delete(listOf(config))
        }
    }

    /**
     * Generate a large log file content stream
     * 
     * This method creates a custom InputStream that generates log entries on-the-fly
     * as they are read, avoiding the need to load the entire 1GB file into memory.
     */
    private fun generateLargeLogContent(): InputStream {
        return object : InputStream() {
            // Calculate how many log lines we need to generate to reach 1GB
            private val totalLines = (ONE_GB / LOG_LINE_SIZE).toInt()
            
            // Create a sample log line template
            private val logLineTemplate = "2025-08-02 12:26:%02d.%03d [thread-%d] INFO com.cleanbuild.tech.monolit.LargeFileTest - This is log entry #%d with some additional content to make the line longer: %s\n"
            
            // Keep track of current position
            private var currentLine = 0
            private var currentLineBytes: ByteArray? = null
            private var currentPosition = 0
            
            override fun read(): Int {
                // If we've reached the end of the current line, generate a new one
                if (currentLineBytes == null || currentPosition >= currentLineBytes!!.size) {
                    if (currentLine >= totalLines) {
                        return -1 // End of stream
                    }
                    
                    // Generate the next line
                    currentLine++
                    val second = currentLine % 60
                    val millisecond = currentLine % 1000
                    val threadId = currentLine % 100
                    val randomContent = UUID.randomUUID().toString() // Add some randomness
                    val logLine = String.format(logLineTemplate, second, millisecond, threadId, currentLine, randomContent)
                    
                    // Convert to bytes
                    currentLineBytes = logLine.toByteArray(StandardCharsets.UTF_8)
                    currentPosition = 0
                }
                
                // Return the next byte
                val byteValue = currentLineBytes!![currentPosition].toInt() and 0xFF
                currentPosition++
                return byteValue
            }
            
            // Optimize bulk reads
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (b == null) {
                    throw NullPointerException()
                } else if (off < 0 || len < 0 || len > b.size - off) {
                    throw IndexOutOfBoundsException()
                } else if (len == 0) {
                    return 0
                }
                
                var bytesRead = 0
                var bytesToRead = len
                
                while (bytesRead < len) {
                    // If we need a new line
                    if (currentLineBytes == null || currentPosition >= currentLineBytes!!.size) {
                        if (currentLine >= totalLines) {
                            return if (bytesRead > 0) bytesRead else -1 // End of stream
                        }
                        
                        // Generate the next line
                        currentLine++
                        val second = currentLine % 60
                        val millisecond = currentLine % 1000
                        val threadId = currentLine % 100
                        val randomContent = UUID.randomUUID().toString() // Add some randomness
                        val logLine = String.format(logLineTemplate, second, millisecond, threadId, currentLine, randomContent)
                        
                        // Convert to bytes
                        currentLineBytes = logLine.toByteArray(StandardCharsets.UTF_8)
                        currentPosition = 0
                    }
                    
                    // Calculate how many bytes we can read from the current line
                    val availableInCurrentLine = currentLineBytes!!.size - currentPosition
                    val bytesToReadFromCurrentLine = minOf(availableInCurrentLine, bytesToRead)
                    
                    // Copy bytes from current line to output buffer
                    System.arraycopy(currentLineBytes!!, currentPosition, b, off + bytesRead, bytesToReadFromCurrentLine)
                    
                    // Update positions
                    currentPosition += bytesToReadFromCurrentLine
                    bytesRead += bytesToReadFromCurrentLine
                    bytesToRead -= bytesToReadFromCurrentLine
                    
                    // If we've read enough bytes, break
                    if (bytesToRead == 0) {
                        break
                    }
                }
                
                return bytesRead
            }
        }
    }

    @Test
    fun `test ingestion performance with 1GB log file`() {
        // Configure the mock to return our streaming large file content
        whenever(sshCommandRunner.getFileStream(any(), any())).doReturn(generateLargeLogContent())
        
        // Record start time
        val startTime = System.currentTimeMillis()
        println("[DEBUG_LOG] Starting large file ingestion test at: $startTime")
        println("[DEBUG_LOG] Processing approximately 1GB of log data using streaming approach")
        
        // Execute the method under test
        service.ingestRecords()
        
        // Record end time
        val endTime = System.currentTimeMillis()
        val durationMs = endTime - startTime
        
        // Log the performance metrics in different time units for better readability
        println("[DEBUG_LOG] Large file ingestion completed in: $durationMs ms")
        println("[DEBUG_LOG] Large file ingestion completed in: ${TimeUnit.MILLISECONDS.toSeconds(durationMs)} seconds")
        
        val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60
        println("[DEBUG_LOG] Large file ingestion completed in: $minutes minutes and $seconds seconds")
        
        // Calculate throughput
        val throughputMBPerSecond = ONE_GB / (1024.0 * 1024.0) / (durationMs / 1000.0)
        println("[DEBUG_LOG] Throughput: %.2f MB/second".format(throughputMBPerSecond))
        
        // Verify the record status was updated to INDEXED
        val updatedRecords = sshLogWatcherRecordCrud.findByColumnValues(
            mapOf(SSHLogWatcherRecord::sshLogWatcherName to testWatcherName)
        )
        assertEquals(1, updatedRecords.size, "Expected one record")
        assertEquals("INDEXED", updatedRecords[0].consumptionStatus, "Record status should be INDEXED")
        
        // Verify the index contains documents
        val watcherIndexDir = indexDir.resolve(testWatcherName)
        assertTrue(Files.exists(watcherIndexDir), "Watcher index directory was not created")
        
        val directory = FSDirectory.open(watcherIndexDir)
        val reader = DirectoryReader.open(directory)
        val searcher = IndexSearcher(reader)
        
        // Check that we have documents in the index
        val allDocsQuery = TermQuery(Term("sshWatcherName", testWatcherName))
        val hits = searcher.search(allDocsQuery, Integer.MAX_VALUE)
        assertTrue(hits.totalHits.value > 0, "Expected documents in the index")
        
        // Log the number of indexed documents
        println("[DEBUG_LOG] Number of indexed documents: ${hits.totalHits.value}")
        
        // Clean up
        reader.close()
        directory.close()
    }
    
    /**
     * Helper method to create a streaming gzipped content source
     */
    private fun createStreamingGzippedContent(): InputStream {
        // Create a piped stream to connect the output of the gzip compressor to our test
        val pipedOutputStream = java.io.PipedOutputStream()
        val pipedInputStream = java.io.PipedInputStream(pipedOutputStream)
        
        // Start a thread to write the gzipped content
        Thread {
            try {
                GZIPOutputStream(pipedOutputStream).use { gzipOutputStream ->
                    // Create a buffer for efficient writing
                    val buffer = ByteArray(8192)
                    val sourceStream = generateLargeLogContent()
                    
                    // Read from source and write to gzip stream
                    var bytesRead: Int
                    while (sourceStream.read(buffer).also { bytesRead = it } != -1) {
                        gzipOutputStream.write(buffer, 0, bytesRead)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    pipedOutputStream.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }.start()
        
        return pipedInputStream
    }
    
    @Test
    fun `test file generation throughput for 1GB log file`() {
        // Record start time
        val startTime = System.currentTimeMillis()
        println("[DEBUG_LOG] Starting file generation throughput test at: $startTime")
        println("[DEBUG_LOG] Generating approximately 1GB of log data")
        
        // Create the input stream
        val inputStream = generateLargeLogContent()
        
        // Read through the entire stream to measure generation throughput
        val buffer = ByteArray(8192)
        var totalBytesRead = 0L
        var bytesRead: Int
        
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            totalBytesRead += bytesRead
        }
        
        // Record end time
        val endTime = System.currentTimeMillis()
        val durationMs = endTime - startTime
        
        // Log the performance metrics
        println("[DEBUG_LOG] File generation completed in: $durationMs ms")
        println("[DEBUG_LOG] File generation completed in: ${TimeUnit.MILLISECONDS.toSeconds(durationMs)} seconds")
        
        val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60
        println("[DEBUG_LOG] File generation completed in: $minutes minutes and $seconds seconds")
        
        // Calculate throughput
        val throughputMBPerSecond = totalBytesRead / (1024.0 * 1024.0) / (durationMs / 1000.0)
        println("[DEBUG_LOG] File generation throughput: %.2f MB/second".format(throughputMBPerSecond))
        
        // Verify we generated the expected amount of data
        println("[DEBUG_LOG] Total bytes generated: $totalBytesRead")
        assertTrue(totalBytesRead > 0, "Expected to generate data")
        assertEquals(ONE_GB.toDouble(), totalBytesRead.toDouble(), ONE_GB * 0.05, "Generated data size should be close to 1GB")
    }
    
    @Test
    fun `test ingestion performance with 1GB compressed log file`() {
        // Configure the mock to return our streaming compressed file content
        whenever(sshCommandRunner.getFileStream(any(), any())).thenAnswer { invocation ->
            val filepath = invocation.arguments[1] as String
            if (filepath.endsWith(".gz")) {
                createStreamingGzippedContent()
            } else {
                generateLargeLogContent()
            }
        }
        
        // Create a record for the gzipped file
        val gzipRecord = SSHLogWatcherRecord(
            sshLogWatcherName = testWatcherName,
            fullFilePath = "$testFilePath.gz",
            fileSize = ONE_GB / 10, // Compressed size would be smaller
            cTime = Timestamp(System.currentTimeMillis()),
            fileHash = "gzip-hash-${UUID.randomUUID()}",
            consumptionStatus = "NEW",
            fileName = "large-test.log.gz",
            noOfIndexedDocuments = null
        )
        sshLogWatcherRecordCrud.insert(listOf(gzipRecord))
        
        // Record start time
        val startTime = System.currentTimeMillis()
        println("[DEBUG_LOG] Starting compressed large file ingestion test at: $startTime")
        println("[DEBUG_LOG] Processing approximately 1GB of compressed log data using streaming approach")
        
        // Execute the method under test
        service.ingestRecords()
        
        // Record end time
        val endTime = System.currentTimeMillis()
        val durationMs = endTime - startTime
        
        // Log the performance metrics
        println("[DEBUG_LOG] Compressed large file ingestion completed in: $durationMs ms")
        println("[DEBUG_LOG] Compressed large file ingestion completed in: ${TimeUnit.MILLISECONDS.toSeconds(durationMs)} seconds")
        
        val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60
        println("[DEBUG_LOG] Compressed large file ingestion completed in: $minutes minutes and $seconds seconds")
        
        // Calculate throughput (based on uncompressed size)
        val throughputMBPerSecond = ONE_GB / (1024.0 * 1024.0) / (durationMs / 1000.0)
        println("[DEBUG_LOG] Throughput: %.2f MB/second".format(throughputMBPerSecond))
        
        // Verify the record status was updated to INDEXED
        val updatedRecords = sshLogWatcherRecordCrud.findByColumnValues(
            mapOf(
                SSHLogWatcherRecord::sshLogWatcherName to testWatcherName,
                SSHLogWatcherRecord::fullFilePath to "$testFilePath.gz"
            )
        )
        assertEquals(1, updatedRecords.size, "Expected one compressed record")
        assertEquals("INDEXED", updatedRecords[0].consumptionStatus, "Compressed record status should be INDEXED")
        
        // Verify the index contains documents
        val watcherIndexDir = indexDir.resolve(testWatcherName)
        assertTrue(Files.exists(watcherIndexDir), "Watcher index directory was not created")
        
        val directory = FSDirectory.open(watcherIndexDir)
        val reader = DirectoryReader.open(directory)
        val searcher = IndexSearcher(reader)
        
        // Check that we have documents in the index
        val allDocsQuery = TermQuery(Term("sshWatcherName", testWatcherName))
        val hits = searcher.search(allDocsQuery, Integer.MAX_VALUE)
        assertTrue(hits.totalHits.value > 0, "Expected documents in the index")
        
        // Log the number of indexed documents
        println("[DEBUG_LOG] Number of indexed documents from compressed file: ${hits.totalHits.value}")
        
        // Clean up
        reader.close()
        directory.close()
    }
    
    @Test
    fun `test parallel ingestion of multiple 1GB log files`() {
        // Configure the mock to return a new streaming large file content for each call
        whenever(sshCommandRunner.getFileStream(any(), any())).thenAnswer { generateLargeLogContent() }
        
        // Create 5 different watcher names for parallel processing
        val watcherNames = (1..5).map { "parallel-test-watcher-$it-${UUID.randomUUID()}" }
        
        // Create 5 SSH log watchers
        val watchers = watcherNames.map { watcherName ->
            SSHLogWatcher(
                name = watcherName,
                sshConfigName = testConfigName,
                watchDir = "/var/log",
                recurDepth = 1,
                filePrefix = "",
                fileContains = "large-test",
                filePostfix = ".log"
            )
        }
        sshLogWatcherCrud.insert(watchers)
        
        // Create 5 SSH log watcher records, one for each watcher
        val records = watcherNames.mapIndexed { index, watcherName ->
            SSHLogWatcherRecord(
                sshLogWatcherName = watcherName,
                fullFilePath = "/var/log/large-test-$index.log",
                fileSize = ONE_GB,
                cTime = Timestamp(System.currentTimeMillis()),
                fileHash = "parallel-hash-$index-${UUID.randomUUID()}",
                consumptionStatus = "NEW",
                fileName = "large-test-$index.log",
                noOfIndexedDocuments = null
            )
        }
        sshLogWatcherRecordCrud.insert(records)
        
        // Record start time
        val startTime = System.currentTimeMillis()
        println("[DEBUG_LOG] Starting parallel ingestion test at: $startTime")
        println("[DEBUG_LOG] Processing 5 files of approximately 1GB each in parallel")
        
        // Execute the method under test
        service.ingestRecords()
        
        // Record end time
        val endTime = System.currentTimeMillis()
        val durationMs = endTime - startTime
        
        // Log the performance metrics
        println("[DEBUG_LOG] Parallel ingestion completed in: $durationMs ms")
        println("[DEBUG_LOG] Parallel ingestion completed in: ${TimeUnit.MILLISECONDS.toSeconds(durationMs)} seconds")
        
        val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60
        println("[DEBUG_LOG] Parallel ingestion completed in: $minutes minutes and $seconds seconds")
        
        // Calculate total throughput (based on 5GB total size)
        val totalSize = 5 * ONE_GB
        val throughputMBPerSecond = totalSize / (1024.0 * 1024.0) / (durationMs / 1000.0)
        println("[DEBUG_LOG] Total throughput: %.2f MB/second".format(throughputMBPerSecond))
        
        // Verify all records were updated to INDEXED
        for (watcherName in watcherNames) {
            val updatedRecords = sshLogWatcherRecordCrud.findByColumnValues(
                mapOf(SSHLogWatcherRecord::sshLogWatcherName to watcherName)
            )
            assertEquals(1, updatedRecords.size, "Expected one record for watcher $watcherName")
            assertEquals("INDEXED", updatedRecords[0].consumptionStatus, "Record status should be INDEXED for watcher $watcherName")
            
            // Verify each watcher's index contains documents
            val watcherIndexDir = indexDir.resolve(watcherName)
            assertTrue(Files.exists(watcherIndexDir), "Watcher index directory was not created for $watcherName")
            
            val directory = FSDirectory.open(watcherIndexDir)
            val reader = DirectoryReader.open(directory)
            val searcher = IndexSearcher(reader)
            
            // Check that we have documents in the index
            val allDocsQuery = TermQuery(Term("sshWatcherName", watcherName))
            val hits = searcher.search(allDocsQuery, Integer.MAX_VALUE)
            assertTrue(hits.totalHits.value > 0, "Expected documents in the index for watcher $watcherName")
            
            // Log the number of indexed documents
            println("[DEBUG_LOG] Number of indexed documents for watcher $watcherName: ${hits.totalHits.value}")
            
            // Clean up
            reader.close()
            directory.close()
        }
        
        // Clean up the additional watchers
        watchers.forEach { watcher ->
            val watcherRecords = sshLogWatcherRecordCrud.findByColumnValues(
                mapOf(SSHLogWatcherRecord::sshLogWatcherName to watcher.name)
            )
            if (watcherRecords.isNotEmpty()) {
                sshLogWatcherRecordCrud.delete(watcherRecords)
            }
            sshLogWatcherCrud.delete(listOf(watcher))
        }
    }
}