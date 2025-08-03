package com.cleanbuild.tech.monolit.controller

import com.cleanbuild.tech.monolit.DbRecord.SSHConfig
import com.cleanbuild.tech.monolit.DbRecord.SSHLogWatcher
import com.cleanbuild.tech.monolit.repository.CRUDOperation
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.sql.Timestamp
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.sql.DataSource

@RestController
@RequestMapping("/ssh-log-watcher")
class SSHLogWatcherController(private val dataSource: DataSource) {

    private val crudOperation = CRUDOperation<SSHLogWatcher>(dataSource, SSHLogWatcher::class)
    private val sshConfigCrudOperation = CRUDOperation<SSHConfig>(dataSource, SSHConfig::class)
    
    // Format timestamp for display in HTML
    private fun formatTimestamp(timestamp: Timestamp): String {
        val localDateTime = timestamp.toLocalDateTime()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        return localDateTime.format(formatter)
    }
    
    // Get all SSH config names for dropdown
    private fun getAllSSHConfigNames(): List<String> {
        return sshConfigCrudOperation.findAll().map { it.name }
    }
    
    // Get all available Java Time Zone IDs
    @GetMapping("/timezones", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getAllTimeZones(): List<Map<String, String>> {
        return ZoneId.getAvailableZoneIds()
            .sorted()
            .map { zoneId -> mapOf("id" to zoneId, "displayName" to zoneId.replace("/", " / ")) }
    }

    @GetMapping(produces = [MediaType.TEXT_HTML_VALUE])
    fun dashboard(): String {
        val sshLogWatchers = getAllSSHLogWatchers()
        val sshConfigNames = getAllSSHConfigNames()
        
        return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>SSH Log Watcher Dashboard</title>
            <link href="https://cdn.jsdelivr.net/npm/select2@4.1.0-rc.0/dist/css/select2.min.css" rel="stylesheet" />
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
                input[type="password"],
                select {
                    width: 100%;
                    padding: 8px;
                    border: 1px solid #ddd;
                    border-radius: 4px;
                    box-sizing: border-box;
                }
                .select2-container {
                    width: 100% !important;
                }
                .select2-container--default .select2-selection--single {
                    height: 38px;
                    padding: 5px;
                    border: 1px solid #ddd;
                }
                .select2-container--default .select2-selection--single .select2-selection__arrow {
                    height: 36px;
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
                .checkbox-container {
                    display: flex;
                    align-items: center;
                }
                .checkbox-container input[type="checkbox"] {
                    margin-right: 10px;
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
                
                <h1>SSH Log Watcher Dashboard</h1>
                
                <div id="message-container" class="hidden message"></div>
                
                <div class="form-container">
                    <h2 id="form-title">Create New SSH Log Watcher</h2>
                    <form id="ssh-log-watcher-form">
                        <input type="hidden" id="form-mode" value="create">
                        
                        <div class="form-group">
                            <label for="name">Name:</label>
                            <input type="text" id="name" name="name" required>
                        </div>
                        
                        <div class="form-group">
                            <label for="sshConfigName">SSH Config Name:</label>
                            <select id="sshConfigName" name="sshConfigName" required>
                                <option value="">-- Select SSH Config --</option>
                                ${sshConfigNames.joinToString("") { configName ->
                                    """<option value="$configName">$configName</option>"""
                                }}
                            </select>
                        </div>
                        
                        <div class="form-group">
                            <label for="watchDir">Watch Directory:</label>
                            <input type="text" id="watchDir" name="watchDir" required>
                        </div>
                        
                        <div class="form-group">
                            <label for="recurDepth">Recursion Depth:</label>
                            <input type="number" id="recurDepth" name="recurDepth" value="1" required>
                        </div>
                        
                        <div class="form-group">
                            <label for="filePrefix">File Prefix:</label>
                            <input type="text" id="filePrefix" name="filePrefix" required>
                        </div>
                        
                        <div class="form-group">
                            <label for="fileContains">File Contains:</label>
                            <input type="text" id="fileContains" name="fileContains" required>
                        </div>
                        
                        <div class="form-group">
                            <label for="filePostfix">File Postfix:</label>
                            <input type="text" id="filePostfix" name="filePostfix" required>
                        </div>
                        
                        <div class="form-group checkbox-container">
                            <input type="checkbox" id="archivedLogs" name="archivedLogs" checked>
                            <label for="archivedLogs">Archived Logs</label>
                        </div>
                        
                        <div class="form-group checkbox-container">
                            <input type="checkbox" id="enabled" name="enabled" checked>
                            <label for="enabled">Enabled</label>
                        </div>
                        
                        <div class="form-group">
                            <label for="javaTimeZoneId">Java Time Zone ID:</label>
                            <select id="javaTimeZoneId" name="javaTimeZoneId" required class="timezone-select">
                                <option value="UTC">UTC</option>
                            </select>
                        </div>
                        
                        <button type="submit" class="btn btn-success">Save</button>
                        <button type="button" id="cancel-btn" class="btn" style="display:none;">Cancel</button>
                    </form>
                </div>
                
                <h2>SSH Log Watchers</h2>
                <table id="ssh-log-watcher-table">
                    <thead>
                        <tr>
                            <th>Name</th>
                            <th>SSH Config</th>
                            <th>Watch Directory</th>
                            <th>Recursion Depth</th>
                            <th>File Pattern</th>
                            <th>Archived Logs</th>
                            <th>Enabled</th>
                            <th>Time Zone</th>
                            <th>Created At</th>
                            <th>Updated At</th>
                            <th>Actions</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${sshLogWatchers.joinToString("") { watcher ->
                            """
                            <tr data-id="${watcher.name}">
                                <td>${watcher.name}</td>
                                <td>${watcher.sshConfigName}</td>
                                <td>${watcher.watchDir}</td>
                                <td>${watcher.recurDepth}</td>
                                <td>${watcher.filePrefix}*${watcher.fileContains}*${watcher.filePostfix}</td>
                                <td>${if (watcher.archivedLogs) "Yes" else "No"}</td>
                                <td>${if (watcher.enabled) "Yes" else "No"}</td>
                                <td>${watcher.javaTimeZoneId}</td>
                                <td>${formatTimestamp(watcher.createdAt)}</td>
                                <td>${formatTimestamp(watcher.updatedAt)}</td>
                                <td>
                                    <button class="btn edit-btn" data-id="${watcher.name}">Edit</button>
                                    <button class="btn btn-success copy-btn" data-id="${watcher.name}">Copy</button>
                                    <button class="btn btn-danger delete-btn" data-id="${watcher.name}">Delete</button>
                                </td>
                            </tr>
                            """
                        }}
                    </tbody>
                </table>
            </div>
            
            <script src="https://code.jquery.com/jquery-3.6.0.min.js"></script>
            <script src="https://cdn.jsdelivr.net/npm/select2@4.1.0-rc.0/dist/js/select2.min.js"></script>
            <script>
                document.addEventListener('DOMContentLoaded', function() {
                    const form = document.getElementById('ssh-log-watcher-form');
                    const formMode = document.getElementById('form-mode');
                    const formTitle = document.getElementById('form-title');
                    const cancelBtn = document.getElementById('cancel-btn');
                    const messageContainer = document.getElementById('message-container');
                    
                    // Initialize and populate the timezone dropdown
                    $(document).ready(function() {
                        // Initialize Select2
                        $('.timezone-select').select2({
                            placeholder: "Select a timezone",
                            allowClear: true
                        });
                        
                        // Fetch timezones from the API
                        fetch('/ssh-log-watcher/timezones')
                            .then(response => response.json())
                            .then(timezones => {
                                const select = document.getElementById('javaTimeZoneId');
                                // Clear existing options except UTC
                                select.innerHTML = '';
                                
                                // Add all timezones
                                timezones.forEach(timezone => {
                                    const option = document.createElement('option');
                                    option.value = timezone.id;
                                    option.textContent = timezone.displayName;
                                    // Set UTC as selected by default
                                    if (timezone.id === 'UTC') {
                                        option.selected = true;
                                    }
                                    select.appendChild(option);
                                });
                                
                                // Refresh Select2 to show the new options
                                $('.timezone-select').trigger('change');
                            })
                            .catch(error => console.error('Error fetching timezones:', error));
                    });
                    
                    // Form submission
                    form.addEventListener('submit', function(e) {
                        e.preventDefault();
                        
                        const sshLogWatcher = {
                            name: document.getElementById('name').value,
                            sshConfigName: document.getElementById('sshConfigName').value,
                            watchDir: document.getElementById('watchDir').value,
                            recurDepth: parseInt(document.getElementById('recurDepth').value),
                            filePrefix: document.getElementById('filePrefix').value,
                            fileContains: document.getElementById('fileContains').value,
                            filePostfix: document.getElementById('filePostfix').value,
                            archivedLogs: document.getElementById('archivedLogs').checked,
                            enabled: document.getElementById('enabled').checked,
                            javaTimeZoneId: document.getElementById('javaTimeZoneId').value
                        };
                        
                        const mode = formMode.value;
                        
                        if (mode === 'create') {
                            createSSHLogWatcher(sshLogWatcher);
                        } else if (mode === 'update') {
                            updateSSHLogWatcher(sshLogWatcher);
                        }
                    });
                    
                    // Edit button click
                    document.addEventListener('click', function(e) {
                        if (e.target.classList.contains('edit-btn')) {
                            const id = e.target.getAttribute('data-id');
                            editSSHLogWatcher(id);
                        }
                    });
                    
                    // Copy button click
                    document.addEventListener('click', function(e) {
                        if (e.target.classList.contains('copy-btn')) {
                            const id = e.target.getAttribute('data-id');
                            copySSHLogWatcher(id);
                        }
                    });
                    
                    // Delete button click
                    document.addEventListener('click', function(e) {
                        if (e.target.classList.contains('delete-btn')) {
                            const id = e.target.getAttribute('data-id');
                            if (confirm('Are you sure you want to delete this SSH Log Watcher?')) {
                                deleteSSHLogWatcher(id);
                            }
                        }
                    });
                    
                    // Cancel button click
                    cancelBtn.addEventListener('click', function() {
                        resetForm();
                    });
                    
                    // Create SSH Log Watcher
                    function createSSHLogWatcher(sshLogWatcher) {
                        fetch('/ssh-log-watcher/watchers', {
                            method: 'POST',
                            headers: {
                                'Content-Type': 'application/json'
                            },
                            body: JSON.stringify(sshLogWatcher)
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
                                    throw new Error('Failed to create SSH Log Watcher');
                                }
                                return data;
                            });
                        })
                        .then(data => {
                            showMessage('SSH Log Watcher created successfully!', 'success');
                            resetForm();
                            window.location.reload();
                        })
                        .catch(error => {
                            showMessage(error.message, 'error');
                        });
                    }
                    
                    // Update SSH Log Watcher
                    function updateSSHLogWatcher(sshLogWatcher) {
                        fetch('/ssh-log-watcher/watchers/' + sshLogWatcher.name, {
                            method: 'PUT',
                            headers: {
                                'Content-Type': 'application/json'
                            },
                            body: JSON.stringify(sshLogWatcher)
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
                                    throw new Error('Failed to update SSH Log Watcher');
                                }
                                return data;
                            });
                        })
                        .then(data => {
                            showMessage('SSH Log Watcher updated successfully!', 'success');
                            resetForm();
                            window.location.reload();
                        })
                        .catch(error => {
                            showMessage(error.message, 'error');
                        });
                    }
                    
                    // Delete SSH Log Watcher
                    function deleteSSHLogWatcher(id) {
                        fetch('/ssh-log-watcher/watchers/' + id, {
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
                                    throw new Error('Failed to delete SSH Log Watcher');
                                }
                                return data;
                            });
                        })
                        .then(data => {
                            showMessage('SSH Log Watcher deleted successfully!', 'success');
                            window.location.reload();
                        })
                        .catch(error => {
                            showMessage(error.message, 'error');
                        });
                    }
                    
                    // Edit SSH Log Watcher
                    function editSSHLogWatcher(id) {
                        fetch('/ssh-log-watcher/watchers/' + id)
                        .then(response => {
                            if (!response.ok) {
                                throw new Error('Failed to fetch SSH Log Watcher');
                            }
                            return response.json();
                        })
                        .then(data => {
                            document.getElementById('name').value = data.name;
                            document.getElementById('name').readOnly = true;
                            
                            // Set the selected option in the dropdown
                            const sshConfigSelect = document.getElementById('sshConfigName');
                            for (let i = 0; i < sshConfigSelect.options.length; i++) {
                                if (sshConfigSelect.options[i].value === data.sshConfigName) {
                                    sshConfigSelect.selectedIndex = i;
                                    break;
                                }
                            }
                            
                            document.getElementById('watchDir').value = data.watchDir;
                            document.getElementById('recurDepth').value = data.recurDepth;
                            document.getElementById('filePrefix').value = data.filePrefix;
                            document.getElementById('fileContains').value = data.fileContains;
                            document.getElementById('filePostfix').value = data.filePostfix;
                            document.getElementById('archivedLogs').checked = data.archivedLogs;
                            document.getElementById('enabled').checked = data.enabled;
                            // Set the timezone value
                            const timezoneSelect = document.getElementById('javaTimeZoneId');
                            timezoneSelect.value = data.javaTimeZoneId;
                            // Refresh Select2 to show the selected option
                            $(timezoneSelect).trigger('change');
                            
                            formMode.value = 'update';
                            formTitle.textContent = 'Edit SSH Log Watcher';
                            cancelBtn.style.display = 'inline-block';
                        })
                        .catch(error => {
                            showMessage(error.message, 'error');
                        });
                    }
                    
                    // Copy SSH Log Watcher
                    function copySSHLogWatcher(id) {
                        fetch('/ssh-log-watcher/watchers/' + id)
                        .then(response => {
                            if (!response.ok) {
                                throw new Error('Failed to fetch SSH Log Watcher');
                            }
                            return response.json();
                        })
                        .then(data => {
                            // Clear the name field and keep it editable for the new name
                            document.getElementById('name').value = '';
                            document.getElementById('name').readOnly = false;
                            
                            // Set the selected option in the dropdown
                            const sshConfigSelect = document.getElementById('sshConfigName');
                            for (let i = 0; i < sshConfigSelect.options.length; i++) {
                                if (sshConfigSelect.options[i].value === data.sshConfigName) {
                                    sshConfigSelect.selectedIndex = i;
                                    break;
                                }
                            }
                            
                            // Copy all other fields
                            document.getElementById('watchDir').value = data.watchDir;
                            document.getElementById('recurDepth').value = data.recurDepth;
                            document.getElementById('filePrefix').value = data.filePrefix;
                            document.getElementById('fileContains').value = data.fileContains;
                            document.getElementById('filePostfix').value = data.filePostfix;
                            document.getElementById('archivedLogs').checked = data.archivedLogs;
                            document.getElementById('enabled').checked = data.enabled;
                            // Set the timezone value and trigger change for Select2
                            const timezoneSelect = document.getElementById('javaTimeZoneId');
                            timezoneSelect.value = data.javaTimeZoneId;
                            $(timezoneSelect).trigger('change');
                            
                            // Set form mode to create since we're creating a new watcher
                            formMode.value = 'create';
                            formTitle.textContent = 'Create New SSH Log Watcher (Copied)';
                            cancelBtn.style.display = 'inline-block';
                        })
                        .catch(error => {
                            showMessage(error.message, 'error');
                        });
                    }
                    
                    // Reset form
                    function resetForm() {
                        form.reset();
                        formMode.value = 'create';
                        formTitle.textContent = 'Create New SSH Log Watcher';
                        cancelBtn.style.display = 'none';
                        
                        // Reset Select2 dropdown to default (UTC)
                        const timezoneSelect = document.getElementById('javaTimeZoneId');
                        timezoneSelect.value = 'UTC';
                        $(timezoneSelect).trigger('change');
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

    @GetMapping("/watchers", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getAllSSHLogWatchers(): List<SSHLogWatcher> {
        return crudOperation.findAll()
    }

    @GetMapping("/watchers/{name}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getSSHLogWatcher(@PathVariable name: String): ResponseEntity<SSHLogWatcher> {
        val watcher = crudOperation.findByPrimaryKey(name)
        return if (watcher != null) {
            ResponseEntity.ok(watcher)
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @PostMapping("/watchers", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun createSSHLogWatcher(@RequestBody watcher: SSHLogWatcher): ResponseEntity<Any> {
        val newWatcher = watcher.copy(
            createdAt = Timestamp(System.currentTimeMillis()),
            updatedAt = Timestamp(System.currentTimeMillis())
        )
        try {
            crudOperation.insert(listOf(newWatcher))
            return ResponseEntity.ok(newWatcher)
        } catch (e: Exception) {
            // Check if it's a unique constraint violation (error code 23505)
            if (e.cause?.message?.contains("Unique index or primary key violation") == true || 
                e.message?.contains("Unique index or primary key violation") == true) {
                return ResponseEntity.badRequest().body(mapOf("error" to "A watcher with the name '${watcher.name}' already exists. Please use a different name."))
            }
            // For other exceptions, rethrow
            throw e
        }
    }

    @PutMapping("/watchers/{name}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun updateSSHLogWatcher(@PathVariable name: String, @RequestBody watcher: SSHLogWatcher): ResponseEntity<Any> {
        // First, get the existing watcher to preserve createdAt
        val existingWatcher = crudOperation.findByPrimaryKey(name)
        
        if (existingWatcher == null) {
            return ResponseEntity.notFound().build()
        }
        
        val updatedWatcher = SSHLogWatcher(
            name = name,
            sshConfigName = watcher.sshConfigName,
            watchDir = watcher.watchDir,
            recurDepth = watcher.recurDepth,
            filePrefix = watcher.filePrefix,
            fileContains = watcher.fileContains,
            filePostfix = watcher.filePostfix,
            archivedLogs = watcher.archivedLogs,
            enabled = watcher.enabled,
            javaTimeZoneId = watcher.javaTimeZoneId,
            createdAt = existingWatcher.createdAt,
            updatedAt = Timestamp(System.currentTimeMillis())
        )
        
        try {
            crudOperation.update(listOf(updatedWatcher))
            return ResponseEntity.ok(updatedWatcher)
        } catch (e: Exception) {
            // Check if it's a unique constraint violation (error code 23505)
            if (e.cause?.message?.contains("Unique index or primary key violation") == true || 
                e.message?.contains("Unique index or primary key violation") == true) {
                return ResponseEntity.badRequest().body(mapOf("error" to "A watcher with the name '${watcher.name}' already exists. Please use a different name."))
            }
            // For other exceptions, return a meaningful error message
            return ResponseEntity.badRequest().body(mapOf("error" to "Failed to update SSH Log Watcher: ${e.message}"))
        }
    }

    @DeleteMapping("/watchers/{name}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun deleteSSHLogWatcher(@PathVariable name: String): ResponseEntity<Any> {
        // First, get the existing watcher
        val existingWatcher = crudOperation.findByPrimaryKey(name)
        
        if (existingWatcher == null) {
            return ResponseEntity.notFound().build()
        }
        
        try {
            crudOperation.delete(listOf(existingWatcher))
            return ResponseEntity.ok(mapOf("deleted" to true))
        } catch (e: Exception) {
            // For exceptions, return a meaningful error message
            return ResponseEntity.badRequest().body(mapOf("error" to "Failed to delete SSH Log Watcher: ${e.message}"))
        }
    }
}
