package com.dbbackup.service;

import com.dbbackup.model.BackupRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


@Service
public class RestoreService {

    public String restoreBackup(MultipartFile zipFile, BackupRequest request) throws IOException, InterruptedException {
        // 1. Create temporary directory
        String tempDirPath = System.getProperty("java.io.tmpdir") + File.separator + "restore_temp_" + System.currentTimeMillis();
        Path tempDir = Paths.get(tempDirPath);
        Files.createDirectories(tempDir);

        // 2. Save uploaded zip file
        Path zipPath = tempDir.resolve(zipFile.getOriginalFilename());
        Files.copy(zipFile.getInputStream(), zipPath, StandardCopyOption.REPLACE_EXISTING);

        // 3. Extract zip file
        Path extractDir = tempDir.resolve("extracted");
        Files.createDirectories(extractDir);
        unzip(zipPath.toFile(), extractDir.toFile());

        // 4. Find .sql or .archive file (recursive)
        String dbType = request.getDbType().toLowerCase();

        if ("mysql".equals(dbType)) {
            File backupFile = findBackupFile(extractDir.toFile());
            if (backupFile == null) {
                throw new FileNotFoundException("No .sql file found in the uploaded zip.");
            }
            return restoreMySQL(request, backupFile);

        } else if ("mongodb".equals(dbType)) {
            return restoreMongoDB(request, extractDir.toFile());

        } else {
            throw new UnsupportedOperationException("Unsupported DB type: " + request.getDbType());
        }


        // 5. Restore based on dbType
//        String dbType = request.getDbType().toLowerCase();
//        return switch (dbType) {
//            case "mysql" -> restoreMySQL(request, backupFile);
//            case "mongodb" -> restoreMongoDB(request, backupFile);
//            default -> throw new UnsupportedOperationException("Unsupported DB type: " + request.getDbType());
//        };
    }

    private File findBackupFile(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return null;

        for (File file : files) {
            if (file.isDirectory()) {
                File result = findBackupFile(file);
                if (result != null) return result;
            } else if (file.getName().endsWith(".sql") || file.getName().endsWith(".archive")) {
                return file;
            }
        }
        return null;
    }

    private String restoreMySQL(BackupRequest request, File backupFile) throws IOException, InterruptedException {
        List<String> command = List.of(
                "mysql",
                "-h", request.getHost(),
                "-P", String.valueOf(request.getPort()),
                "-u", request.getUsername(),
                "-p" + request.getPassword(),
                request.getDbName()
        );

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);  // Merge stderr with stdout
        Process process = pb.start();

        // Pipe the .sql file into mysql
        try (OutputStream os = process.getOutputStream();
             FileInputStream fis = new FileInputStream(backupFile)) {
            fis.transferTo(os);
        }

        // Read process output for debugging
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("MySQL restore failed. Exit code: " + exitCode + "\nOutput:\n" + output);
        }

        return "MySQL database restored successfully.\nOutput:\n" + output;
    }
    private String restoreMongoDB(BackupRequest request, File backupDir) throws IOException, InterruptedException {
        String uri = buildMongoURI(request);

        // Expecting structure: backupDir -> someFolder -> actual MongoDB db folder
        File[] topLevelDirs = backupDir.listFiles(File::isDirectory);
        if (topLevelDirs == null || topLevelDirs.length == 0) {
            throw new FileNotFoundException("No subdirectories found in extracted backup.");
        }

        // Go one level deeper: expected structure is something like /extracted/backupFolder/dbFolder
        File[] dbFolders = topLevelDirs[0].listFiles(File::isDirectory);
        if (dbFolders == null || dbFolders.length == 0) {
            throw new FileNotFoundException("No MongoDB database folder found in backup.");
        }

        File actualDbFolder = dbFolders[0]; // This is the folder like 'employees/'
        String actualDbName = actualDbFolder.getName();

        List<String> command = List.of(
                "mongorestore",
                "--uri=" + uri,
                "--nsInclude=" + actualDbName + ".*",
                "--dir=" + actualDbFolder.getAbsolutePath()
        );

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        // Read process output for debugging
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("MongoDB restore failed. Exit code: " + exitCode + "\nOutput:\n" + output);
        }

        return "MongoDB database restored successfully.\nOutput:\n" + output;
    }


    private String buildMongoURI(BackupRequest request) {
        if (request.getUsername() == null || request.getUsername().isEmpty()) {
            return "mongodb://" + request.getHost() + ":" + request.getPort() + "/" + request.getDbName();
        }
        return "mongodb://" + request.getUsername() + ":" + request.getPassword() + "@" +
                request.getHost() + ":" + request.getPort() + "/" + request.getDbName();
    }

    private void unzip(File zipFile, File destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File newFile = new File(destDir, entry.getName());
                if (entry.isDirectory()) {
                    newFile.mkdirs();
                } else {
                    File parent = newFile.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }
}
