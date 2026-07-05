package com.megasurvivors.game

import android.graphics.Color
import android.graphics.RectF
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

enum class GameState { RUNNING, LEVEL_UP, ITEM_POPUP, PAUSED, GAME_OVER }

class Game(val screenW: Float, val screenH: Float) {

    val rng = Random(System.currentTimeMillis())
    var state = GameState.RUNNING

    val player = Player()
    var world = WorldGen(rng.nextInt())

    val enemies = ArrayList<Enemy>()
    val projectiles = ArrayList<Projectile>()
    val pickups = ArrayList<Pickup>()
    val texts = ArrayList<FloatingText>()
    /** Вспышки молний для отрисовки: (x1,y1,x2,y2,время жизни). */
    val lightningBolts = ArrayList<FloatArray>()

    var time = 0f
    var kills = 0
    private var spawnTimer = 0f
    private var eliteTimer = 45f
    private var shrineWaveTimer = 0f
    private var regenTimer = 0f

    // UI-состояния.
    var upgradeOptions: List<UpgradeOption> = emptyList()
    var pendingLevelUps = 0
    var popupItem: Item? = null
    var popupOldItem: Item? = null

    // Прямоугольники интерактивных зон текущего оверлея.
    val optionRects = ArrayList<RectF>()
    val pauseRect = RectF(screenW - 110f, 20f, screenW - 20f, 110f)

    init {
        player.weapons.add(WeaponInstance(WeaponType.DART))
        player.hp = player.maxHp
    }

    val minute: Int get() = (time / 60f).toInt()

    // ------------------------------------------------------------------
    // Основной шаг симуляции.
    // ------------------------------------------------------------------
    fun update(dt: Float, moveX: Float, moveY: Float) {
        if (state != GameState.RUNNING) return

        time += dt
        updatePlayer(dt, moveX, moveY)
        updateWeapons(dt)
        updateEnemies(dt)
        updateProjectiles(dt)
        updatePickups(dt)
        updateWorldObjects(dt)
        updateBuffs(dt)
        updateTexts(dt)
        spawnEnemies(dt)

        if (player.hp <= 0f) {
            state = GameState.GAME_OVER
        }
    }

    private fun updatePlayer(dt: Float, moveX: Float, moveY: Float) {
        val sp = player.moveSpeed
        player.x += moveX * sp * dt
        player.y += moveY * sp * dt
        if (moveX > 0.1f) player.facing = 1f
        if (moveX < -0.1f) player.facing = -1f
        if (player.iFrames > 0f) player.iFrames -= dt

        // Лёгкий реген здоровья.
        regenTimer += dt
        if (regenTimer >= 1f) {
            regenTimer = 0f
            player.hp = min(player.maxHp, player.hp + 0.5f)
        }
    }

    // ------------------------------------------------------------------
    // Оружие.
    // ------------------------------------------------------------------
    private fun updateWeapons(dt: Float) {
        val dmgMult = player.mult(Stat.DAMAGE)
        val areaMult = player.mult(Stat.AREA)
        val cdFactor = player.cooldownFactor()

        for (w in player.weapons) {
            when (w.type) {
                WeaponType.DART -> {
                    w.timer -= dt
                    if (w.timer <= 0f) {
                        val target = nearestEnemy(player.x, player.y, 1400f)
                        if (target != null) {
                            w.timer = WeaponBalance.dartCooldown(w.level) * cdFactor
                            fireDarts(w, target, dmgMult)
                        }
                    }
                }
                WeaponType.ORBIT -> {
                    w.orbitAngle += dt * 2.6f
                    val count = WeaponBalance.orbitCount(w.level)
                    val radius = WeaponBalance.orbitRadius(w.level) * areaMult
                    val dmg = WeaponBalance.orbitDamage(w.level) * dmgMult
                    for (i in 0 until count) {
                        val a = w.orbitAngle + i * (Math.PI.toFloat() * 2f / count)
                        val bx = player.x + cos(a) * radius
                        val by = player.y + sin(a) * radius
                        for (e in enemies) {
                            if (e.orbitTick <= 0f &&
                                dist(bx, by, e.x, e.y) < 30f + e.type.radius
                            ) {
                                hitEnemy(e, dmg, knockFrom = player)
                                e.orbitTick = WeaponBalance.ORBIT_HIT_INTERVAL
                            }
                        }
                    }
                }
                WeaponType.AURA -> {
                    w.timer -= dt
                    if (w.timer <= 0f) {
                        w.timer = WeaponBalance.AURA_TICK * cdFactor
                        val radius = WeaponBalance.auraRadius(w.level) * areaMult
                        val dmg = WeaponBalance.auraDamage(w.level) * dmgMult
                        for (e in enemies) {
                            if (dist(player.x, player.y, e.x, e.y) < radius + e.type.radius) {
                                hitEnemy(e, dmg, knockFrom = null)
                            }
                        }
                    }
                }
                WeaponType.LIGHTNING -> {
                    w.timer -= dt
                    if (w.timer <= 0f && enemies.isNotEmpty()) {
                        w.timer = WeaponBalance.lightningCooldown(w.level) * cdFactor
                        val dmg = WeaponBalance.lightningDamage(w.level) * dmgMult
                        val targets = WeaponBalance.lightningTargets(w.level)
                        val onScreen = enemies.filter {
                            dist(player.x, player.y, it.x, it.y) < screenW * 0.6f
                        }
                        for (e in onScreen.shuffled(rng).take(targets)) {
                            hitEnemy(e, dmg, knockFrom = null)
                            lightningBolts.add(
                                floatArrayOf(e.x, e.y - 700f, e.x, e.y, 0.25f),
                            )
                        }
                    }
                }
            }
        }

        var i = lightningBolts.size - 1
        while (i >= 0) {
            lightningBolts[i][4] -= dt
            if (lightningBolts[i][4] <= 0f) lightningBolts.removeAt(i)
            i--
        }
    }

    private fun fireDarts(w: WeaponInstance, target: Enemy, dmgMult: Float) {
        val count = WeaponBalance.dartCount(w.level)
        val dmg = WeaponBalance.dartDamage(w.level) * dmgMult
        val speed = 750f * player.mult(Stat.PROJ_SPEED)
        val baseAngle = atan2(target.y - player.y, target.x - player.x)
        for (i in 0 until count) {
            val spread = (i - (count - 1) / 2f) * 0.14f
            val a = baseAngle + spread
            projectiles.add(
                Projectile(
                    player.x, player.y,
                    cos(a) * speed, sin(a) * speed,
                    dmg, 12f, pierce = 1 + w.level / 3,
                ),
            )
        }
    }

    private fun nearestEnemy(x: Float, y: Float, maxDist: Float): Enemy? {
        var best: Enemy? = null
        var bestD = maxDist
        for (e in enemies) {
            val d = dist(x, y, e.x, e.y)
            if (d < bestD) {
                bestD = d
                best = e
            }
        }
        return best
    }

    private fun hitEnemy(e: Enemy, dmg: Float, knockFrom: Player?) {
        e.hp -= dmg
        e.hitFlash = 0.12f
        texts.add(
            FloatingText(
                e.x + rng.nextFloat() * 20f - 10f, e.y - e.type.radius,
                dmg.toInt().toString(), Color.WHITE, 26f,
            ),
        )
        if (knockFrom != null) {
            val d = dist(knockFrom.x, knockFrom.y, e.x, e.y).coerceAtLeast(1f)
            e.knockX = (e.x - knockFrom.x) / d * 220f
            e.knockY = (e.y - knockFrom.y) / d * 220f
        }
    }

    // ------------------------------------------------------------------
    // Враги.
    // ------------------------------------------------------------------
    private fun updateEnemies(dt: Float) {
        // Пока идёт захват шрайна — врагов тянет туда.
        val activeShrine = world.objectsAround(player.x, player.y)
            .filterIsInstance<Shrine>()
            .firstOrNull { !it.consumed && it.progress > 0f }

        var i = enemies.size - 1
        while (i >= 0) {
            val e = enemies[i]
            if (e.hp <= 0f) {
                onEnemyDeath(e)
                enemies.removeAt(i)
                i--
                continue
            }

            var tx = player.x
            var ty = player.y
            if (activeShrine != null && e.type != EnemyType.ELITE && rngHash(e) % 3 == 0) {
                tx = activeShrine.x
                ty = activeShrine.y
            }
            val d = dist(e.x, e.y, tx, ty).coerceAtLeast(1f)
            e.x += (tx - e.x) / d * e.type.speed * dt + e.knockX * dt
            e.y += (ty - e.y) / d * e.type.speed * dt + e.knockY * dt
            e.knockX *= 0.85f
            e.knockY *= 0.85f
            if (e.hitFlash > 0f) e.hitFlash -= dt
            if (e.auraTick > 0f) e.auraTick -= dt
            if (e.orbitTick > 0f) e.orbitTick -= dt

            // Контактный урон игроку.
            if (player.iFrames <= 0f &&
                dist(e.x, e.y, player.x, player.y) < e.type.radius + player.radius
            ) {
                val dmg = player.reduceDamage(e.damage)
                player.hp -= dmg
                player.iFrames = 0.5f
                texts.add(
                    FloatingText(
                        player.x, player.y - 40f,
                        "-${dmg.toInt()}", Color.rgb(255, 82, 82), 30f,
                    ),
                )
            }
            i--
        }
    }

    /** Детерминированный "случайный" выбор для врага (чтобы не дёргался). */
    private fun rngHash(e: Enemy): Int = System.identityHashCode(e) and 0x7FFFFFFF

    private fun onEnemyDeath(e: Enemy) {
        kills++
        pickups.add(Pickup(PickupType.XP, e.x, e.y, e.type.xp))
        if (rng.nextFloat() < 0.03f) {
            pickups.add(Pickup(PickupType.HP, e.x + 20f, e.y, 20f))
        }
        // Элита всегда оставляет сундук со шмоткой.
        if (e.type == EnemyType.ELITE) {
            giveItem(ItemGenerator.roll(rng, minute))
        }
    }

    private fun spawnEnemies(dt: Float) {
        spawnTimer -= dt
        if (spawnTimer <= 0f) {
            spawnTimer = (1.1f - minute * 0.05f).coerceAtLeast(0.45f)
            val count = 2 + minute
            val hpScale = 1f + minute * 0.55f
            val dmgScale = 1f + minute * 0.25f
            repeat(count) {
                val type = rollEnemyType()
                val (x, y) = spawnPoint()
                enemies.add(Enemy(type, x, y, hpScale, dmgScale))
            }
        }

        eliteTimer -= dt
        if (eliteTimer <= 0f) {
            eliteTimer = 60f
            val (x, y) = spawnPoint()
            enemies.add(Enemy(EnemyType.ELITE, x, y, 1f + minute * 0.8f, 1f + minute * 0.2f))
            texts.add(
                FloatingText(player.x, player.y - 200f, "ЭЛИТА!", Color.rgb(255, 213, 79), 44f, 1.6f),
            )
        }

        // Волны при захвате шрайна/статуи.
        val capturing = world.objectsAround(player.x, player.y).any {
            !it.consumed && (
                (it is Shrine && it.progress > 0f) || (it is Statue && it.progress > 0f)
                )
        }
        if (capturing) {
            shrineWaveTimer -= dt
            if (shrineWaveTimer <= 0f) {
                shrineWaveTimer = 2f
                repeat(3 + minute / 2) {
                    val (x, y) = spawnPoint()
                    enemies.add(
                        Enemy(EnemyType.RUNNER, x, y, 1f + minute * 0.4f, 1f + minute * 0.2f),
                    )
                }
            }
        }
        if (enemies.size > 220) {
            // Страховка от переполнения: убираем самых дальних.
            enemies.sortBy { dist(player.x, player.y, it.x, it.y) }
            while (enemies.size > 200) enemies.removeAt(enemies.size - 1)
        }
    }

    private fun rollEnemyType(): EnemyType {
        val m = minute
        val r = rng.nextFloat()
        return when {
            m >= 3 && r < 0.15f -> EnemyType.TANK
            m >= 2 && r < 0.35f -> EnemyType.BRUTE
            m >= 1 && r < 0.60f -> EnemyType.RUNNER
            else -> EnemyType.WALKER
        }
    }

    private fun spawnPoint(): Pair<Float, Float> {
        val a = rng.nextFloat() * Math.PI.toFloat() * 2f
        val r = screenW * 0.65f + rng.nextFloat() * 200f
        return Pair(player.x + cos(a) * r, player.y + sin(a) * r)
    }

    // ------------------------------------------------------------------
    // Снаряды и подбираемое.
    // ------------------------------------------------------------------
    private fun updateProjectiles(dt: Float) {
        var i = projectiles.size - 1
        while (i >= 0) {
            val p = projectiles[i]
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.life -= dt
            var dead = p.life <= 0f
            if (!dead) {
                for (e in enemies) {
                    if (dist(p.x, p.y, e.x, e.y) < p.radius + e.type.radius) {
                        hitEnemy(e, p.damage, knockFrom = null)
                        p.pierce--
                        if (p.pierce <= 0) {
                            dead = true
                            break
                        }
                    }
                }
            }
            if (dead) projectiles.removeAt(i)
            i--
        }
    }

    private fun updatePickups(dt: Float) {
        val magnetR = 150f * player.mult(Stat.MAGNET)
        var i = pickups.size - 1
        while (i >= 0) {
            val p = pickups[i]
            val d = dist(p.x, p.y, player.x, player.y)
            if (p.magnetized || d < magnetR) {
                p.magnetized = true
                val speed = 650f
                p.x += (player.x - p.x) / d.coerceAtLeast(1f) * speed * dt
                p.y += (player.y - p.y) / d.coerceAtLeast(1f) * speed * dt
            }
            if (d < player.radius + 16f) {
                when (p.type) {
                    PickupType.XP -> gainXp(p.value)
                    PickupType.HP -> {
                        player.hp = min(player.maxHp, player.hp + p.value)
                        texts.add(
                            FloatingText(player.x, player.y - 50f, "+${p.value.toInt()}", Color.rgb(102, 187, 106)),
                        )
                    }
                }
                pickups.removeAt(i)
            }
            i--
        }
    }

    private fun gainXp(amount: Float) {
        player.xp += amount * player.mult(Stat.XP_GAIN)
        while (player.xp >= player.xpToNext) {
            player.xp -= player.xpToNext
            player.level++
            player.xpToNext = 10f + (player.level - 1) * 6f + (player.level - 1) * (player.level - 1) * 0.8f
            pendingLevelUps++
        }
        if (pendingLevelUps > 0 && state == GameState.RUNNING) {
            openLevelUp()
        }
    }

    private fun openLevelUp() {
        pendingLevelUps--
        upgradeOptions = UpgradePool.rollOptions(player, rng)
        if (upgradeOptions.isEmpty()) return
        layoutOptionCards(upgradeOptions.size)
        state = GameState.LEVEL_UP
    }

    private fun layoutOptionCards(count: Int) {
        optionRects.clear()
        val cardW = min(420f, screenW * 0.26f)
        val cardH = screenH * 0.5f
        val gap = 40f
        val totalW = count * cardW + (count - 1) * gap
        val startX = (screenW - totalW) / 2f
        val top = (screenH - cardH) / 2f
        for (i in 0 until count) {
            val left = startX + i * (cardW + gap)
            optionRects.add(RectF(left, top, left + cardW, top + cardH))
        }
    }

    // ------------------------------------------------------------------
    // Объекты мира: сундуки, шрайны, статуи.
    // ------------------------------------------------------------------
    private fun updateWorldObjects(dt: Float) {
        for (obj in world.objectsAround(player.x, player.y)) {
            if (obj.consumed) continue
            when (obj) {
                is Chest -> {
                    if (dist(player.x, player.y, obj.x, obj.y) < obj.radius + player.radius) {
                        obj.consumed = true
                        giveItem(ItemGenerator.roll(rng, minute))
                    }
                }
                is Shrine -> {
                    val inside = dist(player.x, player.y, obj.x, obj.y) < obj.radius
                    if (inside) {
                        obj.progress += dt / obj.captureTime
                        if (obj.progress >= 1f) {
                            obj.consumed = true
                            captureShrine(obj)
                        }
                    } else if (obj.progress > 0f) {
                        obj.progress = (obj.progress - dt / obj.captureTime * 0.5f).coerceAtLeast(0f)
                    }
                }
                is Statue -> {
                    val inside = dist(player.x, player.y, obj.x, obj.y) < obj.radius
                    if (inside) {
                        obj.progress += dt / obj.captureTime
                        if (obj.progress >= 1f) {
                            obj.consumed = true
                            captureStatue(obj)
                        }
                    } else if (obj.progress > 0f) {
                        obj.progress = (obj.progress - dt / obj.captureTime * 0.5f).coerceAtLeast(0f)
                    }
                }
            }
        }
    }

    private fun captureShrine(s: Shrine) {
        player.buffs.removeAll { it.name == s.kind.label }
        player.buffs.add(Buff(s.kind.label, s.kind.stats, s.kind.duration, s.kind.color))
        texts.add(
            FloatingText(s.x, s.y - 100f, "${s.kind.label}!", s.kind.color, 40f, 1.8f),
        )
    }

    private fun captureStatue(s: Statue) {
        val bonus = mapOf(
            Stat.DAMAGE to 0.05f,
            Stat.COOLDOWN to 0.05f,
            Stat.MOVE_SPEED to 0.03f,
            Stat.MAX_HP to 0.05f,
            Stat.AREA to 0.04f,
        )
        player.buffs.add(Buff("Статуя предков", bonus, Float.POSITIVE_INFINITY, Color.rgb(178, 223, 219)))
        player.hp = min(player.maxHp, player.hp + player.maxHp * 0.2f)
        texts.add(
            FloatingText(s.x, s.y - 100f, "+5% ко всем навыкам (навсегда)", Color.rgb(178, 223, 219), 34f, 2.2f),
        )
    }

    private fun giveItem(item: Item) {
        popupOldItem = player.equipment[item.slot]
        popupItem = item
        player.equipment[item.slot] = item
        state = GameState.ITEM_POPUP
    }

    // ------------------------------------------------------------------
    // Баффы и тексты.
    // ------------------------------------------------------------------
    private fun updateBuffs(dt: Float) {
        var i = player.buffs.size - 1
        while (i >= 0) {
            val b = player.buffs[i]
            if (!b.permanent) {
                b.remaining -= dt
                if (b.remaining <= 0f) player.buffs.removeAt(i)
            }
            i--
        }
    }

    private fun updateTexts(dt: Float) {
        var i = texts.size - 1
        while (i >= 0) {
            val t = texts[i]
            t.life -= dt
            t.y -= 45f * dt
            if (t.life <= 0f) texts.removeAt(i)
            i--
        }
    }

    // ------------------------------------------------------------------
    // Ввод по оверлеям.
    // ------------------------------------------------------------------
    fun handleTap(x: Float, y: Float) {
        when (state) {
            GameState.LEVEL_UP -> {
                for (i in optionRects.indices) {
                    if (optionRects[i].contains(x, y)) {
                        upgradeOptions[i].apply(player)
                        state = GameState.RUNNING
                        if (pendingLevelUps > 0) openLevelUp()
                        return
                    }
                }
            }
            GameState.ITEM_POPUP -> {
                popupItem = null
                popupOldItem = null
                state = GameState.RUNNING
                if (pendingLevelUps > 0) openLevelUp()
            }
            GameState.PAUSED -> state = GameState.RUNNING
            GameState.GAME_OVER -> restart()
            GameState.RUNNING -> {
                if (pauseRect.contains(x, y)) state = GameState.PAUSED
            }
        }
    }

    private fun restart() {
        enemies.clear()
        projectiles.clear()
        pickups.clear()
        texts.clear()
        lightningBolts.clear()
        world = WorldGen(rng.nextInt())
        with(player) {
            x = 0f
            y = 0f
            level = 1
            xp = 0f
            xpToNext = 10f
            weapons.clear()
            weapons.add(WeaponInstance(WeaponType.DART))
            equipment.clear()
            buffs.clear()
            passives.clear()
            iFrames = 0f
            hp = maxHp
        }
        time = 0f
        kills = 0
        spawnTimer = 0f
        eliteTimer = 45f
        pendingLevelUps = 0
        popupItem = null
        popupOldItem = null
        state = GameState.RUNNING
    }
}
