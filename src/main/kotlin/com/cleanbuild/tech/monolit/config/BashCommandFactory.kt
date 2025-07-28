package com.cleanbuild.tech.monolit.config

import org.apache.sshd.server.Environment
import org.apache.sshd.server.ExitCallback
import org.apache.sshd.server.channel.ChannelSession
import org.apache.sshd.server.command.Command
import org.apache.sshd.server.command.CommandFactory
import org.apache.sshd.server.command.CommandLifecycle
import org.apache.sshd.server.command.CommandDirectStreamsAware
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.io.OutputStream

class BashCommandFactory : CommandFactory {
    override fun createCommand(channel: ChannelSession, command: String): Command {
        return BashProcessCommand(command)
    }
}

class BashProcessCommand(private val command: String) : Command, CommandLifecycle, CommandDirectStreamsAware {
    private val logger = LoggerFactory.getLogger(BashProcessCommand::class.java)
    private var inStream: InputStream? = null
    private var outStream: OutputStream? = null
    private var errStream: OutputStream? = null
    private var exitCallback: ExitCallback? = null
    private var process: Process? = null
    private var thread: Thread? = null

    override fun setInputStream(`in`: InputStream?) {
        inStream = `in`
    }

    override fun setOutputStream(out: OutputStream?) {
        outStream = out
    }

    override fun setErrorStream(err: OutputStream?) {
        errStream = err
    }

    override fun setExitCallback(callback: ExitCallback?) {
        exitCallback = callback
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
                    process?.inputStream?.copyTo(outStream ?: System.out)
                    outStream?.flush()
                }

                val errorThread = Thread {
                    process?.errorStream?.copyTo(errStream ?: System.err)
                    errStream?.flush()
                }

                val inputThread = Thread {
                    inStream?.copyTo(process?.outputStream ?: return@Thread)
                    process?.outputStream?.flush()
                }

                outputThread.start()
                errorThread.start()
                inputThread.start()

                outputThread.join()
                errorThread.join()
                inputThread.join()

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
    }

    override fun destroy(channel: ChannelSession?) {
        try {
            logger.debug("Destroying command process")
            process?.destroy()
            Thread.sleep(100)
            if (process?.isAlive == true) {
                logger.debug("Process still alive, forcing termination")
                process?.destroyForcibly()
            }
            thread?.interrupt()
        } catch (e: Exception) {
            logger.error("Error destroying command process", e)
        }
    }
}
