import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

class HttpRequest implements Runnable {
	private Socket s;
	private DataOutputStream sOut;
	private DataInputStream sIn;
	private static final String BASE_FOLDER = "/var/www/rcomp";
	private static final String UPLOAD_DIR = BASE_FOLDER + "/files";
	private static final String WEB_ROOT = BASE_FOLDER + "/www";
	private static final Properties config = new Properties();

	static {
		try {
			config.load(new FileInputStream(BASE_FOLDER + "/config.properties"));
		} catch (IOException e) {
			System.out.println("Error loading config.properties: " + e.getMessage());
		}
	}

	public HttpRequest(Socket cli_s) {
		s = cli_s;
	}

	public void run() {
		try {
			sOut = new DataOutputStream(s.getOutputStream());
			sIn = new DataInputStream(s.getInputStream());
		} catch (IOException ex) {
			System.out.println("Data Stream IOException: " + ex.getMessage());
			try {
				s.close();
			} catch (IOException e) {
			}
			return;
		}

		try {
			String request = HTTP.readLineCRLF(sIn);
			if (request == null || request.isEmpty()) {
				HTTP.sendHttpStringResponse(sOut, "400 Bad Request", "text/html",
						"<html><body><h1>400 Bad Request</h1></body></html>");
				return;
			}

			if (request.startsWith("POST /home")) {
				processPostLogin();
			} else if (request.startsWith("POST /upload")) {
				processPostUpload();
			} else if (request.startsWith("POST /list")) {
				processPostList();
			} else if (request.startsWith("POST /delete")) {
				processPostDelete();
			} else {
				processGet(request);
			}
		} catch (Exception ex) {
			System.out.println("Error processing request: " + ex.getMessage());
			HTTP.sendHttpStringResponse(sOut, "500 Internal Server Error", "text/html",
					"<html><body><h1>500 Server Error</h1></body></html>");
		} finally {
			try {
				s.close();
			} catch (IOException ex) {
				System.out.println("CLOSE IOException: " + ex.getMessage());
			}
		}
	}

	void processPostLogin() {
		try {
			// Read headers
			int contentLength = 0;
			String line;
			do {
				line = HTTP.readLineCRLF(sIn);
				if (line.startsWith("Content-Length: ")) {
					contentLength = Integer.parseInt(line.substring("Content-Length: ".length()));
				}
			} while (line.length() > 0);

			// Read POST data
			byte[] postData = new byte[contentLength];
			sIn.readFully(postData);
			String postString = new String(postData, StandardCharsets.UTF_8);

			// Parse username and password
			String[] params = postString.split("&");
			String username = "";
			String password = "";
			for (String param : params) {
				String[] keyValue = param.split("=");
				if (keyValue.length == 2) {
					if (keyValue[0].equals("username")) {
						username = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8.name());
					} else if (keyValue[0].equals("password")) {
						password = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8.name());
					}
				}
			}

			// Validate credentials
			String validUsername = config.getProperty("auth.username");
			String validPassword = config.getProperty("auth.password");

			if (username.equals(validUsername) && password.equals(validPassword)) {
				// Successful login - send main page with file count
				File uploadDir = new File(UPLOAD_DIR);
				int currentFiles = HTTP.countFilesInDirectory(uploadDir);
				int maxFiles = Integer.parseInt(config.getProperty("max.files", "10"));
				String fileCount = currentFiles + "/" + maxFiles;

				String template = HTTP.readHtmlFile(WEB_ROOT + "/home.html");
				String response = template.replace("${file_count}", fileCount);
				HTTP.sendHttpStringResponse(sOut, "200 Ok", "text/html", response);
			} else {
				// Failed login - show error
				String template = HTTP.readHtmlFile(WEB_ROOT + "/index.html");
				String errorMessage = "<div class=\"error-message\">Invalid username or password</div>";
				String response = template.replace("${error_message}", errorMessage);
				HTTP.sendHttpStringResponse(sOut, "401 Unauthorized", "text/html", response);
			}
		} catch (Exception ex) {
			System.out.println("Error in processPostLogin: " + ex.getMessage());
			HTTP.sendHttpStringResponse(sOut, "500 Internal Server Error", "text/html",
					"<html><body><h1>500 Server Error</h1></body></html>");
		}
	}

	void processGet(String req) {
		try {
			String line;
			do {
				line = HTTP.readLineCRLF(sIn);
				if (line == null) {
					HTTP.sendHttpStringResponse(sOut, "400 Bad Request", "text/html",
							"<html><body><h1>400 Bad Request</h1></body></html>");
					return;
				}
			} while (line.length() > 0);

			String fileName = req.split(" ")[1];
			if (fileName.equals("/") || fileName.equals("/index.html")) {
				String template = HTTP.readHtmlFile(WEB_ROOT + "/index.html");
				String response = template.replace("${error_message}", "");
				HTTP.sendHttpStringResponse(sOut, "200 Ok", "text/html", response);
				return;
			} else if (fileName.equals("/home.html")) {
				File uploadDir = new File(UPLOAD_DIR);
				int currentFiles = HTTP.countFilesInDirectory(uploadDir);
				int maxFiles = Integer.parseInt(config.getProperty("max.files", "10"));
				String fileCount = currentFiles + "/" + maxFiles;
				boolean uploadDisabled = currentFiles >= maxFiles;

				String template = HTTP.readHtmlFile(WEB_ROOT + "/home.html");
				String response = template.replace("${file_count}", fileCount)
						.replace("${upload_disabled_attribute}", uploadDisabled ? "disabled" : "")
						.replace("${upload_style}",
								uploadDisabled ? "style='background-color: #cccccc; cursor: not-allowed;'" : "");
				HTTP.sendHttpStringResponse(sOut, "200 Ok", "text/html", response);
				return;
			}

			try {
				fileName = URLDecoder.decode(fileName, StandardCharsets.UTF_8.name());
			} catch (Exception e) {
				System.out.println("Error decoding URL: " + e.getMessage());
			}

			String filePath;
			if (fileName.startsWith("/files/")) {
				filePath = UPLOAD_DIR + fileName.substring("/files".length());
			} else {
				filePath = WEB_ROOT + fileName;
			}

			File f = new File(filePath);
			if (!f.exists() || !f.isFile()) {
				HTTP.sendHttpStringResponse(sOut, "404 Not Found", "text/html",
						"<html><body><h1>404 File not found</h1></body></html>");
				return;
			}

			HTTP.sendHttpFileResponse(sOut, null, filePath);

		} catch (Exception ex) {
			System.out.println("Error in processGet: " + ex.getMessage());
			HTTP.sendHttpStringResponse(sOut, "500 Internal Server Error", "text/html",
					"<html><body><h1>500 Server Error</h1></body></html>");
		}
	}

	void processPostUpload() {
		File uploadDir = new File(UPLOAD_DIR);
		int currentFiles = HTTP.countFilesInDirectory(uploadDir);
		int maxFiles = Integer.parseInt(config.getProperty("max.files", "10"));

		if (currentFiles >= maxFiles) {
			String errorMessage = "Maximum file limit reached (" + maxFiles + " files). Cannot upload more files.";
			String template = HTTP.readHtmlFile(WEB_ROOT + "/error.html");
			String response = template.replace("${error_message}", errorMessage)
					.replace("${file_count}", currentFiles + "/" + maxFiles);
			HTTP.sendHttpStringResponse(sOut, "403 Forbidden", "text/html", response);
			return;
		}

		String line, boundary, filename, filePath;
		int done, readNow, len;
		String cDisp = "Content-Disposition: form-data; name=\"filename\"; filename=\"";
		File f;
		FileOutputStream fOut;
		byte[] data = new byte[300];

		if (!uploadDir.exists()) {
			uploadDir.mkdirs();
		}

		len = 0;
		boundary = null;
		do {
			line = HTTP.readLineCRLF(sIn);
			if (line.startsWith("Content-Length: ")) {
				len = Integer.parseInt(line.split(" ")[1]);
			} else if (line.startsWith("Content-Type: multipart/form-data; boundary=")) {
				boundary = line.split("=")[1];
			}
		} while (line.length() > 0);

		if (len == 0) {
			replyPostError("Content-Length: expected and not found");
			return;
		}
		if (boundary == null) {
			replyPostError("Content-Type: multipart/form-data; expected and not found");
			return;
		}

		line = HTTP.readLineCRLF(sIn);
		if (!line.endsWith(boundary)) {
			replyPostError("Multipart separator expected and not found");
			return;
		}
		len = len - line.length() - 2;
		filename = "";
		do {
			line = HTTP.readLineCRLF(sIn);
			len = len - line.length() - 2;
			if (line.startsWith(cDisp)) {
				filename = line.split("=")[2];
				filename = filename.substring(1, filename.length() - 1);
			}
		} while (line.length() > 0);

		try {
			if (filename.length() == 0) {
				do {
					done = sIn.read(data, 0, 300);
					len = len - done;
				} while (len > 0);
				replyPostError("Content-Disposition: form-data; expected and not found (NO FILENAME)");
				return;
			}

			filePath = UPLOAD_DIR + "/" + filename;
			f = new File(filePath);
			fOut = new FileOutputStream(f);

			len = len - boundary.length() - 6;

			do {
				if (len > 300)
					readNow = 300;
				else
					readNow = len;
				done = sIn.read(data, 0, readNow);
				fOut.write(data, 0, done);
				len = len - done;
			} while (len > 0);
			fOut.close();
			line = HTTP.readLineCRLF(sIn);

			// Update file count after successful upload
			currentFiles = HTTP.countFilesInDirectory(uploadDir);
			String fileCount = currentFiles + "/" + maxFiles;

			String template = HTTP.readHtmlFile(WEB_ROOT + "/upload-success.html");
			String response = template.replace("${file_count}", fileCount);
			HTTP.sendHttpStringResponse(sOut, "200 Ok", "text/html", response);

		} catch (IOException ex) {
			System.out.println("IOException during file upload: " + ex.getMessage());
			replyPostError("Error during file upload: " + ex.getMessage());
		}
	}

	void processPostList() {
		String line;
		do {
			line = HTTP.readLineCRLF(sIn);
		} while (line.length() > 0);
		replyPostList();
	}

	void replyPostList() {
		String template = HTTP.readHtmlFile(WEB_ROOT + "/file-list.html");
		File uploadDir = new File(UPLOAD_DIR);
		String fileListItems = HTTP.generateFileListItems(uploadDir);

		int currentFiles = HTTP.countFilesInDirectory(uploadDir);
		int maxFiles = Integer.parseInt(config.getProperty("max.files", "10"));
		String fileCount = currentFiles + "/" + maxFiles;

		String response = template.replace("${file_list_items}", fileListItems)
				.replace("${file_count}", fileCount);
		HTTP.sendHttpStringResponse(sOut, "200 Ok", "text/html", response);
	}

	void replyPostError(String error) {
		String template = HTTP.readHtmlFile(WEB_ROOT + "/error.html");
		String response = template.replace("${error_message}", error);
		HTTP.sendHttpStringResponse(sOut, "500 Internal Server Error", "text/html", response);
	}

	void processPostDelete() {
		try {
			// Read headers
			int contentLength = 0;
			String line;
			do {
				line = HTTP.readLineCRLF(sIn);
				if (line.startsWith("Content-Length: ")) {
					contentLength = Integer.parseInt(line.substring("Content-Length: ".length()));
				}
			} while (line.length() > 0);

			// Read POST data
			byte[] postData = new byte[contentLength];
			sIn.readFully(postData);
			String postString = new String(postData, StandardCharsets.UTF_8);

			// Parse filename
			String[] params = postString.split("=");
			String filename = params.length > 1 ? URLDecoder.decode(params[1], StandardCharsets.UTF_8.name()) : "";

			if (!filename.isEmpty()) {
				File fileToDelete = new File(UPLOAD_DIR + "/" + filename);
				if (fileToDelete.exists()) {
					if (fileToDelete.delete()) {
						// File deleted successfully - redirect to file list
						replyPostList();
					} else {
						replyPostError("Failed to delete file");
					}
				} else {
					replyPostError("File not found");
				}
			} else {
				replyPostError("No filename specified");
			}
		} catch (Exception ex) {
			System.out.println("Error in processPostDelete: " + ex.getMessage());
			replyPostError("Error deleting file: " + ex.getMessage());
		}
	}
}