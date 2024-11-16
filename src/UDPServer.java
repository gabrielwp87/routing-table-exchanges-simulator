import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class UDPServer {
    private static final int DEST_PORT = 19000; // Porta padrão
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private static final List<Route> routingTable = new ArrayList<>();
    private static final Object routingTableLock = new Object();
    private static String myIP;

    public static void main(String[] args) throws Exception {

        if (args.length < 3) {
            System.err.println("Uso: java UDPServer <IP_LOCAL> <ARQUIVO_CONFIG> <PORTA>");
            return;
        }

        myIP = args[0]; // Primeiro argumento: IP local
        String file = args[1]; // Segundo argumento: Arquivo de configuração
        int port = Integer.parseInt(args[2]); // Terceiro argumento: Porta

        File configFile = new File(file);
        if (!configFile.exists()) {
            System.err.println("Erro: Arquivo de configuração não encontrado: " + file);
            return;
        }

        DatagramSocket serverSocket = new DatagramSocket(DEST_PORT, InetAddress.getByName(myIP));
        System.out.println("Servidor iniciado no IP " + myIP + " e porta " + port);

        BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));

        System.out.println("Criar uma nova rede? (responda 'sim' ou 'não')");
        String answer = consoleReader.readLine().trim().toLowerCase();

        if (answer.equals("sim") || answer.equals("s") || answer.equals("yes") || answer.equals("y")) {
            // Carregar roteadores vizinhos do arquivo
            loadInitialRoutingTable(file);

            // Iniciar envio periódico de tabelas de roteamento
            scheduleRoutingTableUpdates(serverSocket);

            // Exibir tabela inicial
            System.out.println("Tabela de roteamento inicial:");
            synchronized (routingTableLock) {
                routingTable.forEach(System.out::println);
            }
        } else {
            // Anunciar a um roteador específico
            System.out.println("Digite o IP de destino:");
            String targetIp = consoleReader.readLine().trim();
            String messageToSend = "@" + targetIp;
            byte[] sendData = messageToSend.getBytes();
            InetAddress ipDestiny = InetAddress.getByName(targetIp);
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ipDestiny, DEST_PORT);
            serverSocket.send(sendPacket);
        }

        // Thread para recepção de mensagens
        startReceiverThread(serverSocket);

        // Thread para entrada de mensagens manuais
        startInputThread(serverSocket);

        // Remoção periódica de roteadores inativos
        scheduleRemoveInactiveRouters();
    }

    private static void loadInitialRoutingTable(String file) throws IOException {
        try (BufferedReader fileReader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = fileReader.readLine()) != null) {
                synchronized (routingTableLock) {
                    routingTable.add(new Route(line.trim(), 1, line.trim()));
                }
            }
        } catch (FileNotFoundException e) {
            System.err.println("Erro: Arquivo de configuração não encontrado.");
            e.printStackTrace();
        }
    }

    private static void scheduleRoutingTableUpdates(DatagramSocket socket) {
        scheduler.scheduleAtFixedRate(() -> {
            synchronized (routingTableLock) {
                if (routingTable.isEmpty()) {
                    System.out.println("[Aviso] Tabela de roteamento vazia, nada para enviar.");
                    return;
                }
    
                StringBuilder messageToSend = new StringBuilder();
    
                for (Route route : routingTable) {
                    if (!route.getIpDestiny().equals(myIP)) {
                        messageToSend.append("!").append(route.getIpDestiny()).append(":").append(route.getMetric());
                    }
                }
    
                String message = messageToSend.toString();
                for (Route route : routingTable) {
                    if (route.getMetric() == 1) { // Apenas enviar para vizinhos diretos
                        try {
                            byte[] sendData = message.getBytes();
                            InetAddress ipDestiny = InetAddress.getByName(route.getIpDestiny());
                            DatagramPacket packet = new DatagramPacket(sendData, sendData.length, ipDestiny, DEST_PORT);
                            socket.send(packet);
                            System.out.println("[Tabela Enviada] Para: " + ipDestiny);
                        } catch (IOException e) {
                            System.err.println("Erro ao enviar tabela de roteamento: " + e.getMessage());
                        }
                    }
                }
            }
        }, 0, 15, TimeUnit.SECONDS); // Executa a cada 15 segundos
    }
    
    private static void startReceiverThread(DatagramSocket socket) {
        new Thread(() -> {
            byte[] receiveData = new byte[1024];
            while (true) {
                try {
                    DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                    socket.receive(receivePacket);

                    String message = new String(receivePacket.getData(), 0, receivePacket.getLength());
                    String senderIp = receivePacket.getAddress().getHostAddress();

                    handleCommunication(message.trim(), senderIp, socket);
                } catch (IOException e) {
                    System.err.println("Erro ao receber pacote: " + e.getMessage());
                }
            }
        }).start();
    }

    private static void startInputThread(DatagramSocket socket) {
        new Thread(() -> {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            while (true) {
                try {
                    System.out.print("Digite o IP de destino: ");
                    String destIp = reader.readLine();
                    System.out.print("Digite a mensagem: ");
                    String text = reader.readLine();
                    sendMessage(text, socket, destIp);
                } catch (IOException e) {
                    System.err.println("Erro ao enviar mensagem: " + e.getMessage());
                }
            }
        }).start();
    }

    private static void sendMessage(String message, DatagramSocket socket, String destIp) throws IOException {
        String fullMessage = "&" + myIP + "%" + destIp + "%" + message;
        byte[] sendData = fullMessage.getBytes();

        InetAddress ipDestiny = InetAddress.getByName(destIp);
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ipDestiny, DEST_PORT);
        socket.send(sendPacket);
    }

    private static void handleCommunication(String message, String senderIp, DatagramSocket socket) throws IOException {
        switch (message.charAt(0)) {
            case '@':
                addIPToRoutingTable(senderIp);
                break;
            case '!':
                registerInRoutingTable(message, senderIp);
                break;
            case '&':
                routeTextMessage(message, socket);
                break;
            default:
                System.err.println("Mensagem desconhecida: " + message);
        }
    }

    private static void scheduleRemoveInactiveRouters() {
        scheduler.scheduleAtFixedRate(() -> {
            long currentTime = System.currentTimeMillis();
            synchronized (routingTableLock) {
                Iterator<Route> iterator = routingTable.iterator();
                while (iterator.hasNext()) {
                    Route route = iterator.next();
                    if ((currentTime - route.getLastSeen()) > 35000) { // Mais de 35 segundos inativo
                        System.out.println("[Removendo] Rota inativa: " + route);
                        iterator.remove();
                    }
                }
            }
        }, 0, 35, TimeUnit.SECONDS); // Executa a cada 35 segundos
    }
    

    private static void registerInRoutingTable(String message, String senderIp) {
        String[] routes = message.split("!");
        for (String route : routes) {
            if (!route.isEmpty()) {
                String[] parts = route.split(":");
                String destIp = parts[0];
                int metric = Integer.parseInt(parts[1]) + 1;
                if (destIp.equals(myIP)) {
                    continue;
                }
                synchronized (routingTableLock) {
                    Optional<Route> existingRoute = routingTable.stream()
                            .filter(r -> r.getIpDestiny().equals(destIp))
                            .findFirst();

                    if (existingRoute.isPresent()) {
                        Route routeToUpdate = existingRoute.get();
                        if (metric < routeToUpdate.getMetric()) {
                            routeToUpdate.updateRoute(metric, senderIp);
                        }
                        routeToUpdate.updateLastSeen(); // Atualiza o `lastSeen`
                    } else {
                        routingTable.add(new Route(destIp, metric, senderIp));
                    }
                }
            }
        }
    }

    private static void addIPToRoutingTable(String senderIp) {
        synchronized (routingTableLock) {
            Optional<Route> existingRoute = routingTable.stream()
                    .filter(r -> r.getIpDestiny().equals(senderIp))
                    .findFirst();

            if (existingRoute.isPresent()) {
                Route routeToUpdate = existingRoute.get();
                routeToUpdate.updateRoute(1, senderIp); // Atualiza a métrica e o IP de saída
                routeToUpdate.updateLastSeen(); // Atualiza o `lastSeen`
            } else {
                routingTable.add(new Route(senderIp, 1, senderIp));
            }

            System.out.println("[Atualização] Novo roteador adicionado: " + senderIp);
        }
    }

    private static void routeTextMessage(String message, DatagramSocket socket) throws IOException {
        String[] parts = message.split("%");
        String sourceIp = parts[0];
        String destIp = parts[1];
        String text = parts[2];

        if (destIp.equals(myIP)) {
            System.out.println("[Mensagem recebida] Origem: " + sourceIp + ", Mensagem: '" + text + "'");
            return;
        }

        synchronized (routingTableLock) {
            routingTable.stream()
                    .filter(route -> route.getIpDestiny().equals(destIp))
                    .findFirst()
                    .ifPresentOrElse(route -> {
                        try {
                            sendMessage(message, socket, route.getIpOut());
                            System.out.println("[Encaminhamento] Mensagem enviada para " + route.getIpOut());
                        } catch (IOException e) {
                            System.err.println("Erro ao encaminhar mensagem: " + e.getMessage());
                        }
                    }, () -> System.out.println("[Erro] Rota não encontrada para " + destIp));
        }
    }

    private static void addRoutesTask(DatagramSocket socket) {
        scheduler.scheduleAtFixedRate(() -> {
            synchronized (routingTableLock) {
                StringBuilder messageToSend = new StringBuilder();

                System.out.println("[Envio de Tabela] Rotas incluídas na mensagem:");
                for (Route route : routingTable) {
                    if (!route.getIpDestiny().equals(myIP)) {
                        messageToSend.append("!");
                        messageToSend.append(route.getIpDestiny());
                        messageToSend.append(":");
                        messageToSend.append(route.getMetric());
                        System.out.println("  - " + route); // Log da rota
                    }
                }

                // Envia a mensagem para os roteadores vizinhos
                for (Route route : routingTable) {
                    if (route.getMetric() == 1) {
                        try {
                            byte[] sendData = messageToSend.toString().getBytes();
                            InetAddress ipDestiny = InetAddress.getByName(route.getIpDestiny());
                            DatagramPacket packet = new DatagramPacket(sendData, sendData.length, ipDestiny, DEST_PORT);
                            socket.send(packet);
                            System.out.println("[Tabela Enviada] Para: " + ipDestiny);
                        } catch (IOException e) {
                            System.err.println("Erro ao enviar tabela de roteamento: " + e.getMessage());
                        }
                    }
                }
            }
        }, 0, 15, TimeUnit.SECONDS);
    }

}
