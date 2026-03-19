package dev.gimi.server.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Loads user details from PostgreSQL for Spring Security authentication.
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(UserDetailsServiceImpl.class);

    private final DataSource dataSource;

    public UserDetailsServiceImpl(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String sql = "SELECT id, username, password_hash, email, roles, enabled FROM users WHERE username = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new UsernameNotFoundException("User not found: " + username);
                }

                String passwordHash = rs.getString("password_hash");
                boolean enabled = rs.getBoolean("enabled");
                String rolesStr = rs.getString("roles");

                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                if (rolesStr != null && !rolesStr.isBlank()) {
                    Arrays.stream(rolesStr.split(","))
                            .map(String::trim)
                            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                            .forEach(authorities::add);
                }

                return new org.springframework.security.core.userdetails.User(
                        username, passwordHash, enabled,
                        true, true, true, authorities);
            }

        } catch (SQLException e) {
            log.error("Database error loading user '{}': {}", username, e.getMessage());
            throw new UsernameNotFoundException("Error loading user: " + username, e);
        }
    }
}
