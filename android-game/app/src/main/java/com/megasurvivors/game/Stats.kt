package com.megasurvivors.game

/** Все прокачиваемые характеристики персонажа. */
enum class Stat(val label: String) {
    DAMAGE("Урон"),
    COOLDOWN("Скорость атаки"),
    MOVE_SPEED("Скорость бега"),
    MAX_HP("Макс. здоровье"),
    ARMOR("Броня"),
    MAGNET("Радиус сбора"),
    AREA("Область атак"),
    PROJ_SPEED("Скорость снарядов"),
    XP_GAIN("Опыт"),
    CRIT_CHANCE("Шанс крита"),
    CRIT_DMG("Урон крита"),
    GOLD_GAIN("Золото");
}

/**
 * Временный или постоянный бафф. Значения — добавка в долях:
 * 0.25 к DAMAGE означает +25% урона.
 */
class Buff(
    val name: String,
    val stats: Map<Stat, Float>,
    /** Оставшееся время в секундах; POSITIVE_INFINITY — навсегда. */
    var remaining: Float,
    val color: Int,
) {
    val permanent: Boolean get() = remaining == Float.POSITIVE_INFINITY
}
