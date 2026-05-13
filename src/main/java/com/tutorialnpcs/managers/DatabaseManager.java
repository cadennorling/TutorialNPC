package com.tutorialnpcs.managers;

import com.tutorialnpcs.TutorialNPCsPlugin;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class DatabaseManager {

    private final TutorialNPCsPlugin plugin;
    private HikariDataSource dataSource;

    public DatabaseManager(TutorialNPCsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean connect() {
        try {
            HikariConfig config = new HikariConfig();
            String host = plugin.getConfig().getString("database.host", "localhost");
            int port = plugin.getConfig().getInt("database.port", 3306);
            String db = plugin.getConfig().getString("database.name", "tutorialnpcs");
            String user = plugin.getConfig().getString("database.username", "root");
            String pass = plugin.getConfig().getString("database.password", "password");
            int poolSize = plugin.getConfig().getInt("database.pool-size", 5);

            config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + db
                    + "?useSSL=false&autoReconnect=true&characterEncoding=utf8");
            config.setUsername(user);
            config.setPassword(pass);
            config.setMaximumPoolSize(poolSize);
            config.setMinimumIdle(1);
            config.setConnectionTimeout(10000);
            config.setIdleTimeout(600000);
            config.setMaxLifetime(1800000);
            config.setPoolName("TutorialNPCs-Pool");

            dataSource = new HikariDataSource(config);
            createTables();
            plugin.getLogger().info("Connected to MySQL database successfully.");
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to connect to MySQL! Check your database settings in config.yml.", e);
            return false;
        }
    }

    private void createTables() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS player_progress (
                    uuid VARCHAR(36) NOT NULL,
                    next_npc_index INT NOT NULL DEFAULT 0,
                    PRIMARY KEY (uuid)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """);
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS player_completed_npcs (
                    uuid VARCHAR(36) NOT NULL,
                    npc_id INT NOT NULL,
                    completed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (uuid, npc_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """);
        }
    }

    public void disconnect() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("Disconnected from MySQL.");
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public int loadNextNpcIndex(UUID uuid) {
        String sql = "SELECT next_npc_index FROM player_progress WHERE uuid = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("next_npc_index");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load progress for " + uuid, e);
        }
        return 0;
    }

    public void saveNextNpcIndex(UUID uuid, int index) {
        String sql = "INSERT INTO player_progress (uuid, next_npc_index) VALUES (?, ?) " +
                     "ON DUPLICATE KEY UPDATE next_npc_index = VALUES(next_npc_index)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, index);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save progress for " + uuid, e);
        }
    }

    public Set<Integer> loadCompletedNpcs(UUID uuid) {
        Set<Integer> completed = new HashSet<>();
        String sql = "SELECT npc_id FROM player_completed_npcs WHERE uuid = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) completed.add(rs.getInt("npc_id"));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load completed NPCs for " + uuid, e);
        }
        return completed;
    }

    public void markNpcCompleted(UUID uuid, int npcId) {
        String sql = "INSERT IGNORE INTO player_completed_npcs (uuid, npc_id) VALUES (?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, npcId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to mark NPC completed for " + uuid, e);
        }
    }

    public void resetPlayer(UUID uuid) {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM player_completed_npcs WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO player_progress (uuid, next_npc_index) VALUES (?, 0) " +
                        "ON DUPLICATE KEY UPDATE next_npc_index = 0")) {
                    ps.setString(1, uuid.toString());
                    ps.executeUpdate();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to reset player " + uuid, e);
        }
    }

    public void setProgress(UUID uuid, int index) {
        saveNextNpcIndex(uuid, index);
    }
}
