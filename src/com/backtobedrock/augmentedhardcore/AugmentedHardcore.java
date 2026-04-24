package com.backtobedrock.augmentedhardcore;

import com.backtobedrock.augmentedhardcore.commands.Commands;
import com.backtobedrock.augmentedhardcore.configs.Configurations;
import com.backtobedrock.augmentedhardcore.configs.Messages;
import com.backtobedrock.augmentedhardcore.domain.data.ServerData;
import com.backtobedrock.augmentedhardcore.domain.enums.StorageType;
import com.backtobedrock.augmentedhardcore.eventListeners.*;
import com.backtobedrock.augmentedhardcore.eventListeners.dependencies.ListenerCombatLogX;
import com.backtobedrock.augmentedhardcore.guis.AbstractGui;
import com.backtobedrock.augmentedhardcore.guis.GuiMyStats;
import com.backtobedrock.augmentedhardcore.mappers.Patch;
import com.backtobedrock.augmentedhardcore.mappers.player.patches.LastDeathAdditionPatch;
import com.backtobedrock.augmentedhardcore.repositories.PlayerRepository;
import com.backtobedrock.augmentedhardcore.repositories.ServerRepository;
import com.backtobedrock.augmentedhardcore.runnables.UpdateChecker;
import com.backtobedrock.augmentedhardcore.utilities.Metrics;
import com.backtobedrock.augmentedhardcore.utilities.placeholderAPI.PlaceholdersAugmentedHardcore;
import com.tchristofferson.configupdater.ConfigUpdater;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class AugmentedHardcore extends JavaPlugin implements Listener {

    //various
    private final Map<Class<?>, AbstractEventListener> activeEventListeners = new HashMap<>();
    private final Map<UUID, AbstractGui> openGuis = new HashMap<>();
    private boolean stopping = false;

    //configurations
    private Commands commands;
    private Configurations configurations;
    private Messages messages;

    //repositories
    private PlayerRepository playerRepository;
    private ServerRepository serverRepository;

    //runnables
    private UpdateChecker updateChecker;

    //async executor
    private ExecutorService executor;
    private final ThreadFactory asyncThreadFactory = new ThreadFactory() {
        private final AtomicInteger count = new AtomicInteger(1);
        private final ThreadFactory backingFactory = Executors.defaultThreadFactory();

        @Override
        public Thread newThread(@NotNull Runnable r) {
            Thread thread = backingFactory.newThread(r);
            thread.setName(String.format("AugHC-Async-%d", count.getAndIncrement()));
            thread.setDaemon(true);
            return thread;
        }
    };

    @Override
    public void onEnable() {
        this.initialize();

        //update checker
        this.updateChecker = new UpdateChecker();
        this.updateChecker.start();

        //bstats metrics
        Metrics metrics = new Metrics(this, 10843);
        metrics.addCustomChart(new Metrics.SingleLineChart("currently_ongoing_death_bans", () -> (Integer) this.serverRepository.getServerDataSync().getTotalOngoingBans()));
        metrics.addCustomChart(new Metrics.SingleLineChart("total_death_bans", () -> (Integer) this.serverRepository.getServerDataSync().getTotalDeathBans()));

        //PAPI
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlaceholdersAugmentedHardcore().register();
        }

        super.onEnable();
    }

    @Override
    public void onDisable() {
        this.stopping = true;

        if (this.getServerRepository() != null) {
            ServerData serverData = this.getServerRepository().getServerDataSync();
            if (serverData != null) {
                this.getServerRepository().updateServerData(serverData).join();
            }
        }

        if (this.updateChecker != null) {
            this.updateChecker.stop();
        }

        if (this.executor != null) {
            this.executor.shutdown();
            try {
                if (!this.executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    this.getLogger().log(Level.WARNING, "Executor did not terminate in the allotted time.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (this.getConfigurations() != null && this.getConfigurations().getDataConfiguration().getDatabase() != null) {
            this.getConfigurations().getDataConfiguration().getDatabase().close();
        }

        super.onDisable();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender cs, @NotNull Command cmnd, @NotNull String alias, String[] args) {
        return this.commands.onCommand(cs, cmnd, alias, args);
    }

    public void initialize() {
        if (this.executor == null || this.executor.isShutdown()) {
            this.executor = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors()), this.asyncThreadFactory);
        }

        this.prepareConfigFiles();
        this.loadConfigurations();
        this.setupDatabase();
        this.initRepositories();
        this.registerListeners();
    }

    private void prepareConfigFiles() {
        File dir = new File(this.getDataFolder() + "/old");
        List<File> configs = List.of(
                new File(getDataFolder(), "config.old.yml"),
                new File(getDataFolder(), "messages.old.yml")
        );
        //noinspection ResultOfMethodCallIgnored
        dir.mkdir();
        //noinspection ResultOfMethodCallIgnored
        configs.stream().filter(File::exists).forEach(File::delete);

        File configFile = new File(this.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            this.getLogger().log(Level.INFO, "Creating {0}.", configFile.getAbsolutePath());
            this.saveResource("config.yml", false);
        }

        File messagesFile = new File(this.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            this.getLogger().log(Level.INFO, "Creating {0}.", messagesFile.getAbsolutePath());
            this.saveResource("messages.yml", false);
        }

        try {
            File copy = new File(this.getDataFolder() + "/old/", "config.old.yml");
            if (copy.exists()) {
                //noinspection ResultOfMethodCallIgnored
                copy.delete();
            }
            Files.copy(configFile.toPath(), copy.toPath());
            ConfigUpdater.update(this, "config.yml", configFile, Arrays.asList("LifePartsPerKill", "MaxHealthIncreasePerKill"));
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Failed to update config.yml", e);
        }

        try {
            File copy = new File(this.getDataFolder() + "/old/", "messages.old.yml");
            if (copy.exists()) {
                //noinspection ResultOfMethodCallIgnored
                copy.delete();
            }
            Files.copy(messagesFile.toPath(), copy.toPath());
            ConfigUpdater.update(this, "messages.yml", messagesFile, Collections.emptyList());
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Failed to update messages.yml", e);
        }
    }

    private void loadConfigurations() {
        File configFile = new File(this.getDataFolder(), "config.yml");
        File messagesFile = new File(this.getDataFolder(), "messages.yml");
        this.configurations = new Configurations(configFile);
        this.messages = new Messages(messagesFile);
        this.commands = new Commands();
    }

    private void setupDatabase() {
        if (this.getConfigurations().getDataConfiguration().getStorageType() != StorageType.MYSQL) {
            return;
        }

        String setup = "";
        try (InputStream in = getClassLoader().getResourceAsStream("dbsetup.sql")) {
            setup = new BufferedReader(new InputStreamReader(in)).lines().collect(Collectors.joining());
        } catch (IOException | NullPointerException e) {
            getLogger().log(Level.SEVERE, "Could not read db setup file.", e);
        }
        String[] queries = setup.split(";");
        for (String query : queries) {
            query = query.trim();
            if (query.isEmpty()) {
                continue;
            }
            try (Connection conn = this.getConfigurations().getDataConfiguration().getDatabase().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.execute();

            } catch (SQLException e) {
                getLogger().log(Level.SEVERE, "Error executing db setup query.", e);
            }
        }

        //PATCHES
        Arrays.asList(
                new LastDeathAdditionPatch()
        ).forEach(Patch::executePatch);

        getLogger().info("Setup complete.");
    }

    private void initRepositories() {
        if (this.playerRepository == null) {
            this.playerRepository = new PlayerRepository(this);
        } else {
            this.playerRepository.onReload();
        }
        if (this.serverRepository == null) {
            this.serverRepository = new ServerRepository(this);
        } else {
            this.serverRepository.onReload();
        }
    }

    private void registerListeners() {
        Arrays.asList(
                //dependencies
                new ListenerCombatLogX(),
                //internal
                new ListenerCustomInventory(),
                new ListenerEntityDeath(),
                new ListenerPlayerDamageByEntity(),
                new ListenerPlayerDeath(),
                new ListenerPlayerGameModeChange(),
                new ListenerPlayerJoin(),
                new ListenerPlayerKick(),
                new ListenerPlayerLogin(),
                new ListenerPlayerQuit(),
                new ListenerPlayerRegainHealth(),
                new ListenerPlayerRespawn()
        ).forEach(e ->
                {
                    if (this.activeEventListeners.containsKey(e.getClass())) {
                        AbstractEventListener listener = this.activeEventListeners.get(e.getClass());
                        if (!listener.isEnabled()) {
                            HandlerList.unregisterAll(listener);
                            this.activeEventListeners.remove(listener.getClass());
                        }
                    } else if (e.isEnabled()) {
                        getServer().getPluginManager().registerEvents(e, this);
                        this.activeEventListeners.put(e.getClass(), e);
                    }
                }
        );
    }

    public Configurations getConfigurations() {
        return configurations;
    }

    public Messages getMessages() {
        return messages;
    }

    public PlayerRepository getPlayerRepository() {
        return playerRepository;
    }

    public ServerRepository getServerRepository() {
        return serverRepository;
    }

    public boolean isStopping() {
        return stopping;
    }

    public void addToGuis(Player player, AbstractGui gui) {
        this.openGuis.put(player.getUniqueId(), gui);
    }

    public void removeFromGuis(Player player) {
        AbstractGui gui = this.openGuis.remove(player.getUniqueId());
        if (gui == null) {
            return;
        }

        if (gui instanceof GuiMyStats) {
            ((GuiMyStats) gui).getPlayerData().unregisterObserver(player);
        }
    }

    public UpdateChecker getUpdateChecker() {
        return updateChecker;
    }

    public ExecutorService getExecutor() {
        return executor;
    }
}
