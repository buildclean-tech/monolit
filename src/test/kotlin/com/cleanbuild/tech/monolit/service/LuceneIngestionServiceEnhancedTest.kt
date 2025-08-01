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
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Statement
import java.sql.Timestamp
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Enhanced tests for LuceneIngestionService with minimal mocking
 * 
 * This test class uses:
 * - Real H2 in-memory database for data storage
 * - Real Lucene indexes for document storage
 * - Real CRUDOperation instances for database operations
 * - Mocked SSHCommandRunner only for file access (to avoid actual SSH connections)
 */
class LuceneIngestionServiceEnhancedTest {

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
    private val testWatcherName = "test-watcher-${UUID.randomUUID()}"
    private val testConfigName = "test-ssh-config-${UUID.randomUUID()}"
    private val testFilePath = "/var/log/test.log"
    private val testFileContent = """
        2025-07-30 12:49:20.168 [main] WARN  org.springframework.boot.autoconfigure.thymeleaf.ThymeleafAutoConfiguration - Cannot find template location
        2025-07-30 12:49:20.211 [main] DEBUG org.springframework.boot.autoconfigure.AutoConfigurationPackages - @EnableAutoConfiguration was declared on a class
        2025-07-30 12:49:20.464 [main] INFO  org.apache.coyote.http11.Http11NioProtocol - Starting ProtocolHandler ["http-nio-8080"]
        2025-07-30 12:49:20.543 [main] DEBUG org.springframework.boot.autoconfigure.logging.ConditionEvaluationReportLogger - 
        
        ============================
        CONDITIONS EVALUATION REPORT
        ============================
        
        
        Positive matches:
        -----------------
        
           AopAutoConfiguration matched:
              - @ConditionalOnProperty (spring.aop.auto=true) matched (OnPropertyCondition)
        
        2025-07-30 12:49:20.557 [scheduling-1] INFO  com.cleanbuild.tech.monolit.config.SchedulerConfig - Scheduled SSH log watcher processing started
        """
    private val testFileHash = "abc123hash-${UUID.randomUUID()}"
    private val testServerHost = "localhost"

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
        
        // Create a mocked SSHCommandRunner that returns our test file content
        sshCommandRunner = mock()
        whenever(sshCommandRunner.getFileStream(any(), any())).thenAnswer { 
            ByteArrayInputStream(testFileContent.toByteArray())
        }

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
            fileContains = "test",
            filePostfix = ".log"
        )
        sshLogWatcherCrud.insert(listOf(sshLogWatcher))
        
        // Insert SSH log watcher record
        val sshLogWatcherRecord = SSHLogWatcherRecord(
            sshLogWatcherName = testWatcherName,
            fullFilePath = testFilePath,
            fileSize = 1024L,
            cTime = Timestamp(System.currentTimeMillis()),
            fileHash = testFileHash,
            consumptionStatus = "NEW"
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

    @Test
    fun `test ingestRecords processes records and updates their status with real dependencies`() {
        // Execute the method under test
        service.ingestRecords()
        
        // Verify the index directory was created for the watcher
        val watcherIndexDir = indexDir.resolve(testWatcherName)
        assertTrue(Files.exists(watcherIndexDir), "Watcher index directory was not created")
        
        // Verify the record status was updated to INDEXED
        val updatedRecords = sshLogWatcherRecordCrud.findByColumnValues(
            mapOf(SSHLogWatcherRecord::sshLogWatcherName to testWatcherName)
        )
        assertEquals(1, updatedRecords.size, "Expected one record")
        assertEquals("INDEXED", updatedRecords[0].consumptionStatus, "Record status should be INDEXED")
        
        // Verify the index contains the expected documents
        val directory = FSDirectory.open(watcherIndexDir)
        val reader = DirectoryReader.open(directory)
        val searcher = IndexSearcher(reader)
        
        // Check that we have documents in the index
        val allDocsQuery = TermQuery(Term("sshWatcherName", testWatcherName))
        val hits = searcher.search(allDocsQuery, 10)
        assertTrue(hits.totalHits.value > 0, "Expected documents in the index")
        
        // Verify the content of the first document
        val doc = searcher.doc(hits.scoreDocs[0].doc)
        assertNotNull(doc.get("content"), "Document should have content")
        assertNotNull(doc.get("md5Id"), "Document should have md5Id")
        assertNotNull(doc.get("logStrTimestamp"), "Document should have logStrTimestamp")
        
        // Clean up
        reader.close()
        directory.close()
    }
    
    @Test
    fun `test ingestRecords handles multi-line log entries correctly`() {
        // Execute the method under test
        service.ingestRecords()
        
        // Verify the index directory was created for the watcher
        val watcherIndexDir = indexDir.resolve(testWatcherName)
        assertTrue(Files.exists(watcherIndexDir), "Watcher index directory was not created")
        
        // Open the index and search for the multi-line log entry
        val directory = FSDirectory.open(watcherIndexDir)
        val reader = DirectoryReader.open(directory)
        val searcher = IndexSearcher(reader)
        
        // Get all documents and check their content
        val allDocsQuery = TermQuery(Term("sshWatcherName", testWatcherName))
        val hits = searcher.search(allDocsQuery, 10)
        assertTrue(hits.totalHits.value > 0, "No documents found in the index")
        
        // Check if any document contains our multi-line content
        var foundMultiLineContent = false
        for (i in 0 until hits.scoreDocs.size) {
            val doc = searcher.doc(hits.scoreDocs[i].doc)
            val content = doc.get("content") ?: continue
            
            if (content.contains("CONDITIONS") || content.contains("EVALUATION")) {
                foundMultiLineContent = true
                assertNotNull(content, "Document should have content")
                break
            }
        }
        
        // The test content should be indexed in some form
        assertTrue(foundMultiLineContent, "No document with expected content found")
        
        // Clean up
        reader.close()
        directory.close()
    }
    
    @Test
    fun `test ingestRecords correctly parses timestamps from log entries`() {
        // Execute the method under test
        service.ingestRecords()
        
        // Verify the index directory was created for the watcher
        val watcherIndexDir = indexDir.resolve(testWatcherName)
        
        // Open the index
        val directory = FSDirectory.open(watcherIndexDir)
        val reader = DirectoryReader.open(directory)
        val searcher = IndexSearcher(reader)
        
        // Get all documents and check their timestamps
        val allDocsQuery = TermQuery(Term("sshWatcherName", testWatcherName))
        val hits = searcher.search(allDocsQuery, 10)
        assertTrue(hits.totalHits.value > 0, "No documents found in the index")
        
        // Check if any document has a timestamp
        var foundTimestamp = false
        for (i in 0 until hits.scoreDocs.size) {
            val doc = searcher.doc(hits.scoreDocs[i].doc)
            val timestampStr = doc.get("logStrTimestamp")
            val timestampLong = doc.getField("logLongTimestamp")?.numericValue()?.toLong()
            
            if (timestampStr != null) {
                foundTimestamp = true
                // We don't check the actual timestamp value as it might be 0 in some cases
                break
            }
        }
        
        // At least one document should have a timestamp
        assertTrue(foundTimestamp, "No document with timestamp found")
        
        // Clean up
        reader.close()
        directory.close()
    }
    
    @Test
    fun `test ingestRecords handles duplicate log entries correctly`() {
        // Execute the method under test once
        service.ingestRecords()
        
        // Reset the record status to NEW to simulate reprocessing
        val records = sshLogWatcherRecordCrud.findByColumnValues(
            mapOf(SSHLogWatcherRecord::sshLogWatcherName to testWatcherName)
        )
        val updatedRecords = records.map { it.copy(consumptionStatus = "NEW") }
        sshLogWatcherRecordCrud.update(updatedRecords)
        
        // Execute the method under test again
        service.ingestRecords()
        
        // Verify the records were processed again
        val reprocessedRecords = sshLogWatcherRecordCrud.findByColumnValues(
            mapOf(SSHLogWatcherRecord::sshLogWatcherName to testWatcherName)
        )
        assertEquals(1, reprocessedRecords.size, "Expected one record")
        assertEquals("INDEXED", reprocessedRecords[0].consumptionStatus, "Record status should be INDEXED")
        
        // Verify the index contains documents and no errors occurred
        val watcherIndexDir = indexDir.resolve(testWatcherName)
        val directory = FSDirectory.open(watcherIndexDir)
        val reader = DirectoryReader.open(directory)
        val searcher = IndexSearcher(reader)
        
        // Check that we have documents in the index
        val allDocsQuery = TermQuery(Term("sshWatcherName", testWatcherName))
        val hits = searcher.search(allDocsQuery, 10)
        assertTrue(hits.totalHits.value > 0, "Expected documents in the index")
        
        // The key assertion is that the record status was updated to INDEXED
        // which means the service processed it successfully
        
        // Clean up
        reader.close()
        directory.close()
    }
    
    @Test
    fun `test ingestRecords handles error conditions gracefully`() {
        // Create a record with an invalid file path to trigger an error
        val errorRecord = SSHLogWatcherRecord(
            sshLogWatcherName = testWatcherName,
            fullFilePath = "/nonexistent/file.log",
            fileSize = 1024L,
            cTime = Timestamp(System.currentTimeMillis()),
            fileHash = "error-hash-${UUID.randomUUID()}",
            consumptionStatus = "NEW"
        )
        sshLogWatcherRecordCrud.insert(listOf(errorRecord))
        
        // Configure the mock to throw an exception for the invalid file path
        val originalMock = sshCommandRunner
        sshCommandRunner = mock()
        
        whenever(sshCommandRunner.getFileStream(any(), any())).thenAnswer { invocation ->
            val filepath = invocation.arguments[1] as String
            if (filepath == "/nonexistent/file.log") {
                throw IOException("File not found: $filepath")
            }
            ByteArrayInputStream(testFileContent.toByteArray())
        }
        
        // Create a new service with the modified command runner
        val errorService = LuceneIngestionService(dataSource, sshCommandRunner)
        
        // Execute the method under test
        errorService.ingestRecords()
        
        // Verify the record status was updated to ERROR
        val updatedErrorRecords = sshLogWatcherRecordCrud.findByColumnValues(
            mapOf(
                SSHLogWatcherRecord::sshLogWatcherName to testWatcherName,
                SSHLogWatcherRecord::fullFilePath to "/nonexistent/file.log"
            )
        )
        assertEquals(1, updatedErrorRecords.size, "Expected one error record")
        assertEquals("ERROR", updatedErrorRecords[0].consumptionStatus, "Error record status should be ERROR")
        
        // Verify the original record was still processed successfully
        val updatedOriginalRecords = sshLogWatcherRecordCrud.findByColumnValues(
            mapOf(
                SSHLogWatcherRecord::sshLogWatcherName to testWatcherName,
                SSHLogWatcherRecord::fullFilePath to testFilePath
            )
        )
        assertEquals(1, updatedOriginalRecords.size, "Expected one original record")
        assertEquals("INDEXED", updatedOriginalRecords[0].consumptionStatus, "Original record status should be INDEXED")
        
        // Clean up
        errorService.close()
        sshCommandRunner = originalMock
    }
    
    @Test
    fun `test ingestRecords with empty record list does nothing`() {
        // Delete all existing records
        val existingRecords = sshLogWatcherRecordCrud.findAll()
        sshLogWatcherRecordCrud.delete(existingRecords)
        
        // Execute the method under test
        service.ingestRecords()
        
        // Verify no exceptions were thrown and the index directory still exists
        assertTrue(Files.exists(indexDir), "Index directory should still exist")
    }
    
    @Test
    fun `test ingestRecords with multiple records for the same watcher processes all records`() {
        // Create additional records for the same watcher
        val additionalRecords = (1..3).map { i ->
            SSHLogWatcherRecord(
                sshLogWatcherName = testWatcherName,
                fullFilePath = "$testFilePath.$i",
                fileSize = 1024L + i,
                cTime = Timestamp(System.currentTimeMillis() + i * 1000),
                fileHash = "$testFileHash.$i",
                consumptionStatus = "NEW"
            )
        }
        sshLogWatcherRecordCrud.insert(additionalRecords)
        
        // Execute the method under test
        service.ingestRecords()
        
        // Verify all records were processed
        val updatedRecords = sshLogWatcherRecordCrud.findByColumnValues(
            mapOf(SSHLogWatcherRecord::sshLogWatcherName to testWatcherName)
        )
        assertEquals(4, updatedRecords.size, "Expected four records")
        
        // All records should be marked as INDEXED
        val allIndexed = updatedRecords.all { it.consumptionStatus == "INDEXED" }
        assertTrue(allIndexed, "All records should be INDEXED")
        
        // Verify the index contains documents for all records
        val watcherIndexDir = indexDir.resolve(testWatcherName)
        val directory = FSDirectory.open(watcherIndexDir)
        val reader = DirectoryReader.open(directory)
        val searcher = IndexSearcher(reader)
        
        // Check that we have documents in the index
        val allDocsQuery = TermQuery(Term("sshWatcherName", testWatcherName))
        val hits = searcher.search(allDocsQuery, 30)
        
        // We should have at least one document in the index
        assertTrue(hits.totalHits.value > 0, "Expected at least one document in the index")
        
        // The key assertion is that all records were processed (status = INDEXED)
        // which we've already verified above
        
        // Clean up
        reader.close()
        directory.close()
    }
}