package com.megasurvivors.game

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView

class GameView(context: Context, private val version: String) :
    SurfaceView(context), SurfaceHolder.Callback, Runnable {

    private var thread: Thread? = null
    @Volatile private var running = false

    private val meta = MetaStore(context)
    private var game: Game? = null
    private var renderer: Renderer? = null
    private val joystick = Joystick()

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        running = true
        thread = Thread(this, "GameLoop").also { it.start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (game == null) {
            game = Game(width.toFloat(), height.toFloat(), meta, version).also {
                renderer = Renderer(it, context)
            }
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        thread?.join()
        thread = null
    }

    fun pauseGame() {
        val g = game ?: return
        if (g.state == GameState.RUNNING) g.state = GameState.PAUSED
    }

    override fun run() {
        var last = System.nanoTime()
        while (running) {
            val now = System.nanoTime()
            // Ограничиваем шаг, чтобы после лагов не было "телепортов".
            val dt = ((now - last) / 1_000_000_000.0).toFloat().coerceAtMost(1f / 30f)
            last = now

            val g = game
            val r = renderer
            if (g != null && r != null) {
                g.update(dt, joystick.dirX, joystick.dirY)
                val canvas = holder.lockCanvas()
                if (canvas != null) {
                    try {
                        r.draw(canvas, joystick)
                    } finally {
                        holder.unlockCanvasAndPost(canvas)
                    }
                }
            } else {
                Thread.sleep(16)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val g = game ?: return true
        val index = event.actionIndex
        val id = event.getPointerId(index)
        val x = event.getX(index)
        val y = event.getY(index)

        // Меню обрабатывается отдельно: там есть скролл списка предметов.
        if (g.state == GameState.MENU) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> g.menu.touchDown(x, y)
                MotionEvent.ACTION_MOVE -> g.menu.touchMove(event.x, event.y)
                MotionEvent.ACTION_UP -> {
                    if (g.menu.touchUp(event.x, event.y)) g.startRun()
                }
            }
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (g.state == GameState.RUNNING) {
                    // Тап по кнопке паузы — не задействуем джойстик.
                    if (g.pauseRect.contains(x, y)) {
                        g.handleTap(x, y)
                    } else {
                        joystick.down(id, x, y)
                    }
                } else {
                    g.handleTap(x, y)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    joystick.move(event.getPointerId(i), event.getX(i), event.getY(i))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                joystick.up(id)
            }
        }
        return true
    }
}
