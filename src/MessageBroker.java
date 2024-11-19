import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.locks.ReentrantLock;

public class MessageBroker {
    private static final ReentrantLock routingTableLock = new ReentrantLock();

    public static void sendInitialMessage(DatagramSocket socket, String myIP) throws IOException {
        String message = "@" + myIP;

        byte[] sendData = message.getBytes();

        UDPServer.routingTable.values().stream()
                .filter(route -> route.getMetric() == 1)
                .forEach(route -> {
                    try {
                        InetAddress ipDestiny = InetAddress.getByName(route.getIpDestiny());
                        DatagramPacket packet = new DatagramPacket(sendData, sendData.length, ipDestiny, UDPServer.DEST_PORT);
                        socket.send(packet);
                        System.out.println("@ enviado: " + route.getIpDestiny());
                    } catch (IOException e) {
                        System.out.println("Erro ao enviar tabela: " + e.getMessage());
                    }
                });
    }

    public static void receiveMessages(DatagramSocket socket) {
        byte[] buffer = new byte[1024];
        while (true) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength());
                String senderIP = packet.getAddress().getHostAddress();
                UDPServer.handleCommunication(message, senderIP, socket);
            } catch (IOException e) {
                System.out.println("Erro ao receber pacote: " + e.getMessage());
            }
        }
    }

    public static void sendRoutingTable(DatagramSocket socket) {
        StringBuilder messageBuilder = new StringBuilder();
        routingTableLock.lock();
        try {
            for (Route route : UDPServer.routingTable.values()) {
                if (!route.getIpDestiny().equals(UDPServer.myIP)) {
                    messageBuilder.append("!").append(route.getIpDestiny()).append(":").append(route.getMetric());
                }
            }
        } finally {
            routingTableLock.unlock();
        }

        String message = messageBuilder.toString();
        byte[] sendData = message.getBytes();

        for (Route route : UDPServer.routingTable.values()) {
            if (route.getMetric() == 1) {
                try {
                    InetAddress ipDestiny = InetAddress.getByName(route.getIpDestiny());
                    DatagramPacket packet = new DatagramPacket(sendData, sendData.length, ipDestiny, UDPServer.DEST_PORT);
                    socket.send(packet);
                    System.out.println("Tabela enviada para: " + route.getIpDestiny());
                } catch (IOException e) {
                    System.out.println("Erro ao enviar tabela: " + e.getMessage());
                }
            }
        }
    }

    public static void receiveMessage(String message, DatagramSocket socket) throws IOException {
        String[] parts = message.substring(1).split("%");
        String ipSource = parts[0];
        String ipDest = parts[1];
        String text = parts[2];

        if (ipDest.equals(UDPServer.myIP)) {
            System.out.println("Mensagem recebida de " + ipSource + " : " + text);
        } else {
            Route route = UDPServer.routingTable.get(ipDest);
            if (route != null) {
                byte[] sendData = message.getBytes();
                InetAddress ipOut = InetAddress.getByName(route.getIpOut());
                DatagramPacket packet = new DatagramPacket(sendData, sendData.length, ipOut, UDPServer.DEST_PORT);
                socket.send(packet);
                System.out.println("Mensagem encaminhada para " + route.getIpOut());
            } else {
                System.out.println("Rota para " + ipDest + " não encontrada!");
            }
        }
    }

    public static void sendMessage(String message, DatagramSocket socket, String IpDestiny) throws IOException {
        String formattedMessage = "&" + UDPServer.myIP + "%" + IpDestiny + "%" + message;
        byte[] sendData = formattedMessage.getBytes();

        Route route = UDPServer.routingTable.get(IpDestiny);
        String ipOut = route.getIpOut();

        InetAddress ip = InetAddress.getByName(ipOut);
        DatagramPacket packet = new DatagramPacket(sendData, sendData.length, ip, UDPServer.DEST_PORT);
        socket.send(packet);
    }
}
