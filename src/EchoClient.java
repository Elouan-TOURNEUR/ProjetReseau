/*  echo / client simple
    Master Informatique 2012 -- Université Aix-Marseille
    Emmanuel Godard - Bilel Derbel
*/

import java.io.*;
import java.net.*;
import java.util.Scanner;

import static java.lang.System.exit;

public class EchoClient {

    public static void main(String[] args) throws IOException {
        Socket echoSocket; // la socket client
        String ip; // adresse IPv4 du serveur en notation pointée
        int port; // port TCP serveur
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
        PrintWriter out;
        BufferedReader in;
        boolean fini = false;

        /* Traitement des arguments */
        if (args.length != 2) {
            /* erreur de syntaxe */
            System.out.println("Usage: java EchoClient @server @port");
            exit(1);
        }
        ip = args[0];
        port = Integer.parseInt(args[1]);

        if (port > 65535) {
            System.err.println("Port hors limite");
            exit(3);
        }

        /* Connexion */
        System.out.println("Essai de connexion à  " + ip + " sur le port " + port + "\n");
        try {
            echoSocket = new Socket(ip, port);
            System.err.println("le n° de la socket est : " + echoSocket);
            /* Initialisation d'agréables flux d'entrée/sortie */
            out = new PrintWriter(echoSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(echoSocket.getInputStream()));
        } catch (UnknownHostException e) {
            System.err.println("Connexion: hôte inconnu : " + ip);
            e.printStackTrace();
            return;
        }

        /* Session */
        try {
            String reponseLogin;
            String entreeLogin;
            System.out.println("LOGIN pseudo");
            Scanner scan = new Scanner(System.in);
            entreeLogin = scan.nextLine();
            out.println(entreeLogin);
            reponseLogin = in.readLine();
            if ((reponseLogin != null) && (reponseLogin.equals("ERROR LOGIN aborting chatamu protocol"))) {
                System.out.println(reponseLogin);
                echoSocket.close();
                exit(2);
            }
            while (true) {
                String entreeMessage;
                String reponseMessage;
                System.out.println("MESSAGE message");
                entreeMessage = scan.nextLine();

                if(entreeMessage.equals("exit")){
                    break;
                }

                out.println(entreeMessage);
                System.out.println(entreeMessage);
                reponseMessage = in.readLine();
                System.out.println(reponseMessage);

                if ((reponseMessage != null) && (reponseMessage.equals("ERROR chatamu"))) {
                    System.out.println(reponseMessage);
                }
            }


            in.close();
            out.close();
            stdin.close();
            echoSocket.close();
            System.err.println("Fin de la session.");
        } catch (IOException e) {
            System.err.println("Erreur E/S socket");
            e.printStackTrace();
            exit(8);
        }
    }
}