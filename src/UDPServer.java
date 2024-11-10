package src;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

import src.Route;
import src.UDPClient.MessageReceiver;

class UDPServer {
    private static final int DEST_PORT = 19000; // Porta do destinatário

    private static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private static ArrayList<Route> routingTable = new ArrayList<Route>();

    // Objetos de bloqueio para `routingTable` e `rotaTasks`
    private static final Object routingTableLock = new Object();

    public static void main(String args[]) throws Exception {
        // default port, as asked

        String file = "src\\roteadores.txt";

        // get local IP
        InetAddress localIp = InetAddress.getLocalHost();

        // convert IP address to string
        String myIP = localIp.getHostAddress();

        DatagramSocket serverSocket = new DatagramSocket(DEST_PORT);

        // Question the user if a new net will be created (YES case) or if there is a
        // net (case no) and need to connect
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
                addRoutesTask(serverSocket, myIP); // Adiciona as tarefa para a rota

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
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ipDestiny, DEST_PORT);
            serverSocket.send(sendPacket);

        }
        scanner.close();

        // // Schedule tasks
        scheduleRemoveInactiveRouters();

        // Thread para receber mensagens
        // new Thread(new MessageReceiver(scanner, serverSocket, localIp, port)).start();

        String senderIpAddress;

        // Thread para enviar mensagens
        Thread sendThread = new Thread(() -> {
            Scanner localScanner = new Scanner(System.in);
            while (true) {
                synchronized (routingTableLock) {
                    for (Route route : routingTable) {
                        System.out.println(route);
                    }
                }
                System.out.print("Digite sua mensagem:");
                String message = localScanner.nextLine();
                System.out.print("Digite o IP de destino: ");
                String routeDest = localScanner.nextLine();
                try {
                    sendMessage(message, serverSocket, myIP, routeDest);
                } catch (IOException e) {
                    System.out.println("Erro ao enviar mensagem: " + e.getMessage());
                }
            }
        });
        sendThread.start();

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
             * senderIpAddress is the IP address from who sent the message
             */
            handleCommunication(receivedMessage.trim(), senderIpAddress, serverSocket, myIP);
        }
    }

    private static void sendMessage(String message, DatagramSocket socket, String myIP, String routeDest) throws IOException {
        // &192.168.1.2%192.168.1.1%Oi tudo bem?
        String messageToSend = "&" + myIP + "%" + routeDest + "%" + message;
        byte[] sendData = messageToSend.getBytes();
        InetAddress ipDestiny = InetAddress.getByName(routeDest);

        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ipDestiny, DEST_PORT);
        try {
            socket.send(sendPacket);
            System.out.println("Mensagem enviada: " + message);
        } catch (IOException e) {
            System.out.println("Erro ao enviar mensagem: " + e.getMessage());
        }
    }

    private static void scheduleRemoveInactiveRouters() {
        scheduler.scheduleAtFixedRate(() -> removeInactiveRouters(), 0, 35, TimeUnit.SECONDS);
    }

    private static void handleCommunication(String message, String senderIpAddress, DatagramSocket socket, String myIP)
            throws IOException {

        switch (message.charAt(0)) {
            case '!':
                registerInRoutingTable(message, senderIpAddress, socket);
                break;
            case '@': // message to enter in a network (that already exist)
                addIPToRoutingTable(message, senderIpAddress, socket);
                break;
            case '&':
                recieveMessage(message, socket, myIP);
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
                String metric = parts[1];

                int metricInt = Integer.parseInt(metric) + 1;

                boolean found = false;

                String newIpOut = senderIpAddress;

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
        String ip = message.substring(1);

        // TODO: confirm if the message is real or not, the message send an IP (ex.:
        // @192.168.1.1) and the address has it's IP
        if (ip.equals(senderIpAddress)) {
            Route newRoute = new Route(ip, 1, senderIpAddress);
            routingTable.add(newRoute);

            System.out.println("New route added: " + message);
            System.out.println(newRoute.getIpDestiny() + " " + newRoute.getMetric() + " " + newRoute.getIpOut());

        } else {
            System.out.println("IP is different from ip source, addicion to routing table not accepted!");
        }

    }

    private static void sendRoutingTable(DatagramSocket socket, String myIP) throws IOException {
        // (String nickname, String message, InetAddress senderAddress, int senderPort,
        // DatagramSocket socket) throws IOException {
        // It will (re)create the message with the routing table to send
        synchronized (routingTableLock) {

            String messageToSend = "!" + myIP + "1";
            for (Route route : routingTable) {
                messageToSend += "!" + route.getIpDestiny() + ":" + route.getMetric();
            }

            // It will send the message only to the neighboors routers
            for (Route route : routingTable) {
                if (route.getMetric() == 1) {
                    byte[] sendData = messageToSend.getBytes();
                    InetAddress ipDestiny = InetAddress.getByName(route.getIpDestiny());
                    DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ipDestiny, DEST_PORT);
                    socket.send(sendPacket);
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

    private static void recieveMessage(String message, DatagramSocket socket, String myIP) throws IOException {
        // &192.168.1.2%192.168.1.1%Oi tudo bem?
        // O primeiro endereço é o IP da origem, o segundo é o IP de destino e a seguir
        // vem a mensagem de texto.

        String mensageRecieved = message.substring(1);
        String[] parts = mensageRecieved.split("%");

        String ipDestiny = parts[0];

        if (parts[1].equals(myIP)) {
            System.out.println(parts[2]);
            return;
        }

        boolean found = false;

        for (Route route : routingTable) { // iterate the existing routing table
            if (route.getIpDestiny().equals(ipDestiny)) {
                found = true;
                String sendTo = route.getIpOut(); // get the ip out to know to who it needs to repass the message

                byte[] sendData = message.getBytes();
                InetAddress IpOut = InetAddress.getByName(sendTo);
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IpOut, DEST_PORT);
                socket.send(sendPacket);
                break;
            }
        }
        if (found) {
            System.out.println("Message recieved and sent: " + message);
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


    private static void addRoutesTask(DatagramSocket socket, String myIP) {
        // Adiciona uma nova tarefa para enviar a tabela de roteamento para a nova rota
        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(() -> {
            try {
                sendRoutingTable(socket, myIP);
            } catch (IOException e) {
                System.out.println("Error sending routing table: " + e.getMessage());
            }
        }, 0, 15, TimeUnit.SECONDS);
        System.out.println("Nova tarefa para a rota adicionada.");
    }



    

}
