package ServeurFedereRobuste;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

import static java.lang.System.exit;

public class PairsClient {

    public static ReadMessages readMessages;
    public static WriteMessages writeMessages;
    public static int[] listeClientServer;

    public static Long[] tempsEmetteur ;
    public static boolean[] reçu ;

    static Timer timer;
    static ConcurrentLinkedQueue<String> messages;
    static Thread threadWrite;
    static Thread threadRead;
    static int port;
    static int portClient;
    static String ip;
    static SocketChannel client;
    static Scanner scan;

    public static String pseudo = null ;

    public static void main(String[] args) throws IOException {
        Socket echoSocket; // la socket client
        boolean fini = false;
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
                pseudo = entreeLogin.split(" ")[1] ;
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
            //chercherUnAutreServeur();
        }
        System.out.println(nbAlea);
        /*if (client.isConnected()){
            System.out.println(nbAlea);
        }
        else
            chercherUnAutreServeur();*/
    }
}

class ReadMessages implements Runnable{

    private volatile SocketChannel client;
    private static boolean run = true ;

    /*public static void setRun(boolean run) {
        ReadMessages.run = run;
    }*/

    public ReadMessages(SocketChannel client){
        this.client = client;
    }

    public synchronized void setClient(SocketChannel client){
        this.client = client;
    }

    /*public void avertir(){
        TimeMessage.setReçu(true);
    }*/

    @Override
    public void run() {
        try {
            while (client.isConnected()) {
                read();
            }
            System.out.println("je m'arrete");
        } catch (IOException e) {
            /*client = PairsClient.chercherUnAutreServeur();
            PairsClient.writeMessages.setClient(client);
            ReadMessages readMessages = new ReadMessages(client);
            Thread threadRead = new Thread(readMessages);
            WriteMessages.estPret = false ;
            try {
                Thread.sleep(10);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
            threadRead.start();*/
            //e.printStackTrace();
        }
    }
    public void read() throws IOException {
        String reponseMessage;
        ByteBuffer buffer = ByteBuffer.allocate(128);
        client.read(buffer);
        buffer.flip();
        if (client.isConnected()){
            reponseMessage = (buffer != null) ? new String(buffer.array()).trim() : "";
            /*if (reponseMessage.split(" ")[0].equals(PairsClient.pseudo + ">") || reponseMessage.equals("ok")) {

            }*/
            if(reponseMessage.equals("OK")) {
                WriteMessages.setCanReadIn(true);
                PairsClient.timer.cancel();
                //System.out.println("CANCELED");
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


class WriteMessages implements Runnable{
    //public static TimeMessage timeMessage ;
    private volatile SocketChannel client;
    private static volatile boolean canReadIn;
    static public boolean estPret ;
    public static int indice = 0 ;

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

        /*try {
            client.close();
        } catch (IOException e) {
            e.printStackTrace();
        }*/

        //System.err.println("Fin de la session.");
        //exit(0);
    }


    private void write(String entreeMessage, ByteBuffer buffer) throws IOException, InterruptedException {
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
            //Long time = System.nanoTime() ;
            client.write(ByteBuffer.wrap(entreeMessage.getBytes()));


            GregorianCalendar gc = new GregorianCalendar();
            gc.add(Calendar.SECOND, 3);
            PairsClient.timer.schedule(new CheckResponseServer(), gc.getTime());

            /*TimeMessage timeMessage = new TimeMessage(time, entreeMessage) ;
            Thread timeMess = new Thread(timeMessage);
            timeMess.start();*/
        } catch (IOException e){
            /*this.client = PairsClient.chercherUnAutreServeur();
            PairsClient.readMessages.setClient(this.client);*/
            e.printStackTrace();
        }
        buffer.flip();
        canReadIn = false;
    }
}


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

        /*try {
            Thread.sleep(100);
            Robot robot = new Robot();
            robot.keyPress(KeyEvent.VK_ENTER);
            Thread.sleep(50);
            robot.keyRelease(KeyEvent.VK_ENTER);
        } catch (AWTException | InterruptedException e) {
            e.printStackTrace();
        }*/

        PairsClient.chercherUnAutreServeur();
        PairsClient.readMessages = new ReadMessages(PairsClient.client);
        PairsClient.writeMessages = new WriteMessages(PairsClient.client);
        PairsClient.threadRead = new Thread(PairsClient.readMessages);
        PairsClient.threadWrite = new Thread(PairsClient.writeMessages);
        PairsClient.threadRead.start();
        PairsClient.threadWrite.start();
    }
}



/*class TimeMessage implements Runnable {
    private long tempsReferant ;
    private String message ;
    private static long seuil = 2 ;
    private static boolean reçu = false ;

    public static void setReçu(boolean reçu) {
        TimeMessage.reçu = reçu;
    }

    public TimeMessage(long temps, String entree) {
        this.tempsReferant = temps ;
        this.message = entree ;
    }

    @Override
    public void run() {
        try {
            long temps = System.nanoTime() ;
            while ((temps - tempsReferant)/1000000000 < seuil){
                temps = System.nanoTime() ;
                Thread.sleep(100);
                if (reçu) {
                    return;
                }
            }
            SocketChannel channel = PairsClient.chercherUnAutreServeur() ;
            ReadMessages.setRun(false) ;
            //System.out.println("je change de serveur " + channel.toString());
            PairsClient.readMessages.setClient(channel);
            PairsClient.writeMessages.setClient(channel);
            ReadMessages readMessages = new ReadMessages(channel) ;
            Thread threadRead = new Thread(readMessages);
            threadRead.start();
            ByteBuffer buffer = ByteBuffer.allocate(128);
            PairsClient.writeMessages.write(message, buffer);
        } catch (InterruptedException | IOException ex) {
            ex.printStackTrace();
        }
    }
}*/
