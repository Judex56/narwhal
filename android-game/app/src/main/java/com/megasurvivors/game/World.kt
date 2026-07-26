package com.megasurvivors.game

import android.graphics.Color
import kotlin.random.Random

const val CHUNK_SIZE = 1400f

/** Полукрай карты: мир ограничен квадратом [-WORLD_HALF, WORLD_HALF]. */
const val WORLD_HALF = 8400f

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

/** Сундук: открывается за золото, даёт предмет. */
class Chest(x: Float, y: Float) : WorldObject(x, y) {
    val radius = 52f
}

/** Кучка золота на карте — награда за исследование. */
class GoldPile(x: Float, y: Float, val amount: Int) : WorldObject(x, y) {
    val radius = 34f
}

/**
 * Шрайн: стой в круге, чтобы захватить. Даёт мощный ВРЕМЕННЫЙ бафф.
 * Пока идёт захват — на точку стягиваются волны врагов.
 */
class Shrine(x: Float, y: Float, val kind: ShrineKind) : WorldObject(x, y) {
    val radius = 170f
    var progress = 0f // 0..1
    val captureTime = 5f
}

/** Статуя: долгий захват, постоянный бонус до конца забега. */
class Statue(x: Float, y: Float) : WorldObject(x, y) {
    val radius = 150f
    var progress = 0f
    val captureTime = 9f
}

/**
 * Ограниченный мир из чанков. Всё генерируется сразу при старте
 * забега — чтобы миникарта видела все объекты.
 */
class WorldGen(private val seed: Int) {

    private val chunks = HashMap<Long, List<WorldObject>>()

    /** Диапазон индексов чанков внутри границ мира. */
    val minChunk = Math.floorDiv((-WORLD_HALF).toInt(), CHUNK_SIZE.toInt())
    val maxChunk = Math.floorDiv((WORLD_HALF - 1).toInt(), CHUNK_SIZE.toInt())

    /** Все объекты мира (для миникарты и обхода). */
    val allObjects = ArrayList<WorldObject>()

    init {
        for (cx in minChunk..maxChunk) {
            for (cy in minChunk..maxChunk) {
                val objs = generate(cx, cy)
                chunks[key(cx, cy)] = objs
                allObjects.addAll(objs)
            }
        }
    }

    fun key(cx: Int, cy: Int): Long = (cx.toLong() shl 32) or (cy.toLong() and 0xFFFFFFFFL)

    /** Объекты в чанках вокруг точки (радиус в чанках). */
    fun objectsAround(x: Float, y: Float, chunkRadius: Int = 1): List<WorldObject> {
        val cx = Math.floorDiv(x.toInt(), CHUNK_SIZE.toInt())
        val cy = Math.floorDiv(y.toInt(), CHUNK_SIZE.toInt())
        val out = ArrayList<WorldObject>()
        for (dx in -chunkRadius..chunkRadius) {
            for (dy in -chunkRadius..chunkRadius) {
                chunks[key(cx + dx, cy + dy)]?.let { out.addAll(it) }
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
        val margin = 200f

        fun px() = baseX + margin + rng.nextFloat() * (CHUNK_SIZE - 2 * margin)
        fun py() = baseY + margin + rng.nextFloat() * (CHUNK_SIZE - 2 * margin)

        fun tryAdd(obj: WorldObject) {
            // Не спавним объекты у точки старта и за границей мира.
            if (dist(obj.x, obj.y, 0f, 0f) < 500f) return
            if (obj.x < -WORLD_HALF + 120f || obj.x > WORLD_HALF - 120f) return
            if (obj.y < -WORLD_HALF + 120f || obj.y > WORLD_HALF - 120f) return
            objs.add(obj)
        }

        // Сундуков теперь заметно больше: 1 гарантированный + шанс второго.
        tryAdd(Chest(px(), py()))
        if (rng.nextFloat() < 0.45f) tryAdd(Chest(px(), py()))

        if (rng.nextFloat() < 0.40f) {
            val kind = ShrineKind.entries[rng.nextInt(ShrineKind.entries.size)]
            tryAdd(Shrine(px(), py(), kind))
        }
        if (rng.nextFloat() < 0.18f) tryAdd(Statue(px(), py()))

        // Кучки золота — награда за движение по карте.
        repeat(3) {
            tryAdd(GoldPile(px(), py(), 8 + rng.nextInt(15)))
        }
        return objs
    }
}
