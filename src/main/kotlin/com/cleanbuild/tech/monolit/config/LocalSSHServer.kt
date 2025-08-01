package com.cleanbuild.tech.monolit.config

import org.apache.sshd.server.Environment
import org.apache.sshd.server.ExitCallback
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.server.channel.ChannelSession
import org.apache.sshd.server.command.Command
import org.apache.sshd.server.command.CommandFactory
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.server.shell.ProcessShellFactory
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path

/**
 * A configurable SSH server for localhost that can be used in both production and testing environments.
 */
@Configuration
open class LocalSSHServer(
    private val port: Int = 22,
    private val hostKeyFilePath: String = "ssh-hostkey.ser",
    private val password: String = "pass"
) {
    private val logger = LoggerFactory.getLogger(LocalSSHServer::class.java)

    /**
     * Starts a localhost SSH server with the configured parameters.
     * @return The started SSH server instance
     */
    @Bean
    open fun startServer(): SshServer {
        val sshServer = SshServer.setUpDefaultServer()
        sshServer.port = port

        val hostKeyFile = File(hostKeyFilePath)
        sshServer.keyPairProvider = SimpleGeneratorHostKeyProvider(hostKeyFile.toPath())

        sshServer.passwordAuthenticator = PasswordAuthenticator { username, password, session ->
            return@PasswordAuthenticator password == this.password
        }

        // Set up shell factory based on OS
        sshServer.shellFactory = if (System.getProperty("os.name").lowercase().contains("windows")) {
            // Windows-specific shell factory
            ProcessShellFactory(
                "C:\\Program Files\\Git\\bin\\bash.exe", "--login", "-i"
            )
        } else {
            // Unix-like shell factory
            ProcessShellFactory(
                "/bin/sh", "-i", "-l"
            )
        }
        
        // Use custom command factory if provided, otherwise use default BashCommandFactory
        sshServer.commandFactory = BashCommandFactory()

        // Start the server
        sshServer.start()
        logger.info("SSH server started on localhost:${sshServer.port}")
        return sshServer
    }
}

class BashCommandFactory : CommandFactory {
    override fun createCommand(channel: ChannelSession, command: String): Command {
        return BashProcessCommand(command)
    }
}

class BashProcessCommand(
    private val command: String,
    private var inStream: InputStream? = null,
    private var outStream: OutputStream? = null,
    private var errStream: OutputStream? = null,
    private var exitCallback: ExitCallback? = null,
    private var process: Process? = null,
    private var thread: Thread? = null
) : Command {
    private val logger = LoggerFactory.getLogger(BashProcessCommand::class.java)

    override fun setInputStream(`in`: InputStream?) {
        this.inStream = `in`
    }

    override fun setOutputStream(out: OutputStream?) {
        this.outStream = out
    }

    override fun setErrorStream(err: OutputStream?) {
        this.errStream = err
    }

    override fun setExitCallback(callback: ExitCallback?) {
        this.exitCallback = callback
    }

    override fun start(channel: ChannelSession?, env: Environment?) {
        thread = Thread {
            try {
                val bashCommand = command.replace("\"", "\\\"")
                val bashPath = "\"C:\\Program Files\\Git\\bin\\bash.exe\""

                logger.debug("Executing command via bash.exe: {}", bashCommand)

                val processBuilder = ProcessBuilder(bashPath, "-c" , bashCommand)
                process = processBuilder.start()

                val outputThread = Thread {
                    process?.inputStream?.use { input ->
                        outStream?.let { out ->
                            input.copyTo(out)
                            out.flush()
                        }
                    }
                }

                val errorThread = Thread {
                    process?.errorStream?.use { error ->
                        errStream?.let { err ->
                            error.copyTo(err)
                            err.flush()
                        }
                    }
                }

                outputThread.start()
                errorThread.start()

                outputThread.join()
                errorThread.join()

                process?.outputStream?.close()

                val exitCode = process?.waitFor() ?: -1
                exitCallback?.onExit(exitCode)

            } catch (e: Exception) {
                logger.error("Error executing command: {}", e.message)
                errStream?.write((e.message ?: "Unknown error").toByteArray())
                errStream?.flush()
                exitCallback?.onExit(-1, e.message)
            }
        }
        thread?.start()
//        thread?.join()
//        process?.destroy()
//        channel?.close()
        logger.info("Command process exited successfully.")
    }

    override fun destroy(channel: ChannelSession?) {
        try {
            Thread.sleep(100)
            if (process?.isAlive == true) {
                logger.info("Process still alive, forcing termination.")
                process?.destroyForcibly()
            }
            thread?.interrupt()
            inStream?.close()
            outStream?.close()
            errStream?.close()
            channel?.close()
            logger.info("Command process destroyed successfully.")
        } catch (e: Exception) {
            logger.error("Error destroying command process.", e)
        }
    }
}