package dev.gimi.engine.audit;

import dev.gimi.core.model.audit.*;
import dev.gimi.core.model.tenant.ResourceScope;
import java.time.Instant;
import java.util.*;

public class AuditEventBuilder {
    private String action;
    private AuditCategory category;
    private String resourceType;
    private String resourceId;
    private String userId;
    private String username;
    private ResourceScope scope;
    private Map<String, Object> metadata = new HashMap<>();
    private Map<String, Object> before;
    private Map<String, Object> after;
    private String ipAddress;
    private String userAgent;

    public AuditEventBuilder(String action, AuditCategory category) {
        this.action = action;
        this.category = category;
    }

    public AuditEventBuilder resource(String type, String id) {
        this.resourceType = type;
        this.resourceId = id;
        return this;
    }

    public AuditEventBuilder user(String userId, String username) {
        this.userId = userId;
        this.username = username;
        return this;
    }

    public AuditEventBuilder scope(ResourceScope scope) {
        this.scope = scope;
        return this;
    }

    public AuditEventBuilder metadata(String key, Object value) {
        this.metadata.put(key, value);
        return this;
    }

    public AuditEventBuilder before(Map<String, Object> before) {
        this.before = before;
        return this;
    }

    public AuditEventBuilder after(Map<String, Object> after) {
        this.after = after;
        return this;
    }

    public AuditEventBuilder request(String ipAddress, String userAgent) {
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        return this;
    }

    public AuditEvent build() {
        return new AuditEvent(
            UUID.randomUUID().toString(),
            action, category, resourceType, resourceId,
            userId, username, scope, metadata, before, after,
            ipAddress, userAgent, Instant.now()
        );
    }
}
