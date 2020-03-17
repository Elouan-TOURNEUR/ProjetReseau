import javax.naming.ldap.SortKey;
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
    /*

    Proposer une architecture à base de files d'attente (https://docs.oracle.com/javase/8/docs/api/java/util/Queue.html) associées à chaque client, permettant de
    utiliser ces files en mode producteurs-consommateur
    où les producteurs transmettent les messages envoyés par un client sur l'ensemble des autres files d'attentes
    il y a un seul consommateur par file, qui renvoie dans la socket du client les messages présents dans la file d'attente associée à ce client donné.
            - On pourra utiliser les implémentations ArrayBlockingQueue<String> ou ConcurrentLinkedQueue<String>.
    */

    /* Map qui associe un port client à un pseudo */
    private static HashMap<Integer, String> map = new HashMap<>();

    /* Map qui associe un socketChannel à une file d'attente */
    private static HashMap<SocketChannel, ConcurrentLinkedQueue> filesAttentes = new HashMap<>();

    /* Liste qui contient toutes les files d'attentes */
    private static List<ConcurrentLinkedQueue> listeFileAttente = new ArrayList<>() ;

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

                    if(map.containsKey(chan.socket().getPort())) {
                        //traiterMessage(entree, chan);
                        if(!verifierMessage(entree)){
                            chan.write(ByteBuffer.wrap("ERROR chatamu".getBytes()));
                            supprimerFileAttente(chan);
                        } else{
                            int portSocket = chan.socket().getPort();
                            String pseudo = map.get(portSocket);
                            String messsage = recupererContenuMessage(entree);

                            String messageTraite = pseudo + "> " + messsage ;
                            //ajouterListes(messageTraite, portSocket);
                            System.out.println(pseudo + "> " + messsage);
                            for (ConcurrentLinkedQueue file : listeFileAttente) {
                                /* Que sur les autres files*/
                                if(filesAttentes.get(portSocket) != file) {
                                    file.add(messageTraite);
                                    /* On récupère le SocketChannel de la file d'attente */
                                    SocketChannel channel = getChan(file) ;
                                    channel.configureBlocking(false) ;
                                    /* On le met en mode write car le serveur renvoie dans la socket du client les messages de la file */
                                    channel.register(select, SelectionKey.OP_WRITE) ;
                                }
                            }

                            chan.write(ByteBuffer.wrap("OK".getBytes()));
                        }
                    }
                    else{
                        traiterLogin(entree, chan);
                    }
                    buffer = ByteBuffer.allocate(128);

                } else if(key.isAcceptable()){
                    SocketChannel csc = ssc.accept();
                    csc.configureBlocking(false);
                    csc.register(select, SelectionKey.OP_READ);

                } else if (key.isReadable()){
                    SocketChannel chan = (SocketChannel) key.channel();
                    /* On récupère la file d'attente */
                    ConcurrentLinkedQueue fileAttente = filesAttentes.get(chan) ;
                    /* On récupère le premier message de la file d'attente et on le supprime grace à poll() */
                    String message = fileAttente.poll().toString() ;
                    chan.write(ByteBuffer.wrap(message.getBytes())) ;
                }

                keys.remove();
            }
        }

    }

    private static SocketChannel getChan(ConcurrentLinkedQueue fileAttente) {
        for (SocketChannel socketChannel : listeSocket){
            if (filesAttentes.get(socketChannel) == fileAttente){
                return socketChannel ;
            }
        }
        return null ;
    }

    private static void traiterMessage(String entree, SocketChannel chan) throws IOException {

        if(!verifierMessage(entree)){
            chan.write(ByteBuffer.wrap("ERROR chatamu".getBytes()));
            supprimerFileAttente(chan);
        } else{
            int portSocket = chan.socket().getPort();
            String pseudo = map.get(portSocket);
            String messsage = recupererContenuMessage(entree);

            String messageTraite = pseudo + "> " + messsage ;
            ajouterListes(messageTraite, chan);

            //System.out.println(pseudo + "> " + messsage);

            chan.write(ByteBuffer.wrap("OK".getBytes()));
        }
    }

    private static void traiterLogin(String entree, SocketChannel chan) throws IOException {

        if(!verifierConnexion(entree)){
            chan.write(ByteBuffer.wrap("ERROR LOGIN aborting chatamu protocol".getBytes()));
        } else{
            String pseudo = recupererContenuLogin(entree) ;
            int portSocket = chan.socket().getPort();
            map.put(portSocket, pseudo);

            /* A chaque nouveau client on lui associe sa file */
            ConcurrentLinkedQueue fileAttenteClient = new ConcurrentLinkedQueue() ;
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

    /* Messages client transmis sur les autres files */
    private static void ajouterListes(String message, SocketChannel socketChannel){
        for (ConcurrentLinkedQueue file : listeFileAttente) {
            /* Que sur les autres files*/
            if(filesAttentes.get(socketChannel) != file)
                file.add(message) ;
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
