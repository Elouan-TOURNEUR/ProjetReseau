package ServiceCentralise;

import java.io.IOException;
import java.net.*;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.nio.*;

/*
* Première version du serveur SalonCentral qui n'affiche les messages du salon
* que sur la sortie standard du serveur.
* Serveur du service centralise correspondant au seul serveur
* recevant et envoyant les messages entre les clients.
*/
class SalonCentral {

    /* Map qui associe un numéro de port client à un pseudo */
    private static HashMap<Integer, String> map = new HashMap<>();

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

        /* Boucle sur le selecteur */
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
                        traiterMessage(entree, chan);
                    }
                    else{
                        traiterLogin(entree, chan);
                    }

                    buffer = ByteBuffer.allocate(128);

                } else if(key.isAcceptable()){
                    SocketChannel csc = ssc.accept();
                    csc.configureBlocking(false);
                    csc.register(select, SelectionKey.OP_READ);
                }

                keys.remove();
            }
        }

    }

    /* Traite la réception d'un message et l'ajoute dans les files des autres clients */
    private static void traiterMessage(String entree, SocketChannel chan) throws IOException {
        if(!verifierMessage(entree)){
            chan.write(ByteBuffer.wrap("ERROR chatamu".getBytes()));
        } else{
            int portSocket = chan.socket().getPort();
            String pseudo = map.get(portSocket);
            String messsage = recupererContenuMessage(entree);
            System.out.println(pseudo + "> " + messsage);

            chan.write(ByteBuffer.wrap("OK".getBytes()));
        }
    }

    /* Traite la réception d'un login */
    private static void traiterLogin(String entree, SocketChannel chan) throws IOException {
        if(!verifierConnexion(entree)){
            chan.write(ByteBuffer.wrap("ERROR LOGIN aborting chatamu protocol".getBytes()));
        } else{
            String pseudo = recupererContenuLogin(entree) ;
            int portSocket = chan.socket().getPort();
            map.put(portSocket, pseudo);

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
}
