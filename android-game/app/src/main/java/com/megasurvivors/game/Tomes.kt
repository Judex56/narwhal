package com.megasurvivors.game

import android.graphics.Color

/**
 * Фолианты: пассивные усиления на весь забег. Работают как предметы —
 * выбираются в меню (сколько угодно, хоть все), в игре не прокачиваются
 * и в вариантах левел-апа не появляются.
 */
enum class Tome(
    val label: String,
    val color: Int,
    val stats: Map<Stat, Float>,
) {
    MIGHT("Фолиант мощи", Color.rgb(239, 83, 80), mapOf(Stat.DAMAGE to 0.15f)),
    HASTE("Фолиант спешки", Color.rgb(255, 202, 40), mapOf(Stat.COOLDOWN to 0.15f)),
    WIND("Фолиант ветра", Color.rgb(77, 208, 225), mapOf(Stat.MOVE_SPEED to 0.10f)),
    VITALITY("Фолиант жизни", Color.rgb(102, 187, 106), mapOf(Stat.MAX_HP to 0.25f)),
    GREED("Фолиант жадности", Color.rgb(255, 179, 0), mapOf(Stat.GOLD_GAIN to 0.30f)),
    WISDOM("Фолиант мудрости", Color.rgb(149, 117, 205), mapOf(Stat.XP_GAIN to 0.20f)),
    PRECISION(
        "Фолиант точности", Color.rgb(255, 138, 101),
        mapOf(Stat.CRIT_CHANCE to 0.08f, Stat.CRIT_DMG to 0.25f),
    ),
    REACH("Фолиант размаха", Color.rgb(129, 212, 250), mapOf(Stat.AREA to 0.15f));

    /** "+15% урон" — что даёт фолиант на весь забег. */
    val desc: String
        get() = stats.entries.joinToString(", ") {
            "+${(it.value * 100).toInt()}% ${it.key.label.lowercase()}"
        }
}
