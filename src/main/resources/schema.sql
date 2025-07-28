-- Create sshConfig table if it doesn't exist
CREATE TABLE IF NOT EXISTS sshConfig (
    name VARCHAR(255) PRIMARY KEY,
    serverHost VARCHAR(255) NOT NULL,
    port INT NOT NULL,
    password VARCHAR(255) NOT NULL,
    createdAt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updatedAt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes if they don't exist
CREATE INDEX IF NOT EXISTS idxSshConfigServerHost ON sshConfig(serverHost);
CREATE INDEX IF NOT EXISTS idxSshConfigServerHostPort ON sshConfig(serverHost, port);