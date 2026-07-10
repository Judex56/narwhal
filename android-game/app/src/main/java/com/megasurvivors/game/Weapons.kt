package com.megasurvivors.game

import android.graphics.Color

const val MAX_WEAPON_LEVEL = 20

/** Все оружия. price — стоимость открытия в меню (0 = открыто сразу). */
enum class WeaponType(
    val label: String,
    val desc: String,
    val color: Int,
    val price: Int = 0,
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
    GREED_BLADE("Клинок жажды", "Урон растёт с каждым убийством", Color.rgb(240, 98, 146), 600);

    /** Двухбуквенный код для иконок. */
    val code: String get() = label.take(2).uppercase()
}

class WeaponInstance(val type: WeaponType, var level: Int = 1) {
    var timer = 0f
    var orbitAngle = 0f
    /** Второй таймер (заморозка Ледяной бури, автоспин Вечного вихря). */
    var extraTimer = 0f
    /** Счётчик стаков Клинка жажды (+урон за убийства). */
    var stacks = 0
    /** Эволюционировано катализатором на 20 уровне. */
    var evolved = false

    val displayName: String
        get() = if (evolved) EVOLUTIONS.getValue(type).label else type.label

    val displayColor: Int
        get() = if (evolved) EVOLUTIONS.getValue(type).color else type.color
}

/** Эволюция: оружие 20 ур. + предмет-катализатор → супер-форма. */
class EvolutionDef(val label: String, val color: Int, val desc: String)

/** У КАЖДОГО оружия есть эволюция через свой предмет. */
val EVOLUTIONS: Map<WeaponType, EvolutionDef> = mapOf(
    WeaponType.DART to EvolutionDef(
        "Стальной шквал", Color.rgb(3, 169, 244),
        "Урон ×2, +2 снаряда, +2 пробития, перезарядка -25%",
    ),
    WeaponType.ORBIT to EvolutionDef(
        "Танец тысячи клинков", Color.rgb(224, 64, 251),
        "Урон ×2, +3 клинка, радиус +30%",
    ),
    WeaponType.AURA to EvolutionDef(
        "Инферно", Color.rgb(255, 61, 0),
        "Урон ×2.2, радиус +40%",
    ),
    WeaponType.LIGHTNING to EvolutionDef(
        "Гроза", Color.rgb(255, 234, 0),
        "Урон ×2, +3 цели, перезарядка -20%",
    ),
    WeaponType.SCYTHE to EvolutionDef(
        "Жнец", Color.rgb(213, 0, 249),
        "Вторая коса, урон ×2, скорость ×1.5, +1 HP за удар",
    ),
    WeaponType.FROST_AURA to EvolutionDef(
        "Ледяная буря", Color.rgb(0, 229, 255),
        "Урон ×2.5, радиус +50%, заморозка всех каждые 5с",
    ),
    WeaponType.COUNTER_SPIN to EvolutionDef(
        "Вечный вихрь", Color.rgb(255, 111, 0),
        "Урон ×2, перезарядка -40%, автоспин каждые 5с",
    ),
    WeaponType.POISON to EvolutionDef(
        "Чумное облако", Color.rgb(118, 255, 3),
        "Яд ×2, +2 ножа, прямой урон ×2",
    ),
    WeaponType.NUKE to EvolutionDef(
        "Апокалипсис", Color.rgb(255, 23, 68),
        "Урон ×2, перезарядка -40%",
    ),
    WeaponType.BANANA to EvolutionDef(
        "Банановый шторм", Color.rgb(255, 214, 0),
        "Урон ×2, +2 банана",
    ),
    WeaponType.PSI_BLADES to EvolutionDef(
        "Разлом разума", Color.rgb(213, 0, 249),
        "Урон ×2, ширина +50%, длина +30%",
    ),
    WeaponType.METEOR to EvolutionDef(
        "Метеоритный дождь", Color.rgb(255, 109, 0),
        "Урон ×1.8, +3 метеора, радиус +30%",
    ),
    WeaponType.SHOTGUN to EvolutionDef(
        "Адская картечь", Color.rgb(255, 82, 82),
        "Урон ×1.6, +6 дробин",
    ),
    WeaponType.GREED_BLADE to EvolutionDef(
        "Клинок голода", Color.rgb(255, 64, 129),
        "Урон ×1.5, +1.0 урона за убийство, перезарядка -20%",
    ),
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

    // Коса.
    fun scytheDamage(l: Int) = 14f + 3.5f * (l - 1)
    fun scytheRadius(l: Int) = 150f + 7f * (l - 1)
    fun scytheSpeed(l: Int) = 2.4f + 0.08f * (l - 1) // рад/с
    const val SCYTHE_HIT_INTERVAL = 0.4f

    // Морозная аура.
    fun frostDamage(l: Int) = 3f + 1.2f * (l - 1)
    fun frostRadius(l: Int) = 130f + 8f * (l - 1)
    fun frostSlow(l: Int) = (0.65f - 0.012f * (l - 1)).coerceAtLeast(0.35f)
    const val FROST_TICK = 0.5f
    const val FREEZE_EVERY = 5f
    const val FREEZE_TIME = 1.3f

    // Ответный вихрь.
    fun spinDamage(l: Int) = 20f + 6f * (l - 1)
    fun spinRadius(l: Int) = 190f + 6f * (l - 1)
    fun spinCooldown(l: Int) = (2.5f - 0.08f * (l - 1)).coerceAtLeast(0.8f)
    const val AUTO_SPIN_EVERY = 5f

    // Ядовитые ножи.
    fun poisonDamage(l: Int) = 6f + 1.5f * (l - 1)
    fun poisonDps(l: Int) = 3f + 1.2f * (l - 1)
    fun poisonCount(l: Int) = 1 + l / 5 // 1..5
    fun poisonCooldown(l: Int) = (1.3f - 0.03f * (l - 1)).coerceAtLeast(0.6f)
    const val POISON_DURATION = 4f
    const val MAX_POISON_STACKS = 5

    // Судный час.
    fun nukeDamage(l: Int) = 120f + 30f * (l - 1)
    fun nukeCooldown(l: Int) = (30f - 0.7f * (l - 1)).coerceAtLeast(15f)

    // Банан.
    fun bananaDamage(l: Int) = 12f + 3f * (l - 1)
    fun bananaCount(l: Int) = 1 + l / 5 // 1..5
    fun bananaCooldown(l: Int) = (1.6f - 0.04f * (l - 1)).coerceAtLeast(0.7f)

    // Пси-клинки.
    fun psiDamage(l: Int) = 16f + 4f * (l - 1)
    fun psiLength(l: Int) = 500f + 20f * (l - 1)
    fun psiWidth(l: Int) = 46f + 2f * (l - 1)
    fun psiCooldown(l: Int) = (1.1f - 0.025f * (l - 1)).coerceAtLeast(0.55f)

    // Метеоры.
    fun meteorDamage(l: Int) = 30f + 8f * (l - 1)
    fun meteorCount(l: Int) = 2 + l / 4 // 2..7
    fun meteorRadius(l: Int) = 110f + 4f * (l - 1)
    fun meteorCooldown(l: Int) = (3.0f - 0.08f * (l - 1)).coerceAtLeast(1.2f)

    // Дробовик.
    fun shotgunDamage(l: Int) = 7f + 1.8f * (l - 1)
    fun shotgunPellets(l: Int) = 5 + l / 3 // 5..11
    fun shotgunCooldown(l: Int) = (1.5f - 0.035f * (l - 1)).coerceAtLeast(0.75f)
    const val SHOTGUN_RANGE = 0.45f

    // Клинок жажды.
    fun greedDamage(l: Int, stacks: Int, stackValue: Float = 0.4f) =
        8f + 2f * (l - 1) + stacks * stackValue
    fun greedCooldown(l: Int) = (1.2f - 0.03f * (l - 1)).coerceAtLeast(0.6f)

    private fun f(v: Float): String = "%.2f".format(v).trimEnd('0').trimEnd('.')

    /**
     * Читабельное описание апгрейда: что и насколько изменится
     * при переходе с уровня [from] на [from]+1.
     */
    fun upgradeDesc(type: WeaponType, from: Int): String {
        val to = from + 1
        return when (type) {
            WeaponType.DART ->
                "Урон ${dartDamage(from).toInt()} → ${dartDamage(to).toInt()}" +
                    " • Снарядов ${dartCount(from)} → ${dartCount(to)}" +
                    " • Перезарядка ${f(dartCooldown(from))}с → ${f(dartCooldown(to))}с"
            WeaponType.ORBIT ->
                "Урон ${orbitDamage(from).toInt()} → ${orbitDamage(to).toInt()}" +
                    " • Клинков ${orbitCount(from)} → ${orbitCount(to)}" +
                    " • Радиус ${orbitRadius(from).toInt()} → ${orbitRadius(to).toInt()}"
            WeaponType.AURA ->
                "Урон ${auraDamage(from).toInt()} → ${auraDamage(to).toInt()} каждые 0.5с" +
                    " • Радиус ${auraRadius(from).toInt()} → ${auraRadius(to).toInt()}"
            WeaponType.LIGHTNING ->
                "Урон ${lightningDamage(from).toInt()} → ${lightningDamage(to).toInt()}" +
                    " • Целей ${lightningTargets(from)} → ${lightningTargets(to)}" +
                    " • Перезарядка ${f(lightningCooldown(from))}с → ${f(lightningCooldown(to))}с"
            WeaponType.SCYTHE ->
                "Урон ${scytheDamage(from).toInt()} → ${scytheDamage(to).toInt()}" +
                    " • Радиус ${scytheRadius(from).toInt()} → ${scytheRadius(to).toInt()}" +
                    " • Скорость вращения выше"
            WeaponType.FROST_AURA ->
                "Урон ${frostDamage(from).toInt()} → ${frostDamage(to).toInt()} каждые 0.5с" +
                    " • Радиус ${frostRadius(from).toInt()} → ${frostRadius(to).toInt()}" +
                    " • Замедление ${100 - (frostSlow(from) * 100).toInt()}% → ${100 - (frostSlow(to) * 100).toInt()}%"
            WeaponType.COUNTER_SPIN ->
                "Урон ${spinDamage(from).toInt()} → ${spinDamage(to).toInt()}" +
                    " • Радиус ${spinRadius(from).toInt()} → ${spinRadius(to).toInt()}" +
                    " • Перезарядка ${f(spinCooldown(from))}с → ${f(spinCooldown(to))}с"
            WeaponType.POISON ->
                "Яд ${poisonDps(from).toInt()} → ${poisonDps(to).toInt()} урона/с за стак (макс. 5 стаков)" +
                    " • Ножей ${poisonCount(from)} → ${poisonCount(to)}"
            WeaponType.NUKE ->
                "Урон ${nukeDamage(from).toInt()} → ${nukeDamage(to).toInt()} по всему экрану" +
                    " • Перезарядка ${nukeCooldown(from).toInt()}с → ${nukeCooldown(to).toInt()}с"
            WeaponType.BANANA ->
                "Урон ${bananaDamage(from).toInt()} → ${bananaDamage(to).toInt()} (бьёт туда и обратно)" +
                    " • Бананов ${bananaCount(from)} → ${bananaCount(to)}"
            WeaponType.PSI_BLADES ->
                "Урон ${psiDamage(from).toInt()} → ${psiDamage(to).toInt()} всем на линии" +
                    " • Длина ${psiLength(from).toInt()} → ${psiLength(to).toInt()}" +
                    " • Ширина ${psiWidth(from).toInt()} → ${psiWidth(to).toInt()}"
            WeaponType.METEOR ->
                "Урон ${meteorDamage(from).toInt()} → ${meteorDamage(to).toInt()} за взрыв" +
                    " • Метеоров ${meteorCount(from)} → ${meteorCount(to)}" +
                    " • Радиус взрыва ${meteorRadius(from).toInt()} → ${meteorRadius(to).toInt()}"
            WeaponType.SHOTGUN ->
                "Урон ${shotgunDamage(from).toInt()} → ${shotgunDamage(to).toInt()} за дробину" +
                    " • Дроби ${shotgunPellets(from)} → ${shotgunPellets(to)}" +
                    " • Перезарядка ${f(shotgunCooldown(from))}с → ${f(shotgunCooldown(to))}с"
            WeaponType.GREED_BLADE ->
                "База ${greedDamage(from, 0).toInt()} → ${greedDamage(to, 0).toInt()}" +
                    " • +0.4 урона за каждое убийство (без предела)" +
                    " • Перезарядка ${f(greedCooldown(from))}с → ${f(greedCooldown(to))}с"
        }
    }

    fun levelUpText(type: WeaponType, l: Int): String = when (type) {
        WeaponType.DART -> "Урон ${dartDamage(l).toInt()}, снарядов ${dartCount(l)}"
        WeaponType.ORBIT -> "Урон ${orbitDamage(l).toInt()}, клинков ${orbitCount(l)}"
        WeaponType.AURA -> "Урон ${auraDamage(l).toInt()}/тик, радиус ${auraRadius(l).toInt()}"
        WeaponType.LIGHTNING -> "Урон ${lightningDamage(l).toInt()}, целей ${lightningTargets(l)}"
        WeaponType.SCYTHE -> "Урон ${scytheDamage(l).toInt()}, радиус ${scytheRadius(l).toInt()}"
        WeaponType.FROST_AURA -> "Урон ${frostDamage(l).toInt()}/тик, замедляет врагов"
        WeaponType.COUNTER_SPIN -> "Урон ${spinDamage(l).toInt()} при получении удара"
        WeaponType.POISON -> "Яд ${poisonDps(l).toInt()}/с за стак, ножей ${poisonCount(l)}"
        WeaponType.NUKE -> "Урон ${nukeDamage(l).toInt()}, кулдаун ${nukeCooldown(l).toInt()}с"
        WeaponType.BANANA -> "Урон ${bananaDamage(l).toInt()}, бананов ${bananaCount(l)}"
        WeaponType.PSI_BLADES -> "Урон ${psiDamage(l).toInt()}, длина ${psiLength(l).toInt()}"
        WeaponType.METEOR -> "Урон ${meteorDamage(l).toInt()}, метеоров ${meteorCount(l)}"
        WeaponType.SHOTGUN -> "Урон ${shotgunDamage(l).toInt()}x${shotgunPellets(l)} дроби"
        WeaponType.GREED_BLADE -> "База ${greedDamage(l, 0).toInt()} (+0.4 за убийство)"
    }
}
