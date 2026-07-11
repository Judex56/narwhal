package com.megasurvivors.game

import android.graphics.Color
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

enum class GameState { MENU, RUNNING, LEVEL_UP, ITEM_POPUP, PAUSED, GAME_OVER, VICTORY }

class Game(
    val screenW: Float,
    val screenH: Float,
    val meta: MetaStore,
    val version: String,
) {

    val rng = Random(System.currentTimeMillis())
    var state = GameState.MENU
    val menu = Menu(screenW, screenH, meta)

    val player = Player()
    var world = WorldGen(rng.nextInt())

    var gameLevel = 1
    var levelDef: LevelDef = LEVELS[0]

    val enemies = ArrayList<Enemy>()
    val projectiles = ArrayList<Projectile>()
    /** Снаряды врагов (босс): бьют по игроку. */
    val enemyShots = ArrayList<Projectile>()
    val pickups = ArrayList<Pickup>()
    val texts = ArrayList<FloatingText>()
    val booms = ArrayList<Boom>()
    val particles = ArrayList<Particle>()
    /** Вспышки молний: (x1,y1,x2,y2,время жизни). */
    val lightningBolts = ArrayList<FloatArray>()
    /** Лучи пси-клинков: (x1,y1,x2,y2,время жизни). */
    val beams = ArrayList<FloatArray>()

    var time = 0f
    var kills = 0
    var gold = 0
    var chestsOpened = 0
    var spinEffect = 0f
    var nukeFlash = 0f
    var revivesUsed = 0
    var victoryReward = 0

    // Босс.
    var boss: Enemy? = null
    private var bossSpawned = false

    // Камера: плавно догоняет игрока; тряска при мощных событиях.
    var camX = 0f
    var camY = 0f
    var shakeTime = 0f
    var shakeMag = 0f
    /** Сила движения джойстика в текущем кадре (для анимации ног). */
    var moveMag = 0f

    private var spawnTimer = 0f
    private var eliteTimer = 45f
    private var miniBossTimer = 180f
    private var shrineWaveTimer = 0f
    private var regenTimer = 0f
    private var chestHintTimer = 0f

    // UI-состояния.
    var upgradeOptions: List<UpgradeOption> = emptyList()
    var pendingLevelUps = 0
    var popupItem: ItemDef? = null

    val optionRects = ArrayList<RectF>()
    val pauseRect = RectF(screenW - 110f, 20f, screenW - 20f, 110f)

    val minute: Int get() = (time / 60f).toInt()

    /** Цена сундука растёт с каждым открытым; Отмычка и Идол дают скидку. */
    val chestCost: Int
        get() {
            val base = 25f + 15f * chestsOpened
            val discount = (1f - 0.15f * player.countSpecial(Special.CHEST_DISCOUNT))
                .coerceAtLeast(0.4f)
            return (base * discount).toInt()
        }

    // Множители сложности: минуты забега × уровень (этаж).
    private val hpScale: Float get() = (1f + minute * 0.55f) * levelDef.hpMult
    private val dmgScaleNow: Float get() = (1f + minute * 0.25f) * levelDef.dmgMult
    private val speedScaleNow: Float get() = 1f + min(minute, 15) * 0.02f

    // ------------------------------------------------------------------
    // Запуск и завершение забега.
    // ------------------------------------------------------------------
    fun startRun() {
        gameLevel = meta.selectedLevel.coerceIn(1, LEVELS.size)
        levelDef = LEVELS[gameLevel - 1]

        enemies.clear()
        projectiles.clear()
        enemyShots.clear()
        pickups.clear()
        texts.clear()
        booms.clear()
        particles.clear()
        lightningBolts.clear()
        beams.clear()
        world = WorldGen(rng.nextInt())

        with(player) {
            x = 0f
            y = 0f
            level = 1
            xp = 0f
            xpToNext = 10f
            iFrames = 0f
            weapons.clear()
            items.clear()
            buffs.clear()
            tomes.clear()
            forged.clear()
        }
        // Весь выбранный в меню набор оружия — с 1 уровня каждое.
        for (type in menu.selectedWeapons) {
            player.weapons.add(WeaponInstance(type))
        }
        for (name in meta.selectedTomes) {
            val tome = Tome.entries.firstOrNull { it.name == name } ?: continue
            player.tomes[tome] = 1
        }
        player.hp = player.maxHp

        time = 0f
        kills = 0
        gold = 30
        chestsOpened = 0
        spawnTimer = 0f
        eliteTimer = 45f
        miniBossTimer = 180f
        pendingLevelUps = 0
        popupItem = null
        spinEffect = 0f
        nukeFlash = 0f
        revivesUsed = 0
        victoryReward = 0
        boss = null
        bossSpawned = false
        camX = 0f
        camY = 0f
        shakeTime = 0f
        state = GameState.RUNNING
    }

    private fun finishRun() {
        meta.gold += gold
        val secs = time.toInt()
        if (secs > meta.bestTime) meta.bestTime = secs
        meta.save()
        state = GameState.MENU
    }

    private fun victory() {
        victoryReward = 200 + 250 * gameLevel
        gold += victoryReward
        if (gameLevel >= meta.unlockedLevel && gameLevel < LEVELS.size) {
            meta.unlockedLevel = gameLevel + 1
        }
        meta.save()
        shake(20f, 0.7f)
        state = GameState.VICTORY
    }

    // ------------------------------------------------------------------
    // Основной шаг симуляции.
    // ------------------------------------------------------------------
    fun update(dt: Float, moveX: Float, moveY: Float) {
        if (state != GameState.RUNNING) return

        time += dt
        moveMag = dist(0f, 0f, moveX, moveY).coerceAtMost(1f)
        updatePlayer(dt, moveX, moveY)
        updateWeapons(dt)
        spawnBossIfTime()
        updateEnemies(dt)
        updateEnemyShots(dt)
        updateProjectiles(dt)
        updateBooms(dt)
        updatePickups(dt)
        updateWorldObjects(dt)
        updateBuffs(dt)
        updateTexts(dt)
        updateParticles(dt)
        spawnEnemies(dt)
        updateCamera(dt)

        if (spinEffect > 0f) spinEffect -= dt
        if (nukeFlash > 0f) nukeFlash -= dt
        if (chestHintTimer > 0f) chestHintTimer -= dt

        if (player.hp <= 0f) {
            if (player.countSpecial(Special.REVIVE) > revivesUsed) {
                revivesUsed++
                player.hp = player.maxHp * 0.5f
                player.iFrames = 2f
                for (e in enemies) {
                    val d = dist(player.x, player.y, e.x, e.y).coerceAtLeast(1f)
                    e.knockX = (e.x - player.x) / d * 900f
                    e.knockY = (e.y - player.y) / d * 900f
                }
                shake(14f, 0.5f)
                addText(player.x, player.y - 120f, "ВОСКРЕШЕНИЕ!", Color.rgb(255, 171, 64), 46f, 2f)
            } else {
                state = GameState.GAME_OVER
            }
        }
    }

    private fun updateCamera(dt: Float) {
        val k = (dt * 8f).coerceAtMost(1f)
        camX += (player.x - camX) * k
        camY += (player.y - camY) * k
        if (shakeTime > 0f) shakeTime -= dt
    }

    fun shake(mag: Float, duration: Float) {
        shakeMag = mag
        shakeTime = duration
    }

    private fun updatePlayer(dt: Float, moveX: Float, moveY: Float) {
        val sp = player.moveSpeed
        player.x = (player.x + moveX * sp * dt)
            .coerceIn(-WORLD_HALF + player.radius, WORLD_HALF - player.radius)
        player.y = (player.y + moveY * sp * dt)
            .coerceIn(-WORLD_HALF + player.radius, WORLD_HALF - player.radius)
        if (moveX > 0.1f) player.facing = 1f
        if (moveX < -0.1f) player.facing = -1f
        if (player.iFrames > 0f) player.iFrames -= dt

        regenTimer += dt
        if (regenTimer >= 1f) {
            regenTimer = 0f
            val regen = 0.5f + player.countSpecial(Special.REGEN) * 1f
            player.hp = min(player.maxHp, player.hp + regen)
        }
    }

    /** Весь входящий урон по игроку проходит здесь. */
    private fun damagePlayer(raw: Float, attacker: Enemy? = null) {
        if (player.iFrames > 0f) return
        if (player.hasSpecial(Special.DODGE) && rng.nextFloat() < 0.10f) {
            addText(player.x, player.y - 44f, "уклон!", Color.rgb(178, 235, 242), 26f)
            player.iFrames = 0.3f
            return
        }
        val dmg = player.reduceDamage(raw)
        player.hp -= dmg
        player.iFrames = 0.5f
        addText(player.x, player.y - 40f, "-${dmg.toInt()}", Color.rgb(255, 82, 82), 30f)

        val thorns = player.countSpecial(Special.THORNS)
        if (thorns > 0 && attacker != null) {
            hitEnemy(attacker, raw * 0.5f * thorns, knockFrom = player, canCrit = false)
        }

        // Ответный вихрь: контратака при получении урона.
        for (w in player.weapons) {
            if (w.type == WeaponType.COUNTER_SPIN && w.timer <= 0f) {
                triggerSpin(w)
            }
        }
    }

    private fun triggerSpin(w: WeaponInstance) {
        val evoCd = if (w.evolved) 0.6f else 1f
        w.timer = WeaponBalance.spinCooldown(w.level) * player.cooldownFactor() * evoCd
        spinEffect = 0.4f
        val radius = WeaponBalance.spinRadius(w.level) * player.mult(Stat.AREA)
        val dmg = WeaponBalance.spinDamage(w.level) * player.mult(Stat.DAMAGE) *
            (if (w.evolved) 2f else 1f)
        for (e in enemies) {
            if (dist(player.x, player.y, e.x, e.y) < radius + e.type.radius) {
                hitEnemy(e, dmg, knockFrom = player)
            }
        }
    }

    // ------------------------------------------------------------------
    // Оружие (evolved = эволюционная супер-форма с множителями).
    // ------------------------------------------------------------------
    private fun updateWeapons(dt: Float) {
        val dmgMult = player.mult(Stat.DAMAGE)
        val areaMult = player.mult(Stat.AREA)
        val cdFactor = player.cooldownFactor()

        for (w in player.weapons) {
            val evo = w.evolved
            when (w.type) {
                WeaponType.DART -> {
                    w.timer -= dt
                    if (w.timer <= 0f) {
                        val target = nearestEnemy(player.x, player.y, 1400f) ?: continue
                        w.timer = WeaponBalance.dartCooldown(w.level) * cdFactor * (if (evo) 0.75f else 1f)
                        fireSpread(
                            target,
                            WeaponBalance.dartCount(w.level) + (if (evo) 2 else 0),
                            WeaponBalance.dartDamage(w.level) * dmgMult * (if (evo) 2f else 1f),
                            750f * player.mult(Stat.PROJ_SPEED),
                            ProjKind.DART, w.displayColor,
                            pierce = 1 + w.level / 6 + (if (evo) 2 else 0), life = 1.6f,
                        )
                    }
                }
                WeaponType.ORBIT -> {
                    w.orbitAngle += dt * 2.6f
                    val count = WeaponBalance.orbitCount(w.level) + (if (evo) 3 else 0)
                    val radius = WeaponBalance.orbitRadius(w.level) * areaMult * (if (evo) 1.3f else 1f)
                    val dmg = WeaponBalance.orbitDamage(w.level) * dmgMult * (if (evo) 2f else 1f)
                    for (i in 0 until count) {
                        val a = w.orbitAngle + i * (Math.PI.toFloat() * 2f / count)
                        val bx = player.x + cos(a) * radius
                        val by = player.y + sin(a) * radius
                        for (e in enemies) {
                            if (e.orbitTick <= 0f && dist(bx, by, e.x, e.y) < 30f + e.type.radius) {
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
                        val radius = WeaponBalance.auraRadius(w.level) * areaMult * (if (evo) 1.4f else 1f)
                        val dmg = WeaponBalance.auraDamage(w.level) * dmgMult * (if (evo) 2.2f else 1f)
                        for (e in enemies) {
                            if (e.fireTick <= 0f &&
                                dist(player.x, player.y, e.x, e.y) < radius + e.type.radius
                            ) {
                                hitEnemy(e, dmg, knockFrom = null)
                                e.fireTick = WeaponBalance.AURA_TICK
                            }
                        }
                    }
                }
                WeaponType.LIGHTNING -> {
                    w.timer -= dt
                    if (w.timer <= 0f && enemies.isNotEmpty()) {
                        w.timer = WeaponBalance.lightningCooldown(w.level) * cdFactor *
                            (if (evo) 0.8f else 1f)
                        val dmg = WeaponBalance.lightningDamage(w.level) * dmgMult * (if (evo) 2f else 1f)
                        val targets = WeaponBalance.lightningTargets(w.level) + (if (evo) 3 else 0)
                        val onScreen = enemies.filter {
                            dist(player.x, player.y, it.x, it.y) < screenW * 0.6f
                        }
                        for (e in onScreen.shuffled(rng).take(targets)) {
                            hitEnemy(e, dmg, knockFrom = null)
                            lightningBolts.add(floatArrayOf(e.x, e.y - 700f, e.x, e.y, 0.25f))
                        }
                    }
                }
                WeaponType.SCYTHE -> {
                    sweepBlades(
                        w, dt,
                        blades = if (evo) 2 else 1,
                        speed = WeaponBalance.scytheSpeed(w.level) * (if (evo) 1.5f else 1f),
                        radius = WeaponBalance.scytheRadius(w.level) * areaMult,
                        dmg = WeaponBalance.scytheDamage(w.level) * dmgMult * (if (evo) 2f else 1f),
                        lifesteal = evo,
                    )
                }
                WeaponType.FROST_AURA -> {
                    w.timer -= dt
                    val radius = WeaponBalance.frostRadius(w.level) * areaMult * (if (evo) 1.5f else 1f)
                    if (w.timer <= 0f) {
                        w.timer = WeaponBalance.FROST_TICK * cdFactor
                        val dmg = WeaponBalance.frostDamage(w.level) * dmgMult * (if (evo) 2.5f else 1f)
                        val slow = if (evo) 0.3f else WeaponBalance.frostSlow(w.level)
                        for (e in enemies) {
                            if (dist(player.x, player.y, e.x, e.y) < radius + e.type.radius) {
                                if (e.frostTick <= 0f) {
                                    hitEnemy(e, dmg, knockFrom = null)
                                    e.frostTick = WeaponBalance.FROST_TICK
                                }
                                e.slowTimer = 1.5f
                                e.slowMult = slow
                            }
                        }
                    }
                    // Эволюция: периодическая заморозка всех в радиусе.
                    if (evo) {
                        w.extraTimer += dt
                        if (w.extraTimer >= WeaponBalance.FREEZE_EVERY) {
                            w.extraTimer = 0f
                            for (e in enemies) {
                                if (dist(player.x, player.y, e.x, e.y) < radius + e.type.radius) {
                                    e.freezeTimer = WeaponBalance.FREEZE_TIME
                                }
                            }
                            addText(player.x, player.y - 90f, "ЗАМОРОЗКА!", Color.rgb(0, 229, 255), 34f)
                        }
                    }
                }
                WeaponType.COUNTER_SPIN -> {
                    if (w.timer > 0f) w.timer -= dt
                    // Эволюция: автоспин раз в 5с даже без получения урона.
                    if (evo) {
                        w.extraTimer += dt
                        if (w.extraTimer >= WeaponBalance.AUTO_SPIN_EVERY) {
                            w.extraTimer = 0f
                            triggerSpin(w)
                        }
                    }
                }
                WeaponType.POISON -> {
                    w.timer -= dt
                    if (w.timer <= 0f) {
                        val target = nearestEnemy(player.x, player.y, 1200f) ?: continue
                        w.timer = WeaponBalance.poisonCooldown(w.level) * cdFactor
                        fireSpread(
                            target,
                            WeaponBalance.poisonCount(w.level) + (if (evo) 2 else 0),
                            WeaponBalance.poisonDamage(w.level) * dmgMult * (if (evo) 2f else 1f),
                            700f * player.mult(Stat.PROJ_SPEED),
                            ProjKind.POISON, w.displayColor,
                            pierce = 1, life = 1.5f,
                        )
                    }
                }
                WeaponType.NUKE -> {
                    w.timer -= dt
                    if (w.timer <= 0f && enemies.isNotEmpty()) {
                        w.timer = WeaponBalance.nukeCooldown(w.level) * cdFactor *
                            (if (evo) 0.6f else 1f)
                        nukeFlash = 0.5f
                        shake(16f, 0.4f)
                        val dmg = WeaponBalance.nukeDamage(w.level) * dmgMult * (if (evo) 2f else 1f)
                        for (e in enemies) {
                            if (dist(player.x, player.y, e.x, e.y) < screenW * 0.75f) {
                                hitEnemy(e, dmg, knockFrom = player)
                            }
                        }
                        addText(player.x, player.y - 120f, "СУДНЫЙ ЧАС!", Color.rgb(255, 82, 82), 46f, 1.4f)
                    }
                }
                WeaponType.BANANA -> {
                    w.timer -= dt
                    if (w.timer <= 0f) {
                        val target = nearestEnemy(player.x, player.y, 1200f) ?: continue
                        w.timer = WeaponBalance.bananaCooldown(w.level) * cdFactor
                        val base = atan2(target.y - player.y, target.x - player.x)
                        val count = WeaponBalance.bananaCount(w.level) + (if (evo) 2 else 0)
                        val dmg = WeaponBalance.bananaDamage(w.level) * dmgMult * (if (evo) 2f else 1f)
                        for (i in 0 until count) {
                            val a = base + (i - (count - 1) / 2f) * 0.35f
                            val speed = 700f * player.mult(Stat.PROJ_SPEED)
                            projectiles.add(
                                Projectile(
                                    player.x, player.y, cos(a) * speed, sin(a) * speed,
                                    dmg, 18f, pierce = Int.MAX_VALUE, life = 5f,
                                    kind = ProjKind.BANANA, color = w.displayColor,
                                ),
                            )
                        }
                    }
                }
                WeaponType.PSI_BLADES -> {
                    w.timer -= dt
                    if (w.timer <= 0f) {
                        val target = nearestEnemy(player.x, player.y, 900f) ?: continue
                        w.timer = WeaponBalance.psiCooldown(w.level) * cdFactor
                        firePsiBeam(w, target, dmgMult, areaMult, evo)
                    }
                }
                WeaponType.METEOR -> {
                    w.timer -= dt
                    if (w.timer <= 0f) {
                        w.timer = WeaponBalance.meteorCooldown(w.level) * cdFactor
                        repeat(WeaponBalance.meteorCount(w.level) + (if (evo) 3 else 0)) {
                            val a = rng.nextFloat() * Math.PI.toFloat() * 2f
                            val r = 120f + rng.nextFloat() * 380f
                            booms.add(
                                Boom(
                                    (player.x + cos(a) * r).coerceIn(-WORLD_HALF, WORLD_HALF),
                                    (player.y + sin(a) * r).coerceIn(-WORLD_HALF, WORLD_HALF),
                                    delay = 0.7f,
                                    radius = WeaponBalance.meteorRadius(w.level) * areaMult *
                                        (if (evo) 1.3f else 1f),
                                    damage = WeaponBalance.meteorDamage(w.level) * dmgMult *
                                        (if (evo) 1.8f else 1f),
                                ),
                            )
                        }
                    }
                }
                WeaponType.SHOTGUN -> {
                    w.timer -= dt
                    if (w.timer <= 0f) {
                        val target = nearestEnemy(player.x, player.y, 700f) ?: continue
                        w.timer = WeaponBalance.shotgunCooldown(w.level) * cdFactor
                        val base = atan2(target.y - player.y, target.x - player.x)
                        repeat(WeaponBalance.shotgunPellets(w.level) + (if (evo) 6 else 0)) {
                            val a = base + (rng.nextFloat() - 0.5f) * 0.9f
                            val speed = (800f + rng.nextFloat() * 150f) * player.mult(Stat.PROJ_SPEED)
                            projectiles.add(
                                Projectile(
                                    player.x, player.y, cos(a) * speed, sin(a) * speed,
                                    WeaponBalance.shotgunDamage(w.level) * dmgMult *
                                        (if (evo) 1.6f else 1f),
                                    8f, pierce = 1, life = WeaponBalance.SHOTGUN_RANGE,
                                    kind = ProjKind.PELLET, color = w.displayColor,
                                ),
                            )
                        }
                    }
                }
                WeaponType.GREED_BLADE -> {
                    w.timer -= dt
                    if (w.timer <= 0f) {
                        val target = nearestEnemy(player.x, player.y, 1000f) ?: continue
                        w.timer = WeaponBalance.greedCooldown(w.level) * cdFactor *
                            (if (evo) 0.8f else 1f)
                        val stackValue = if (evo) 1.0f else 0.4f
                        fireSpread(
                            target, 1,
                            WeaponBalance.greedDamage(w.level, w.stacks, stackValue) * dmgMult *
                                (if (evo) 1.5f else 1f),
                            820f * player.mult(Stat.PROJ_SPEED),
                            ProjKind.SLASH, w.displayColor,
                            pierce = 3, life = 1.2f,
                        )
                    }
                }
            }
        }

        decayEffects(lightningBolts, dt)
        decayEffects(beams, dt)
    }

    private fun decayEffects(list: ArrayList<FloatArray>, dt: Float) {
        var i = list.size - 1
        while (i >= 0) {
            list[i][4] -= dt
            if (list[i][4] <= 0f) list.removeAt(i)
            i--
        }
    }

    private fun fireSpread(
        target: Enemy,
        count: Int,
        dmg: Float,
        speed: Float,
        kind: ProjKind,
        color: Int,
        pierce: Int,
        life: Float,
    ) {
        val baseAngle = atan2(target.y - player.y, target.x - player.x)
        for (i in 0 until count) {
            val a = baseAngle + (i - (count - 1) / 2f) * 0.14f
            projectiles.add(
                Projectile(
                    player.x, player.y, cos(a) * speed, sin(a) * speed,
                    dmg, 12f, pierce, life, kind, color,
                ),
            )
        }
    }

    private fun sweepBlades(
        w: WeaponInstance,
        dt: Float,
        blades: Int,
        speed: Float,
        radius: Float,
        dmg: Float,
        lifesteal: Boolean,
    ) {
        w.orbitAngle += dt * speed
        for (e in enemies) {
            if (e.scytheTick > 0f) continue
            val d = dist(player.x, player.y, e.x, e.y)
            if (d > radius * 1.15f + e.type.radius) continue
            val enemyAngle = atan2(e.y - player.y, e.x - player.x)
            for (b in 0 until blades) {
                val bladeAngle = w.orbitAngle + b * (Math.PI.toFloat() * 2f / blades)
                var diff = abs(enemyAngle - bladeAngle) % (Math.PI.toFloat() * 2f)
                if (diff > Math.PI.toFloat()) diff = Math.PI.toFloat() * 2f - diff
                if (diff < 0.6f) {
                    hitEnemy(e, dmg, knockFrom = player)
                    e.scytheTick = WeaponBalance.SCYTHE_HIT_INTERVAL
                    if (lifesteal) player.hp = min(player.maxHp, player.hp + 1f)
                    break
                }
            }
        }
    }

    private fun firePsiBeam(
        w: WeaponInstance,
        target: Enemy,
        dmgMult: Float,
        areaMult: Float,
        evo: Boolean,
    ) {
        val len = WeaponBalance.psiLength(w.level) * (if (evo) 1.3f else 1f)
        val width = WeaponBalance.psiWidth(w.level) * areaMult * (if (evo) 1.5f else 1f)
        val dmg = WeaponBalance.psiDamage(w.level) * dmgMult * (if (evo) 2f else 1f)
        val a = atan2(target.y - player.y, target.x - player.x)
        val dx = cos(a)
        val dy = sin(a)
        for (e in enemies) {
            val relX = e.x - player.x
            val relY = e.y - player.y
            val proj = relX * dx + relY * dy
            if (proj < 0f || proj > len) continue
            val perp = abs(relX * dy - relY * dx)
            if (perp < width + e.type.radius) {
                hitEnemy(e, dmg, knockFrom = null)
            }
        }
        beams.add(floatArrayOf(player.x, player.y, player.x + dx * len, player.y + dy * len, 0.15f))
    }

    private fun applyPoison(e: Enemy, dpsPerStack: Float) {
        e.poisonStacks = min(e.poisonStacks + 1, WeaponBalance.MAX_POISON_STACKS)
        e.poisonDps = dpsPerStack
        e.poisonTimer = WeaponBalance.POISON_DURATION
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

    private fun hitEnemy(e: Enemy, baseDmg: Float, knockFrom: Player?, canCrit: Boolean = true) {
        var dmg = baseDmg
        var crit = false
        if (canCrit) {
            val chance = 0.05f + player.bonus(Stat.CRIT_CHANCE)
            if (rng.nextFloat() < chance) {
                crit = true
                dmg *= 2f + player.bonus(Stat.CRIT_DMG)
            }
        }
        e.hp -= dmg
        e.hitFlash = 0.12f
        spawnParticles(e.x, e.y, if (crit) Color.rgb(255, 213, 79) else Color.WHITE, 2, 160f, 3f)
        if (crit) {
            addText(e.x, e.y - e.type.radius - 8f, "${dmg.toInt()}!", Color.rgb(255, 213, 79), 36f)
        } else {
            addText(e.x + rng.nextFloat() * 20f - 10f, e.y - e.type.radius, dmg.toInt().toString(), Color.WHITE, 26f)
        }
        // Босс не отлетает от ударов.
        if (knockFrom != null && e.type != EnemyType.BOSS) {
            val d = dist(knockFrom.x, knockFrom.y, e.x, e.y).coerceAtLeast(1f)
            e.knockX = (e.x - knockFrom.x) / d * 220f
            e.knockY = (e.y - knockFrom.y) / d * 220f
        }
    }

    // ------------------------------------------------------------------
    // Босс.
    // ------------------------------------------------------------------
    private fun spawnBossIfTime() {
        if (bossSpawned || time < BOSS_TIME) return
        bossSpawned = true
        val (x, y) = spawnPoint()
        val b = Enemy(
            EnemyType.BOSS, x, y,
            hpScale = levelDef.hpMult * levelDef.bossHpMult,
            dmgScale = levelDef.dmgMult,
            speedScale = 1f,
        )
        enemies.add(b)
        boss = b
        shake(18f, 0.8f)
        addText(
            player.x, player.y - 220f,
            "БОСС: ${levelDef.bossName}!", levelDef.bossColor, 52f, 3f,
        )
    }

    private fun updateBoss(b: Enemy, dt: Float) {
        // Рывок к игроку.
        b.bossChargeTimer -= dt
        if (b.bossChargeTimer <= 0f) {
            b.bossChargeTimer = 6.5f
            val d = dist(b.x, b.y, player.x, player.y).coerceAtLeast(1f)
            b.knockX = (player.x - b.x) / d * 1300f
            b.knockY = (player.y - b.y) / d * 1300f
            shake(8f, 0.3f)
            addText(b.x, b.y - b.type.radius - 30f, "РЫВОК!", Color.rgb(255, 138, 101), 32f)
        }
        // Веер снарядов по кругу.
        b.bossShootTimer -= dt
        if (b.bossShootTimer <= 0f) {
            b.bossShootTimer = 9f
            val shots = 12
            for (i in 0 until shots) {
                val a = i * (Math.PI.toFloat() * 2f / shots) + rng.nextFloat() * 0.3f
                enemyShots.add(
                    Projectile(
                        b.x, b.y, cos(a) * 340f, sin(a) * 340f,
                        damage = b.damage * 0.5f, radius = 15f,
                        pierce = 1, life = 4f,
                        kind = ProjKind.DART, color = Color.rgb(255, 82, 82),
                    ),
                )
            }
        }
    }

    private fun updateEnemyShots(dt: Float) {
        var i = enemyShots.size - 1
        while (i >= 0) {
            val p = enemyShots[i]
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.life -= dt
            var dead = p.life <= 0f
            if (!dead && dist(p.x, p.y, player.x, player.y) < p.radius + player.radius) {
                damagePlayer(p.damage)
                dead = true
            }
            if (dead) enemyShots.removeAt(i)
            i--
        }
    }

    // ------------------------------------------------------------------
    // Враги.
    // ------------------------------------------------------------------
    private fun updateEnemies(dt: Float) {
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

            if (e.type == EnemyType.BOSS) updateBoss(e, dt)

            // Яд: тикающий урон раз в полсекунды.
            if (e.poisonStacks > 0 && e.poisonTimer > 0f) {
                e.poisonTimer -= dt
                e.poisonAcc += dt
                if (e.poisonAcc >= 0.5f) {
                    e.poisonAcc -= 0.5f
                    val dmg = e.poisonDps * e.poisonStacks * 0.5f
                    e.hp -= dmg
                    e.hitFlash = 0.06f
                    addText(e.x, e.y - e.type.radius, dmg.toInt().toString(), Color.rgb(156, 204, 101), 22f)
                }
                if (e.poisonTimer <= 0f) e.poisonStacks = 0
            }

            var tx = player.x
            var ty = player.y
            if (activeShrine != null && e.type != EnemyType.ELITE &&
                e.type != EnemyType.BOSS && e.type != EnemyType.MINIBOSS &&
                rngHash(e) % 3 == 0
            ) {
                tx = activeShrine.x
                ty = activeShrine.y
            }
            val d = dist(e.x, e.y, tx, ty).coerceAtLeast(1f)
            val sp = e.effectiveSpeed
            e.x = (e.x + (tx - e.x) / d * sp * dt + e.knockX * dt).coerceIn(-WORLD_HALF, WORLD_HALF)
            e.y = (e.y + (ty - e.y) / d * sp * dt + e.knockY * dt).coerceIn(-WORLD_HALF, WORLD_HALF)
            e.knockX *= 0.85f
            e.knockY *= 0.85f

            if (e.hitFlash > 0f) e.hitFlash -= dt
            if (e.fireTick > 0f) e.fireTick -= dt
            if (e.frostTick > 0f) e.frostTick -= dt
            if (e.orbitTick > 0f) e.orbitTick -= dt
            if (e.scytheTick > 0f) e.scytheTick -= dt
            if (e.bananaTick > 0f) e.bananaTick -= dt
            if (e.plagueTick > 0f) e.plagueTick -= dt
            if (e.slowTimer > 0f) e.slowTimer -= dt
            if (e.freezeTimer > 0f) {
                // Босс замораживается на треть длительности.
                e.freezeTimer -= if (e.type == EnemyType.BOSS) dt * 3f else dt
            }

            if (dist(e.x, e.y, player.x, player.y) < e.type.radius + player.radius) {
                damagePlayer(e.damage, attacker = e)
            }
            i--
        }
    }

    private fun rngHash(e: Enemy): Int = System.identityHashCode(e) and 0x7FFFFFFF

    private fun onEnemyDeath(e: Enemy) {
        kills++
        spawnParticles(e.x, e.y, e.type.color, 6, 220f, 5f)
        pickups.add(Pickup(PickupType.XP, e.x, e.y, e.type.xp))
        pickups.add(Pickup(PickupType.GOLD, e.x - 14f, e.y + 10f, e.type.gold.toFloat()))
        if (rng.nextFloat() < 0.03f) {
            pickups.add(Pickup(PickupType.HP, e.x + 20f, e.y, 20f))
        }
        val steal = player.countSpecial(Special.LIFESTEAL)
        if (steal > 0) player.hp = min(player.maxHp, player.hp + steal)
        for (w in player.weapons) {
            if (w.type == WeaponType.GREED_BLADE) w.stacks++
        }
        if (e.type == EnemyType.ELITE) {
            giveItem(ItemPool.roll(rng, minute, player.items, meta.disabledItems))
        }
        if (e.type == EnemyType.MINIBOSS) {
            // Жирная награда: предмет + россыпь золота + лечение.
            spawnParticles(e.x, e.y, EnemyType.MINIBOSS.color, 24, 380f, 7f)
            repeat(6) {
                pickups.add(
                    Pickup(
                        PickupType.GOLD,
                        e.x + rng.nextFloat() * 120f - 60f,
                        e.y + rng.nextFloat() * 120f - 60f,
                        (25 + 5 * gameLevel).toFloat(),
                    ),
                )
            }
            pickups.add(Pickup(PickupType.HP, e.x, e.y - 30f, 40f))
            giveItem(ItemPool.roll(rng, minute, player.items, meta.disabledItems))
        }
        if (e.type == EnemyType.BOSS) {
            boss = null
            spawnParticles(e.x, e.y, levelDef.bossColor, 40, 420f, 8f)
            victory()
        }
    }

    private fun spawnEnemies(dt: Float) {
        spawnTimer -= dt
        if (spawnTimer <= 0f) {
            // После выхода босса волны редеют вдвое.
            val bossFactor = if (bossSpawned) 2f else 1f
            spawnTimer = ((1.1f - minute * 0.05f).coerceAtLeast(0.35f)) * bossFactor
            val count = 2 + minute + levelDef.spawnBonus
            repeat(count) {
                val type = rollEnemyType()
                val (x, y) = spawnPoint()
                enemies.add(Enemy(type, x, y, hpScale, dmgScaleNow, speedScaleNow))
            }
        }

        eliteTimer -= dt
        if (eliteTimer <= 0f) {
            eliteTimer = 60f
            val (x, y) = spawnPoint()
            enemies.add(
                Enemy(
                    EnemyType.ELITE, x, y,
                    (1f + minute * 0.8f) * levelDef.hpMult,
                    (1f + minute * 0.2f) * levelDef.dmgMult,
                    speedScaleNow,
                ),
            )
            addText(player.x, player.y - 200f, "ЭЛИТА!", Color.rgb(255, 213, 79), 44f, 1.6f)
        }

        // Мини-босс каждые 3 минуты — жирная награда за убийство.
        miniBossTimer -= dt
        if (miniBossTimer <= 0f) {
            miniBossTimer = 180f
            val (x, y) = spawnPoint()
            enemies.add(
                Enemy(
                    EnemyType.MINIBOSS, x, y,
                    (1f + minute * 0.6f) * levelDef.hpMult,
                    (1f + minute * 0.2f) * levelDef.dmgMult,
                    speedScaleNow,
                ),
            )
            shake(10f, 0.4f)
            addText(player.x, player.y - 220f, "МИНИ-БОСС!", Color.rgb(233, 30, 99), 48f, 2f)
        }

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
                        Enemy(
                            EnemyType.RUNNER, x, y,
                            (1f + minute * 0.4f) * levelDef.hpMult,
                            dmgScaleNow, speedScaleNow,
                        ),
                    )
                }
            }
        }
        if (enemies.size > 260) {
            enemies.sortBy {
                if (it.type == EnemyType.BOSS || it.type == EnemyType.MINIBOSS) {
                    0f
                } else {
                    dist(player.x, player.y, it.x, it.y)
                }
            }
            while (enemies.size > 240) enemies.removeAt(enemies.size - 1)
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
        val x = (player.x + cos(a) * r).coerceIn(-WORLD_HALF + 60f, WORLD_HALF - 60f)
        val y = (player.y + sin(a) * r).coerceIn(-WORLD_HALF + 60f, WORLD_HALF - 60f)
        return Pair(x, y)
    }

    // ------------------------------------------------------------------
    // Снаряды, взрывы, частицы, подбираемое.
    // ------------------------------------------------------------------
    private fun updateProjectiles(dt: Float) {
        var i = projectiles.size - 1
        while (i >= 0) {
            val p = projectiles[i]
            p.age += dt

            if (p.kind == ProjKind.BANANA) {
                if (!p.returning && p.age > 0.45f) p.returning = true
                if (p.returning) {
                    val d = dist(p.x, p.y, player.x, player.y).coerceAtLeast(1f)
                    p.vx = (player.x - p.x) / d * 900f
                    p.vy = (player.y - p.y) / d * 900f
                    if (d < 44f) p.life = 0f
                }
            }

            p.x += p.vx * dt
            p.y += p.vy * dt
            p.life -= dt
            var dead = p.life <= 0f
            if (!dead) {
                for (e in enemies) {
                    if (dist(p.x, p.y, e.x, e.y) < p.radius + e.type.radius) {
                        if (p.kind == ProjKind.BANANA) {
                            if (e.bananaTick <= 0f) {
                                hitEnemy(e, p.damage, knockFrom = null)
                                e.bananaTick = 0.3f
                            }
                            continue
                        }
                        hitEnemy(e, p.damage, knockFrom = null)
                        if (p.kind == ProjKind.POISON) {
                            val w = player.weapons.firstOrNull { it.type == WeaponType.POISON }
                            val evoMult = if (w?.evolved == true) 2f else 1f
                            val dps = WeaponBalance.poisonDps(w?.level ?: 1) *
                                player.mult(Stat.DAMAGE) * evoMult
                            applyPoison(e, dps)
                        }
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

    private fun updateBooms(dt: Float) {
        var i = booms.size - 1
        while (i >= 0) {
            val b = booms[i]
            if (!b.exploded) {
                b.delay -= dt
                if (b.delay <= 0f) {
                    b.exploded = true
                    b.flash = 0.35f
                    spawnParticles(b.x, b.y, Color.rgb(255, 167, 38), 8, 300f, 5f)
                    for (e in enemies) {
                        if (dist(b.x, b.y, e.x, e.y) < b.radius + e.type.radius) {
                            hitEnemy(e, b.damage, knockFrom = null)
                        }
                    }
                }
            } else {
                b.flash -= dt
                if (b.flash <= 0f) booms.removeAt(i)
            }
            i--
        }
    }

    fun spawnParticles(x: Float, y: Float, color: Int, count: Int, speed: Float, size: Float) {
        if (particles.size > 280) return
        repeat(count) {
            val a = rng.nextFloat() * Math.PI.toFloat() * 2f
            val v = speed * (0.4f + rng.nextFloat() * 0.6f)
            particles.add(
                Particle(
                    x, y, cos(a) * v, sin(a) * v,
                    life = 0.3f + rng.nextFloat() * 0.3f,
                    color = color,
                    size = size * (0.6f + rng.nextFloat() * 0.8f),
                ),
            )
        }
    }

    private fun updateParticles(dt: Float) {
        var i = particles.size - 1
        while (i >= 0) {
            val p = particles[i]
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.vx *= 0.9f
            p.vy *= 0.9f
            p.life -= dt
            if (p.life <= 0f) particles.removeAt(i)
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
                    PickupType.GOLD -> gainGold(p.value.toInt(), silent = true)
                    PickupType.HP -> {
                        player.hp = min(player.maxHp, player.hp + p.value)
                        addText(player.x, player.y - 50f, "+${p.value.toInt()}", Color.rgb(102, 187, 106))
                    }
                }
                pickups.removeAt(i)
            }
            i--
        }
    }

    private fun gainGold(amount: Int, silent: Boolean = false) {
        val total = (amount * player.mult(Stat.GOLD_GAIN)).toInt().coerceAtLeast(1)
        gold += total
        if (!silent) {
            addText(player.x, player.y - 60f, "+$total золота", Color.rgb(255, 213, 79), 28f)
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
    // Объекты мира.
    // ------------------------------------------------------------------
    private fun updateWorldObjects(dt: Float) {
        for (obj in world.objectsAround(player.x, player.y)) {
            if (obj.consumed) continue
            when (obj) {
                is Chest -> {
                    if (dist(player.x, player.y, obj.x, obj.y) < obj.radius + player.radius) {
                        if (gold >= chestCost) {
                            gold -= chestCost
                            chestsOpened++
                            obj.consumed = true
                            giveItem(ItemPool.roll(rng, minute, player.items, meta.disabledItems))
                        } else if (chestHintTimer <= 0f) {
                            chestHintTimer = 1.5f
                            addText(
                                obj.x, obj.y - 70f,
                                "Нужно $chestCost золота", Color.rgb(255, 213, 79), 28f,
                            )
                        }
                    }
                }
                is GoldPile -> {
                    if (dist(player.x, player.y, obj.x, obj.y) < obj.radius + player.radius) {
                        obj.consumed = true
                        spawnParticles(obj.x, obj.y, Color.rgb(255, 193, 7), 5, 200f, 4f)
                        gainGold(obj.amount)
                    }
                }
                is Shrine -> {
                    val inside = dist(player.x, player.y, obj.x, obj.y) < obj.radius
                    if (inside) {
                        obj.progress += dt / obj.captureTime * captureSpeed()
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
                        obj.progress += dt / obj.captureTime * captureSpeed()
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

    private fun captureSpeed(): Float =
        1f + 0.3f * player.countSpecial(Special.CAPTURE_SPEED)

    private fun captureShrine(s: Shrine) {
        player.buffs.removeAll { it.name == s.kind.label }
        player.buffs.add(Buff(s.kind.label, s.kind.stats, s.kind.duration, s.kind.color))
        spawnParticles(s.x, s.y, s.kind.color, 14, 320f, 6f)
        addText(s.x, s.y - 100f, "${s.kind.label}!", s.kind.color, 40f, 1.8f)
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
        spawnParticles(s.x, s.y, Color.rgb(178, 223, 219), 14, 320f, 6f)
        addText(s.x, s.y - 100f, "+5% ко всем навыкам (навсегда)", Color.rgb(178, 223, 219), 34f, 2.2f)
    }

    private fun giveItem(item: ItemDef) {
        player.items.add(item)
        meta.discover(item.id)
        popupItem = item
        state = GameState.ITEM_POPUP
        checkEvolutions()
    }

    /** Оружие 20 ур. + свой катализатор → эволюция. */
    private fun checkEvolutions() {
        for (w in player.weapons) {
            if (w.evolved || w.level < MAX_WEAPON_LEVEL) continue
            if (player.items.any { it.catalystFor == w.type }) {
                w.evolved = true
                val evo = EVOLUTIONS.getValue(w.type)
                spawnParticles(player.x, player.y, evo.color, 24, 380f, 7f)
                shake(12f, 0.5f)
                addText(player.x, player.y - 140f, "ЭВОЛЮЦИЯ: ${evo.label}!", evo.color, 44f, 2.5f)
            }
        }
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

    fun addText(x: Float, y: Float, msg: String, color: Int, size: Float = 30f, life: Float = 0.9f) {
        if (texts.size > 80) return
        texts.add(FloatingText(x, y, msg, color, size, life))
    }

    // ------------------------------------------------------------------
    // Ввод по оверлеям.
    // ------------------------------------------------------------------
    fun handleTap(x: Float, y: Float) {
        when (state) {
            // Меню обрабатывается в GameView через touchDown/Move/Up.
            GameState.MENU -> Unit
            GameState.LEVEL_UP -> {
                for (i in optionRects.indices) {
                    if (optionRects[i].contains(x, y)) {
                        upgradeOptions[i].apply(player)
                        checkEvolutions()
                        state = GameState.RUNNING
                        if (pendingLevelUps > 0) openLevelUp()
                        return
                    }
                }
            }
            GameState.ITEM_POPUP -> {
                popupItem = null
                state = GameState.RUNNING
                if (pendingLevelUps > 0) openLevelUp()
            }
            GameState.PAUSED -> state = GameState.RUNNING
            GameState.GAME_OVER -> finishRun()
            GameState.VICTORY -> finishRun()
            GameState.RUNNING -> {
                if (pauseRect.contains(x, y)) state = GameState.PAUSED
            }
        }
    }
}
