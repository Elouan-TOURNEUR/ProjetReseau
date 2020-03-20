import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
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
        System.out.println("Essai de connexion à  " + ip + " sur le port " + port + "\n");

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
        traiterLogin(client, buffer);


    }

    public static void traiterLogin(SocketChannel client, ByteBuffer buffer) {
        try {
            String reponseLogin;
            String entreeLogin;

            System.out.println("LOGIN pseudo");
            Scanner scan = new Scanner(System.in);
            entreeLogin = scan.nextLine();

            client.write(ByteBuffer.wrap(entreeLogin.getBytes()));
            client.read(buffer);

            reponseLogin = (buffer != null) ? new String(buffer.array()).trim() : "";
            buffer.clear();
            if (reponseLogin.equals("ERROR LOGIN aborting chatamu protocol")) {
                System.out.println(reponseLogin);
                client.close();
                exit(2);
            }
            else if (reponseLogin.equals("ERROR LOGIN username")) {
                System.out.println("Pseudo déja pris.");
                traiterLogin(client, buffer);
            } else {
                System.out.println(reponseLogin);
                traiterServerConnect(client, buffer);
            }

        } catch (IOException e) {
            System.err.println("Erreur E/S socket");
            e.printStackTrace();
            exit(8);
        }
    }

    private static void traiterServerConnect(SocketChannel client, ByteBuffer buffer) {
        try {
            String reponseServerConnect;
            String entreeServerConnect;

            System.out.println("SERVERCONNECT nom");
            Scanner scan = new Scanner(System.in);
            entreeServerConnect = scan.nextLine();

            client.write(ByteBuffer.wrap(entreeServerConnect.getBytes()));
            buffer.clear();
            System.out.println(buffer);
            client.read(buffer);

            reponseServerConnect = (buffer != null) ? new String(buffer.array()).trim() : "";
            //System.out.println(reponseServerConnect);
            if (reponseServerConnect.equals("ERROR SERVER")) {
                System.out.println("Serveur invalide.");
                buffer.clear();
                traiterServerConnect(client, buffer);
            } else {
                System.out.println("Vous avez rejoin le server avec succès.");
                Thread threadRead = new Thread(new ReadMessages(client));
                Thread threadWrite = new Thread(new WriteMessages(client));
                threadRead.start();
                threadWrite.start();
            }

        } catch (IOException e) {
            System.err.println("Erreur E/S socket");
            e.printStackTrace();
            exit(8);
        }
    }
}

class ReadMessages implements Runnable{

    private SocketChannel client;

    public ReadMessages(SocketChannel client){
        this.client = client;
    }

    @Override
    public void run() {
        ByteBuffer buffer = ByteBuffer.allocate(128);
        String reponseMessage;

        try {
            while (true) {
                client.read(buffer);
                buffer.flip();

                reponseMessage = (buffer != null) ? new String(buffer.array()).trim() : "";
                System.out.println(reponseMessage);

                buffer.clear();
                buffer = ByteBuffer.allocate(128);
            }
        } catch (IOException e) {
            e.printStackTrace();
            exit(0);
        }
    }
}


class WriteMessages implements Runnable{

    private SocketChannel client;

    public WriteMessages(SocketChannel client){
        this.client = client;
    }

    @Override
    public void run() {
        Scanner scan = new Scanner(System.in);
        ByteBuffer buffer = ByteBuffer.allocate(128);

        String entreeMessage;

        try {
            while (true) {
                System.out.println("MESSAGE message");
                entreeMessage = scan.nextLine();

                if(entreeMessage.equals("exit")){
                    System.out.println("c'est la fin j'envoie un dernier message au serveur.");
                    client.write(ByteBuffer.wrap(entreeMessage.getBytes()));
                    client.close();
                    System.err.println("Fin de la session.");
                    exit(0);
                }


                client.write(ByteBuffer.wrap(entreeMessage.getBytes()));
                buffer.flip();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            client.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.err.println("Fin de la session.");
        exit(0);
    }
}