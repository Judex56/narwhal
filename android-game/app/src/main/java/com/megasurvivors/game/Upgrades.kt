package com.megasurvivors.game

import kotlin.random.Random

/** Вариант на экране повышения уровня. */
class UpgradeOption(
    val title: String,
    val desc: String,
    val color: Int,
    val apply: (Player) -> Unit,
)

/**
 * Каждый левел-ап — выбор из 3 вариантов: прокачать одно из своих
 * оружий, взять/прокачать фолиант или взять новое оружие.
 */
object UpgradePool {

    fun rollOptions(player: Player, rng: Random, unlockedWeapons: Set<String>): List<UpgradeOption> {
        val options = ArrayList<UpgradeOption>()

        // Прокачка имеющегося оружия — двойной вес, чтобы оружие
        // предлагалось чаще фолиантов.
        for (w in player.weapons) {
            if (w.level < MAX_WEAPON_LEVEL) {
                val opt = UpgradeOption(
                    "${w.displayName}: ур. ${w.level} → ${w.level + 1}",
                    WeaponBalance.upgradeDesc(w.type, w.level),
                    w.displayColor,
                ) { p -> p.weapons.first { it.type == w.type }.level++ }
                options.add(opt)
                options.add(opt)
            }
        }

        // Новое оружие — лимита на количество больше нет,
        // предлагаются все открытые в меню.
        for (type in WeaponType.entries) {
            if (!unlockedWeapons.contains(type.name)) continue
            if (player.weapons.none { it.type == type }) {
                options.add(
                    UpgradeOption(
                        "НОВОЕ ОРУЖИЕ: ${type.label}",
                        "${type.desc} • ${WeaponBalance.levelUpText(type, 1)}",
                        type.color,
                    ) { p -> p.weapons.add(WeaponInstance(type)) },
                )
            }
        }

        // Фолианты: взять новый или прокачать имеющийся (до 10 ур.).
        for (tome in Tome.entries) {
            val cur = player.tomes[tome] ?: 0
            if (cur >= MAX_TOME_LEVEL) continue
            val title = if (cur == 0) {
                "НОВЫЙ: ${tome.label}"
            } else {
                "${tome.label}: ур. $cur → ${cur + 1}"
            }
            options.add(
                UpgradeOption(title, tome.upgradeDesc(cur), tome.color) { p ->
                    p.tomes[tome] = cur + 1
                    // Прибавка к макс. HP сразу лечит на половину прибавки.
                    val hpGain = tome.stats[Stat.MAX_HP]
                    if (hpGain != null) p.hp += p.maxHp * hpGain * 0.5f
                },
            )
        }

        options.shuffle(rng)
        val seen = HashSet<String>()
        return options.filter { seen.add(it.title) }.take(3)
    }
}
