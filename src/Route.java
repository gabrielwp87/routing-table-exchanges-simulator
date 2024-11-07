package src;
public class Route {
    private String ipDestiny;
    private int metric;
    private String ipOut;

    public Route(String ipDestiny, int metric, String ipOut) {
        this.ipDestiny = ipDestiny;
        this.metric = metric;
        this.ipOut = ipOut;
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

    // update olny the metric to a better one and the ip output, keeping the destiny
    public void UpdateRoute(int newMetric, String newIpOut) {
        this.metric = newMetric;
        this.ipOut = newIpOut;
    }

}
