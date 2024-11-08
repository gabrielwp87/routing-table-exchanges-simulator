package src;

import java.io.*;
import java.net.*;
import java.util.*;

import src.Route;

class UDPServer {
    private static Map<String, InetAddress> clients = new HashMap<>();
    private static Map<String, Integer> clientPorts = new HashMap<>();

    public static void main(String args[]) throws Exception {
        // default port, as asked
        int port = 19000;

        String file = "src\\roteadores.txt";

        // create a list that will keep the routes, forming a routing table
        ArrayList<Route> routingTable = new ArrayList<Route>();

        // Question the user if a new net will be created (YES case) or if there is a net (case no) and need to connect
        Scanner scanner = new Scanner(System.in);

        System.out.println("Construir uma rede nova? (responda com 'sim' ou 'não')");
        String answer = scanner.nextLine().trim().toLowerCase();

        if (answer.equals("sim") || answer.equals("s") || answer.equals("yes") || answer.equals("y")) {
            // Path to yes
            // read the roteadores.txt file and create the initial rounting table
            try {
                File roteadores = new File(file);
                Scanner myReader = new Scanner(roteadores);
                while (myReader.hasNextLine()) {
                    String data = myReader.nextLine();
                    Route route = new Route(data, 1, data);
                    routingTable.add(route);
                }
                myReader.close();
            } catch (FileNotFoundException e) {
                System.out.println("An error occurred.");
                e.printStackTrace();
            }

            for (Route route : routingTable) {
                System.out.println("Initial table: ");
                System.out.println(route.getIpDestiny() + " " + route.getMetric() + " " + route.getIpOut());
            }

        } else {
            // Path to no
            // send message to an IP

        }

        scanner.close();



        

        // Mensage recieve and added as route =======================================
        String mensageRecieved = "";
        String[] mensage = mensageRecieved.split("!");
        System.out.println("Mensage to recieved: ");
        
        for (String s : mensage) {
            if (s.length() > 0) {
                System.out.println(s);
                String ip = s.substring(0, s.length() - 2);
                String metric = s.substring(s.length() - 1, s.length());
                
                int metricInt = Integer.parseInt(metric);
                
                // TODO: newIpOut must be correct to the ip that send the mensage it recieves
                String newIpOut = "192.168.1.200";
                for (Route route : routingTable) {
                    if(route.getIpDestiny().equals(ip) && metricInt < route.getMetric()) {
                        route.UpdateRoute(metricInt, newIpOut);
                        break;
                    }
                }
            }
        }


        DatagramSocket serverSocket = new DatagramSocket(port);

        while (true) {
            byte[] receiveData = new byte[1024];
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            serverSocket.receive(receivePacket);

            String receivedMessage = new String(receivePacket.getData(), 0, receivePacket.getLength());
            InetAddress clientIpAddress = receivePacket.getAddress();
            int clientPort = receivePacket.getPort();

            // TODO: put the 15 seconds wait to it
            sendMessage(routingTable, serverSocket);

            /* 
            clienteIpAddress is the IP address from who sent the message
             */
            handleCommand(routingTable, receivedMessage, clientIpAddress, clientPort, serverSocket);
        }
    }


    private static void handleCommand(ArrayList<Route> routingTable, String command, InetAddress address, int port, DatagramSocket socket) throws IOException {
        if(command.startsWith("!")) {
            registerRoutingTable(routingTable, command, address, port, socket);
       
        } else if (command.startsWith("@")) {
            addIPToRoutingTable(routingTable, command, address, port, socket);

        } else if (command.startsWith("&")) {
            sendMessage(routingTable, socket);

        } else {
            System.out.println("Unknow command!");
        }
    }

    // TODO: registeRoutingTable logic
    private static void registerRoutingTable(String message, InetAddress address, int metric) {
        // clients.put(nickname, address);
        // clientPorts.put(nickname, port);



        System.out.println("Message recieved: " + message);
    }

    private static void addIPToRoutingTable(ArrayList<Route> routingTable, command, address, port, socket) {


        for (Route route : routingTable) {
            if(route.getIpDestiny().equals(ip) && metricInt < route.getMetric()) {
                route.UpdateRoute(metricInt, newIpOut);
                break;
            }
        }

        System.out.println("New route added: " + message);
    }

    private static void sendRoutingTable(ArrayList<Route> routingTable, DatagramSocket socket) throws IOException {
    // (String nickname, String message, InetAddress senderAddress, int senderPort, DatagramSocket socket) throws IOException {
        // It will (re)create the message with the routing table to send
        String messageToSend = "";
        for (Route route : routingTable) {
            messageToSend += "!" + route.getIpDestiny() + ":" + route.getMetric();
        }

        // It will send the message only to the neighboors routers
        for (Route route : routingTable) {
            if (route.getMetric() == 1) {
                byte[] sendData = messageToSend.getBytes();
                InetAddress ipDestiny = InetAddress.getByName(route.getIpDestiny());
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ipDestiny, 19000);
                socket.send(sendPacket);
            }
        }

        // Test Mensage to send =======================================
        // System.out.println("Initial table: ");
        // System.out.println();
        // System.out.println("Mensage to send: ");
        // System.out.println(messageToSend);
        // System.out.println();
    }

    private static void unregisterUser(InetAddress address, int port) {
        // Implementar lógica para remover usuário registrado
    }
}
