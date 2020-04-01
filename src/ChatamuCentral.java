import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ChatamuCentral {

    /* Map qui associe un port client à un pseudo */
    private static HashMap<Integer, String> clientPseudo = new HashMap<>();

    /* Map qui associe un socketChannel à une file d'attente */
    private static HashMap<SocketChannel, ConcurrentLinkedQueue> socketChannelFileAttente = new HashMap<>();

    /* Liste qui contient toutes les files d'attentes */
    private static List<ConcurrentLinkedQueue> listeFileAttente = new ArrayList<>() ;

    /* Liste qui contient toutes les socketsChannels */
    private static List<SocketChannel> listeSocket = new ArrayList<>() ;

    /* Map qui associe un nom à une liste de socketsChannels */
    private static HashMap<String, ArrayList<SocketChannel>> clientsParSalon = new HashMap<>() ;

    /* Map qui associe un serveur à un nom */
    private static HashMap<String, String> serveursNames = new HashMap<>() ;

    /* Liste qui contient toutes les serveurs salon */
    private static List<String> serveursDisponnibles = new ArrayList<String>() ;

    public static void main(String[] args) throws IOException {
        int argc = args.length;
        int port = 0;

        /* Traitement des arguments */
        if (argc == 1) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("Usage: java EchoServer port");
            System.exit(2);
        }

        ServerSocketChannel ssc = ServerSocketChannel.open();
        ssc.socket().bind(new InetSocketAddress(port));
        ssc.configureBlocking(false);

        Selector select = Selector.open();
        ssc.register(select, SelectionKey.OP_ACCEPT);
        ByteBuffer buffer = ByteBuffer.allocate(128);


        while(true){
            select.select();
            Iterator<SelectionKey> keys = select.selectedKeys().iterator();

            while(keys.hasNext()){
                SelectionKey key = keys.next();

                if(key.isReadable()){
                    SocketChannel chan = (SocketChannel) key.channel();
                    chan.configureBlocking(false);

                    try{
                        chan.read(buffer);
                    } catch (IOException e){
                        chan.close();
                        break;
                    }
                    ByteBuffer msg = buffer.flip();
                    String entree = new String(msg.array()).trim();
                    if(messageDeSalon(entree))
                        traiterMessageDeSalon(entree) ;
                    else if (clientSurAucunServer(chan) && clientPseudo.containsKey(chan.socket().getPort())){
                        traiterConnexionSalon(entree, chan) ;
                    }
                    else if(clientPseudo.containsKey(chan.socket().getPort())) {
                        traiterMessage(entree, chan, select);
                    }
                    else if (!clientPseudo.containsKey(chan.socket().getPort())){
                        traiterLogin(entree, chan);
                    }
                    buffer = ByteBuffer.allocate(128);

                } else if(key.isAcceptable()){
                    SocketChannel csc = ssc.accept();
                    csc.configureBlocking(false);
                    csc.register(select, SelectionKey.OP_READ);

                } else if (key.isWritable()){
                    SocketChannel chan = (SocketChannel) key.channel();
                    chan.configureBlocking(false);

                    /* On récupère la file d'attente */
                    ConcurrentLinkedQueue fileAttente = socketChannelFileAttente.get(chan);
                    if(fileAttente == null) break;

                    /* On récupère le premier message de la file d'attente et on le supprime grace à poll() */
                    String message = (String)fileAttente.poll();

                    if(message != null)
                        chan.write(ByteBuffer.wrap(message.getBytes()));

                    /* On repasse le canal en lecture */
                    chan.register(select, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                }
                keys.remove();
            }
        }
    }

    private static void traiterMessageDeSalon(String entree) {
        String first = entree.split(" ")[0] ;
        if (first.equals("OPEN"))
            traiterOuvertureSalon(entree) ;
        else
            traiterFermetureSalon(entree) ;
    }

    private static void traiterOuvertureSalon(String entree) {
        String nomSalon = entree.split(" ")[1] ;
        System.out.println("Le serveur " + nomSalon + " a ouvert.");
        serveursDisponnibles.add(nomSalon) ;
        serveursNames.put(nomSalon, entree.split(" ")[2]) ;
        ArrayList<SocketChannel> salon = new ArrayList<SocketChannel>() ;
        clientsParSalon.put(nomSalon, salon) ;
    }

    private static void traiterFermetureSalon(String entree) {
        String nomSalon = entree.split(" ")[1] ;
        System.out.println("Le serveur " + nomSalon + " a fermé.");
        serveursNames.remove(nomSalon, entree.split(" ")[2]) ;
        serveursDisponnibles.remove(nomSalon) ;
    }

    private static void traiterLogin(String entree, SocketChannel chan) throws IOException {
        if(!verifierConnexion(entree)){
            chan.write(ByteBuffer.wrap("ERROR LOGIN aborting chatamu protocol".getBytes()));
        }
        else if(!verifierPseudo(recupererContenuLogin(entree))) {
            chan.write(ByteBuffer.wrap("ERROR LOGIN username".getBytes()));
        }
        else {
            String pseudo =  recupererContenuLogin(entree) ;
            int portSocket = chan.socket().getPort();
            clientPseudo.put(portSocket, pseudo);

            /* A chaque nouveau client on lui associe sa file */
            ConcurrentLinkedQueue fileAttenteClient = new ConcurrentLinkedQueue() ;
            socketChannelFileAttente.put(chan, fileAttenteClient) ;
            listeFileAttente.add(fileAttenteClient) ;
            listeSocket.add(chan) ;

            String listeSalon = "" ;
            for (String salon : serveursDisponnibles) {
                listeSalon += salon + '\n' ;
            }
            chan.write(ByteBuffer.wrap(listeSalon.getBytes())) ;
        }
    }

    private static void traiterConnexionSalon(String entree, SocketChannel chan) throws IOException {
        if(!verifierCoServeur(entree)){
            chan.write(ByteBuffer.wrap("ERROR SERVER".getBytes()));
        }
        else {
            String name = entree.split(" ")[1] ;
            if (verifierSalon(name)) {
                chan.write(ByteBuffer.wrap("ok".getBytes()));
                clientsParSalon.get(name).add(chan);
                String pseudo = clientPseudo.get(chan.socket().getPort()) ;
                System.out.println(pseudo + " a rejoint le serveur " + name);
            }
            else {
                chan.write(ByteBuffer.wrap("ERROR SERVER NAME".getBytes()));
            }
        }
    }

    private static void traiterMessage(String entree, SocketChannel chan, Selector select) throws IOException {
        if(entree.equals("exit")) {
            supprimerFileAttente(chan);
        }
        else if (!verifierMessage(entree)){
            chan.write(ByteBuffer.wrap("ERROR chatamu".getBytes()));
            //supprimerFileAttente(chan);
        }
        else{
            int portSocket = chan.socket().getPort();
            String pseudo = clientPseudo.get(portSocket);
            String messsage = recupererContenuMessage(entree);

            String messageTraite = pseudo + "> " + messsage ;
            //ajouterListes(messageTraite, portSocket);
            System.out.println(pseudo + "> " + messsage);
            for (ConcurrentLinkedQueue file : listeFileAttente) {
                ConcurrentLinkedQueue f = socketChannelFileAttente.get(chan);
                if (f == null) continue;
                /* Que sur les autres files*/
                if(! f.equals(file)) {
                    file.add(messageTraite + "\n");
                    /* On récupère le SocketChannel de la file d'attente */
                    SocketChannel channel = getChan(file) ;
                    channel.configureBlocking(false) ;
                    /* On le met en mode write car le serveur renvoie dans la socket du client les messages de la file */
                    channel.register(select, SelectionKey.OP_WRITE | SelectionKey.OP_READ) ;
                }
            }
            //chan.write(ByteBuffer.wrap("OK".getBytes()));
        }
    }

    private static boolean verifierSalon(String name) {
        for(String salon : serveursDisponnibles){
            if (salon.equals(name))
                return true ;
        }
        return false ;
    }

    private static boolean verifierConnexion(String entree){
        return (entree.split(" ")[0].equals("LOGIN")) && (entree.split(" ").length == 2) ;
    }

    private static boolean verifierMessage(String entree){
        return (entree.split(" ")[0].equals("MESSAGE"));
    }

    private static boolean verifierPseudo(String entree) {
        for (SocketChannel sock : listeSocket) {
            if (clientPseudo.get(sock.socket().getPort()).equals(entree)) {
                return false;
            }
        }
        return true;
    }

    private static boolean verifierCoServeur(String entree){
        return (entree.split(" ")[0].equals("SERVERCONNECT")) && (entree.split(" ").length == 2);
    }

    private static boolean messageDeSalon(String entree) {
        return ((entree.split(" ")[0].equals("OPEN") || (entree.split(" ")[0].equals("CLOSE"))) && (entree.split(" ").length == 3)) ;
    }

    private static boolean clientSurAucunServer(SocketChannel socketChannel){
        for(String server : serveursDisponnibles){
            ArrayList<SocketChannel> clients = clientsParSalon.get(server) ;
            for(SocketChannel client : clients){
                if(client.equals(socketChannel))
                    return false ;
            }
        }
        return true ;
    }

    private static SocketChannel getChan(ConcurrentLinkedQueue fileAttente) {
        for (SocketChannel socketChannel : listeSocket){
            if (socketChannelFileAttente.get(socketChannel) == fileAttente){
                return socketChannel ;
            }
        }
        return null ;
    }

    private static String recupererContenuLogin(String entree){
        return entree.split(" ")[1] ;
    }

    private static String recupererContenuMessage(String entree){
        String[] entrees = entree.split(" ", 2);
        return entrees[1];
    }

    /* Messages client transmis sur les autres files */
    private static void ajouterListes(String message, SocketChannel socketChannel){
        for (ConcurrentLinkedQueue file : listeFileAttente) {
            /* Que sur les autres files*/
            if(! socketChannelFileAttente.get(socketChannel).equals(file))
                file.add(message) ;
        }
    }

    /* Lorsqu'un client se déconnecte, on supprime sa file d'attente */
    private static void supprimerFileAttente(SocketChannel socketChannel){
        /* on supprime de la liste */
        listeFileAttente.remove(socketChannelFileAttente.get(socketChannel)) ;

        /* on supprime la file d'attente */
        socketChannelFileAttente.remove(socketChannel) ;
        listeSocket.remove(socketChannel) ;
    }
}