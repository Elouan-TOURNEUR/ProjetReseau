package ServeurFedereRobuste;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import static java.lang.System.*;

/*
 * Serveur pair :
 *   - Attribution d'une horloge vectorielle à chaque message diffusé et à utiliser les informations de causalité dans l'horloge vectorielle
 *     pour décider à chaque destination quand un message peut être délivré.
 *   - Si un message arrive à une destination donnée avant que les messages précédents causalement aient été remis, le service retarde la
 *     livraison de ce message jusqu'à ce que ces messages arrivent et soient remis
 *   - Méthode co_broadcast() envoie à tout le monde
 *   - Méthode co_deliver() réception du message et traitement
 */
public class PairsServer {

    /* Nom du serveur */
    public static String name = null;

    /* Port du serveur */
    public static Integer port = null;

    /* IP du serveur */
    private static String ip = "127.0.0.1";

    /* Nombre de parires du serveur robuste */
    public static Integer nbPairs = null ;

    /* Numéro du serveur */
    public static Integer numero = null ;

    /* Etat distinguant l'état d'un client pouvant écrire un message */
    private static int STATE_MESSAGE = 2 ;

    /* Liste des Socket des serveurs pairs */
    public static List<SocketChannel> listSocketServeurs = new ArrayList<>();

    /* Liste des pseudos connectés à la fédération de serveurs */
    public static List<String> clients = new ArrayList<>();

    /* Liste des noms des serveurs disponibles */
    private static List<String> serveursDisponnibles = new ArrayList<>() ;

    /* Map associant le port d'un client avec le pseudo qu'il a choisi */
    public static HashMap<Integer, String> clientPseudo = new HashMap<>();

    /* Map associant le pseudo d'un client avec son Socket */
    public static volatile HashMap<String, SocketChannel> clientSocket = new HashMap<>();

    /* Map associant le nom d'un serveur avec son Socket*/
    public static HashMap<String, SocketChannel> serveursNames = new HashMap<>();

    /* Tableau de l'ordre des ports des serveurs */
    public static Integer[] serverOrder;

    /* Liste des messages et de leur origine */
    public static ConcurrentLinkedQueue pair = new ConcurrentLinkedQueue() ;

    /* Socket vers le serveur pair courant */
    private static SocketChannel client;

    /* Map assicant les port des clients avec leur état (STATE_MESSAGE ou rien) */
    public static HashMap<Integer, Integer> stateClient = new HashMap<>() ;

    /* Horloge vectorielle */
    private static Vector<Integer> broadcast;

    /* Socket vers les autres serveurs */
    private static SocketChannel[] socketCoServer = new SocketChannel[2] ;



    public static void main(String[] args) throws IOException, InterruptedException {
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

        broadcast = new Vector<>(nbPairs);
        serverOrder = new Integer[nbPairs];

        ServerSocketChannel server = ServerSocketChannel.open();
        server.socket().bind(new InetSocketAddress(port));

        server.configureBlocking(false);
        Selector select = Selector.open();
        server.register(select, SelectionKey.OP_ACCEPT);
        ByteBuffer buffer = ByteBuffer.allocate(128);

        Thread.sleep(4000);
        broadcast.setSize(nbPairs);

        initialiserCo();

        // Boucle sur le selecteur
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

                    chan.register(select, SelectionKey.OP_READ | SelectionKey.OP_WRITE);

                } else if (key.isAcceptable()) {
                    SocketChannel csc = server.accept();
                    csc.configureBlocking(false);

                    csc.register(select, SelectionKey.OP_READ);

                    int port = csc.socket().getPort();
                    if(stateClient.containsKey(port)){
                        clientSocket.put(clientPseudo.get(port), csc);
                    }
                } else if(key.isWritable()){
                    SocketChannel chan = (SocketChannel) key.channel();
                    chan.configureBlocking(false);

                    if(pair.isEmpty()) {
                        chan.register(select, SelectionKey.OP_READ);
                        continue;
                    }

                    SocketChannel channel = (SocketChannel) pair.poll();
                    String message = (String) pair.poll();

                    if(message == null){
                        chan.register(select, SelectionKey.OP_READ);
                        continue;
                    }

                    if(message.split(" ")[0].equals("INITIALISER"))
                        traiterInitialiserCo(channel, message);
                    else if (messageServeur(channel)) {
                        if(message.split(" ")[5].equals("LOGCLIENT"))
                            traiterLoginServeur(message.split(" ", 6)[5], channel);
                        else
                            traiterMessageServeur(message);
                    }else if (!clientPseudo.containsKey(channel.socket().getPort()))
                        traiterLogin(message, channel);
                    else if (STATE_MESSAGE == stateClient.get(channel.socket().getPort()))
                        traiterMessageClient(message, channel);
                    else if(message.equals("DISCONNECT"))
                        traiterDisconnectClient(channel);

                    chan.register(select, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                }

                keys.remove();
            }
        }
    }

    /* Ajout du message et de la Socket dans la liste du thread PairReturn */
    private static void traiterInformation(String entree, SocketChannel chan) {
        pair.add(chan);
        pair.add(entree);
    }

    /* Traitement de la déconnexion d'un client */
    private static void traiterDisconnectClient(SocketChannel chan) throws IOException {
        clientSocket.remove(clientPseudo.get(chan.socket().getPort()));
        chan.close();
    }

    /* Traitement du LOGIN sur la fédération de serveur */
    private static void traiterLoginServeur(String message, SocketChannel chan) throws IOException {
        if(!serveursNames.containsValue(chan)){
            chan.write(ByteBuffer.wrap("ERROR SERVER LOGIN".getBytes()));
            return;
        }
        String msg = recupererContenuMessage(message);
        String[] split = msg.split(" ");
        int port = Integer.parseInt(split[1]);
        String pseudo = split[0];

        clientPseudo.put(port, pseudo);
        clients.add(pseudo) ;
        stateClient.put(port, STATE_MESSAGE) ;
    }

    /* Connexion sur les autres serveurs pairs */
    private static void initialiserCo() throws IOException {
        Integer port = 12339 ;
        int indice_socketCoServer = 0 ;
        for (int i = 0; i < nbPairs; i++) {
            ++port ;
            serverOrder[i] = port ;
            broadcast.set(i, 0) ;
            if (port.equals(PairsServer.port)){
                continue;
            }
            System.out.println("j'envoie un message chez " + port);
            client = SocketChannel.open(new InetSocketAddress("127.0.0.1", port));
            socketCoServer[indice_socketCoServer] = client ;
            ++indice_socketCoServer ;
            String messageReady = "INITIALISER " + name ;
            client.write(ByteBuffer.wrap(messageReady.getBytes()));
        }
    }

    /* Réception de la connexion des autres serveurs pairs */
    private static void traiterInitialiserCo(SocketChannel chan, String message) {
        String nom = recupererContenuMessage(message) ;
        serveursNames.put(nom, chan) ;
        listSocketServeurs.add(chan) ;
        serveursDisponnibles.add(nom) ;
    }


    private static boolean messageServeur(SocketChannel chan) {
        return listSocketServeurs.contains(chan);
    }

    /* Traitement d'un message venant d'un serveur */
    private static void traiterMessageServeur(String message) throws InterruptedException {
        System.out.println("Je traite messageServer");
        int numero = Integer.parseInt(message.split(" ")[0]) ;
        String stringVector = message.split(" ")[1] + message.split(" ")[2] + message.split(" ")[3] ;
        String[] entrees = message.split(" ", 5);
        String messageString = entrees[4];
        System.out.println(stringVector);
        Vector<Integer> vector = new Vector<>(nbPairs) ;
        vector.setSize(nbPairs);
        int indice = 1 ;
        String str ;
        for (int i = 0; i < nbPairs ; i++) {
            str = Character.toString(stringVector.charAt(indice)) ;
            indice = indice + 2 ;
            vector.set(i, Integer.parseInt(str)) ;
        }

        Message messageObjet = new Message(messageString, vector) ;
        receive_co_broadcast(messageObjet, numero);
    }

    private static void receive_co_broadcast(Message message, int numero) throws InterruptedException {
        System.out.println("Je traite receive co_broadcast");
        if (!traitementPossible(message)){
            System.out.println("Pas le moment.");
        }
        else {
            co_delivery(message.message, numero);
        }
        /**for (int i = 0 ; i < listSocketServeurs.size() ; i++) {
         broadcast.set(i, Math.max(broadcast.get(i), message.broadcast.get(i))) ;
         }**/
    }

    private static boolean traitementPossible(Message message) {
        for (int i = 0; i < message.broadcast.size(); i++) {
            if (message.broadcast.get(i) > broadcast.get(i))
                return false ;
        }
        return true ;
    }

    private static void co_delivery(String message, int numero) throws InterruptedException {
        System.out.println("Je traite Co_delivery");
        broadcast.set(numero, broadcast.get(numero)+1) ;

        System.out.println(message);
        boolean isServerMsg = message.split(" ")[0].equals("SERVER");
        for (String c : clientSocket.keySet()) {
            Thread.sleep(10);
            SocketChannel chan = clientSocket.get(c);
            try {
                if (chan.isConnected()) {
                    if(!isServerMsg){
                        out.println("j'envoie " + message + " à " + c);
                        chan.write(ByteBuffer.wrap(message.getBytes()));
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                continue;
            }
        }
        for (String c : serveursNames.keySet()) {
            Thread.sleep(10);
            SocketChannel chan = serveursNames.get(c);
            try {
                if (chan.isConnected()) {
                    out.println("j'envoie " + message + " à " + c);
                    chan.write(ByteBuffer.wrap(message.getBytes()));
                }
            } catch (IOException e) {
                e.printStackTrace();
                continue;
            }
        }
    }

    /* Traitement du message d'un client connecté sur ce serveur */
    private static void traiterMessageClient(String message, SocketChannel chan) throws IOException, InterruptedException {
        System.out.println("Je traite messageClient");

        // Simulation d'un défaillance sur les serveurs 0 et 1
        if(numero == 1 || numero == 0){
            Thread.sleep(500000);
        }
        if (verifierMessage(message)) {
            String pseudo = clientPseudo.get(chan.socket().getPort()) ;
            String messageTraite = pseudo + "> " + recupererContenuMessage(message) ;
            Message messageObjet = new Message(messageTraite, broadcast) ;
            chan.write(ByteBuffer.wrap("OK".getBytes()));
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

    /* Envoie du message à tout le monde */
    private static void co_broadcast(Message message) throws InterruptedException {
        System.out.println("Je traite co_broadcast");

        Integer port = 12339 ;
        Integer indice_socketCoServeur = 0 ;
        String envoi = numero.toString() + " " + message.broadcast.toString() + " " + message.message ;
        for (int i = 0; i < nbPairs; i++) {
            ++port ;
            if (port.equals(PairsServer.port)){
                continue;
            }
            System.out.println("j'envoie un message chez " + port);
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
        co_delivery(message.message, numero) ;
    }

    /* Traitement du login */
    private static void traiterLogin(String entree, SocketChannel chan) throws IOException, InterruptedException {
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
            stateClient.put(chan.socket().getPort(), STATE_MESSAGE) ;
            chan.write(ByteBuffer.wrap("OK".getBytes())) ;

            String brdcst = "SERVER LOGCLIENT " + pseudo + " " + portSocket;
            co_broadcast(new Message(brdcst, broadcast));
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
}
