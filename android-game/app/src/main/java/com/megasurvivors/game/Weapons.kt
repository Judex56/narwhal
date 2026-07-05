package com.megasurvivors.game

import android.graphics.Color

const val MAX_WEAPON_LEVEL = 5

enum class WeaponType(val label: String, val desc: String, val color: Int) {
    DART(
        "Дротики",
        "Летят в ближайшего врага",
        Color.rgb(129, 212, 250),
    ),
    ORBIT(
        "Клинки",
        "Вращаются вокруг героя",
        Color.rgb(206, 147, 216),
    ),
    AURA(
        "Аура огня",
        "Жжёт врагов вокруг",
        Color.rgb(255, 138, 101),
    ),
    LIGHTNING(
        "Молния",
        "Бьёт случайных врагов",
        Color.rgb(255, 241, 118),
    );
}

class WeaponInstance(val type: WeaponType, var level: Int = 1) {
    var timer = 0f
    var orbitAngle = 0f
}

/** Числовые параметры оружия по уровням. */
object WeaponBalance {

    fun dartDamage(l: Int) = 10f + 4f * (l - 1)
    fun dartCount(l: Int) = 1 + (l + 1) / 2 // 1,2,2,3,3
    fun dartCooldown(l: Int) = 1.0f - 0.06f * (l - 1)

    fun orbitDamage(l: Int) = 8f + 4f * (l - 1)
    fun orbitCount(l: Int) = 2 + (l - 1) // 2..6
    fun orbitRadius(l: Int) = 95f + 8f * (l - 1)
    const val ORBIT_HIT_INTERVAL = 0.35f

    fun auraDamage(l: Int) = 4f + 2.5f * (l - 1)
    fun auraRadius(l: Int) = 120f + 18f * (l - 1)
    const val AURA_TICK = 0.5f

    fun lightningDamage(l: Int) = 22f + 10f * (l - 1)
    fun lightningTargets(l: Int) = 1 + l / 2 // 1,2,2,3,3
    fun lightningCooldown(l: Int) = 2.2f - 0.15f * (l - 1)

    fun levelUpText(type: WeaponType, newLevel: Int): String = when (type) {
        WeaponType.DART -> "Урон ${dartDamage(newLevel).toInt()}, снарядов: ${dartCount(newLevel)}"
        WeaponType.ORBIT -> "Урон ${orbitDamage(newLevel).toInt()}, клинков: ${orbitCount(newLevel)}"
        WeaponType.AURA -> "Урон ${auraDamage(newLevel).toInt()}/тик, радиус +18"
        WeaponType.LIGHTNING -> "Урон ${lightningDamage(newLevel).toInt()}, целей: ${lightningTargets(newLevel)}"
    }
}
