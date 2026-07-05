package com.megasurvivors.game

import android.graphics.Color
import kotlin.random.Random

const val CHUNK_SIZE = 1400f

/** Типы шрайнов: какой временный бафф даёт захват. */
enum class ShrineKind(
    val label: String,
    val color: Int,
    val duration: Float,
    val stats: Map<Stat, Float>,
) {
    WAR(
        "Шрайн ярости", Color.rgb(239, 83, 80), 35f,
        mapOf(Stat.DAMAGE to 0.6f, Stat.AREA to 0.25f),
    ),
    SWIFT(
        "Шрайн ветра", Color.rgb(77, 208, 225), 35f,
        mapOf(Stat.MOVE_SPEED to 0.35f, Stat.COOLDOWN to 0.45f),
    ),
    GUARD(
        "Шрайн камня", Color.rgb(255, 202, 40), 45f,
        mapOf(Stat.ARMOR to 0.8f, Stat.MAX_HP to 0.25f, Stat.MAGNET to 0.5f),
    );
}

sealed class WorldObject(var x: Float, var y: Float) {
    var consumed = false
}

/** Сундук: подошёл — открыл, получил предмет. */
class Chest(x: Float, y: Float) : WorldObject(x, y) {
    val radius = 46f
}

/**
 * Шрайн: стой в круге, чтобы захватить. Даёт мощный ВРЕМЕННЫЙ бафф
 * всех навыков из списка. Пока идёт захват — врагов тянет к шрайну.
 */
class Shrine(x: Float, y: Float, val kind: ShrineKind) : WorldObject(x, y) {
    val radius = 170f
    var progress = 0f // 0..1
    val captureTime = 5f
}

/**
 * Статуя: захватывается дольше, но даёт небольшой ПОСТОЯННЫЙ бонус
 * ко всем характеристикам до конца забега.
 */
class Statue(x: Float, y: Float) : WorldObject(x, y) {
    val radius = 150f
    var progress = 0f
    val captureTime = 9f
}

/**
 * Ленивая генерация мира чанками: содержимое чанка детерминировано
 * его координатами и сидом забега.
 */
class WorldGen(private val seed: Int) {

    private val chunks = HashMap<Long, List<WorldObject>>()

    fun key(cx: Int, cy: Int): Long = (cx.toLong() shl 32) or (cy.toLong() and 0xFFFFFFFFL)

    fun chunkAt(cx: Int, cy: Int): List<WorldObject> =
        chunks.getOrPut(key(cx, cy)) { generate(cx, cy) }

    /** Все объекты в чанках вокруг точки (радиус в чанках). */
    fun objectsAround(x: Float, y: Float, chunkRadius: Int = 1): List<WorldObject> {
        val cx = Math.floorDiv(x.toInt(), CHUNK_SIZE.toInt())
        val cy = Math.floorDiv(y.toInt(), CHUNK_SIZE.toInt())
        val out = ArrayList<WorldObject>()
        for (dx in -chunkRadius..chunkRadius) {
            for (dy in -chunkRadius..chunkRadius) {
                out.addAll(chunkAt(cx + dx, cy + dy))
            }
        }
        return out
    }

    fun chunkSeed(cx: Int, cy: Int): Int =
        cx * 73856093 xor cy * 19349663 xor seed

    private fun generate(cx: Int, cy: Int): List<WorldObject> {
        val rng = Random(chunkSeed(cx, cy))
        val objs = ArrayList<WorldObject>()
        val baseX = cx * CHUNK_SIZE
        val baseY = cy * CHUNK_SIZE
        val margin = 220f

        fun px() = baseX + margin + rng.nextFloat() * (CHUNK_SIZE - 2 * margin)
        fun py() = baseY + margin + rng.nextFloat() * (CHUNK_SIZE - 2 * margin)

        // Стартовый чанк оставляем пустым, чтобы не началось с захвата.
        if (cx == 0 && cy == 0) return objs

        if (rng.nextFloat() < 0.45f) objs.add(Chest(px(), py()))
        if (rng.nextFloat() < 0.30f) {
            val kind = ShrineKind.entries[rng.nextInt(ShrineKind.entries.size)]
            objs.add(Shrine(px(), py(), kind))
        }
        if (rng.nextFloat() < 0.12f) objs.add(Statue(px(), py()))
        return objs
    }
}
