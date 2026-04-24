package com.backtobedrock.augmentedhardcore.domain.configurationDomain;

import com.backtobedrock.augmentedhardcore.utilities.ConfigUtils;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.OptionalInt;

public class ConfigurationRevive {
    private final boolean useRevive;
    private final int livesLostOnReviving;
    private final int livesGainedOnRevive;
    private final int timeBetweenRevives;
    private final boolean reviveOnFirstJoin;
    private final List<String> disableReviveInWorlds;

    public ConfigurationRevive(boolean useRevive, int livesLostOnReviving, int livesGainedOnRevive, int timeBetweenRevives, boolean reviveOnFirstJoin, List<String> disableReviveInWorlds) {
        this.useRevive = useRevive;
        this.livesLostOnReviving = livesLostOnReviving;
        this.livesGainedOnRevive = livesGainedOnRevive;
        this.timeBetweenRevives = timeBetweenRevives;
        this.reviveOnFirstJoin = reviveOnFirstJoin;
        this.disableReviveInWorlds = disableReviveInWorlds;
    }

    public static ConfigurationRevive deserialize(ConfigurationSection section) {
        boolean cUseRevive = section.getBoolean("UseRevive", true);
        OptionalInt cLivesLostOnReviving = ConfigUtils.checkMinMax("LivesLostOnReviving", section.getInt("LivesLostOnReviving", 1), 1, Integer.MAX_VALUE);
        OptionalInt cLivesGainedOnRevive = ConfigUtils.checkMinMax("LivesGainedOnRevive", section.getInt("LivesGainedOnRevive", 1), 1, Integer.MAX_VALUE);
        OptionalInt cTimeBetweenRevives = ConfigUtils.checkMinMax("TimeBetweenRevives", section.getInt("TimeBetweenRevives", 1440), 0, Integer.MAX_VALUE);
        boolean cReviveOnFirstJoin = section.getBoolean("ReviveOnFirstJoin", false);
        List<String> cDisableReviveInWorlds = section.getStringList("DisableReviveInWorlds").stream().map(String::toLowerCase).toList();

        if (cTimeBetweenRevives.isEmpty() || cLivesLostOnReviving.isEmpty() || cLivesGainedOnRevive.isEmpty()) {
            return null;
        }

        return new ConfigurationRevive(
                cUseRevive,
                cLivesLostOnReviving.getAsInt(),
                cLivesGainedOnRevive.getAsInt(),
                cTimeBetweenRevives.getAsInt() * 1200,
                cReviveOnFirstJoin,
                cDisableReviveInWorlds
        );
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
}
