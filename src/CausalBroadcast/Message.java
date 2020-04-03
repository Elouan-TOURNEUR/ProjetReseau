package CausalBroadcast;

import java.nio.channels.SocketChannel;
import java.util.Vector;

public class Message {
    Vector<Integer> broadcast ;
    String message ;
    SocketChannel emetteur ;
    SocketChannel destinataire ;

    public Message(String message) {
        this.broadcast = new Vector<Integer>();
        this.message = message;
    }
}
