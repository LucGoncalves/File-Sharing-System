import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

class HttpRequest implements Runnable {
	private Socket s;
	private DataOutputStream sOut;
	private DataInputStream sIn;
	private String baseFolder;

	public HttpRequest(Socket cli_s, String folder) {
		s = cli_s;
		baseFolder = folder;
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

			if (request.startsWith("POST /upload")) {
				processPostUpload();
			} else if (request.startsWith("POST /list")) {
				processPostList();
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
			if (fileName.equals("/")) {
				fileName = "/index.html";
			}

			try {
				fileName = URLDecoder.decode(fileName, StandardCharsets.UTF_8.name());
			} catch (Exception e) {
				System.out.println("Error decoding URL: " + e.getMessage());
			}

			String filePath = baseFolder + fileName;
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
		String line, boundary, filename, filePath;
		int done, readNow, len;
		String cDisp = "Content-Disposition: form-data; name=\"filename\"; filename=\"";
		File f;
		FileOutputStream fOut;
		byte[] data = new byte[300];

		String uploadDirPath = baseFolder + "/files";
		File uploadDir = new File(uploadDirPath);
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

			filePath = uploadDirPath + "/" + filename;
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
			String template = HTTP.readHtmlFile(baseFolder + "/upload-success.html");
			HTTP.sendHttpStringResponse(sOut, "200 Ok", "text/html", template);

		} catch (IOException ex) {
			System.out.println("IOException");
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
		String template = HTTP.readHtmlFile(baseFolder + "/file-list.html");
		File uploadDir = new File(baseFolder + "/files");
		String fileListItems = HTTP.generateFileListItems(uploadDir);
		String response = template.replace("${file_list_items}", fileListItems);
		HTTP.sendHttpStringResponse(sOut, "200 Ok", "text/html", response);
	}

	void replyPostError(String error) {
		String template = HTTP.readHtmlFile(baseFolder + "/error.html");
		String response = template.replace("${error_message}", error);
		HTTP.sendHttpStringResponse(sOut, "500 Internal Server Error", "text/html", response);
	}
}