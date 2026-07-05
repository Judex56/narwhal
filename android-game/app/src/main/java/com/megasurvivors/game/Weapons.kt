package com.megasurvivors.game

import android.graphics.Color

const val MAX_WEAPON_LEVEL = 20

/**
 * Все оружия. price — стоимость открытия в меню (0 = открыто сразу
 * или недоступно для выбора, см. [evolved]).
 */
enum class WeaponType(
    val label: String,
    val desc: String,
    val color: Int,
    val price: Int = 0,
    val evolved: Boolean = false,
) {
    DART("Дротики", "Летят в ближайшего врага", Color.rgb(129, 212, 250)),
    ORBIT("Клинки", "Вращаются вокруг героя", Color.rgb(206, 147, 216), 100),
    AURA("Аура огня", "Жжёт врагов вокруг", Color.rgb(255, 138, 101), 150),
    LIGHTNING("Молния", "Бьёт случайных врагов", Color.rgb(255, 241, 118), 200),

    SCYTHE("Коса", "Широкий круговой взмах", Color.rgb(207, 216, 220), 250),
    FROST_AURA("Морозная аура", "Ранит и замедляет вокруг", Color.rgb(128, 222, 234), 300),
    COUNTER_SPIN("Ответный вихрь", "Крутится при получении урона", Color.rgb(255, 112, 67), 350),
    POISON("Ядовитые ножи", "Отравляют врагов ядом", Color.rgb(156, 204, 101), 350),
    NUKE("Судный час", "Зачистка экрана, долгий кулдаун", Color.rgb(255, 82, 82), 500),
    BANANA("Банан", "Бумеранг: летит и возвращается", Color.rgb(255, 238, 88), 250),
    PSI_BLADES("Пси-клинки", "Атака пробивает всех на линии", Color.rgb(186, 104, 200), 400),
    METEOR("Метеоры", "Случайные взрывы вокруг", Color.rgb(255, 167, 38), 450),
    SHOTGUN("Дробовик", "Веер дроби в упор", Color.rgb(189, 189, 189), 300),
    GREED_BLADE("Клинок жажды", "Урон растёт с каждым убийством", Color.rgb(240, 98, 146), 600),

    // Эволюции — получаются только комбинацией оружия 20 ур. + катализатора.
    ICE_STORM("Ледяная буря", "Эволюция Морозной ауры", Color.rgb(0, 229, 255), evolved = true),
    PLAGUE_CLOUD("Чумное облако", "Эволюция Ядовитых ножей", Color.rgb(118, 255, 3), evolved = true),
    REAPER("Жнец", "Эволюция Косы", Color.rgb(213, 0, 249), evolved = true);

    /** Двухбуквенный код для иконок. */
    val code: String get() = label.take(2).uppercase()
}

class WeaponInstance(var type: WeaponType, var level: Int = 1) {
    var timer = 0f
    var orbitAngle = 0f
    /** Счётчик стаков Клинка жажды (+урон за убийства). */
    var stacks = 0
}

/** Правило эволюции: оружие 20 ур. + предмет-катализатор → супероружие. */
class EvolutionDef(val base: WeaponType, val catalyst: Special, val result: WeaponType)

val EVOLUTIONS = listOf(
    EvolutionDef(WeaponType.FROST_AURA, Special.FROST_CUBE, WeaponType.ICE_STORM),
    EvolutionDef(WeaponType.POISON, Special.PLAGUE_HEART, WeaponType.PLAGUE_CLOUD),
    EvolutionDef(WeaponType.SCYTHE, Special.DARK_SICKLE, WeaponType.REAPER),
)

/** Числовые параметры оружия по уровням (1..20). */
object WeaponBalance {

    // Дротики.
    fun dartDamage(l: Int) = 10f + 2.5f * (l - 1)
    fun dartCount(l: Int) = 1 + l / 4 // 1..6
    fun dartCooldown(l: Int) = (1.0f - 0.025f * (l - 1)).coerceAtLeast(0.5f)

    // Клинки на орбите.
    fun orbitDamage(l: Int) = 8f + 2.5f * (l - 1)
    fun orbitCount(l: Int) = 2 + l / 3 // 2..8
    fun orbitRadius(l: Int) = 95f + 5f * (l - 1)
    const val ORBIT_HIT_INTERVAL = 0.35f

    // Аура огня.
    fun auraDamage(l: Int) = 4f + 1.6f * (l - 1)
    fun auraRadius(l: Int) = 120f + 9f * (l - 1)
    const val AURA_TICK = 0.5f

    // Молния.
    fun lightningDamage(l: Int) = 22f + 6f * (l - 1)
    fun lightningTargets(l: Int) = 1 + l / 3 // 1..7
    fun lightningCooldown(l: Int) = (2.2f - 0.07f * (l - 1)).coerceAtLeast(0.9f)

    // Коса: один большой клинок, метущий по кругу.
    fun scytheDamage(l: Int) = 14f + 3.5f * (l - 1)
    fun scytheRadius(l: Int) = 150f + 7f * (l - 1)
    fun scytheSpeed(l: Int) = 2.4f + 0.08f * (l - 1) // рад/с
    const val SCYTHE_HIT_INTERVAL = 0.4f

    // Морозная аура: слабее огня, но замедляет.
    fun frostDamage(l: Int) = 3f + 1.2f * (l - 1)
    fun frostRadius(l: Int) = 130f + 8f * (l - 1)
    fun frostSlow(l: Int) = (0.65f - 0.012f * (l - 1)).coerceAtLeast(0.35f) // множитель скорости
    const val FROST_TICK = 0.5f

    // Ответный вихрь (Axe): срабатывает при получении урона.
    fun spinDamage(l: Int) = 20f + 6f * (l - 1)
    fun spinRadius(l: Int) = 190f + 6f * (l - 1)
    fun spinCooldown(l: Int) = (2.5f - 0.08f * (l - 1)).coerceAtLeast(0.8f)

    // Ядовитые ножи: стаки яда, тикающий урон.
    fun poisonDamage(l: Int) = 6f + 1.5f * (l - 1) // прямой урон ножа
    fun poisonDps(l: Int) = 3f + 1.2f * (l - 1) // урон в сек за стак
    fun poisonCount(l: Int) = 1 + l / 5 // 1..5 ножей
    fun poisonCooldown(l: Int) = (1.3f - 0.03f * (l - 1)).coerceAtLeast(0.6f)
    const val POISON_DURATION = 4f
    const val MAX_POISON_STACKS = 5

    // Судный час: зачистка экрана.
    fun nukeDamage(l: Int) = 120f + 30f * (l - 1)
    fun nukeCooldown(l: Int) = (30f - 0.7f * (l - 1)).coerceAtLeast(15f)

    // Банан-бумеранг.
    fun bananaDamage(l: Int) = 12f + 3f * (l - 1)
    fun bananaCount(l: Int) = 1 + l / 5 // 1..5
    fun bananaCooldown(l: Int) = (1.6f - 0.04f * (l - 1)).coerceAtLeast(0.7f)

    // Пси-клинки: пробивающий луч через всех на линии.
    fun psiDamage(l: Int) = 16f + 4f * (l - 1)
    fun psiLength(l: Int) = 500f + 20f * (l - 1)
    fun psiWidth(l: Int) = 46f + 2f * (l - 1)
    fun psiCooldown(l: Int) = (1.1f - 0.025f * (l - 1)).coerceAtLeast(0.55f)

    // Метеоры: случайные взрывы вокруг героя.
    fun meteorDamage(l: Int) = 30f + 8f * (l - 1)
    fun meteorCount(l: Int) = 2 + l / 4 // 2..7
    fun meteorRadius(l: Int) = 110f + 4f * (l - 1)
    fun meteorCooldown(l: Int) = (3.0f - 0.08f * (l - 1)).coerceAtLeast(1.2f)

    // Дробовик.
    fun shotgunDamage(l: Int) = 7f + 1.8f * (l - 1) // за дробину
    fun shotgunPellets(l: Int) = 5 + l / 3 // 5..11
    fun shotgunCooldown(l: Int) = (1.5f - 0.035f * (l - 1)).coerceAtLeast(0.75f)
    const val SHOTGUN_RANGE = 0.45f // сек жизни дроби

    // Клинок жажды: базовый урон скромный, но растёт с убийствами без предела.
    fun greedDamage(l: Int, stacks: Int) = 8f + 2f * (l - 1) + stacks * 0.4f
    fun greedCooldown(l: Int) = (1.2f - 0.03f * (l - 1)).coerceAtLeast(0.6f)

    // --- Эволюции ---
    const val ICE_STORM_DMG = 45f
    const val ICE_STORM_RADIUS = 330f
    const val ICE_STORM_SLOW = 0.3f
    const val ICE_STORM_FREEZE_EVERY = 5f
    const val ICE_STORM_FREEZE_TIME = 1.3f

    const val PLAGUE_DMG = 25f
    const val PLAGUE_RADIUS = 280f
    const val PLAGUE_DPS_PER_STACK = 12f

    const val REAPER_DMG = 90f
    const val REAPER_RADIUS = 230f
    const val REAPER_SPEED = 4.5f
    const val REAPER_BLADES = 2

    fun levelUpText(type: WeaponType, l: Int): String = when (type) {
        WeaponType.DART -> "Урон ${dartDamage(l).toInt()}, снарядов ${dartCount(l)}"
        WeaponType.ORBIT -> "Урон ${orbitDamage(l).toInt()}, клинков ${orbitCount(l)}"
        WeaponType.AURA -> "Урон ${auraDamage(l).toInt()}/тик, радиус ${auraRadius(l).toInt()}"
        WeaponType.LIGHTNING -> "Урон ${lightningDamage(l).toInt()}, целей ${lightningTargets(l)}"
        WeaponType.SCYTHE -> "Урон ${scytheDamage(l).toInt()}, радиус ${scytheRadius(l).toInt()}"
        WeaponType.FROST_AURA -> "Урон ${frostDamage(l).toInt()}/тик, замедление сильнее"
        WeaponType.COUNTER_SPIN -> "Урон ${spinDamage(l).toInt()}, кулдаун ${"%.1f".format(spinCooldown(l))}с"
        WeaponType.POISON -> "Яд ${poisonDps(l).toInt()}/с за стак, ножей ${poisonCount(l)}"
        WeaponType.NUKE -> "Урон ${nukeDamage(l).toInt()}, кулдаун ${nukeCooldown(l).toInt()}с"
        WeaponType.BANANA -> "Урон ${bananaDamage(l).toInt()}, бананов ${bananaCount(l)}"
        WeaponType.PSI_BLADES -> "Урон ${psiDamage(l).toInt()}, длина ${psiLength(l).toInt()}"
        WeaponType.METEOR -> "Урон ${meteorDamage(l).toInt()}, метеоров ${meteorCount(l)}"
        WeaponType.SHOTGUN -> "Урон ${shotgunDamage(l).toInt()}x${shotgunPellets(l)} дроби"
        WeaponType.GREED_BLADE -> "База ${greedDamage(l, 0).toInt()} (+0.4 за убийство)"
        else -> ""
    }
}
