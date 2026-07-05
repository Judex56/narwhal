package com.megasurvivors.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class Renderer(private val game: Game) {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private val bgColor = Color.rgb(26, 34, 24)
    private val gridColor = Color.rgb(34, 44, 32)
    private val decorColor = Color.rgb(45, 58, 42)

    fun draw(canvas: Canvas, joystick: Joystick) {
        if (game.state == GameState.MENU) {
            drawMenu(canvas)
            return
        }

        val g = game
        val camX = g.player.x - g.screenW / 2f
        val camY = g.player.y - g.screenH / 2f

        canvas.drawColor(bgColor)
        canvas.save()
        canvas.translate(-camX, -camY)

        drawGround(canvas, camX, camY)
        drawWorldBorder(canvas)
        drawWorldObjects(canvas)
        drawPickups(canvas)
        drawAuras(canvas)
        drawBooms(canvas)
        drawEnemies(canvas)
        drawPlayer(canvas)
        drawSpinEffect(canvas)
        drawOrbitBlades(canvas)
        drawSweepBlades(canvas)
        drawProjectiles(canvas)
        drawBeams(canvas)
        drawLightning(canvas)
        drawFloatingTexts(canvas)

        canvas.restore()

        if (g.nukeFlash > 0f) {
            fill.color = Color.argb((110 * g.nukeFlash / 0.5f).toInt(), 255, 120, 80)
            canvas.drawRect(0f, 0f, g.screenW, g.screenH, fill)
        }

        drawHud(canvas)
        drawMinimap(canvas)
        joystick.draw(canvas, fill, stroke)

        when (g.state) {
            GameState.LEVEL_UP -> drawLevelUp(canvas)
            GameState.ITEM_POPUP -> drawItemPopup(canvas)
            GameState.PAUSED -> drawCenterMessage(canvas, "ПАУЗА", "Коснитесь, чтобы продолжить")
            GameState.GAME_OVER -> drawGameOver(canvas)
            else -> Unit
        }
    }

    // ------------------------------------------------------------------
    // Мир.
    // ------------------------------------------------------------------
    private fun drawGround(canvas: Canvas, camX: Float, camY: Float) {
        val g = game
        val cell = 175f
        stroke.color = gridColor
        stroke.strokeWidth = 2f
        var x = ((camX / cell).toInt() - 1) * cell
        while (x < camX + g.screenW + cell) {
            canvas.drawLine(x, camY, x, camY + g.screenH, stroke)
            x += cell
        }
        var y = ((camY / cell).toInt() - 1) * cell
        while (y < camY + g.screenH + cell) {
            canvas.drawLine(camX, y, camX + g.screenW, y, stroke)
            y += cell
        }

        fill.color = decorColor
        val cs = CHUNK_SIZE.toInt()
        val c0x = Math.floorDiv(camX.toInt(), cs)
        val c0y = Math.floorDiv(camY.toInt(), cs)
        for (cx in c0x..c0x + (g.screenW / CHUNK_SIZE).toInt() + 1) {
            for (cy in c0y..c0y + (g.screenH / CHUNK_SIZE).toInt() + 1) {
                val rng = Random(game.world.chunkSeed(cx, cy) * 31)
                repeat(14) {
                    val dx = cx * CHUNK_SIZE + rng.nextFloat() * CHUNK_SIZE
                    val dy = cy * CHUNK_SIZE + rng.nextFloat() * CHUNK_SIZE
                    if (dx > -WORLD_HALF && dx < WORLD_HALF && dy > -WORLD_HALF && dy < WORLD_HALF) {
                        canvas.drawCircle(dx, dy, 6f + rng.nextFloat() * 14f, fill)
                    }
                }
            }
        }
    }

    private fun drawWorldBorder(canvas: Canvas) {
        // Тьма за границей мира.
        fill.color = Color.rgb(12, 14, 12)
        val h = WORLD_HALF
        val far = h + 3000f
        canvas.drawRect(-far, -far, far, -h, fill)
        canvas.drawRect(-far, h, far, far, fill)
        canvas.drawRect(-far, -h, -h, h, fill)
        canvas.drawRect(h, -h, far, h, fill)
        stroke.color = Color.rgb(255, 112, 67)
        stroke.strokeWidth = 8f
        canvas.drawRect(-h, -h, h, h, stroke)
    }

    private fun drawWorldObjects(canvas: Canvas) {
        for (obj in game.world.objectsAround(game.player.x, game.player.y, 2)) {
            if (obj.consumed) continue
            when (obj) {
                is Chest -> {
                    fill.color = Color.rgb(121, 85, 72)
                    canvas.drawRoundRect(
                        RectF(obj.x - 34f, obj.y - 26f, obj.x + 34f, obj.y + 26f), 8f, 8f, fill,
                    )
                    fill.color = Color.rgb(255, 213, 79)
                    canvas.drawRect(obj.x - 34f, obj.y - 6f, obj.x + 34f, obj.y + 2f, fill)
                    canvas.drawCircle(obj.x, obj.y - 2f, 7f, fill)
                    stroke.color = Color.rgb(62, 39, 35)
                    stroke.strokeWidth = 4f
                    canvas.drawRoundRect(
                        RectF(obj.x - 34f, obj.y - 26f, obj.x + 34f, obj.y + 26f), 8f, 8f, stroke,
                    )
                    // Цена открытия.
                    text.textAlign = Paint.Align.CENTER
                    text.textSize = 24f
                    text.color = Color.rgb(255, 213, 79)
                    canvas.drawText("${game.chestCost}з", obj.x, obj.y - 40f, text)
                }
                is GoldPile -> {
                    fill.color = Color.rgb(255, 193, 7)
                    canvas.drawCircle(obj.x - 10f, obj.y + 6f, 12f, fill)
                    canvas.drawCircle(obj.x + 10f, obj.y + 4f, 10f, fill)
                    canvas.drawCircle(obj.x, obj.y - 8f, 11f, fill)
                    stroke.color = Color.rgb(255, 160, 0)
                    stroke.strokeWidth = 3f
                    canvas.drawCircle(obj.x, obj.y - 8f, 11f, stroke)
                }
                is Shrine -> drawCaptureZone(
                    canvas, obj.x, obj.y, obj.radius, obj.progress, obj.kind.color,
                ) {
                    fill.color = Color.rgb(69, 90, 100)
                    canvas.drawRect(obj.x - 26f, obj.y, obj.x + 26f, obj.y + 34f, fill)
                    fill.color = obj.kind.color
                    val path = Path()
                    path.moveTo(obj.x, obj.y - 46f)
                    path.lineTo(obj.x + 22f, obj.y - 6f)
                    path.lineTo(obj.x, obj.y + 8f)
                    path.lineTo(obj.x - 22f, obj.y - 6f)
                    path.close()
                    canvas.drawPath(path, fill)
                }
                is Statue -> drawCaptureZone(
                    canvas, obj.x, obj.y, obj.radius, obj.progress, Color.rgb(178, 223, 219),
                ) {
                    fill.color = Color.rgb(120, 144, 156)
                    canvas.drawRect(obj.x - 30f, obj.y + 14f, obj.x + 30f, obj.y + 36f, fill)
                    canvas.drawRect(obj.x - 14f, obj.y - 30f, obj.x + 14f, obj.y + 14f, fill)
                    canvas.drawCircle(obj.x, obj.y - 42f, 15f, fill)
                }
            }
        }
    }

    private inline fun drawCaptureZone(
        canvas: Canvas,
        x: Float,
        y: Float,
        radius: Float,
        progress: Float,
        color: Int,
        body: () -> Unit,
    ) {
        fill.color = (color and 0x00FFFFFF) or 0x22000000
        canvas.drawCircle(x, y, radius, fill)
        stroke.color = (color and 0x00FFFFFF) or 0x88000000.toInt()
        stroke.strokeWidth = 5f
        canvas.drawCircle(x, y, radius, stroke)
        if (progress > 0f) {
            stroke.color = color
            stroke.strokeWidth = 12f
            canvas.drawArc(
                RectF(x - radius, y - radius, x + radius, y + radius),
                -90f, 360f * progress.coerceIn(0f, 1f), false, stroke,
            )
        }
        body()
    }

    private fun drawPickups(canvas: Canvas) {
        for (p in game.pickups) {
            when (p.type) {
                PickupType.XP -> {
                    fill.color = if (p.value >= 4f) Color.rgb(255, 213, 79) else Color.rgb(38, 198, 218)
                    val r = if (p.value >= 4f) 14f else 10f
                    val path = Path()
                    path.moveTo(p.x, p.y - r)
                    path.lineTo(p.x + r * 0.7f, p.y)
                    path.lineTo(p.x, p.y + r)
                    path.lineTo(p.x - r * 0.7f, p.y)
                    path.close()
                    canvas.drawPath(path, fill)
                }
                PickupType.GOLD -> {
                    fill.color = Color.rgb(255, 193, 7)
                    canvas.drawCircle(p.x, p.y, 9f, fill)
                    stroke.color = Color.rgb(255, 160, 0)
                    stroke.strokeWidth = 2.5f
                    canvas.drawCircle(p.x, p.y, 9f, stroke)
                }
                PickupType.HP -> {
                    fill.color = Color.rgb(239, 83, 80)
                    canvas.drawRect(p.x - 5f, p.y - 13f, p.x + 5f, p.y + 13f, fill)
                    canvas.drawRect(p.x - 13f, p.y - 5f, p.x + 13f, p.y + 5f, fill)
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Сущности и эффекты оружия.
    // ------------------------------------------------------------------
    private fun auraFor(type: WeaponType): Pair<Float, Int>? {
        val w = game.player.weapons.firstOrNull { it.type == type } ?: return null
        val areaMult = game.player.mult(Stat.AREA)
        return when (type) {
            WeaponType.AURA -> Pair(WeaponBalance.auraRadius(w.level) * areaMult, Color.rgb(255, 112, 67))
            WeaponType.FROST_AURA -> Pair(WeaponBalance.frostRadius(w.level) * areaMult, Color.rgb(128, 222, 234))
            WeaponType.ICE_STORM -> Pair(WeaponBalance.ICE_STORM_RADIUS * areaMult, Color.rgb(0, 229, 255))
            WeaponType.PLAGUE_CLOUD -> Pair(WeaponBalance.PLAGUE_RADIUS * areaMult, Color.rgb(118, 255, 3))
            else -> null
        }
    }

    private fun drawAuras(canvas: Canvas) {
        for (type in listOf(
            WeaponType.AURA, WeaponType.FROST_AURA,
            WeaponType.ICE_STORM, WeaponType.PLAGUE_CLOUD,
        )) {
            val (r, color) = auraFor(type) ?: continue
            fill.color = (color and 0x00FFFFFF) or 0x26000000
            canvas.drawCircle(game.player.x, game.player.y, r, fill)
            stroke.color = (color and 0x00FFFFFF) or 0x66000000.toInt()
            stroke.strokeWidth = 3f
            canvas.drawCircle(game.player.x, game.player.y, r, stroke)
        }
    }

    private fun drawBooms(canvas: Canvas) {
        for (b in game.booms) {
            if (!b.exploded) {
                // Метка приземления.
                stroke.color = Color.argb(160, 255, 167, 38)
                stroke.strokeWidth = 4f
                canvas.drawCircle(b.x, b.y, b.radius, stroke)
                fill.color = Color.argb(60, 255, 167, 38)
                canvas.drawCircle(b.x, b.y, b.radius * (1f - b.delay / 0.7f).coerceIn(0f, 1f), fill)
            } else {
                val t = (b.flash / 0.35f).coerceIn(0f, 1f)
                fill.color = Color.argb((200 * t).toInt(), 255, 200, 90)
                canvas.drawCircle(b.x, b.y, b.radius * (1.4f - 0.4f * t), fill)
            }
        }
    }

    private fun drawEnemies(canvas: Canvas) {
        for (e in game.enemies) {
            fill.color = when {
                e.hitFlash > 0f -> Color.WHITE
                e.freezeTimer > 0f -> Color.rgb(100, 181, 246)
                e.poisonStacks > 0 -> blend(e.type.color, Color.rgb(118, 255, 3), 0.35f)
                e.slowTimer > 0f -> blend(e.type.color, Color.rgb(128, 222, 234), 0.35f)
                else -> e.type.color
            }
            canvas.drawCircle(e.x, e.y, e.type.radius, fill)
            val d = dist(e.x, e.y, game.player.x, game.player.y).coerceAtLeast(1f)
            val ex = (game.player.x - e.x) / d * e.type.radius * 0.4f
            val ey = (game.player.y - e.y) / d * e.type.radius * 0.4f
            fill.color = Color.BLACK
            canvas.drawCircle(e.x + ex - e.type.radius * 0.22f, e.y + ey, e.type.radius * 0.14f, fill)
            canvas.drawCircle(e.x + ex + e.type.radius * 0.22f, e.y + ey, e.type.radius * 0.14f, fill)

            if (e.type == EnemyType.ELITE || e.hp < e.maxHp) {
                val w = e.type.radius * 2f
                val top = e.y - e.type.radius - 14f
                fill.color = Color.rgb(40, 40, 40)
                canvas.drawRect(e.x - w / 2, top, e.x + w / 2, top + 6f, fill)
                fill.color = Color.rgb(239, 83, 80)
                canvas.drawRect(e.x - w / 2, top, e.x - w / 2 + w * (e.hp / e.maxHp), top + 6f, fill)
            }
        }
    }

    private fun blend(c1: Int, c2: Int, t: Float): Int {
        val r = (Color.red(c1) * (1 - t) + Color.red(c2) * t).toInt()
        val g = (Color.green(c1) * (1 - t) + Color.green(c2) * t).toInt()
        val b = (Color.blue(c1) * (1 - t) + Color.blue(c2) * t).toInt()
        return Color.rgb(r, g, b)
    }

    private fun drawPlayer(canvas: Canvas) {
        val p = game.player
        if (p.iFrames > 0f && (p.iFrames * 20).toInt() % 2 == 0) return
        fill.color = Color.rgb(236, 239, 241)
        canvas.drawCircle(p.x, p.y, p.radius, fill)
        fill.color = Color.rgb(38, 50, 56)
        canvas.drawCircle(p.x + 8f * p.facing, p.y - 6f, 4.5f, fill)
        canvas.drawCircle(p.x + 16f * p.facing, p.y - 6f, 4.5f, fill)
        stroke.color = Color.rgb(96, 125, 139)
        stroke.strokeWidth = 4f
        canvas.drawCircle(p.x, p.y, p.radius, stroke)
    }

    private fun drawSpinEffect(canvas: Canvas) {
        if (game.spinEffect <= 0f) return
        val w = game.player.weapons.firstOrNull { it.type == WeaponType.COUNTER_SPIN } ?: return
        val radius = WeaponBalance.spinRadius(w.level) * game.player.mult(Stat.AREA)
        val t = (game.spinEffect / 0.4f).coerceIn(0f, 1f)
        stroke.color = Color.argb((220 * t).toInt(), 255, 112, 67)
        stroke.strokeWidth = 10f
        canvas.drawCircle(game.player.x, game.player.y, radius * (1.2f - 0.2f * t), stroke)
    }

    private fun drawOrbitBlades(canvas: Canvas) {
        val w = game.player.weapons.firstOrNull { it.type == WeaponType.ORBIT } ?: return
        val count = WeaponBalance.orbitCount(w.level)
        val radius = WeaponBalance.orbitRadius(w.level) * game.player.mult(Stat.AREA)
        fill.color = WeaponType.ORBIT.color
        for (i in 0 until count) {
            val a = w.orbitAngle + i * (Math.PI.toFloat() * 2f / count)
            val bx = game.player.x + cos(a) * radius
            val by = game.player.y + sin(a) * radius
            canvas.save()
            canvas.rotate(a * 180f / Math.PI.toFloat() + 90f, bx, by)
            canvas.drawRoundRect(RectF(bx - 8f, by - 24f, bx + 8f, by + 24f), 6f, 6f, fill)
            canvas.restore()
        }
    }

    /** Коса и Жнец: большой клинок-полумесяц на конце рукояти. */
    private fun drawSweepBlades(canvas: Canvas) {
        for (w in game.player.weapons) {
            val (blades, radius, color) = when (w.type) {
                WeaponType.SCYTHE -> Triple(
                    1, WeaponBalance.scytheRadius(w.level) * game.player.mult(Stat.AREA),
                    WeaponType.SCYTHE.color,
                )
                WeaponType.REAPER -> Triple(
                    WeaponBalance.REAPER_BLADES,
                    WeaponBalance.REAPER_RADIUS * game.player.mult(Stat.AREA),
                    WeaponType.REAPER.color,
                )
                else -> continue
            }
            for (b in 0 until blades) {
                val a = w.orbitAngle + b * (Math.PI.toFloat() * 2f / blades)
                val bx = game.player.x + cos(a) * radius
                val by = game.player.y + sin(a) * radius
                stroke.color = (color and 0x00FFFFFF) or 0x55000000
                stroke.strokeWidth = 6f
                canvas.drawLine(game.player.x, game.player.y, bx, by, stroke)
                // След взмаха.
                stroke.color = (color and 0x00FFFFFF) or 0x44000000
                stroke.strokeWidth = 26f
                val deg = a * 180f / Math.PI.toFloat()
                canvas.drawArc(
                    RectF(
                        game.player.x - radius, game.player.y - radius,
                        game.player.x + radius, game.player.y + radius,
                    ),
                    deg - 40f, 40f, false, stroke,
                )
                fill.color = color
                canvas.save()
                canvas.rotate(deg + 90f, bx, by)
                canvas.drawRoundRect(RectF(bx - 10f, by - 34f, bx + 10f, by + 34f), 8f, 8f, fill)
                canvas.restore()
            }
        }
    }

    private fun drawProjectiles(canvas: Canvas) {
        for (p in game.projectiles) {
            fill.color = p.color
            when (p.kind) {
                ProjKind.BANANA -> {
                    // Вращающийся полумесяц.
                    canvas.save()
                    canvas.rotate(p.age * 720f, p.x, p.y)
                    val r = RectF(p.x - 16f, p.y - 16f, p.x + 16f, p.y + 16f)
                    stroke.color = p.color
                    stroke.strokeWidth = 9f
                    canvas.drawArc(r, 20f, 200f, false, stroke)
                    canvas.restore()
                }
                ProjKind.SLASH -> {
                    canvas.save()
                    canvas.rotate(p.age * 540f, p.x, p.y)
                    canvas.drawRoundRect(RectF(p.x - 20f, p.y - 6f, p.x + 20f, p.y + 6f), 5f, 5f, fill)
                    canvas.restore()
                }
                else -> canvas.drawCircle(p.x, p.y, p.radius, fill)
            }
        }
    }

    private fun drawBeams(canvas: Canvas) {
        for (b in game.beams) {
            val t = (b[4] / 0.15f).coerceIn(0f, 1f)
            stroke.color = Color.argb((200 * t).toInt(), 186, 104, 200)
            stroke.strokeWidth = 30f * t + 6f
            canvas.drawLine(b[0], b[1], b[2], b[3], stroke)
            stroke.color = Color.argb((240 * t).toInt(), 255, 255, 255)
            stroke.strokeWidth = 6f * t + 2f
            canvas.drawLine(b[0], b[1], b[2], b[3], stroke)
        }
    }

    private fun drawLightning(canvas: Canvas) {
        stroke.color = WeaponType.LIGHTNING.color
        for (bolt in game.lightningBolts) {
            stroke.strokeWidth = 6f * (bolt[4] / 0.25f)
            var y = bolt[1]
            var x = bolt[0]
            val rng = Random((bolt[2] * 13 + bolt[3] * 7).toInt())
            while (y < bolt[3]) {
                val ny = (y + 90f).coerceAtMost(bolt[3])
                val nx = if (ny >= bolt[3]) bolt[2] else bolt[2] + rng.nextFloat() * 60f - 30f
                canvas.drawLine(x, y, nx, ny, stroke)
                x = nx
                y = ny
            }
        }
    }

    private fun drawFloatingTexts(canvas: Canvas) {
        text.textAlign = Paint.Align.CENTER
        for (t in game.texts) {
            text.color = t.color
            text.alpha = (255 * (t.life / t.maxLife)).toInt().coerceIn(0, 255)
            text.textSize = t.size
            canvas.drawText(t.text, t.x, t.y, text)
        }
        text.alpha = 255
    }

    // ------------------------------------------------------------------
    // HUD.
    // ------------------------------------------------------------------
    private fun drawHud(canvas: Canvas) {
        val g = game
        fill.color = Color.rgb(30, 30, 40)
        canvas.drawRect(0f, 0f, g.screenW, 14f, fill)
        fill.color = Color.rgb(38, 198, 218)
        canvas.drawRect(0f, 0f, g.screenW * (g.player.xp / g.player.xpToNext), 14f, fill)

        val hpW = 320f
        fill.color = Color.rgb(30, 30, 30)
        canvas.drawRoundRect(RectF(24f, 30f, 24f + hpW, 62f), 8f, 8f, fill)
        fill.color = Color.rgb(229, 57, 53)
        val hpFrac = (g.player.hp / g.player.maxHp).coerceIn(0f, 1f)
        canvas.drawRoundRect(RectF(24f, 30f, 24f + hpW * hpFrac, 62f), 8f, 8f, fill)
        text.textAlign = Paint.Align.CENTER
        text.color = Color.WHITE
        text.textSize = 24f
        canvas.drawText("${g.player.hp.toInt()} / ${g.player.maxHp.toInt()}", 24f + hpW / 2f, 54f, text)
        text.textAlign = Paint.Align.LEFT
        canvas.drawText("Ур. ${g.player.level}", 24f + hpW + 18f, 54f, text)

        // Золото.
        fill.color = Color.rgb(255, 193, 7)
        canvas.drawCircle(36f, 90f, 12f, fill)
        text.color = Color.rgb(255, 213, 79)
        text.textSize = 28f
        canvas.drawText("${g.gold}", 56f, 100f, text)

        text.textAlign = Paint.Align.CENTER
        text.color = Color.WHITE
        text.textSize = 44f
        val m = (g.time / 60).toInt()
        val s = (g.time % 60).toInt()
        canvas.drawText(String.format("%02d:%02d", m, s), g.screenW / 2f, 70f, text)
        text.textSize = 24f
        text.color = Color.rgb(255, 171, 145)
        canvas.drawText("Убито: ${g.kills}", g.screenW / 2f, 100f, text)

        fill.color = 0x55000000
        canvas.drawRoundRect(game.pauseRect, 12f, 12f, fill)
        fill.color = Color.WHITE
        val pr = game.pauseRect
        canvas.drawRect(pr.centerX() - 14f, pr.centerY() - 16f, pr.centerX() - 4f, pr.centerY() + 16f, fill)
        canvas.drawRect(pr.centerX() + 4f, pr.centerY() - 16f, pr.centerX() + 14f, pr.centerY() + 16f, fill)

        drawBuffs(canvas)
        drawInventory(canvas)
    }

    private fun drawBuffs(canvas: Canvas) {
        var y = 130f
        text.textAlign = Paint.Align.LEFT
        text.textSize = 22f
        for (b in game.player.buffs) {
            fill.color = (b.color and 0x00FFFFFF) or 0x44000000
            val label = if (b.permanent) b.name else "${b.name} ${b.remaining.toInt()}с"
            val w = text.measureText(label) + 24f
            canvas.drawRoundRect(RectF(24f, y, 24f + w, y + 34f), 8f, 8f, fill)
            text.color = b.color
            canvas.drawText(label, 36f, y + 25f, text)
            y += 42f
        }
    }

    /** Собранные предметы — ряд цветных точек по тиру внизу справа. */
    private fun drawInventory(canvas: Canvas) {
        val items = game.player.items
        if (items.isEmpty()) return
        val size = 44f
        val gap = 10f
        val maxPerRow = 12
        val startX = game.screenW - 24f - (minOf(items.size, maxPerRow)) * (size + gap)
        var x = startX
        var y = game.screenH - size - 20f
        for ((idx, item) in items.withIndex()) {
            if (idx > 0 && idx % maxPerRow == 0) {
                x = startX
                y -= size + gap
            }
            fill.color = (item.tier.color and 0x00FFFFFF) or 0x77000000
            canvas.drawRoundRect(RectF(x, y, x + size, y + size), 8f, 8f, fill)
            stroke.color = item.tier.color
            stroke.strokeWidth = 3f
            canvas.drawRoundRect(RectF(x, y, x + size, y + size), 8f, 8f, stroke)
            text.textAlign = Paint.Align.CENTER
            text.color = Color.WHITE
            text.textSize = 18f
            canvas.drawText(item.name.take(2), x + size / 2f, y + size / 2f + 6f, text)
            x += size + gap
        }
    }

    /** Миникарта всей (ограниченной) карты с объектами. */
    private fun drawMinimap(canvas: Canvas) {
        val size = 250f
        val left = game.screenW - size - 24f
        val top = 130f
        val scale = size / (WORLD_HALF * 2f)

        fun mx(wx: Float) = left + (wx + WORLD_HALF) * scale
        fun my(wy: Float) = top + (wy + WORLD_HALF) * scale

        fill.color = 0x77000000
        canvas.drawRoundRect(RectF(left, top, left + size, top + size), 10f, 10f, fill)
        stroke.color = 0x88FFFFFF.toInt()
        stroke.strokeWidth = 3f
        canvas.drawRoundRect(RectF(left, top, left + size, top + size), 10f, 10f, stroke)

        for (obj in game.world.allObjects) {
            if (obj.consumed) continue
            when (obj) {
                is Chest -> {
                    fill.color = Color.rgb(255, 213, 79)
                    canvas.drawRect(
                        mx(obj.x) - 4f, my(obj.y) - 4f, mx(obj.x) + 4f, my(obj.y) + 4f, fill,
                    )
                }
                is Shrine -> {
                    fill.color = obj.kind.color
                    canvas.drawCircle(mx(obj.x), my(obj.y), 5f, fill)
                }
                is Statue -> {
                    fill.color = Color.rgb(178, 223, 219)
                    canvas.drawCircle(mx(obj.x), my(obj.y), 5f, fill)
                }
                is GoldPile -> {
                    fill.color = Color.rgb(255, 160, 0)
                    canvas.drawCircle(mx(obj.x), my(obj.y), 2.5f, fill)
                }
            }
        }
        // Элита на миникарте.
        for (e in game.enemies) {
            if (e.type == EnemyType.ELITE) {
                fill.color = Color.rgb(255, 82, 82)
                canvas.drawCircle(mx(e.x), my(e.y), 5f, fill)
            }
        }
        // Игрок.
        fill.color = Color.WHITE
        canvas.drawCircle(mx(game.player.x), my(game.player.y), 6f, fill)
    }

    // ------------------------------------------------------------------
    // Меню.
    // ------------------------------------------------------------------
    private fun drawMenu(canvas: Canvas) {
        val g = game
        val menu = g.menu
        canvas.drawColor(Color.rgb(22, 27, 34))

        text.textAlign = Paint.Align.LEFT
        text.color = Color.rgb(255, 213, 79)
        text.textSize = 60f
        canvas.drawText("MEGA SURVIVORS", 40f, 84f, text)
        text.color = Color.rgb(120, 144, 156)
        text.textSize = 24f
        canvas.drawText(
            "v${g.version}" + if (g.meta.bestTime > 0) {
                "   рекорд: ${g.meta.bestTime / 60}:${String.format("%02d", g.meta.bestTime % 60)}"
            } else {
                ""
            },
            40f, 120f, text,
        )

        // Золото справа сверху.
        text.textAlign = Paint.Align.RIGHT
        text.color = Color.rgb(255, 213, 79)
        text.textSize = 40f
        canvas.drawText("${g.meta.gold} з", g.screenW - 40f, 84f, text)

        // Заголовки колонок.
        text.textAlign = Paint.Align.LEFT
        text.textSize = 28f
        text.color = Color.WHITE
        if (menu.weaponCells.isNotEmpty()) {
            canvas.drawText("ОРУЖИЕ", menu.weaponCells[0].rect.left, 158f, text)
        }
        if (menu.itemCells.isNotEmpty()) {
            canvas.drawText("ПРЕДМЕТ", menu.itemCells[0].rect.left, 158f, text)
        }
        if (menu.tomeCells.isNotEmpty()) {
            canvas.drawText("ФОЛИАНТЫ (${g.meta.selectedTomes.size}/$MAX_TOMES)", menu.tomeCells[0].rect.left, 158f, text)
        }

        // Оружие.
        for (i in menu.weaponCells.indices) {
            val type = menu.weaponTypes[i]
            val r = menu.weaponCells[i].rect
            val unlocked = g.meta.isUnlocked(type)
            val selected = g.meta.selectedWeapon == type.name

            fill.color = if (unlocked) {
                (type.color and 0x00FFFFFF) or 0x55000000
            } else {
                0x33000000
            }
            canvas.drawRoundRect(r, 12f, 12f, fill)
            stroke.strokeWidth = if (selected) 6f else 3f
            stroke.color = when {
                selected -> Color.WHITE
                unlocked -> type.color
                else -> 0x44FFFFFF
            }
            canvas.drawRoundRect(r, 12f, 12f, stroke)

            text.textAlign = Paint.Align.CENTER
            text.textSize = 30f
            text.color = if (unlocked) Color.WHITE else 0x77FFFFFF
            canvas.drawText(type.code, r.centerX(), r.centerY() + 10f, text)

            text.textSize = 17f
            text.color = if (unlocked) Color.rgb(207, 216, 220) else Color.rgb(120, 144, 156)
            canvas.drawText(type.label, r.centerX(), r.bottom + 20f, text)
            if (!unlocked) {
                text.color = Color.rgb(255, 213, 79)
                text.textSize = 20f
                canvas.drawText("${type.price}з", r.centerX(), r.centerY() + 42f, text)
            }
        }

        // Предметы.
        for (i in menu.itemCells.indices) {
            val item = menu.startItems[i]
            val r = menu.itemCells[i].rect
            val selected = g.meta.selectedItem == item.id
            fill.color = (item.tier.color and 0x00FFFFFF) or 0x55000000
            canvas.drawRoundRect(r, 12f, 12f, fill)
            stroke.strokeWidth = if (selected) 6f else 3f
            stroke.color = if (selected) Color.WHITE else item.tier.color
            canvas.drawRoundRect(r, 12f, 12f, stroke)
            text.textAlign = Paint.Align.CENTER
            text.textSize = 26f
            text.color = Color.WHITE
            canvas.drawText(item.name.take(2), r.centerX(), r.centerY() + 9f, text)
            text.textSize = 16f
            text.color = Color.rgb(207, 216, 220)
            canvas.drawText(item.name, r.centerX(), r.bottom + 20f, text)
        }

        // Фолианты.
        for (i in menu.tomeCells.indices) {
            val tome = menu.tomes[i]
            val r = menu.tomeCells[i].rect
            val selected = menu.isTomeSelected(tome)
            fill.color = (tome.color and 0x00FFFFFF) or 0x55000000
            canvas.drawRoundRect(r, 12f, 12f, fill)
            stroke.strokeWidth = if (selected) 6f else 3f
            stroke.color = if (selected) Color.WHITE else tome.color
            canvas.drawRoundRect(r, 12f, 12f, stroke)
            text.textAlign = Paint.Align.CENTER
            text.textSize = 26f
            text.color = Color.WHITE
            canvas.drawText(tome.label.takeLast(tome.label.length - 8).take(2).uppercase(), r.centerX(), r.centerY() + 9f, text)
            text.textSize = 15f
            text.color = Color.rgb(207, 216, 220)
            canvas.drawText(tome.desc, r.centerX(), r.bottom + 20f, text)
        }

        // Инфо-строка.
        text.textAlign = Paint.Align.CENTER
        text.textSize = 24f
        text.color = Color.rgb(176, 190, 197)
        canvas.drawText(menu.infoText, g.screenW / 2f, menu.startRect.top - 26f, text)

        // Кнопка старта.
        fill.color = Color.rgb(46, 125, 50)
        canvas.drawRoundRect(menu.startRect, 18f, 18f, fill)
        stroke.color = Color.rgb(129, 199, 132)
        stroke.strokeWidth = 4f
        canvas.drawRoundRect(menu.startRect, 18f, 18f, stroke)
        text.color = Color.WHITE
        text.textSize = 44f
        canvas.drawText("В БОЙ!", menu.startRect.centerX(), menu.startRect.centerY() + 15f, text)
    }

    // ------------------------------------------------------------------
    // Оверлеи.
    // ------------------------------------------------------------------
    private fun dimScreen(canvas: Canvas) {
        fill.color = 0xB2000000.toInt()
        canvas.drawRect(0f, 0f, game.screenW, game.screenH, fill)
    }

    private fun drawLevelUp(canvas: Canvas) {
        dimScreen(canvas)
        text.textAlign = Paint.Align.CENTER
        text.color = Color.rgb(255, 213, 79)
        text.textSize = 52f
        canvas.drawText("НОВЫЙ УРОВЕНЬ!", game.screenW / 2f, game.optionRects[0].top - 40f, text)

        for (i in game.upgradeOptions.indices) {
            val r = game.optionRects[i]
            val opt = game.upgradeOptions[i]
            fill.color = Color.rgb(38, 45, 52)
            canvas.drawRoundRect(r, 18f, 18f, fill)
            stroke.color = opt.color
            stroke.strokeWidth = 5f
            canvas.drawRoundRect(r, 18f, 18f, stroke)

            fill.color = opt.color
            canvas.drawCircle(r.centerX(), r.top + 90f, 36f, fill)

            text.color = Color.WHITE
            text.textSize = 32f
            drawWrapped(canvas, opt.title, r.centerX(), r.top + 180f, r.width() - 40f)
            text.color = Color.rgb(176, 190, 197)
            text.textSize = 26f
            drawWrapped(canvas, opt.desc, r.centerX(), r.top + 260f, r.width() - 40f)
        }
    }

    private fun drawItemPopup(canvas: Canvas) {
        dimScreen(canvas)
        val item = game.popupItem ?: return
        val w = minOf(game.screenW * 0.5f, 640f)
        val h = game.screenH * 0.62f
        val r = RectF(
            (game.screenW - w) / 2f, (game.screenH - h) / 2f,
            (game.screenW + w) / 2f, (game.screenH + h) / 2f,
        )
        fill.color = Color.rgb(38, 45, 52)
        canvas.drawRoundRect(r, 20f, 20f, fill)
        stroke.color = item.tier.color
        stroke.strokeWidth = 6f
        canvas.drawRoundRect(r, 20f, 20f, stroke)

        text.textAlign = Paint.Align.CENTER
        text.color = item.tier.color
        text.textSize = 30f
        canvas.drawText(item.tier.label.uppercase(), r.centerX(), r.top + 60f, text)
        text.color = Color.WHITE
        text.textSize = 38f
        drawWrapped(canvas, item.name, r.centerX(), r.top + 115f, r.width() - 60f)

        text.textSize = 28f
        var y = r.top + 190f
        for ((stat, v) in item.stats) {
            text.color = Color.rgb(129, 199, 132)
            canvas.drawText("+${(v * 100).toInt()}% ${stat.label}", r.centerX(), y, text)
            y += 44f
        }
        if (item.desc.isNotEmpty()) {
            text.color = Color.rgb(255, 213, 79)
            text.textSize = 25f
            y += 12f
            drawWrapped(canvas, item.desc, r.centerX(), y, r.width() - 60f)
        }

        text.color = Color.rgb(255, 213, 79)
        text.textSize = 26f
        canvas.drawText("Коснитесь, чтобы продолжить", r.centerX(), r.bottom - 40f, text)
    }

    private fun drawCenterMessage(canvas: Canvas, title: String, sub: String) {
        dimScreen(canvas)
        text.textAlign = Paint.Align.CENTER
        text.color = Color.WHITE
        text.textSize = 64f
        canvas.drawText(title, game.screenW / 2f, game.screenH / 2f - 20f, text)
        text.textSize = 28f
        text.color = Color.rgb(176, 190, 197)
        canvas.drawText(sub, game.screenW / 2f, game.screenH / 2f + 50f, text)
    }

    private fun drawGameOver(canvas: Canvas) {
        dimScreen(canvas)
        text.textAlign = Paint.Align.CENTER
        text.color = Color.rgb(239, 83, 80)
        text.textSize = 72f
        canvas.drawText("ВЫ ПОГИБЛИ", game.screenW / 2f, game.screenH / 2f - 120f, text)
        text.color = Color.WHITE
        text.textSize = 32f
        val m = (game.time / 60).toInt()
        val s = (game.time % 60).toInt()
        canvas.drawText(
            String.format("Выжили %02d:%02d — убито %d — уровень %d", m, s, game.kills, game.player.level),
            game.screenW / 2f, game.screenH / 2f - 40f, text,
        )
        text.color = Color.rgb(255, 213, 79)
        canvas.drawText("Золото в копилку: +${game.gold}", game.screenW / 2f, game.screenH / 2f + 20f, text)
        text.textSize = 28f
        canvas.drawText("Коснитесь, чтобы вернуться в меню", game.screenW / 2f, game.screenH / 2f + 90f, text)
    }

    private fun drawWrapped(canvas: Canvas, str: String, cx: Float, y: Float, maxW: Float) {
        var line = ""
        var yy = y
        for (word in str.split(" ")) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (text.measureText(candidate) > maxW && line.isNotEmpty()) {
                canvas.drawText(line, cx, yy, text)
                yy += text.textSize + 8f
                line = word
            } else {
                line = candidate
            }
        }
        if (line.isNotEmpty()) canvas.drawText(line, cx, yy, text)
    }
}
