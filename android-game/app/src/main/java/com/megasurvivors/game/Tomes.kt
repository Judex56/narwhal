package com.megasurvivors.game

import android.graphics.Color

const val MAX_TOMES = 2

/**
 * Фолианты (в духе Megabonk): пассивные усиления, которые
 * выбираются перед забегом и действуют весь забег.
 */
enum class Tome(
    val label: String,
    val desc: String,
    val color: Int,
    val stats: Map<Stat, Float>,
) {
    MIGHT(
        "Фолиант мощи", "+15% урона", Color.rgb(239, 83, 80),
        mapOf(Stat.DAMAGE to 0.15f),
    ),
    HASTE(
        "Фолиант спешки", "+15% скорости атаки", Color.rgb(255, 202, 40),
        mapOf(Stat.COOLDOWN to 0.15f),
    ),
    WIND(
        "Фолиант ветра", "+10% скорости бега", Color.rgb(77, 208, 225),
        mapOf(Stat.MOVE_SPEED to 0.10f),
    ),
    VITALITY(
        "Фолиант жизни", "+25% здоровья", Color.rgb(102, 187, 106),
        mapOf(Stat.MAX_HP to 0.25f),
    ),
    GREED(
        "Фолиант жадности", "+30% золота", Color.rgb(255, 179, 0),
        mapOf(Stat.GOLD_GAIN to 0.30f),
    ),
    WISDOM(
        "Фолиант мудрости", "+20% опыта", Color.rgb(149, 117, 205),
        mapOf(Stat.XP_GAIN to 0.20f),
    ),
    PRECISION(
        "Фолиант точности", "+8% шанс крита, +25% урон крита", Color.rgb(255, 138, 101),
        mapOf(Stat.CRIT_CHANCE to 0.08f, Stat.CRIT_DMG to 0.25f),
    ),
    REACH(
        "Фолиант размаха", "+15% области атак", Color.rgb(129, 212, 250),
        mapOf(Stat.AREA to 0.15f),
    );
}
