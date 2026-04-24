package com.backtobedrock.augmentedhardcore.domain.data;

import com.backtobedrock.augmentedhardcore.AugmentedHardcore;
import com.backtobedrock.augmentedhardcore.domain.Ban;
import com.backtobedrock.augmentedhardcore.domain.BanEntry;
import com.backtobedrock.augmentedhardcore.domain.Killer;
import com.backtobedrock.augmentedhardcore.domain.enums.DamageCause;
import com.backtobedrock.augmentedhardcore.domain.enums.Permission;
import com.backtobedrock.augmentedhardcore.domain.enums.TimePattern;
import com.backtobedrock.augmentedhardcore.domain.observer.*;
import com.backtobedrock.augmentedhardcore.guis.AbstractGui;
import com.backtobedrock.augmentedhardcore.guis.GuiMyStats;
import com.backtobedrock.augmentedhardcore.runnables.CombatTag.AbstractCombatTag;
import com.backtobedrock.augmentedhardcore.runnables.Playtime.AbstractPlaytime;
import com.backtobedrock.augmentedhardcore.runnables.Playtime.PlaytimeLifePart;
import com.backtobedrock.augmentedhardcore.runnables.Playtime.PlaytimeMaxHealth;
import com.backtobedrock.augmentedhardcore.runnables.Playtime.PlaytimeRevive;
import com.backtobedrock.augmentedhardcore.utilities.EventUtils;
import com.backtobedrock.augmentedhardcore.utilities.MessageUtils;
import com.backtobedrock.augmentedhardcore.utilities.PlayerUtils;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.time.LocalDateTime;
import java.util.*;
import java.util.logging.Level;

public class PlayerData {
    private final AugmentedHardcore plugin;
    private final OfflinePlayer player;
    private final List<AbstractPlaytime> playtime = new ArrayList<>();
    private final Map<UUID, Map<Class<?>, IObserver>> observers;
    private final String lastKnownIp;
    private final BanManager banManager;
    private final LifeManager lifeManager;
    private final PlaytimeTracker playtimeTracker;
    private List<AbstractCombatTag> combatTag = new ArrayList<>();
    private boolean kicked = false;
    private boolean combatLogged = false;
    private Killer combatTagger;
    private Killer reviving;
    private boolean spectatorBanned;
    private LocalDateTime lastDeath;

    public PlayerData(AugmentedHardcore plugin, OfflinePlayer player) {
        this(
                plugin,
                player,
                null,
                LocalDateTime.now(),
                plugin.getConfigurations().getLivesAndLifePartsConfiguration().getLivesAtStart(),
                plugin.getConfigurations().getLivesAndLifePartsConfiguration().getLifePartsAtStart(),
                false,
                plugin.getConfigurations().getReviveConfiguration().isReviveOnFirstJoin() ? 0L : plugin.getConfigurations().getReviveConfiguration().getTimeBetweenRevives(),
                plugin.getConfigurations().getLivesAndLifePartsConfiguration().getPlaytimePerLifePart(),
                plugin.getConfigurations().getMaxHealthConfiguration().getPlaytimePerHalfHeart(),
                new TreeMap<>()
        );
    }

    public PlayerData(AugmentedHardcore plugin, OfflinePlayer player, String lastKnownIp, LocalDateTime lastDeath, int lives, int lifeParts, boolean spectatorBanned, long timeTillNextRevive, long timeTillNextLifePart, long timeTillNextMaxHealth, NavigableMap<Integer, Ban> bans) {
        this.plugin = plugin;
        this.player = player;
        this.lastDeath = lastDeath;
        this.observers = new HashMap<>();
        this.spectatorBanned = spectatorBanned;
        this.lifeManager = new LifeManager(plugin, lives, lifeParts);
        this.playtimeTracker = new PlaytimeTracker(plugin, this, timeTillNextRevive, timeTillNextLifePart, timeTillNextMaxHealth);
        this.banManager = new BanManager(bans);
        if (player.getPlayer() != null) {
            lastKnownIp = PlayerUtils.getPlayerIP(player.getPlayer());
        }
        this.lastKnownIp = lastKnownIp;
    }

    public static PlayerData deserialize(AugmentedHardcore plugin, ConfigurationSection section, OfflinePlayer player) {
        NavigableMap<Integer, Ban> cBans = new TreeMap<>();
        String cLastKnownIp = section.getString("LastKnownIp", null);
        LocalDateTime cLastDeath = LocalDateTime.parse(section.getString("LastDeath", LocalDateTime.now().toString()));
        int cLives = section.getInt("Lives", plugin.getConfigurations().getLivesAndLifePartsConfiguration().getLivesAtStart());
        int cLifeParts = section.getInt("LifeParts", plugin.getConfigurations().getLivesAndLifePartsConfiguration().getLifePartsAtStart());
        boolean cSpectatorBanned = section.getBoolean("SpectatorBanned", false);
        int cTimeTillNextLifePart = section.getInt("TimeTillNextLifePart", plugin.getConfigurations().getLivesAndLifePartsConfiguration().getPlaytimePerLifePart());
        int cTimeTillNextMaxHealth = section.getInt("TimeTillNextMaxHealth", plugin.getConfigurations().getMaxHealthConfiguration().getPlaytimePerHalfHeart());
        long cTimeTillNextRevive = section.getLong("TimeTillNextRevive", plugin.getConfigurations().getReviveConfiguration().getTimeBetweenRevives());

        //get all bans
        ConfigurationSection bansSection = section.getConfigurationSection("Bans");
        if (bansSection != null) {
            for (String e : bansSection.getKeys(false)) {
                ConfigurationSection banSection = bansSection.getConfigurationSection(e);
                if (banSection != null) {
                    cBans.put(Integer.parseInt(e), Ban.Deserialize(banSection));
                }
            }
        }

        return new PlayerData(plugin, player, cLastKnownIp, cLastDeath, cLives, cLifeParts, cSpectatorBanned, cTimeTillNextRevive, cTimeTillNextLifePart, cTimeTillNextMaxHealth, cBans);
    }

    public PlaytimeTracker getPlaytimeTracker() {
        return this.playtimeTracker;
    }

    public long getTimeTillNextRevive() {
        if (this.plugin.getConfigurations().getReviveConfiguration().getGlobalReviveUnDeathBanConfiguration().isEnabled()) {
            if (this.plugin.getGlobalReviveUnDeathBan() != null) {
                return this.plugin.getGlobalReviveUnDeathBan().getTicksUntilNextRun();
            }
            return 0L;
        }
        return this.playtimeTracker.getTimeTillNextRevive();
    }

    private void setTimeTillNextRevive(long timeTillNextRevive) {
        this.playtimeTracker.setTimeTillNextRevive(timeTillNextRevive);
        this.observers.forEach((key, value) -> value.get(MyStatsTimeTillNextReviveObserver.class).update());
    }

    public String getLastKnownIp() {
        return this.lastKnownIp;
    }

    public LifeManager getLifeManager() {
        return this.lifeManager;
    }

    public BanManager getBanManager() {
        return this.banManager;
    }

    public int getLives() {
        return this.lifeManager.getLives();
    }

    public void setLives(int lives) {
        this.lifeManager.setLives(lives);
        this.observers.forEach((key, value) -> value.get(MyStatsLivesObserver.class).update());
    }

    public OfflinePlayer getPlayer() {
        return player;
    }

    public int getLifeParts() {
        return this.lifeManager.getLifeParts();
    }

    public void setLifeParts(int lifeParts) {
        this.lifeManager.setLifeParts(lifeParts);
        this.observers.forEach((key, value) -> value.get(MyStatsLifePartsObserver.class).update());
    }

    public NavigableMap<Integer, Ban> getBans() {
        return this.banManager.getBans();
    }

    public int getBanCount() {
        return this.banManager.getBanCount();
    }

    private void decreaseLives(int amount) {
        this.lifeManager.decreaseLives(amount);
        this.observers.forEach((key, value) -> value.get(MyStatsLivesObserver.class).update());
    }

    public void increaseLives(int amount) {
        this.lifeManager.increaseLives(amount);
        this.observers.forEach((key, value) -> value.get(MyStatsLivesObserver.class).update());
    }

    private void decreaseLifeParts(int amount) {
        this.lifeManager.decreaseLifeParts(amount);
        this.observers.forEach((key, value) -> value.get(MyStatsLifePartsObserver.class).update());
    }

    public void increaseLifeParts(int amount) {
        this.lifeManager.increaseLifeParts(amount);
        this.observers.forEach((key, value) -> value.get(MyStatsLifePartsObserver.class).update());
    }

    public void onDeath(PlayerDeathEvent event, Player player) {
        if (this.isSpectatorBanned()) {
            return;
        }

        if (this.plugin.getConfigurations().getMiscellaneousConfiguration().isLightningOnDeath()) {
            Bukkit.getScheduler().runTask(this.plugin, () -> player.getWorld().strikeLightningEffect(player.getLocation()));
        }

        if (!this.plugin.getConfigurations().getMiscellaneousConfiguration().getCommandsOnDeath().isEmpty() && this.player.getName() != null) {
            this.plugin.getConfigurations().getMiscellaneousConfiguration().getCommandsOnDeath().forEach(e -> Bukkit.getScheduler().runTask(this.plugin, () -> Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), e.replaceAll("%player%", this.player.getName()))));
        }

        this.loseLives(this.plugin.getConfigurations().getLivesAndLifePartsConfiguration().getLivesLostPerDeath(), player);

        this.loseMaxHealth(this.plugin.getConfigurations().getMaxHealthConfiguration().getMaxHealthDecreasePerDeath(), player);

        if (this.plugin.getConfigurations().getLivesAndLifePartsConfiguration().isUseLives()) {
            if (this.getLives() == 0) {
                this.loseLifeParts(this.plugin.getConfigurations().getLivesAndLifePartsConfiguration().getLifePartsLostPerDeathBan(), player);
                this.ban(event, player);
            } else {
                this.loseLifeParts(this.plugin.getConfigurations().getLivesAndLifePartsConfiguration().getLifePartsLostPerDeath(), player);
            }
        } else {
            this.loseLifeParts(this.plugin.getConfigurations().getLivesAndLifePartsConfiguration().getLifePartsLostPerDeath(), player);
            this.ban(event, player);
        }

        this.unCombatTag();

        this.respawn(player);

        this.lastDeath = LocalDateTime.now();

        this.plugin.getPlayerRepository().updatePlayerData(this);
    }

    private void respawn(Player player) {
        if (!player.isDead()) {
            return;
        }

        if (!this.plugin.getConfigurations().getMiscellaneousConfiguration().isDeathScreen()) {
            Bukkit.getScheduler().runTaskLater(this.plugin, () -> player.spigot().respawn(), 1L);
        }
    }

    private void loseMaxHealth(double amount, Player player) {
        if (!this.plugin.getConfigurations().getMaxHealthConfiguration().isUseMaxHealth()) {
            return;
        }

        if (player.hasPermission(Permission.BYPASS_LOSEMAXHEALTH.getPermissionString())) {
            return;
        }

        if (this.plugin.getConfigurations().getMaxHealthConfiguration().getDisableLosingMaxHealthInWorlds().contains(player.getWorld().getName().toLowerCase())) {
            return;
        }

        this.decreaseMaxHealth(amount, player);
    }

    private void decreaseMaxHealth(double amount, Player player) {
        PlayerUtils.setMaxHealth(player, PlayerUtils.getMaxHealth(player) - amount);
        this.observers.forEach((key, value) -> value.get(MyStatsMaxHealthObserver.class).update());
    }

    private void increaseMaxHealth(double amount, Player player) {
        PlayerUtils.setMaxHealth(player, PlayerUtils.getMaxHealth(player) + amount);
        this.observers.forEach((key, value) -> value.get(MyStatsMaxHealthObserver.class).update());
    }

    private void loseLives(int amount, Player player) {
        if (!this.plugin.getConfigurations().getLivesAndLifePartsConfiguration().isUseLives()) {
            return;
        }

        //check if permission to bypass
        if (player.hasPermission(Permission.BYPASS_LOSELIVES.getPermissionString())) {
            return;
        }

        //check if losing lives is limited to specific worlds
        //If EnableLosingLivesInWorlds is empty, losing lives is enabled in all worlds.
        //If it has entries, players only lose lives in those listed worlds.
        List<String> enabledWorlds = this.plugin.getConfigurations().getLivesAndLifePartsConfiguration().getEnableLosingLivesInWorlds();
        if (!enabledWorlds.isEmpty() && !enabledWorlds.contains(player.getWorld().getName().toLowerCase())) {
            return;
        }

        //lose lives
        this.decreaseLives(amount);
    }

    private void loseLifeParts(int amount, Player player) {
        if (!this.plugin.getConfigurations().getLivesAndLifePartsConfiguration().isUseLifeParts()) {
            return;
        }

        //check if permission to bypass
        if (player.hasPermission(Permission.BYPASS_LOSELIFEPARTS.getPermissionString())) {
            return;
        }

        //check if in disabled world
        if (this.plugin.getConfigurations().getLivesAndLifePartsConfiguration().getDisableLosingLifePartsInWorlds().contains(player.getWorld().getName().toLowerCase())) {
            return;
        }

        //lose life parts
        this.decreaseLifeParts(amount);
    }

    private void ban(PlayerDeathEvent event, Player player) {
        if (!this.plugin.getConfigurations().getDeathBanConfiguration().isUseDeathBan()) {
            return;
        }

        //Check if permission to bypass
        if (player.hasPermission(Permission.BYPASS_BAN.getPermissionString())) {
            return;
        }

        //check if in disabled world
        if (this.plugin.getConfigurations().getDeathBanConfiguration().getDisableBanInWorlds().contains(player.getWorld().getName().toLowerCase())) {
            return;
        }

        EntityDamageEvent damageEvent = event.getEntity().getLastDamageCause();

        DamageCause cause = EventUtils.getDamageCauseFromDamageEvent(this, damageEvent);
        if (cause == null) {
            return;
        }

        Killer killer = this.isReviving() ? this.getReviving() : EventUtils.getDamageEventKiller(damageEvent), tagger = this.getCombatTagger();
        if (killer != null && killer.equals(tagger)) {
            tagger = null;
        }

        Ban ban = EventUtils.getDeathBan(player, this, cause, killer, tagger, event.getDeathMessage(), EventUtils.getDamageCauseTypeFromEntityDamageEvent(damageEvent));

        //check if not killed due to self harm if disabled
        if (!this.plugin.getConfigurations().getDeathBanConfiguration().isSelfHarmBan() && ban.getKiller() != null && ban.getKiller().getName().equals(player.getName())) {
            return;
        }

        //always store a death record, even when it does not trigger an active death ban
        if (ban.getBanTime() == 0) {
            ban.setNullified(true);
            BanEntry entry = this.addBan(ban);
            this.plugin.getServerRepository().removeBanFromServerData(this.player.getUniqueId(), entry);
            this.plugin.getPlayerRepository().updatePlayerData(this);
            return;
        }

        if (this.plugin.getConfigurations().getDeathBanConfiguration().isLightningOnDeathBan() && !this.plugin.getConfigurations().getMiscellaneousConfiguration().isLightningOnDeath()) {
            Bukkit.getScheduler().runTask(this.plugin, () -> player.getWorld().strikeLightningEffect(player.getLocation()));
        }

        if (!this.plugin.getConfigurations().getDeathBanConfiguration().getCommandsOnDeathBan().isEmpty() && this.player.getName() != null) {
            this.plugin.getConfigurations().getDeathBanConfiguration().getCommandsOnDeathBan().forEach(e -> Bukkit.getScheduler().runTask(this.plugin, () -> Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), e.replaceAll("%player%", this.player.getName()))));
        }

        this.plugin.getServerRepository().getServerData(this.plugin.getServer()).thenAcceptAsync(serverData -> serverData.addBan(this, player, this.addBan(ban))).exceptionally(e -> {
            this.plugin.getLogger().log(Level.SEVERE, "Error processing death ban.", e);
            return null;
        });
    }

    private BanEntry addBan(Ban ban) {
        BanEntry entry = this.banManager.addBan(ban);
        this.observers.forEach((k, value) -> value.get(MyStatsDeathBansObserver.class).update());
        return entry;
    }

    public void onRespawn(Player player) {
        if (this.isSpectatorBanned()) {
            return;
        }

        if (this.plugin.getConfigurations().getLivesAndLifePartsConfiguration().isUseLives()) {
            if (this.getLives() != 0) {
                return;
            }

            this.setLives(this.plugin.getConfigurations().getLivesAndLifePartsConfiguration().getLivesAfterBan());
        }

        if (this.plugin.getConfigurations().getLivesAndLifePartsConfiguration().getLifePartsAfterBan() != -1) {
            this.setLifeParts(this.plugin.getConfigurations().getLivesAndLifePartsConfiguration().getLifePartsAfterBan());
        }

        if (this.plugin.getConfigurations().getMaxHealthConfiguration().isUseMaxHealth() && this.plugin.getConfigurations().getMaxHealthConfiguration().getMaxHealthAfterBan() != -1) {
            PlayerUtils.setMaxHealth(player, this.plugin.getConfigurations().getMaxHealthConfiguration().getMaxHealthAfterBan());
            Bukkit.getScheduler().runTask(this.plugin, () -> player.setHealth(PlayerUtils.getMaxHealth(player)));
        }

        this.plugin.getPlayerRepository().updatePlayerData(this);
    }

    public void onEntityKill(EntityType type, Player player) {
        if (this.isSpectatorBanned()) {
            return;
        }

        //gain life parts on kill
        this.onLifePartsEntityKill(type, player);

        //gain max health on kill
        this.onMaxHealthEntityKill(type, player);

        this.plugin.getPlayerRepository().updatePlayerData(this);
    }

    private void onLifePartsEntityKill(EntityType type, Player player) {
        if (!this.plugin.getConfigurations().getLivesAndLifePartsConfiguration().isUseLifeParts()) {
            return;
        }

        if (!this.plugin.getConfigurations().getLivesAndLifePartsConfiguration().isLifePartsOnKill()) {
            return;
        }

        //check if permission to gain life parts for kill
        if (player.hasPermission(Permission.BYPASS_GAINLIFEPARTS_KILL.getPermissionString())) {
            return;
        }

        //gain life parts
        this.gainLifeParts(this.plugin.getConfigurations().getLivesAndLifePartsConfiguration().getLifePartsPerKill().getOrDefault(type, 0), player);
    }

    private void onMaxHealthEntityKill(EntityType type, Player player) {
        if (!this.plugin.getConfigurations().getMaxHealthConfiguration().isUseMaxHealth()) {
            return;
        }

        if (!this.plugin.getConfigurations().getMaxHealthConfiguration().isMaxHealthIncreaseOnKill()) {
            return;
        }

        //check if permission to gain max health for kill
        if (player.hasPermission(Permission.BYPASS_GAINMAXHEALTH_KILL.getPermissionString())) {
            return;
        }

        //increase max health
        this.gainMaxHealth(this.plugin.getConfigurations().getMaxHealthConfiguration().getMaxHealthIncreasePerKill().getOrDefault(type, 0D), player);
    }

    void gainLifeParts(int amount, Player player) {
        //check if in disabled world
        if (this.plugin.getConfigurations().getLivesAndLifePartsConfiguration().getDisableGainingLifePartsInWorlds().contains(player.getWorld().getName().toLowerCase())) {
            return;
        }

        this.increaseLifeParts(amount);
    }

    void gainMaxHealth(double amount, Player player) {
        //check if in disabled world
        if (this.plugin.getConfigurations().getMaxHealthConfiguration().getDisableGainingMaxHealthInWorlds().contains(player.getWorld().getName().toLowerCase())) {
            return;
        }

        this.increaseMaxHealth(amount, player);
    }

    public void onCombatTag(Killer tagger, Player player) {
        // 1) Spectator or disabled → do nothing
        if (isSpectatorBanned()) return;
        if (!plugin.getConfigurations().getCombatTagConfiguration().isUseCombatTag()) return;

        // 2) Null or self-tagging disabled → do nothing
        if (tagger == null) return;
        if (!plugin.getConfigurations().getCombatTagConfiguration().isCombatTagSelf()
                && tagger.getName().equals(player.getName())) return;

        // 3) Disabled world → do nothing
        String worldName = player.getWorld().getName().toLowerCase();
        if (plugin.getConfigurations().getCombatTagConfiguration()
                .getDisableCombatTagInWorlds()
                .contains(worldName)) {
            return;
        }

        // 4) Make sure our list exists
        if (combatTag == null) {
            combatTag = new ArrayList<>();
        }

        // 5) If we already have tags, restart them—filtering out and logging any nulls
        if (!combatTag.isEmpty()) {
            Iterator<AbstractCombatTag> it = combatTag.iterator();
            while (it.hasNext()) {
                AbstractCombatTag tag = it.next();
                if (tag != null) {
                    tag.restart(tagger);
                } else {
                    plugin.getLogger().warning(
                            "[CombatTag] Found and removed null entry for player " + player.getName()
                    );
                    it.remove();
                }
            }
        }
        // 6) Otherwise, create and start new tags
        else {
            combatTag = plugin.getMessages()
                    .getCombatTagNotificationsConfiguration(player, this, tagger);
            Iterator<AbstractCombatTag> it = combatTag.iterator();
            while (it.hasNext()) {
                AbstractCombatTag tag = it.next();
                if (tag != null) {
                    tag.start();
                } else {
                    plugin.getLogger().warning(
                            "[CombatTag] Dropped null new‐tag for player " + player.getName()
                    );
                    it.remove();
                }
            }
        }
    }

    public void removeFromCombatTag(AbstractCombatTag tag) {
        this.combatTag.remove(tag);
    }

    public void unCombatTag() {
        if (!this.combatTag.isEmpty()) {
            new ArrayList<>(this.combatTag).forEach(AbstractCombatTag::stop);
        }
    }

    public void onJoin(Player player) {
        //get rid of death screen after death ban if disabled
        this.respawn(player);

        //if spectator banned, check if still banned or still in spectator
        if (this.isSpectatorBanned()) {
            if (!this.plugin.getServerRepository().getServerDataSync().isDeathBanned(this.player.getUniqueId())) {
                this.resetSpectatorDeathBan(player);
            } else if (player.getGameMode() != GameMode.SPECTATOR) {
                Bukkit.getScheduler().runTask(this.plugin, () -> player.setGameMode(GameMode.SPECTATOR));
            }
        }

        if (player.getHealth() != 0D) {
            //fix visual bug from Minecraft when max health is higher than 20
            Bukkit.getScheduler().runTask(this.plugin, () -> player.setHealth(player.getHealth()));
        }

        this.startPlaytimeRunnables(player);
    }

    public void startPlaytimeRunnables(Player player) {
        this.stopPlaytimeRunnables();

        if (this.plugin.getConfigurations().getLivesAndLifePartsConfiguration().isUseLifeParts() && this.plugin.getConfigurations().getLivesAndLifePartsConfiguration().isGetLifePartsByPlaytime() && !this.isSpectatorBanned()) {
            this.playtime.add(new PlaytimeLifePart(this, player));
        }

        if (this.plugin.getConfigurations().getMaxHealthConfiguration().isUseMaxHealth() && this.plugin.getConfigurations().getMaxHealthConfiguration().isGetMaxHealthByPlaytime() && !this.isSpectatorBanned()) {
            this.playtime.add(new PlaytimeMaxHealth(this, player));
        }

        if (this.plugin.getConfigurations().getReviveConfiguration().isUseRevive() && this.plugin.getConfigurations().getReviveConfiguration().getTimeBetweenRevives() > 0 && !this.isSpectatorBanned() && !player.hasPermission(Permission.BYPASS_REVIVECOOLDOWN.getPermissionString())) {
            if (!this.plugin.getConfigurations().getReviveConfiguration().getGlobalReviveUnDeathBanConfiguration().isEnabled()) {
                this.playtime.add(new PlaytimeRevive(this, player));
            }
        }

        this.playtime.forEach(AbstractPlaytime::start);
    }

    public void resetSpectatorDeathBan(Player player) {
        if (!this.isSpectatorBanned()) {
            return;
        }

        this.setSpectatorBanned(false);

        if (player.getGameMode() == GameMode.SPECTATOR) {
            Bukkit.getScheduler().runTask(this.plugin, () -> player.setGameMode(GameMode.SURVIVAL));
        }

        Location location = player.getBedSpawnLocation();
        World respawnWorld = Bukkit.getWorld(this.plugin.getConfigurations().getDeathBanConfiguration().getSpectatorBanRespawnWorld());
        Bukkit.getScheduler().runTask(this.plugin, () -> player.teleport(location != null ? location : respawnWorld != null ? respawnWorld.getSpawnLocation() : Bukkit.getWorlds().get(0).getSpawnLocation()));

        //run onRespawn to set player data to as if they just respawned
        this.onRespawn(player);

        this.startPlaytimeRunnables(player);
    }

    public void decreaseTimeTillNextLifePart(int amount, Player player) {
        this.playtimeTracker.decreaseTimeTillNextLifePart(amount, player);
        this.observers.forEach((key, value) -> value.get(MyStatsTimeTillNextLifePartObserver.class).update());
    }

    public void decreaseTimeTillNextMaxHealth(int amount, Player player) {
        this.playtimeTracker.decreaseTimeTillNextMaxHealth(amount, player);
        this.observers.forEach((key, value) -> value.get(MyStatsTimeTillNextMaxHealthObserver.class).update());
    }

    public void decreaseTimeTillNextRevive(int amount) {
        this.playtimeTracker.decreaseTimeTillNextRevive(amount);
        this.observers.forEach((key, value) -> value.get(MyStatsTimeTillNextReviveObserver.class).update());
    }

    public long getTimeTillNextMaxHealth() {
        return this.playtimeTracker.getTimeTillNextMaxHealth();
    }

    private void setTimeTillNextMaxHealth(long timeTillNextMaxHealth) {
        this.playtimeTracker.setTimeTillNextMaxHealth(timeTillNextMaxHealth);
        this.observers.forEach((key, value) -> value.get(MyStatsTimeTillNextMaxHealthObserver.class).update());
    }

    public void onLeave(Player player) {
        this.stopPlaytimeRunnables();

        if (this.plugin.getConfigurations().getCombatTagConfiguration().isUseCombatTag()) {
            if (this.isCombatTagged() && !this.plugin.getConfigurations().getCombatTagConfiguration().isCombatTagPlayerKickDeath() && this.kicked) {
                this.unCombatTag();
                this.kicked = false;
            } else if (this.isCombatTagged()) {
                this.combatLogged = true;
                player.setHealth(0.0D);
            }
        }

        this.plugin.getPlayerRepository().updatePlayerData(this);
        this.plugin.getPlayerRepository().removeFromPlayerCache(player);
    }

    public void stopPlaytimeRunnables() {
        if (!this.playtime.isEmpty()) {
            this.playtime.forEach(AbstractPlaytime::stop);
            this.playtime.clear();
        }
    }

    public void onReviving(PlayerData revivingData, Player reviverPlayer) {
        if (!this.checkRevivePermissionsReviver(revivingData.getPlayer(), reviverPlayer)) {
            return;
        }

        if (!this.checkRevivePermissionsReviving(revivingData, reviverPlayer)) {
            return;
        }

        this.plugin.getServerRepository().getServerData(this.plugin.getServer()).thenAcceptAsync(serverData -> {
            if (revivingData.getLives() == 0) {
                if (!reviverPlayer.hasPermission(Permission.GAIN_REVIVE_DEATH.getPermissionString())) {
                    reviverPlayer.sendMessage("§cYou don't have the right permission to give lives to a dead banned player.");
                    return;
                }

                if (!serverData.unDeathBan(revivingData.getPlayer().getUniqueId())) {
                    reviverPlayer.sendMessage(String.format("%s is not death banned and cannot be given a life right now.", revivingData.getPlayer().getName()));
                    return;
                }
            } else {
                if (!reviverPlayer.hasPermission(Permission.GAIN_REVIVE_ALIVE.getPermissionString())) {
                    reviverPlayer.sendMessage("§cYou don't have the right permission to give lives to an alive player.");
                    return;
                }
            }
            revivingData.onRevive(reviverPlayer);

            if (!this.plugin.getConfigurations().getReviveConfiguration().getGlobalReviveUnDeathBanConfiguration().isEnabled()) {
                this.setTimeTillNextRevive(this.plugin.getConfigurations().getReviveConfiguration().getTimeBetweenRevives());
            }

            int amount = this.plugin.getConfigurations().getReviveConfiguration().getLivesLostOnReviving();
            this.decreaseLives(amount);
            if (this.getLives() <= 0) {
                this.reviving = new Killer(revivingData.getPlayer().getName(), revivingData.getPlayer().getPlayer() == null ? null : revivingData.getPlayer().getPlayer().getDisplayName(), EntityType.PLAYER);
                Bukkit.getScheduler().runTask(this.plugin, () -> reviverPlayer.setHealth(0D));
            } else {
                reviverPlayer.sendMessage(String.format("§aSuccessfully given §e%s§a to §e%s§a, you have §e%s §aleft.", amount + (amount > 1 ? " lives" : " life"), revivingData.getPlayer().getName(), this.getLives() + (this.getLives() > 1 ? " lives" : " life")));
            }

            this.plugin.getPlayerRepository().updatePlayerData(this);
        });
    }

    public boolean checkRevivePermissionsReviving(PlayerData revivingData, Player player) {
        //check if player already on max lives
        if (revivingData.getLives() == this.plugin.getConfigurations().getLivesAndLifePartsConfiguration().getMaxLives()) {
            player.sendMessage(String.format("§c%s already has the maximum amount of lives.", revivingData.getPlayer().getName()));
            return false;
        }
        return true;
    }

    public boolean checkRevivePermissionsReviver(OfflinePlayer reviving, Player player) {
        if (this.isSpectatorBanned()) {
            player.sendMessage("§cYou cannot revive while spectator death banned.");
            return false;
        }

        //check if reviving is enabled
        if (!this.plugin.getConfigurations().getReviveConfiguration().isUseRevive()) {
            player.sendMessage("§cReviving is not enabled on the server.");
            return false;
        }

        //check if permission
        if (!player.hasPermission(Permission.REVIVE.getPermissionString())) {
            return false;
        }

        //check if not same player
        if (player.getUniqueId().equals(reviving.getUniqueId())) {
            player.sendMessage("§cYou cannot revive yourself, that would break the space-time continuum!");
            return false;
        }

        //check if in disabled world
        if (this.plugin.getConfigurations().getReviveConfiguration().getDisableReviveInWorlds().contains(player.getWorld().getName().toLowerCase())) {
            player.sendMessage(String.format("§cYou cannot revive %s while in this world (%s).", reviving.getName(), player.getWorld().getName()));
            return false;
        }

        //check if enough lives left to revive
        int amount = this.plugin.getConfigurations().getReviveConfiguration().getLivesLostOnReviving();
        if (this.getLives() < amount) {
            player.sendMessage(String.format("§cYou'll need %s in order to revive %s, you currently have %s.", amount + (amount > 1 ? " lives" : " life"), reviving.getName(), this.getLives() + (this.getLives() > 1 ? " lives" : " life")));
            return false;
        }

        //check if revive on cooldown
        if (this.getTimeTillNextRevive() > 0 && !player.hasPermission(Permission.BYPASS_REVIVECOOLDOWN.getPermissionString())) {
            player.sendMessage(String.format("§cYou cannot revive %s for another %s.", reviving.getName(), MessageUtils.getTimeFromTicks(this.getTimeTillNextRevive(), TimePattern.LONG)));
            return false;
        }

        return true;
    }

    public void onRevive(Player reviver) {
        if (!this.plugin.getConfigurations().getReviveConfiguration().isUseRevive()) {
            return;
        }

        int amount = this.plugin.getConfigurations().getReviveConfiguration().getLivesGainedOnRevive();
        this.increaseLives(amount);
        if (this.player.getPlayer() != null) {
            this.player.getPlayer().sendMessage(String.format("§a%s has successfully given you §e%s§a, you now have §e%s§a.", reviver.getName(), amount + (amount > 1 ? " lives" : " life"), this.getLives() + (this.getLives() > 1 ? " lives" : " life")));
        }

        this.plugin.getPlayerRepository().updatePlayerData(this);
    }

    public long getTimeTillNextLifePart() {
        return this.playtimeTracker.getTimeTillNextLifePart();
    }

    private void setTimeTillNextLifePart(long timeTillNextLifePart) {
        this.playtimeTracker.setTimeTillNextLifePart(timeTillNextLifePart);
        this.observers.forEach((key, value) -> value.get(MyStatsTimeTillNextLifePartObserver.class).update());
    }

    public void onReload(Player player) {
        this.onJoin(player);
    }

    public void resetReviveCooldown() {
        this.setTimeTillNextRevive(0L);
    }

    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();

        Map<Object, Object> cBans = new HashMap<>();
        this.banManager.getBans().forEach((key, value) -> cBans.put(key, value.serialize()));

        map.put("Bans", cBans);
        map.put("TimeTillNextMaxHealth", this.getTimeTillNextMaxHealth());
        map.put("TimeTillNextLifePart", this.getTimeTillNextLifePart());
        map.put("SpectatorBanned", this.spectatorBanned);
        map.put("TimeTillNextRevive", this.getTimeTillNextRevive());
        map.put("LifeParts", this.getLifeParts());
        map.put("Lives", this.getLives());
        map.put("LastDeath", this.lastDeath.toString());
        map.put("LastKnownIp", this.lastKnownIp);
        map.put("LastKnownName", this.player.getName());

        return map;
    }

    public LocalDateTime getLastDeath() {
        return lastDeath;
    }

    public void onKick() {
        this.kicked = true;
    }

    public boolean isReviving() {
        return this.reviving != null;
    }

    public Killer getReviving() {
        return reviving;
    }

    public Ban getLastDeathBan() {
        return this.banManager.getLastDeathBan();
    }

    public void registerObserver(Player player, AbstractGui gui) {
        Map<Class<?>, IObserver> observers = new HashMap<>();
        if (gui instanceof GuiMyStats) {
            GuiMyStats guiMyStats = (GuiMyStats) gui;
            Arrays.asList(
                    new MyStatsDeathBansObserver(guiMyStats),
                    new MyStatsLifePartsObserver(guiMyStats),
                    new MyStatsLivesObserver(guiMyStats),
                    new MyStatsMaxHealthObserver(guiMyStats),
                    new MyStatsTimeTillNextLifePartObserver(guiMyStats),
                    new MyStatsTimeTillNextMaxHealthObserver(guiMyStats),
                    new MyStatsTimeTillNextReviveObserver(guiMyStats)
            ).forEach(e -> observers.put(e.getClass(), e));
        }
        if (!observers.isEmpty()) {
            this.observers.put(player.getUniqueId(), observers);
        }
    }

    public void unregisterObserver(Player player) {
        this.observers.remove(player.getUniqueId());
    }

    public boolean isSpectatorBanned() {
        return this.spectatorBanned;
    }

    public void setSpectatorBanned(boolean spectatorBanned) {
        this.spectatorBanned = spectatorBanned;
    }

    public void reset() {
        this.setLives(this.plugin.getConfigurations().getLivesAndLifePartsConfiguration().getLivesAtStart());
        this.setLifeParts(this.plugin.getConfigurations().getLivesAndLifePartsConfiguration().getLifePartsAtStart());
        this.setTimeTillNextRevive(this.plugin.getConfigurations().getReviveConfiguration().isReviveOnFirstJoin() ? 0L : this.plugin.getConfigurations().getReviveConfiguration().getTimeBetweenRevives());
        this.setTimeTillNextLifePart(this.plugin.getConfigurations().getLivesAndLifePartsConfiguration().getPlaytimePerLifePart());
        this.setTimeTillNextMaxHealth(this.plugin.getConfigurations().getMaxHealthConfiguration().getPlaytimePerHalfHeart());
        this.banManager.clearBans();
        this.plugin.getServerRepository().getServerData(this.plugin.getServer()).thenAcceptAsync(serverData -> serverData.getBan(this.player.getUniqueId()).finish());
        this.plugin.getPlayerRepository().deletePlayerData(this.player);
        this.plugin.getPlayerRepository().updatePlayerData(this);
    }

    public boolean isCombatLogged() {
        return combatLogged;
    }

    public void setCombatLogged(boolean combatLogged) {
        this.combatLogged = combatLogged;
    }

    public boolean isCombatTagged() {
        return this.combatTagger != null;
    }

    public Killer getCombatTagger() {
        return combatTagger;
    }

    public void setCombatTagger(Killer combatTagger) {
        this.combatTagger = combatTagger;
    }
}
