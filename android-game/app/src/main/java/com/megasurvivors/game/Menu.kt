package com.megasurvivors.game

import android.graphics.Color
import android.graphics.RectF
import kotlin.math.abs

/**
 * Главное меню: выбор оружия (с покупкой за золото), стартового
 * предмета из ПОЛНОГО каталога (ненайденные видны, но заблокированы)
 * и до 2 фолиантов. Панель предметов скроллится пальцем.
 */
class Menu(private val screenW: Float, private val screenH: Float, val meta: MetaStore) {

    val weaponTypes: List<WeaponType> = WeaponType.entries.filter { !it.evolved }
    val allItems: List<ItemDef> = ItemPool.catalog
    val tomes: List<Tome> = Tome.entries.toList()

    val cell = 110f
    val gap = 12f
    private val rowPitch = cell + gap + 22f // место под подпись

    val weaponCells = ArrayList<RectF>()
    val tomeCells = ArrayList<RectF>()

    /** Видимая область списка предметов (внутри — скролл). */
    val itemArea: RectF
    val itemCols = 4
    var itemScroll = 0f
    private val itemRowPitch = cell + gap
    private val itemContentHeight: Float

    val infoArea: RectF
    val startRect: RectF

    var infoTitle = "Выберите снаряжение и жмите В БОЙ!"
    var infoColor = Color.WHITE
    var infoDesc = ""

    // Обработка касаний: отличаем тап от скролла.
    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var dragging = false
    private var downInItems = false

    init {
        val top = 170f

        val wLeft = 40f
        for (i in weaponTypes.indices) {
            val col = i % 5
            val row = i / 5
            val x = wLeft + col * (cell + gap)
            val y = top + row * rowPitch
            weaponCells.add(RectF(x, y, x + cell, y + cell))
        }

        val iLeft = wLeft + 5 * (cell + gap) + 50f
        itemArea = RectF(
            iLeft, top,
            iLeft + itemCols * (cell + gap) - gap,
            screenH - 280f,
        )
        val rows = (allItems.size + itemCols - 1) / itemCols
        itemContentHeight = rows * itemRowPitch - gap

        val tLeft = itemArea.right + 50f
        for (i in tomes.indices) {
            val col = i % 2
            val row = i / 2
            val x = tLeft + col * (cell + gap)
            val y = top + row * rowPitch
            tomeCells.add(RectF(x, y, x + cell, y + cell))
        }

        infoArea = RectF(40f, screenH - 262f, screenW - 40f, screenH - 160f)
        startRect = RectF(
            screenW / 2f - 260f, screenH - 150f,
            screenW / 2f + 260f, screenH - 46f,
        )
    }

    /** Прямоугольник ячейки предмета с учётом скролла. */
    fun itemCellRect(i: Int): RectF {
        val col = i % itemCols
        val row = i / itemCols
        val x = itemArea.left + col * (cell + gap)
        val y = itemArea.top + row * itemRowPitch - itemScroll
        return RectF(x, y, x + cell, y + cell)
    }

    private fun maxScroll(): Float = (itemContentHeight - itemArea.height()).coerceAtLeast(0f)

    val selectedWeapon: WeaponType
        get() = weaponTypes.firstOrNull { it.name == meta.selectedWeapon } ?: WeaponType.DART

    fun isTomeSelected(t: Tome) = meta.selectedTomes.contains(t.name)

    // ------------------------------------------------------------------
    // Касания.
    // ------------------------------------------------------------------
    fun touchDown(x: Float, y: Float) {
        downX = x
        downY = y
        lastY = y
        dragging = false
        downInItems = itemArea.contains(x, y)
    }

    fun touchMove(x: Float, y: Float) {
        if (downInItems) {
            if (!dragging && abs(y - downY) > 24f) dragging = true
            if (dragging) {
                itemScroll = (itemScroll - (y - lastY)).coerceIn(0f, maxScroll())
            }
        }
        lastY = y
    }

    /** true — нажата кнопка старта, пора начинать забег. */
    fun touchUp(x: Float, y: Float): Boolean {
        if (dragging) {
            dragging = false
            return false
        }
        if (startRect.contains(x, y)) return true

        for (i in weaponCells.indices) {
            if (weaponCells[i].contains(x, y)) {
                tapWeapon(weaponTypes[i])
                return false
            }
        }
        if (itemArea.contains(x, y)) {
            for (i in allItems.indices) {
                if (itemCellRect(i).contains(x, y)) {
                    tapItem(allItems[i])
                    return false
                }
            }
        }
        for (i in tomeCells.indices) {
            if (tomeCells[i].contains(x, y)) {
                tapTome(tomes[i])
                return false
            }
        }
        return false
    }

    private fun tapWeapon(type: WeaponType) {
        if (meta.isUnlocked(type)) {
            meta.selectedWeapon = type.name
            meta.save()
            infoTitle = type.label
            infoColor = type.color
            infoDesc = "${type.desc} • ${WeaponBalance.levelUpText(type, 1)}"
        } else if (meta.gold >= type.price) {
            meta.tryUnlock(type)
            meta.selectedWeapon = type.name
            meta.save()
            infoTitle = "${type.label} — ОТКРЫТО за ${type.price} золота!"
            infoColor = type.color
            infoDesc = "${type.desc} • ${WeaponBalance.levelUpText(type, 1)}"
        } else {
            infoTitle = "${type.label} — нужно ${type.price} золота (у вас ${meta.gold})"
            infoColor = Color.rgb(255, 138, 101)
            infoDesc = "${type.desc} • ${WeaponBalance.levelUpText(type, 1)}"
        }
    }

    private fun tapItem(item: ItemDef) {
        if (meta.isDiscovered(item.id)) {
            // Тап переключает предмет в пуле выпадения.
            val enabled = meta.toggleItem(item.id)
            infoTitle = "${item.name} (${item.tier.label}) — " +
                if (enabled) "ВКЛЮЧЁН: может выпасть из сундуков" else "ВЫКЛЮЧЕН: не будет выпадать"
            infoColor = if (enabled) item.tier.color else Color.rgb(120, 144, 156)
            infoDesc = item.fullDesc
        } else {
            infoTitle = "${item.name} (${item.tier.label}) — ещё не найден"
            infoColor = Color.rgb(120, 144, 156)
            infoDesc = "${item.fullDesc} • Может выпасть из сундука в любой момент — так и открывается"
        }
    }

    private fun tapTome(tome: Tome) {
        if (isTomeSelected(tome)) {
            meta.selectedTomes.remove(tome.name)
            infoTitle = "${tome.label} — убран"
        } else if (meta.selectedTomes.size < MAX_TOMES) {
            meta.selectedTomes.add(tome.name)
            infoTitle = "${tome.label} — взят (стартует на 1 ур.)"
        } else {
            infoTitle = "Можно взять не больше $MAX_TOMES фолиантов"
            infoColor = Color.rgb(255, 138, 101)
            infoDesc = ""
            return
        }
        meta.save()
        infoColor = tome.color
        infoDesc = "${tome.perLevelText} • Качается на левел-апах до $MAX_TOME_LEVEL ур."
    }
}
