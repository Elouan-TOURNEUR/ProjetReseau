package FederationServeurs.version2;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/* Serveur esclave de la fédération de serveur */
public class SlaveServeur {

    /* Nom du serveur */
    private static String name = null;

    /* Socket vers le serveur maître */
    private static  SocketChannel master = null;

    /* Liste qui contient toutes les socketsChannels */
    private static List<SocketChannel> listeSocket = new ArrayList<>();

    /* Liste des messages déterminant l'ordre d'affichage */
    private static ConcurrentLinkedQueue<String> messages = new ConcurrentLinkedQueue<>();


    public static void main(String[] args) throws IOException {
        int argc = args.length;

        // Traitement des arguments
        if (argc == 1) {
            name = args[0];
        } else {
            System.out.println("Usage: java SlaveServeur name");
            System.exit(2);
        }

        String address = "";
        String addressMaster = "";
        int port = 0;
        int portMaster = 0;

        // Lecture des infos serveurs dans le fichier de configuration
        List<String> lConf = Files.readAllLines(new File("./src/pairs.cfg").toPath());

        // Chargement des infos de config
        for(String conf : lConf){
            String[] confSplit = conf.split(" = ");
            if(confSplit[0].equals("master")){
                String[] split = confSplit[1].split(" ");
                addressMaster = split[0];
                portMaster = Integer.parseInt(split[1]);
            } else if(confSplit[0].equals(name)){
                String[] split = confSplit[1].split(" ");
                address = split[0];
                port = Integer.parseInt(split[1]);
            }
        }

        master = SocketChannel.open(new InetSocketAddress(addressMaster, portMaster));

        ServerSocketChannel ssc = ServerSocketChannel.open();
        ssc.socket().bind(new InetSocketAddress(address, port));
        ssc.configureBlocking(false);

        String messageReady = "OPEN " + name + " " + port ;
        System.out.println(master.toString());
        master.write(ByteBuffer.wrap(messageReady.getBytes()));

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
                        continue;
                    }

                    ByteBuffer msg = buffer.flip();
                    String entree = new String(msg.array()).trim();
                    String keyword = entree.split(" ")[0];

                    // Traitement d'actions en fonction du mot clé reçu
                    switch (keyword){
                        case "MESSAGE":
                            traiterMessage(entree, chan, select); break;
                        case "SERVERCONNECT":
                            traiterConnexionSalon(entree, chan); break;
                        case "LOGIN":
                            traiterLogin(entree, chan); break;
                        case "REPLIC":
                            traiterReplicationSalon(entree, chan, select); break;
                        case "SEND":
                            traiterMessagePersonnel(entree); break;
                        case "LIST":
                            traiterList(entree, chan); break;
                        case "EXIT":
                            traiterFin(entree, chan); break;
                        default:
                            chan.write(ByteBuffer.wrap("ERROR chatamu protocol".getBytes())); break;
                    }

                    buffer = ByteBuffer.allocate(128);

                } else if(key.isAcceptable()){
                    SocketChannel csc = ssc.accept();
                    csc.configureBlocking(false);
                    csc.register(select, SelectionKey.OP_READ);
                    listeSocket.add(csc);

                } else if (key.isWritable()){
                    SocketChannel chan = (SocketChannel) key.channel();
                    chan.configureBlocking(false);

                    String message = messages.poll();
                    if(message == null){
                        chan.register(select, SelectionKey.OP_READ);
                        continue;
                    }
                    System.out.println(message);
                    for(SocketChannel channel : listeSocket){
                        channel.write(ByteBuffer.wrap(message.getBytes()));
                    }

                    chan.register(select, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                }
                keys.remove();
            }
        }
    }

    /* Traitement de la déconnexion d'un client */
    private static void traiterFin(String entree, SocketChannel chan) throws IOException {
        if(!entree.equals("EXIT"))
            entree = "EXIT";

        chan.close();
        String message = entree + " " + chan.socket().getInetAddress().toString() + " " + chan.socket().getPort();
        master.write(ByteBuffer.wrap(message.getBytes()));
    }

    /* Envoie de la liste des serveurs au client qui a demandé */
    private static void traiterList(String entree, SocketChannel chan) throws IOException {
        String message = entree + " " + chan.socket().getInetAddress().toString() + " " + chan.socket().getPort();
        master.write(ByteBuffer.wrap(message.getBytes()));
    }

    /* Traitement d'un message destiné à un client en particulier */
    private static void traiterMessagePersonnel(String entree) throws IOException {
        String message = recupererContenuMessage(entree);
        String[] split = message.split(" ", 4);
        String address = split[1];
        int port = Integer.parseInt(split[2]);
        String list = split[3];
        SocketChannel channel = getChannel(address, port);

        if(channel != null){
            channel.write(ByteBuffer.wrap(list.getBytes()));
        }
    }

    /* Traitement du login */
    private static void traiterLogin(String entree, SocketChannel chan) throws IOException {
        String login = recupererContenuLogin(entree);
        String message = "LOGCLIENT " + login + " " +  chan.socket().getPort();
        master.write(ByteBuffer.wrap(message.getBytes()));
        traiterErreur(chan);
    }

    /* Traitement de la réplication d'un message */
    private static void traiterReplicationSalon(String entree, SocketChannel chan, Selector select) throws IOException {
        String message = recupererContenuMessage(entree);
        messages.add(message);
        chan.register(select, SelectionKey.OP_WRITE | SelectionKey.OP_READ);
    }

    /* Traitement des messages */
    private static void traiterMessage(String entree, SocketChannel chan, Selector select) throws IOException {
        if (!verifierMessage(entree)) {
            String messageErreur = "ERROR chatamu";
            chan.write(ByteBuffer.wrap(messageErreur.getBytes()));
        } else {
            String msg = recupererContenuMessage(entree);
            String message = "MESSAGE " + chan.socket().getPort() + " " + msg;
            master.write(ByteBuffer.wrap(message.getBytes()));
            traiterErreur(chan);
        }

        chan.register(select, SelectionKey.OP_READ);
    }

    /* Traitement de la connexion au salon */
    private static void traiterConnexionSalon(String entree, SocketChannel chan) throws IOException {
        String message  = entree + " " + chan.socket().getPort();
        master.write(ByteBuffer.wrap(message.getBytes()));
        ByteBuffer response = ByteBuffer.allocate(128);
        master.read(response);
        response.flip();
        chan.write(response);

        String resp = new String(response.array()).trim();
        if(!resp.split(" ")[0].equals("ERROR")){
            listeSocket.remove(chan);
            chan.close();
        }
    }

    /* Traitement des erreurs pour un client connecté sur ce serveur */
    private static void traiterErreur(SocketChannel chan) throws IOException {
        ByteBuffer response = ByteBuffer.allocate(128);
        master.read(response);
        response.flip();
        String resp = new String(response.array()).trim();
        if(!resp.equals("OK")) {
            chan.write(response);
        }
    }

    private static boolean verifierMessage(String entree){
        return (entree.split(" ")[0].equals("MESSAGE")) && (entree.split(" ").length > 1);
    }

    private static String recupererContenuLogin(String entree){
        return entree.split(" ")[1] ;
    }

    private static String recupererContenuMessage(String entree){
        String[] entrees = entree.split(" ", 2);
        return entrees[1];
    }

    private static SocketChannel getChannel(String address, int port){
        for(SocketChannel chan : listeSocket){
            if((chan.socket().getPort() == port) && (chan.socket().getInetAddress().toString().equals(address))){
                return chan;
            }
        }
        return null;
    }
}
