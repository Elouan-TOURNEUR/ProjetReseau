import java.io.IOException;
import java.net.InetAddress;
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

public class SlaveServeur {



    private static String name = null ;

    private static  SocketChannel client = null ;

    /* Map qui associe un socketChannel à une file d'attente */
    private static HashMap<SocketChannel, ConcurrentLinkedQueue> socketChannelFileAttente = new HashMap<>();

    /* Liste qui contient toutes les files d'attentes */
    private static List<ConcurrentLinkedQueue> listeFileAttente = new ArrayList<>() ;

    /* Liste qui contient toutes les socketsChannels */
    private static List<SocketChannel> listeSocket = new ArrayList<>() ;

    private static List<String>  clients = new ArrayList<>() ;


    public static void main(String[] args) throws IOException {
        int argc = args.length;
        int port = 0 ;
        String ip = "" ;
        client = SocketChannel.open(new InetSocketAddress(ip, 12345));

        /* Traitement des arguments */
        if (argc == 3) {
            try {
                ip = args[0];
                port = Integer.parseInt(args[1]);
                name = args[2] ;
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("Usage: java EchoServer port");
            System.exit(2);
        }



        String messageReady = "OPEN " + name + " " + port ;
        System.out.println(client.toString());
        client.write(ByteBuffer.wrap(messageReady.getBytes()));


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
                    if(verifierPseudo(entree.split(" ")[0])) {
                        traiterMessage(entree, chan, select);
                    }
                    else if(entree.split(" ")[0].equals("[SALON]"))
                        traiterReplicationSalon(entree, chan) ;
                    else{
                        traiterLogin(entree);
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




    private static void traiterLogin(String entree) throws IOException {
        System.out.println("Je traite un login");
        clients.add(entree);

            /* A chaque nouveau client on lui associe sa file
            ConcurrentLinkedQueue fileAttenteClient = new ConcurrentLinkedQueue() ;
            socketChannelFileAttente.put(chan, fileAttenteClient) ;
            listeFileAttente.add(fileAttenteClient) ;
            listeSocket.add(chan) ;

             */

    }

    private static void traiterReplicationSalon(String entree, SocketChannel chan) throws IOException {
        System.out.println("Je traite une replication");
        String message = recupererContenuMessage(entree) ;
        String pseudo = entree.split(" ")[0] ;
        System.out.println(message);
        for (String c : clients) {
            String messageRetourne = c + " " + message ;
            System.out.println("j'envoie : " + messageRetourne);
            client.write(ByteBuffer.wrap(messageRetourne.getBytes()));
        }
    }
    private static void traiterMessage(String entree, SocketChannel chan, Selector select) throws IOException {
        System.out.println("Je traite un message");
        /*if(entree.equals("exit")) {
            supprimerFileAttente(chan);
        }
        else if (!verifierMessage(entree)){
            chan.write(ByteBuffer.wrap("ERROR chatamu".getBytes()));
            //supprimerFileAttente(chan);
        }*/


        String pseudo = entree.split(" ")[0];
        String message = recupererContenuMessage(entree);
        if (!verifierMessage(message)) {
            String messageErreur = pseudo + " ERROR chatamu";
            client.write(ByteBuffer.wrap(messageErreur.getBytes()));
        } else {
            String messageTraite = pseudo + "> " + recupererContenuMessage(message);
            //ajouterListes(messageTraite, portSocket);
            System.out.println(messageTraite);

            /* Que sur les autres files*/
            String messageRetourne = name + " " + messageTraite;

            client.write(ByteBuffer.wrap(messageRetourne.getBytes()));
            System.out.println("j'envoie " + messageRetourne);

            for (String c : clients) {
                String messageClient = c + " " + messageTraite;
                client.write(ByteBuffer.wrap(messageClient.getBytes()));
                System.out.println("j'envoie " + messageClient);

            }
            /* On récupère le SocketChannel de la file d'attente
            SocketChannel channel = getChan(file) ;
            channel.configureBlocking(false) ;
             On le met en mode write car le serveur renvoie dans la socket du client les messages de la file
            channel.register(select, SelectionKey.OP_WRITE | SelectionKey.OP_READ) ;
            */
        }
    }
    //chan.write(ByteBuffer.wrap("OK".getBytes()));



    private static boolean verifierMessage(String entree){
        return (entree.split(" ")[0].equals("MESSAGE"));
    }

    private static boolean verifierPseudo(String entree) {
        for (String client : clients) {
            if (client.equals(entree)) {
                return true;
            }
        }
        return false;
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

    private static void fermerServer(SocketChannel client) throws IOException {
        String messageFermeture = "CLOSE " + name ;
        client.write(ByteBuffer.wrap(messageFermeture.getBytes()));
    }
}
