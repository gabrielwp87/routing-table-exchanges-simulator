import java.io.*;
import java.net.*;
import java.util.concurrent.*;

class UDPServer {
    public static final int DEST_PORT = 19000;
    public static final int TIME_SEND = 15000; // Intervalo de envio da tabela
    public static final int TIME_REMOVE = 35000; // Tempo para remover rotas inativas
    public static ConcurrentHashMap<String, Route> routingTable = new ConcurrentHashMap<>();
    public static String myIP;
    private static String configFile = "src\\configs\\roteadores.txt";

    public static void main(String[] args) throws Exception {
        if (System.getProperty("os.name").equals("Linux")) configFile = "src/configs/roteadores.txt";

        // get local IP
        InetAddress localIp = InetAddress.getLocalHost();

        // convert IP address to string
        myIP = localIp.getHostAddress();
//            myIP = "10.32.162.223";

        if (args.length == 2) {
            myIP = args[0];
            String configFile = args[1];
            File file = new File(configFile);

            if (!file.exists()) {
                System.err.println("Erro: Arquivo de configuração não foi encontrado.");
                return;
            }
        }

        DatagramSocket serverSocket = new DatagramSocket(DEST_PORT, InetAddress.getByName(myIP));
        System.out.println("Servidor iniciado no IP " + myIP + " e porta " + DEST_PORT);

        BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("Criar nova rede? (responda 'sim' ou 'não')");
        String answer = consoleReader.readLine().trim().toLowerCase();

        RoutingTable.initializeRoutingTable(configFile);
        if (answer.equals("sim") || answer.equals("s") || answer.equals("yes") || answer.equals("y")) {
            RoutingTable.printRoutingTable();
        } else {
            MessageBroker.sendInitialMessage(serverSocket, myIP);
        }

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);

        // Agendamento de tarefas
        scheduler.scheduleAtFixedRate(() -> MessageBroker.sendRoutingTable(serverSocket), 0, TIME_SEND, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(RoutingTable::removeInactiveRoutes, 0, TIME_REMOVE, TimeUnit.MILLISECONDS);

        // Thread para receber mensagens
        new Thread(() -> MessageBroker.receiveMessages(serverSocket)).start();

        // Thread para enviar mensagens do console
        new Thread(() -> handleConsoleInput(serverSocket)).start();
    }

    private static void handleConsoleInput(DatagramSocket socket) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            while (true) {
                System.out.print("Digite sua mensagem: ");
                String message = reader.readLine();
                System.out.print("Digite o IP de destino: ");
                String destinationIP = reader.readLine();
                MessageBroker.sendMessage(message, socket, destinationIP);
            }
        } catch (IOException e) {
            System.out.println("Erro ao enviar mensagem: " + e.getMessage());
        }
    }

    public static void handleCommunication(String message, String senderIP, DatagramSocket socket) throws IOException {
        System.out.println("Mensagem recebida de " + senderIP + ": " + message);
        if (message.startsWith("!")) {
            RoutingTable.registerInRoutingTable(message, senderIP, socket);
        } else if (message.startsWith("@")) {
            RoutingTable.addIPToRoutingTable(senderIP);
        } else if (message.startsWith("&")) {
            MessageBroker.receiveMessage(message, socket);
        } else {
            System.out.println("Mensagem desconhecida: " + message);
        }
    }

}