package dev.gimi.engine.connector;

import dev.gimi.core.model.connector.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultConnectorService implements ConnectorService {
    private static final Logger log = LoggerFactory.getLogger(DefaultConnectorService.class);
    private final Map<String, Connector> connectors = new ConcurrentHashMap<>();
    private final HttpClient httpClient = HttpClient.newBuilder().build();

    @Override
    public Connector create(Connector connector) {
        connectors.put(connector.id(), connector);
        log.info("Connector created: {} ({})", connector.name(), connector.type());
        return connector;
    }

    @Override
    public Connector update(Connector connector) {
        connectors.put(connector.id(), connector);
        return connector;
    }

    @Override
    public void delete(String id) { connectors.remove(id); }

    @Override
    public Optional<Connector> get(String id) { return Optional.ofNullable(connectors.get(id)); }

    @Override
    public List<Connector> list() { return List.copyOf(connectors.values()); }

    @Override
    public List<Connector> listByType(ConnectorType type) {
        return connectors.values().stream().filter(c -> c.type() == type).toList();
    }

    @Override
    public List<Connector> listByCategory(ConnectorCategory category) {
        return connectors.values().stream().filter(c -> c.category() == category).toList();
    }

    @Override
    public ConnectorTestResult test(String id) {
        Connector conn = connectors.get(id);
        if (conn == null) return new ConnectorTestResult(false, "Connector not found", 0);

        long start = System.currentTimeMillis();
        try {
            boolean success = switch (conn.type()) {
                case GITHUB, GITLAB, BITBUCKET -> testGitProvider(conn);
                case AWS -> testAws(conn);
                case KUBERNETES -> testKubernetes(conn);
                case DOCKER_HUB, ECR, GCR, ACR, NEXUS, ARTIFACTORY -> testRegistry(conn);
                case HASHICORP_VAULT -> testVault(conn);
                case PROMETHEUS, DATADOG, NEW_RELIC, SPLUNK -> testMonitoring(conn);
                case HTTP, CUSTOM -> testHttp(conn);
                default -> testHttp(conn);
            };
            long latency = System.currentTimeMillis() - start;

            ConnectorStatus status = success ? ConnectorStatus.CONNECTED : ConnectorStatus.ERROR;
            String message = success ? "Connection successful" : "Connection failed";
            Connector updated = new Connector(conn.id(), conn.name(), conn.description(),
                conn.type(), conn.category(), conn.config(), conn.credentials(),
                status, message, Instant.now(), conn.createdAt(), Instant.now());
            connectors.put(id, updated);

            return new ConnectorTestResult(success, message, latency);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            Connector updated = new Connector(conn.id(), conn.name(), conn.description(),
                conn.type(), conn.category(), conn.config(), conn.credentials(),
                ConnectorStatus.ERROR, e.getMessage(), Instant.now(), conn.createdAt(), Instant.now());
            connectors.put(id, updated);
            return new ConnectorTestResult(false, "Error: " + e.getMessage(), latency);
        }
    }

    private boolean testGitProvider(Connector conn) throws Exception {
        String url = conn.config().getOrDefault("url", "https://api.github.com");
        String token = conn.credentials().getOrDefault("token", "");
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url))
            .header("Authorization", "Bearer " + token).GET().build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        return resp.statusCode() < 400;
    }

    private boolean testAws(Connector conn) {
        // Validate AWS credentials by checking config
        return conn.credentials().containsKey("accessKeyId") && conn.credentials().containsKey("secretAccessKey");
    }

    private boolean testKubernetes(Connector conn) {
        String apiServer = conn.config().getOrDefault("apiServer", "");
        return !apiServer.isEmpty();
    }

    private boolean testRegistry(Connector conn) throws Exception {
        String url = conn.config().getOrDefault("url", "");
        if (url.isEmpty()) return false;
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url + "/v2/")).GET().build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        return resp.statusCode() < 400 || resp.statusCode() == 401;
    }

    private boolean testVault(Connector conn) throws Exception {
        String url = conn.config().getOrDefault("url", "");
        String token = conn.credentials().getOrDefault("token", "");
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url + "/v1/sys/health"))
            .header("X-Vault-Token", token).GET().build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        return resp.statusCode() < 400;
    }

    private boolean testMonitoring(Connector conn) throws Exception {
        return testHttp(conn);
    }

    private boolean testHttp(Connector conn) throws Exception {
        String url = conn.config().getOrDefault("url", "");
        if (url.isEmpty()) return false;
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        return resp.statusCode() < 400;
    }
}
