package com.cleanbuild.tech.monolit.ssh

import com.cleanbuild.tech.monolit.DbRecord.SSHConfig
import com.cleanbuild.tech.monolit.config.LocalSSHServer
import org.apache.sshd.server.SshServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Timestamp
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SSHCommandRunnerTest {
    private val logger = LoggerFactory.getLogger(SSHCommandRunnerTest::class.java)
    
    // Use a non-privileged port for testing
    private val TEST_SSH_PORT = 2223
    
    private lateinit var sshServer: SshServer
    private lateinit var sshSessionFactory: SSHSessionFactory
    private lateinit var sshCommandRunner: SSHCommandRunner
    private lateinit var testConfig: SSHConfig
    
    // Test directory structure
    @TempDir
    lateinit var tempDir: Path
    private lateinit var testDir: Path
    private lateinit var subDir1: Path
    private lateinit var subDir2: Path
    
    // Test files
    private lateinit var testFile1: Path
    private lateinit var testFile2: Path
    private lateinit var testFileWithSpaces: Path
    private lateinit var testFileInSubDir: Path
    
    @BeforeAll
    fun setupServer() {
        // Create and start a local SSH server for testing
        val localSSHServer = LocalSSHServer(
            port = TEST_SSH_PORT,
            password = "testpass"
        )
        
        // Start the server
        sshServer = localSSHServer.startServer()
        logger.info("Test SSH server started on localhost:{}", TEST_SSH_PORT)
        
        // Create the SSH session factory
        sshSessionFactory = SSHSessionFactory()
        
        // Create the command runner
        sshCommandRunner = SSHCommandRunner(sshSessionFactory)
        
        // Create test directory structure
        setupTestDirectories()
        
        // Create test files
        createTestFiles()
    }
    
    @AfterAll
    fun tearDownServer() {
        // Stop the SSH server
        sshServer.stop(true)
        logger.info("Test SSH server stopped")
        
        // Close the SSH session factory
        sshSessionFactory.close()
    }
    
    @BeforeEach
    fun setUp() {
        // Create a test SSH config that points to our test server
        testConfig = SSHConfig(
            name = "test-config",
            serverHost = "localhost",
            port = TEST_SSH_PORT,
            username = "testuser",
            password = "testpass",
            createdAt = Timestamp(System.currentTimeMillis()),
            updatedAt = Timestamp(System.currentTimeMillis())
        )
    }
    
    private fun setupTestDirectories() {
        // Create test directory structure
        testDir = tempDir.resolve("test-dir")
        subDir1 = testDir.resolve("subdir1")
        subDir2 = testDir.resolve("subdir2")
        
        Files.createDirectories(testDir)
        Files.createDirectories(subDir1)
        Files.createDirectories(subDir2)
        
        logger.info("Created test directories: {}, {}, {}", testDir, subDir1, subDir2)
    }
    
    private fun createTestFiles() {
        // Create test files
        testFile1 = testDir.resolve("file1.txt")
        testFile2 = testDir.resolve("file2.txt")
        testFileWithSpaces = testDir.resolve("file with spaces.txt")
        testFileInSubDir = subDir1.resolve("file3.txt")
        
        // Write content to files
        Files.write(testFile1, "This is test file 1".toByteArray())
        Files.write(testFile2, "This is test file 2".toByteArray())
        Files.write(testFileWithSpaces, "This is a test file with spaces in the name".toByteArray())
        Files.write(testFileInSubDir, "This is test file 3 in a subdirectory".toByteArray())
        
        logger.info("Created test files: {}, {}, {}, {}", testFile1, testFile2, testFileWithSpaces, testFileInSubDir)
    }
    
    /**
     * Helper method to convert Windows path to Unix path for SSH commands
     */
    private fun toUnixPath(path: Path): String {
        // Convert Windows path (C:\path\to\file) to Unix path (/path/to/file)
        return path.toString().replace("\\", "/")
    }
    
    @Test
    fun `findFiles should find files matching pattern`() {
        // Arrange
        val testDirPath = toUnixPath(testDir)
        
        // Act
        val files = sshCommandRunner.findFiles(
            sshConfig = testConfig,
            directory = testDirPath,
            pattern = "*.txt",
            maxDepth = 1
        )
        
        // Assert
        assertEquals(3, files.size, "Should find 3 .txt files in the root directory")
        
        // Verify file metadata
        files.forEach { file ->
            assertTrue(file.size > 0, "File size should be greater than 0")
            assertTrue(file.ctime > 0, "Creation time should be greater than 0")
            assertTrue(file.filename.endsWith(".txt"), "Filename should end with .txt")
            assertNotNull(file.filepath, "Filepath should not be null")
        }
        
        // Verify specific files are found
        val filenames = files.map { it.filename }.toSet()
        assertTrue(filenames.contains("file1.txt"), "Should find file1.txt")
        assertTrue(filenames.contains("file2.txt"), "Should find file2.txt")
        assertTrue(filenames.contains("file with spaces.txt"), "Should find 'file with spaces.txt'")
    }
    
    @Test
    fun `findFiles should respect maxDepth parameter`() {
        // Arrange
        val testDirPath = toUnixPath(testDir)
        
        // Act - with maxDepth=1 (no recursion)
        val filesNoRecursion = sshCommandRunner.findFiles(
            sshConfig = testConfig,
            directory = testDirPath,
            pattern = "*.txt",
            maxDepth = 1
        )
        
        // Act - with maxDepth=2 (one level of recursion)
        val filesWithRecursion = sshCommandRunner.findFiles(
            sshConfig = testConfig,
            directory = testDirPath,
            pattern = "*.txt",
            maxDepth = 2
        )
        
        // Assert
        assertEquals(3, filesNoRecursion.size, "Should find 3 .txt files in the root directory")
        assertEquals(4, filesWithRecursion.size, "Should find 4 .txt files including subdirectory")
        
        // Verify file in subdirectory is found with recursion
        val filepaths = filesWithRecursion.map { it.filepath }.toSet()
        val subDirFilePath = toUnixPath(testFileInSubDir)
        assertTrue(filepaths.any { it.contains("subdir1/file3.txt") }, 
            "Should find file in subdirectory with recursion")
    }
    
    @Test
    fun `findFiles should handle specific filename patterns`() {
        // Arrange
        val testDirPath = toUnixPath(testDir)
        
        // Act - find only file1.txt
        val file1Results = sshCommandRunner.findFiles(
            sshConfig = testConfig,
            directory = testDirPath,
            pattern = "file1.txt",
            maxDepth = 2
        )
        
        // Act - find files with spaces
        val spacesResults = sshCommandRunner.findFiles(
            sshConfig = testConfig,
            directory = testDirPath,
            pattern = "*spaces*",
            maxDepth = 2
        )
        
        // Assert
        assertEquals(1, file1Results.size, "Should find exactly 1 file matching file1.txt")
        assertEquals("file1.txt", file1Results[0].filename, "Should find file1.txt")
        
        assertEquals(1, spacesResults.size, "Should find exactly 1 file with 'spaces' in the name")
        assertEquals("file with spaces.txt", spacesResults[0].filename, "Should find 'file with spaces.txt'")
    }
    
    @Test
    fun `findFiles should handle empty result`() {
        // Arrange
        val testDirPath = toUnixPath(testDir)
        
        // Act
        val files = sshCommandRunner.findFiles(
            sshConfig = testConfig,
            directory = testDirPath,
            pattern = "*.nonexistent",
            maxDepth = 2
        )
        
        // Assert
        assertTrue(files.isEmpty(), "Should return empty list when no files match")
    }
    
    @Test
    fun `findFiles should throw exception for nonexistent directory`() {
        // Act & Assert
        val exception = org.junit.jupiter.api.assertThrows<IOException> {
            sshCommandRunner.findFiles(
                sshConfig = testConfig,
                directory = "/nonexistent/dir",
                pattern = "*.txt",
                maxDepth = 1
            )
        }
        
        // Verify the exception message contains error information
        assertTrue(exception.message?.contains("Failed to execute command") == true ||
                  exception.message?.contains("Command execution failed") == true,
                  "Exception should indicate command execution failure")
    }
    
    @Test
    fun `getFileStream should return content of file`() {
        // Arrange
        val testFilePath = toUnixPath(testFile1)
        val expectedContent = "This is test file 1"
        
        // Act
        val inputStream = sshCommandRunner.getFileStream(
            sshConfig = testConfig,
            filepath = testFilePath
        )
        
        // Assert
        val content = inputStream.bufferedReader().use { it.readText() }
        assertEquals(expectedContent, content, "File content should match")
    }
    
    @Test
    fun `getFileStream should handle files with spaces in name`() {
        // Arrange
        val testFilePath = toUnixPath(testFileWithSpaces)
        val expectedContent = "This is a test file with spaces in the name"
        
        // Act
        val inputStream = sshCommandRunner.getFileStream(
            sshConfig = testConfig,
            filepath = testFilePath
        )
        
        // Assert
        val content = inputStream.bufferedReader().use { it.readText() }
        assertEquals(expectedContent, content, "File content should match")
    }
    
    @Test
    fun `getFileStream should throw exception for nonexistent file`() {
        // Arrange
        val nonexistentFilePath = toUnixPath(testDir) + "/nonexistent.txt"
        
        // Act & Assert
        val exception = org.junit.jupiter.api.assertThrows<IOException> {
            sshCommandRunner.getFileStream(
                sshConfig = testConfig,
                filepath = nonexistentFilePath
            )
        }
        
        // Verify the exception message contains error information
        assertTrue(exception.message?.contains("Failed to get file stream") == true,
                  "Exception should indicate file stream failure")
    }
}