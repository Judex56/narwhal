package com.megasurvivors.game

import android.graphics.Color
import kotlin.random.Random

/** Вариант на экране повышения уровня. */
class UpgradeOption(
    val title: String,
    val desc: String,
    val color: Int,
    val apply: (Player) -> Unit,
)

/**
 * Левел-ап качает ТОЛЬКО взятое в забег оружие (фолианты и предметы
 * настраиваются в меню и в игре не появляются). Когда всё оружие
 * прокачано до максимума — предлагаются запасные карточки.
 */
object UpgradePool {

    private val fallbacks = listOf(
        UpgradeOption(
            "Аптечка", "Восстанавливает 50% макс. здоровья", Color.rgb(102, 187, 106),
        ) { p -> p.hp = minOf(p.maxHp, p.hp + p.maxHp * 0.5f) },
        UpgradeOption(
            "Закалка", "+6% к урону до конца забега", Color.rgb(239, 83, 80),
        ) { p -> p.forged[Stat.DAMAGE] = (p.forged[Stat.DAMAGE] ?: 0f) + 0.06f },
        UpgradeOption(
            "Разгон", "+6% к скорости атаки до конца забега", Color.rgb(255, 202, 40),
        ) { p -> p.forged[Stat.COOLDOWN] = (p.forged[Stat.COOLDOWN] ?: 0f) + 0.06f },
        UpgradeOption(
            "Стойкость", "+8% к макс. здоровью до конца забега", Color.rgb(129, 199, 132),
        ) { p -> p.forged[Stat.MAX_HP] = (p.forged[Stat.MAX_HP] ?: 0f) + 0.08f },
    )

    fun rollOptions(player: Player, rng: Random): List<UpgradeOption> {
        val options = ArrayList<UpgradeOption>()
        // Взятое оружие.
        for (w in player.weapons) {
            if (w.level < MAX_WEAPON_LEVEL) {
                options.add(
                    UpgradeOption(
                        "${w.displayName}: ур. ${w.level} → ${w.level + 1}",
                        WeaponBalance.upgradeDesc(w.type, w.level),
                        w.displayColor,
                    ) { p -> p.weapons.first { it.type == w.type }.level++ },
                )
            }
        }
        // Взятые фолианты — та же механика, что и у оружия.
        for ((tome, lvl) in player.tomes) {
            if (lvl < MAX_TOME_LEVEL) {
                options.add(
                    UpgradeOption(
                        "${tome.label}: ур. $lvl → ${lvl + 1}",
                        tome.upgradeDesc(lvl),
                        tome.color,
                    ) { p ->
                        val cur = p.tomes[tome] ?: 0
                        p.tomes[tome] = cur + 1
                        val hpGain = tome.stats[Stat.MAX_HP]
                        if (hpGain != null) p.hp += p.maxHp * hpGain * 0.5f
                    },
                )
            }
        }
        options.shuffle(rng)
        val result = options.take(3).toMutableList()
        if (result.size < 3) {
            result.addAll(fallbacks.shuffled(rng).take(3 - result.size))
        }
        return result
    }
}
