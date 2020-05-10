package ServiceCentralise;

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

/*
* Serveur du service centralise correspondant au seul serveur
* recevant et envoyant les messages entre les clients.
*/
public class ChatamuCentral {

    /* Map qui associe un port client à un pseudo */
    private static HashMap<Integer, String> map = new HashMap<>();

    /* Map qui associe un socketChannel à une file d'attente */
    private static HashMap<SocketChannel, ConcurrentLinkedQueue<String>> filesAttentes = new HashMap<>();

    /* Liste qui contient toutes les files d'attentes */
    private static List<ConcurrentLinkedQueue<String>> listeFileAttente = new ArrayList<>() ;

    /* Liste qui contient toutes les socketsChannels */
    private static List<SocketChannel> listeSocket = new ArrayList<>() ;


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

        // Boucle sur le selecteur
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

                    // Si le port du client à déjà été enregistré à un pseudo
                    if(map.containsKey(chan.socket().getPort())) {
                        traiterMessage(entree, chan, select);
                    }
                    else{
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
                    ConcurrentLinkedQueue<String> fileAttente = filesAttentes.get(chan);
                    if(fileAttente == null) break;

                    /* On récupère le premier message de la file d'attente et on le supprime grace à poll() */
                    String message = fileAttente.poll();

                    if(message != null)
                        chan.write(ByteBuffer.wrap(message.getBytes()));

                    /* On repasse le canal en lecture */
                    chan.register(select, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                }

                keys.remove();
            }
        }

    }

    /* Retourne le channel correspondant à la file d'attente */
    private static SocketChannel getChan(ConcurrentLinkedQueue fileAttente) {
        for (SocketChannel socketChannel : listeSocket){
            if (filesAttentes.get(socketChannel) == fileAttente){
                return socketChannel ;
            }
        }
        return null ;
    }

    /* Traite la réception d'un message et l'ajoute dans les files des autres clients */
    private static void traiterMessage(String entree, SocketChannel chan, Selector select) throws IOException {
        if(entree.equals("exit"))
            supprimerFileAttente(chan);
        else if(!verifierMessage(entree)){
            chan.write(ByteBuffer.wrap("ERROR chatamu".getBytes()));
            //supprimerFileAttente(chan);
        }
        else{
            int portSocket = chan.socket().getPort();
            String pseudo = map.get(portSocket);
            String messsage = recupererContenuMessage(entree);

            String messageTraite = pseudo + "> " + messsage ;
            ajouterListes(messageTraite, chan, select);
            System.out.println(pseudo + "> " + messsage);
        }
    }

    /* Traite la réception d'un login */
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
            map.put(portSocket, pseudo);

            /* A chaque nouveau client on lui associe sa file */
            ConcurrentLinkedQueue<String> fileAttenteClient = new ConcurrentLinkedQueue<>() ;
            filesAttentes.put(chan, fileAttenteClient) ;
            listeFileAttente.add(fileAttenteClient) ;
            listeSocket.add(chan) ;

            chan.write(ByteBuffer.wrap("OK".getBytes()));
        }
    }


    private static String recupererContenuLogin(String entree){
        return entree.split(" ")[1] ;
    }

    private static String recupererContenuMessage(String entree){
        String[] entrees = entree.split(" ", 2);

        return entrees[1];
    }

    private static boolean verifierConnexion(String entree){
        return (entree.split(" ")[0].equals("LOGIN")) && (entree.split(" ").length == 2) ;
    }

    private static boolean verifierMessage(String entree){
        return (entree.split(" ")[0].equals("MESSAGE"));
    }

    private static boolean verifierPseudo(String entree) {
        for (SocketChannel sock : listeSocket) {
            if (map.get(sock.socket().getPort()).equals(entree)) {
                return false;
            }
        }
        return true;
    }

    /* Messages client transmis sur les autres files */
    private static void ajouterListes(String message, SocketChannel chan, Selector select) throws IOException {
        for (ConcurrentLinkedQueue<String> file : listeFileAttente) {
            ConcurrentLinkedQueue<String> f = filesAttentes.get(chan);
            if (f == null) continue;
            /* Que sur les autres files*/
            if(! f.equals(file)) {
                file.add(message + "\n");
                /* On récupère le SocketChannel de la file d'attente */
                SocketChannel channel = getChan(file) ;
                if(channel == null) continue;

                channel.configureBlocking(false) ;
                /* On le met en mode write (et read) car le serveur renvoie dans la socket du client les messages de la file */
                channel.register(select, SelectionKey.OP_WRITE | SelectionKey.OP_READ) ;
            }
        }
    }

    /* Lorsqu'un client se déconnecte, on supprime sa file d'attente */
    private static void supprimerFileAttente(SocketChannel socketChannel){
        /* on supprime de la liste */
        listeFileAttente.remove(filesAttentes.get(socketChannel)) ;

        /* on supprime la file d'attente */
        filesAttentes.remove(socketChannel) ;
        listeSocket.remove(socketChannel) ;
    }
}


