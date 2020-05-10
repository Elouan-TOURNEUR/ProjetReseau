package ServeurFedereRobuste;

import java.util.Vector;

/** Classe qui définit les messages qui transitent entre clients-serveurs
 * Le vecteur d'integer sert à l'implémentation de l'algorithme distribué
 */
public class Message {
    Vector<Integer> broadcast ;
    String message ;


    public Message(String message, Vector<Integer> vector) {
        this.broadcast = vector;
        this.message = message;
    }
}
