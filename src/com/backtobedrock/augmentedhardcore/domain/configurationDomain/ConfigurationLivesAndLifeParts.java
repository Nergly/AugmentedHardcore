package com.backtobedrock.augmentedhardcore.domain.configurationDomain;

import com.backtobedrock.augmentedhardcore.AugmentedHardcore;
import com.backtobedrock.augmentedhardcore.utilities.ConfigUtils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.List;
import java.util.OptionalInt;
import java.util.logging.Level;

public class ConfigurationLivesAndLifeParts {
    //lives
    private final boolean useLives;
    private final int maxLives;
    private final int livesAtStart;
    private final int livesAfterBan;
    private final int livesLostPerDeath;
    private final List<String> enableLosingLivesInWorlds;

    //life parts
    private final boolean useLifeParts;
    private final int maxLifeParts;
    private final int lifePartsPerLife;
    private final int lifePartsAtStart;
    private final int lifePartsAfterBan;
    private final int lifePartsLostPerDeath;
    private final int lifePartsLostPerDeathBan;
    private final boolean lifePartsOnKill;
    private final EnumMap<EntityType, Integer> lifePartsPerKill;
    private final boolean getLifePartsByPlaytime;
    private final int playtimePerLifePart;
    private final List<String> disableGainingLifePartsInWorlds;
    private final List<String> disableLosingLifePartsInWorlds;

    public ConfigurationLivesAndLifeParts(
            //lives
            boolean useLives,
            int maxLives,
            int livesAtStart,
            int livesAfterBan,
            int livesLostPerDeath,
            List<String> enableLosingLivesInWorlds,
            //life parts
            boolean useLifeParts,
            int maxLifeParts,
            int lifePartsPerLife,
            int lifePartsAtStart,
            int lifePartsAfterBan,
            int lifePartsLostPerDeath,
            int lifePartsLostPerDeathBan,
            boolean lifePartsOnKill,
            EnumMap<EntityType, Integer> lifePartsPerKill,
            boolean getLifePartsByPlaytime,
            int playtimePerLifePart,
            List<String> disableGainingLifePartsInWorlds,
            List<String> disableLosingLifePartsInWorlds) {
        //lives
        this.useLives = useLives;
        this.maxLives = maxLives;
        this.livesAtStart = livesAtStart;
        this.livesAfterBan = livesAfterBan;
        this.livesLostPerDeath = livesLostPerDeath;
        this.enableLosingLivesInWorlds = enableLosingLivesInWorlds;

        //life parts
        this.useLifeParts = useLifeParts;
        this.maxLifeParts = maxLifeParts;
        this.lifePartsPerLife = lifePartsPerLife;
        this.lifePartsAtStart = lifePartsAtStart;
        this.lifePartsAfterBan = lifePartsAfterBan;
        this.lifePartsLostPerDeath = lifePartsLostPerDeath;
        this.lifePartsLostPerDeathBan = lifePartsLostPerDeathBan;
        this.lifePartsOnKill = lifePartsOnKill;
        this.lifePartsPerKill = lifePartsPerKill;
        this.getLifePartsByPlaytime = getLifePartsByPlaytime;
        this.playtimePerLifePart = playtimePerLifePart;
        this.disableGainingLifePartsInWorlds = disableGainingLifePartsInWorlds;
        this.disableLosingLifePartsInWorlds = disableLosingLifePartsInWorlds;
    }

    public static ConfigurationLivesAndLifeParts deserialize(ConfigurationSection section) {
        //lives
        boolean cUseLives = section.getBoolean("UseLives", true);
        OptionalInt cMaxLives = ConfigUtils.checkMinMax("MaxLives", section.getInt("MaxLives", 5), 1, Integer.MAX_VALUE);
        OptionalInt cLivesAtStart = ConfigUtils.checkMinMax("LivesAtStart", section.getInt("LivesAtStart", 1), 1, Integer.MAX_VALUE);
        OptionalInt cLivesAfterBan = ConfigUtils.checkMinMax("LivesAfterBan", section.getInt("LivesAfterBan", 1), 1, Integer.MAX_VALUE);
        OptionalInt cLivesLostPerDeath = ConfigUtils.checkMinMax("LivesLostPerDeath", section.getInt("LivesLostPerDeath", 1), 1, Integer.MAX_VALUE);
        List<String> cEnableLosingLivesInWorlds = section.getStringList("EnableLosingLivesInWorlds").stream().map(String::toLowerCase).toList();

        //life parts
        boolean cUseLifeParts = section.getBoolean("UseLifeParts", true);
        OptionalInt cMaxLifeParts = ConfigUtils.checkMinMax("MaxLifeParts", section.getInt("MaxLifeParts", 6), -1, Integer.MAX_VALUE);
        OptionalInt cLifePartsPerLife = ConfigUtils.checkMinMax("LifePartsPerLife", section.getInt("LifePartsPerLife"), 1, Integer.MAX_VALUE);
        OptionalInt cLifePartsAtStart = ConfigUtils.checkMinMax("LifePartsAtStart", section.getInt("LifePartsAtStart"), 0, Integer.MAX_VALUE);
        OptionalInt cLifePartsAfterBan = ConfigUtils.checkMinMax("LifePartsAfterBan", section.getInt("LifePartsAfterBan"), -1, Integer.MAX_VALUE);
        OptionalInt cLifePartsLostPerDeath = ConfigUtils.checkMinMax("LifePartsLostPerDeath", section.getInt("LifePartsLostPerDeath", 1), -1, Integer.MAX_VALUE);
        OptionalInt cLifePartsLostPerDeathBan = ConfigUtils.checkMinMax("LifePartsLostPerDeathBan", section.getInt("LifePartsLostPerDeathBan", -1), -1, Integer.MAX_VALUE);
        boolean cLifePartsOnKill = section.getBoolean("LifePartsOnKill");
        EnumMap<EntityType, Integer> cLifePartsPerKill = new EnumMap<>(EntityType.class);
        boolean cGetLifePartsByPlaytime = section.getBoolean("GetLifePartByPlaytime", false);
        OptionalInt cPlaytimePerLifePart = ConfigUtils.checkMinMax("PlaytimePerLifePart", section.getInt("PlaytimePerLifePart", 30), 1, Integer.MAX_VALUE);
        List<String> cDisableGainingLifePartsInWorlds = section.getStringList("DisableGainingLifePartsInWorlds").stream().map(String::toLowerCase).toList();
        List<String> cDisableLosingLifePartsInWorlds = section.getStringList("DisableLosingLifePartsInWorlds").stream().map(String::toLowerCase).toList();

        if (cUseLifeParts && !cUseLives) {
            JavaPlugin.getPlugin(AugmentedHardcore.class).getLogger().log(Level.WARNING, "Life parts are enabled without having lives enabled.");
        }

        //if cLifePartsLostPerDeath or cLifePartsLostPerDeathBan == -1 then set to max Integer.
        if (cLifePartsLostPerDeath.isPresent() && cLifePartsLostPerDeath.getAsInt() == -1) {
            cLifePartsLostPerDeath = OptionalInt.of(Integer.MAX_VALUE);
        }
        if (cLifePartsLostPerDeathBan.isPresent() && cLifePartsLostPerDeathBan.getAsInt() == -1) {
            cLifePartsLostPerDeathBan = OptionalInt.of(Integer.MAX_VALUE);
        }
        if (cMaxLifeParts.isPresent() && cMaxLifeParts.getAsInt() == -1) {
            cMaxLifeParts = OptionalInt.of(Integer.MAX_VALUE);
        }

        ConfigurationSection lifePartsPerKillSection = section.getConfigurationSection("LifePartsPerKill");
        if (lifePartsPerKillSection != null) {
            lifePartsPerKillSection.getKeys(false).forEach(e -> {
                EntityType type = ConfigUtils.getLivingEntityType("LifePartsPerKill", e);
                if (type != null) {
                    OptionalInt amount = ConfigUtils.checkMin("LifePartsPerKill." + e, lifePartsPerKillSection.getInt(e, 0), 0);
                    amount.ifPresent(a -> cLifePartsPerKill.put(type, a));
                }
            });
        }

        if (cMaxLives.isEmpty() || cLivesAtStart.isEmpty() || cLivesAfterBan.isEmpty() || cLivesLostPerDeath.isEmpty() || cMaxLifeParts.isEmpty() || cLifePartsPerLife.isEmpty() || cLifePartsAtStart.isEmpty() || cLifePartsAfterBan.isEmpty() || cLifePartsLostPerDeath.isEmpty() || cLifePartsLostPerDeathBan.isEmpty() || cPlaytimePerLifePart.isEmpty()) {
            return null;
        }

        int vMaxLives = cMaxLives.getAsInt();
        int vLivesAtStart = cLivesAtStart.getAsInt();
        int vLivesAfterBan = cLivesAfterBan.getAsInt();
        int vLivesLostPerDeath = cLivesLostPerDeath.getAsInt();
        int vMaxLifeParts = cMaxLifeParts.getAsInt();
        int vLifePartsPerLife = cLifePartsPerLife.getAsInt();
        int vLifePartsAtStart = cLifePartsAtStart.getAsInt();
        int vLifePartsAfterBan = cLifePartsAfterBan.getAsInt();
        int vLifePartsLostPerDeath = cLifePartsLostPerDeath.getAsInt();
        int vLifePartsLostPerDeathBan = cLifePartsLostPerDeathBan.getAsInt();
        int vPlaytimePerLifePart = cPlaytimePerLifePart.getAsInt();

        return new ConfigurationLivesAndLifeParts(
                //lives
                cUseLives,
                vMaxLives,
                vLivesAtStart,
                vLivesAfterBan,
                vLivesLostPerDeath,
                cEnableLosingLivesInWorlds,
                //life parts
                cUseLifeParts,
                vMaxLifeParts,
                vLifePartsPerLife,
                vLifePartsAtStart,
                vLifePartsAfterBan,
                vLifePartsLostPerDeath,
                vLifePartsLostPerDeathBan,
                cLifePartsOnKill,
                cLifePartsPerKill,
                cGetLifePartsByPlaytime,
                vPlaytimePerLifePart * 1200,
                cDisableGainingLifePartsInWorlds,
                cDisableLosingLifePartsInWorlds
        );
    }

    public int getLifePartsAfterBan() {
        return lifePartsAfterBan;
    }

    public int getLivesAfterBan() {
        return livesAfterBan;
    }

    public int getLifePartsAtStart() {
        return lifePartsAtStart;
    }

    public boolean isUseLives() {
        return useLives;
    }

    public int getMaxLives() {
        return maxLives;
    }

    public int getLivesAtStart() {
        return livesAtStart;
    }

    public int getLivesLostPerDeath() {
        return livesLostPerDeath;
    }

    public List<String> getEnableLosingLivesInWorlds() {
        return enableLosingLivesInWorlds;
    }

    public boolean isUseLifeParts() {
        return useLifeParts;
    }

    public int getLifePartsPerLife() {
        return lifePartsPerLife;
    }

    public int getLifePartsLostPerDeath() {
        return lifePartsLostPerDeath;
    }

    public boolean isLifePartsOnKill() {
        return lifePartsOnKill;
    }

    public boolean isGetLifePartsByPlaytime() {
        return getLifePartsByPlaytime;
    }

    public int getPlaytimePerLifePart() {
        return playtimePerLifePart;
    }

    public List<String> getDisableLosingLifePartsInWorlds() {
        return disableLosingLifePartsInWorlds;
    }

    public int getLifePartsLostPerDeathBan() {
        return lifePartsLostPerDeathBan;
    }

    public EnumMap<EntityType, Integer> getLifePartsPerKill() {
        return lifePartsPerKill;
    }

    public List<String> getDisableGainingLifePartsInWorlds() {
        return disableGainingLifePartsInWorlds;
    }

    public int getMaxLifeParts() {
        return maxLifeParts;
    }
}
