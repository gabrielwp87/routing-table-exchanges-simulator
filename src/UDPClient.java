package src;

import java.io.*;
import java.net.*;
import java.util.Scanner;

public class UDPClient {

    private static DatagramSocket clientSocket;
    private static InetAddress serverAddress;
    private static int serverPort;

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: java UDPClient <server_ip> <port>");
            return;
        }

        serverAddress = InetAddress.getByName(args[0]);
        serverPort = Integer.parseInt(args[1]);
        clientSocket = new DatagramSocket();

        // Thread para receber mensagens
        new Thread(new MessageReceiver()).start();

        Scanner scanner = new Scanner(System.in);
        System.out.print("Comandos: /REG /MSG /FILE\n");
        System.out.print("Registre-se: ");
        String nickname = scanner.nextLine();
        registerNickname(nickname);

        String message;
        while (true) {
            message = scanner.nextLine();

            if (message.startsWith("/FILE")) {
                String[] tokens = message.split(" ", 3);
                if (tokens.length == 3) {
                    String targetNickname = tokens[1];
                    String filePath = tokens[2];
                    sendFile(targetNickname, filePath);
                } else {
                    System.out.println("Uso correto: /FILE <nickname> <caminho_do_arquivo>");
                }
            } else {
                sendMessage(message);
            }

            if (message.equalsIgnoreCase("FIM")) {
                break;
            }
        }

        clientSocket.close();
    }

    // Registra o nickname no servidor
    private static void registerNickname(String nickname) throws IOException {
        // String command = "/REG " + nickname;
        byte[] sendData = nickname.getBytes();
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, serverPort);
        clientSocket.send(sendPacket);
    }

    // Envia uma mensagem simples
    private static void sendMessage(String message) throws IOException {
        byte[] sendData = message.getBytes();
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, serverPort);
        clientSocket.send(sendPacket);
    }

    // Método para enviar o arquivo em partes
    private static void sendFile(String targetNickname, String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists() || file.isDirectory()) {
            System.out.println("Arquivo não encontrado ou é um diretório.");
            return;
        }

        // Enviar informações do arquivo
        String fileInfo = "/FILE " + targetNickname + " " + file.getName() + " " + file.length();
        byte[] fileInfoData = fileInfo.getBytes();
        DatagramPacket fileInfoPacket = new DatagramPacket(fileInfoData, fileInfoData.length, serverAddress, serverPort);
        clientSocket.send(fileInfoPacket);

        // Enviar os dados do arquivo em blocos de 1024 bytes
        FileInputStream fis = new FileInputStream(file);
        byte[] buffer = new byte[1024];
        int bytesRead;

        while ((bytesRead = fis.read(buffer)) != -1) {
            DatagramPacket fileDataPacket = new DatagramPacket(buffer, bytesRead, serverAddress, serverPort);
            clientSocket.send(fileDataPacket);
        }

        fis.close();
        System.out.println("Arquivo " + file.getName() + " enviado para " + targetNickname);
    }

    // Classe para receber mensagens e arquivos
    static class MessageReceiver implements Runnable {
        public void run() {
            try {
                byte[] receiveData = new byte[1024];
                while (true) {
                    DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                    clientSocket.receive(receivePacket);
                    String receivedMessage = new String(receivePacket.getData(), 0, receivePacket.getLength());

                    if (receivedMessage.startsWith("/FILEINFO")) {
                        String[] fileInfo = receivedMessage.split(" ");
                        String fileName = fileInfo[2];
                        long fileSize = Long.parseLong(fileInfo[3]);

                        System.out.println("Recebendo arquivo: " + fileName + " (" + fileSize + " bytes)");
                        receiveFile(fileName, fileSize);
                    } else {
                        System.out.println("Mensagem recebida: " + receivedMessage);
                    }
                }
            } catch (IOException e) {
                System.out.println("Erro ao receber mensagens: " + e.getMessage());
            }
        }

        // Método para receber o arquivo
        private void receiveFile(String fileName, long fileSize) throws IOException {
            File file = new File("recebido_" + fileName);
            FileOutputStream fos = new FileOutputStream(file);

            long totalBytesReceived = 0;
            byte[] buffer = new byte[1024];

            while (totalBytesReceived < fileSize) {
                DatagramPacket filePacket = new DatagramPacket(buffer, buffer.length);
                clientSocket.receive(filePacket);
                fos.write(buffer, 0, filePacket.getLength());
                totalBytesReceived += filePacket.getLength();
            }

            fos.close();
            System.out.println("Arquivo " + file.getName() + " recebido com sucesso.");
        }
    }
}
