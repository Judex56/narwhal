package com.megasurvivors.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
        val g = game
        val camX = g.player.x - g.screenW / 2f
        val camY = g.player.y - g.screenH / 2f

        canvas.drawColor(bgColor)
        canvas.save()
        canvas.translate(-camX, -camY)

        drawGround(canvas, camX, camY)
        drawWorldObjects(canvas)
        drawPickups(canvas)
        drawAura(canvas)
        drawEnemies(canvas)
        drawPlayer(canvas)
        drawOrbitBlades(canvas)
        drawProjectiles(canvas)
        drawLightning(canvas)
        drawFloatingTexts(canvas)

        canvas.restore()

        drawHud(canvas)
        joystick.draw(canvas, fill, stroke)

        when (g.state) {
            GameState.LEVEL_UP -> drawLevelUp(canvas)
            GameState.ITEM_POPUP -> drawItemPopup(canvas)
            GameState.PAUSED -> drawCenterMessage(canvas, "ПАУЗА", "Коснитесь, чтобы продолжить")
            GameState.GAME_OVER -> drawGameOver(canvas)
            GameState.RUNNING -> Unit
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

        // Декор (камни/трава) детерминированно по чанкам.
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
                    val r = 6f + rng.nextFloat() * 14f
                    canvas.drawCircle(dx, dy, r, fill)
                }
            }
        }
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
                }
                is Shrine -> drawCaptureZone(
                    canvas, obj.x, obj.y, obj.radius, obj.progress, obj.kind.color,
                ) {
                    // Пьедестал с кристаллом.
                    fill.color = Color.rgb(69, 90, 100)
                    canvas.drawRect(obj.x - 26f, obj.y, obj.x + 26f, obj.y + 34f, fill)
                    fill.color = obj.kind.color
                    val path = android.graphics.Path()
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
                    val path = android.graphics.Path()
                    path.moveTo(p.x, p.y - r)
                    path.lineTo(p.x + r * 0.7f, p.y)
                    path.lineTo(p.x, p.y + r)
                    path.lineTo(p.x - r * 0.7f, p.y)
                    path.close()
                    canvas.drawPath(path, fill)
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
    // Сущности.
    // ------------------------------------------------------------------
    private fun drawAura(canvas: Canvas) {
        val w = game.player.weapons.firstOrNull { it.type == WeaponType.AURA } ?: return
        val r = WeaponBalance.auraRadius(w.level) * game.player.mult(Stat.AREA)
        fill.color = 0x26FF7043
        canvas.drawCircle(game.player.x, game.player.y, r, fill)
        stroke.color = 0x66FF7043
        stroke.strokeWidth = 3f
        canvas.drawCircle(game.player.x, game.player.y, r, stroke)
    }

    private fun drawEnemies(canvas: Canvas) {
        for (e in game.enemies) {
            fill.color = if (e.hitFlash > 0f) Color.WHITE else e.type.color
            canvas.drawCircle(e.x, e.y, e.type.radius, fill)
            // Глаза, чтобы читалось направление на игрока.
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

    private fun drawPlayer(canvas: Canvas) {
        val p = game.player
        // Мигание при неуязвимости.
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

    private fun drawProjectiles(canvas: Canvas) {
        for (p in game.projectiles) {
            fill.color = p.color
            canvas.drawCircle(p.x, p.y, p.radius, fill)
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
        // Полоса опыта на всю ширину сверху.
        fill.color = Color.rgb(30, 30, 40)
        canvas.drawRect(0f, 0f, g.screenW, 14f, fill)
        fill.color = Color.rgb(38, 198, 218)
        canvas.drawRect(0f, 0f, g.screenW * (g.player.xp / g.player.xpToNext), 14f, fill)

        // Здоровье.
        val hpW = 320f
        fill.color = Color.rgb(30, 30, 30)
        canvas.drawRoundRect(RectF(24f, 30f, 24f + hpW, 62f), 8f, 8f, fill)
        fill.color = Color.rgb(229, 57, 53)
        val hpFrac = (g.player.hp / g.player.maxHp).coerceIn(0f, 1f)
        canvas.drawRoundRect(RectF(24f, 30f, 24f + hpW * hpFrac, 62f), 8f, 8f, fill)
        text.textAlign = Paint.Align.CENTER
        text.color = Color.WHITE
        text.textSize = 24f
        canvas.drawText(
            "${g.player.hp.toInt()} / ${g.player.maxHp.toInt()}",
            24f + hpW / 2f, 54f, text,
        )
        // Уровень.
        text.textAlign = Paint.Align.LEFT
        canvas.drawText("Ур. ${g.player.level}", 24f + hpW + 18f, 54f, text)

        // Таймер и убийства по центру.
        text.textAlign = Paint.Align.CENTER
        text.textSize = 44f
        val m = (g.time / 60).toInt()
        val s = (g.time % 60).toInt()
        canvas.drawText(String.format("%02d:%02d", m, s), g.screenW / 2f, 70f, text)
        text.textSize = 24f
        text.color = Color.rgb(255, 171, 145)
        canvas.drawText("Убито: ${g.kills}", g.screenW / 2f, 100f, text)

        // Кнопка паузы.
        fill.color = 0x55000000
        canvas.drawRoundRect(game.pauseRect, 12f, 12f, fill)
        fill.color = Color.WHITE
        val pr = game.pauseRect
        canvas.drawRect(pr.centerX() - 14f, pr.centerY() - 16f, pr.centerX() - 4f, pr.centerY() + 16f, fill)
        canvas.drawRect(pr.centerX() + 4f, pr.centerY() - 16f, pr.centerX() + 14f, pr.centerY() + 16f, fill)

        drawBuffs(canvas)
        drawEquipment(canvas)
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

    private fun drawEquipment(canvas: Canvas) {
        // Слоты экипировки в правом нижнем углу.
        val size = 74f
        val gap = 12f
        val slots = Slot.entries
        val totalW = slots.size * size + (slots.size - 1) * gap
        var x = game.screenW - totalW - 24f
        val y = game.screenH - size - 20f
        text.textAlign = Paint.Align.CENTER
        for (slot in slots) {
            val item = game.player.equipment[slot]
            fill.color = if (item != null) {
                (item.rarity.color and 0x00FFFFFF) or 0x66000000
            } else {
                0x33000000
            }
            canvas.drawRoundRect(RectF(x, y, x + size, y + size), 10f, 10f, fill)
            stroke.strokeWidth = 3f
            stroke.color = item?.rarity?.color ?: 0x55FFFFFF
            canvas.drawRoundRect(RectF(x, y, x + size, y + size), 10f, 10f, stroke)
            text.color = Color.WHITE
            text.textSize = 20f
            canvas.drawText(slot.label.take(3), x + size / 2f, y + size / 2f + 7f, text)
            x += size + gap
        }
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
        val w = min(game.screenW * 0.5f, 640f)
        val h = game.screenH * 0.62f
        val r = RectF(
            (game.screenW - w) / 2f, (game.screenH - h) / 2f,
            (game.screenW + w) / 2f, (game.screenH + h) / 2f,
        )
        fill.color = Color.rgb(38, 45, 52)
        canvas.drawRoundRect(r, 20f, 20f, fill)
        stroke.color = item.rarity.color
        stroke.strokeWidth = 6f
        canvas.drawRoundRect(r, 20f, 20f, stroke)

        text.textAlign = Paint.Align.CENTER
        text.color = item.rarity.color
        text.textSize = 30f
        canvas.drawText(item.rarity.label.uppercase(), r.centerX(), r.top + 60f, text)
        text.color = Color.WHITE
        text.textSize = 38f
        drawWrapped(canvas, item.name, r.centerX(), r.top + 115f, r.width() - 60f)

        text.textSize = 28f
        var y = r.top + 200f
        for ((stat, v) in item.stats) {
            text.color = Color.rgb(129, 199, 132)
            canvas.drawText("+${(v * 100).toInt()}% ${stat.label}", r.centerX(), y, text)
            y += 44f
        }

        val old = game.popupOldItem
        if (old != null) {
            text.color = Color.rgb(144, 164, 174)
            text.textSize = 24f
            y += 20f
            canvas.drawText("Заменяет: ${old.name} (${old.rarity.label})", r.centerX(), y, text)
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
        canvas.drawText("ВЫ ПОГИБЛИ", game.screenW / 2f, game.screenH / 2f - 100f, text)
        text.color = Color.WHITE
        text.textSize = 32f
        val m = (game.time / 60).toInt()
        val s = (game.time % 60).toInt()
        canvas.drawText(
            String.format("Выжили %02d:%02d — убито %d — уровень %d", m, s, game.kills, game.player.level),
            game.screenW / 2f, game.screenH / 2f - 20f, text,
        )
        text.color = Color.rgb(255, 213, 79)
        text.textSize = 28f
        canvas.drawText("Коснитесь, чтобы начать заново", game.screenW / 2f, game.screenH / 2f + 60f, text)
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

    private fun min(a: Float, b: Float) = if (a < b) a else b
}
