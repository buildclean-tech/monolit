# Localhost SSH Test Server

This document provides instructions on how to use the localhost SSH test server implemented in this project. The test server is designed for development and testing purposes, allowing you to test SSH-related functionality without connecting to external servers.

## Overview

The localhost SSH test server is implemented using Apache MINA SSHD and provides:

- A configurable SSH server running on localhost
- Password-based authentication
- Shell access using the system shell
- Direct command execution support (e.g., `ssh username@localhost -p 2222 ls -al`)
- REST API for managing the server and configurations

## Configuration

SSH server configurations are stored in the database and can be managed through the REST API. Each configuration includes:

- `name`: A unique identifier for the configuration
- `serverHost`: The hostname (always "localhost" for the test server)
- `port`: The port number to listen on (default: 2222)
- `password`: The password for authentication

A default configuration named "localhost-test" is automatically created when needed.

## REST API Endpoints

The following REST API endpoints are available for managing the SSH server:

### Server Management

- `POST /api/ssh/start`: Start the SSH server
  - Optional query parameter: `configName` (uses default if not provided)
  - Returns: Status and configuration name

- `POST /api/ssh/stop`: Stop the SSH server
  - Returns: Status and message

- `GET /api/ssh/status`: Check if the server is running
  - Returns: Status, running state, and message

### Configuration Management

- `POST /api/ssh/config`: Create a new SSH configuration
  - Request body: JSON representation of SSHConfig
  - Returns: Status, message, and created configuration

- `GET /api/ssh/config`: Get all SSH configurations
  - Returns: Status and list of configurations

- `GET /api/ssh/config/{name}`: Get a specific SSH configuration by name
  - Returns: Status and configuration details

- `POST /api/ssh/config/default`: Create the default localhost SSH configuration
  - Returns: Status, message, and configuration details

## Usage Examples

### Starting the SSH Server

To start the SSH server with the default configuration:

```bash
curl -X POST http://localhost:8080/api/ssh/start
```

To start the SSH server with a specific configuration:

```bash
curl -X POST "http://localhost:8080/api/ssh/start?configName=my-config"
```

### Stopping the SSH Server

```bash
curl -X POST http://localhost:8080/api/ssh/stop
```

### Checking Server Status

```bash
curl -X GET http://localhost:8080/api/ssh/status
```

### Creating a Custom Configuration

```bash
curl -X POST http://localhost:8080/api/ssh/config \
  -H "Content-Type: application/json" \
  -d '{
    "name": "custom-config",
    "serverHost": "localhost",
    "port": 2223,
    "password": "custompassword"
  }'
```

### Connecting to the SSH Server

Once the server is running, you can connect to it using any SSH client:

```bash
ssh -p 2222 username@localhost
```

Replace `username` with any username (the server only checks the password) and `2222` with the configured port number.

### Executing Commands Directly

You can also execute commands directly without starting an interactive shell session:

```bash
ssh -p 2222 username@localhost ls -al
```

This will execute the command on the server and return the results to your terminal.

## Programmatic Usage

You can also use the `SSHServerService` directly in your code:

```kotlin
@Autowired
private lateinit var sshServerService: SSHServerService

// Create default configuration
val configName = sshServerService.createDefaultConfig()

// Start the server
sshServerService.startServer(configName)

// Check if server is running
val isRunning = sshServerService.isServerRunning()

// Stop the server
sshServerService.stopServer()
```

## Security Considerations

The test SSH server is intended for development and testing purposes only. It should not be used in production environments for the following reasons:

1. It uses a simple password authentication mechanism
2. It accepts any username with the configured password
3. It uses a generated host key that changes on restart
4. It provides full shell access to the system

Always ensure that the test server is only accessible from localhost and is properly secured in development environments.