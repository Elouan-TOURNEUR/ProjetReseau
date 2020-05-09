package FederationServeurs.version2;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/*
* Serveur maître de la fédération de serveur
*/
public class ChatamuCentral {

    /* Map qui associe un port client à un pseudo */
    private static HashMap<Integer, String> clientPseudo = new HashMap<>();

    /* Liste qui contient toutes les socketsChannels client */
    private static List<SocketChannel> listeSocketClient = new ArrayList<>() ;

    /* Map qui associe un serveur à un nom */
    private static HashMap<String, SocketChannel> serveursNames = new HashMap<>() ;

    /* Map qui associe un SocketChannel de réception d'un serveur à un SocketChannel client vers ces serveurs */
    private static HashMap<SocketChannel, SocketChannel> serveurCommunication = new HashMap<>();

    /* Liste du master qui détermine l'ordre d'affichage */
    private static ConcurrentLinkedQueue<String> master = new ConcurrentLinkedQueue<>() ;

    /* Serveur courant */
    private static ServerSocketChannel server;

    public static void main(String[] args) throws IOException {
        String address = "";
        int port = 0;

        // Lecture des infos serveurs dans le fichier de configuration
        List<String> lConf = Files.readAllLines(new File("./src/pairs.cfg").toPath());

        // Chargement des infos de config
        for(String conf : lConf){
            String[] confSplit = conf.split(" = ");
            String[] split = confSplit[1].split(" ");
            if(confSplit[0].equals("master")){
                address = split[0];
                port = Integer.parseInt(split[1]);
                break;
            }
        }

        server = ServerSocketChannel.open();
        server.socket().bind(new InetSocketAddress(address, port));

        try {
            long timeReferant = System.nanoTime() ;
            long time = System.nanoTime() ;

            server.configureBlocking(false);
            Selector select = Selector.open();
            server.register(select, SelectionKey.OP_ACCEPT);
            ByteBuffer buffer = ByteBuffer.allocate(128);

            //serveursDisponibles.add("master");
            serveursNames.put("master", null);

            // Boucle sur le selecteur
            while (true) {
                select.select();
                Iterator<SelectionKey> keys = select.selectedKeys().iterator();

                while (keys.hasNext()) {
                    // Simulation d'une défaillance
                    /*if (System.nanoTime()/1000000000 - timeReferant/1000000000 > 180)
                        return ;
                    if (System.nanoTime()/1000000000 - time/1000000000 > 15) {
                        Thread.sleep(10000);
                        time = System.nanoTime() ;
                    }*/
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
                        String keyword = entree.split(" ")[0];

                        // Traitement d'actions en fonction du mot clé reçu
                        switch (keyword){
                            case "MESSAGE":
                                traiterMessages(entree, chan, select); break;
                            case "SERVERCONNECT":
                                traiterConnexionSalon(entree, chan, select); break;
                            case "LOGIN":
                                traiterLogin(entree, chan, select); break;
                            case "LOGCLIENT":
                                traiterAjoutClient(entree, chan); break;
                            case "OPEN":
                                traiterOuvertureSalon(entree, chan); break;
                            case "CLOSE":
                                traiterFermetureSalon(entree, chan); break;
                            case "LIST":
                                traiterList(entree, chan); break;
                            case "EXIT":
                                traiterFin(entree, chan); break;
                            default:
                                chan.write(ByteBuffer.wrap("ERROR chatamu protocol".getBytes())); break;
                        }

                        buffer = ByteBuffer.allocate(128);

                    } else if (key.isAcceptable()) {
                        SocketChannel csc = server.accept();
                        csc.configureBlocking(false);
                        csc.register(select, SelectionKey.OP_READ);
                        listeSocketClient.add(csc);

                    } else if (key.isWritable()) {
                        SocketChannel chan = (SocketChannel) key.channel();
                        chan.configureBlocking(false);

                        String message = master.poll();
                        if(message == null) {
                            chan.register(select, SelectionKey.OP_READ);
                            continue;
                        }

                        System.out.println(recupererContenuMessage(message));
                        // Ecriture du message sur les serveurs pairs
                        for(SocketChannel channel : serveursNames.values()){
                            if(channel != null)
                                serveurCommunication.get(channel).write(ByteBuffer.wrap(message.getBytes()));
                        }
                        message = recupererContenuMessage(message);
                        // Ecriture du message sur les clients directement connectés
                        for(SocketChannel channel : listeSocketClient){
                            channel.write(ByteBuffer.wrap(message.getBytes()));
                        }

                        chan.register(select, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                    }

                    keys.remove();
                }
            }
        }catch (IOException e) {
            e.printStackTrace();
        }

    }

    /* Traitement de la déconnexion d'un client */
    private static void traiterFin(String entree, SocketChannel chan) throws IOException {
        int port;
        if(serveursNames.containsValue(chan)){
            port = Integer.parseInt(entree.split(" ")[2]);
        } else{
            port = chan.socket().getPort();
            listeSocketClient.remove(chan);
            chan.close();
        }

        System.out.println(port);
        System.out.println(clientPseudo.remove(port));
    }

    /* Ajout d'un client dans la Map */
    private static void traiterAjoutClient(String entree, SocketChannel chan) throws IOException {
        if(!serveursNames.containsValue(chan)){
            chan.write(ByteBuffer.wrap("ERROR SERVER LOGIN".getBytes()));
            return;
        }
        String msg = recupererContenuMessage(entree);
        String[] split = msg.split(" ");
        int port = Integer.parseInt(split[1]);
        String pseudo = split[0];
        clientPseudo.put(port, pseudo);

        chan.write(ByteBuffer.wrap("OK".getBytes()));
    }

    /* Traitement de la connexion d'un serveur esclave */
    private static void traiterOuvertureSalon(String entree, SocketChannel chan) throws IOException {
        String nomSalon = entree.split(" ")[1] ;
        String portString = entree.split(" ")[2] ;
        int port = Integer.parseInt(portString) ;

        System.out.println("Le serveur " + nomSalon + " a ouvert.");
        serveursNames.put(nomSalon, chan) ;

        String address = chan.socket().getLocalAddress().toString().split("/")[1];
        SocketChannel communication = SocketChannel.open(new InetSocketAddress(address, port));
        serveurCommunication.put(chan, communication);
        listeSocketClient.remove(chan);
    }

    /* Traitement de la déconnexion d'un serveur esclave*/
    private static void traiterFermetureSalon(String entree, SocketChannel chan) {
        String nomSalon = entree.split(" ")[1] ;
        System.out.println("Le serveur " + nomSalon + " a fermé.");
        serveursNames.remove(nomSalon, chan) ;
        serveurCommunication.remove(chan);
    }

    /* Traitement du login */
    private static void traiterLogin(String entree, SocketChannel chan, Selector select) throws IOException {
        if(!verifierConnexion(entree)){
            chan.write(ByteBuffer.wrap("ERROR LOGIN aborting chatamu protocol".getBytes()));
        }
        else if(!verifierPseudo(recupererContenuLogin(entree))) {
            chan.write(ByteBuffer.wrap("ERROR LOGIN username".getBytes()));
        }
        else {
            String pseudo =  recupererContenuLogin(entree) ;
            int portSocket = chan.socket().getPort();

            if(clientPseudo.get(portSocket) != null){
                String message = "REPLIC " + clientPseudo.get(portSocket) + " est devenu " + pseudo;
                master.add(message);
                chan.register(select, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
            }

            clientPseudo.put(portSocket, pseudo);

            chan.write(ByteBuffer.wrap("OK".getBytes()));
        }
    }

    /* Envoie de la liste des serveurs au client qui a demandé */
    private static void traiterList(String entree, SocketChannel chan) throws IOException {
        StringBuilder listeSalon = new StringBuilder();
        for (String salon : serveursNames.keySet()) {
            listeSalon.append(salon).append('\n');
        }

        if(serveursNames.containsValue(chan)){
            String msg = "SEND " + recupererContenuMessage(entree) + listeSalon;
            chan.write(ByteBuffer.wrap(msg.getBytes()));
        } else{
            chan.write(ByteBuffer.wrap(listeSalon.toString().getBytes()));
        }
    }

    /* Traitement de la connexion au salon et replication de la notification sur les autres serveurs */
    private static void traiterConnexionSalon(String entree, SocketChannel chan, Selector select) throws IOException {
        String name = entree.split(" ")[1];
        if(!verifierServeur(name)){
            chan.write(ByteBuffer.wrap("ERROR SERVER NAME".getBytes()));
            return;
        }

        SocketChannel serveur = serveursNames.get(name) ;

        int port;
        if(!listeSocketClient.contains(chan))
            port = Integer.parseInt(entree.split(" ")[2]);
        else{
            port = chan.socket().getPort();
        }

        String pseudo = clientPseudo.get(port);

        if(pseudo == null){
            chan.write(ByteBuffer.wrap("ERROR UNKNOWN CLIENT".getBytes()));
            return;
        }

        String message = "REPLIC " + pseudo + " a rejoint le serveur " + name;
        master.add(message);

        String address;
        if(serveur != null) {
            address = serveur.socket().getLocalAddress().toString().split("/")[1];
            port = serveurCommunication.get(serveur).socket().getPort();
        }
        else {
            address = server.socket().getInetAddress().toString().split("/")[1];
            port = server.socket().getLocalPort();
        }

        String redirection = "CONNECT " + address + " " + port;
        chan.write(ByteBuffer.wrap(redirection.getBytes()));

        if(!serveursNames.containsValue(chan)){
            listeSocketClient.remove(chan);
            chan.close();
        }

        if(serveur != null)
            serveur.register(select, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
        else
            chan.register(select, SelectionKey.OP_READ | SelectionKey.OP_WRITE);

    }

    /* Traitement des messages et réplication sur les autres serveurs */
    private static void traiterMessages(String entree, SocketChannel chan, Selector select) throws IOException {
        if (!verifierMessage(entree)) {
            String messageErreur = "ERROR chatamu";
            chan.write(ByteBuffer.wrap(messageErreur.getBytes()));
            return;
        }

        String message = recupererContenuMessage(entree);
        int port;

        if (serveursNames.containsValue(chan)){
            String[] split = message.split(" ", 2);
            port = Integer.parseInt(split[0]);
            message = split[1];
        } else {
            port = chan.socket().getPort();
        }

        if(!clientPseudo.containsKey(port)){
            chan.write(ByteBuffer.wrap("ERROR UNKNOWN CLIENT".getBytes()));
            return;
        }

        message = clientPseudo.get(port) + "> " + message;
        String messageTraite = "REPLIC " + message;
        master.add(messageTraite);

        chan.write(ByteBuffer.wrap("OK".getBytes()));
        chan.register(select, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
    }

    private static boolean verifierMessage(String entree){
        return (entree.split(" ")[0].equals("MESSAGE")) && (entree.split(" ").length > 1);
    }

    private static boolean verifierServeur(String name) {
        return serveursNames.containsKey(name);
    }

    private static boolean verifierConnexion(String entree){
        return (entree.split(" ")[0].equals("LOGIN")) && (entree.split(" ").length == 2) ;
    }

    private static boolean verifierPseudo(String login) {
        return !clientPseudo.containsValue(login);
    }

    private static String recupererContenuLogin(String entree){
        return entree.split(" ")[1] ;
    }

    private static String recupererContenuMessage(String entree){
        return entree.split(" ", 2)[1];
    }

}





