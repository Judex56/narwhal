package com.megasurvivors.game

import android.graphics.Color

/** Босс выходит на этой секунде забега (15 минут). */
const val BOSS_TIME = 900f

/**
 * Уровень (этаж) игры: своя палитра карты, множители силы врагов
 * и свой босс. Проходится убийством босса; следующий открывается
 * после победы.
 */
class LevelDef(
    val index: Int,
    val name: String,
    val bossName: String,
    val bg: Int,
    val grid: Int,
    val decor: Int,
    val bossColor: Int,
    /** Множитель здоровья врагов. */
    val hpMult: Float,
    /** Множитель урона врагов. */
    val dmgMult: Float,
    /** Дополнительные враги в каждой волне. */
    val spawnBonus: Int,
    /** Доп. множитель здоровья босса (финальный босс ×5). */
    val bossHpMult: Float = 1f,
)

val LEVELS: List<LevelDef> = listOf(
    LevelDef(
        1, "Лес", "Древень",
        Color.rgb(26, 34, 24), Color.rgb(34, 44, 32), Color.rgb(45, 58, 42),
        Color.rgb(104, 159, 56), 1.0f, 1.0f, 0,
    ),
    LevelDef(
        2, "Пустыня", "Фараон песков",
        Color.rgb(46, 40, 26), Color.rgb(58, 50, 32), Color.rgb(72, 62, 40),
        Color.rgb(255, 179, 0), 1.7f, 1.35f, 1,
    ),
    LevelDef(
        3, "Кладбище", "Костяной король",
        Color.rgb(30, 30, 38), Color.rgb(40, 40, 50), Color.rgb(52, 52, 64),
        Color.rgb(207, 216, 220), 2.4f, 1.7f, 2,
    ),
    LevelDef(
        4, "Ледник", "Ледяной голем",
        Color.rgb(24, 34, 44), Color.rgb(32, 44, 56), Color.rgb(42, 56, 70),
        Color.rgb(77, 208, 225), 3.2f, 2.05f, 3,
    ),
    LevelDef(
        5, "Болото", "Болотный ужас",
        Color.rgb(24, 36, 30), Color.rgb(32, 46, 38), Color.rgb(42, 58, 48),
        Color.rgb(124, 179, 66), 4.1f, 2.4f, 4,
    ),
    LevelDef(
        6, "Вулкан", "Магмовый титан",
        Color.rgb(42, 26, 22), Color.rgb(54, 34, 28), Color.rgb(68, 42, 34),
        Color.rgb(255, 87, 34), 5.1f, 2.75f, 5,
    ),
    LevelDef(
        7, "Руины", "Страж руин",
        Color.rgb(36, 32, 28), Color.rgb(46, 42, 36), Color.rgb(58, 52, 44),
        Color.rgb(141, 110, 99), 6.2f, 3.1f, 6,
    ),
    LevelDef(
        8, "Бездна", "Пожиратель миров",
        Color.rgb(18, 16, 28), Color.rgb(26, 24, 38), Color.rgb(36, 32, 50),
        Color.rgb(171, 71, 188), 7.5f, 3.5f, 8,
        bossHpMult = 5f,
    ),
)
