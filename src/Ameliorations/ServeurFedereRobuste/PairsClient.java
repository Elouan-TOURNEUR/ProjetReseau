package Ameliorations.ServeurFedereRobuste;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Random;
import java.util.Scanner;
import static java.lang.System.exit;

/* Client de la fédération de serveur robuste :
*     - Demande un pseudo à l'utilisateur, et le transmet au serveur
*     - Attend les messages de l'utilisateur et les transmet au serveur
*     - Affiche (sur le terminal) les messages reçus du serveur
*/
public class PairsClient {

    /* Classe de lecture des messages (Runnable) */
    public static ReadMessages readMessages;

    /* Classe d'écriture des messages (Runnable) */
    public static WriteMessages writeMessages;

    /* Liste des serveurs */
    public static SocketChannel[] listeClientServer;

    public static void main(String[] args) {
        String ip; // adresse IPv4 du serveur en notation pointée
        int port; // port TCP serveur

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


        listeClientServer = new SocketChannel[3] ;
        SocketChannel clientServer1 = null;
        listeClientServer[0] = clientServer1 ;
        SocketChannel clientServer2 = null;
        listeClientServer[1] = clientServer2 ;
        SocketChannel clientServer3 = null;
        listeClientServer[2] = clientServer3 ;

        ByteBuffer buffer = null;

        try {
            for (int i = 0; i < 3 ; i++) {
                listeClientServer[i] = SocketChannel.open(new InetSocketAddress(ip, port + i));
            }
            buffer = ByteBuffer.allocate(256);
        } catch (IOException e) {
            System.err.println("Connexion: hôte inconnu : " + ip);
            e.printStackTrace();
        }

        /* Session */
        traiterLogin(listeClientServer, buffer);
    }

    /* Demande du login au client */
    public static void traiterLogin(SocketChannel[] clients, ByteBuffer buffer) {
        try {
            String reponseLogin;
            String entreeLogin;

            System.out.println("LOGIN pseudo");
            Scanner scan = new Scanner(System.in);
            entreeLogin = scan.nextLine();
            for (int i = 0; i < 3 ; i++) {
                if (clients[i].isConnected())
                    clients[i].write(ByteBuffer.wrap(entreeLogin.getBytes()));
            }
            SocketChannel client = chercherUnAutreServeur() ;
            client.read(buffer);

            reponseLogin = (buffer != null) ? new String(buffer.array()).trim() : "";
            buffer.clear();
            if (reponseLogin.equals("ERROR LOGIN aborting chatamu protocol")) {
                System.out.println(reponseLogin);
                clients[2].close();
                exit(2);
            }
            else if (reponseLogin.equals("ERROR LOGIN username")) {
                System.out.println("Pseudo déja pris.");
                traiterLogin(clients, buffer);
            } else {
                System.out.println("Vous avez rejoin le server avec succès.");
                readMessages = new ReadMessages(client);
                writeMessages = new WriteMessages(client);
                Thread threadRead = new Thread(readMessages);
                Thread threadWrite = new Thread(writeMessages);
                threadRead.start();
                threadWrite.start();
            }

        } catch (IOException e) {
            System.err.println("Erreur E/S socket");
            e.printStackTrace();
            exit(8);
        }
    }

    /* Recherche un autre serveur de manière aléatoire */
    public static SocketChannel chercherUnAutreServeur() {
        Random rand = new Random() ;
        int nbAlea = rand.nextInt(3) ;
        if (listeClientServer[nbAlea].isConnected()){
            System.out.println(nbAlea);
            return listeClientServer[nbAlea] ;
        }
        else
            return chercherUnAutreServeur() ;
    }
}

/*
 * Thread de réception des messages et les affiche
 */
class ReadMessages implements Runnable{

    private volatile SocketChannel client;

    public ReadMessages(SocketChannel client){
        this.client = client;
    }

    public void setClient(SocketChannel client){
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
                if(WriteMessages.estPret)
                    System.out.println(reponseMessage);
                else{
                    WriteMessages.estPret = true ;
                }
                buffer.clear();
                buffer = ByteBuffer.allocate(128);
            }
        } catch (IOException e) {
            client = PairsClient.chercherUnAutreServeur();
            PairsClient.writeMessages.setClient(client);
            ReadMessages readMessages = new ReadMessages(client);
            Thread threadRead = new Thread(readMessages);
            WriteMessages.estPret = false ;
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
            threadRead.start();

        }
    }
}

/*
 * Thread de lecture et d'envoie des messages de ce client
 */
class WriteMessages implements Runnable{

    private volatile SocketChannel client;
    static public boolean estPret ;

    public WriteMessages(SocketChannel client){
        this.client = client;
    }

    public void setClient(SocketChannel client){
        this.client = client;
    }

    @Override
    public void run() {
        Scanner scan = new Scanner(System.in);
        ByteBuffer buffer = ByteBuffer.allocate(128);
        estPret = true ;
        String entreeMessage;

        try {
            while (true) {
                System.out.println("MESSAGE message");
                entreeMessage = scan.nextLine();

                /* Déconnecte la session et ferme l'application */
                if(entreeMessage.equals("exit")){
                    System.out.println("c'est la fin j'envoie un dernier message au serveur.");
                    client.write(ByteBuffer.wrap(entreeMessage.getBytes()));
                    client.close();
                    System.err.println("Fin de la session.");
                    exit(0);
                }

                try {
                    client.write(ByteBuffer.wrap(entreeMessage.getBytes()));
                } catch (IOException e){
                    this.client = PairsClient.chercherUnAutreServeur();
                    PairsClient.readMessages.setClient(this.client);
                }

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