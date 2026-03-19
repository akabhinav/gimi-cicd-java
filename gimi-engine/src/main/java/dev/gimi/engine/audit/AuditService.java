package dev.gimi.engine.audit;

import dev.gimi.core.model.audit.AuditCategory;
import dev.gimi.core.model.audit.AuditEvent;
import dev.gimi.core.model.tenant.ResourceScope;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface AuditService {
    void record(AuditEvent event);

    AuditEventBuilder newEvent(String action, AuditCategory category);

    List<AuditEvent> query(AuditQuery query);

    long count(AuditQuery query);

    record AuditQuery(
        ResourceScope scope,
        String userId,
        String resourceType,
        String resourceId,
        AuditCategory category,
        Instant from,
        Instant to,
        int limit,
        int offset
    ) {
        public AuditQuery {
            limit = limit <= 0 ? 50 : Math.min(limit, 1000);
            offset = Math.max(offset, 0);
        }
    }
}
