package Ameliorations;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import javafx.scene.text.*;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import static java.lang.System.exit;

/* Client GUI */
public class ClientGUI extends Application {

    /* Info reçu par le serveur */
    private String info;

    /* Stage de l'interface permettant de changer de scène */
    private Stage stage;

    /* Socket de ce client */
    public static volatile SocketChannel client;

    /* Buffer */
    private static ByteBuffer buffer;

    /* Correspond à la première fois que l'on change son pseudo : permet de savoir si l'on change de scène ou non */
    private static boolean first;


    public static void main(String[] args) {
        String ip;
        int port;
        first = true;

        /* Traitement des arguments */
        if (args.length != 2) {
            /* erreur de syntaxe */
            System.out.println("Usage: java ClientGUI ipServer port");
            exit(1);
        }
        ip = args[0];
        port = Integer.parseInt(args[1]);

        if (port > 65535) {
            System.err.println("Port hors limite");
            exit(3);
        }


        /* Connexion */
        System.out.println("Essai de connexion à  " + ip + " sur le port " + port + "\n");

        try {
            ClientGUI.client = SocketChannel.open(new InetSocketAddress(ip, port));
            ClientGUI.buffer = ByteBuffer.allocate(128);
            System.out.println(ClientGUI.client.toString());
            //System.err.println("le n° de la socket est : " + client);
        } catch (IOException e) {
            System.err.println("Connexion: hôte inconnu : " + ip);
            e.printStackTrace();
        }

        // Lance l'interface (appelle start)
        launch(args);
    }


    @Override
    public void start(Stage stage) {
        GridPane view = this.handleLogin();
        Scene scene = new Scene(view);

        this.stage = stage;
        stage.setScene(scene);
        stage.show();

    }

    /* Changer de vue */
    private void changeView(GridPane view){
        this.stage.setScene(new Scene(view));
        this.stage.show();
    }

    /* Change de vue pour le login */
    private GridPane handleLogin(){
        GridPane view = new GridPane();
        this.info = "Choisissez votre nom utilisateur :";
        view.add(new Text(this.info), 1,0,1,1);
        view.setMinSize(400, 400);
        view.setPadding(new Insets(25));
        view.setHgap(100);
        view.setVgap(30);

        TextField loginField = new TextField();
        loginField.setOnAction(actionEvent -> login(loginField.getText()));
        view.add(loginField,1,1,1,1);

        Button button = new Button("Valider");
        view.add(button, 1, 2, 1, 1);
        button.setOnMouseClicked(mouseEvent -> login(loginField.getText()));

        return view;
    }

    /* Change du vue pour le chat */
    private GridPane handleMainPane(){
        GridPane view = new GridPane();
        this.info = "Connecté";
        view.add(new Text(this.info), 1,0,1,1);
        view.setMinSize(400, 400);
        view.setPadding(new Insets(25));
        view.setHgap(100);
        view.setVgap(30);

        TextArea messages = new TextArea();
        messages.setEditable(false);
        messages.setMouseTransparent(true);
        messages.setFocusTraversable(false);
        messages.setText("Bienvenue sur le serveur !");
        view.add(messages, 1,1,2,1);
        TextField messageCl = new TextField();
        view.add(messageCl,1,2,2,1);

        ByteBuffer buffer = ByteBuffer.allocate(128);
        messageCl.setOnAction(acctionEvent -> {
            try{
                String entreeMessage = messageCl.getText();
                if(entreeMessage.equals("exit")){
                    ClientGUI.client.write(ByteBuffer.wrap(entreeMessage.getBytes()));
                    ClientGUI.client.close();
                    System.err.println("Fin de la session.");
                    exit(0);
                }

                entreeMessage = "MESSAGE "+entreeMessage;
                ClientGUI.client.write(ByteBuffer.wrap(entreeMessage.getBytes()));
                buffer.flip();

                messageCl.setText("");

            } catch (IOException e){
                e.printStackTrace();
                exit(2);
            }
        });

        Thread threadRead = new Thread(new ReadGUIMessages(messages));
        threadRead.start();

        return view;
    }

    /* Demande du login au client */
    public void login(String pseudo) {
        String response;

        try {
            String entreeLogin;

            entreeLogin = "LOGIN " + pseudo;
            ClientGUI.client.write(ByteBuffer.wrap(entreeLogin.getBytes()));

            if(first){
                ClientGUI.client.read(buffer);
                System.out.println(buffer);

                response = (buffer != null) ? new String(buffer.array()).trim() : "";
                buffer.clear();
                System.out.println(response);
                if (response.equals("ERROR LOGIN aborting chatamu protocol")) {
                    alert("Erreur de protocole");
                } else if (response.equals("ERROR LOGIN username")) {
                    alert("Pseudo déja pris");
                } else {
                    this.changeView(this.handleMainPane());
                }
            }

        } catch (IOException e) {
            alert("Erreur E/S socket");
            e.printStackTrace();
        }
    }

    public static void alert(String message){
        Alert alert = new Alert(AlertType.ERROR, message, ButtonType.OK);
        alert.showAndWait();
    }
}


/* Thread pour la lecture et l'envoie de message pour ce client */
class ReadGUIMessages implements Runnable{

    private TextInputControl view;

    public ReadGUIMessages(TextArea view){
        this.view = view;
    }

    @Override
    public void run() {
        ByteBuffer buffer = ByteBuffer.allocate(128);
        String reponseMessage;

        try {
            while (true) {
                ClientGUI.client.read(buffer);
                buffer.flip();

                reponseMessage = (buffer != null) ? new String(buffer.array()).trim() : "";

                if(reponseMessage.split(" ")[0].equals("ERROR")){
                    //System.err.println(reponseMessage);
                    //ClientGUI.alert(reponseMessage);
                } else if(!reponseMessage.split(" ")[0].equals("OK")){
                    view.setText(view.getText()+"\n"+reponseMessage);
                }

                buffer.clear();
                buffer = ByteBuffer.allocate(128);
            }
        } catch (IOException e) {
            e.printStackTrace();
            exit(0);
        }
    }
}


