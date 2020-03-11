/* echo / serveur basique
   Master Informatique 2012 -- Université Aix-Marseille
   Bilel Derbel, Emmanuel Godard
*/

import jdk.swing.interop.SwingInterOpUtils;

import java.awt.datatransfer.SystemFlavorMap;
import java.io.IOException;
import java.net.*;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.*;
import java.nio.*;
import java.lang.Thread;

class EchoServer {

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

        while(true){
            select.select();
            Iterator<SelectionKey> keys = select.selectedKeys().iterator();

            while(keys.hasNext()){
                System.out.println("debut");
                SelectionKey key = keys.next();

                if(key.isReadable()){
                    System.out.println("isReadable");
                    SocketChannel chan = (SocketChannel) key.channel();
                    chan.configureBlocking(false);
                    ByteBuffer buffer = ByteBuffer.allocate(128);

                    chan.read(buffer);
                    buffer.flip();

                    String entree = new String(buffer.array()).trim();

                    //System.out.println(chan.socket().getPort());
                    if(map.containsKey(chan.socket().getPort())) {
                        System.out.println("entree traiterMessage");
                        traiterMessage(entree, chan);
                    }
                    else{
                        System.out.println("entree login");
                        traiterLogin(entree, chan);
                    }

                    buffer.clear();

                    chan.register(select, SelectionKey.OP_READ);
                    chan.close();

                } else if(key.isAcceptable()){
                    SocketChannel csc = ssc.accept();
                    System.out.println("isAcceptable");
                    csc.configureBlocking(false);
                    csc.register(select, SelectionKey.OP_READ);
                    //csc.register(select, SelectionKey.OP_WRITE);
                }
                System.out.println("remove");
                keys.remove();
            }
        }

    }

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

    private static void traiterLogin(String entree, SocketChannel chan) throws IOException {

        if(!verifierConnexion(entree)){
            chan.write(ByteBuffer.wrap("ERROR LOGIN aborting chatamu protocol".getBytes()));
        } else{
            String pseudo = recupererContenuLogin(entree) ;
            int portSocket = chan.socket().getPort();
            map.put(portSocket, pseudo);
            //System.out.println(map);

            chan.write(ByteBuffer.wrap("OK".getBytes()));
        }
    }


    private static String recupererContenuLogin(String entree){
        return entree.split(" ")[1] ;
    }

    private static String recupererContenuMessage(String entree){
        String[] entrees = entree.split(" ");
        entrees[0] = "";

        return Arrays.toString(entrees);
    }

    private static boolean verifierConnexion(String entree){
        return (entree.split(" ")[0].equals("LOGIN")) && (entree.split(" ").length == 2) ;
    }

    private static boolean verifierMessage(String entree){
        return (entree.split(" ")[0].equals("MESSAGE"));
    }
}
