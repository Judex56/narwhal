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

    val weaponTypes: List<WeaponType> = WeaponType.entries.toList()
    val allItems: List<ItemDef> = ItemPool.catalog
    val tomes: List<Tome> = Tome.entries.toList()
    val levelCells = ArrayList<RectF>()

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
            screenH - 350f,
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

        // Селектор уровней (этажей) — ряд по центру над инфо-панелью.
        val lvlCell = 62f
        val lvlGap = 12f
        val totalLvlW = LEVELS.size * lvlCell + (LEVELS.size - 1) * lvlGap
        val lvlLeft = (screenW - totalLvlW) / 2f
        val lvlTop = screenH - 336f
        for (i in LEVELS.indices) {
            val x = lvlLeft + i * (lvlCell + lvlGap)
            levelCells.add(RectF(x, lvlTop, x + lvlCell, lvlTop + lvlCell))
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

    /** Взятые в забег оружия (минимум одно). */
    val selectedWeapons: List<WeaponType>
        get() {
            val list = weaponTypes.filter { meta.selectedWeapons.contains(it.name) }
            return list.ifEmpty { listOf(WeaponType.DART) }
        }

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
        for (i in levelCells.indices) {
            if (levelCells[i].contains(x, y)) {
                tapLevel(LEVELS[i])
                return false
            }
        }
        return false
    }

    private fun tapLevel(def: LevelDef) {
        // Все уровни открыты сразу — это выбор сложности.
        meta.selectedLevel = def.index
        meta.save()
        infoTitle = "Уровень ${def.index}: ${def.name}"
        infoColor = def.bossColor
        val finalNote = if (def.bossHpMult > 1f) " (ФИНАЛЬНЫЙ: HP босса ×${def.bossHpMult.toInt()})" else ""
        infoDesc = "HP врагов ×${"%.1f".format(def.hpMult)}, урон ×${"%.1f".format(def.dmgMult)}" +
            " • Мини-босс каждые 3 мин • Босс на 15:00 — ${def.bossName}$finalNote"
    }

    private fun tapWeapon(type: WeaponType) {
        val descLine = "${type.desc} • ${WeaponBalance.levelUpText(type, 1)}"
        if (meta.isUnlocked(type)) {
            // Мультивыбор: тап добавляет/убирает оружие из набора.
            if (meta.selectedWeapons.contains(type.name)) {
                if (meta.selectedWeapons.size > 1) {
                    meta.selectedWeapons.remove(type.name)
                    infoTitle = "${type.label} — убран из набора (${meta.selectedWeapons.size} в забег)"
                } else {
                    infoTitle = "${type.label} — нельзя убрать последнее оружие"
                }
            } else {
                meta.selectedWeapons.add(type.name)
                infoTitle = "${type.label} — взят в забег (${meta.selectedWeapons.size} всего)"
            }
            meta.save()
            infoColor = type.color
            infoDesc = descLine
        } else if (meta.gold >= type.price) {
            meta.tryUnlock(type)
            meta.selectedWeapons.add(type.name)
            meta.save()
            infoTitle = "${type.label} — ОТКРЫТО за ${type.price} золота и взято в забег!"
            infoColor = type.color
            infoDesc = descLine
        } else {
            infoTitle = "${type.label} — нужно ${type.price} золота (у вас ${meta.gold})"
            infoColor = Color.rgb(255, 138, 101)
            infoDesc = descLine
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
        // Как предметы: сколько угодно, действуют весь забег.
        if (isTomeSelected(tome)) {
            meta.selectedTomes.remove(tome.name)
            infoTitle = "${tome.label} — убран"
        } else {
            meta.selectedTomes.add(tome.name)
            infoTitle = "${tome.label} — взят в забег"
        }
        meta.save()
        infoColor = tome.color
        infoDesc = "${tome.desc} • Действует весь забег"
    }
}
