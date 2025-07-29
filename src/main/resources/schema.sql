-- Create sshConfig table if it doesn't exist
CREATE TABLE IF NOT EXISTS sshConfig (
    name VARCHAR(255) PRIMARY KEY,
    serverHost VARCHAR(255) NOT NULL,
    port INT NOT NULL,
    username VARCHAR(255) DEFAULT '',
    password VARCHAR(255) NOT NULL,
    createdAt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updatedAt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes if they don't exist
CREATE INDEX IF NOT EXISTS idxSshConfigServerHost ON sshConfig(serverHost);
CREATE INDEX IF NOT EXISTS idxSshConfigServerHostPort ON sshConfig(serverHost, port);

-- Create SSHLogWatcher table if it doesn't exist
CREATE TABLE IF NOT EXISTS SSHLogWatcher (
    name VARCHAR(255) PRIMARY KEY,
    sshConfigName VARCHAR(255) NOT NULL,
    watchDir VARCHAR(255) NOT NULL,
    recurDepth INT NOT NULL,
    filePrefix VARCHAR(255) NOT NULL,
    fileContains VARCHAR(255) NOT NULL,
    filePostfix VARCHAR(255) NOT NULL,
    archivedLogs BOOLEAN NOT NULL DEFAULT TRUE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    createdAt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updatedAt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (sshConfigName) REFERENCES sshConfig(name)
);

-- Create indexes for SSHLogWatcher
CREATE INDEX IF NOT EXISTS idxSSHLogWatcherActiveFiles ON SSHLogWatcher(sshConfigName);