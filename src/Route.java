public class Route {
    private String ipDestiny;
    private int metric;
    private String ipOut;
    private long lastSeen;

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

    public void updateRoute(int newMetric, String newIpOut) {
        this.metric = newMetric;
        this.ipOut = newIpOut;
        this.lastSeen = System.currentTimeMillis();
    }

    public void updateTimestamp() {
        this.lastSeen = System.currentTimeMillis();
    }

    @Override
    public String toString() {
        return "Route{" + "ipDestiny='" + ipDestiny + '\'' +
               ", metric=" + metric +
               ", ipOut='" + ipOut + '\'' +
               ", lastSeen=" + lastSeen +
               '}';
    }
}

