# H2 Database Connection Guide for IntelliJ IDEA

This guide provides step-by-step instructions for connecting to the H2 database in the Monolit project using IntelliJ IDEA's database tools.

## Connection Information

The project uses an H2 database with the following configuration:

- **Database file location**: `./data/monolitdb`
- **Username**: `sa`
- **Password**: `password`
- **H2 TCP Server Port**: `9092` (when application is running)
- **H2 Web Console Port**: `8082` (when application is running)

## Method 1: Connecting to the Database File Directly

Use this method when the application is not running:

1. In IntelliJ IDEA, click on the "Database" tab on the right side of the window
2. Click the "+" button and select "Data Source" > "H2"
3. In the configuration dialog, set the following:
   - **Name**: `Monolit DB` (or any name you prefer)
   - **Connection type**: `File`
   - **File**: Navigate to the project directory and select `data/monolitdb` (without file extension)
   - **URL**: Should be automatically set to something like `jdbc:h2:file:C:/Users/hp/IdeaProjects/monolit/data/monolitdb`
   - **User**: `sa`
   - **Password**: `password`
   - **Driver**: Make sure H2 driver is downloaded (click "Download" if needed)
4. Click "Test Connection" to verify the connection works
5. Click "Apply" and then "OK" to save the connection

## Method 2: Connecting via TCP Server (When Application is Running)

Use this method when the application is running:

1. Start the application (run `Main.kt`)
2. In IntelliJ IDEA, click on the "Database" tab on the right side of the window
3. Click the "+" button and select "Data Source" > "H2"
4. In the configuration dialog, set the following:
   - **Name**: `Monolit DB (TCP)` (or any name you prefer)
   - **Connection type**: `Remote`
   - **Host**: `localhost`
   - **Port**: `9092`
   - **Database**: `monolitdb`
   - **URL**: Should be automatically set to `jdbc:h2:tcp://localhost:9092/monolitdb`
   - **User**: `sa`
   - **Password**: `password`
   - **Driver**: Make sure H2 driver is downloaded (click "Download" if needed)
5. Click "Test Connection" to verify the connection works
6. Click "Apply" and then "OK" to save the connection

## Method 3: Using H2 Web Console

Use this method when the application is running:

1. Start the application (run `Main.kt`)
2. Open a web browser and navigate to:
   - **URL**: `http://localhost:8082` (H2 web server)
   - Or: `http://localhost:8080/h2-console` (Spring Boot embedded console)
3. In the login form, enter:
   - **JDBC URL**: `jdbc:h2:file:./data/monolitdb` (for direct file access)
   - Or: `jdbc:h2:tcp://localhost:9092/./data/monolitdb` (for TCP connection)
   - **User Name**: `sa`
   - **Password**: `password`
4. Click "Connect" to access the database

## Troubleshooting

### Connection Failed

1. **Application not running**: For TCP connections, make sure the application is running
2. **Wrong file path**: For file connections, ensure the path to the database file is correct
3. **Port already in use**: If port 9092 or 8082 is already in use, the H2 servers won't start

### Database File Not Found

1. Make sure the application has been run at least once to create the database file
2. Check if the file exists at `data/monolitdb.mv.db` in the project root

### Driver Issues

1. If IntelliJ can't find the H2 driver, click "Download" in the data source configuration
2. Alternatively, add the H2 driver manually by clicking "Driver Properties" and adding the JAR file

### TCP Connection Refused

1. Ensure the application is running in the "dev" profile (check `spring.profiles.active=dev` in application.properties)
2. Verify no firewall is blocking port 9092
3. Try connecting to the file directly instead

## Tips for Working with the Database

1. **View tables**: After connecting, expand the connection in the Database tab to see tables
2. **Run SQL queries**: Right-click on the connection and select "New" > "Query Console"
3. **Export/Import data**: Right-click on a table and select "Export Data" or use the "Import Data" option
4. **Generate DDL**: Right-click on a table and select "SQL Scripts" > "SQL Generator"
5. **View table data**: Double-click on a table to view and edit its data