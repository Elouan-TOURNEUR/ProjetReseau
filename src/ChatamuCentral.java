import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

import static java.lang.System.exit;
import static java.lang.System.setOut;

public class ChatamuCentral {

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

        Thread threadRead = new Thread(new MasterRecup(ssc));
        Thread threadWrite = new Thread(new MasterReturn(ssc));
        threadRead.start();
        threadWrite.start();

    }
}

class MasterRecup implements Runnable{

    /* Map qui associe un port client à un pseudo */
    private static HashMap<Integer, String> clientPseudo = new HashMap<>();

    private static HashMap<String, SocketChannel> pseudoChannel = new HashMap<>();


    /* Map qui associe un socketChannel à une file d'attente */
    private static HashMap<SocketChannel, ConcurrentLinkedQueue> socketChannelFileAttente = new HashMap<>();

    /* Liste qui contient toutes les files d'attentes */
    private static List<ConcurrentLinkedQueue> listeFileAttente = new ArrayList<>() ;

    /* Liste qui contient toutes les socketsChannels client */
    private static List<SocketChannel> listeSocketClient = new ArrayList<>() ;

    /* Map qui associe un nom à une liste de socketsChannels */
    private static HashMap<String, ArrayList<SocketChannel>> clientsParSalon = new HashMap<>() ;

    /* Map qui associe un serveur à un nom */
    public static HashMap<String, SocketChannel> serveursNames = new HashMap<>() ;

    /* Liste qui contient toutes les serveurs salon */
    private static List<String> serveursDisponnibles = new ArrayList<>() ;

    private static List<SocketChannel> listSocketServeur = new ArrayList<>() ;


    public static ConcurrentLinkedQueue getMaster() {
        return master;
    }

    public static ConcurrentLinkedQueue master = new ConcurrentLinkedQueue() ;

    private ServerSocketChannel server;

    public MasterRecup(ServerSocketChannel server){
        this.server = server;
    }

    @Override
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
                        System.out.println("je recoit : " + entree);
                        if (gestionServeur(entree))
                            traiterGestionServeur(entree, chan);
                        else if (messageServeur(chan)){
                            traiterMessageServeur(entree, chan, select) ;
                        }
                        else if (clientSurAucunServer(chan) && clientPseudo.containsKey(chan.socket().getPort())) {
                            traiterConnexionSalon(entree, chan);
                        } else if (clientPseudo.containsKey(chan.socket().getPort())) {
                            traiterMessageClient(entree, chan, select);
                        } else if (!clientPseudo.containsKey(chan.socket().getPort())) {
                            traiterLogin(entree, chan);
                        }
                        buffer = ByteBuffer.allocate(128);

                    } else if (key.isAcceptable()) {
                        SocketChannel csc = server.accept();
                        csc.configureBlocking(false);
                        csc.register(select, SelectionKey.OP_READ);

                    } else if (key.isWritable()) {
                        SocketChannel chan = (SocketChannel) key.channel();
                        chan.configureBlocking(false);

                        /* On récupère la file d'attente */
                        ConcurrentLinkedQueue fileAttente = socketChannelFileAttente.get(chan);
                        if (fileAttente == null) break;

                        /* On récupère le premier message de la file d'attente et on le supprime grace à poll() */
                        String message = (String) fileAttente.poll();

                        if (message != null)
                            chan.write(ByteBuffer.wrap(message.getBytes()));

                        /* On repasse le canal en lecture */
                        chan.register(select, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                    }
                    keys.remove();
                }
            }
        }catch (IOException e) {
            e.printStackTrace();
        }
    }



    private boolean messageServeur(SocketChannel chan) {
        for (SocketChannel socketChannel : listSocketServeur){
            if (chan.equals(socketChannel))
                return true ;
        }
        return false ;
    }

    private static void traiterGestionServeur(String entree, SocketChannel chan) {
        String first = entree.split(" ")[0] ;
        if (first.equals("OPEN"))
            traiterOuvertureSalon(entree, chan) ;
        else
            traiterFermetureSalon(entree, chan) ;
    }

    private static void traiterOuvertureSalon(String entree, SocketChannel chan) {
        String nomSalon = entree.split(" ")[1] ;
        System.out.println("Le serveur " + nomSalon + " a ouvert.");
        serveursDisponnibles.add(nomSalon) ;
        listSocketServeur.add(chan) ;
        serveursNames.put(nomSalon, chan) ;
        ArrayList<SocketChannel> salon = new ArrayList<SocketChannel>() ;
        clientsParSalon.put(nomSalon, salon) ;
    }

    private static void traiterFermetureSalon(String entree, SocketChannel chan) {
        String nomSalon = entree.split(" ")[1] ;
        System.out.println("Le serveur " + nomSalon + " a fermé.");
        serveursNames.remove(nomSalon, chan) ;
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
            pseudoChannel.put(pseudo, chan) ;

            /* A chaque nouveau client on lui associe sa file */
            ConcurrentLinkedQueue fileAttenteClient = new ConcurrentLinkedQueue() ;
            socketChannelFileAttente.put(chan, fileAttenteClient) ;
            listeFileAttente.add(fileAttenteClient) ;
            listeSocketClient.add(chan) ;

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
                SocketChannel serveur = serveursNames.get(name) ;
                String pseudo = clientPseudo.get(chan.socket().getPort()) ;
                master.add(serveur) ;
                master.add(pseudo) ;
                System.out.println(pseudo + " a rejoin le serveur " + name);
            }
            else {
                chan.write(ByteBuffer.wrap("ERROR SERVER NAME".getBytes()));
            }
        }
    }

    private void traiterMessageServeur(String entree, SocketChannel chan, Selector select) throws IOException {
        System.out.println("je recois message de serveur");
        if (serveursDisponnibles.contains(entree.split(" ")[0])){
            String nomEmetteur = entree.split(" ")[0] ;
            String message = recupererContenuMessage(entree) ;
            String messageTraite = "[SALON] " + message ;
            for(String serveur : serveursDisponnibles){
                if (serveur.equals(nomEmetteur))
                        continue;
                else {
                    SocketChannel ServeurARepliquer = serveursNames.get(serveur) ;
                    //System.out.println(ServeurARepliquer.toString());
                    master.add(ServeurARepliquer) ;
                    master.add(messageTraite) ;
                }
            }
        }
        else {
            String message = recupererContenuMessage(entree) ;
            //System.out.println(entree);
            SocketChannel client = pseudoChannel.get(entree.split(" ")[0]) ;
            System.out.println(client.toString());

            master.add(client) ;
            master.add(message) ;
        }
    }

    private static void traiterMessageClient(String entree, SocketChannel chan, Selector select) throws IOException {
        int portSocket = chan.socket().getPort();
        String pseudo = clientPseudo.get(portSocket);
        // On recupere le salon auquel le client est connecté
        String nomSalon = trouverSalonClient(chan) ;
        //String message = recupererContenuMessage(entree) ;
        String messagetraite = pseudo + " " + entree ;
        SocketChannel dest = serveursNames.get(nomSalon) ;

        // On transmet d'abord les infos concernant le client au serveur

        // On transmet ensuite au serveur le message du client
        master.add(dest) ;
        master.add(messagetraite) ;
    }


    private static void traiterMessageSalon(String entree, SocketChannel chan, Selector select) throws IOException {
        int portSocket = chan.socket().getPort();
        String pseudo = clientPseudo.get(portSocket);
        // On recupere le salon auquel le client est connecté
        String nomSalon = trouverSalonClient(chan) ;
        String message = pseudo + entree ;
        SocketChannel dest = serveursNames.get(nomSalon) ;

        // On transmet d'abord les infos concernant le client au serveur

        // On transmet ensuite au serveur le message du client
        master.add(dest) ;
        master.add(message) ;
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


    private static boolean verifierPseudo(String entree) {
        for (SocketChannel sock : listeSocketClient) {
            if (clientPseudo.get(sock.socket().getPort()).equals(entree)) {
                return false;
            }
        }
        return true;
    }

    private static boolean verifierCoServeur(String entree){
        return (entree.split(" ")[0].equals("SERVERCONNECT")) && (entree.split(" ").length == 2);
    }

    private static boolean gestionServeur(String entree) {
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
        for (SocketChannel socketChannel : listeSocketClient){
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


    /* Lorsqu'un client se déconnecte, on supprime sa file d'attente */
    private static void supprimerFileAttente(SocketChannel socketChannel){
        /* on supprime de la liste */
        listeFileAttente.remove(socketChannelFileAttente.get(socketChannel)) ;

        /* on supprime la file d'attente */
        socketChannelFileAttente.remove(socketChannel) ;
        listeSocketClient.remove(socketChannel) ;
    }

    private static String trouverSalonClient(SocketChannel chan){
        /* Pour chaque serveur on parcours sa liste de socketChannel */
        ArrayList<SocketChannel> liste = new ArrayList<>() ;
        for (String salon : serveursDisponnibles) {
            liste = clientsParSalon.get(salon) ;
            for (SocketChannel socketChannel : liste)
                if(socketChannel.equals(chan))
                    return salon ;
        }
        return null ;
    }
}


class MasterReturn implements Runnable{

    private ServerSocketChannel server;

    public MasterReturn(ServerSocketChannel server){
        this.server = server;
    }

    @Override
    public void run() {
        try {
            while (true) {
                //Thread.sleep(3000);
                //System.out.println(MasterRecup.master.size());
                if(MasterRecup.master.isEmpty())
                    continue;
                SocketChannel chan = (SocketChannel) MasterRecup.master.poll();
                //System.out.println("dans la pile");
                System.out.println(chan.toString());
                String message = (String) MasterRecup.master.poll();
                System.out.println(message);;
                //chan.write(ByteBuffer.wrap(message.getBytes())) ;

                SocketChannel client ;

                if(chan.equals(MasterRecup.serveursNames.get("Slave1"))) {
                    client = SocketChannel.open(new InetSocketAddress("127.0.0.1", 12346));
                    //System.out.println(client.toString());
                    client.write(ByteBuffer.wrap(message.getBytes()));
                }
                else if(chan.equals(MasterRecup.serveursNames.get("Slave2"))){
                    client = SocketChannel.open(new InetSocketAddress("127.0.0.1", 12347));
                    client.write(ByteBuffer.wrap(message.getBytes()));
                }
                else if(chan.equals(MasterRecup.serveursNames.get("Slave3"))){
                    client = SocketChannel.open(new InetSocketAddress("127.0.0.1", 12348));
                    client.write(ByteBuffer.wrap(message.getBytes()));
                }
                else {
                    chan.write(ByteBuffer.wrap(message.getBytes())) ;
                }

                //chan.write(ByteBuffer.wrap(message.getBytes())) ;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.err.println("Fin de la session.");
        exit(0);
    }
}