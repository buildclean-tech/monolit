# SSH Server Configuration DAO Documentation

This document provides an overview of the SSH Server Configuration DAO implementation.

## Overview

The SSH Server Configuration DAO (Data Access Object) provides a way to store, retrieve, and manage SSH server connection configurations in the database. It includes:

1. An entity class representing SSH server configuration data
2. A repository interface for database operations
3. A service class for business logic and transaction management
4. A configuration initializer to ensure default values are available

## Components

### Entity

`SshServerConfig` (in `com.cleanbuild.tech.monolit.model`)

- Represents SSH server configuration data
- Contains fields for:
  - `id`: Unique identifier
  - `serverHost`: SSH server hostname or IP address
  - `port`: SSH server port
  - `password`: SSH server password
  - `createdAt`: Timestamp when the configuration was created
  - `updatedAt`: Timestamp when the configuration was last updated
- Includes JPA annotations for database mapping
- Provides a no-args constructor required by JPA
- Automatically updates the `updatedAt` timestamp when changes are persisted

### Repository

`SshServerConfigRepository` (in `com.cleanbuild.tech.monolit.repository`)

- Extends Spring Data JPA's `JpaRepository`
- Provides standard CRUD operations for SSH server configurations
- Includes custom query methods:
  - `findByServerHost`: Find configuration by server host
  - `findByServerHostAndPort`: Find configuration by server host and port
  - `existsByServerHost`: Check if a configuration exists for a server host

### Service

`SshServerConfigService` (in `com.cleanbuild.tech.monolit.service`)

- Provides business logic and transaction management
- Includes methods for:
  - Finding all configurations
  - Finding configuration by ID
  - Finding configuration by server host
  - Finding configuration by server host and port
  - Saving or updating a configuration
  - Deleting a configuration by ID
  - Checking if a configuration exists for a server host
- Validates configuration data before saving
- Uses `@Transactional` annotations for transaction management

### Configuration Initializer

`SshServerConfigInitializer` (in `com.cleanbuild.tech.monolit.config`)

- Loads default SSH server configuration properties from `application.properties`
- Creates a default configuration in the database if none exists
- Uses a `CommandLineRunner` to initialize the configuration after the application context is loaded
- Logs information about the initialization process

## Default Configuration

Default SSH server configuration properties are defined in `application.properties`:

```properties
ssh.server.host=localhost
ssh.server.port=22
ssh.server.password=password
```

These properties are used to initialize a default configuration if none exists in the database.

## Usage

To use the SSH Server Configuration DAO in your application:

1. Inject the `SshServerConfigService` into your component:

```kotlin
@Autowired
private lateinit var sshServerConfigService: SshServerConfigService
```

2. Use the service to retrieve or manage SSH server configurations:

```kotlin
// Get all configurations
val configs = sshServerConfigService.findAll()

// Get configuration by ID
val config = sshServerConfigService.findById(1L).orElse(null)

// Get configuration by server host
val config = sshServerConfigService.findByServerHost("example.com").orElse(null)

// Create or update a configuration
val newConfig = SshServerConfig(
    serverHost = "example.com",
    port = 22,
    password = "secret"
)
val savedConfig = sshServerConfigService.save(newConfig)

// Delete a configuration
sshServerConfigService.deleteById(1L)
```

## Database Schema

The SSH server configuration is stored in the `ssh_server_config` table with the following columns:

- `id`: BIGINT (Primary Key, Auto-increment)
- `server_host`: VARCHAR (Not Null)
- `port`: INT (Not Null)
- `password`: VARCHAR (Not Null)
- `created_at`: TIMESTAMP (Not Null)
- `updated_at`: TIMESTAMP (Nullable)