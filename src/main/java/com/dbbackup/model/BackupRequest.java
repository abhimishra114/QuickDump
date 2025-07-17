package com.dbbackup.model;

import lombok.Data;

@Data
public class BackupRequest {
    private String dbType;
    private String host;
    private int port;
    private String username;
    private String password;
    private String dbName;

    private String backupType;


}
