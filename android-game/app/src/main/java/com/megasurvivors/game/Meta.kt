package com.megasurvivors.game

import android.content.Context
import android.content.SharedPreferences

/**
 * Мета-прогресс между забегами: золото, открытые оружия и
 * выбранный лоадаут. Хранится в SharedPreferences.
 */
class MetaStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("megasurvivors", Context.MODE_PRIVATE)

    var gold: Int = prefs.getInt("gold", 0)

    val unlockedWeapons: MutableSet<String> =
        (
            prefs.getStringSet("unlocked", null)
                ?: setOf("DART", "ORBIT", "AURA", "LIGHTNING")
            ).toMutableSet()

    /** Выбранные в забег оружия: от одного до всех сразу. */
    val selectedWeapons: MutableSet<String> =
        (prefs.getStringSet("sel_weapons", null) ?: setOf("DART")).toMutableSet()

    val selectedTomes: MutableSet<String> =
        (prefs.getStringSet("sel_tomes", null) ?: emptySet()).toMutableSet()

    /** Включённые моды забега. */
    val selectedMods: MutableSet<String> =
        (prefs.getStringSet("sel_mods", null) ?: emptySet()).toMutableSet()

    /** Все предметы открыты сразу — игрок управляет только пулом. */
    val discoveredItems: MutableSet<String> =
        ItemPool.catalog.map { it.id }.toMutableSet()

    /**
     * Выключенные из пула выпадения предметы. Выключать можно только
     * найденные; ненайденные всегда могут выпасть (так они и открываются).
     */
    val disabledItems: MutableSet<String> =
        (prefs.getStringSet("disabled", null) ?: emptySet()).toMutableSet()

    var bestTime: Int = prefs.getInt("best_time", 0)

    /** Максимальный открытый уровень (этаж). */
    var unlockedLevel: Int = prefs.getInt("unlocked_level", 1)
    var selectedLevel: Int = prefs.getInt("sel_level", 1)

    fun isDiscovered(id: String): Boolean = discoveredItems.contains(id)

    fun isEnabled(id: String): Boolean = !disabledItems.contains(id)

    /** true — предмет теперь включён в пул. */
    fun toggleItem(id: String): Boolean {
        val enabledNow = if (disabledItems.contains(id)) {
            disabledItems.remove(id)
            true
        } else {
            disabledItems.add(id)
            false
        }
        save()
        return enabledNow
    }

    fun discover(id: String) {
        if (discoveredItems.add(id)) save()
    }

    fun save() {
        prefs.edit()
            .putInt("gold", gold)
            .putStringSet("unlocked", unlockedWeapons)
            .putStringSet("sel_weapons", selectedWeapons)
            .putStringSet("sel_tomes", selectedTomes)
            .putStringSet("sel_mods", selectedMods)
            .putStringSet("disabled", disabledItems)
            .putInt("best_time", bestTime)
            .putInt("unlocked_level", unlockedLevel)
            .putInt("sel_level", selectedLevel)
            .apply()
    }

    fun isUnlocked(type: WeaponType): Boolean = unlockedWeapons.contains(type.name)

    /** Пытается купить оружие; true если получилось (или уже открыто). */
    fun tryUnlock(type: WeaponType): Boolean {
        if (isUnlocked(type)) return true
        if (gold < type.price) return false
        gold -= type.price
        unlockedWeapons.add(type.name)
        save()
        return true
    }
}
