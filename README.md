# 🚀 QuickDump

**QuickDump** is a lightweight, user-friendly database backup and restore utility with a web-based interface. It supports both **MySQL** and **MongoDB**, allowing developers and non-technical users to perform full database backups and restores with just a few clicks — no command line required.

---

## ✨ Features

- ✅ **One-Click Backup** for MySQL and MongoDB databases
- ♻️ **Restore Support** from `.zip`, `.sql`, or `.bson` dump files
- 📁 **GUI-Based File Management** to upload and download backup files
- 📦 **Local Storage with Compression** for efficient space usage
- 🧩 **Modular Design** ready for integration with cloud storage or schedulers

---

## 📌 Use Cases

- Local database snapshots before deployment or code updates
- Manual restore points during development/testing
- Backup automation for internal tools
- Simple backup interface for non-technical team members
- DB migration with full/partial dumps

---

## 🧰 Technologies Used

- **Java 21**, **Spring Boot** – Backend and service logic
- **Thymeleaf** – Server-side rendering for the web interface
- **MySQL**, **MongoDB** – Supported database engines
- **ProcessBuilder API** – Running `mysqldump`, `mongodump`, and restore commands

---

