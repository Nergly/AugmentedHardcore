package com.backtobedrock.augmentedhardcore.domain.configurationDomain;

import com.backtobedrock.augmentedhardcore.utilities.ConfigUtils;
import com.backtobedrock.augmentedhardcore.domain.configurationDomain.configurationHelperClasses.GlobalReviveUnDeathBanConfiguration;
import com.backtobedrock.augmentedhardcore.domain.enums.GlobalResetScheduleType;
import com.backtobedrock.augmentedhardcore.AugmentedHardcore;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.OptionalInt;
import java.util.logging.Level;

public class ConfigurationRevive {
    private final boolean useRevive;
    private final int livesLostOnReviving;
    private final int livesGainedOnRevive;
    private final int timeBetweenRevives;
    private final boolean reviveOnFirstJoin;
    private final List<String> disableReviveInWorlds;
    private final GlobalReviveUnDeathBanConfiguration globalReviveUnDeathBanConfiguration;

    public ConfigurationRevive(boolean useRevive, int livesLostOnReviving, int livesGainedOnRevive, int timeBetweenRevives, boolean reviveOnFirstJoin, List<String> disableReviveInWorlds, GlobalReviveUnDeathBanConfiguration globalReviveUnDeathBanConfiguration) {
        this.useRevive = useRevive;
        this.livesLostOnReviving = livesLostOnReviving;
        this.livesGainedOnRevive = livesGainedOnRevive;
        this.timeBetweenRevives = timeBetweenRevives;
        this.reviveOnFirstJoin = reviveOnFirstJoin;
        this.disableReviveInWorlds = disableReviveInWorlds;
        this.globalReviveUnDeathBanConfiguration = globalReviveUnDeathBanConfiguration;
    }

    public static ConfigurationRevive deserialize(ConfigurationSection section) {
        boolean cUseRevive = section.getBoolean("UseRevive", true);
        OptionalInt cLivesLostOnReviving = ConfigUtils.checkMinMax("LivesLostOnReviving", section.getInt("LivesLostOnReviving", 1), 1, Integer.MAX_VALUE);
        OptionalInt cLivesGainedOnRevive = ConfigUtils.checkMinMax("LivesGainedOnRevive", section.getInt("LivesGainedOnRevive", 1), 1, Integer.MAX_VALUE);
        OptionalInt cTimeBetweenRevives = ConfigUtils.checkMinMax("TimeBetweenRevives", section.getInt("TimeBetweenRevives", 1440), 0, Integer.MAX_VALUE);
        boolean cReviveOnFirstJoin = section.getBoolean("ReviveOnFirstJoin", false);
        List<String> cDisableReviveInWorlds = section.getStringList("DisableReviveInWorlds").stream().map(String::toLowerCase).toList();
        GlobalReviveUnDeathBanConfiguration cGlobalReviveUnDeathBanConfiguration = deserializeGlobalReviveUnDeathBan(section.getConfigurationSection("GlobalReviveUnDeathBan"));

        if (cTimeBetweenRevives.isEmpty() || cLivesLostOnReviving.isEmpty() || cLivesGainedOnRevive.isEmpty()) {
            return null;
        }

        return new ConfigurationRevive(
                cUseRevive,
                cLivesLostOnReviving.getAsInt(),
                cLivesGainedOnRevive.getAsInt(),
                cTimeBetweenRevives.getAsInt() * 1200,
                cReviveOnFirstJoin,
                cDisableReviveInWorlds,
                cGlobalReviveUnDeathBanConfiguration
        );
    }

    private static GlobalReviveUnDeathBanConfiguration deserializeGlobalReviveUnDeathBan(ConfigurationSection section) {
        if (section == null) {
            return new GlobalReviveUnDeathBanConfiguration(false, GlobalResetScheduleType.DAILY, 24, LocalTime.MIDNIGHT, DayOfWeek.MONDAY, 1, ZoneId.of("UTC"));
        }

        boolean enabled = section.getBoolean("Enabled", false);

        GlobalResetScheduleType scheduleType;
        try {
            scheduleType = GlobalResetScheduleType.valueOf(section.getString("ScheduleType", "DAILY").toUpperCase());
        } catch (IllegalArgumentException ex) {
            JavaPlugin.getPlugin(AugmentedHardcore.class).getLogger().log(Level.WARNING, "Invalid GlobalReviveUnDeathBan.ScheduleType value. Defaulting to DAILY.");
            scheduleType = GlobalResetScheduleType.DAILY;
        }

        int intervalHours = Math.max(1, section.getInt("IntervalHours", 24));

        LocalTime timeOfDay = parseTimeOfDay(section);

        DayOfWeek dayOfWeek;
        try {
            dayOfWeek = DayOfWeek.valueOf(section.getString("DayOfWeek", "MONDAY").toUpperCase());
        } catch (IllegalArgumentException ex) {
            JavaPlugin.getPlugin(AugmentedHardcore.class).getLogger().log(Level.WARNING, "Invalid GlobalReviveUnDeathBan.DayOfWeek value. Defaulting to MONDAY.");
            dayOfWeek = DayOfWeek.MONDAY;
        }

        int dayOfMonth = Math.max(1, Math.min(31, section.getInt("DayOfMonth", 1)));

        ZoneId timezone;
        try {
            timezone = ZoneId.of(section.getString("Timezone", "UTC"));
        } catch (Exception ex) {
            JavaPlugin.getPlugin(AugmentedHardcore.class).getLogger().log(Level.WARNING, "Invalid GlobalReviveUnDeathBan.Timezone value. Defaulting to UTC.");
            timezone = ZoneId.of("UTC");
        }

        return new GlobalReviveUnDeathBanConfiguration(enabled, scheduleType, intervalHours, timeOfDay, dayOfWeek, dayOfMonth, timezone);
    }

    private static LocalTime parseTimeOfDay(ConfigurationSection section) {
        Object rawTimeOfDay = section.get("TimeOfDay");

        if (rawTimeOfDay instanceof Number number) {
            int minutesSinceMidnight = number.intValue();
            if (minutesSinceMidnight >= 0 && minutesSinceMidnight < 24 * 60) {
                JavaPlugin.getPlugin(AugmentedHardcore.class).getLogger().log(Level.WARNING, "GlobalReviveUnDeathBan.TimeOfDay was loaded as a number ({0}). This can happen when HH:mm is unquoted in YAML. Interpreting it as minutes since midnight.", minutesSinceMidnight);
                return LocalTime.of(minutesSinceMidnight / 60, minutesSinceMidnight % 60);
            }

            JavaPlugin.getPlugin(AugmentedHardcore.class).getLogger().log(Level.WARNING, "Invalid GlobalReviveUnDeathBan.TimeOfDay numeric value ({0}). Expected HH:mm, defaulting to 00:00.", minutesSinceMidnight);
            return LocalTime.MIDNIGHT;
        }

        String rawTimeOfDayString = section.getString("TimeOfDay", "00:00");

        try {
            return LocalTime.parse(rawTimeOfDayString);
        } catch (Exception ignored) {
            try {
                int minutesSinceMidnight = Integer.parseInt(rawTimeOfDayString);
                if (minutesSinceMidnight >= 0 && minutesSinceMidnight < 24 * 60) {
                    JavaPlugin.getPlugin(AugmentedHardcore.class).getLogger().log(Level.WARNING, "GlobalReviveUnDeathBan.TimeOfDay looked numeric ({0}). Interpreting it as minutes since midnight.", minutesSinceMidnight);
                    return LocalTime.of(minutesSinceMidnight / 60, minutesSinceMidnight % 60);
                }
            } catch (NumberFormatException ignoredAgain) {
                // ignored intentionally, falls through to default warning below
            }

            JavaPlugin.getPlugin(AugmentedHardcore.class).getLogger().log(Level.WARNING, "Invalid GlobalReviveUnDeathBan.TimeOfDay value. Expected HH:mm, defaulting to 00:00.");
            return LocalTime.MIDNIGHT;
        }
    }

    public boolean isUseRevive() {
        return useRevive;
    }

    public int getTimeBetweenRevives() {
        return timeBetweenRevives;
    }

    public boolean isReviveOnFirstJoin() {
        return reviveOnFirstJoin;
    }

    public List<String> getDisableReviveInWorlds() {
        return disableReviveInWorlds;
    }

    public int getLivesLostOnReviving() {
        return livesLostOnReviving;
    }

    public int getLivesGainedOnRevive() {
        return livesGainedOnRevive;
    }

    public GlobalReviveUnDeathBanConfiguration getGlobalReviveUnDeathBanConfiguration() {
        return globalReviveUnDeathBanConfiguration;
    }
}
