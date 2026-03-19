package dev.gimi.engine.connector;

import dev.gimi.core.model.connector.*;
import java.util.*;

public interface ConnectorService {
    Connector create(Connector connector);
    Connector update(Connector connector);
    void delete(String id);
    Optional<Connector> get(String id);
    List<Connector> list();
    List<Connector> listByType(ConnectorType type);
    List<Connector> listByCategory(ConnectorCategory category);
    ConnectorTestResult test(String id);

    record ConnectorTestResult(boolean success, String message, long latencyMs) {}
}
