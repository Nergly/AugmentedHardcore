package com.backtobedrock.augmentedhardcore.mappers.server;

import com.backtobedrock.augmentedhardcore.AugmentedHardcore;
import com.backtobedrock.augmentedhardcore.domain.BanEntry;
import com.backtobedrock.augmentedhardcore.domain.data.ServerData;
import com.backtobedrock.augmentedhardcore.mappers.AbstractMapper;
import com.backtobedrock.augmentedhardcore.mappers.ban.MySQLBanMapper;
import org.bukkit.Server;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class MySQLServerMapper extends AbstractMapper implements IServerMapper {

    private final MySQLBanMapper banMapper;

    public MySQLServerMapper(AugmentedHardcore plugin) {
        super(plugin);
        this.banMapper = new MySQLBanMapper(plugin);
    }

    @Override
    public void insertServerDataAsync(ServerData serverData) {
        this.updateServerData(serverData).exceptionally(ex -> {
            this.plugin.getLogger().log(Level.SEVERE, "Could not insert server data asynchronously.", ex);
            return null;
        });
    }

    @Override
    public void insertServerDataSync(ServerData serverData) {
        this.updateServerDataSync(serverData);
    }

    @Override
    public CompletableFuture<ServerData> getServerData(Server server) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * "
                    + "FROM ah_ban AS b "
                    + "RIGHT OUTER JOIN ah_server as s ON b.server_ip = s.server_ip AND b.server_port = s.server_port "
                    + "WHERE s.server_ip = ? AND s.server_port = ?;";

            try (Connection connection = this.database.getConnection(); PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                preparedStatement.setString(1, InetAddress.getLocalHost().getHostAddress());
                preparedStatement.setInt(2, server.getPort());
                ResultSet resultSet = preparedStatement.executeQuery();
                Map<UUID, BanEntry> deathBans = new HashMap<>();
                int totalDeathBans = 0;
                while (resultSet.next()) {
                    totalDeathBans = resultSet.getInt("total_death_bans");
                    String uuidString = resultSet.getString("player_uuid");
                    if (uuidString != null && !uuidString.isEmpty()) {
                        BanEntry banPair = this.banMapper.getBanFromResultSetSync(resultSet);
                        if (banPair != null) {
                            deathBans.put(UUID.fromString(uuidString), banPair);
                        }
                    }
                }
                return new ServerData(totalDeathBans, deathBans);
            } catch (SQLException | UnknownHostException e) {
                this.plugin.getLogger().log(Level.SEVERE, "Could not load server data.", e);
            }
            return null;
        }, this.plugin.getExecutor());
    }

    @Override
    public CompletableFuture<Void> updateServerData(ServerData data) {
        if (this.plugin.isStopping()) {
            this.updateServerDataSync(data);
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> this.updateServerDataSync(data), this.plugin.getExecutor()).exceptionally(ex -> {
            this.plugin.getLogger().log(Level.SEVERE, "Could not update server data asynchronously.", ex);
            return null;
        });
    }

    private void updateServerDataSync(ServerData data) {
        String sql = "INSERT INTO ah_server (`server_ip`, `server_port`, `total_death_bans`)"
                + "VALUES(?, ?, ?)"
                + "ON DUPLICATE KEY UPDATE `total_death_bans` = ?;";

        try (Connection connection = this.database.getConnection(); PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, InetAddress.getLocalHost().getHostAddress());
            preparedStatement.setInt(2, this.plugin.getServer().getPort());
            preparedStatement.setInt(3, data.getTotalDeathBans());
            preparedStatement.setInt(4, data.getTotalDeathBans());
            preparedStatement.execute();
        } catch (SQLException | UnknownHostException e) {
            this.plugin.getLogger().log(Level.SEVERE, "Could not update server data.", e);
            return;
        }

        data.getOngoingBans().forEach((key, value) -> this.banMapper.updateBanSync(this.plugin.getServer(), key, value.getBan()));
    }

    @Override
    public void deleteServerData() {
        if (this.plugin.isStopping()) {
            this.deleteServerDataSync();
            return;
        }

        CompletableFuture.runAsync(this::deleteServerDataSync, this.plugin.getExecutor()).exceptionally(ex -> {
            this.plugin.getLogger().log(Level.SEVERE, "Could not delete server data asynchronously.", ex);
            return null;
        });
    }

    private void deleteServerDataSync() {
        String sql = "DELETE FROM ah_server " +
                "WHERE server_ip = ? AND server_port = ?;";

        try (Connection connection = this.database.getConnection(); PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, InetAddress.getLocalHost().getHostAddress());
            preparedStatement.setInt(2, this.plugin.getServer().getPort());
            preparedStatement.execute();
        } catch (SQLException | UnknownHostException e) {
            this.plugin.getLogger().log(Level.SEVERE, "Could not delete server data.", e);
        }
    }

    @Override
    public void deleteBanFromServerData(UUID uuid, BanEntry ban) {
        if (this.plugin.isStopping()) {
            this.banMapper.updateBanSync(null, uuid, ban);
        } else {
            this.banMapper.updateBan(null, uuid, ban);
        }
    }
}
