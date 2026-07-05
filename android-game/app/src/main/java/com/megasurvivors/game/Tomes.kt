package com.megasurvivors.game

import android.graphics.Color

const val MAX_TOMES = 2
const val MAX_TOME_LEVEL = 10

/**
 * Фолианты (в духе Megabonk): пассивные ветки прокачки.
 * До 2 штук выбираются перед забегом (стартуют на 1 уровне),
 * дальше качаются на левел-апах вместе с оружием — до 10 уровня.
 * [stats] — прибавка ЗА ОДИН уровень.
 */
enum class Tome(
    val label: String,
    val color: Int,
    val stats: Map<Stat, Float>,
) {
    MIGHT("Фолиант мощи", Color.rgb(239, 83, 80), mapOf(Stat.DAMAGE to 0.08f)),
    HASTE("Фолиант спешки", Color.rgb(255, 202, 40), mapOf(Stat.COOLDOWN to 0.08f)),
    WIND("Фолиант ветра", Color.rgb(77, 208, 225), mapOf(Stat.MOVE_SPEED to 0.05f)),
    VITALITY("Фолиант жизни", Color.rgb(102, 187, 106), mapOf(Stat.MAX_HP to 0.12f)),
    GREED("Фолиант жадности", Color.rgb(255, 179, 0), mapOf(Stat.GOLD_GAIN to 0.15f)),
    WISDOM("Фолиант мудрости", Color.rgb(149, 117, 205), mapOf(Stat.XP_GAIN to 0.10f)),
    PRECISION(
        "Фолиант точности", Color.rgb(255, 138, 101),
        mapOf(Stat.CRIT_CHANCE to 0.04f, Stat.CRIT_DMG to 0.10f),
    ),
    REACH("Фолиант размаха", Color.rgb(129, 212, 250), mapOf(Stat.AREA to 0.08f));

    /** "+8% урон за уровень" */
    val perLevelText: String
        get() = stats.entries.joinToString(", ") {
            "+${(it.value * 100).toInt()}% ${it.key.label.lowercase()}"
        } + " за уровень"

    /** Суммарный бонус на уровне [level]: "+16% урон". */
    fun totalText(level: Int): String = stats.entries.joinToString(", ") {
        "+${(it.value * level * 100).toInt()}% ${it.key.label.lowercase()}"
    }

    /** Описание апгрейда с текущего уровня [cur] на следующий. */
    fun upgradeDesc(cur: Int): String = stats.entries.joinToString(" • ") {
        "${it.key.label}: +${(it.value * cur * 100).toInt()}% → +${(it.value * (cur + 1) * 100).toInt()}%"
    }
}
