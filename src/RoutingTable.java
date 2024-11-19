import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.DatagramSocket;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class RoutingTable {
    private static final ReentrantLock routingTableLock = new ReentrantLock();

    public static void initializeRoutingTable(String file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                UDPServer.routingTable.put(line, new Route(line, 1, line));
            }
        } catch (IOException e) {
            System.out.println("Erro ao ler o arquivo: " + e.getMessage());
        }
    }

    public static void printRoutingTable() {
        routingTableLock.lock();
        try {
            System.out.println("Tabela de roteamento: ");
            UDPServer.routingTable.values().forEach(System.out::println);
        } finally {
            routingTableLock.unlock();
        }
    }

    public static void removeInactiveRoutes() {
        long currentTime = System.currentTimeMillis();

        routingTableLock.lock();
        try {

            Set<String> inactiveIPs = UDPServer.routingTable.values().stream()
                    .filter(route -> (currentTime - route.getLastSeen()) > UDPServer.TIME_REMOVE)
                    .map(Route::getIpDestiny)
                    .collect(Collectors.toSet());

            System.out.println("IP's inativos: " + inactiveIPs);

            UDPServer.routingTable.entrySet().removeIf(entry -> inactiveIPs.contains(entry.getValue().getIpOut()));

            UDPServer.routingTable.entrySet().removeIf(entry -> (currentTime - entry.getValue().getLastSeen()) > UDPServer.TIME_REMOVE);

            printRoutingTable();
        } finally {
            routingTableLock.unlock();
        }
    }

    public static void registerInRoutingTable(String message, String senderIP, DatagramSocket socket) {
        String[] entries = message.split("!");
        boolean updated = false;

        routingTableLock.lock();
        UDPServer.routingTable.get(senderIP).updateTimestamp();
        routingTableLock.unlock();

        for (String entry : entries) {
            if (entry.isEmpty())
                continue;

            String[] parts = entry.split(":");
            String ip = parts[0];
            int metric = Integer.parseInt(parts[1]) + 1;

            if(ip.equals(UDPServer.myIP))
                continue;

            routingTableLock.lock();
            try {
                Route route = UDPServer.routingTable.get(ip);
                if (route == null || metric < route.getMetric()) {
                    updated = true;
                    UDPServer.routingTable.put(ip, new Route(ip, metric, senderIP));
                } else if (1 != route.getMetric()) {
                    route.updateTimestamp();
                }
            } finally {
                routingTableLock.unlock();
            }
        }

        if (updated) {
            MessageBroker.sendRoutingTable(socket);
        }
    }

    public static void addIPToRoutingTable(String senderIP) {
        routingTableLock.lock();
        UDPServer.routingTable.put(senderIP, new Route(senderIP, 1, senderIP));
        routingTableLock.unlock();
    }
}
