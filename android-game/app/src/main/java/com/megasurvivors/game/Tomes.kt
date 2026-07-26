package com.megasurvivors.game

import android.graphics.Color

const val MAX_TOME_LEVEL = 20

/**
 * Фолианты: выбираются в меню (сколько угодно, хоть все) и качаются
 * в забеге НА ЛЕВЕЛ-АПАХ — точно так же, как оружие, до 20 уровня.
 * [stats] — прибавка ЗА ОДИН уровень.
 */
enum class Tome(
    val label: String,
    val color: Int,
    val stats: Map<Stat, Float>,
) {
    MIGHT("Фолиант мощи", Color.rgb(239, 83, 80), mapOf(Stat.DAMAGE to 0.04f)),
    HASTE("Фолиант спешки", Color.rgb(255, 202, 40), mapOf(Stat.COOLDOWN to 0.04f)),
    WIND("Фолиант ветра", Color.rgb(77, 208, 225), mapOf(Stat.MOVE_SPEED to 0.025f)),
    VITALITY("Фолиант жизни", Color.rgb(102, 187, 106), mapOf(Stat.MAX_HP to 0.06f)),
    GREED("Фолиант жадности", Color.rgb(255, 179, 0), mapOf(Stat.GOLD_GAIN to 0.08f)),
    WISDOM("Фолиант мудрости", Color.rgb(149, 117, 205), mapOf(Stat.XP_GAIN to 0.05f)),
    PRECISION(
        "Фолиант точности", Color.rgb(255, 138, 101),
        mapOf(Stat.CRIT_CHANCE to 0.02f, Stat.CRIT_DMG to 0.05f),
    ),
    REACH("Фолиант размаха", Color.rgb(129, 212, 250), mapOf(Stat.AREA to 0.04f));

    /** "+4% урон за уровень". */
    val perLevelText: String
        get() = stats.entries.joinToString(", ") {
            "+${fmtPct(it.value)}% ${it.key.label.lowercase()}"
        } + " за уровень"

    /** Описание апгрейда с уровня [cur] на следующий: точные цифры. */
    fun upgradeDesc(cur: Int): String = stats.entries.joinToString(" • ") {
        "${it.key.label}: +${fmtPct(it.value * cur)}% → +${fmtPct(it.value * (cur + 1))}%"
    }

    private fun fmtPct(v: Float): String {
        val p = v * 100
        return if (p % 1f == 0f) "${p.toInt()}" else "%.1f".format(p)
    }
}
