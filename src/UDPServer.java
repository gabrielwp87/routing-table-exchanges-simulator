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

        // get local IP
        InetAddress localIp = InetAddress.getLocalHost();

        // convert IP address to string
        String myIP = localIp.getHostAddress();

        // create a list that will keep the routes, forming a routing table
        ArrayList<Route> routingTable = new ArrayList<Route>();

        DatagramSocket serverSocket = new DatagramSocket(port);

        // Question the user if a new net will be created (YES case) or if there is a net (case no) and need to connect
        Scanner scanner = new Scanner(System.in);

        System.out.println("Create a new network? (answer with 'yes' or 'no')");
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

            System.out.println("Initial table: ");
            for (Route route : routingTable) {
                System.out.println(route.getIpDestiny() + " " + route.getMetric() + " " + route.getIpOut());
            }

        } else {
            // Path to no --- THERE IS A NETWORK
            // send message to an IP

            System.out.println("Enter target's IP: ");
            String answerIP = scanner.nextLine().trim();
            String messageToSend = "@" + myIP;

            byte[] sendData = messageToSend.getBytes();
            InetAddress ipDestiny = InetAddress.getByName(answerIP);
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ipDestiny, 19000);
            serverSocket.send(sendPacket);

        }
        scanner.close();

        String senderIpAddress;

        while (true) {
            byte[] receiveData = new byte[1024];
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            serverSocket.receive(receivePacket);

            String receivedMessage = new String(receivePacket.getData(), 0, receivePacket.getLength());
            InetAddress IpAddress = receivePacket.getAddress();
            senderIpAddress = IpAddress.getHostAddress();
            
            // it shoulnd not be needed, but let it here!!!
            // int clientPort = receivePacket.getPort();

            // TODO: put the 15 seconds wait to it
            // sendMessage(routingTable, message, socket, myIP);

            /*
            senderIpAddress is the IP address from who sent the message
             */
            handleCommunication(routingTable, receivedMessage.trim(), senderIpAddress, serverSocket, myIP);
        }
    }


    private static void handleCommunication(ArrayList<Route> routingTable, String message, String senderIpAddress, DatagramSocket socket, String myIP) throws IOException {

         switch (message.charAt(0)) {
            case '!':
                registerInRoutingTable(routingTable, message, senderIpAddress, socket);
                break;
            case '@':  // message to enter in a network (that already exist)
                addIPToRoutingTable(routingTable, message, senderIpAddress, socket);
                break;
            case '&':
                recieveMessage(routingTable, message, socket, myIP);
                break;

            default:
                System.out.println("Unknown message!");
        }
    }

    private static void registerInRoutingTable(ArrayList<Route> routingTable, String message, String senderIpAddress, DatagramSocket socket) {
        // case message starts with '!'
        String[] mensageRecieved = message.split("!");
        // System.out.println("Mensage to recieved: ");
        
        // !192.168.1.2:1!192.168.1.3:1
        for (String s : mensageRecieved) {
            if (s.length() > 0) {
                System.out.println(s);
                String[] parts = s.split(":");
                String ip = parts[0];
                String metric = parts[1];

                int metricInt = Integer.parseInt(metric) + 1;

                boolean found = false;

                String newIpOut = senderIpAddress;

                for (Route route : routingTable) {  // iterate the existing routing table
                    if(route.getIpDestiny().equals(ip)) {  // find if the ip is already in the routing table
                        found = true;
                        if(metricInt < route.getMetric()) {
                            route.UpdateRoute(metricInt, newIpOut);  // if the ip has a smaller metric it updates the routing table
                        }
                        break;
                    }
                }

                // as the ip wasn't in the routing table it will be added to it
                if(!found) {
                    Route newRoute = new Route(ip, metricInt, newIpOut);
                    routingTable.add(newRoute);
                }
            }
        }

        System.out.println("Current Routing Table: ");
        for (Route route : routingTable) {
            System.out.println(route.getIpDestiny() + " " + route.getMetric() + " " + route.getIpOut());
        }
    }

    private static void addIPToRoutingTable(ArrayList<Route> routingTable, String message, String senderIpAddress, DatagramSocket socket) {
        // case message starts with '@'
        String ip = message.substring(1);
        
        // TODO: confirm if the message is real or not, the message send an IP (ex.: @192.168.1.1) and the address has it's IP
        if(ip.equals(senderIpAddress)) {
            Route newRoute = new Route(ip, 1, senderIpAddress);
            routingTable.add(newRoute);

            System.out.println("New route added: " + message);
            System.out.println(newRoute.getIpDestiny() + " " + newRoute.getMetric() + " " + newRoute.getIpOut());

        } else {
            System.out.println("IP is different from ip source, addicion to routing table not accepted!");
        }

    }

    private static void sendRoutingTable(ArrayList<Route> routingTable, DatagramSocket socket, String myIP) throws IOException {
    // (String nickname, String message, InetAddress senderAddress, int senderPort, DatagramSocket socket) throws IOException {
        // It will (re)create the message with the routing table to send
        String messageToSend = "!" + myIP + "1";
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

    private static void recieveMessage(ArrayList<Route> routingTable, String message, DatagramSocket socket, String myIP) throws IOException {
        // &192.168.1.2%192.168.1.1%Oi tudo bem?
        // O primeiro endereço é o IP da origem, o segundo é o IP de destino e a seguir vem a mensagem de texto.
        
        String mensageRecieved = message.substring(1);
        String[] parts = mensageRecieved.split("%");

        String ipDestiny = parts[0];

        if(parts[1].equals(myIP)) {
            System.out.println(parts[2]);
            return;
        }

        boolean found = false;

        for (Route route : routingTable) {  // iterate the existing routing table
            if(route.getIpDestiny().equals(ipDestiny)) {
                found = true;        
                String sendTo = route.getIpOut();  // get the ip out to know to who it needs to repass the message

                byte[] sendData = message.getBytes();
                InetAddress IpOut = InetAddress.getByName(sendTo);
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IpOut, 19000);
                socket.send(sendPacket);
                break;
            }
        }
        if(found) {
            System.out.println("Message recieved and sent: " + message);
        } else {
            System.out.println("Route not found!");
        }
    }

    // TODO
    private static void removeRoute(String senderIpAddress) {
    }
}
