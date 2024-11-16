public class Route {
    private String ipDestiny;
    private int metric;
    private String ipOut;
    private long lastSeen;

    public Route(String ipDestiny, int metric, String ipOut) {
        this.ipDestiny = ipDestiny;
        this.metric = metric;
        this.ipOut = ipOut;
        this.lastSeen = System.currentTimeMillis(); // Define o `lastSeen` na criação
    }

    public void updateRoute(int newMetric, String newIpOut) {
        this.metric = newMetric;
        this.ipOut = newIpOut;
        this.lastSeen = System.currentTimeMillis(); // Atualiza o `lastSeen`
    }

    public void updateLastSeen() {
        this.lastSeen = System.currentTimeMillis(); // Método para atualizar apenas o `lastSeen`
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

    @Override
    public String toString() {
        return "Dest: " + ipDestiny + ", Metric: " + metric + ", Next Hop: " + ipOut;
    }
}

