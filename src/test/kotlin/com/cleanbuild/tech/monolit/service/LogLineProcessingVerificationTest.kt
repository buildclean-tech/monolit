package com.cleanbuild.tech.monolit.service

import com.cleanbuild.tech.monolit.DbRecord.SSHConfig
import com.cleanbuild.tech.monolit.DbRecord.SSHLogWatcher
import com.cleanbuild.tech.monolit.DbRecord.SSHLogWatcherRecord
import com.cleanbuild.tech.monolit.com.cleanbuild.tech.monolit.repository.CRUDOperation
import com.cleanbuild.tech.monolit.ssh.SSHCommandRunner
import org.apache.lucene.document.Document
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexReader
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.MatchAllDocsQuery
import org.apache.lucene.search.TopDocs
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

/**
 * Verification test for log line processing
 */
class LogLineProcessingVerificationTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var indexDir: Path
    private lateinit var dataSource: DataSource
    private lateinit var sshCommandRunner: SSHCommandRunner
    private lateinit var service: LuceneIngestionService

    // Mock CRUD operations
    private lateinit var sshLogWatcherRecordCrud: CRUDOperation<SSHLogWatcherRecord>
    private lateinit var sshLogWatcherCrud: CRUDOperation<SSHLogWatcher>
    private lateinit var sshConfigCrud: CRUDOperation<SSHConfig>

    // Test data
    private val testWatcherName = "test-watcher"
    private val testConfigName = "test-ssh-config"
    private val testFilePath = "/var/log/test.log"
    private val testServerHost = "localhost"
    
    // Sample log content from the issue description
    private val sampleLogContent = """
        2025-07-30 12:49:20.168 [main] WARN  org.springframework.boot.autoconfigure.thymeleaf.ThymeleafAutoConfiguration${'$'}DefaultTemplateResolverConfiguration - Cannot find template location: classpath:/templates/ (please add some templates, check your Thymeleaf configuration, or set spring.thymeleaf.check-template-location=false)
        2025-07-30 12:49:20.211 [main] DEBUG org.springframework.boot.autoconfigure.AutoConfigurationPackages - @EnableAutoConfiguration was declared on a class in the package 'com.cleanbuild.tech.monolit'. Automatic @Repository and @Entity scanning is enabled.
        2025-07-30 12:49:20.464 [main] INFO  org.apache.coyote.http11.Http11NioProtocol - Starting ProtocolHandler ["http-nio-8080"]
        2025-07-30 12:49:20.505 [main] INFO  org.springframework.boot.web.embedded.tomcat.TomcatWebServer - Tomcat started on port(s): 8080 (http) with context path ''
        2025-07-30 12:49:20.543 [main] DEBUG org.springframework.boot.autoconfigure.logging.ConditionEvaluationReportLogger - 

        ============================
        CONDITIONS EVALUATION REPORT
        ============================


        Positive matches:
        -----------------

           AopAutoConfiguration matched:
              - @ConditionalOnProperty (spring.aop.auto=true) matched (OnPropertyCondition)

           AopAutoConfiguration.ClassProxyingConfiguration matched:
              - @ConditionalOnMissingClass did not find unwanted class 'org.aspectj.weaver.Advice' (OnClassCondition)
              - @ConditionalOnProperty (spring.aop.proxy-target-class=true) matched (OnPropertyCondition)


        2025-07-30 12:49:20.557 [scheduling-1] INFO  com.cleanbuild.tech.monolit.config.SchedulerConfig - Scheduled SSH log watcher processing started
        2025-07-30 12:49:20.558 [scheduling-1] INFO  com.cleanbuild.tech.monolit.service.SSHLogWatcherService - Starting SSH log watcher processing
        2025-07-30 12:49:20.560 [main] INFO  com.cleanbuild.tech.monolit.MainKt - Started MainKt in 7.248 seconds (process running for 9.521)
    """.trimIndent()

    @BeforeEach
    fun setup() {
        // Create a custom index directory path inside the temp directory
        indexDir = tempDir.resolve("lucene-indexes")

        // Set system property to override the default index directory
        System.setProperty("lucene.index.dir", indexDir.toString())

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

        // Reset system property
        System.clearProperty("lucene.index.dir")
    }

    @Test
    fun `verify log line processing with sample logs`() {
        // Create test data
        val testRecord = createTestRecord(1L, "NEW")
        val testWatcher = createTestWatcher()
        val testConfig = createTestConfig()
        
        // Expected number of log entries in our sample content
        // We have 8 log entries with timestamps:
        // 1. 2025-07-30 12:49:20.168 [main] WARN ...
        // 2. 2025-07-30 12:49:20.211 [main] DEBUG ...
        // 3. 2025-07-30 12:49:20.464 [main] INFO ...
        // 4. 2025-07-30 12:49:20.505 [main] INFO ...
        // 5. 2025-07-30 12:49:20.543 [main] DEBUG ... (multi-line log with CONDITIONS EVALUATION REPORT)
        // 6. 2025-07-30 12:49:20.557 [scheduling-1] INFO ...
        // 7. 2025-07-30 12:49:20.558 [scheduling-1] INFO ...
        // 8. 2025-07-30 12:49:20.560 [main] INFO ...
        val expectedLogEntries = 8
        
        // Setup mocks
        whenever(sshLogWatcherRecordCrud.findByColumnValues(mapOf(SSHLogWatcherRecord::consumptionStatus to "NEW")))
            .thenReturn(listOf(testRecord))
        whenever(sshLogWatcherCrud.findByPrimaryKey(testWatcherName)).thenReturn(testWatcher)
        whenever(sshConfigCrud.findByPrimaryKey(testConfigName)).thenReturn(testConfig)
        whenever(sshCommandRunner.getFileStream(eq(testConfig), eq(testFilePath))).thenReturn(
            ByteArrayInputStream(sampleLogContent.toByteArray())
        )
        
        // Execute the method under test
        service.ingestRecords()
        
        // Verify the index directory was created for the watcher
        val watcherIndexDir = indexDir.resolve(testWatcherName)
        assert(Files.exists(watcherIndexDir)) { "Watcher index directory was not created" }
        
        // Open the index and count the documents
        val reader: IndexReader = DirectoryReader.open(FSDirectory.open(watcherIndexDir))
        val searcher = IndexSearcher(reader)
        val query = MatchAllDocsQuery()
        val topDocs: TopDocs = searcher.search(query, Integer.MAX_VALUE)
        
        println("[DEBUG_LOG] Found ${topDocs.totalHits.value} documents in the index")
        
        // Verify we have the expected number of documents
        assert(topDocs.totalHits.value == expectedLogEntries.toLong()) { 
            "Expected $expectedLogEntries documents, but found ${topDocs.totalHits.value}" 
        }
        
        // Print out the first few documents to verify their content
        for (i in 0 until minOf(3, topDocs.scoreDocs.size)) {
            val docId = topDocs.scoreDocs[i].doc
            val document: Document = searcher.doc(docId)
            println("[DEBUG_LOG] Document ${i+1}:")
            println("[DEBUG_LOG] ID: ${document.get("id")}")
            println("[DEBUG_LOG] Timestamp: ${document.get("logTimestampString")}")
            println("[DEBUG_LOG] Timestamp (long): ${document.get("logTimestamp")}")
            println("[DEBUG_LOG] Content: ${document.get("content").take(100)}...")
            println()
        }
        
        // Verify that multi-line logs are handled correctly
        // Find the document with the CONDITIONS EVALUATION REPORT
        var foundMultiLineLog = false
        for (i in 0 until topDocs.scoreDocs.size) {
            val docId = topDocs.scoreDocs[i].doc
            val document: Document = searcher.doc(docId)
            val content = document.get("content")
            if (content.contains("CONDITIONS EVALUATION REPORT")) {
                foundMultiLineLog = true
                println("[DEBUG_LOG] Found multi-line log document:")
                println("[DEBUG_LOG] ID: ${document.get("id")}")
                println("[DEBUG_LOG] Content length: ${content.length}")
                println("[DEBUG_LOG] First line: ${content.lines().first()}")
                println("[DEBUG_LOG] Last line: ${content.lines().last()}")
                break
            }
        }
        
        assert(foundMultiLineLog) { "Multi-line log document not found" }
        
        // Close the reader
        reader.close()
    }

    // Helper methods to create test data

    private fun createTestRecord(id: Long, status: String, watcherName: String = testWatcherName): SSHLogWatcherRecord {
        return SSHLogWatcherRecord(
            id = id,
            sshLogWatcherName = watcherName,
            fullFilePath = testFilePath,
            fileSize = 1024L,
            cTime = Timestamp(System.currentTimeMillis()),
            fileHash = "sample-log-hash",
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