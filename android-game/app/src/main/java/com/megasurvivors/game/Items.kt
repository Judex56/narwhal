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
enum class Special(val text: String) {
    NONE(""),
    FROST_CUBE("Катализатор: Морозная аура 20 ур. → ЛЕДЯНАЯ БУРЯ"),
    PLAGUE_HEART("Катализатор: Ядовитые ножи 20 ур. → ЧУМНОЕ ОБЛАКО"),
    DARK_SICKLE("Катализатор: Коса 20 ур. → ЖНЕЦ"),
    LIFESTEAL("+1 HP за каждое убийство"),
    REGEN("+1 HP в секунду"),
    DODGE("10% шанс полностью избежать урона"),
    THORNS("Враг, ударивший вас, получает 50% своего урона"),
    CHEST_DISCOUNT("Сундуки на 15% дешевле"),
    CAPTURE_SPEED("Захват шрайнов и статуй на 30% быстрее"),
    REVIVE("Воскрешение с 50% HP — один раз за забег"),
}

class ItemDef(
    val id: String,
    val name: String,
    val tier: Tier,
    val stats: Map<Stat, Float>,
    val special: Special = Special.NONE,
) {
    val isCatalyst: Boolean
        get() = special == Special.FROST_CUBE ||
            special == Special.PLAGUE_HEART ||
            special == Special.DARK_SICKLE

    /** "+15% урон, +10% броня" */
    val statsText: String
        get() = stats.entries.joinToString(", ") {
            "+${(it.value * 100).toInt()}% ${it.key.label.lowercase()}"
        }

    /** Полное описание: статы + особый эффект. */
    val fullDesc: String
        get() {
            val parts = ArrayList<String>()
            if (stats.isNotEmpty()) parts.add(statsText)
            if (special != Special.NONE) parts.add(special.text)
            return parts.joinToString("  •  ")
        }
}

/**
 * Каталог всех предметов и настраиваемый пул выпадения.
 * Вес тира крутится в [Tier.baseWeight]; со временем забега
 * шанс высоких тиров растёт.
 */
object ItemPool {

    private fun item(
        id: String,
        name: String,
        tier: Tier,
        vararg stats: Pair<Stat, Float>,
        special: Special = Special.NONE,
    ) = ItemDef(id, name, tier, mapOf(*stats), special)

    val catalog: List<ItemDef> = listOf(
        // ------------------- ОБЫЧНЫЕ (12) -------------------
        item("whetstone", "Точильный камень", Tier.COMMON, Stat.DAMAGE to 0.08f),
        item("belt", "Кожаный ремень", Tier.COMMON, Stat.MAX_HP to 0.10f),
        item("feather", "Перо ястреба", Tier.COMMON, Stat.MOVE_SPEED to 0.06f),
        item("coin", "Медная монета", Tier.COMMON, Stat.GOLD_GAIN to 0.10f),
        item("glasses", "Очки мудреца", Tier.COMMON, Stat.XP_GAIN to 0.08f),
        item("gloves", "Кожаные перчатки", Tier.COMMON, Stat.COOLDOWN to 0.06f),
        item("buckler", "Деревянный щит", Tier.COMMON, Stat.ARMOR to 0.08f),
        item("lodestone", "Осколок магнетита", Tier.COMMON, Stat.MAGNET to 0.15f),
        item("flint", "Кремень", Tier.COMMON, Stat.AREA to 0.06f),
        item("arrowhead", "Наконечник стрелы", Tier.COMMON, Stat.PROJ_SPEED to 0.10f),
        item("dice", "Костяная кость", Tier.COMMON, Stat.CRIT_CHANCE to 0.03f),
        item("pouch", "Кошель", Tier.COMMON, Stat.GOLD_GAIN to 0.08f, Stat.XP_GAIN to 0.04f),

        // ------------------- НЕОБЫЧНЫЕ (12) -------------------
        item("steel", "Стальной клинок", Tier.UNCOMMON, Stat.DAMAGE to 0.15f),
        item("boots", "Сапоги скорохода", Tier.UNCOMMON, Stat.MOVE_SPEED to 0.12f, Stat.MAGNET to 0.10f),
        item("goblet", "Кубок жадности", Tier.UNCOMMON, Stat.GOLD_GAIN to 0.20f),
        item("plate", "Тяжёлая пластина", Tier.UNCOMMON, Stat.MAX_HP to 0.15f, Stat.ARMOR to 0.10f),
        item("quiver", "Тугой колчан", Tier.UNCOMMON, Stat.PROJ_SPEED to 0.15f, Stat.COOLDOWN to 0.08f),
        item("tome_shard", "Обрывок фолианта", Tier.UNCOMMON, Stat.XP_GAIN to 0.15f, Stat.COOLDOWN to 0.05f),
        item("spikes", "Ржавые шипы", Tier.UNCOMMON, Stat.DAMAGE to 0.05f, special = Special.THORNS),
        item("lantern", "Фонарь шахтёра", Tier.UNCOMMON, Stat.MAGNET to 0.20f, Stat.GOLD_GAIN to 0.10f),
        item("bracer", "Наруч дуэлянта", Tier.UNCOMMON, Stat.CRIT_CHANCE to 0.05f, Stat.COOLDOWN to 0.05f),
        item("haft", "Тяжёлое древко", Tier.UNCOMMON, Stat.AREA to 0.12f, Stat.DAMAGE to 0.06f),
        item("tonic", "Бодрящий тоник", Tier.UNCOMMON, Stat.MAX_HP to 0.10f, Stat.MOVE_SPEED to 0.08f),
        item("lockpick", "Отмычка", Tier.UNCOMMON, Stat.GOLD_GAIN to 0.05f, special = Special.CHEST_DISCOUNT),

        // ------------------- РЕДКИЕ (10) -------------------
        item("fang", "Вампирский клык", Tier.RARE, Stat.DAMAGE to 0.05f, special = Special.LIFESTEAL),
        item("clover", "Счастливый клевер", Tier.RARE, Stat.CRIT_CHANCE to 0.08f),
        item("warhorn", "Боевой рог", Tier.RARE, Stat.DAMAGE to 0.12f, Stat.COOLDOWN to 0.10f),
        item("runeshield", "Рунный щит", Tier.RARE, Stat.ARMOR to 0.20f, Stat.MAX_HP to 0.10f),
        item("magnetite", "Магнетит", Tier.RARE, Stat.MAGNET to 0.35f, Stat.XP_GAIN to 0.10f),
        item("banner", "Знамя завоевателя", Tier.RARE, Stat.DAMAGE to 0.08f, special = Special.CAPTURE_SPEED),
        item("hourglass", "Песочные часы", Tier.RARE, Stat.COOLDOWN to 0.15f, Stat.PROJ_SPEED to 0.10f),
        item("warboots", "Кованые сапоги", Tier.RARE, Stat.MOVE_SPEED to 0.15f, Stat.ARMOR to 0.10f),
        item("gem_eye", "Глаз василиска", Tier.RARE, Stat.CRIT_CHANCE to 0.06f, Stat.CRIT_DMG to 0.20f),
        item("ledger", "Гроссбух торговца", Tier.RARE, Stat.GOLD_GAIN to 0.25f, Stat.XP_GAIN to 0.10f),

        // ------------------- МИСТИЧЕСКИЕ (8) -------------------
        item("frost_cube", "Морозный куб", Tier.MYSTIC, Stat.AREA to 0.10f, special = Special.FROST_CUBE),
        item("plague_heart", "Сердце чумы", Tier.MYSTIC, Stat.DAMAGE to 0.10f, special = Special.PLAGUE_HEART),
        item("dark_sickle", "Тёмный серп", Tier.MYSTIC, Stat.DAMAGE to 0.10f, special = Special.DARK_SICKLE),
        item("storm_eye", "Око бури", Tier.MYSTIC, Stat.CRIT_CHANCE to 0.15f, Stat.CRIT_DMG to 0.30f),
        item("chronos", "Часы Хроноса", Tier.MYSTIC, Stat.COOLDOWN to 0.20f, Stat.MOVE_SPEED to 0.10f),
        item("barbs", "Клинковый панцирь", Tier.MYSTIC, Stat.ARMOR to 0.15f, Stat.MAX_HP to 0.10f, special = Special.THORNS),
        item("idol", "Идол старателя", Tier.MYSTIC, Stat.GOLD_GAIN to 0.20f, Stat.MAGNET to 0.15f, special = Special.CHEST_DISCOUNT),
        item("warbanner", "Штандарт вождя", Tier.MYSTIC, Stat.DAMAGE to 0.10f, Stat.MAX_HP to 0.10f, special = Special.CAPTURE_SPEED),

        // ------------------- ЛЕГЕНДАРНЫЕ (8) -------------------
        item("dragon_fang", "Клык дракона", Tier.LEGENDARY, Stat.DAMAGE to 0.35f),
        item("greed_crown", "Корона алчности", Tier.LEGENDARY, Stat.GOLD_GAIN to 0.50f, Stat.XP_GAIN to 0.20f),
        item(
            "titan_heart", "Сердце титана", Tier.LEGENDARY,
            Stat.MAX_HP to 0.40f, Stat.ARMOR to 0.20f, special = Special.REGEN,
        ),
        item("shadow_cloak", "Плащ теней", Tier.LEGENDARY, Stat.MOVE_SPEED to 0.20f, special = Special.DODGE),
        item(
            "philosopher", "Философский камень", Tier.LEGENDARY,
            Stat.DAMAGE to 0.15f, Stat.COOLDOWN to 0.15f, Stat.MAX_HP to 0.15f, Stat.MOVE_SPEED to 0.15f,
        ),
        item("phoenix", "Перо феникса", Tier.LEGENDARY, Stat.MAX_HP to 0.15f, special = Special.REVIVE),
        item("executioner", "Топор палача", Tier.LEGENDARY, Stat.CRIT_CHANCE to 0.10f, Stat.CRIT_DMG to 0.60f),
        item(
            "world_map", "Карта первооткрывателя", Tier.LEGENDARY,
            Stat.MAGNET to 0.30f, Stat.GOLD_GAIN to 0.30f, special = Special.CAPTURE_SPEED,
        ),
    )

    fun byId(id: String): ItemDef? = catalog.firstOrNull { it.id == id }

    /** Что открыто с самого начала: все обычные + база редких + катализаторы. */
    val defaultDiscovered: Set<String> =
        catalog.filter { it.tier == Tier.COMMON }.map { it.id }.toSet() +
            setOf("fang", "clover", "goblet", "frost_cube", "plague_heart", "dark_sickle")

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
            return catalog.filter { !it.isCatalyst }.random(rng)
        }
        return pool.random(rng)
    }
}
