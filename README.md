# File Sharing System - RCOMP Project 2 (2024/2025) - Alternative Project

## Project Overview

This project implements the **Alternative Project** for the RCOMP (Computer Networks) course, Project 2 (2024/2025). The alternative project applies to students not enrolled in the Integrative Project of the 4th semester.

The system is a functional **File Sharing System**, which allows authenticated access, file listing, and additional upload functionality through a custom HTTP-based protocol implemented over TCP sockets using the Berkeley Sockets API in Java.

The server is fully self-contained, running outside of any IDE, and deployed in the DEI Virtual Servers Private Cloud.

---

## Student Information

* **Project type:** Alternative Project (Standalone)
* **Developed by:** Individual work (solo project)
* **Course:** Redes de Computadores (RCOMP) 2024/2025

---

## Functional Requirements Implemented

The following features from the Alternative Project backlog have been implemented:

* User authentication (configured at runtime via properties file)
* File slot listing (list of stored files with file count)
* Client-side application developed in Java via web interface

In addition, an extra feature was implemented:

* File upload to server with maximum slots limit

---

## System Architecture

* **Language:** Java
* **Architecture:** Client-server, multi-threaded TCP server
* **Communication protocol:** Custom HTTP-like protocol using Berkeley Sockets API
* **Deployment:** Remote server (DEI Virtual Servers Private Cloud)

---

## Directory Structure

```
file-sharing-system/
├── Makefile               # Build automation
├── HttpServerFormFileUpload.java  # Server entry point (main class)
├── HttpRequest.java       # Handles incoming HTTP requests
├── HTTP.java              # HTTP protocol utility methods
├── config.properties      # Stores server credentials
└── www/                   # Web content (HTML templates)
    ├── index.html         # Login page
    ├── home.html          # Upload and list page
    ├── file-list.html     # Files listing page
    ├── upload-success.html# Upload success page
    └── error.html         # Error page when maximum files reached
```

---

## Build and Execution Instructions

### Compilation

Navigate to the project root (`file-sharing-system/`) and compile using `make`:

```bash
make all
```

To clean compiled classes:

```bash
make clean
```

### Configuration

The server credentials are stored in `config.properties`:

```properties
auth.username=admin
auth.password=admin1
```

The administrator can change these credentials before launching the server.

### Web Files

All HTML files are located under `./file-sharing-system/www/`. These pages are dynamically served by the backend.

### Running the Server

The server is executed with two command-line arguments:

```bash
java HttpServerFormFileUpload <port> <maxFiles>
```

Example (as configured for production deployment):

```bash
java HttpServerFormFileUpload 9990 10
```

* `port`: TCP port where the server listens.
* `maxFiles`: Maximum number of files allowed for upload.

### Deployment in DEI Virtual Servers Private Cloud

The server has been configured to run automatically via the **services manager** with the following command:

```bash
java HttpServerFormFileUpload 9990 10
```

* Web files located at `/var/www/rcomp/www/`
* Uploaded files stored persistently at `/var/www/rcomp/files/`

---

## Usage Instructions

### 1. Access Login Page

Access the server from any browser using:

```
http://<server-ip>:9990/
```

Example for DEI deployment:

```
http://10.9.23.64:9990/
```

### 2. Authentication

* Enter username and password as defined in `config.properties`.

### 3. Upload Files (Extra Feature)

* After authentication, the user can upload files through `home.html`.
* Supported formats: `.pdf`, `.txt`, `.gif`, `.png`
* Upload is blocked once maximum file count is reached.

### 4. List Files

* Use the "List Uploaded Files" option to display all uploaded files.
* Files are displayed with their names and sizes.

### 5. Error Handling

* If the maximum file count is reached, further uploads are blocked and the user is redirected to `error.html`.

---

## Technical Highlights

* Fully custom HTTP server built using pure Java Sockets API.
* Server designed to handle multiple simultaneous client connections via multithreading.
* Simple HTTP protocol parsing and dynamic HTML response generation.
* File uploads handled via multipart form-data parsing.
* File count dynamically displayed and updated.
* Persistent storage and consistent error management.

---

## Compliance with RCOMP Alternative Project Backlog

* ✅ 5.3.1 Files Server authentication and slot listing.
* ✅ 5.3.2 Client implemented in Java.
* ☑️ 5.3.4 File upload (Implemented as extra feature - not mandatory for 1-person team).

---

## Author
- **Student:** Lucas Gonçalves - 1211601
- **Course:** RCOMP (Redes de Computadores), 2024/2025
