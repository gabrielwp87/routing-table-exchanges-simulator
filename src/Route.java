package src;
public class Route {
    private String ipDestiny;
    private int metric;
    private String ipOut;
    private long lastSeen; // Armazena o timestamp da última atualização da rota


    public Route(String ipDestiny, int metric, String ipOut) {
        this.ipDestiny = ipDestiny;
        this.metric = metric;
        this.ipOut = ipOut;
        this.lastSeen = System.currentTimeMillis();
    }

    public String getIpDestiny() {
        return ipDestiny;
    }

    public int getMetric() {
        return metric;
    }

    public String getIpOut() {
        return ipOut;
    }

    public long getLastSeen() {
        return lastSeen;
    }

    public void updateLastSeen() {
        this.lastSeen = System.currentTimeMillis(); // Atualiza o timestamp
    }

    // update olny the metric to a better one and the ip output, keeping the destiny
    public void updateRoute(int newMetric, String newIpOut) {
        this.metric = newMetric;
        this.ipOut = newIpOut;
        updateLastSeen(); // Atualiza o lastSeen quando a rota é modificada
    }

    @Override
    public String toString() {
        
        return this.getIpDestiny() + " " + this.getMetric() + " " + this.getIpOut();
    }
}
