package com.cleanbuild.tech.monolit.ssh

import com.cleanbuild.tech.monolit.DbRecord.SSHConfig
import org.apache.sshd.client.channel.ClientChannel
import org.apache.sshd.client.channel.ClientChannelEvent
import org.apache.sshd.client.session.ClientSession
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.util.EnumSet
import java.util.concurrent.TimeUnit

/**
 * A class for executing file-related commands over SSH.
 *
 * This class provides functionality to:
 * 1. Find files matching a pattern in a directory with a specified recursion depth
 * 2. Stream the contents of a file
 */
@Component
class SSHCommandRunner(private val sshSessionFactory: SSHSessionFactory) {
    private val logger = LoggerFactory.getLogger(SSHCommandRunner::class.java)

    /**
     * Data class representing file metadata
     *
     * @property size The size of the file in bytes
     * @property ctime The creation time of the file as Unix timestamp
     * @property filename The name of the file
     * @property filepath The full path to the file
     */
    data class FileMetadata(
        val size: Long,
        val ctime: Long,
        val filename: String,
        val filepath: String
    ) {
        val creationTime: Instant
            get() = Instant.ofEpochSecond(ctime)
    }

    /**
     * Finds files matching a pattern in a directory with a specified recursion depth.
     *
     * @param sshConfig The SSH configuration to use for the connection
     * @param directory The directory to search in
     * @param pattern The file pattern to match (glob pattern)
     * @param maxDepth The maximum recursion depth (default is 1, meaning no recursion)
     * @return A list of FileMetadata objects for the matching files
     * @throws IOException If there's an error executing the command
     */
    @Throws(IOException::class)
    fun findFiles(
        sshConfig: SSHConfig,
        directory: String,
        pattern: String,
        maxDepth: Int = 1
    ): List<FileMetadata> {
        // Validate inputs
        require(directory.isNotBlank()) { "Directory cannot be blank" }
        require(pattern.isNotBlank()) { "Pattern cannot be blank" }
        require(maxDepth >= 1) { "Max depth must be at least 1" }

        // Sanitize directory path
        val sanitizedDirectory = directory.trim().let {
            if (it.endsWith("/")) it else "$it/"
        }

        // Build the find command
        // Using -printf to format the output with fields separated by a special delimiter (|||)
        // %s = size in bytes, %T@ = modification time in seconds since Jan 1, 1970, %f = filename, %p = full path
        val command = """
            find "${sanitizedDirectory.replace("\"", "\\\"")}" -maxdepth $maxDepth -type f -name "${pattern.replace("\"", "\\\"")}" -printf "%s|||%C@|||%f|||%p\n"
        """.trimIndent()

        logger.debug("Executing find command: $command")

        // Execute the command
        val result = executeCommand(sshConfig, command)

        // Parse the result
        return result.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                try {
                    val parts = line.split("|||")
                    if (parts.size >= 4) {
                        FileMetadata(
                            size = parts[0].toLongOrNull() ?: 0,
                            ctime = (parts[1].toDoubleOrNull()?.toLong() ?: 0)*1000L,
                            filename = parts[2],
                            filepath = parts[3]
                        )
                    } else {
                        logger.warn("Invalid line format: $line")
                        null
                    }
                } catch (e: Exception) {
                    logger.error("Error parsing line: $line", e)
                    null
                }
            }
            .toList()
    }

    /**
     * Gets an input stream for a file.
     *
     * @param sshConfig The SSH configuration to use for the connection
     * @param filepath The full path to the file
     * @return An InputStream for reading the file contents
     * @throws IOException If there's an error executing the command
     */
    @Throws(IOException::class)
    fun getFileStream(sshConfig: SSHConfig, filepath: String): InputStream {
        // Validate inputs
        require(filepath.isNotBlank()) { "Filepath cannot be blank" }

        // Get an SSH session
        val session = sshSessionFactory.getSession(sshConfig)

        try {
            // Create a command to cat the file
            val command = "cat \"${filepath.replace("\"", "\\\"")}\""
            val channel = session.createExecChannel(command)
            
            // Open the channel
            val openFuture = channel.open()
            if (!openFuture.await(COMMAND_TIMEOUT, TimeUnit.SECONDS)) {
                throw IOException("Timeout while opening channel for file: $filepath")
            }
            
            if (!openFuture.isOpened) {
                throw IOException("Failed to open channel for file: $filepath")
            }
            
            // Return the input stream
            // Note: The caller is responsible for closing this stream
            return FileStreamWrapper(channel)
        } catch (e: Exception) {
            logger.error("Error getting file stream for $filepath", e)
            throw IOException("Failed to get file stream: ${e.message}", e)
        }
    }

    /**
     * Executes a command over SSH and returns the output.
     *
     * @param sshConfig The SSH configuration to use for the connection
     * @param command The command to execute
     * @return The command output as a string
     * @throws IOException If there's an error executing the command
     */
    @Throws(IOException::class)
    private fun executeCommand(sshConfig: SSHConfig, command: String): String {
        // Get an SSH session
        val session = sshSessionFactory.getSession(sshConfig)

        try {
            // Create a command channel
            val channel = session.createExecChannel(command)
            
            // Open the channel
            val openFuture = channel.open()
            if (!openFuture.await(COMMAND_TIMEOUT, TimeUnit.SECONDS)) {
                throw IOException("Timeout while executing command: $command")
            }
            
            if (!openFuture.isOpened) {
                throw IOException("Failed to open channel for command: $command")
            }
            
            // Read the command output
            val outputStream = channel.getInvertedOut()
            val errorStream = channel.getInvertedErr()
            val buffer = ByteArray(8192)
            val output = StringBuilder()
            val errorOutput = StringBuilder()
            
            // Read standard output
            var bytesRead: Int
            while (outputStream.read(buffer).also { bytesRead = it } != -1) {
                output.append(String(buffer, 0, bytesRead))
            }
            
            // Read error output
            while (errorStream.read(buffer).also { bytesRead = it } != -1) {
                errorOutput.append(String(buffer, 0, bytesRead))
            }
            
            // Wait for command to complete
            val events = channel.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), COMMAND_TIMEOUT * 1000)
            
            // Check for timeout
            if (!events.contains(ClientChannelEvent.CLOSED)) {
                throw IOException("Command execution timed out: $command")
            }
            
            // Check exit status
            val exitStatus = channel.exitStatus
            if (exitStatus != null && exitStatus != 0) {
                val errorMsg = errorOutput.toString().trim()
                val errorDetails = if (errorMsg.isNotEmpty()) ": $errorMsg" else ""
                throw IOException("Command execution failed with exit status $exitStatus: $command$errorDetails")
            }
            
            // Close the channel
            channel.close(false)
            
            return output.toString()
        } catch (e: Exception) {
            logger.error("Error executing command: $command", e)
            throw IOException("Failed to execute command: ${e.message}", e)
        }
    }

    /**
     * A wrapper for the input stream from an SSH channel that closes the channel when the stream is closed.
     */
    private class FileStreamWrapper(private val channel: ClientChannel) : InputStream() {
        private val inputStream = channel.getInvertedOut()

        override fun read(): Int = inputStream.read()

        override fun read(b: ByteArray): Int = inputStream.read(b)

        override fun read(b: ByteArray, off: Int, len: Int): Int = inputStream.read(b, off, len)

        override fun close() {
            try {
                inputStream.close()
            } finally {
                channel.close(false)
            }
        }
    }

    companion object {
        // Timeout value in seconds
        private const val COMMAND_TIMEOUT = 30L
    }
}