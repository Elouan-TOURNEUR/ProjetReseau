package ServeurFedereRobuste;

import java.nio.channels.SocketChannel;
import java.util.Vector;

public class Message {
    Vector<Integer> broadcast ;
    String message ;
    SocketChannel emetteur ;
    SocketChannel destinataire ;

    public Message(String message, Vector<Integer> vector) {
        this.broadcast = vector;
        this.message = message;
    }
}
