package ServeurFedereRobuste;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import static java.lang.System.exit;


/* Client de la fédération de serveur robuste :
 *     - Demande un pseudo à l'utilisateur, et le transmet au serveur
 *     - Attend les messages de l'utilisateur et les transmet au serveur
 *     - Affiche (sur le terminal) les messages reçus du serveur
 *     - Reconnexion automatique vers un autre serveur en cas de non-réponse
 */
public class PairsClient {

    /* Classe de lecture des messages (Runnable) */
    public static ReadMessages readMessages;

    /* Classe d'écriture des messages (Runnable) */
    public static WriteMessages writeMessages;

    /* Liste des serveurs */
    public static int[] listeClientServer;

    /* Timer lancé la reconnexion en cas de non-réponse (annulé en cas de réponse du serveur) */
    static Timer timer;

    /* Liste des messages */
    static ConcurrentLinkedQueue<String> messages;

    /* Thread d'envoie des messages du client au serveur */
    static Thread threadWrite;

    /* Thread de réception et d'affichage des messages */
    static Thread threadRead;

    /* Port du serveur sur lequel le client est connecté */
    static int port;

    /* IP du serveur sur lequel le client est connecté */
    static String ip;

    /* Port du client : ne change pas du reconnexion à une autre */
    static int portClient;

    /* Socket vers le serveur */
    static SocketChannel client;

    /* Scanner pour l'entrée des messages */
    static Scanner scan;

    public static void main(String[] args) {
        timer = new Timer();
        messages = new ConcurrentLinkedQueue<>();
        PairsClient.scan = new Scanner(System.in);

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


        listeClientServer = new int[3] ;
        for (int i = 0; i < 3 ; i++) {
            listeClientServer[i] = port + i;
        }

        /* Session */
        traiterLogin(ByteBuffer.allocate(128));
    }

    /* Traitement du login du client */
    public static void traiterLogin(ByteBuffer buffer) {
        try {
            String reponseLogin;
            String entreeLogin;

            if((client==null) || (!client.isConnected()))
                chercherUnAutreServeur();

            portClient = client.socket().getLocalPort();

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
                traiterLogin(buffer);
            } else {
                System.out.println("Vous avez rejoint le server avec succès.");

                // Lancement des thread pour le traitement de la réception et de l'envoie des messages
                readMessages = new ReadMessages(client);
                writeMessages = new WriteMessages(client);
                threadRead = new Thread(readMessages);
                threadWrite = new Thread(writeMessages);
                threadRead.start();
                threadWrite.start();
            }

        } catch (IOException e) {
            System.err.println("Erreur E/S socket");
            e.printStackTrace();
            exit(8);
        }
    }

    /* Chercher un ature serveur sur lequel se connecter */
    public static void chercherUnAutreServeur() {
        Random rand = new Random() ;
        int nbAlea = rand.nextInt(3);

        if((client != null) && (client.isConnected())){
            try {
                client.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {
            client = SocketChannel.open();
            client.bind(new InetSocketAddress(portClient));
            client.connect(new InetSocketAddress(PairsClient.ip, PairsClient.port+nbAlea));
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println(nbAlea);
    }
}

/* Thread du traitement de la réception des messages du serveur et affichage de ce dernier */
class ReadMessages implements Runnable{

    /* Socket vers le serveur */
    private volatile SocketChannel client;

    public ReadMessages(SocketChannel client){
        this.client = client;
    }

    public synchronized void setClient(SocketChannel client){
        this.client = client;
    }

    @Override
    public void run() {
        try {
            while (client.isConnected()) {
                read();
            }
            System.out.println("je m'arrete");
        } catch (IOException e) {
        }
    }
    public void read() throws IOException {
        String reponseMessage;
        ByteBuffer buffer = ByteBuffer.allocate(128);
        client.read(buffer);
        buffer.flip();
        if (client.isConnected()){
            reponseMessage = (buffer != null) ? new String(buffer.array()).trim() : "";

            if(reponseMessage.equals("OK")) {
                WriteMessages.setCanReadIn(true);

                /* On reset le timer */
                PairsClient.timer.cancel();
                PairsClient.messages.poll();
                PairsClient.timer = new Timer();
            } else if(WriteMessages.estPret)
                System.out.println(reponseMessage);
            else{
                WriteMessages.estPret = true ;
            }
        }
        buffer.clear();
    }
}

/* Thread de traitement de la lecture des messages sur la console et de l'envoie des messages sur le serveur */
class WriteMessages implements Runnable{

    /* Socket vers le serveur */
    private volatile SocketChannel client;

    /* Booléen déterminant si le thread peux lire un message dans la console ou non */
    private static volatile boolean canReadIn;

    /* Booléen déterminant si le thread est prêt ou non */
    static public boolean estPret ;

    public WriteMessages(SocketChannel client){
        this.client = client;
    }

    public synchronized void setClient(SocketChannel client){
        this.client = client;
    }

    public static synchronized void setCanReadIn(boolean canReadIn){
        WriteMessages.canReadIn = canReadIn;
    }

    @Override
    public void run() {
        ByteBuffer buffer = ByteBuffer.allocate(128);
        estPret = true ;
        canReadIn = true;
        String entreeMessage = null;
        PairsClient.scan = new Scanner(System.in);

        try {
            while (client.isConnected()) {
                /* Attente active */
                while(!canReadIn){
                    Thread.sleep(50);
                }

                System.out.println("MESSAGE message");
                entreeMessage = PairsClient.scan.nextLine();
                write(entreeMessage, buffer);
            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException ignored) {}
    }


    /* Ecriture sur le serveur */
    private void write(String entreeMessage, ByteBuffer buffer) throws IOException {
        PairsClient.messages.add(entreeMessage);
        if(!client.isConnected()) return;

        if(entreeMessage.equals("exit")){
            System.out.println("c'est la fin j'envoie un dernier message au serveur.");
            client.write(ByteBuffer.wrap(entreeMessage.getBytes()));
            client.close();
            System.err.println("Fin de la session.");
            exit(0);
        }
        try {
            client.write(ByteBuffer.wrap(entreeMessage.getBytes()));

            // On set la fin du timer 3 secondes après la date courante
            GregorianCalendar gc = new GregorianCalendar();
            gc.add(Calendar.SECOND, 3);
            PairsClient.timer.schedule(new CheckResponseServer(), gc.getTime());
        } catch (IOException e){
            e.printStackTrace();
        }
        buffer.flip();
        canReadIn = false;
    }
}

/* Classe timer permettant de se reconnecter sur un autre serveur en cas de non réponse du serveur courant */
class CheckResponseServer extends TimerTask {

    @Override
    public void run() {
        try {
            PairsClient.client.write(ByteBuffer.wrap("DISCONNECT".getBytes()));
            PairsClient.client.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        PairsClient.threadWrite.interrupt();
        PairsClient.threadRead.interrupt();

        PairsClient.chercherUnAutreServeur();
        PairsClient.readMessages = new ReadMessages(PairsClient.client);
        PairsClient.writeMessages = new WriteMessages(PairsClient.client);
        PairsClient.threadRead = new Thread(PairsClient.readMessages);
        PairsClient.threadWrite = new Thread(PairsClient.writeMessages);
        PairsClient.threadRead.start();
        PairsClient.threadWrite.start();
    }
}
