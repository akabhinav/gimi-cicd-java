package dev.gimi.server.controller;

import dev.gimi.core.model.connector.*;
import dev.gimi.engine.connector.ConnectorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/connectors")
public class ConnectorController {

    private final ConnectorService connectorService;

    public ConnectorController(ConnectorService connectorService) {
        this.connectorService = connectorService;
    }

    @GetMapping
    public ResponseEntity<List<Connector>> listConnectors(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String category) {
        if (type != null) {
            return ResponseEntity.ok(connectorService.listByType(ConnectorType.valueOf(type.toUpperCase())));
        }
        if (category != null) {
            return ResponseEntity.ok(connectorService.listByCategory(ConnectorCategory.valueOf(category.toUpperCase())));
        }
        return ResponseEntity.ok(connectorService.list());
    }

    @PostMapping
    public ResponseEntity<Connector> createConnector(@RequestBody Connector connector) {
        return ResponseEntity.ok(connectorService.create(connector));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Connector> getConnector(@PathVariable String id) {
        return connectorService.get(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Connector> updateConnector(@PathVariable String id, @RequestBody Connector connector) {
        return ResponseEntity.ok(connectorService.update(connector));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteConnector(@PathVariable String id) {
        connectorService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<ConnectorService.ConnectorTestResult> testConnector(@PathVariable String id) {
        return ResponseEntity.ok(connectorService.test(id));
    }
}
