package com.megasurvivors.game

import android.graphics.Color
import kotlin.random.Random

enum class Rarity(val label: String, val color: Int, val statCount: Int, val power: Float) {
    COMMON("Обычный", Color.rgb(176, 190, 197), 1, 0.08f),
    RARE("Редкий", Color.rgb(66, 165, 245), 2, 0.12f),
    EPIC("Эпический", Color.rgb(171, 71, 188), 3, 0.16f),
    LEGENDARY("Легендарный", Color.rgb(255, 179, 0), 4, 0.22f),
}

enum class Slot(val label: String) {
    CHARM("Талисман"),
    ARMOR("Броня"),
    BOOTS("Сапоги"),
    AMULET("Амулет"),
    RING("Кольцо"),
}

class Item(
    val slot: Slot,
    val rarity: Rarity,
    val name: String,
    val stats: Map<Stat, Float>,
) {
    /** Суммарная "сила" предмета для сравнения со старым. */
    val score: Float get() = stats.values.sum()
}

object ItemGenerator {

    // Какие статы характерны для какого слота (первый — основной).
    private val slotStats = mapOf(
        Slot.CHARM to listOf(Stat.DAMAGE, Stat.PROJ_SPEED, Stat.AREA, Stat.COOLDOWN),
        Slot.ARMOR to listOf(Stat.ARMOR, Stat.MAX_HP, Stat.DAMAGE, Stat.AREA),
        Slot.BOOTS to listOf(Stat.MOVE_SPEED, Stat.MAGNET, Stat.COOLDOWN, Stat.ARMOR),
        Slot.AMULET to listOf(Stat.MAGNET, Stat.XP_GAIN, Stat.MAX_HP, Stat.DAMAGE),
        Slot.RING to listOf(Stat.COOLDOWN, Stat.DAMAGE, Stat.XP_GAIN, Stat.PROJ_SPEED),
    )

    private val prefixes = mapOf(
        Rarity.COMMON to listOf("Потёртый", "Простой", "Старый"),
        Rarity.RARE to listOf("Кованый", "Охотничий", "Боевой"),
        Rarity.EPIC to listOf("Рунический", "Проклятый", "Драконий"),
        Rarity.LEGENDARY to listOf("Древний", "Божественный", "Мифический"),
    )

    fun roll(rng: Random, minute: Int): Item {
        // Чем дольше игра, тем выше шанс редких вещей.
        val bonus = (minute * 0.02f).coerceAtMost(0.25f)
        val r = rng.nextFloat()
        val rarity = when {
            r < 0.06f + bonus -> Rarity.LEGENDARY
            r < 0.20f + bonus -> Rarity.EPIC
            r < 0.50f + bonus -> Rarity.RARE
            else -> Rarity.COMMON
        }
        val slot = Slot.entries[rng.nextInt(Slot.entries.size)]
        val pool = slotStats.getValue(slot)
        val stats = LinkedHashMap<Stat, Float>()
        // Основной стат слота всегда присутствует.
        stats[pool[0]] = roundStat(rarity.power * (0.9f + rng.nextFloat() * 0.5f))
        var i = 1
        while (stats.size < rarity.statCount && i < pool.size) {
            stats[pool[i]] = roundStat(rarity.power * (0.5f + rng.nextFloat() * 0.5f))
            i++
        }
        val prefix = prefixes.getValue(rarity).random(rng)
        return Item(slot, rarity, "$prefix ${slot.label.lowercase()}", stats)
    }

    private fun roundStat(v: Float): Float = (v * 100).toInt() / 100f
}
