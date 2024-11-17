import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

class UDPServer {
    private static final int DEST_PORT = 19000;
    private static final int TIME_SEND = 15000; // Intervalo de envio da tabela
    private static final int TIME_REMOVE = 35000; // Tempo para remover rotas inativas
    private static final ConcurrentHashMap<String, Route> routingTable = new ConcurrentHashMap<>();
    private static final ReentrantLock routingTableLock = new ReentrantLock();
    private static String myIP;

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Uso: java UDPServer <IP_LOCAL> <ARQUIVO_CONFIG>");
            return;
        }

        myIP = args[0];
        String configFile = args[1];

        File file = new File(configFile);
        if (!file.exists()) {
            System.err.println("Erro: Arquivo de configuração não encontrado.");
            return;
        }

        DatagramSocket serverSocket = new DatagramSocket(DEST_PORT, InetAddress.getByName(myIP));
        System.out.println("Servidor iniciado no IP " + myIP + " e porta " + DEST_PORT);

        BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("Criar nova rede? (responda 'sim' ou 'não')");
        String answer = consoleReader.readLine().trim().toLowerCase();

        initializeRoutingTable(configFile);
        if (answer.startsWith("s")) {
            printRoutingTable();
        } else {
            sendInitialMessage(serverSocket);
        }

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);

        // Agendamento de tarefas periódicas
        scheduler.scheduleAtFixedRate(() -> sendRoutingTable(serverSocket), 0, TIME_SEND, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(UDPServer::removeInactiveRoutes, 0, TIME_REMOVE, TimeUnit.MILLISECONDS);

        // Thread para receber mensagens
        new Thread(() -> receiveMessages(serverSocket)).start();

        // Thread para enviar mensagens do console
        new Thread(() -> handleConsoleInput(serverSocket)).start();
    }

    private static void initializeRoutingTable(String file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                routingTable.put(line, new Route(line, 1, line));
            }
        } catch (IOException e) {
            System.out.println("Erro ao ler o arquivo: " + e.getMessage());
        }
    }

    private static void printRoutingTable() {
        routingTableLock.lock();
        try {
            System.out.println("Tabela de roteamento:");
            routingTable.values().forEach(System.out::println);
        } finally {
            routingTableLock.unlock();
        }
    }

    private static void sendInitialMessage(DatagramSocket socket) throws IOException {
        String message = "@" + myIP;

        byte[] sendData = message.getBytes();

        routingTable.values().stream()
        .filter(route -> route.getMetric() == 1)
        .forEach(route -> {
            try {
                InetAddress ipDestiny = InetAddress.getByName(route.getIpDestiny());
                DatagramPacket packet = new DatagramPacket(sendData, sendData.length, ipDestiny, DEST_PORT);
                socket.send(packet);
                System.out.println("@ enviado: " + route.getIpDestiny());
            } catch (IOException e) {
                System.out.println("Erro ao enviar tabela: " + e.getMessage());
            }
        });

    }

    private static void receiveMessages(DatagramSocket socket) {
        byte[] buffer = new byte[1024];
        while (true) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength());
                String senderIP = packet.getAddress().getHostAddress();
                handleCommunication(message, senderIP, socket);
            } catch (IOException e) {
                System.out.println("Erro ao receber pacote: " + e.getMessage());
            }
        }
    }

    private static void sendRoutingTable(DatagramSocket socket) {
        StringBuilder messageBuilder = new StringBuilder();
        routingTableLock.lock();
        try {
            for (Route route : routingTable.values()) {
                if (!route.getIpDestiny().equals(myIP)) {
                    messageBuilder.append("!").append(route.getIpDestiny()).append(":").append(route.getMetric());
                }
            }
        } finally {
            routingTableLock.unlock();
        }

        String message = messageBuilder.toString();
        byte[] sendData = message.getBytes();

        routingTable.values().stream()
                .filter(route -> route.getMetric() == 1)
                .forEach(route -> {
                    try {
                        InetAddress ipDestiny = InetAddress.getByName(route.getIpDestiny());
                        DatagramPacket packet = new DatagramPacket(sendData, sendData.length, ipDestiny, DEST_PORT);
                        socket.send(packet);
                        System.out.println("Tabela enviada para: " + route.getIpDestiny());
                    } catch (IOException e) {
                        System.out.println("Erro ao enviar tabela: " + e.getMessage());
                    }
                });
    }

    private static void removeInactiveRoutes() {
        long currentTime = System.currentTimeMillis();
        routingTableLock.lock();
        try {
            routingTable.entrySet().removeIf(entry -> (currentTime - entry.getValue().getLastSeen()) > TIME_REMOVE);
            System.out.println("Rotas inativas removidas!");
        } finally {
            routingTableLock.unlock();
        }
    }

    private static void handleConsoleInput(DatagramSocket socket) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            while (true) {
                System.out.print("Digite sua mensagem: ");
                String message = reader.readLine();
                System.out.print("Digite o IP de destino: ");
                String destinationIP = reader.readLine();
                sendMessage(message, socket, destinationIP);
            }
        } catch (IOException e) {
            System.out.println("Erro ao enviar mensagem: " + e.getMessage());
        }
    }

    private static void handleCommunication(String message, String senderIP, DatagramSocket socket) throws IOException {
        System.out.println("Mensagem recebida de " + senderIP + ": " + message);
        if (message.startsWith("!")) {
            registerInRoutingTable(message, senderIP);
        } else if (message.startsWith("@")) {
            addIPToRoutingTable(senderIP);
        } else if (message.startsWith("&")) {
            receiveMessage(message, socket);
        } else {
            System.out.println("Mensagem desconhecida: " + message);
        }
    }

    private static void registerInRoutingTable(String message, String senderIP) {
        String[] entries = message.split("!");
        routingTableLock.lock();
        routingTable.get(senderIP).updateTimestamp();
        routingTableLock.unlock();

        for (String entry : entries) {
            if (entry.isEmpty())
                continue;

            String[] parts = entry.split(":");
            String ip = parts[0];
            int metric = Integer.parseInt(parts[1]) + 1;

            routingTableLock.lock();
            try {
                routingTable.compute(ip, (key, route) -> {
                    if (route == null || metric < route.getMetric()) {
                        return new Route(ip, metric, senderIP);
                    } else if (1 == route.getMetric()) {
                        return route;
                    }
                    route.updateTimestamp();
                    return route;
                });
            } finally {
                routingTableLock.unlock();
            }
        }
    }

    private static void addIPToRoutingTable(String senderIP) {
        routingTable.put(senderIP, new Route(senderIP, 1, senderIP));
    }

    private static void receiveMessage(String message, DatagramSocket socket) throws IOException {
        String[] parts = message.substring(1).split("%");
        String ipSource = parts[0];
        String ipDest = parts[1];
        String text = parts[2];

        if (ipDest.equals(myIP)) {
            System.out.println("Mensagem recebida de " + ipSource + ": " + text);
        } else {
            Route route = routingTable.get(ipDest);
            if (route != null) {
                byte[] sendData = message.getBytes();
                InetAddress ipOut = InetAddress.getByName(route.getIpOut());
                DatagramPacket packet = new DatagramPacket(sendData, sendData.length, ipOut, DEST_PORT);
                socket.send(packet);
                System.out.println("Mensagem encaminhada para " + route.getIpOut());
            } else {
                System.out.println("Rota para " + ipDest + " não encontrada!");
            }
        }
    }

    private static void sendMessage(String message, DatagramSocket socket, String routeDest) throws IOException {
        String formattedMessage = "&" + myIP + "%" + routeDest + "%" + message;
        byte[] sendData = formattedMessage.getBytes();
        InetAddress ipDestiny = InetAddress.getByName(routeDest);
        DatagramPacket packet = new DatagramPacket(sendData, sendData.length, ipDestiny, DEST_PORT);
        socket.send(packet);
    }
}