package Ameliorations;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.util.List;

import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import javafx.scene.text.*;
import javafx.scene.control.*;

import static java.lang.System.exit;

public class ClientGUI extends Application {
    private String info;
    private Stage stage;


    public static void main(String[] args) {
        launch(args);
    }


    @Override
    public void start(Stage stage) throws Exception {
        System.out.println(initConnection());
        GridPane view = this.handleLogin();
        Scene scene = new Scene(view);

        this.stage = stage;
        stage.setScene(scene);
        stage.show();

    }


    private void changeView(GridPane view){
        this.stage.setScene(new Scene(view));
        this.stage.show();
    }


    private GridPane handleServ() throws IOException {
        this.info = "Choisissez un serveur :";
        GridPane view = new GridPane();
        view.add(new Text(this.info), 1, 0, 1, 1);
        view.setMinSize(400, 400);
        view.setPadding(new Insets(25));
        view.setHgap(100);
        view.setVgap(30);

        List<String> lConf = Files.readAllLines(new File("./src/pairs.cfg").toPath());

        int line = 3;

        for(String conf : lConf){
            String txt = conf.split(" =")[0];
            Button button = new Button(txt);
            view.add(button, 1, line++, 1, 1);
            button.setOnMouseClicked(mouseEvent -> info = connect(txt));
        }

        return view;
    }

    private GridPane handleLogin(){
        GridPane view = new GridPane();
        this.info = "Choisissez votre nom utilisateur :";
        view.add(new Text(this.info), 1,0,1,1);
        view.setMinSize(400, 400);
        view.setPadding(new Insets(25));
        view.setHgap(100);
        view.setVgap(30);

        TextField loginField = new TextField();
        view.add(loginField,1,1,1,1);

        Button button = new Button("Valider");
        view.add(button, 1, 2, 1, 1);
        button.setOnMouseClicked(mouseEvent -> login(loginField.getText()));

        return view;
    }

    private GridPane handleChat(){
        GridPane view = new GridPane();
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
        view.add(messages, 1,1,1,1);
        TextField messageCl = new TextField();
        view.add(messageCl,1,2,1,1);

        ByteBuffer buffer = ByteBuffer.allocate(128);
        messageCl.setOnAction(acctionEvent -> {
            try{
                String entreeMessage = messageCl.getText();
                if(entreeMessage.equals("exit")){
                    client.write(ByteBuffer.wrap(entreeMessage.getBytes()));
                    client.close();
                    System.err.println("Fin de la session.");
                    exit(0);
                }

                entreeMessage = "MESSAGE "+entreeMessage;
                client.write(ByteBuffer.wrap(entreeMessage.getBytes()));
                buffer.flip();

                messageCl.setText("");

            } catch (IOException e){
                e.printStackTrace();
                exit(2);
            }
        });

        Thread threadRead = new Thread(new ReadGUIMessages(client, messages));
        threadRead.start();


        return view;
    }











    private ByteBuffer buffer;
    private SocketChannel client;



    public String initConnection() throws IOException {
        String response;
        String ip = null;
        int port = 0;

        /* Connexion*/
        //System.out.println("Essai de connexion à  " + ip + " sur le port " + port + "\n");
        this.buffer = null;


        List<String> lConf = Files.readAllLines(new File("./src/pairs.cfg").toPath());

        for(String conf : lConf){
            String[] confSplit = conf.split(" = ");
            if(confSplit[0].equals("master")){
                String[] split = confSplit[1].split(" ");
                ip = split[0];
                port = Integer.parseInt(split[1]);
                break;
            }
        }

        if (port > 65535) {
            return "Port hors limite";
        }


        try {
            client = SocketChannel.open(new InetSocketAddress(ip, port));
            response = client.toString();
            //System.err.println("le n° de la socket est : " + client);
            this.buffer = ByteBuffer.allocate(256);
        } catch (IOException e) {
            response = "Connexion: hôte inconnu : " + ip;
            e.printStackTrace();
        }

        return response;
    }


    private String connect(String servName) {
        ByteBuffer buffer = ByteBuffer.allocate(128);
        String response = null;
        try {
            String entreeServerConnect = "SERVERCONNECT " + servName;
            System.out.println(entreeServerConnect);
            client.write(ByteBuffer.wrap(entreeServerConnect.getBytes()));
            buffer.clear();
            client.read(buffer);

            response = (buffer != null) ? new String(buffer.array()).trim() : "";
            if (response.equals("ERROR SERVER")) {
                response = "Syntaxe invalide.";
                buffer.clear();
            } else if (response.equals("ERROR SERVER NAME")) {
                response = "Serveur invalide.";
            }
            else {
                response = "Vous avez rejoint le server avec succès.";
                this.changeView(this.handleChat());
                /*Thread threadRead = new Thread(new ReadMessages(client));
                Thread threadWrite = new Thread(new WriteMessages(client));
                threadRead.start();
                threadWrite.start();*/
            }

        } catch (IOException e) {
            response = "Erreur E/S socket";
            e.printStackTrace();
        }

        return response;
    }


    public String login(String pseudo) {
        String response;

        try {
            String entreeLogin;

            entreeLogin = "LOGIN " + pseudo;
            client.write(ByteBuffer.wrap(entreeLogin.getBytes()));
            client.read(this.buffer);
            System.out.println(this.buffer);

            response = (buffer != null) ? new String(buffer.array()).trim() : "";
            buffer.clear();
            System.out.println(response);
            if (response.equals("ERROR LOGIN aborting chatamu protocol")) {
                client.close();
                return "ERROR LOGIN aborting chatamu protocol";
            } else if (response.equals("ERROR LOGIN username")) {
                response = "Pseudo déja pris.";
            } else {
                this.changeView(this.handleServ());
            }

        } catch (IOException e) {
            response = "Erreur E/S socket";
            e.printStackTrace();
        }

        return response;
    }
}



class ReadGUIMessages implements Runnable{

    private SocketChannel client;
    private TextInputControl view;

    public ReadGUIMessages(SocketChannel client, TextArea view){
        this.client = client;
        this.view = view;
    }

    @Override
    public void run() {
        ByteBuffer buffer = ByteBuffer.allocate(128);
        String reponseMessage;

        try {
            while (true) {
                client.read(buffer);
                buffer.flip();

                reponseMessage = (buffer != null) ? new String(buffer.array()).trim() : "";

                view.setText(view.getText()+"\n"+reponseMessage);
                //System.out.println(reponseMessage);

                buffer.clear();
                buffer = ByteBuffer.allocate(128);
            }
        } catch (IOException e) {
            e.printStackTrace();
            exit(0);
        }
    }
}


