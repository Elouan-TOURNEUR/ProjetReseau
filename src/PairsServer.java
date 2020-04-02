import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;

public class PairsServer {
    private static String name = null ;
    private static  SocketChannel client = null ;

    /* Liste qui contient toutes les socketsChannels */
    private static List<SocketChannel> listeSocket = new ArrayList<>() ;

    private static List<String>  clients = new ArrayList<>() ;

    private class message {
        int[][] matrice ;
        String message ;
        SocketChannel emetteur ;
        SocketChannel destinataire ;

        public message(int[][] matrice, String message, SocketChannel emetteur, SocketChannel destinataire) {
            this.matrice = matrice;
            this.message = message;
            this.emetteur = emetteur;
            this.destinataire = destinataire;
        }
    }

}
