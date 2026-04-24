package com.backtobedrock.augmentedhardcore.mappers.ban;

import com.backtobedrock.augmentedhardcore.AugmentedHardcore;
import com.backtobedrock.augmentedhardcore.domain.Ban;
import com.backtobedrock.augmentedhardcore.domain.BanEntry;
import com.backtobedrock.augmentedhardcore.domain.Killer;
import com.backtobedrock.augmentedhardcore.domain.Location;
import com.backtobedrock.augmentedhardcore.mappers.AbstractMapper;
import com.backtobedrock.augmentedhardcore.utilities.ConfigUtils;
import org.bukkit.Server;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class MySQLBanMapper extends AbstractMapper implements IBanMapper {
    public MySQLBanMapper(AugmentedHardcore plugin) {
        super(plugin);
    }

    public BanEntry getBanFromResultSetSync(ResultSet resultSet) {
        try {
            if (resultSet.getObject("ban_id") != null) {
                boolean nullified;
                try {
                    nullified = resultSet.getBoolean("nullified");
                } catch (SQLException ignored) {
                    nullified = false;
                }
                return new BanEntry(resultSet.getInt("ban_id"),
                        new Ban(
                                resultSet.getTimestamp("start_date").toLocalDateTime(),
                                resultSet.getTimestamp("expiration_date").toLocalDateTime(),
                                resultSet.getInt("ban_time"),
                                ConfigUtils.getDamageCause(resultSet.getString("damage_cause")),
                                ConfigUtils.getDamageCauseType(resultSet.getString("damage_cause_type")),
                                new Location(resultSet.getString("world"), resultSet.getDouble("x"), resultSet.getDouble("y"), resultSet.getDouble("z")),
                                resultSet.getBoolean("has_killer") ? new Killer(resultSet.getString("killer_name"), resultSet.getString("killer_display_name"), ConfigUtils.getEntityType(resultSet.getString("killer_entity_type"))) : null,
                                resultSet.getBoolean("in_combat") ? new Killer(resultSet.getString("in_combat_with_name"), resultSet.getString("in_combat_with_display_name"), ConfigUtils.getEntityType(resultSet.getString("in_combat_with_entity_type"))) : null,
                                resultSet.getString("death_message"),
                                resultSet.getLong("time_since_previous_death_ban"),
                                resultSet.getLong("time_since_previous_death"),
                                nullified
                        )
                );
            }
        } catch (SQLException e) {
            this.plugin.getLogger().log(Level.SEVERE, "Could not parse ban from result set.", e);
        }
        return null;
    }

    @Override
    public void insertBan(Server server, UUID uuid, BanEntry ban) {
        this.updateBan(server, uuid, ban);
    }

    @Override
    public void updateBan(Server server, UUID uuid, BanEntry ban) {
        if (this.plugin.isStopping()) {
            this.updateBanSync(server, uuid, ban);
            return;
        }

        this.updateBanAsync(server, uuid, ban).exceptionally(ex -> {
            this.plugin.getLogger().log(Level.SEVERE, "Could not update ban asynchronously.", ex);
            return null;
        });
    }

    public CompletableFuture<Void> updateBanAsync(Server server, UUID uuid, BanEntry ban) {
        return CompletableFuture.runAsync(() -> this.updateBanSync(server, uuid, ban), this.plugin.getExecutor());
    }

    public void updateBanSync(Server server, UUID uuid, BanEntry ban) {
        String sql = "INSERT INTO ah_ban (`ban_id`,`player_uuid`,`server_ip`,`server_port`,`start_date`,`expiration_date`,`ban_time`,`damage_cause`,`damage_cause_type`,`world`,`x`,`y`,`z`,`has_killer`,`killer_name`,`killer_display_name`,`killer_entity_type`,`in_combat`,`in_combat_with_name`,`in_combat_with_display_name`,`in_combat_with_entity_type`,`death_message`,`time_since_previous_death_ban`,`time_since_previous_death`,`nullified`)"
                + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
                + "ON DUPLICATE KEY UPDATE "
                + "`server_ip` = ?,"
                + "`server_port` = ?,"
                + "`start_date` = ?,"
                + "`expiration_date` = ?,"
                + "`ban_time` = ?,"
                + "`damage_cause` = ?,"
                + "`damage_cause_type` = ?,"
                + "`world` = ?,"
                + "`x` = ?,"
                + "`y` = ?,"
                + "`z` = ?,"
                + "`has_killer`= ?,"
                + "`killer_name`= ?,"
                + "`killer_display_name`= ?,"
                + "`killer_entity_type`= ?,"
                + "`in_combat`= ?,"
                + "`in_combat_with_name`= ?,"
                + "`in_combat_with_display_name`= ?,"
                + "`in_combat_with_entity_type`= ?,"
                + "`death_message` = ?,"
                + "`time_since_previous_death_ban` = ?,"
                + "`time_since_previous_death` = ?,"
                + "`nullified` = ?;";

        try (Connection connection = this.database.getConnection(); PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            bindBanParameters(preparedStatement, 1, server, uuid, ban);
            bindBanParameters(preparedStatement, 26, server, uuid, ban);
            preparedStatement.execute();
        } catch (SQLException | UnknownHostException e) {
            this.plugin.getLogger().log(Level.SEVERE, "Could not update ban.", e);
        }
    }

    private void bindBanParameters(PreparedStatement preparedStatement, int startIndex, Server server, UUID uuid, BanEntry ban) throws SQLException, UnknownHostException {
        int index = startIndex;
        if (startIndex == 1) {
            preparedStatement.setInt(index++, ban.id());
            preparedStatement.setString(index++, uuid.toString());
        }
        preparedStatement.setString(index++, server != null ? InetAddress.getLocalHost().getHostAddress() : null);
        preparedStatement.setObject(index++, server != null ? this.plugin.getServer().getPort() : null);
        preparedStatement.setTimestamp(index++, Timestamp.valueOf(ban.ban().getStartDate()));
        preparedStatement.setTimestamp(index++, Timestamp.valueOf(ban.ban().getExpirationDate()));
        preparedStatement.setInt(index++, ban.ban().getBanTime());
        preparedStatement.setString(index++, ban.ban().getDamageCause().name());
        preparedStatement.setString(index++, ban.ban().getDamageCauseType().name());
        preparedStatement.setString(index++, ban.ban().getLocation().getWorld());
        preparedStatement.setDouble(index++, ban.ban().getLocation().getX());
        preparedStatement.setDouble(index++, ban.ban().getLocation().getY());
        preparedStatement.setDouble(index++, ban.ban().getLocation().getZ());
        preparedStatement.setBoolean(index++, ban.ban().getKiller() != null);
        preparedStatement.setString(index++, ban.ban().getKiller() == null ? null : ban.ban().getKiller().getName());
        preparedStatement.setString(index++, ban.ban().getKiller() == null ? null : ban.ban().getKiller().getDisplayName());
        preparedStatement.setString(index++, ban.ban().getKiller() == null ? null : ban.ban().getKiller().getType().name());
        preparedStatement.setBoolean(index++, ban.ban().getInCombatWith() != null);
        preparedStatement.setString(index++, ban.ban().getInCombatWith() == null ? null : ban.ban().getInCombatWith().getName());
        preparedStatement.setString(index++, ban.ban().getInCombatWith() == null ? null : ban.ban().getInCombatWith().getDisplayName());
        preparedStatement.setString(index++, ban.ban().getInCombatWith() == null ? null : ban.ban().getInCombatWith().getType().name());
        preparedStatement.setString(index++, ban.ban().getDeathMessage());
        preparedStatement.setLong(index++, ban.ban().getTimeSincePreviousDeathBan());
        preparedStatement.setLong(index++, ban.ban().getTimeSincePreviousDeath());
        preparedStatement.setBoolean(index, ban.ban().isNullified());
    }

    @Override
    public void deleteBan(UUID uuid, Integer id) {
        if (this.plugin.isStopping()) {
            this.deleteBanSync(uuid, id);
            return;
        }

        CompletableFuture.runAsync(() -> this.deleteBanSync(uuid, id), this.plugin.getExecutor()).exceptionally(ex -> {
            this.plugin.getLogger().log(Level.SEVERE, "Could not delete ban asynchronously.", ex);
            return null;
        });
    }

    private void deleteBanSync(UUID uuid, Integer id) {
        String sql = "DELETE FROM ah_ban " +
                "WHERE ban_id = ? AND player_uuid = ?;";

        try (Connection connection = this.database.getConnection(); PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, id.toString());
            preparedStatement.setString(2, uuid.toString());
            preparedStatement.execute();
        } catch (SQLException e) {
            this.plugin.getLogger().log(Level.SEVERE, "Could not delete ban.", e);
        }
    }
}
