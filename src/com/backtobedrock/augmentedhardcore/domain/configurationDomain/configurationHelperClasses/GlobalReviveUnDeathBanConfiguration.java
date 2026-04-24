package com.backtobedrock.augmentedhardcore.domain.configurationDomain.configurationHelperClasses;

import com.backtobedrock.augmentedhardcore.domain.enums.GlobalResetScheduleType;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;

public class GlobalReviveUnDeathBanConfiguration {
    private final boolean enabled;
    private final GlobalResetScheduleType scheduleType;
    private final int intervalHours;
    private final LocalTime timeOfDay;
    private final DayOfWeek dayOfWeek;
    private final int dayOfMonth;
    private final ZoneId timezone;

    public GlobalReviveUnDeathBanConfiguration(boolean enabled, GlobalResetScheduleType scheduleType, int intervalHours, LocalTime timeOfDay, DayOfWeek dayOfWeek, int dayOfMonth, ZoneId timezone) {
        this.enabled = enabled;
        this.scheduleType = scheduleType;
        this.intervalHours = intervalHours;
        this.timeOfDay = timeOfDay;
        this.dayOfWeek = dayOfWeek;
        this.dayOfMonth = dayOfMonth;
        this.timezone = timezone;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public GlobalResetScheduleType getScheduleType() {
        return scheduleType;
    }

    public int getIntervalHours() {
        return intervalHours;
    }

    public LocalTime getTimeOfDay() {
        return timeOfDay;
    }

    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }

    public int getDayOfMonth() {
        return dayOfMonth;
    }

    public ZoneId getTimezone() {
        return timezone;
    }
}
