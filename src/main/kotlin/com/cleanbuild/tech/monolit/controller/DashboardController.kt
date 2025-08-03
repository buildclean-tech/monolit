package com.cleanbuild.tech.monolit.controller

import com.cleanbuild.tech.monolit.DbRecord.SSHConfig
import com.cleanbuild.tech.monolit.repository.CRUDOperation
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.sql.Timestamp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.sql.DataSource

@RestController
@RequestMapping("/dashboard")
class DashboardController(private val dataSource: DataSource) {

    private val crudOperation = CRUDOperation<SSHConfig>(dataSource, SSHConfig::class)
    
    // Format timestamp for display in HTML
    private fun formatTimestamp(timestamp: Timestamp): String {
        val localDateTime = timestamp.toLocalDateTime()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        return localDateTime.format(formatter)
    }

    @GetMapping(produces = [MediaType.TEXT_HTML_VALUE])
    fun dashboard(): String {
        val sshConfigs = getAllSSHConfigs()
        
        return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>SSH Configs Dashboard</title>
            <style>
                body {
                    font-family: Arial, sans-serif;
                    line-height: 1.6;
                    margin: 0;
                    padding: 20px;
                    color: #333;
                    background-color: #f5f5f5;
                }
                .container {
                    max-width: 1200px;
                    margin: 0 auto;
                    padding: 20px;
                    background-color: #fff;
                    border-radius: 5px;
                    box-shadow: 0 2px 5px rgba(0,0,0,0.1);
                }
                h1 {
                    color: #2c3e50;
                    border-bottom: 2px solid #3498db;
                    padding-bottom: 10px;
                }
                table {
                    width: 100%;
                    border-collapse: collapse;
                    margin-bottom: 20px;
                }
                th, td {
                    padding: 12px 15px;
                    text-align: left;
                    border-bottom: 1px solid #ddd;
                }
                th {
                    background-color: #f2f2f2;
                    font-weight: bold;
                }
                tr:hover {
                    background-color: #f5f5f5;
                }
                .btn {
                    display: inline-block;
                    padding: 8px 12px;
                    background-color: #3498db;
                    color: white;
                    border: none;
                    border-radius: 4px;
                    cursor: pointer;
                    text-decoration: none;
                    font-size: 14px;
                    margin-right: 5px;
                }
                .btn-danger {
                    background-color: #e74c3c;
                }
                .btn-success {
                    background-color: #2ecc71;
                }
                .form-container {
                    background-color: #f9f9f9;
                    padding: 20px;
                    border-radius: 5px;
                    margin-bottom: 20px;
                    border: 1px solid #ddd;
                }
                .form-group {
                    margin-bottom: 15px;
                }
                label {
                    display: block;
                    margin-bottom: 5px;
                    font-weight: bold;
                }
                input[type="text"],
                input[type="number"],
                input[type="password"] {
                    width: 100%;
                    padding: 8px;
                    border: 1px solid #ddd;
                    border-radius: 4px;
                    box-sizing: border-box;
                }
                .hidden {
                    display: none;
                }
                .message {
                    padding: 10px;
                    margin-bottom: 15px;
                    border-radius: 4px;
                }
                .success {
                    background-color: #d4edda;
                    color: #155724;
                    border: 1px solid #c3e6cb;
                }
                .error {
                    background-color: #f8d7da;
                    color: #721c24;
                    border: 1px solid #f5c6cb;
                }
            </style>
        </head>
        <body>
            <div class="container">
                <div class="nav-links" style="margin-bottom: 20px;">
                    <a href="/" style="margin-right: 15px; color: #3498db; text-decoration: none;">Home</a>
                    <a href="/dashboard" style="margin-right: 15px; color: #3498db; text-decoration: none;">SSH Configs</a>
                    <a href="/ssh-log-watcher" style="margin-right: 15px; color: #3498db; text-decoration: none;">SSH Log Watchers</a>
                    <a href="/sshlogwatcher-records" style="margin-right: 15px; color: #3498db; text-decoration: none;">SSH Log Watcher Records</a>
                    <a href="/log-search" style="margin-right: 15px; color: #3498db; text-decoration: none;">Log Search</a>
                </div>
                
                <h1>SSH Configs Dashboard</h1>
                
                <div id="message-container" class="hidden message"></div>
                
                <div class="form-container">
                    <h2 id="form-title">Create New SSH Config</h2>
                    <form id="ssh-form">
                        <input type="hidden" id="form-mode" value="create">
                        
                        <div class="form-group">
                            <label for="name">Name:</label>
                            <input type="text" id="name" name="name" required>
                        </div>
                        
                        <div class="form-group">
                            <label for="serverHost">Server Host:</label>
                            <input type="text" id="serverHost" name="serverHost" required>
                        </div>
                        
                        <div class="form-group">
                            <label for="port">Port:</label>
                            <input type="number" id="port" name="port" value="22" required>
                        </div>
                        
                        <div class="form-group">
                            <label for="username">Username:</label>
                            <input type="text" id="username" name="username" placeholder="Leave blank for default">
                        </div>
                        
                        <div class="form-group">
                            <label for="password">Password:</label>
                            <input type="password" id="password" name="password" required>
                        </div>
                        
                        <button type="submit" class="btn btn-success">Save</button>
                        <button type="button" id="cancel-btn" class="btn" style="display:none;">Cancel</button>
                    </form>
                </div>
                
                <h2>SSH Configs</h2>
                <table id="ssh-table">
                    <thead>
                        <tr>
                            <th>Name</th>
                            <th>Server Host</th>
                            <th>Port</th>
                            <th>Username</th>
                            <th>Created At</th>
                            <th>Updated At</th>
                            <th>Actions</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${sshConfigs.joinToString("") { config ->
                            """
                            <tr data-id="${config.name}">
                                <td>${config.name}</td>
                                <td>${config.serverHost}</td>
                                <td>${config.port}</td>
                                <td>${config.username}</td>
                                <td>${formatTimestamp(config.createdAt)}</td>
                                <td>${formatTimestamp(config.updatedAt)}</td>
                                <td>
                                    <button class="btn edit-btn" data-id="${config.name}">Edit</button>
                                    <button class="btn btn-success copy-btn" data-id="${config.name}">Copy</button>
                                    <button class="btn btn-danger delete-btn" data-id="${config.name}">Delete</button>
                                </td>
                            </tr>
                            """
                        }}
                    </tbody>
                </table>
            </div>
            
            <script>
                document.addEventListener('DOMContentLoaded', function() {
                    const form = document.getElementById('ssh-form');
                    const formMode = document.getElementById('form-mode');
                    const formTitle = document.getElementById('form-title');
                    const cancelBtn = document.getElementById('cancel-btn');
                    const messageContainer = document.getElementById('message-container');
                    
                    // Form submission
                    form.addEventListener('submit', function(e) {
                        e.preventDefault();
                        
                        const sshConfig = {
                            name: document.getElementById('name').value,
                            serverHost: document.getElementById('serverHost').value,
                            port: parseInt(document.getElementById('port').value),
                            username: document.getElementById('username').value,
                            password: document.getElementById('password').value
                        };
                        
                        const mode = formMode.value;
                        
                        if (mode === 'create') {
                            createSSHConfig(sshConfig);
                        } else if (mode === 'update') {
                            updateSSHConfig(sshConfig);
                        }
                    });
                    
                    // Edit button click
                    document.addEventListener('click', function(e) {
                        if (e.target.classList.contains('edit-btn')) {
                            const id = e.target.getAttribute('data-id');
                            editSSHConfig(id);
                        }
                    });
                    
                    // Copy button click
                    document.addEventListener('click', function(e) {
                        if (e.target.classList.contains('copy-btn')) {
                            const id = e.target.getAttribute('data-id');
                            copySSHConfig(id);
                        }
                    });
                    
                    // Delete button click
                    document.addEventListener('click', function(e) {
                        if (e.target.classList.contains('delete-btn')) {
                            const id = e.target.getAttribute('data-id');
                            if (confirm('Are you sure you want to delete this SSH config?')) {
                                deleteSSHConfig(id);
                            }
                        }
                    });
                    
                    // Cancel button click
                    cancelBtn.addEventListener('click', function() {
                        resetForm();
                    });
                    
                    // Create SSH Config
                    function createSSHConfig(sshConfig) {
                        fetch('/dashboard/ssh-configs', {
                            method: 'POST',
                            headers: {
                                'Content-Type': 'application/json'
                            },
                            body: JSON.stringify(sshConfig)
                        })
                        .then(response => {
                            // Always parse the JSON, even for error responses
                            return response.json().then(data => {
                                if (!response.ok) {
                                    // If the server returned an error object with an error message, use it
                                    if (data && data.error) {
                                        throw new Error(data.error);
                                    }
                                    // Otherwise use a generic error message
                                    throw new Error('Failed to create SSH config');
                                }
                                return data;
                            });
                        })
                        .then(data => {
                            showMessage('SSH config created successfully!', 'success');
                            resetForm();
                            window.location.reload();
                        })
                        .catch(error => {
                            showMessage(error.message, 'error');
                        });
                    }
                    
                    // Update SSH Config
                    function updateSSHConfig(sshConfig) {
                        fetch('/dashboard/ssh-configs/' + sshConfig.name, {
                            method: 'PUT',
                            headers: {
                                'Content-Type': 'application/json'
                            },
                            body: JSON.stringify(sshConfig)
                        })
                        .then(response => {
                            // Always parse the JSON, even for error responses
                            return response.json().then(data => {
                                if (!response.ok) {
                                    // If the server returned an error object with an error message, use it
                                    if (data && data.error) {
                                        throw new Error(data.error);
                                    }
                                    // Otherwise use a generic error message
                                    throw new Error('Failed to update SSH config');
                                }
                                return data;
                            });
                        })
                        .then(data => {
                            showMessage('SSH config updated successfully!', 'success');
                            resetForm();
                            window.location.reload();
                        })
                        .catch(error => {
                            showMessage(error.message, 'error');
                        });
                    }
                    
                    // Delete SSH Config
                    function deleteSSHConfig(id) {
                        fetch('/dashboard/ssh-configs/' + id, {
                            method: 'DELETE'
                        })
                        .then(response => {
                            // Always parse the JSON, even for error responses
                            return response.json().then(data => {
                                if (!response.ok) {
                                    // If the server returned an error object with an error message, use it
                                    if (data && data.error) {
                                        throw new Error(data.error);
                                    }
                                    // Otherwise use a generic error message
                                    throw new Error('Failed to delete SSH config');
                                }
                                return data;
                            });
                        })
                        .then(data => {
                            showMessage('SSH config deleted successfully!', 'success');
                            window.location.reload();
                        })
                        .catch(error => {
                            showMessage(error.message, 'error');
                        });
                    }
                    
                    // Edit SSH Config
                    function editSSHConfig(id) {
                        fetch('/dashboard/ssh-configs/' + id)
                        .then(response => {
                            if (!response.ok) {
                                throw new Error('Failed to fetch SSH config');
                            }
                            return response.json();
                        })
                        .then(data => {
                            document.getElementById('name').value = data.name;
                            document.getElementById('name').readOnly = true;
                            document.getElementById('serverHost').value = data.serverHost;
                            document.getElementById('port').value = data.port;
                            document.getElementById('username').value = data.username;
                            document.getElementById('password').value = data.password;
                            
                            formMode.value = 'update';
                            formTitle.textContent = 'Edit SSH Config';
                            cancelBtn.style.display = 'inline-block';
                        })
                        .catch(error => {
                            showMessage(error.message, 'error');
                        });
                    }
                    
                    // Copy SSH Config
                    function copySSHConfig(id) {
                        fetch('/dashboard/ssh-configs/' + id)
                        .then(response => {
                            if (!response.ok) {
                                throw new Error('Failed to fetch SSH config');
                            }
                            return response.json();
                        })
                        .then(data => {
                            document.getElementById('name').value = data.name + '-copy';
                            document.getElementById('name').readOnly = false;
                            document.getElementById('serverHost').value = data.serverHost;
                            document.getElementById('port').value = data.port;
                            document.getElementById('username').value = data.username;
                            document.getElementById('password').value = data.password;
                            
                            formMode.value = 'create';
                            formTitle.textContent = 'Create New SSH Config';
                            cancelBtn.style.display = 'inline-block';
                        })
                        .catch(error => {
                            showMessage(error.message, 'error');
                        });
                    }
                    
                    // Reset form
                    function resetForm() {
                        form.reset();
                        document.getElementById('name').readOnly = false;
                        formMode.value = 'create';
                        formTitle.textContent = 'Create New SSH Config';
                        cancelBtn.style.display = 'none';
                    }
                    
                    // Show message
                    function showMessage(message, type) {
                        messageContainer.textContent = message;
                        messageContainer.className = 'message ' + type;
                        messageContainer.classList.remove('hidden');
                        
                        setTimeout(() => {
                            messageContainer.classList.add('hidden');
                        }, 5000);
                    }
                });
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    @GetMapping("/ssh-configs", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getAllSSHConfigs(): List<SSHConfig> {
        return crudOperation.findAll()
    }

    @GetMapping("/ssh-configs/{name}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getSSHConfig(@PathVariable name: String): ResponseEntity<SSHConfig> {
        val config = crudOperation.findByPrimaryKey(name)
        return if (config != null) {
            ResponseEntity.ok(config)
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @PostMapping("/ssh-configs", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun createSSHConfig(@RequestBody config: SSHConfig): ResponseEntity<Any> {
        val newConfig = config.copy(
            createdAt = Timestamp(System.currentTimeMillis()),
            updatedAt = Timestamp(System.currentTimeMillis())
        )
        try {
            crudOperation.insert(listOf(newConfig))
            return ResponseEntity.ok(newConfig)
        } catch (e: Exception) {
            // Check if it's a unique constraint violation (error code 23505)
            if (e.cause?.message?.contains("Unique index or primary key violation") == true || 
                e.message?.contains("Unique index or primary key violation") == true) {
                return ResponseEntity.badRequest().body(mapOf("error" to "A configuration with the name '${config.name}' already exists. Please use a different name."))
            }
            // For other exceptions, rethrow
            throw e
        }
    }

    @PutMapping("/ssh-configs/{name}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun updateSSHConfig(@PathVariable name: String, @RequestBody config: SSHConfig): ResponseEntity<Any> {
        // First, get the existing config to preserve createdAt
        val existingConfig = crudOperation.findByPrimaryKey(name)
        
        if (existingConfig == null) {
            return ResponseEntity.notFound().build()
        }
        
        val updatedConfig = SSHConfig(
            name = name,
            serverHost = config.serverHost,
            port = config.port,
            username = config.username,
            password = config.password,
            createdAt = existingConfig.createdAt,
            updatedAt = Timestamp(System.currentTimeMillis())
        )
        
        try {
            crudOperation.update(listOf(updatedConfig))
            return ResponseEntity.ok(updatedConfig)
        } catch (e: Exception) {
            // Check if it's a unique constraint violation (error code 23505)
            if (e.cause?.message?.contains("Unique index or primary key violation") == true || 
                e.message?.contains("Unique index or primary key violation") == true) {
                return ResponseEntity.badRequest().body(mapOf("error" to "A configuration with the name '${config.name}' already exists. Please use a different name."))
            }
            // For other exceptions, return a meaningful error message
            return ResponseEntity.badRequest().body(mapOf("error" to "Failed to update SSH config: ${e.message}"))
        }
    }

    @DeleteMapping("/ssh-configs/{name}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun deleteSSHConfig(@PathVariable name: String): ResponseEntity<Any> {
        // First, get the existing config
        val existingConfig = crudOperation.findByPrimaryKey(name)
        
        if (existingConfig == null) {
            return ResponseEntity.notFound().build()
        }
        
        try {
            crudOperation.delete(listOf(existingConfig))
            return ResponseEntity.ok(mapOf("deleted" to true))
        } catch (e: Exception) {
            // For exceptions, return a meaningful error message
            return ResponseEntity.badRequest().body(mapOf("error" to "Failed to delete SSH config: ${e.message}"))
        }
    }
}