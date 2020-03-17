import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Scanner;

import static java.lang.System.exit;

public class EchoClient {

    public static void main(String[] args) throws IOException {
        Socket echoSocket; // la socket client
        String ip; // adresse IPv4 du serveur en notation pointée
        int port; // port TCP serveur
        boolean fini = false;

        /* Traitement des arguments */
        if (args.length != 2) {
            /* erreur de syntaxe */
            System.out.println("Usage: java EchoClient @server @port");
            exit(1);
        }
        ip = args[0];
        port = Integer.parseInt(args[1]);

        if (port > 65535) {
            System.err.println("Port hors limite");
            exit(3);
        }

        /* Connexion */
        System.out.println("Essai de connexion à  " + ip + " sur le port " + port + "\n");

        SocketChannel client = null;
        ByteBuffer buffer = null;

        try {
            client = SocketChannel.open(new InetSocketAddress(ip, port));
            System.err.println("le n° de la socket est : " + client);
            buffer = ByteBuffer.allocate(256);
        } catch (IOException e) {
            System.err.println("Connexion: hôte inconnu : " + ip);
            e.printStackTrace();
        }

        /* Session */
        try {
            String reponseLogin;
            String entreeLogin;

            System.out.println("LOGIN pseudo");
            Scanner scan = new Scanner(System.in);
            entreeLogin = scan.nextLine();

            client.write(ByteBuffer.wrap(entreeLogin.getBytes())); //out.println(entreeLogin);
            client.read(buffer); //in.readLine();

            reponseLogin = (buffer != null) ? new String(buffer.array()).trim() : "";
            if (reponseLogin.equals("ERROR LOGIN aborting chatamu protocol")) {
                System.out.println(reponseLogin);
                client.close();
                exit(2);
            }
            buffer.clear();

            while (true) {
                String entreeMessage;
                String reponseMessage;

                System.out.println("MESSAGE message");
                entreeMessage = scan.nextLine();

                if(entreeMessage.equals("exit")){
                    break;
                }

                client.write(ByteBuffer.wrap(entreeMessage.getBytes())); //out.println(entreeMessage);
                client.read(buffer); //reponseMessage = in.readLine();
                reponseMessage = (buffer != null) ? new String(buffer.array()).trim() : "";
                if (reponseMessage.equals("ERROR chatamu")) {
                    System.out.println(reponseMessage);
                }
            }

            client.close();
            System.err.println("Fin de la session.");
        } catch (IOException e) {
            System.err.println("Erreur E/S socket");
            e.printStackTrace();
            exit(8);
        }
    }
}