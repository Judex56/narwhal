package com.megasurvivors.game

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.sqrt

/**
 * Плавающий виртуальный джойстик: появляется там, где игрок
 * коснулся экрана, исчезает при отпускании.
 */
class Joystick(private val maxRadius: Float = 130f) {

    var active = false
        private set
    private var pointerId = -1
    private var baseX = 0f
    private var baseY = 0f
    private var knobX = 0f
    private var knobY = 0f

    /** Нормализованный вектор движения (длина 0..1). */
    var dirX = 0f
        private set
    var dirY = 0f
        private set

    fun down(id: Int, x: Float, y: Float) {
        if (active) return
        active = true
        pointerId = id
        baseX = x
        baseY = y
        knobX = x
        knobY = y
        dirX = 0f
        dirY = 0f
    }

    fun move(id: Int, x: Float, y: Float) {
        if (!active || id != pointerId) return
        var dx = x - baseX
        var dy = y - baseY
        val len = sqrt(dx * dx + dy * dy)
        if (len > maxRadius) {
            dx = dx / len * maxRadius
            dy = dy / len * maxRadius
        }
        knobX = baseX + dx
        knobY = baseY + dy
        // dx/dy уже ограничены maxRadius, так что длина вектора <= 1.
        dirX = dx / maxRadius
        dirY = dy / maxRadius
    }

    fun up(id: Int) {
        if (id != pointerId) return
        active = false
        pointerId = -1
        dirX = 0f
        dirY = 0f
    }

    fun draw(canvas: Canvas, fill: Paint, stroke: Paint) {
        if (!active) return
        fill.color = 0x22FFFFFF
        canvas.drawCircle(baseX, baseY, maxRadius, fill)
        stroke.color = 0x55FFFFFF
        stroke.strokeWidth = 3f
        canvas.drawCircle(baseX, baseY, maxRadius, stroke)
        fill.color = 0x66FFFFFF
        canvas.drawCircle(knobX, knobY, 52f, fill)
    }
}
