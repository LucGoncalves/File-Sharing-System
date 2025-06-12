import java.io.*;
import java.net.*;

class HttpServerFormFileUpload {
	static private ServerSocket sock;
	private static int maxFiles;

	public static void main(String args[]) throws Exception {
		if (args.length != 2) {
			System.out.println("Usage: java HttpServerFormFileUpload <port> <maxFiles>");
			System.exit(1);
		}

		try {
			maxFiles = Integer.parseInt(args[1]);
			sock = new ServerSocket(Integer.parseInt(args[0]));
			System.out.println("Server started on port " + args[0]);
			System.out.println("Maximum files allowed: " + maxFiles);
			System.out.println("Client HTTP applications can connect to http://10.9.23.64:" + args[0]);

			while (true) {
				try {
					Socket cliSock = sock.accept();
					new Thread(new HttpRequest(cliSock, maxFiles)).start();
				} catch (IOException ex) {
					System.out.println("Error accepting connection: " + ex.getMessage());
				}
			}
		} catch (NumberFormatException ex) {
			System.out.println("Invalid port number or max files value: " + ex.getMessage());
			System.exit(1);
		} catch (IOException ex) {
			System.out.println("Failed to open local port " + args[0] + ": " + ex.getMessage());
			System.exit(1);
		}
	}
}