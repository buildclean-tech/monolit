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
import org.apache.lucene.search.WildcardQuery
import org.apache.lucene.store.FSDirectory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Timestamp
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test specifically for verifying case-insensitive indexing in LuceneIngestionService
 */
class LuceneIngestionServiceCaseInsensitiveTest {
    
    @TempDir
    lateinit var tempDir: Path
    
    private lateinit var dataSource: DataSource
    private lateinit var sshCommandRunner: SSHCommandRunner
    private lateinit var sshLogWatcherRecordCrud: CRUDOperation<SSHLogWatcherRecord>
    private lateinit var sshLogWatcherCrud: CRUDOperation<SSHLogWatcher>
    private lateinit var sshConfigCrud: CRUDOperation<SSHConfig>
    private lateinit var service: LuceneIngestionService
    
    private val testWatcherName = "test-watcher"
    private val testConfigName = "test-config"
    private val testServerHost = "test-server"
    private val testFilePath = "/var/log/test.log"
    private val testFileHash = "abcdef123456"
    
    // Test content with mixed case to verify case-insensitive indexing
    private val testFileContent = """
        2025-07-30 12:49:20.168 [main] WARN  com.example.Test - This is a WARNING message
        2025-07-30 12:49:20.211 [main] DEBUG com.example.Test - This is a DEBUG message with MixedCase words
        2025-07-30 12:49:20.464 [main] INFO  com.example.Test - This is an INFO message with UPPERCASE words
    """.trimIndent()
    
    @BeforeEach
    fun setup() {
        // Set up temp directory for Lucene indexes
        System.setProperty("lucene.index.dir", tempDir.toString())
        
        // Create mocks
        dataSource = mock()
        sshCommandRunner = mock()
        sshLogWatcherRecordCrud = mock()
        sshLogWatcherCrud = mock()
        sshConfigCrud = mock()
        
        // Create service
        service = LuceneIngestionService(dataSource, sshCommandRunner)
        
        // Use reflection to replace the CRUD operations with our mocks
        val recordCrudField = LuceneIngestionService::class.java.getDeclaredField("sshLogWatcherRecordCrud")
        recordCrudField.isAccessible = true
        recordCrudField.set(service, sshLogWatcherRecordCrud)
        
        val watcherCrudField = LuceneIngestionService::class.java.getDeclaredField("sshLogWatcherCrud")
        watcherCrudField.isAccessible = true
        watcherCrudField.set(service, sshLogWatcherCrud)
        
        val configCrudField = LuceneIngestionService::class.java.getDeclaredField("sshConfigCrud")
        configCrudField.isAccessible = true
        configCrudField.set(service, sshConfigCrud)
    }
    
    @AfterEach
    fun cleanup() {
        // Clean up
        service.close()
        System.clearProperty("lucene.index.dir")
    }
    
    @Test
    fun `test case-insensitive indexing and searching`() {
        // Create test data
        val testRecord = createTestRecord(1L, "NEW")
        val testWatcher = createTestWatcher()
        val testConfig = createTestConfig()
        
        // Setup mocks
        whenever(sshLogWatcherRecordCrud.findByColumnValues(mapOf(SSHLogWatcherRecord::consumptionStatus to "NEW")))
            .thenReturn(listOf(testRecord))
        whenever(sshLogWatcherCrud.findByPrimaryKey(testWatcherName)).thenReturn(testWatcher)
        whenever(sshConfigCrud.findByPrimaryKey(testConfigName)).thenReturn(testConfig)
        whenever(sshCommandRunner.getFileStream(eq(testConfig), eq(testFilePath))).thenReturn(
            ByteArrayInputStream(testFileContent.toByteArray())
        )
        
        // Process the records to create the index
        service.ingestRecords()
        
        // Verify the index directory was created
        val watcherIndexDir = tempDir.resolve(testWatcherName)
        assertTrue(Files.exists(watcherIndexDir), "Watcher index directory was not created")
        
        // Now search the index with different case variations
        val directory = FSDirectory.open(watcherIndexDir)
        val reader = DirectoryReader.open(directory)
        val searcher = IndexSearcher(reader)
        
        // Test lowercase search - this should work because content is indexed as lowercase
        val lowercaseQuery = WildcardQuery(Term("content", "*warning*"))
        var hits = searcher.search(lowercaseQuery, 10)
        assertEquals(1, hits.totalHits.value, "Should find 1 document with lowercase 'warning'")
        
        // Test uppercase search - this should NOT work because content is indexed as lowercase
        // and the search is case-sensitive at the Term level
        val uppercaseQuery = WildcardQuery(Term("content", "*WARNING*"))
        hits = searcher.search(uppercaseQuery, 10)
        assertEquals(0, hits.totalHits.value, "Should NOT find documents with uppercase 'WARNING' because terms are case-sensitive")
        
        // Test mixed case search - this should NOT work because content is indexed as lowercase
        val mixedCaseQuery = WildcardQuery(Term("content", "*MiXeDcAsE*"))
        hits = searcher.search(mixedCaseQuery, 10)
        assertEquals(0, hits.totalHits.value, "Should NOT find documents with mixed case 'MixedCase' because terms are case-sensitive")
        
        // Test another uppercase search - this should NOT work because content is indexed as lowercase
        val anotherUppercaseQuery = WildcardQuery(Term("content", "*UPPERCASE*"))
        hits = searcher.search(anotherUppercaseQuery, 10)
        assertEquals(0, hits.totalHits.value, "Should NOT find documents with 'UPPERCASE' because terms are case-sensitive")
        
        // Test lowercase versions of the same searches - these should all work
        val lowercaseWarningQuery = WildcardQuery(Term("content", "*warning*"))
        hits = searcher.search(lowercaseWarningQuery, 10)
        assertEquals(1, hits.totalHits.value, "Should find 1 document with lowercase 'warning'")
        
        val lowercaseMixedQuery = WildcardQuery(Term("content", "*mixedcase*"))
        hits = searcher.search(lowercaseMixedQuery, 10)
        assertEquals(1, hits.totalHits.value, "Should find 1 document with lowercase 'mixedcase'")
        
        val lowercaseUpperQuery = WildcardQuery(Term("content", "*uppercase*"))
        hits = searcher.search(lowercaseUpperQuery, 10)
        assertEquals(1, hits.totalHits.value, "Should find 1 document with lowercase 'uppercase'")
        
        // Clean up
        reader.close()
        directory.close()
    }
    
    // Helper methods to create test data
    
    private fun createTestRecord(id: Long, status: String): SSHLogWatcherRecord {
        return SSHLogWatcherRecord(
            id = id,
            sshLogWatcherName = testWatcherName,
            fullFilePath = testFilePath,
            fileSize = 1024L,
            cTime = Timestamp(System.currentTimeMillis()),
            fileHash = testFileHash,
            consumptionStatus = status
        )
    }
    
    private fun createTestWatcher(name: String = testWatcherName, configName: String = testConfigName): SSHLogWatcher {
        return SSHLogWatcher(
            name = name,
            sshConfigName = configName,
            watchDir = "/var/log",
            recurDepth = 1,
            filePrefix = "",
            fileContains = "test",
            filePostfix = ".log"
        )
    }
    
    private fun createTestConfig(): SSHConfig {
        return SSHConfig(
            name = testConfigName,
            serverHost = testServerHost,
            port = 22,
            username = "testuser",
            password = "testpass"
        )
    }
}