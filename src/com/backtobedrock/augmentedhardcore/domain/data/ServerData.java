package com.backtobedrock.augmentedhardcore.domain.data;

import com.backtobedrock.augmentedhardcore.AugmentedHardcore;
import com.backtobedrock.augmentedhardcore.domain.Ban;
import com.backtobedrock.augmentedhardcore.domain.BanEntry;
import com.backtobedrock.augmentedhardcore.domain.enums.Permission;
import com.backtobedrock.augmentedhardcore.runnables.Unban;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class ServerData {
    private final AugmentedHardcore plugin;

    //serializable
    private final Server server;
    private final Map<UUID, Unban> ongoingBans = new HashMap<>();
    private final Map<String, Ban> ongoingIPBans = new HashMap<>();
    private int totalDeathBans;

    public ServerData() {
        this(0, new HashMap<>());
    }

    public ServerData(int totalDeathBans, Map<UUID, BanEntry> ongoingBans) {
        this.plugin = JavaPlugin.getPlugin(AugmentedHardcore.class);
        this.server = this.plugin.getServer();
        this.totalDeathBans = totalDeathBans;
        ongoingBans.forEach((key, value) -> {
            OfflinePlayer player = Bukkit.getOfflinePlayer(key);
            this.startBan(player, value);
            if (this.plugin.getConfigurations().getDeathBanConfiguration().getBanType() == BanList.Type.IP) {
                this.plugin.getPlayerRepository().getByPlayer(player).thenAcceptAsync(playerData -> this.ongoingIPBans.put(playerData.getLastKnownIp(), value.ban()));
            }
        });
    }

    public static ServerData deserialize(ConfigurationSection section) {
        Map<UUID, BanEntry> cOngoingBans = new HashMap<>();
        int cTotalBans = section.getInt("TotalDeathBans", section.getInt("TotalBans", 0));

        //get ongoing bans section
        ConfigurationSection ongoingBanSection = section.getConfigurationSection("OngoingBans");
        //if it exists, loop over all keys and deserialize bans
        if (ongoingBanSection != null) {
            ongoingBanSection.getKeys(false).forEach(e -> {
                try {
                    UUID uuid = UUID.fromString(e);
                    //get ban
                    ConfigurationSection banSection = ongoingBanSection.getConfigurationSection(e);
                    if (banSection != null) {
                        banSection.getKeys(false).forEach(a -> {
                            int id = Integer.parseInt(a);
                            ConfigurationSection banConfiguration = banSection.getConfigurationSection(a);
                            Ban ban = null;
                            if (banConfiguration != null) {
                                ban = Ban.Deserialize(banConfiguration);
                            }
                            //if exists, put in map
                            if (ban != null) {
                                cOngoingBans.put(uuid, new BanEntry(id, ban));
                            }
                        });
                    }
                } catch (Exception exception) {
                    //ignore
                }
            });
        }
        return new ServerData(cTotalBans, cOngoingBans);
    }

    public int getTotalDeathBans() {
        return this.totalDeathBans;
    }

    public int getTotalOngoingBans() {
        return this.ongoingBans.size();
    }

    public void addBan(PlayerData playerData, Player player, BanEntry ban) {
        if (player.hasPermission(Permission.BYPASS_BAN_SPECTATOR.getPermissionString())) {
            Bukkit.getScheduler().runTask(plugin, () -> player.setGameMode(GameMode.SPECTATOR));
            playerData.stopPlaytimeRunnables();
            playerData.setSpectatorBanned(true);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> player.kickPlayer(ban.ban().getBanMessage()));
        }
        this.startBan(player, ban);
        if (this.plugin.getConfigurations().getDeathBanConfiguration().getBanType() == BanList.Type.IP) {
            this.ongoingIPBans.put(playerData.getLastKnownIp(), ban.ban());
        }
        this.totalDeathBans++;
        this.plugin.getServerRepository().updateServerData(this);
    }

    private void startBan(OfflinePlayer player, BanEntry ban) {
        Unban unban = new Unban(player, ban);
        this.ongoingBans.put(player.getUniqueId(), unban);
        if (ban.ban().getBanTime() != -1) {
            unban.start();
        }
    }

    public boolean isDeathBanned(UUID uuid) {
        return this.ongoingBans.containsKey(uuid);
    }

    public boolean unDeathBan(UUID uuid) {
        Unban ban = this.getBan(uuid);
        if (ban != null) {
            ban.finish();
            return true;
        }
        return false;
    }

    public void rescheduleBan(UUID uuid) {
        Unban unban = this.getBan(uuid);
        if (unban == null) {
            return;
        }

        unban.stop();

        BanEntry banEntry = unban.getBan();
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        Unban replacement = new Unban(player, banEntry);
        this.ongoingBans.put(uuid, replacement);
        if (banEntry.ban().getBanTime() != -1) {
            replacement.start();
        }
    }

    public void removeBan(OfflinePlayer player) {
        Unban unban = this.ongoingBans.remove(player.getUniqueId());
        if (unban != null) {
            unban.getBan().ban().setExpirationDate(LocalDateTime.now());
            this.plugin.getPlayerRepository().getByPlayer(player).thenAcceptAsync(playerData -> {
                this.ongoingIPBans.remove(playerData.getLastKnownIp());
                if (player.getPlayer() != null) {
                    playerData.resetSpectatorDeathBan(player.getPlayer());
                }
            }).exceptionally(e -> {
                this.plugin.getLogger().log(Level.SEVERE, "Error removing ban.", e);
                return null;
            });
            this.plugin.getServerRepository().removeBanFromServerData(player.getUniqueId(), unban.getBan());
        }
    }

    public Unban getBan(UUID uuid) {
        return this.ongoingBans.get(uuid);
    }

    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();

        Map<String, Object> cOngoingBans = new HashMap<>();
        this.ongoingBans.forEach((key, value) -> {
            Map<Integer, Object> cOngoingBansPlayer = new HashMap<>();
            cOngoingBansPlayer.put(value.getBan().id(), value.getBan().ban().serialize());
            cOngoingBans.put(key.toString(), cOngoingBansPlayer);
        });
        map.put("OngoingBans", cOngoingBans);
        map.put("TotalDeathBans", this.totalDeathBans);

        return map;
    }

    public Map<UUID, Unban> getOngoingBans() {
        return ongoingBans;
    }

    public Server getServer() {
        return server;
    }

    public Map<String, Ban> getOngoingIPBans() {
        return ongoingIPBans;
    }
}
