package com.megasurvivors.game

import android.graphics.RectF

/**
 * Модель главного меню: выбор оружия (с покупкой), стартового
 * предмета и фолиантов. Раскладка считается один раз от размеров
 * экрана; Renderer рисует по этим же прямоугольникам.
 */
class Menu(private val screenW: Float, private val screenH: Float, val meta: MetaStore) {

    class Cell(val rect: RectF)

    val weaponTypes: List<WeaponType> = WeaponType.entries.filter { !it.evolved }
    val startItems: List<ItemDef> = ItemPool.starting
    val tomes: List<Tome> = Tome.entries.toList()

    val weaponCells = ArrayList<Cell>()
    val itemCells = ArrayList<Cell>()
    val tomeCells = ArrayList<Cell>()
    val startRect: RectF
    /** Сообщение под сеткой (описание выбранного/ошибка покупки). */
    var infoText = ""

    init {
        val top = 170f
        val cell = 128f
        val gap = 14f

        // Оружие: сетка 5 колонок слева.
        val wLeft = 40f
        for (i in weaponTypes.indices) {
            val col = i % 5
            val row = i / 5
            val x = wLeft + col * (cell + gap)
            val y = top + row * (cell + gap + 26f)
            weaponCells.add(Cell(RectF(x, y, x + cell, y + cell)))
        }

        // Предметы: 2 колонки в центре-справа.
        val iLeft = wLeft + 5 * (cell + gap) + 60f
        for (i in startItems.indices) {
            val col = i % 2
            val row = i / 2
            val x = iLeft + col * (cell + gap)
            val y = top + row * (cell + gap + 26f)
            itemCells.add(Cell(RectF(x, y, x + cell, y + cell)))
        }

        // Фолианты: 2 колонки справа.
        val tLeft = iLeft + 2 * (cell + gap) + 60f
        for (i in tomes.indices) {
            val col = i % 2
            val row = i / 2
            val x = tLeft + col * (cell + gap)
            val y = top + row * (cell + gap + 26f)
            tomeCells.add(Cell(RectF(x, y, x + cell, y + cell)))
        }

        startRect = RectF(
            screenW / 2f - 260f, screenH - 150f,
            screenW / 2f + 260f, screenH - 46f,
        )
        updateInfo()
    }

    val selectedWeapon: WeaponType
        get() = weaponTypes.firstOrNull { it.name == meta.selectedWeapon } ?: WeaponType.DART

    val selectedItem: ItemDef?
        get() = ItemPool.byId(meta.selectedItem)

    fun isTomeSelected(t: Tome) = meta.selectedTomes.contains(t.name)

    /** true если тап обработан и надо стартовать забег. */
    fun handleTap(x: Float, y: Float): Boolean {
        if (startRect.contains(x, y)) return true

        for (i in weaponCells.indices) {
            if (weaponCells[i].rect.contains(x, y)) {
                val type = weaponTypes[i]
                if (meta.tryUnlock(type)) {
                    meta.selectedWeapon = type.name
                    meta.save()
                    updateInfo()
                } else {
                    infoText = "Нужно ${type.price} золота, у вас ${meta.gold}"
                }
                return false
            }
        }
        for (i in itemCells.indices) {
            if (itemCells[i].rect.contains(x, y)) {
                val item = startItems[i]
                // Повторный тап снимает выбор.
                meta.selectedItem = if (meta.selectedItem == item.id) "" else item.id
                meta.save()
                updateInfo()
                return false
            }
        }
        for (i in tomeCells.indices) {
            if (tomeCells[i].rect.contains(x, y)) {
                val tome = tomes[i]
                if (isTomeSelected(tome)) {
                    meta.selectedTomes.remove(tome.name)
                } else if (meta.selectedTomes.size < MAX_TOMES) {
                    meta.selectedTomes.add(tome.name)
                } else {
                    infoText = "Можно взять не больше $MAX_TOMES фолиантов"
                    return false
                }
                meta.save()
                updateInfo()
                return false
            }
        }
        return false
    }

    private fun updateInfo() {
        val w = selectedWeapon
        val item = selectedItem
        val tomeNames = meta.selectedTomes
            .mapNotNull { name -> Tome.entries.firstOrNull { it.name == name } }
            .joinToString(", ") { it.label }
        infoText = buildString {
            append("${w.label} — ${w.desc}")
            if (item != null) append("  |  ${item.name}")
            if (tomeNames.isNotEmpty()) append("  |  $tomeNames")
        }
    }
}
