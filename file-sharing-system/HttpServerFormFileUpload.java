import java.io.*;
import java.net.*;

class HttpServerFormFileUpload {
	static private final String BASE_FOLDER = "www";
	static private ServerSocket sock;

	public static void main(String args[]) throws Exception {
		if (args.length != 1) {
			System.out.println("Local port number required at the command line.");
			System.exit(1);
		}

		try {
			sock = new ServerSocket(Integer.parseInt(args[0]));
			System.out.println("Server started on port " + args[0]);

			while (true) {
				try {
					Socket cliSock = sock.accept();
					new Thread(new HttpRequest(cliSock, BASE_FOLDER)).start();
				} catch (IOException ex) {
					System.out.println("Error accepting connection: " + ex.getMessage());
				}
			}
		} catch (IOException ex) {
			System.out.println("Failed to open local port " + args[0] + ": " + ex.getMessage());
			System.exit(1);
		}
	}
}