# SSH Server Configuration Web Interface Documentation

This document provides an overview of the SSH Server Configuration Web Interface implementation.

## Overview

The SSH Server Configuration Web Interface provides a user-friendly way to manage SSH server connection configurations through a web browser. It includes:

1. A home page with quick access to key features
2. A list page showing all existing SSH server configurations
3. A form for creating and editing configurations
4. A confirmation page for deleting configurations
5. Client-side validation and responsive design

## Components

### Controllers

#### HomeController

- Handles requests to the root URL (`/`)
- Displays the home page

#### SshServerConfigController

- Handles requests related to SSH server configurations
- Base URL: `/ssh-configs`
- Endpoints:
  - `GET /ssh-configs`: List all configurations
  - `GET /ssh-configs/new`: Display form for creating a new configuration
  - `POST /ssh-configs`: Create a new configuration
  - `GET /ssh-configs/{id}/edit`: Display form for editing a configuration
  - `POST /ssh-configs/{id}`: Update a configuration
  - `GET /ssh-configs/{id}/delete`: Display confirmation page for deleting a configuration
  - `POST /ssh-configs/{id}/delete`: Delete a configuration

### Templates

#### Layout Template

- `layout.html`: Base template for all pages
- Provides consistent header, navigation, and footer
- Uses Bootstrap for responsive design

#### Page Templates

- `index.html`: Home page with welcome message and quick access cards
- `ssh-configs/list.html`: List of all SSH server configurations
- `ssh-configs/form.html`: Form for creating and editing configurations
- `ssh-configs/delete.html`: Confirmation page for deleting configurations

### Static Resources

#### CSS

- `styles.css`: Custom styles for the web interface
- Uses Bootstrap for responsive design
- Includes custom styling for forms, tables, cards, and buttons

#### JavaScript

- `scripts.js`: Client-side functionality
- Form validation
- Password visibility toggle
- Auto-dismissing alerts
- Confirmation for delete actions

## Features

### Listing Configurations

The list page (`/ssh-configs`) displays all existing SSH server configurations in a table with the following information:

- ID
- Server Host
- Port
- Created Date
- Last Updated Date
- Actions (Edit, Delete)

If no configurations exist, a message is displayed prompting the user to create one.

### Creating Configurations

The form page (`/ssh-configs/new`) allows users to create a new SSH server configuration with the following fields:

- Server Host: The hostname or IP address of the SSH server
- Port: The port number of the SSH server (default: 22)
- Password: The password for the SSH server

All fields are required and validated both on the client-side and server-side.

### Editing Configurations

The form page (`/ssh-configs/{id}/edit`) allows users to edit an existing SSH server configuration. It works similarly to the create form but pre-fills the fields with the existing configuration data.

### Deleting Configurations

The delete confirmation page (`/ssh-configs/{id}/delete`) displays the details of the configuration to be deleted and asks for confirmation before proceeding with the deletion.

## Validation

### Client-Side Validation

- Server Host: Required, between 2 and 255 characters
- Port: Required, between 1 and 65535
- Password: Required, between 4 and 100 characters

### Server-Side Validation

- Uses Hibernate Validator annotations on the DTO class
- Provides detailed error messages for invalid input
- Prevents submission of invalid data

## Responsive Design

The web interface is fully responsive and works well on devices of all sizes:

- Desktop: Full layout with all features
- Tablet: Adjusted layout with slightly modified spacing
- Mobile: Stacked layout with optimized controls for touch

## Error Handling

- Form validation errors are displayed inline next to the relevant fields
- Success and error messages are displayed at the top of the page
- Success messages auto-dismiss after 5 seconds
- Error messages remain visible until manually dismissed

## Security Considerations

- Passwords are masked by default in the form
- A password visibility toggle allows users to see the password if needed
- Confirmation is required before deleting configurations

## Usage Examples

### Creating a New Configuration

1. Navigate to the home page (`/`)
2. Click on "Create New" or navigate to `/ssh-configs/new`
3. Fill in the required fields:
   - Server Host: `example.com`
   - Port: `22`
   - Password: `password123`
4. Click "Create"
5. You will be redirected to the list page with a success message

### Editing a Configuration

1. Navigate to the list page (`/ssh-configs`)
2. Find the configuration you want to edit
3. Click the "Edit" button
4. Modify the fields as needed
5. Click "Update"
6. You will be redirected to the list page with a success message

### Deleting a Configuration

1. Navigate to the list page (`/ssh-configs`)
2. Find the configuration you want to delete
3. Click the "Delete" button
4. Review the configuration details on the confirmation page
5. Click "Confirm Delete"
6. You will be redirected to the list page with a success message