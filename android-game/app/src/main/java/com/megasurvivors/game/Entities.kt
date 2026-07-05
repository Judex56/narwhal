package com.megasurvivors.game

import android.graphics.Color
import kotlin.math.sqrt

class Player {
    var x = 0f
    var y = 0f
    var hp = 100f
    var level = 1
    var xp = 0f
    var xpToNext = 10f
    var iFrames = 0f
    var facing = 1f // 1 вправо, -1 влево
    val radius = 26f

    val weapons = ArrayList<WeaponInstance>()
    val equipment = HashMap<Slot, Item>()
    val buffs = ArrayList<Buff>()
    /** Бонусы от пассивных апгрейдов уровня. */
    val passives = HashMap<Stat, Float>()

    private val baseSpeed = 230f
    private val baseMaxHp = 100f

    fun bonus(stat: Stat): Float {
        var sum = passives[stat] ?: 0f
        for (item in equipment.values) sum += item.stats[stat] ?: 0f
        for (buff in buffs) sum += buff.stats[stat] ?: 0f
        return sum
    }

    /** Множитель характеристики: 1.0 = базовое значение. */
    fun mult(stat: Stat): Float = 1f + bonus(stat)

    /** Множитель кулдауна: чем больше бонус, тем МЕНЬШЕ пауза между атаками. */
    fun cooldownFactor(): Float = 1f / (1f + bonus(Stat.COOLDOWN))

    val moveSpeed: Float get() = baseSpeed * mult(Stat.MOVE_SPEED)
    val maxHp: Float get() = baseMaxHp * mult(Stat.MAX_HP)

    /** Броня уменьшает входящий урон, с убывающей отдачей. */
    fun reduceDamage(dmg: Float): Float {
        val armor = bonus(Stat.ARMOR)
        return dmg / (1f + armor * 1.5f)
    }
}

enum class EnemyType(
    val hp: Float,
    val speed: Float,
    val damage: Float,
    val radius: Float,
    val xp: Float,
    val color: Int,
) {
    WALKER(22f, 95f, 8f, 22f, 1f, Color.rgb(141, 110, 99)),
    RUNNER(14f, 185f, 6f, 17f, 1.5f, Color.rgb(255, 112, 67)),
    TANK(90f, 60f, 16f, 34f, 4f, Color.rgb(84, 110, 122)),
    BRUTE(45f, 110f, 12f, 27f, 2.5f, Color.rgb(124, 179, 66)),
    ELITE(600f, 85f, 24f, 48f, 25f, Color.rgb(255, 213, 79)),
}

class Enemy(val type: EnemyType, var x: Float, var y: Float, hpScale: Float, val dmgScale: Float) {
    var hp = type.hp * hpScale
    val maxHp = hp
    var hitFlash = 0f
    // Таймеры получения периодического урона (аура, орбита).
    var auraTick = 0f
    var orbitTick = 0f
    var knockX = 0f
    var knockY = 0f

    val damage: Float get() = type.damage * dmgScale
}

class Projectile(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val damage: Float,
    val radius: Float,
    var pierce: Int,
    var life: Float = 1.6f,
    val color: Int = Color.rgb(129, 212, 250),
)

enum class PickupType { XP, HP }

class Pickup(val type: PickupType, var x: Float, var y: Float, val value: Float) {
    var vx = 0f
    var vy = 0f
    var magnetized = false
}

class FloatingText(
    var x: Float,
    var y: Float,
    val text: String,
    val color: Int,
    val size: Float = 30f,
    var life: Float = 0.9f,
) {
    val maxLife = life
}

fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
    val dx = x2 - x1
    val dy = y2 - y1
    return sqrt(dx * dx + dy * dy)
}
