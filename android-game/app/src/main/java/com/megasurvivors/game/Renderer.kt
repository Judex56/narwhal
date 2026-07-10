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
    private val path = Path()

    fun draw(canvas: Canvas, joystick: Joystick) {
        if (game.state == GameState.MENU) {
            drawMenu(canvas)
            return
        }

        val g = game
        var camX = g.camX - g.screenW / 2f
        var camY = g.camY - g.screenH / 2f
        if (g.shakeTime > 0f) {
            camX += (Random.Default.nextFloat() - 0.5f) * g.shakeMag * 2f
            camY += (Random.Default.nextFloat() - 0.5f) * g.shakeMag * 2f
        }

        canvas.drawColor(g.levelDef.bg)
        canvas.save()
        canvas.translate(-camX, -camY)

        drawGround(canvas, camX, camY)
        drawWorldBorder(canvas)
        drawWorldObjects(canvas)
        drawPickups(canvas)
        drawAuras(canvas)
        drawBooms(canvas)
        drawShadows(canvas)
        drawEnemies(canvas)
        drawPlayer(canvas)
        drawSpinEffect(canvas)
        drawOrbitBlades(canvas)
        drawSweepBlades(canvas)
        drawProjectiles(canvas)
        drawEnemyShots(canvas)
        drawBeams(canvas)
        drawLightning(canvas)
        drawParticles(canvas)
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
            GameState.VICTORY -> drawVictory(canvas)
            else -> Unit
        }
    }

    // ------------------------------------------------------------------
    // Мир.
    // ------------------------------------------------------------------
    private fun drawGround(canvas: Canvas, camX: Float, camY: Float) {
        val g = game
        val cell = 175f
        stroke.color = g.levelDef.grid
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

        fill.color = g.levelDef.decor
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
        fill.color = Color.rgb(10, 10, 12)
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
                    fill.color = 0x33000000
                    canvas.drawOval(RectF(obj.x - 36f, obj.y + 18f, obj.x + 36f, obj.y + 34f), fill)
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
                    path.reset()
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
                    path.reset()
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
    // Ауры оружия.
    // ------------------------------------------------------------------
    private fun drawAuras(canvas: Canvas) {
        for (w in game.player.weapons) {
            val areaMult = game.player.mult(Stat.AREA)
            val (r, color) = when (w.type) {
                WeaponType.AURA -> Pair(
                    WeaponBalance.auraRadius(w.level) * areaMult * (if (w.evolved) 1.4f else 1f),
                    w.displayColor,
                )
                WeaponType.FROST_AURA -> Pair(
                    WeaponBalance.frostRadius(w.level) * areaMult * (if (w.evolved) 1.5f else 1f),
                    w.displayColor,
                )
                else -> continue
            }
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

    // ------------------------------------------------------------------
    // Сущности: тени, формы, анимация.
    // ------------------------------------------------------------------
    private fun drawShadows(canvas: Canvas) {
        fill.color = 0x2E000000
        for (e in game.enemies) {
            val r = e.type.radius
            canvas.drawOval(RectF(e.x - r, e.y + r * 0.55f, e.x + r, e.y + r * 1.05f), fill)
        }
        val p = game.player
        canvas.drawOval(
            RectF(p.x - p.radius, p.y + p.radius * 0.55f, p.x + p.radius, p.y + p.radius * 1.05f),
            fill,
        )
    }

    private fun drawEnemies(canvas: Canvas) {
        for (e in game.enemies) {
            val pulse = 1f + 0.05f * sin(game.time * 6f + e.animPhase)
            val r = e.type.radius * pulse
            val color = when {
                e.hitFlash > 0f -> Color.WHITE
                e.freezeTimer > 0f -> Color.rgb(100, 181, 246)
                e.poisonStacks > 0 -> blend(e.type.color, Color.rgb(118, 255, 3), 0.35f)
                e.slowTimer > 0f -> blend(e.type.color, Color.rgb(128, 222, 234), 0.35f)
                else -> e.type.color
            }
            fill.color = color

            val toPlayer = kotlin.math.atan2(game.player.y - e.y, game.player.x - e.x)
            when (e.type) {
                EnemyType.WALKER -> canvas.drawCircle(e.x, e.y, r, fill)
                EnemyType.RUNNER -> {
                    // Треугольник, смотрящий на игрока.
                    path.reset()
                    for (k in 0..2) {
                        val a = toPlayer + k * (Math.PI.toFloat() * 2f / 3f)
                        val rr = if (k == 0) r * 1.3f else r
                        val px = e.x + cos(a) * rr
                        val py = e.y + sin(a) * rr
                        if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    path.close()
                    canvas.drawPath(path, fill)
                }
                EnemyType.BRUTE -> canvas.drawRoundRect(
                    RectF(e.x - r, e.y - r, e.x + r, e.y + r), r * 0.3f, r * 0.3f, fill,
                )
                EnemyType.TANK -> drawPolygon(canvas, e.x, e.y, r * 1.1f, 6, game.time * 0.4f)
                EnemyType.ELITE -> {
                    drawPolygon(canvas, e.x, e.y, r * 1.15f, 5, game.time * 1.2f)
                    stroke.color = Color.rgb(255, 241, 118)
                    stroke.strokeWidth = 4f
                    canvas.drawCircle(e.x, e.y, r * 1.25f, stroke)
                }
                EnemyType.BOSS -> {
                    // Пульсирующее кольцо + шипастое тело + корона.
                    stroke.color = (game.levelDef.bossColor and 0x00FFFFFF) or 0x55000000
                    stroke.strokeWidth = 10f
                    canvas.drawCircle(e.x, e.y, r * 1.3f + 8f * sin(game.time * 3f), stroke)
                    drawPolygon(canvas, e.x, e.y, r * 1.15f, 8, game.time * 0.8f)
                    fill.color = if (e.hitFlash > 0f) Color.WHITE else game.levelDef.bossColor
                    canvas.drawCircle(e.x, e.y, r * 0.85f, fill)
                    fill.color = Color.rgb(255, 213, 79)
                    path.reset()
                    path.moveTo(e.x - 34f, e.y - r * 0.9f)
                    path.lineTo(e.x - 22f, e.y - r * 0.9f - 30f)
                    path.lineTo(e.x - 10f, e.y - r * 0.9f)
                    path.lineTo(e.x, e.y - r * 0.9f - 34f)
                    path.lineTo(e.x + 10f, e.y - r * 0.9f)
                    path.lineTo(e.x + 22f, e.y - r * 0.9f - 30f)
                    path.lineTo(e.x + 34f, e.y - r * 0.9f)
                    path.close()
                    canvas.drawPath(path, fill)
                }
            }

            // Глаза.
            val ex = cos(toPlayer) * r * 0.4f
            val ey = sin(toPlayer) * r * 0.4f
            fill.color = Color.BLACK
            canvas.drawCircle(e.x + ex - r * 0.22f, e.y + ey, r * 0.14f, fill)
            canvas.drawCircle(e.x + ex + r * 0.22f, e.y + ey, r * 0.14f, fill)

            if (e.type != EnemyType.BOSS && (e.type == EnemyType.ELITE || e.hp < e.maxHp)) {
                val w = e.type.radius * 2f
                val top = e.y - e.type.radius - 14f
                fill.color = Color.rgb(40, 40, 40)
                canvas.drawRect(e.x - w / 2, top, e.x + w / 2, top + 6f, fill)
                fill.color = Color.rgb(239, 83, 80)
                canvas.drawRect(e.x - w / 2, top, e.x - w / 2 + w * (e.hp / e.maxHp), top + 6f, fill)
            }
        }
    }

    private fun drawPolygon(canvas: Canvas, cx: Float, cy: Float, r: Float, sides: Int, rot: Float) {
        path.reset()
        for (k in 0 until sides) {
            val a = rot + k * (Math.PI.toFloat() * 2f / sides)
            val px = cx + cos(a) * r
            val py = cy + sin(a) * r
            if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        canvas.drawPath(path, fill)
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

        // Ноги-кружки шагают, когда игрок движется.
        if (game.moveMag > 0.05f) {
            val step = sin(game.time * 14f) * 10f * game.moveMag
            fill.color = Color.rgb(144, 164, 174)
            canvas.drawCircle(p.x - 10f, p.y + p.radius * 0.8f + step * 0.4f, 7f, fill)
            canvas.drawCircle(p.x + 10f, p.y + p.radius * 0.8f - step * 0.4f, 7f, fill)
        }

        val bob = if (game.moveMag > 0.05f) sin(game.time * 14f) * 2.5f else sin(game.time * 3f) * 1.5f
        val py = p.y + bob
        fill.color = Color.rgb(236, 239, 241)
        canvas.drawCircle(p.x, py, p.radius, fill)
        fill.color = Color.rgb(38, 50, 56)
        canvas.drawCircle(p.x + 8f * p.facing, py - 6f, 4.5f, fill)
        canvas.drawCircle(p.x + 16f * p.facing, py - 6f, 4.5f, fill)
        stroke.color = Color.rgb(96, 125, 139)
        stroke.strokeWidth = 4f
        canvas.drawCircle(p.x, py, p.radius, stroke)
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
        val count = WeaponBalance.orbitCount(w.level) + (if (w.evolved) 3 else 0)
        val radius = WeaponBalance.orbitRadius(w.level) * game.player.mult(Stat.AREA) *
            (if (w.evolved) 1.3f else 1f)
        fill.color = w.displayColor
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

    private fun drawSweepBlades(canvas: Canvas) {
        for (w in game.player.weapons) {
            if (w.type != WeaponType.SCYTHE) continue
            val blades = if (w.evolved) 2 else 1
            val radius = WeaponBalance.scytheRadius(w.level) * game.player.mult(Stat.AREA)
            val color = w.displayColor
            for (b in 0 until blades) {
                val a = w.orbitAngle + b * (Math.PI.toFloat() * 2f / blades)
                val bx = game.player.x + cos(a) * radius
                val by = game.player.y + sin(a) * radius
                stroke.color = (color and 0x00FFFFFF) or 0x55000000
                stroke.strokeWidth = 6f
                canvas.drawLine(game.player.x, game.player.y, bx, by, stroke)
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

    private fun drawEnemyShots(canvas: Canvas) {
        for (p in game.enemyShots) {
            fill.color = p.color
            canvas.drawCircle(p.x, p.y, p.radius, fill)
            stroke.color = Color.WHITE
            stroke.strokeWidth = 2.5f
            canvas.drawCircle(p.x, p.y, p.radius, stroke)
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

    private fun drawParticles(canvas: Canvas) {
        for (p in game.particles) {
            val t = (p.life / p.maxLife).coerceIn(0f, 1f)
            fill.color = (p.color and 0x00FFFFFF) or ((255 * t).toInt() shl 24)
            canvas.drawCircle(p.x, p.y, p.size * t, fill)
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
        canvas.drawText(
            "Уровень ${g.gameLevel}: ${g.levelDef.name} • Убито: ${g.kills}",
            g.screenW / 2f, 100f, text,
        )

        // До босса / полоса здоровья босса.
        val boss = g.boss
        if (boss != null) {
            val bw = g.screenW * 0.44f
            val top = 116f
            fill.color = 0x88000000.toInt()
            canvas.drawRoundRect(
                RectF(g.screenW / 2f - bw / 2 - 6f, top - 6f, g.screenW / 2f + bw / 2 + 6f, top + 30f),
                8f, 8f, fill,
            )
            fill.color = Color.rgb(60, 20, 20)
            canvas.drawRoundRect(
                RectF(g.screenW / 2f - bw / 2, top, g.screenW / 2f + bw / 2, top + 24f), 6f, 6f, fill,
            )
            fill.color = g.levelDef.bossColor
            canvas.drawRoundRect(
                RectF(
                    g.screenW / 2f - bw / 2, top,
                    g.screenW / 2f - bw / 2 + bw * (boss.hp / boss.maxHp).coerceIn(0f, 1f), top + 24f,
                ),
                6f, 6f, fill,
            )
            text.color = Color.WHITE
            text.textSize = 22f
            canvas.drawText(g.levelDef.bossName, g.screenW / 2f, top + 19f, text)
        } else if (g.time < BOSS_TIME) {
            text.color = Color.rgb(144, 164, 174)
            text.textSize = 22f
            val left = (BOSS_TIME - g.time).toInt()
            canvas.drawText(
                "Босс через ${left / 60}:${String.format("%02d", left % 60)}",
                g.screenW / 2f, 128f, text,
            )
        }

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
                    canvas.drawRect(mx(obj.x) - 3f, my(obj.y) - 3f, mx(obj.x) + 3f, my(obj.y) + 3f, fill)
                }
                is Shrine -> {
                    fill.color = obj.kind.color
                    canvas.drawCircle(mx(obj.x), my(obj.y), 4f, fill)
                }
                is Statue -> {
                    fill.color = Color.rgb(178, 223, 219)
                    canvas.drawCircle(mx(obj.x), my(obj.y), 4f, fill)
                }
                is GoldPile -> {
                    fill.color = Color.rgb(255, 160, 0)
                    canvas.drawCircle(mx(obj.x), my(obj.y), 2f, fill)
                }
            }
        }
        for (e in game.enemies) {
            if (e.type == EnemyType.ELITE) {
                fill.color = Color.rgb(255, 82, 82)
                canvas.drawCircle(mx(e.x), my(e.y), 4f, fill)
            }
            if (e.type == EnemyType.BOSS) {
                fill.color = game.levelDef.bossColor
                canvas.drawCircle(mx(e.x), my(e.y), 7f, fill)
                stroke.color = Color.WHITE
                stroke.strokeWidth = 2f
                canvas.drawCircle(mx(e.x), my(e.y), 7f, stroke)
            }
        }
        fill.color = Color.WHITE
        canvas.drawCircle(mx(game.player.x), my(game.player.y), 5f, fill)
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

        text.textAlign = Paint.Align.RIGHT
        text.color = Color.rgb(255, 213, 79)
        text.textSize = 40f
        canvas.drawText("${g.meta.gold} з", g.screenW - 40f, 84f, text)

        text.textAlign = Paint.Align.LEFT
        text.textSize = 26f
        text.color = Color.WHITE
        canvas.drawText("ОРУЖИЕ", menu.weaponCells[0].left, 158f, text)
        val discovered = menu.allItems.count { g.meta.isDiscovered(it.id) }
        canvas.drawText("ПРЕДМЕТЫ ($discovered/${menu.allItems.size})", menu.itemArea.left, 158f, text)
        canvas.drawText(
            "ФОЛИАНТЫ (${g.meta.selectedTomes.size}/$MAX_TOMES)",
            menu.tomeCells[0].left, 158f, text,
        )

        // --- Оружие ---
        for (i in menu.weaponCells.indices) {
            val type = menu.weaponTypes[i]
            val r = menu.weaponCells[i]
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
            text.textSize = 28f
            text.color = if (unlocked) Color.WHITE else 0x77FFFFFF
            canvas.drawText(type.code, r.centerX(), r.centerY() + if (unlocked) 10f else -6f, text)
            if (!unlocked) {
                text.color = Color.rgb(255, 213, 79)
                text.textSize = 20f
                canvas.drawText("${type.price}з", r.centerX(), r.centerY() + 34f, text)
            }
            text.textSize = 16f
            text.color = if (unlocked) Color.rgb(207, 216, 220) else Color.rgb(120, 144, 156)
            canvas.drawText(type.label, r.centerX(), r.bottom + 19f, text)
        }

        // --- Предметы (скроллируемый каталог, тап = вкл/выкл в пуле) ---
        canvas.save()
        canvas.clipRect(
            menu.itemArea.left - 6f, menu.itemArea.top - 6f,
            menu.itemArea.right + 6f, menu.itemArea.bottom + 6f,
        )
        for (i in menu.allItems.indices) {
            val item = menu.allItems[i]
            val r = menu.itemCellRect(i)
            if (r.bottom < menu.itemArea.top - 20f || r.top > menu.itemArea.bottom + 20f) continue
            val found = g.meta.isDiscovered(item.id)
            val enabled = g.meta.isEnabled(item.id)

            fill.color = when {
                found && enabled -> (item.tier.color and 0x00FFFFFF) or 0x55000000
                found -> 0x33000000
                else -> 0x22000000
            }
            canvas.drawRoundRect(r, 10f, 10f, fill)
            stroke.strokeWidth = 3f
            stroke.color = when {
                found && enabled -> item.tier.color
                found -> (item.tier.color and 0x00FFFFFF) or 0x55000000
                else -> (item.tier.color and 0x00FFFFFF) or 0x44000000
            }
            canvas.drawRoundRect(r, 10f, 10f, stroke)

            text.textAlign = Paint.Align.CENTER
            text.textSize = 26f
            if (found) {
                text.color = if (enabled) Color.WHITE else 0x77FFFFFF
                canvas.drawText(item.name.take(2), r.centerX(), r.centerY() + 9f, text)
                if (!enabled) {
                    stroke.color = Color.rgb(239, 83, 80)
                    stroke.strokeWidth = 4f
                    canvas.drawLine(r.left + 14f, r.bottom - 14f, r.right - 14f, r.top + 14f, stroke)
                }
            } else {
                text.color = 0x66FFFFFF
                canvas.drawText("?", r.centerX(), r.centerY() + 9f, text)
            }
        }
        canvas.restore()
        val contentH = ((menu.allItems.size + menu.itemCols - 1) / menu.itemCols) * (menu.cell + menu.gap)
        if (contentH > menu.itemArea.height()) {
            val frac = menu.itemArea.height() / contentH
            val barH = menu.itemArea.height() * frac
            val barY = menu.itemArea.top +
                (menu.itemScroll / (contentH - menu.itemArea.height())) *
                (menu.itemArea.height() - barH)
            fill.color = 0x55FFFFFF
            canvas.drawRoundRect(
                RectF(menu.itemArea.right + 10f, barY, menu.itemArea.right + 16f, barY + barH),
                3f, 3f, fill,
            )
        }

        // --- Фолианты ---
        for (i in menu.tomeCells.indices) {
            val tome = menu.tomes[i]
            val r = menu.tomeCells[i]
            val selected = menu.isTomeSelected(tome)
            fill.color = (tome.color and 0x00FFFFFF) or 0x55000000
            canvas.drawRoundRect(r, 12f, 12f, fill)
            stroke.strokeWidth = if (selected) 6f else 3f
            stroke.color = if (selected) Color.WHITE else tome.color
            canvas.drawRoundRect(r, 12f, 12f, stroke)
            text.textAlign = Paint.Align.CENTER
            text.textSize = 26f
            text.color = Color.WHITE
            canvas.drawText(
                tome.label.removePrefix("Фолиант ").take(2).uppercase(),
                r.centerX(), r.centerY() + 9f, text,
            )
            text.textSize = 15f
            text.color = Color.rgb(207, 216, 220)
            canvas.drawText(tome.label.removePrefix("Фолиант "), r.centerX(), r.bottom + 19f, text)
        }

        // --- Уровни (этажи) ---
        text.textAlign = Paint.Align.CENTER
        text.textSize = 22f
        text.color = Color.WHITE
        canvas.drawText(
            "УРОВЕНЬ: ${LEVELS[(g.meta.selectedLevel - 1).coerceIn(0, LEVELS.size - 1)].name}",
            g.screenW / 2f, menu.levelCells[0].top - 12f, text,
        )
        for (i in menu.levelCells.indices) {
            val def = LEVELS[i]
            val r = menu.levelCells[i]
            val unlocked = def.index <= g.meta.unlockedLevel
            val selected = g.meta.selectedLevel == def.index
            fill.color = if (unlocked) (def.bossColor and 0x00FFFFFF) or 0x55000000 else 0x33000000
            canvas.drawRoundRect(r, 10f, 10f, fill)
            stroke.strokeWidth = if (selected) 5f else 3f
            stroke.color = when {
                selected -> Color.WHITE
                unlocked -> def.bossColor
                else -> 0x44FFFFFF
            }
            canvas.drawRoundRect(r, 10f, 10f, stroke)
            text.textSize = 26f
            text.color = if (unlocked) Color.WHITE else 0x66FFFFFF
            canvas.drawText(if (unlocked) "${def.index}" else "🔒", r.centerX(), r.centerY() + 9f, text)
        }

        // --- Инфо-панель ---
        fill.color = 0x44000000
        canvas.drawRoundRect(menu.infoArea, 12f, 12f, fill)
        text.textAlign = Paint.Align.LEFT
        text.textSize = 26f
        text.color = menu.infoColor
        canvas.drawText(menu.infoTitle, menu.infoArea.left + 20f, menu.infoArea.top + 38f, text)
        if (menu.infoDesc.isNotEmpty()) {
            text.textSize = 22f
            text.color = Color.rgb(207, 216, 220)
            var line1 = menu.infoDesc
            var line2 = ""
            val maxW = menu.infoArea.width() - 40f
            if (text.measureText(line1) > maxW) {
                val words = menu.infoDesc.split(" ")
                line1 = ""
                for (w in words) {
                    val cand = if (line1.isEmpty()) w else "$line1 $w"
                    if (text.measureText(cand) > maxW) {
                        line2 = menu.infoDesc.removePrefix(line1).trim()
                        break
                    }
                    line1 = cand
                }
            }
            canvas.drawText(line1, menu.infoArea.left + 20f, menu.infoArea.top + 70f, text)
            if (line2.isNotEmpty()) {
                canvas.drawText(line2, menu.infoArea.left + 20f, menu.infoArea.top + 96f, text)
            }
        }

        fill.color = Color.rgb(46, 125, 50)
        canvas.drawRoundRect(menu.startRect, 18f, 18f, fill)
        stroke.color = Color.rgb(129, 199, 132)
        stroke.strokeWidth = 4f
        canvas.drawRoundRect(menu.startRect, 18f, 18f, stroke)
        text.textAlign = Paint.Align.CENTER
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
            canvas.drawCircle(r.centerX(), r.top + 70f, 32f, fill)

            text.color = Color.WHITE
            text.textSize = 30f
            drawWrapped(canvas, opt.title, r.centerX(), r.top + 140f, r.width() - 36f)

            text.color = Color.rgb(207, 216, 220)
            text.textSize = 24f
            var y = r.top + 225f
            for (part in opt.desc.split(" • ")) {
                drawWrapped(canvas, part, r.centerX(), y, r.width() - 36f)
                y += if (text.measureText(part) > r.width() - 36f) 66f else 38f
            }
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
        if (item.specialText.isNotEmpty()) {
            text.color = Color.rgb(255, 213, 79)
            text.textSize = 25f
            y += 12f
            drawWrapped(canvas, item.specialText, r.centerX(), y, r.width() - 60f)
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
            String.format(
                "Уровень %d • Выжили %02d:%02d • убито %d",
                game.gameLevel, m, s, game.kills,
            ),
            game.screenW / 2f, game.screenH / 2f - 40f, text,
        )
        text.color = Color.rgb(255, 213, 79)
        canvas.drawText("Золото в копилку: +${game.gold}", game.screenW / 2f, game.screenH / 2f + 20f, text)
        text.textSize = 28f
        canvas.drawText("Коснитесь, чтобы вернуться в меню", game.screenW / 2f, game.screenH / 2f + 90f, text)
    }

    private fun drawVictory(canvas: Canvas) {
        dimScreen(canvas)
        text.textAlign = Paint.Align.CENTER
        text.color = Color.rgb(255, 213, 79)
        text.textSize = 72f
        canvas.drawText("УРОВЕНЬ ПРОЙДЕН!", game.screenW / 2f, game.screenH / 2f - 140f, text)
        text.color = Color.WHITE
        text.textSize = 34f
        canvas.drawText(
            "${game.levelDef.bossName} повержен!",
            game.screenW / 2f, game.screenH / 2f - 60f, text,
        )
        text.textSize = 30f
        text.color = Color.rgb(255, 213, 79)
        canvas.drawText(
            "Награда: +${game.victoryReward} золота (всего +${game.gold})",
            game.screenW / 2f, game.screenH / 2f, text,
        )
        if (game.gameLevel < LEVELS.size) {
            text.color = Color.rgb(129, 199, 132)
            canvas.drawText(
                "Открыт уровень ${game.gameLevel + 1}: ${LEVELS[game.gameLevel].name}",
                game.screenW / 2f, game.screenH / 2f + 55f, text,
            )
        } else {
            text.color = Color.rgb(129, 199, 132)
            canvas.drawText("Все уровни пройдены — вы легенда!", game.screenW / 2f, game.screenH / 2f + 55f, text)
        }
        text.textSize = 28f
        text.color = Color.WHITE
        canvas.drawText("Коснитесь, чтобы вернуться в меню", game.screenW / 2f, game.screenH / 2f + 130f, text)
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
