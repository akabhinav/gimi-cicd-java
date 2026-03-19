package dev.gimi.engine.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.gimi.core.model.audit.*;
import dev.gimi.core.model.tenant.ResourceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;

public class PostgresAuditService implements AuditService {
    private static final Logger log = LoggerFactory.getLogger(PostgresAuditService.class);
    private final DataSource dataSource;
    private final ObjectMapper mapper;

    public PostgresAuditService(DataSource dataSource, ObjectMapper mapper) {
        this.dataSource = dataSource;
        this.mapper = mapper;
    }

    @Override
    public void record(AuditEvent event) {
        String sql = """
            INSERT INTO audit_events (id, action, category, resource_type, resource_id,
                user_id, username, account_id, organization_id, project_id, scope,
                metadata, before_state, after_state, ip_address, user_agent, timestamp)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?)
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, event.id());
            ps.setString(2, event.action());
            ps.setString(3, event.category().name());
            ps.setString(4, event.resourceType());
            ps.setString(5, event.resourceId());
            ps.setString(6, event.userId());
            ps.setString(7, event.username());
            ps.setString(8, event.scope() != null ? event.scope().accountId() : null);
            ps.setString(9, event.scope() != null ? event.scope().organizationId() : null);
            ps.setString(10, event.scope() != null ? event.scope().projectId() : null);
            ps.setString(11, event.scope() != null ? event.scope().scope().name() : null);
            ps.setString(12, mapper.writeValueAsString(event.metadata()));
            ps.setString(13, event.before() != null ? mapper.writeValueAsString(event.before()) : "{}");
            ps.setString(14, event.after() != null ? mapper.writeValueAsString(event.after()) : "{}");
            ps.setString(15, event.ipAddress());
            ps.setString(16, event.userAgent());
            ps.setTimestamp(17, Timestamp.from(event.timestamp()));
            ps.executeUpdate();
        } catch (Exception e) {
            log.error("Failed to record audit event: {}", event.action(), e);
        }
    }

    @Override
    public AuditEventBuilder newEvent(String action, AuditCategory category) {
        return new AuditEventBuilder(action, category);
    }

    @Override
    public List<AuditEvent> query(AuditQuery query) {
        StringBuilder sql = new StringBuilder("SELECT * FROM audit_events WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (query.scope() != null) {
            sql.append(" AND account_id = ?");
            params.add(query.scope().accountId());
            if (query.scope().organizationId() != null) {
                sql.append(" AND organization_id = ?");
                params.add(query.scope().organizationId());
            }
            if (query.scope().projectId() != null) {
                sql.append(" AND project_id = ?");
                params.add(query.scope().projectId());
            }
        }
        if (query.userId() != null) { sql.append(" AND user_id = ?"); params.add(query.userId()); }
        if (query.resourceType() != null) { sql.append(" AND resource_type = ?"); params.add(query.resourceType()); }
        if (query.resourceId() != null) { sql.append(" AND resource_id = ?"); params.add(query.resourceId()); }
        if (query.category() != null) { sql.append(" AND category = ?"); params.add(query.category().name()); }
        if (query.from() != null) { sql.append(" AND timestamp >= ?"); params.add(Timestamp.from(query.from())); }
        if (query.to() != null) { sql.append(" AND timestamp <= ?"); params.add(Timestamp.from(query.to())); }

        sql.append(" ORDER BY timestamp DESC LIMIT ? OFFSET ?");
        params.add(query.limit());
        params.add(query.offset());

        List<AuditEvent> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("Failed to query audit events", e);
        }
        return results;
    }

    @Override
    public long count(AuditQuery query) {
        StringBuilder sql = new StringBuilder("SELECT count(*) FROM audit_events WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (query.scope() != null) {
            sql.append(" AND account_id = ?");
            params.add(query.scope().accountId());
            if (query.scope().organizationId() != null) {
                sql.append(" AND organization_id = ?");
                params.add(query.scope().organizationId());
            }
            if (query.scope().projectId() != null) {
                sql.append(" AND project_id = ?");
                params.add(query.scope().projectId());
            }
        }
        if (query.category() != null) { sql.append(" AND category = ?"); params.add(query.category().name()); }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (Exception e) {
            log.error("Failed to count audit events", e);
        }
        return 0;
    }

    @SuppressWarnings("unchecked")
    private AuditEvent mapRow(ResultSet rs) throws Exception {
        ResourceScope scope = rs.getString("scope") != null ?
            new ResourceScope(
                dev.gimi.core.model.tenant.Scope.valueOf(rs.getString("scope")),
                rs.getString("account_id"),
                rs.getString("organization_id"),
                rs.getString("project_id")
            ) : null;
        return new AuditEvent(
            rs.getString("id"),
            rs.getString("action"),
            AuditCategory.valueOf(rs.getString("category")),
            rs.getString("resource_type"),
            rs.getString("resource_id"),
            rs.getString("user_id"),
            rs.getString("username"),
            scope,
            mapper.readValue(rs.getString("metadata"), Map.class),
            mapper.readValue(rs.getString("before_state"), Map.class),
            mapper.readValue(rs.getString("after_state"), Map.class),
            rs.getString("ip_address"),
            rs.getString("user_agent"),
            rs.getTimestamp("timestamp").toInstant()
        );
    }
}
