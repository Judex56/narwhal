package com.megasurvivors.game

import android.graphics.Color

/**
 * Моды забега: включаются в меню в любых сочетаниях и меняют правила.
 * Эффекты применяются в Game при старте забега.
 */
enum class Mod(
    val label: String,
    val desc: String,
    val color: Int,
) {
    // --- Ключевые моды ---
    WISE(
        "Мудрость",
        "+50% опыта за убийства",
        Color.rgb(149, 117, 205),
    ),
    TWIN_BOSSES(
        "Двойной босс",
        "В конце уровня выходят СРАЗУ ДВА босса",
        Color.rgb(213, 0, 249),
    ),
    SHRINKING(
        "Зона",
        "Карта сжимается со временем; снаружи зоны вы горите",
        Color.rgb(255, 87, 34),
    ),
    RIVAL(
        "Соперник",
        "На карте второй герой-ИИ: фармит, стреляет в вас и попробует убить босса первым. Побеждает тот, кто убьёт другого или босса",
        Color.rgb(229, 57, 53),
    ),

    // --- Классика ---
    HORDE("Орда", "+50% врагов в волнах, +25% золота", Color.rgb(255, 112, 67)),
    GLASS("Стеклянная пушка", "Ваш урон ×2, но и входящий урон ×2", Color.rgb(129, 212, 250)),
    IRON("Железный век", "+50% брони, но -15% скорости бега", Color.rgb(144, 164, 174)),
    VAMPIRE("Вампиризм", "+1 HP за убийство, но -25% макс. HP и нет регенерации", Color.rgb(183, 28, 28)),
    SPEED_DEMON("Дикая скорость", "Враги на 30% быстрее, +30% опыта", Color.rgb(255, 202, 40)),
    GOLD_RUSH("Золотая лихорадка", "Золото ×2, но сундуки на 50% дороже", Color.rgb(255, 179, 0)),
    ELITE_MARCH("Элитный марш", "Элита приходит каждые 30 секунд вместо 60", Color.rgb(255, 213, 79)),
    MIDAS("Мидас", "Золото ×2, но -25% опыта", Color.rgb(255, 160, 0)),
    NIGHT("Тьма", "Видно только рядом с героем, +30% опыта", Color.rgb(69, 39, 160)),
    BERSERK("Берсерк", "Чем меньше HP, тем больше урон (до +100%)", Color.rgb(239, 83, 80)),
    FRENZY("Бешенство", "Мини-босс каждые 90 секунд вместо 3 минут", Color.rgb(233, 30, 99)),
    SWARM("Рой", "Врагов в 2 раза больше, но у них -40% HP", Color.rgb(124, 179, 66)),
    TITANS("Титаны", "+60% HP врагов, +50% опыта", Color.rgb(84, 110, 122)),
    NO_MAGNET("Тяжёлая ноша", "-50% радиуса сбора, +50% золота", Color.rgb(121, 85, 72)),
    TIMEWARP("Спешка", "Босс выходит на 10:00 вместо 15:00", Color.rgb(0, 229, 255)),
    CURSED_CHESTS("Проклятые сундуки", "Сундуки бесплатны, но каждый призывает волну врагов", Color.rgb(103, 58, 183));

    val code: String get() = label.take(2).uppercase()

    /** Постоянные статовые эффекты мода (вешаются баффом на весь забег). */
    val statBuff: Map<Stat, Float>
        get() = when (this) {
            WISE -> mapOf(Stat.XP_GAIN to 0.5f)
            HORDE -> mapOf(Stat.GOLD_GAIN to 0.25f)
            IRON -> mapOf(Stat.ARMOR to 0.5f, Stat.MOVE_SPEED to -0.15f)
            VAMPIRE -> mapOf(Stat.MAX_HP to -0.25f)
            SPEED_DEMON -> mapOf(Stat.XP_GAIN to 0.3f)
            GOLD_RUSH -> mapOf(Stat.GOLD_GAIN to 1.0f)
            MIDAS -> mapOf(Stat.GOLD_GAIN to 1.0f, Stat.XP_GAIN to -0.25f)
            NIGHT -> mapOf(Stat.XP_GAIN to 0.3f)
            TITANS -> mapOf(Stat.XP_GAIN to 0.5f)
            NO_MAGNET -> mapOf(Stat.MAGNET to -0.5f, Stat.GOLD_GAIN to 0.5f)
            else -> emptyMap()
        }
}
