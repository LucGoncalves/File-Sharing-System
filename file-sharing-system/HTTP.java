import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

// Classe para operações HTTP
class HTTP {
	static private final String HTTP_VERSION = "HTTP/1.0";
	static private final String HTTP_CONNECTION_CLOSE = "Connection: close";
	static private final String HTTP_CRLF = "\r\n";

	// Lê uma linha terminada em CRLF de um DataInputStream
	static String readLineCRLF(DataInputStream sIn) {
		if (sIn == null) {
			return null;
		}

		byte[] line = new byte[300];
		int p = 0;
		try {
			while (p < line.length) {
				int bytesRead = sIn.read(line, p, 1); // Lê um byte de cada vez
				if (bytesRead == -1) {
					return p > 0 ? new String(line, 0, p) : null; // Fim do stream
				}
				if (line[p] == '\n') {
					return new String(line, 0, p); // Linha completa
				} else if (line[p] != '\r') {
					p++; // Avança se não for '\r'
				}
			}
			return new String(line, 0, p); // Linha demasiado longa
		} catch (IOException ex) {
			System.out.println("READ IOException: " + ex.getMessage());
			return null;
		}
	}

	// Escreve uma linha terminada em CRLF num DataOutputStream
	static void writeLineCRLF(DataOutputStream sOut, String line) {
		try {
			sOut.write(line.getBytes(), 0, (byte) line.length());
			sOut.write(HTTP_CRLF.getBytes(), 0, 2);
		} catch (IOException ex) {
			System.out.println("WRITE IOException");
		}
	}

	// Envia o cabeçalho de resposta HTTP
	static void sendHttpResponseHeader(DataOutputStream sOut, String status, String contentType, int contentLength) {
		writeLineCRLF(sOut, HTTP_VERSION + " " + status);
		writeLineCRLF(sOut, "Content-Type: " + contentType);
		writeLineCRLF(sOut, "Content-Length: " + contentLength);
		writeLineCRLF(sOut, HTTP_CONNECTION_CLOSE);
		writeLineCRLF(sOut, "");
	}

	// Envia uma resposta HTTP com conteúdo em bytes
	static void sendHttpResponse(DataOutputStream sOut, String status, String contentType,
			byte[] content, int contentLength) {
		sendHttpResponseHeader(sOut, status, contentType, contentLength); // Escreve o conteúdo
		try {
			sOut.write(content, 0, contentLength);
		} catch (IOException ex) {
			System.out.println("IOException");
		}
	}

	// Envia uma resposta HTTP com conteúdo em String
	static void sendHttpStringResponse(DataOutputStream sOut, String status, String contentType, String content) {
		sendHttpResponse(sOut, status, contentType, content.getBytes(), content.length());
	}

	// Envia um ficheiro como resposta HTTP
	static void sendHttpFileResponse(DataOutputStream sOut, String status, String filePath) {
		String responseStatus = "200 Ok";
		String contentType = "application/octet-stream"; // Tipo padrão

		File f = new File(filePath);
		if (!f.exists()) {
			// Se o ficheiro não existir, envia erro 404
			sendHttpStringResponse(sOut, "404 Not Found", "text/html",
					"<html><body><h1>404 File not found</h1></body></html>");
			return;
		}

		// Determinar o tipo correto baseado na extensão do arquivo
		if (filePath.endsWith(".pdf")) {
			contentType = "application/pdf";
		} else if (filePath.endsWith(".txt")) {
			contentType = "text/plain";
		} else if (filePath.endsWith(".gif")) {
			contentType = "image/gif";
		} else if (filePath.endsWith(".png")) {
			contentType = "image/png";
		} else if (filePath.endsWith(".html") || filePath.endsWith(".htm")) {
			contentType = "text/html";
		}

		if (status != null) {
			responseStatus = status;
		}

		int len = (int) f.length();
		sendHttpResponseHeader(sOut, responseStatus, contentType, len);

		// Envia o ficheiro em blocos de bytes
		try (InputStream fReader = new BufferedInputStream(new FileInputStream(f))) {
			byte[] buffer = new byte[4096];
			int bytesRead;
			while ((bytesRead = fReader.read(buffer)) != -1) {
				sOut.write(buffer, 0, bytesRead);
			}
		} catch (IOException ex) {
			System.out.println("Error sending file: " + ex.getMessage());
		}
	}

	// Lê um ficheiro HTML e devolve o seu conteúdo como String
	static String readHtmlFile(String filePath) {
		try {
			File file = new File(filePath);
			byte[] content = new byte[(int) file.length()];
			FileInputStream fis = new FileInputStream(file);
			fis.read(content);
			fis.close();
			return new String(content, "UTF-8");
		} catch (IOException ex) {
			System.out.println("Error reading HTML file: " + ex.getMessage());
			return "<html><body><h1>Error loading page</h1></body></html>";
		}
	}

	// Gera a lista de ficheiros em HTML para mostrar ao utilizador
	static String generateFileListItems(File directory) {
		StringBuilder items = new StringBuilder();
		File[] filesList = directory.listFiles();
		if (filesList != null) {
			for (File f : filesList) {
				if (f.isFile()) {
					// Codifica o nome do ficheiro para URL
					String encodedName = URLEncoder.encode(f.getName(), StandardCharsets.UTF_8)
							.replace("+", "%20");
					items.append("<li style=\"margin-bottom: 10px; display: flex; align-items: center;\">")
							.append("<div style=\"flex-grow: 1;\">")
							.append("<a class=\"file-link\" href=\"/files/").append(encodedName).append("\">")
							.append(f.getName()).append("</a>")
							.append("<span class=\"file-size\" style=\"margin-left: 10px;\">(")
							.append(formatFileSize(f.length()))
							.append(")</span>")
							.append("</li>");
				}
			}
		}
		return items.toString();
	}

	// Formata o tamanho do ficheiro para uma string legível
	static String formatFileSize(long size) {
		if (size < 1024)
			return size + " B";
		int exp = (int) (Math.log(size) / Math.log(1024));
		String pre = "KMGTPE".charAt(exp - 1) + "";
		return String.format("%.1f %sB", size / Math.pow(1024, exp), pre);
	}

	// Conta o número de ficheiros num diretório
	static int countFilesInDirectory(File directory) {
		File[] files = directory.listFiles();
		return files != null ? files.length : 0;
	}
}
