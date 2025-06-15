import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

// Classe que trata cada pedido HTTP do cliente
class HttpRequest implements Runnable {
	private Socket s;
	private DataOutputStream sOut;
	private DataInputStream sIn;
	// Diretórios base para os ficheiros e páginas web
	private static final String BASE_FOLDER = "/var/www/rcomp";
	private static final String UPLOAD_DIR = BASE_FOLDER + "/files";
	private static final String WEB_ROOT = BASE_FOLDER + "/www";
	private static final Properties config = new Properties();
	private final int maxFiles;

	// Carrega as configurações do ficheiro properties ao iniciar a classe
	static {
		try {
			config.load(new FileInputStream(BASE_FOLDER + "/config.properties"));
		} catch (IOException e) {
			System.out.println("Error loading config.properties: " + e.getMessage());
		}
	}

	// Construtor recebe o socket do cliente e o número máximo de ficheiros
	public HttpRequest(Socket cli_s, int maxFiles) {
		s = cli_s;
		this.maxFiles = maxFiles;
	}

	// Método principal que trata o pedido do cliente
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
			// Lê a primeira linha do pedido HTTP
			String request = HTTP.readLineCRLF(sIn);
			if (request == null || request.isEmpty()) {
				// Pedido inválido
				HTTP.sendHttpStringResponse(sOut, "400 Bad Request", "text/html",
						"<html><body><h1>400 Bad Request</h1></body></html>");
				return;
			}

			// Verifica o tipo de pedido e chama o método correspondente
			if (request.startsWith("POST /home")) {
				processPostLogin();
			} else if (request.startsWith("POST /upload")) {
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

	// Processa o login do utilizador
	void processPostLogin() {
		try {
			// Lê os cabeçalhos do pedido
			int contentLength = 0;
			String line;
			do {
				line = HTTP.readLineCRLF(sIn);
				if (line.startsWith("Content-Length: ")) {
					contentLength = Integer.parseInt(line.substring("Content-Length: ".length()));
				}
			} while (line.length() > 0);

			// Lê os dados do POST (username e password)
			byte[] postData = new byte[contentLength];
			sIn.readFully(postData);
			String postString = new String(postData, StandardCharsets.UTF_8);

			// Separa os parâmetros do formulário
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

			// Valida as credenciais com as do ficheiro de configuração
			String validUsername = config.getProperty("auth.username");
			String validPassword = config.getProperty("auth.password");

			if (username.equals(validUsername) && password.equals(validPassword)) {
				// Login com sucesso - mostra página principal com contagem de ficheiros
				File uploadDir = new File(UPLOAD_DIR);
				int currentFiles = HTTP.countFilesInDirectory(uploadDir);
				int maxFiles = this.maxFiles;
				String fileCount = currentFiles + "/" + maxFiles;

				String template = HTTP.readHtmlFile(WEB_ROOT + "/home.html");
				String response = template.replace("${file_count}", fileCount);
				HTTP.sendHttpStringResponse(sOut, "200 Ok", "text/html", response);
			} else {
				// Login falhado - mostra mensagem de erro
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

	// Processa pedidos GET (ex: páginas HTML ou ficheiros)
	void processGet(String req) {
		try {
			// Lê os cabeçalhos do pedido
			String line;
			do {
				line = HTTP.readLineCRLF(sIn);
				if (line == null) {
					HTTP.sendHttpStringResponse(sOut, "400 Bad Request", "text/html",
							"<html><body><h1>400 Bad Request</h1></body></html>");
					return;
				}
			} while (line.length() > 0);

			// Extrai o nome do ficheiro pedido
			String fileName = req.split(" ")[1];
			if (fileName.equals("/") || fileName.equals("/index.html")) {
				// Página inicial
				String template = HTTP.readHtmlFile(WEB_ROOT + "/index.html");
				String response = template.replace("${error_message}", "");
				HTTP.sendHttpStringResponse(sOut, "200 Ok", "text/html", response);
				return;
			} else if (fileName.equals("/home.html")) {
				// Página principal após login
				File uploadDir = new File(UPLOAD_DIR);
				int currentFiles = HTTP.countFilesInDirectory(uploadDir);
				int maxFiles = this.maxFiles;
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

			// Decodifica o nome do ficheiro (caso tenha espaços ou caracteres especiais)
			try {
				fileName = URLDecoder.decode(fileName, StandardCharsets.UTF_8.name());
			} catch (Exception e) {
				System.out.println("Error decoding URL: " + e.getMessage());
			}

			// Determina o caminho completo do ficheiro pedido
			String filePath;
			if (fileName.startsWith("/files/")) {
				filePath = UPLOAD_DIR + fileName.substring("/files".length());
			} else {
				filePath = WEB_ROOT + fileName;
			}

			File f = new File(filePath);
			if (!f.exists() || !f.isFile()) {
				// Ficheiro não encontrado
				HTTP.sendHttpStringResponse(sOut, "404 Not Found", "text/html",
						"<html><body><h1>404 File not found</h1></body></html>");
				return;
			}

			// Envia o ficheiro ao cliente
			HTTP.sendHttpFileResponse(sOut, null, filePath);

		} catch (Exception ex) {
			System.out.println("Error in processGet: " + ex.getMessage());
			HTTP.sendHttpStringResponse(sOut, "500 Internal Server Error", "text/html",
					"<html><body><h1>500 Server Error</h1></body></html>");
		}
	}

	// Processa o upload de ficheiros enviados pelo utilizador
	void processPostUpload() {
		File uploadDir = new File(UPLOAD_DIR);
		int currentFiles = HTTP.countFilesInDirectory(uploadDir);
		int maxFiles = this.maxFiles;
		// Verifica se já atingiu o limite de ficheiros
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

		// Cria o diretório de uploads se não existir
		if (!uploadDir.exists()) {
			uploadDir.mkdirs();
		}

		len = 0;
		boundary = null;
		// Lê os cabeçalhos do pedido para obter o tamanho e boundary
		do {
			line = HTTP.readLineCRLF(sIn);
			if (line.startsWith("Content-Length: ")) {
				len = Integer.parseInt(line.split(" ")[1]);
			} else if (line.startsWith("Content-Type: multipart/form-data; boundary=")) {
				boundary = line.split("=")[1];
			}
		} while (line.length() > 0);

		// Verifica se recebeu os dados necessários
		if (len == 0) {
			replyPostError("Content-Length: expected and not found");
			return;
		}
		if (boundary == null) {
			replyPostError("Content-Type: multipart/form-data; expected and not found");
			return;
		}

		// Lê a linha do separador multipart
		line = HTTP.readLineCRLF(sIn);
		if (!line.endsWith(boundary)) {
			replyPostError("Multipart separator expected and not found");
			return;
		}
		len = len - line.length() - 2;
		filename = "";
		// Procura o nome do ficheiro no cabeçalho Content-Disposition
		do {
			line = HTTP.readLineCRLF(sIn);
			len = len - line.length() - 2;
			if (line.startsWith(cDisp)) {
				filename = line.split("=")[2];
				filename = filename.substring(1, filename.length() - 1);
			}
		} while (line.length() > 0);

		try {
			// Se não houver nome de ficheiro, ignora o conteúdo
			if (filename.length() == 0) {
				do {
					done = sIn.read(data, 0, 300);
					len = len - done;
				} while (len > 0);
				replyPostError("Content-Disposition: form-data; expected and not found (NO FILENAME)");
				return;
			}

			// Caminho completo para guardar o ficheiro
			filePath = UPLOAD_DIR + "/" + filename;
			f = new File(filePath);
			fOut = new FileOutputStream(f);

			// Ajusta o tamanho a ler (retira o tamanho do boundary final)
			len = len - boundary.length() - 6;

			// Lê e grava o ficheiro em blocos
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

			// Atualiza a contagem de ficheiros após upload
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

	// Processa o pedido para listar ficheiros
	void processPostList() {
		String line;
		do {
			line = HTTP.readLineCRLF(sIn);
		} while (line.length() > 0);
		replyPostList();
	}

	// Gera e envia a página HTML com a lista de ficheiros
	void replyPostList() {
		String template = HTTP.readHtmlFile(WEB_ROOT + "/file-list.html");
		File uploadDir = new File(UPLOAD_DIR);
		String fileListItems = HTTP.generateFileListItems(uploadDir);

		int currentFiles = HTTP.countFilesInDirectory(uploadDir);
		int maxFiles = this.maxFiles;
		String fileCount = currentFiles + "/" + maxFiles;

		String response = template.replace("${file_list_items}", fileListItems)
				.replace("${file_count}", fileCount);
		HTTP.sendHttpStringResponse(sOut, "200 Ok", "text/html", response);
	}

	// Envia uma página de erro ao cliente
	void replyPostError(String error) {
		String template = HTTP.readHtmlFile(WEB_ROOT + "/error.html");
		String response = template.replace("${error_message}", error);
		HTTP.sendHttpStringResponse(sOut, "500 Internal Server Error", "text/html", response);
	}
}