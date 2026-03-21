package dev.gimi.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

/**
 * Seeds a default admin user on first startup when the users table is empty.
 */
@Component
public class AdminUserInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserInitializer.class);

    private final DataSource dataSource;
    private final PasswordEncoder passwordEncoder;
    private final ServerConfig serverConfig;

    public AdminUserInitializer(DataSource dataSource,
                                PasswordEncoder passwordEncoder,
                                ServerConfig serverConfig) {
        this.dataSource = dataSource;
        this.passwordEncoder = passwordEncoder;
        this.serverConfig = serverConfig;
    }

    @Override
    public void run(String... args) throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            // Check if any users exist
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM users");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getLong(1) > 0) {
                    log.info("Users already exist, skipping admin seed");
                    return;
                }
            }

            // Seed default admin
            String id = UUID.randomUUID().toString();
            String hash = passwordEncoder.encode(serverConfig.getDefaultAdminPassword());

            String sql = "INSERT INTO users (id, username, password_hash, email, roles, enabled) " +
                         "VALUES (?, 'admin', ?, 'admin@gimi.dev', '[\"ADMIN\"]'::jsonb, true)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, id);
                ps.setString(2, hash);
                ps.executeUpdate();
            }

            log.info("Created default admin user (username: admin, password: {})",
                    serverConfig.getDefaultAdminPassword());
        } catch (Exception e) {
            log.warn("Could not seed admin user: {}", e.getMessage());
        }
    }
}
