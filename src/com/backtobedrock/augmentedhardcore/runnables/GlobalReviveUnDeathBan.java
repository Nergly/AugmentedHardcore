package com.backtobedrock.augmentedhardcore.runnables;

import com.backtobedrock.augmentedhardcore.AugmentedHardcore;
import com.backtobedrock.augmentedhardcore.domain.configurationDomain.configurationHelperClasses.GlobalReviveUnDeathBanConfiguration;
import com.backtobedrock.augmentedhardcore.domain.data.ServerData;
import com.backtobedrock.augmentedhardcore.domain.enums.GlobalResetScheduleType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.logging.Level;

public class GlobalReviveUnDeathBan extends BukkitRunnable {
    private final AugmentedHardcore plugin;
    private final GlobalReviveUnDeathBanConfiguration configuration;
    private ZonedDateTime nextRunAt;
    private BukkitTask task;

    public GlobalReviveUnDeathBan() {
        this.plugin = JavaPlugin.getPlugin(AugmentedHardcore.class);
        this.configuration = this.plugin.getConfigurations().getReviveConfiguration().getGlobalReviveUnDeathBanConfiguration();
        this.nextRunAt = computeNextRun(ZonedDateTime.now(this.configuration.getTimezone()));
    }

    public void start() {
        if (!this.configuration.isEnabled()) {
            return;
        }
        this.plugin.getLogger().log(Level.INFO, "GlobalReviveUnDeathBan enabled. Next run scheduled for {0}.", this.nextRunAt);
        this.task = this.runTaskTimer(this.plugin, 1200L, 1200L);
    }

    public void stop() {
        if (this.task != null) {
            this.task.cancel();
            this.task = null;
        }
    }

    public long getTicksUntilNextRun() {
        if (!this.configuration.isEnabled()) {
            return 0L;
        }
        long seconds = Math.max(0L, java.time.Duration.between(ZonedDateTime.now(this.configuration.getTimezone()), this.nextRunAt).getSeconds());
        return seconds * 20L;
    }

    @Override
    public void run() {
        if (!this.configuration.isEnabled()) {
            return;
        }

        ZonedDateTime now = ZonedDateTime.now(this.configuration.getTimezone());
        if (now.isBefore(this.nextRunAt)) {
            return;
        }

        this.plugin.getLogger().log(Level.INFO, "Running GlobalReviveUnDeathBan at {0}.", now);
        this.nextRunAt = computeNextRun(now.plusSeconds(1));

        this.plugin.getServerRepository().getServerData(this.plugin.getServer())
                .thenAccept(serverData -> Bukkit.getScheduler().runTask(this.plugin, () -> {
                    try {
                        this.runGlobalReset(serverData);
                        this.plugin.getLogger().log(Level.INFO, "GlobalReviveUnDeathBan completed. Next run scheduled for {0}.", this.nextRunAt);
                    } catch (Exception ex) {
                        this.plugin.getLogger().log(Level.SEVERE, "GlobalReviveUnDeathBan failed to execute on main thread.", ex);
                    }
                }))
                .exceptionally(ex -> {
                    this.plugin.getLogger().log(Level.SEVERE, "GlobalReviveUnDeathBan failed to load server data.", ex);
                    return null;
                });
    }

    private void runGlobalReset(ServerData serverData) {
        new ArrayList<>(serverData.getOngoingBans().keySet()).forEach(serverData::unDeathBan);

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            this.plugin.getPlayerRepository().getByPlayer(onlinePlayer).thenAcceptAsync(playerData -> {
                if (playerData != null) {
                    playerData.resetReviveCooldown();
                    this.plugin.getPlayerRepository().updatePlayerData(playerData);
                }
            }, this.plugin.getExecutor());
        }
    }

    private ZonedDateTime computeNextRun(ZonedDateTime now) {
        ZoneId zone = this.configuration.getTimezone();
        GlobalResetScheduleType scheduleType = this.configuration.getScheduleType();
        return switch (scheduleType) {
            case DAILY -> {
                ZonedDateTime candidate = now.with(this.configuration.getTimeOfDay());
                if (!candidate.isAfter(now)) {
                    candidate = candidate.plusDays(1);
                }
                yield candidate;
            }
            case WEEKLY -> {
                ZonedDateTime candidate = now.with(TemporalAdjusters.nextOrSame(this.configuration.getDayOfWeek())).with(this.configuration.getTimeOfDay());
                if (!candidate.isAfter(now)) {
                    candidate = candidate.with(TemporalAdjusters.next(this.configuration.getDayOfWeek())).with(this.configuration.getTimeOfDay());
                }
                yield candidate;
            }
            case MONTHLY -> {
                int day = Math.min(this.configuration.getDayOfMonth(), YearMonth.from(now).lengthOfMonth());
                ZonedDateTime candidate = now.withDayOfMonth(day).with(this.configuration.getTimeOfDay());
                if (!candidate.isAfter(now)) {
                    ZonedDateTime nextMonth = now.plusMonths(1);
                    int nextDay = Math.min(this.configuration.getDayOfMonth(), YearMonth.from(nextMonth).lengthOfMonth());
                    candidate = nextMonth.withDayOfMonth(nextDay).with(this.configuration.getTimeOfDay());
                }
                yield candidate;
            }
            case INTERVAL_HOURS -> now.plusHours(this.configuration.getIntervalHours());
        };
    }
}
