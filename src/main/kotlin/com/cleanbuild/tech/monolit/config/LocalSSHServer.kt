package com.cleanbuild.tech.monolit.com.cleanbuild.tech.monolit.config

import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.server.shell.ProcessShellFactory
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.File
import com.cleanbuild.tech.monolit.config.BashCommandFactory

/**

 */
@Configuration
open class LocalSSHServer() {
    private val logger = LoggerFactory.getLogger(LocalSSHServer::class.java)
    private var sshServer: SshServer? = null
    /**
     * Starts a localhost SSH server with the specified configuration.
     *
     * @param configName The name of the SSH configuration to use
     * @return True if the server was started successfully, false otherwise
     */
    @Bean
    open fun startServer(): SshServer {
        // Check if server is already running
        if (sshServer != null && sshServer!!.isStarted) {
            logger.warn("SSH server is already running")
            return sshServer!!
        }
        // Create and configure SSH server
        sshServer = SshServer.setUpDefaultServer()
        sshServer!!.port = 22

        // Set up host key
        val hostKeyFile = File("ssh-hostkey.ser")
        sshServer!!.keyPairProvider = SimpleGeneratorHostKeyProvider(hostKeyFile.toPath())

        // Set up password authentication
        sshServer!!.passwordAuthenticator = PasswordAuthenticator { username, password, session ->
            // For test server, accept any username with the configured password
            password == "password123"
        }

        // Set up shell factory (use Git Bash with interactive mode)
        sshServer!!.shellFactory = ProcessShellFactory(
            "C:\\Program Files\\Git\\bin\\bash.exe", "--login", "-i"
        )
        
        // Set up command factory for direct command execution (use Git Bash)
        sshServer!!.commandFactory = BashCommandFactory()

        // Start the server
        sshServer!!.start()
        logger.info("SSH server started on localhost:${sshServer!!.port}")
        return sshServer!!

    }

}