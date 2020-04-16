package ServeurFedereRobuste;

import java.beans.EventHandler;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

import static java.lang.System.*;

/*
 -  attribuer une horloge vectorielle à chaque message diffusé et à utiliser les informations de causalité dans l'horloge vectorielle
  pour décider à chaque destination quand un message peut être délivré
 - Si un message arrive à une destination donnée avant que les messages précédents causalement aient été remis, le service retarde la
 livraison de ce message jusqu'à ce que ces messages arrivent et soient remis
 - méthode co_broadcast() envoie à tout le monde
 - méthode co_deliver() réception du message et traitement
 */

public class PairsServer {
    public static String name = null;
    public static Integer port = null;
    private static String ip = "127.0.0.1";
    public static Integer nbPairs = null ;
    public static Integer numero = null ;

    public static void main(String[] args) throws IOException {
        int argc = args.length;

        /* Traitement des arguments */
        if (argc == 4) {
            try {
                ip = args[0];
                port = Integer.parseInt(args[1]);
                numero = port % 12340 ;
                name = args[2];
                nbPairs = Integer.parseInt(args[3]) ;
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("Usage: java EchoServer port");
            System.exit(2);
        }
        ServerSocketChannel ssc = ServerSocketChannel.open();
        ssc.socket().bind(new InetSocketAddress(port));


        Thread threadRead = new Thread(new PairRecup(ssc));
        Thread threadWrite = new Thread(new PairReturn(ssc));
        threadRead.start();
        threadWrite.start();
    }
}

class PairRecup implements Runnable {
    private ServerSocketChannel server;

    public PairRecup(ServerSocketChannel server) {
        this.server = server;
    }

    public void run() {
        try {
            server.configureBlocking(false);
            Selector select = Selector.open();
            server.register(select, SelectionKey.OP_ACCEPT);
            ByteBuffer buffer = ByteBuffer.allocate(128);
            while (true) {
                select.select();
                Iterator<SelectionKey> keys = select.selectedKeys().iterator();
                while (keys.hasNext()) {
                    SelectionKey key = keys.next();

                    if (key.isReadable()) {
                        SocketChannel chan = (SocketChannel) key.channel();
                        chan.configureBlocking(false);

                        try {
                            chan.read(buffer);
                        } catch (IOException e) {
                            chan.close();
                            break;
                        }
                        ByteBuffer msg = buffer.flip();
                        String entree = new String(msg.array()).trim();
                        traiterInformation(entree, chan);
                        buffer = ByteBuffer.allocate(128);

                    } else if (key.isAcceptable()) {
                        SocketChannel csc = server.accept();
                        csc.configureBlocking(false);
                        csc.register(select, SelectionKey.OP_READ);
                    }
                    keys.remove();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void traiterInformation(String entree, SocketChannel chan) {
        PairReturn.pair.add(chan);
        PairReturn.pair.add(entree);
    }
}

class PairReturn implements Runnable{
    private static int STATE_MESSAGE = 2 ;
    private static int STATE_SERVERCONNECT = 1 ;

    /* Liste qui contient toutes les socketsChannels */
    public static List<SocketChannel> listeSocketClients = new ArrayList<>();
    public static List<SocketChannel> listSocketServeurs = new ArrayList<>();


    public static List<String> clients = new ArrayList<>();
    private static List<String> serveursDisponnibles = new ArrayList<>() ;


    private static HashMap<Integer, String> clientPseudo = new HashMap<>();

    public static  HashMap<String, SocketChannel> clientSocket = new HashMap<>();
    public static HashMap<String, SocketChannel> serveursNames = new HashMap<>();

    public static HashMap<SocketChannel, Integer> socketChannelServerPort = new HashMap<>();



    public static Integer[] serverOrder = new Integer[PairsServer.nbPairs];

    public static ConcurrentLinkedQueue pair = new ConcurrentLinkedQueue() ;

    private ServerSocketChannel server;
    private static SocketChannel client;

    public static HashMap<SocketChannel, Integer> stateClient = new HashMap<>() ;

    private static Vector<Integer> broadcast = new Vector<>(PairsServer.nbPairs);

    private static SocketChannel[] socketCoServer = new SocketChannel[2] ;



    public PairReturn(ServerSocketChannel server){
        this.server = server;
    }

    @Override
    public void run() {
        try {
            Thread.sleep(4000);
            broadcast.setSize(PairsServer.nbPairs);

            initialiserCo() ;
            while (true) {
                Thread.sleep(100);
                if(pair.isEmpty())
                    continue;
                SocketChannel chan = (SocketChannel) pair.poll();
                String message = (String) pair.poll();
                if(messageInitialiserCo(message))
                    traiterInitialiserCo(chan, message);
                else if (messageServeur(chan))
                    traiterMessageServeur(chan, message) ;
                else if (!clientPseudo.containsKey(chan.socket().getPort()))
                    traiterLogin(message, chan);
                else if (stateClient.get(chan).equals(STATE_MESSAGE))
                    traiterMessageClient(message, chan);
            }
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
        }
        System.err.println("Fin de la session.");
        exit(0);
    }



    private boolean messageInitialiserCo(String message) {
        String first = message.split(" ")[0] ;
        if (first.equals("INITIALISER"))
            return true ;
        return false ;
    }

    private static void initialiserCo() throws IOException {
        Integer port = 12339 ;
        Integer indice_socketCoServer = 0 ;
        for (int i = 0; i < PairsServer.nbPairs; i++) {
            ++port ;
            serverOrder[i] = port ;
            broadcast.set(i, 0) ;
            if (port.equals(PairsServer.port)){
                continue;
            }
            System.out.println("j'envoi un message chez " + port);
            client = SocketChannel.open(new InetSocketAddress("127.0.0.1", port));
            socketCoServer[indice_socketCoServer] = client ;
            ++indice_socketCoServer ;
            String messageReady = "INITIALISER " + PairsServer.name ;
            client.write(ByteBuffer.wrap(messageReady.getBytes()));
        }
    }

    private static void traiterInitialiserCo(SocketChannel chan, String message) {
        String nom = recupererContenuMessage(message) ;
        Integer port = chan.socket().getLocalPort() ;
        serveursNames.put(nom, chan) ;
        listSocketServeurs.add(chan) ;
        serveursDisponnibles.add(nom) ;
    }

    private static boolean verifierServeur(String name) {
        for(String salon : serveursDisponnibles){
            if (salon.equals(name))
                return true ;
        }
        return false ;
    }

    private boolean messageServeur(SocketChannel chan) {
        for (SocketChannel socketChannel : listSocketServeurs){
            if (chan.equals(socketChannel))
                return true ;
        }
        return false ;
    }

    private void traiterMessageServeur(SocketChannel chan, String message) throws IOException, InterruptedException {
        System.out.println("Je traite messageServer");
        int numero = Integer.parseInt(message.split(" ")[0]) ;
        String stringVector = message.split(" ")[1] + message.split(" ")[2] + message.split(" ")[3] ;
        String[] entrees = message.split(" ", 5);
        String messageString = entrees[4];
        System.out.println(stringVector);
        Vector<Integer> vector = new Vector<>(PairsServer.nbPairs) ;
        vector.setSize(PairsServer.nbPairs);
        int indice = 1 ;
        String str ;
        for (int i = 0; i < PairsServer.nbPairs ; i++) {
            str = Character.toString(stringVector.charAt(indice)) ;
            indice = indice + 2 ;
            vector.set(i, Integer.parseInt(str)) ;
        }
        Message messageObjet = new Message(messageString, vector) ;
        receive_co_broadcast(messageObjet, chan, numero);
    }


    private void receive_co_broadcast(Message message, SocketChannel channel, int numero) throws IOException, InterruptedException {
        System.out.println("Je traite receive co_broadcast");
        if (!traitementPossible(message)){
            String envoi = PairsServer.numero.toString() + " " + message.broadcast.toString() + " " + message.message ;
            pair.add(channel) ;
            pair.add(envoi) ;
            System.out.println("Pas le moment.");
        }
        else {
            co_delivery(message.message, numero);
        }
        /**for (int i = 0 ; i < listSocketServeurs.size() ; i++) {
            broadcast.set(i, Math.max(broadcast.get(i), message.broadcast.get(i))) ;
        }**/
    }

    private boolean traitementPossible(Message message) {
        for (int i = 0; i < message.broadcast.size(); i++) {
            if (message.broadcast.get(i) > broadcast.get(i))
                return false ;
        }
        return true ;
    }

    private void co_delivery(String message, int numero) throws InterruptedException {
        System.out.println("Je traite Co_delivery");
        broadcast.set(numero, broadcast.get(numero)+1) ;

        System.out.println(message);
        for (String c : clients) {
            Thread.sleep(100);
            SocketChannel chan = clientSocket.get(c);
            try {
                if (chan.isConnected()) {
                    chan.write(ByteBuffer.wrap(message.getBytes()));
                }
            } catch (IOException e) {
                continue;
            }
        }
    }

    private void traiterMessageClient(String message, SocketChannel chan) throws IOException, InterruptedException {
        System.out.println("Je traite messageClient");
        if (verifierMessage(message)) {
            String pseudo = clientPseudo.get(chan.socket().getPort()) ;
            String messageTraite = pseudo + "> " + recupererContenuMessage(message) ;
            Message messageObjet = new Message(messageTraite, broadcast) ;
            co_broadcast(messageObjet);
        }
        else {
            String messageErreur = "ERROR chatamu";
            chan.write(ByteBuffer.wrap(messageErreur.getBytes()));
        }

    }

    private static boolean verifierMessage(String entree){
        return (entree.split(" ")[0].equals("MESSAGE"));
    }


    private void co_broadcast(Message message) throws IOException, InterruptedException {
        System.out.println("Je traite co_broadcast");

        /*
        pour chaque autre serveur, on envoie CO_BR(message, vector[1..nbServeur])
        vector[i] = vector[i] + 1
        appelle co_delivery(message) pour le traiter sur ce serveur
        quand serveur reçoit CO_BR(message, vector[1..nbServeur])
        on attend que tous les indices du vecteur sont remplis
        vector[i] = vector[i] + 1
         */

        Integer port = 12339 ;
        Integer indice_socketCoServeur = 0 ;
        String envoi = PairsServer.numero.toString() + " " + message.broadcast.toString() + " " + message.message ;
        for (int i = 0; i < PairsServer.nbPairs; i++) {
            ++port ;
            if (port.equals(PairsServer.port)){
                continue;
            }
            System.out.println("j'envoi un message chez " + port);
            client = socketCoServer[indice_socketCoServeur];
            ++indice_socketCoServeur ;

            try {
                if(client.isConnected())
                    client.write(ByteBuffer.wrap(envoi.getBytes()));
            } catch (IOException e){
                continue;
            }
        }

        for (int i = 0 ; i < listSocketServeurs.size() ; i++) {
            broadcast.set(i, Math.max(broadcast.get(i), message.broadcast.get(i))) ;
        }
        co_delivery(message.message, PairsServer.numero) ;
    }



    private static void traiterLogin(String entree, SocketChannel chan) throws IOException {
        System.out.println("Je traite login");
        if(!verifierConnexion(entree)){
            if (chan.isConnected())
                chan.write(ByteBuffer.wrap("ERROR LOGIN aborting chatamu protocol".getBytes()));
            else
                return;
        }
        else if(!verifierPseudo(recupererContenuLogin(entree))) {
            if(chan.isConnected())
                chan.write(ByteBuffer.wrap("ERROR LOGIN username".getBytes()));
            else
                return;
        }
        else {
            String pseudo =  recupererContenuLogin(entree) ;
            int portSocket = chan.socket().getPort();
            clientPseudo.put(portSocket, pseudo);
            clientSocket.put(pseudo, chan) ;
            clients.add(pseudo) ;
            stateClient.put(chan, STATE_MESSAGE) ;
            chan.write(ByteBuffer.wrap("ok".getBytes())) ;
        }
    }

    private static boolean verifierConnexion(String entree){
        return (entree.split(" ")[0].equals("LOGIN")) && (entree.split(" ").length == 2) ;
    }


    private static boolean verifierPseudo(String entree) {
        for (String client : clients) {
            if (client.equals(entree)) {
                return false;
            }
        }
        return true;
    }

    private static String recupererContenuLogin(String entree){
        return entree.split(" ")[1] ;
    }

    private static String recupererContenuMessage(String entree){
        String[] entrees = entree.split(" ", 2);
        return entrees[1];
    }

    private static void fermerServer(SocketChannel client) throws IOException {
        String messageFermeture = "CLOSE " + PairsServer.name ;
        client.write(ByteBuffer.wrap(messageFermeture.getBytes()));
    }
}
