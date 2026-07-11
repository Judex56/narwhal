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
    /** Собранные предметы (Megabonk-стиль: копятся списком). */
    val items = ArrayList<ItemDef>()
    val buffs = ArrayList<Buff>()
    /** Взятые в забег фолианты и их уровни (качаются как оружие). */
    val tomes = HashMap<Tome, Int>()
    /** Мелкие постоянные бонусы с запасных карточек левел-апа. */
    val forged = HashMap<Stat, Float>()

    private val baseSpeed = 230f
    private val baseMaxHp = 100f

    fun bonus(stat: Stat): Float {
        var sum = forged[stat] ?: 0f
        for ((tome, lvl) in tomes) sum += (tome.stats[stat] ?: 0f) * lvl
        for (item in items) sum += item.stats[stat] ?: 0f
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

    fun hasSpecial(s: Special): Boolean = items.any { it.special == s }
    fun countSpecial(s: Special): Int = items.count { it.special == s }
}

enum class EnemyType(
    val hp: Float,
    val speed: Float,
    val damage: Float,
    val radius: Float,
    val xp: Float,
    val gold: Int,
    val color: Int,
) {
    WALKER(22f, 95f, 8f, 22f, 1f, 1, Color.rgb(141, 110, 99)),
    RUNNER(14f, 185f, 6f, 17f, 1.5f, 1, Color.rgb(255, 112, 67)),
    TANK(90f, 60f, 16f, 34f, 4f, 3, Color.rgb(84, 110, 122)),
    BRUTE(45f, 110f, 12f, 27f, 2.5f, 2, Color.rgb(124, 179, 66)),
    ELITE(600f, 85f, 24f, 48f, 25f, 30, Color.rgb(255, 213, 79)),
    MINIBOSS(2600f, 82f, 30f, 64f, 60f, 0, Color.rgb(233, 30, 99)),
    BOSS(42000f, 72f, 40f, 96f, 100f, 300, Color.rgb(103, 58, 183)),
}

class Enemy(
    val type: EnemyType,
    var x: Float,
    var y: Float,
    hpScale: Float,
    val dmgScale: Float,
    /** Множитель скорости (враги ускоряются к концу забега). */
    val speedScale: Float = 1f,
) {
    var hp = type.hp * hpScale
    val maxHp = hp
    var hitFlash = 0f
    /** Фаза анимации (пульсация), чтобы враги не дёргались синхронно. */
    val animPhase = (System.identityHashCode(this) and 0xFF) / 255f * 6.28f

    // Атаки босса.
    var bossChargeTimer = 6f
    var bossShootTimer = 9f

    // Таймеры получения периодического урона от разных источников.
    var fireTick = 0f
    var frostTick = 0f
    var orbitTick = 0f
    var scytheTick = 0f
    var bananaTick = 0f
    var plagueTick = 0f

    // Дебаффы.
    var slowTimer = 0f
    var slowMult = 1f
    var freezeTimer = 0f
    var poisonStacks = 0
    var poisonTimer = 0f
    var poisonDps = 0f
    var poisonAcc = 0f

    var knockX = 0f
    var knockY = 0f

    val damage: Float get() = type.damage * dmgScale

    val effectiveSpeed: Float
        get() = when {
            freezeTimer > 0f -> 0f
            // Босса нельзя замедлить ниже 70%.
            slowTimer > 0f -> type.speed * speedScale *
                (if (type == EnemyType.BOSS) slowMult.coerceAtLeast(0.7f) else slowMult)
            else -> type.speed * speedScale
        }
}

/** Частица для эффектов: искры попаданий, смерть врагов, взрывы. */
class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    val color: Int,
    val size: Float,
) {
    val maxLife = life
}

enum class ProjKind { DART, POISON, BANANA, PELLET, SLASH }

class Projectile(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val damage: Float,
    val radius: Float,
    var pierce: Int,
    var life: Float = 1.6f,
    val kind: ProjKind = ProjKind.DART,
    val color: Int = Color.rgb(129, 212, 250),
) {
    /** Для банана: возраст и фаза возврата. */
    var age = 0f
    var returning = false
}

/** Отложенный взрыв (метеоры): падает delay сек, потом бахает. */
class Boom(
    var x: Float,
    var y: Float,
    var delay: Float,
    val radius: Float,
    val damage: Float,
) {
    var exploded = false
    var flash = 0f // остаток визуальной вспышки после взрыва
}

enum class PickupType { XP, HP, GOLD }

class Pickup(val type: PickupType, var x: Float, var y: Float, val value: Float) {
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
