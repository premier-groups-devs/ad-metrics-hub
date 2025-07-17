package com.premiergroup.ad_metrics_hub.enums;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

public enum DateFilter {

    LAST_WEEK,
    THIS_WEEK,
    LAST_MONTH,
    THIS_MONTH,
    LAST_YEAR,
    THIS_YEAR,
    YESTERDAY,
    TODAY,
    LAST_7_DAYS,
    LAST_30_DAYS,
    LAST_90_DAYS;

    public String getType() {
        return switch (this) {
            case LAST_YEAR, THIS_YEAR -> "MONTH";
            default -> "DAY";
        };
    }

    public LocalDate getStartDate() {
        LocalDate today = LocalDate.now();

        return switch (this) {
            case LAST_WEEK -> today.minusWeeks(1).with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
            case THIS_WEEK -> today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
            case LAST_MONTH -> today.minusMonths(1).with(TemporalAdjusters.firstDayOfMonth());
            case THIS_MONTH -> today.with(TemporalAdjusters.firstDayOfMonth());
            case LAST_YEAR -> today.minusYears(1).with(TemporalAdjusters.firstDayOfYear());
            case THIS_YEAR -> today.with(TemporalAdjusters.firstDayOfYear());
            case YESTERDAY -> today.minusDays(1);
            case TODAY -> today;
            case LAST_7_DAYS -> today.minusDays(7);
            case LAST_30_DAYS -> today.minusDays(30);
            case LAST_90_DAYS -> today.minusDays(90);
        };
    }

    public LocalDate getEndDate() {
        LocalDate today = LocalDate.now();

        return switch (this) {
            case LAST_WEEK -> today.minusWeeks(1).with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY));
            case THIS_WEEK -> today.with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY));
            case LAST_MONTH -> today.minusMonths(1).with(TemporalAdjusters.lastDayOfMonth());
            case THIS_MONTH -> today.with(TemporalAdjusters.lastDayOfMonth());
            case LAST_YEAR -> today.minusYears(1).with(TemporalAdjusters.lastDayOfYear());
            case THIS_YEAR -> today.with(TemporalAdjusters.lastDayOfYear());
            case YESTERDAY -> today.minusDays(1);
            case LAST_7_DAYS, LAST_30_DAYS, LAST_90_DAYS, TODAY -> today;
        };
    }
}
