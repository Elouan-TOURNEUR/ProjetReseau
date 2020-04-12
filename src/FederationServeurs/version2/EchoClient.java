package FederationServeurs.version2;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.SocketChannel;
import java.util.Scanner;

import static java.lang.System.exit;
import static java.lang.System.out;

public class EchoClient {

    static volatile SocketChannel client;
    static volatile WriteMessages writeMessages;
    static volatile ReadMessages readMessages;
    static volatile Thread threadWrite;
    static volatile Thread threadRead;

    public static void main(String[] args) throws IOException {
        Socket echoSocket; // la socket client
        String ip; // adresse IPv4 du serveur en notation pointée
        int port; // port TCP serveur
        boolean fini = false;

        /* Traitement des arguments */
        if (args.length != 2) {
            /* erreur de syntaxe */
            System.out.println("Usage: java EchoClient ipServer port");
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

        try {
            EchoClient.client = SocketChannel.open(new InetSocketAddress(ip, port));
            System.out.println(EchoClient.client.toString());
            //System.err.println("le n° de la socket est : " + client);
        } catch (IOException e) {
            System.err.println("Connexion: hôte inconnu : " + ip);
            e.printStackTrace();
        }

        /* Session */
        readMessages = new ReadMessages();
        writeMessages = new WriteMessages();
        threadRead = new Thread(readMessages);
        threadWrite = new Thread(writeMessages);
        threadWrite.start();
        threadRead.start();
    }
}

class ReadMessages implements Runnable{

    public ReadMessages(){}

    @Override
    public void run() {
        ByteBuffer buffer = ByteBuffer.allocate(128);
        String reponseMessage;

        try {
            while (true) {
                EchoClient.client.read(buffer);
                buffer.flip();

                reponseMessage = (buffer != null) ? new String(buffer.array()).trim() : "";

                if(reponseMessage.split(" ")[0].equals("CONNECT")){
                    int localPort = EchoClient.client.socket().getLocalPort();
                    EchoClient.client.close();

                    String address = reponseMessage.split(" ")[1];
                    int port = Integer.parseInt(reponseMessage.split(" ")[2]);

                    EchoClient.client = SocketChannel.open();
                    EchoClient.client.bind(new InetSocketAddress(localPort));
                    // TODO problème de connexion de temps en temps
                    EchoClient.client.connect(new InetSocketAddress(address, port));

                    System.out.println("Vous avez rejoint le server avec succès.");
                } else if(!reponseMessage.split(" ")[0].equals("OK")){
                    System.out.println(reponseMessage);
                }

                buffer.clear();
                buffer = ByteBuffer.allocate(128);
            }
        } catch (ClosedByInterruptException e){

        } catch (IOException e) {
            e.printStackTrace();
            exit(1);
        }
    }
}


class WriteMessages implements Runnable{

    public WriteMessages(){}

    @Override
    public void run() {
        Scanner scan = new Scanner(System.in);
        ByteBuffer buffer = ByteBuffer.allocate(128);

        String entreeMessage;

        try {
            while (true) {
                System.out.println("MESSAGE message");
                entreeMessage = scan.nextLine();

                if(entreeMessage.equals("EXIT")){
                    EchoClient.client.write(ByteBuffer.wrap(entreeMessage.getBytes()));
                    EchoClient.threadRead.interrupt();
                    Thread.sleep(100);
                    break;
                }

                EchoClient.client.write(ByteBuffer.wrap(entreeMessage.getBytes()));
                buffer.flip();
            }

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }

        try {
            EchoClient.client.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.err.println("Fin de la session.");
        exit(0);
    }
}