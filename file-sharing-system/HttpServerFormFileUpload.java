import java.io.*;
import java.net.*;

// Classe principal do servidor HTTP para upload de ficheiros
class HttpServerFormFileUpload {
	static private ServerSocket sock;
	private static int maxFiles;

	public static void main(String args[]) throws Exception {
		// Verifica se foram passados os argumentos corretos
		if (args.length != 2) {
			System.out.println("Usage: java HttpServerFormFileUpload <port> <maxFiles>");
			System.exit(1);
		}

		try {
			// Lê o número máximo de ficheiros permitido
			maxFiles = Integer.parseInt(args[1]);
			// Cria o socket do servidor na porta indicada
			sock = new ServerSocket(Integer.parseInt(args[0]));
			System.out.println("Server started on port " + args[0]);
			System.out.println("Maximum files allowed: " + maxFiles);
			System.out.println("Client HTTP applications can connect to http://10.9.23.64:" + args[0]);

			// Ciclo infinito para aceitar ligações dos clientes
			while (true) {
				try {
					// Aceita uma ligação de um cliente
					Socket cliSock = sock.accept();
					// Cria uma nova thread para tratar o pedido do cliente
					new Thread(new HttpRequest(cliSock, maxFiles)).start();
				} catch (IOException ex) {
					System.out.println("Error accepting connection: " + ex.getMessage());
				}
			}
		} catch (NumberFormatException ex) {
			// Erro ao converter argumentos para número
			System.out.println("Invalid port number or max files value: " + ex.getMessage());
			System.exit(1);
		} catch (IOException ex) {
			// Erro ao abrir o socket na porta indicada
			System.out.println("Failed to open local port " + args[0] + ": " + ex.getMessage());
			System.exit(1);
		}
	}
}