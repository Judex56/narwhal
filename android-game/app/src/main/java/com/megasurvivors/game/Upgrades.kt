package com.megasurvivors.game

import kotlin.random.Random

/** Вариант на экране повышения уровня. */
class UpgradeOption(
    val title: String,
    val desc: String,
    val color: Int,
    val apply: (Player) -> Unit,
)

object UpgradePool {

    private class PassiveDef(val stat: Stat, val amount: Float, val title: String, val desc: String)

    private val passives = listOf(
        PassiveDef(Stat.DAMAGE, 0.10f, "Сила", "+10% к урону"),
        PassiveDef(Stat.COOLDOWN, 0.12f, "Ловкость", "+12% к скорости атаки"),
        PassiveDef(Stat.MOVE_SPEED, 0.08f, "Бег", "+8% к скорости бега"),
        PassiveDef(Stat.MAX_HP, 0.15f, "Живучесть", "+15% к макс. здоровью"),
        PassiveDef(Stat.ARMOR, 0.15f, "Кожа камня", "+15% к броне"),
        PassiveDef(Stat.MAGNET, 0.25f, "Магнит", "+25% к радиусу сбора"),
        PassiveDef(Stat.AREA, 0.10f, "Размах", "+10% к области атак"),
        PassiveDef(Stat.XP_GAIN, 0.12f, "Мудрость", "+12% к опыту"),
        PassiveDef(Stat.CRIT_CHANCE, 0.05f, "Меткость", "+5% к шансу крита"),
        PassiveDef(Stat.GOLD_GAIN, 0.15f, "Алчность", "+15% к золоту"),
    )

    /**
     * Собрать 3 случайных варианта. Новые оружия предлагаются только
     * из открытых игроком в меню ([unlockedWeapons]).
     */
    fun rollOptions(player: Player, rng: Random, unlockedWeapons: Set<String>): List<UpgradeOption> {
        val options = ArrayList<UpgradeOption>()

        // Апгрейды имеющегося оружия — добавляем дважды, чтобы
        // качать оружие было проще, чем набирать пассивки.
        for (w in player.weapons) {
            if (w.level < MAX_WEAPON_LEVEL && !w.type.evolved) {
                val opt = UpgradeOption(
                    "${w.type.label} ур. ${w.level + 1}",
                    WeaponBalance.levelUpText(w.type, w.level + 1),
                    w.type.color,
                ) { p -> p.weapons.first { it.type == w.type }.level++ }
                options.add(opt)
                options.add(opt)
            }
        }
        // Новое оружие (максимум 4 слота) — только из открытых.
        if (player.weapons.size < 4) {
            for (type in WeaponType.entries) {
                if (type.evolved) continue
                if (!unlockedWeapons.contains(type.name)) continue
                if (player.weapons.none { it.type == type }) {
                    options.add(
                        UpgradeOption("НОВОЕ: ${type.label}", type.desc, type.color) { p ->
                            p.weapons.add(WeaponInstance(type))
                        },
                    )
                }
            }
        }
        // Пассивки.
        for (def in passives) {
            options.add(
                UpgradeOption(def.title, def.desc, 0xFF90A4AE.toInt()) { p ->
                    p.passives[def.stat] = (p.passives[def.stat] ?: 0f) + def.amount
                    if (def.stat == Stat.MAX_HP) p.hp += p.maxHp * def.amount * 0.5f
                },
            )
        }

        options.shuffle(rng)
        // Убираем дубли одинаковых заголовков в выдаче.
        val seen = HashSet<String>()
        return options.filter { seen.add(it.title) }.take(3)
    }
}
