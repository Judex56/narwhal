package com.megasurvivors.game

import android.graphics.Color
import kotlin.random.Random

/** Тиры предметов. */
enum class Tier(val label: String, val color: Int, val baseWeight: Float) {
    COMMON("Обычный", Color.rgb(176, 190, 197), 45f),
    UNCOMMON("Необычный", Color.rgb(102, 187, 106), 27f),
    RARE("Редкий", Color.rgb(66, 165, 245), 16f),
    MYSTIC("Мистический", Color.rgb(171, 71, 188), 8f),
    LEGENDARY("Легендарный", Color.rgb(255, 179, 0), 4f),
}

/** Особые эффекты предметов, которые нельзя выразить голыми статами. */
enum class Special {
    NONE,
    FROST_CUBE, // катализатор эволюции Морозной ауры
    PLAGUE_HEART, // катализатор эволюции Яда
    DARK_SICKLE, // катализатор эволюции Косы
    LIFESTEAL, // +1 HP за убийство
    REGEN, // +1 HP в секунду
    DODGE, // 10% шанс избежать урона
}

class ItemDef(
    val id: String,
    val name: String,
    val tier: Tier,
    val stats: Map<Stat, Float>,
    val special: Special = Special.NONE,
    val desc: String = "",
) {
    val isCatalyst: Boolean
        get() = special == Special.FROST_CUBE ||
            special == Special.PLAGUE_HEART ||
            special == Special.DARK_SICKLE
}

/**
 * Каталог всех предметов и настраиваемый пул выпадения.
 * Вес тира можно крутить в [Tier.baseWeight]; со временем забега
 * шанс высоких тиров растёт.
 */
object ItemPool {

    val catalog: List<ItemDef> = listOf(
        // --- Обычные ---
        ItemDef(
            "whetstone", "Точильный камень", Tier.COMMON,
            mapOf(Stat.DAMAGE to 0.08f),
        ),
        ItemDef(
            "belt", "Кожаный ремень", Tier.COMMON,
            mapOf(Stat.MAX_HP to 0.10f),
        ),
        ItemDef(
            "feather", "Перо ястреба", Tier.COMMON,
            mapOf(Stat.MOVE_SPEED to 0.06f),
        ),
        ItemDef(
            "coin", "Медная монета", Tier.COMMON,
            mapOf(Stat.GOLD_GAIN to 0.10f),
        ),
        ItemDef(
            "glasses", "Очки мудреца", Tier.COMMON,
            mapOf(Stat.XP_GAIN to 0.08f),
        ),
        // --- Необычные ---
        ItemDef(
            "steel", "Стальной клинок", Tier.UNCOMMON,
            mapOf(Stat.DAMAGE to 0.15f),
        ),
        ItemDef(
            "boots", "Сапоги скорохода", Tier.UNCOMMON,
            mapOf(Stat.MOVE_SPEED to 0.12f, Stat.MAGNET to 0.10f),
        ),
        ItemDef(
            "goblet", "Кубок жадности", Tier.UNCOMMON,
            mapOf(Stat.GOLD_GAIN to 0.20f),
        ),
        ItemDef(
            "plate", "Тяжёлая пластина", Tier.UNCOMMON,
            mapOf(Stat.MAX_HP to 0.15f, Stat.ARMOR to 0.10f),
        ),
        ItemDef(
            "quiver", "Тугой колчан", Tier.UNCOMMON,
            mapOf(Stat.PROJ_SPEED to 0.15f, Stat.COOLDOWN to 0.08f),
        ),
        // --- Редкие ---
        ItemDef(
            "fang", "Вампирский клык", Tier.RARE,
            mapOf(Stat.DAMAGE to 0.05f), Special.LIFESTEAL,
            "+1 HP за каждое убийство",
        ),
        ItemDef(
            "clover", "Счастливый клевер", Tier.RARE,
            mapOf(Stat.CRIT_CHANCE to 0.08f),
        ),
        ItemDef(
            "warhorn", "Боевой рог", Tier.RARE,
            mapOf(Stat.DAMAGE to 0.12f, Stat.COOLDOWN to 0.10f),
        ),
        ItemDef(
            "runeshield", "Рунный щит", Tier.RARE,
            mapOf(Stat.ARMOR to 0.20f, Stat.MAX_HP to 0.10f),
        ),
        ItemDef(
            "magnetite", "Магнетит", Tier.RARE,
            mapOf(Stat.MAGNET to 0.35f, Stat.XP_GAIN to 0.10f),
        ),
        // --- Мистические ---
        ItemDef(
            "frost_cube", "Морозный куб", Tier.MYSTIC,
            mapOf(Stat.AREA to 0.10f), Special.FROST_CUBE,
            "Морозная аура 20 ур. → Ледяная буря",
        ),
        ItemDef(
            "plague_heart", "Сердце чумы", Tier.MYSTIC,
            mapOf(Stat.DAMAGE to 0.10f), Special.PLAGUE_HEART,
            "Яд 20 ур. → Чумное облако",
        ),
        ItemDef(
            "dark_sickle", "Тёмный серп", Tier.MYSTIC,
            mapOf(Stat.DAMAGE to 0.10f), Special.DARK_SICKLE,
            "Коса 20 ур. → Жнец",
        ),
        ItemDef(
            "storm_eye", "Око бури", Tier.MYSTIC,
            mapOf(Stat.CRIT_CHANCE to 0.15f, Stat.CRIT_DMG to 0.30f),
        ),
        ItemDef(
            "chronos", "Часы Хроноса", Tier.MYSTIC,
            mapOf(Stat.COOLDOWN to 0.20f, Stat.MOVE_SPEED to 0.10f),
        ),
        // --- Легендарные ---
        ItemDef(
            "dragon_fang", "Клык дракона", Tier.LEGENDARY,
            mapOf(Stat.DAMAGE to 0.35f),
        ),
        ItemDef(
            "greed_crown", "Корона алчности", Tier.LEGENDARY,
            mapOf(Stat.GOLD_GAIN to 0.50f, Stat.XP_GAIN to 0.20f),
        ),
        ItemDef(
            "titan_heart", "Сердце титана", Tier.LEGENDARY,
            mapOf(Stat.MAX_HP to 0.40f, Stat.ARMOR to 0.20f), Special.REGEN,
            "+1 HP в секунду",
        ),
        ItemDef(
            "shadow_cloak", "Плащ теней", Tier.LEGENDARY,
            mapOf(Stat.MOVE_SPEED to 0.20f), Special.DODGE,
            "10% шанс избежать урона",
        ),
        ItemDef(
            "philosopher", "Философский камень", Tier.LEGENDARY,
            mapOf(
                Stat.DAMAGE to 0.15f, Stat.COOLDOWN to 0.15f,
                Stat.MAX_HP to 0.15f, Stat.MOVE_SPEED to 0.15f,
            ),
        ),
    )

    fun byId(id: String): ItemDef? = catalog.firstOrNull { it.id == id }

    /** Предметы, доступные для выбора перед забегом. */
    val starting: List<ItemDef> = listOf(
        byId("fang")!!, byId("clover")!!, byId("goblet")!!,
        byId("frost_cube")!!, byId("plague_heart")!!, byId("dark_sickle")!!,
    )

    /**
     * Выпадение предмета из сундука. Катализаторы не дублируются.
     * Чем позже минута забега, тем жирнее тиры.
     */
    fun roll(rng: Random, minute: Int, owned: List<ItemDef>): ItemDef {
        val tierBoost = 1f + minute * 0.08f
        val weights = Tier.entries.map { tier ->
            if (tier == Tier.COMMON) tier.baseWeight else tier.baseWeight * tierBoost
        }
        var pick = rng.nextFloat() * weights.sum()
        var tier = Tier.COMMON
        for (i in Tier.entries.indices) {
            pick -= weights[i]
            if (pick <= 0f) {
                tier = Tier.entries[i]
                break
            }
        }
        val pool = catalog.filter { def ->
            def.tier == tier && !(def.isCatalyst && owned.any { it.id == def.id })
        }
        if (pool.isEmpty()) {
            // Все катализаторы тира уже собраны — берём любой некатализатор.
            return catalog.filter { !it.isCatalyst }.random(rng)
        }
        return pool.random(rng)
    }
}
