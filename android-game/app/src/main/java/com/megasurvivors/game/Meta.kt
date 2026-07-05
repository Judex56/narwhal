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

    var selectedWeapon: String = prefs.getString("sel_weapon", "DART") ?: "DART"
    var selectedItem: String = prefs.getString("sel_item", "") ?: ""
    val selectedTomes: MutableSet<String> =
        (prefs.getStringSet("sel_tomes", null) ?: emptySet()).toMutableSet()

    var bestTime: Int = prefs.getInt("best_time", 0)

    fun save() {
        prefs.edit()
            .putInt("gold", gold)
            .putStringSet("unlocked", unlockedWeapons)
            .putString("sel_weapon", selectedWeapon)
            .putString("sel_item", selectedItem)
            .putStringSet("sel_tomes", selectedTomes)
            .putInt("best_time", bestTime)
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
