package UDP;
import java.io.*;
import java.net.*;
import java.util.*;

class UDPServer {
    private static Map<String, InetAddress> clients = new HashMap<>();
    private static Map<String, Integer> clientPorts = new HashMap<>();

    public static void main(String args[]) throws Exception {
        // if (args.length < 1) {
        //     System.out.println("Uso: java UDPServer <port>");
        //     return;
        // }

        if (args.length < 1) {  // default port as asked
            int port = 19000;
        } else {
            int port = Integer.parseInt(args[0]);
        }

        try {
            File roteadores = new File("roteadores.txt");
            Scanner myReader = new Scanner(roteadores);
            while (myReader.hasNextLine()) {
              String data = myReader.nextLine();

                // TODO adiciona os IPs na tabela de IPs
                // os endereços IP informados deverão ser cadastrados em uma tabela de roteamento com métrica 1 e saída com o
                // IP do roteador vizinho. 
                // tabela de roteamento: IP de Destino, Métrica e IP de Saída
            }
            myReader.close();
        } catch (FileNotFoundException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
        }

        DatagramSocket serverSocket = new DatagramSocket(port);

        while (true) {
            byte[] receiveData = new byte[1024];
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            serverSocket.receive(receivePacket);

            String receivedMessage = new String(receivePacket.getData(), 0, receivePacket.getLength());
            InetAddress clientAddress = receivePacket.getAddress();
            int clientPort = receivePacket.getPort();

            handleCommand(receivedMessage, clientAddress, clientPort, serverSocket);
        }
    }

    private static void handleCommand(String command, InetAddress address, int port, DatagramSocket socket) throws IOException {
        String[] parts = command.split(" ", 4);
        String action = parts[0];

        switch (action.toUpperCase()) {
            case "/FILE":
                String targetNickname = parts[1];
                String fileName = parts[2];
                long fileSize = Long.parseLong(parts[3]);

                if (clients.containsKey(targetNickname)) {
                    InetAddress receiverAddress = clients.get(targetNickname);
                    int receiverPort = clientPorts.get(targetNickname);

                    // Informar o cliente destino sobre o arquivo que será recebido
                    String fileInfo = "/FILEINFO " + targetNickname + " " + fileName + " " + fileSize;
                    byte[] fileInfoData = fileInfo.getBytes();
                    DatagramPacket fileInfoPacket = new DatagramPacket(fileInfoData, fileInfoData.length, receiverAddress, receiverPort);
                    socket.send(fileInfoPacket);

                    // Agora o servidor vai receber e repassar os blocos de dados binários
                    transferFile(socket, address, port, receiverAddress, receiverPort);
                } else {
                    System.out.println("Usuário não encontrado: " + targetNickname);
                }
                break;
            case "/REG":
                registerUser(parts[1], address, port);
                break;
            case "/MSG":
                sendMessage(parts[1], parts[2], address, port, socket);
                break;
            case "/QUIT":
                unregisterUser(address, port);
                break;
            default:
                System.out.println("Comando desconhecido.");
        }
    }

    // Função para repassar os dados binários do arquivo do cliente origem para o cliente destino
    private static void transferFile(DatagramSocket socket, InetAddress senderAddress, int senderPort, InetAddress receiverAddress, int receiverPort) throws IOException {
        byte[] buffer = new byte[1024];
        DatagramPacket filePacket;

        while (true) {
            filePacket = new DatagramPacket(buffer, buffer.length);
            socket.receive(filePacket);  // Recebe o bloco do cliente origem

            if (!filePacket.getAddress().equals(senderAddress) || filePacket.getPort() != senderPort) {
                continue;
            }

            DatagramPacket forwardPacket = new DatagramPacket(filePacket.getData(), filePacket.getLength(), receiverAddress, receiverPort);
            socket.send(forwardPacket);

            if (filePacket.getLength() < 1024) {
                break;
            }
        }

        System.out.println("Transferência de arquivo concluída.");
    }

    private static void registerUser(String nickname, InetAddress address, int port) {
        clients.put(nickname, address);
        clientPorts.put(nickname, port);
        System.out.println("Usuário registrado: " + nickname);
    }

    private static void sendMessage(String nickname, String message, InetAddress senderAddress, int senderPort, DatagramSocket socket) throws IOException {
        if (clients.containsKey(nickname)) {
            InetAddress receiverAddress = clients.get(nickname);
            int receiverPort = clientPorts.get(nickname);
            String fullMessage = "Mensagem de " + senderAddress.getHostAddress() + ": " + message;
            byte[] sendData = fullMessage.getBytes();
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, receiverAddress, receiverPort);
            socket.send(sendPacket);
        } else {
            System.out.println("Usuário não encontrado: " + nickname);
        }
    }

    private static void unregisterUser(InetAddress address, int port) {
        // Implementar lógica para remover usuário registrado
    }
}
