package src;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

import src.Route;
import src.UDPClient.MessageReceiver;



class UDPServer {
    private static final int DEST_PORT = 19000; // Recipient port, default port

    private static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private static ArrayList<Route> routingTable = new ArrayList<Route>();

    // Blocking objects for `routingTable` abd `rotaTasks`
    private static final Object routingTableLock = new Object();

    private static String myIP;


    public static void main(String args[]) throws Exception {
        String file = "src\\roteadores.txt";
        
        if (System.getProperty("os.name").equals("Linux")) file = "src/roteadores.txt";
    
        // get local IP
        InetAddress localIp = InetAddress.getLocalHost();
    
        // convert IP address to string
        // myIP = localIp.getHostAddress();
        myIP = "10.32.162.223";
    
        DatagramSocket serverSocket = new DatagramSocket(DEST_PORT);
    
        // Question the user if a new net will be created (YES case) or if there is a
        // net (case no) and need to connect
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    
        System.out.println("Create a new network? (answer with 'yes' or 'no')");
        String answer = reader.readLine().trim().toLowerCase();
    
        if (answer.equals("sim") || answer.equals("s") || answer.equals("yes") || answer.equals("y")) {
            // Path to yes
            // read the roteadores.txt file and create the initial rounting table
    
            String line;
            try (BufferedReader myReader = new BufferedReader(new FileReader(file))) {
                while ((line = myReader.readLine()) != null) {
                    Route route = new Route(line, 1, line);
                    routingTable.add(route);
                }
            } catch (FileNotFoundException e) {
                System.out.println("An error occurred.");
                e.printStackTrace();
            }
    
            addRoutesTask(serverSocket); // Add tasks to a route
    
            System.out.println("Initial table: ");
            for (Route route : routingTable) {
                System.out.println(route.getIpDestiny() + " " + route.getMetric() + " " + route.getIpOut());
            }
    
        } else {
            // Path to no --- THERE IS A NETWORK
            // send message to an IP
            System.out.println("Enter target's IP: ");
            String answerIP = reader.readLine().trim();
            String messageToSend = "@" + answerIP;
            byte[] sendData = messageToSend.getBytes();
            InetAddress ipDestiny = InetAddress.getByName(answerIP);
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ipDestiny, DEST_PORT);
            serverSocket.send(sendPacket);
        }
    
        // Thread to receive messages
        new Thread(() -> {
            System.out.println("Thread to receive messages started.");
            while (true) {
                byte[] receiveData = new byte[1024];
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                try {
                    serverSocket.receive(receivePacket);
                    String receivedMessage = new String(receivePacket.getData(), 0, receivePacket.getLength());
                    InetAddress IpAddress = receivePacket.getAddress();
                    String senderIpAddress = IpAddress.getHostAddress();
                    
                    System.out.println(receivedMessage + " from: " + senderIpAddress);
                    // senderIpAddress is the IP address from who sent the message
                    handleCommunication(receivedMessage.trim(), senderIpAddress, serverSocket);
                } catch (IOException e) {
                    System.out.println("Error receiving packet: " + e.getMessage());
                }
            }
        }).start();
    
        scheduleRemoveInactiveRouters();
        
        while (true) {
            
            synchronized (routingTableLock) {
                for (Route route : routingTable) {
                    System.out.println(route);
                }
            }
            System.out.print("Type your message: ");
            String message = reader.readLine();
            System.out.print("Type the destiny's IP: ");
            String routeDest = reader.readLine();
            System.out.println("Message: " + message + " sent to: " + routeDest);
            try {
                sendMessage(message, serverSocket, routeDest);
                System.out.println(message + " to: " + routeDest + "+++++++++++++++++++++++++++++");
            } catch (IOException e) {
                System.out.println("Error sending message: " + e.getMessage());
            }
        }
    }

    
    private static void sendMessage(String message, DatagramSocket socket, String routeDest) throws IOException {
        // &192.168.1.2%192.168.1.1%Oi tudo bem?
        StringBuilder messageToSend = new StringBuilder();
        messageToSend.append("&");
        messageToSend.append(myIP);
        messageToSend.append("%");
        messageToSend.append(routeDest);
        messageToSend.append("%");
        messageToSend.append(message);
        byte[] sendData = messageToSend.toString().getBytes();
        InetAddress ipDestiny = InetAddress.getByName(routeDest);

        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ipDestiny, DEST_PORT);
        try {
            socket.send(sendPacket);
            System.out.println("Message sent: " + message);
        } catch (IOException e) {
            System.out.println("Error sending message: " + e.getMessage());
        }
    }


    private static void scheduleRemoveInactiveRouters() {
        scheduler.scheduleAtFixedRate(() -> removeInactiveRouters(), 0, 35, TimeUnit.SECONDS);
    }


    private static void handleCommunication(String message, String senderIpAddress, DatagramSocket socket)
            throws IOException {

        switch (message.charAt(0)) {
            case '!':
                registerInRoutingTable(message, senderIpAddress, socket);
                break;
            case '@': // message to enter in a network (that already exist)
                addIPToRoutingTable(message, senderIpAddress, socket);
                break;
            case '&':
                recieveMessage(message, socket);
                break;

            default:
                System.out.println("Unknown message!");
        }
    }


    private static void registerInRoutingTable(String message, String senderIpAddress, DatagramSocket socket) {
        // case message starts with '!'
        String[] mensageRecieved = message.split("!");
        // System.out.println("Mensage to recieved: ");

        // !192.168.1.2:1!192.168.1.3:1
        for (String s : mensageRecieved) {
            if (s.length() > 0) {
                System.out.println(s);
                String[] parts = s.split(":");
                String ip = parts[0];

                // If the IP is the same as my own IP, it will not be added to the routing table
                if (ip.equals(myIP)) {
                    continue;
                }
                
                String metric = parts[1];

                int metricInt = Integer.parseInt(metric) + 1;

                boolean found = false;

                String newIpOut = senderIpAddress;
                Route senderRoute = new Route(senderIpAddress, 1, senderIpAddress);
                routingTable.add(senderRoute);

                for (Route route : routingTable) { // iterate the existing routing table
                    if (route.getIpDestiny().equals(ip)) { // find if the ip is already in the routing table
                        found = true;
                        if (metricInt < route.getMetric()) {
                            route.updateRoute(metricInt, newIpOut); // if the ip has a smaller metric it updates the
                                                                    // routing table
                        }
                        break;
                    }
                }

                // as the ip wasn't in the routing table it will be added to it
                if (!found) {
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


    private static void addIPToRoutingTable(String message, String senderIpAddress, DatagramSocket socket) {
        // case message starts with '@'

        // confirm if the message is real or not, the message send an IP (ex.:
        // @192.168.1.1) and the address has it's IP

            Route newRoute = new Route(senderIpAddress, 1, senderIpAddress);
            routingTable.add(newRoute);

            System.out.println("New route added: " + senderIpAddress);
            System.out.println(newRoute.getIpDestiny() + " " + newRoute.getMetric() + " " + newRoute.getIpOut());

    }


    private static void sendRoutingTable(DatagramSocket socket) throws IOException {
        // (String nickname, String message, InetAddress senderAddress, int senderPort,
        // DatagramSocket socket) throws IOException {
        // It will (re)create the message with the routing table to send
        synchronized (routingTableLock) {


            StringBuilder messageToSend = new StringBuilder();

            for (Route route : routingTable) {
                if (!route.getIpDestiny().equals(myIP)) {
                    messageToSend.append("!");
                    messageToSend.append(route.getIpDestiny());
                    messageToSend.append(":");
                    messageToSend.append(route.getMetric());
                    
                }
            }

            // It will send the message only to the neighboors routers
            for (Route route : routingTable) {
                if (route.getMetric() == 1) {
                    byte[] sendData = messageToSend.toString().getBytes();
                    InetAddress ipDestiny = InetAddress.getByName(route.getIpDestiny());
                    DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ipDestiny, DEST_PORT);
                    socket.send(sendPacket);
                    System.out.println(messageToSend.toString() + " para: " + ipDestiny + "=======================================");
                }

            }
        }
        // Test Mensage to send =======================================
        // System.out.println("Initial table: ");
        // System.out.println();
        // System.out.println("Mensage to send: ");
        // System.out.println(messageToSend);
        // System.out.println();
    }


    private static void recieveMessage(String message, DatagramSocket socket) throws IOException {
        // &192.168.1.2%192.168.1.1%Oi tudo bem?
        // The first address is the source IP, the second is the destination IP and then
        // the text message comes.

        String mensageRecieved = message.substring(1);
        String[] parts = mensageRecieved.split("%");

        String ipDestiny = parts[0];

        if (parts[1].equals(myIP)) {
            System.out.println(parts[1] + " sent: ");
            System.out.println(parts[2]);
            return;
        }

        boolean found = false;
        String ipToSendForward = "";

        for (Route route : routingTable) {  // iterate the existing routing table
            if (route.getIpDestiny().equals(ipDestiny)) {
                found = true;
                ipToSendForward = route.getIpOut();  // get the ip out to know to who it needs to repass the message

                byte[] sendData = message.getBytes();
                InetAddress IpOut = InetAddress.getByName(ipToSendForward);
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IpOut, DEST_PORT);
                socket.send(sendPacket);
                break;
            }
        }
        if (found) {
            System.out.println("Message recieved and forwarded: " + message);
            System.out.println("Message recieved: " +  message + " and forwarded to: " + ipDestiny + " by: " + ipToSendForward);
        } else {
            System.out.println("Route not found!");
        }
    }


    private static void removeInactiveRouters() {
        long currentTime = System.currentTimeMillis();
        synchronized (routingTableLock) {
            routingTable.removeIf(route -> {
                if ((currentTime - route.getLastSeen()) > 35000) {
                    return true;
                }
                return false;
            });
        }
        System.out.println("Inactive routers removed.");
    }


    private static void addRoutesTask(DatagramSocket socket) {
        // Add a new task to send the routing table for the new route
        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(() -> {
            try {
                sendRoutingTable(socket);
            } catch (IOException e) {
                System.out.println("Error sending routing table: " + e.getMessage());
            }
        }, 0, 15, TimeUnit.SECONDS);
        System.out.println("New task for added route.");
    }
}
