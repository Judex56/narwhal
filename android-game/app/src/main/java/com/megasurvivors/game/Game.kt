package com.megasurvivors.game

import android.graphics.Color
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

enum class GameState { MENU, RUNNING, LEVEL_UP, ITEM_POPUP, PAUSED, GAME_OVER }

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

    val enemies = ArrayList<Enemy>()
    val projectiles = ArrayList<Projectile>()
    val pickups = ArrayList<Pickup>()
    val texts = ArrayList<FloatingText>()
    val booms = ArrayList<Boom>()
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
    private var spawnTimer = 0f
    private var eliteTimer = 45f
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
    val chestCost: Int get() = 25 + 15 * chestsOpened

    // ------------------------------------------------------------------
    // Запуск и завершение забега.
    // ------------------------------------------------------------------
    fun startRun() {
        enemies.clear()
        projectiles.clear()
        pickups.clear()
        texts.clear()
        booms.clear()
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
            passives.clear()
        }
        player.weapons.add(WeaponInstance(menu.selectedWeapon))
        menu.selectedItem?.let { player.items.add(it) }
        for (name in meta.selectedTomes) {
            val tome = Tome.entries.firstOrNull { it.name == name } ?: continue
            player.buffs.add(Buff(tome.label, tome.stats, Float.POSITIVE_INFINITY, tome.color))
        }
        player.hp = player.maxHp

        time = 0f
        kills = 0
        gold = 30
        chestsOpened = 0
        spawnTimer = 0f
        eliteTimer = 45f
        pendingLevelUps = 0
        popupItem = null
        spinEffect = 0f
        nukeFlash = 0f
        state = GameState.RUNNING
    }

    private fun finishRun() {
        meta.gold += gold
        val secs = time.toInt()
        if (secs > meta.bestTime) meta.bestTime = secs
        meta.save()
        state = GameState.MENU
    }

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
        updateBooms(dt)
        updatePickups(dt)
        updateWorldObjects(dt)
        updateBuffs(dt)
        updateTexts(dt)
        spawnEnemies(dt)

        if (spinEffect > 0f) spinEffect -= dt
        if (nukeFlash > 0f) nukeFlash -= dt
        if (chestHintTimer > 0f) chestHintTimer -= dt

        if (player.hp <= 0f) {
            state = GameState.GAME_OVER
        }
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
    private fun damagePlayer(raw: Float) {
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

        // Ответный вихрь (Axe): контратака при получении урона.
        for (w in player.weapons) {
            if (w.type == WeaponType.COUNTER_SPIN && w.timer <= 0f) {
                w.timer = WeaponBalance.spinCooldown(w.level) * player.cooldownFactor()
                spinEffect = 0.4f
                val radius = WeaponBalance.spinRadius(w.level) * player.mult(Stat.AREA)
                val dmgOut = WeaponBalance.spinDamage(w.level) * player.mult(Stat.DAMAGE)
                for (e in enemies) {
                    if (dist(player.x, player.y, e.x, e.y) < radius + e.type.radius) {
                        hitEnemy(e, dmgOut, knockFrom = player)
                    }
                }
            }
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
                        val target = nearestEnemy(player.x, player.y, 1400f) ?: continue
                        w.timer = WeaponBalance.dartCooldown(w.level) * cdFactor
                        fireSpread(
                            target, WeaponBalance.dartCount(w.level),
                            WeaponBalance.dartDamage(w.level) * dmgMult,
                            750f * player.mult(Stat.PROJ_SPEED),
                            ProjKind.DART, WeaponType.DART.color,
                            pierce = 1 + w.level / 6, life = 1.6f,
                        )
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
                        val radius = WeaponBalance.auraRadius(w.level) * areaMult
                        val dmg = WeaponBalance.auraDamage(w.level) * dmgMult
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
                        w.timer = WeaponBalance.lightningCooldown(w.level) * cdFactor
                        val dmg = WeaponBalance.lightningDamage(w.level) * dmgMult
                        val onScreen = enemies.filter {
                            dist(player.x, player.y, it.x, it.y) < screenW * 0.6f
                        }
                        for (e in onScreen.shuffled(rng).take(WeaponBalance.lightningTargets(w.level))) {
                            hitEnemy(e, dmg, knockFrom = null)
                            lightningBolts.add(floatArrayOf(e.x, e.y - 700f, e.x, e.y, 0.25f))
                        }
                    }
                }
                WeaponType.COUNTER_SPIN -> {
                    // Срабатывает в damagePlayer(); здесь только тикает кулдаун.
                    if (w.timer > 0f) w.timer -= dt
                }
                WeaponType.SCYTHE -> {
                    sweepBlades(
                        w, dt, blades = 1,
                        speed = WeaponBalance.scytheSpeed(w.level),
                        radius = WeaponBalance.scytheRadius(w.level) * areaMult,
                        dmg = WeaponBalance.scytheDamage(w.level) * dmgMult,
                        lifesteal = false,
                    )
                }
                WeaponType.REAPER -> {
                    sweepBlades(
                        w, dt, blades = WeaponBalance.REAPER_BLADES,
                        speed = WeaponBalance.REAPER_SPEED,
                        radius = WeaponBalance.REAPER_RADIUS * areaMult,
                        dmg = WeaponBalance.REAPER_DMG * dmgMult,
                        lifesteal = true,
                    )
                }
                WeaponType.FROST_AURA -> {
                    w.timer -= dt
                    if (w.timer <= 0f) {
                        w.timer = WeaponBalance.FROST_TICK * cdFactor
                        val radius = WeaponBalance.frostRadius(w.level) * areaMult
                        val dmg = WeaponBalance.frostDamage(w.level) * dmgMult
                        val slow = WeaponBalance.frostSlow(w.level)
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
                }
                WeaponType.ICE_STORM -> {
                    w.timer -= dt
                    // orbitAngle используем как таймер заморозки.
                    w.orbitAngle += dt
                    val radius = WeaponBalance.ICE_STORM_RADIUS * areaMult
                    if (w.timer <= 0f) {
                        w.timer = WeaponBalance.FROST_TICK * cdFactor
                        val dmg = WeaponBalance.ICE_STORM_DMG * dmgMult
                        for (e in enemies) {
                            if (dist(player.x, player.y, e.x, e.y) < radius + e.type.radius) {
                                if (e.frostTick <= 0f) {
                                    hitEnemy(e, dmg, knockFrom = null)
                                    e.frostTick = WeaponBalance.FROST_TICK
                                }
                                e.slowTimer = 1.5f
                                e.slowMult = WeaponBalance.ICE_STORM_SLOW
                            }
                        }
                    }
                    if (w.orbitAngle >= WeaponBalance.ICE_STORM_FREEZE_EVERY) {
                        w.orbitAngle = 0f
                        for (e in enemies) {
                            if (dist(player.x, player.y, e.x, e.y) < radius + e.type.radius) {
                                e.freezeTimer = WeaponBalance.ICE_STORM_FREEZE_TIME
                            }
                        }
                        addText(player.x, player.y - 90f, "ЗАМОРОЗКА!", Color.rgb(0, 229, 255), 34f)
                    }
                }
                WeaponType.POISON -> {
                    w.timer -= dt
                    if (w.timer <= 0f) {
                        val target = nearestEnemy(player.x, player.y, 1200f) ?: continue
                        w.timer = WeaponBalance.poisonCooldown(w.level) * cdFactor
                        fireSpread(
                            target, WeaponBalance.poisonCount(w.level),
                            WeaponBalance.poisonDamage(w.level) * dmgMult,
                            700f * player.mult(Stat.PROJ_SPEED),
                            ProjKind.POISON, WeaponType.POISON.color,
                            pierce = 1, life = 1.5f,
                        )
                    }
                }
                WeaponType.PLAGUE_CLOUD -> {
                    w.timer -= dt
                    if (w.timer <= 0f) {
                        w.timer = 0.5f * cdFactor
                        val radius = WeaponBalance.PLAGUE_RADIUS * areaMult
                        for (e in enemies) {
                            if (dist(player.x, player.y, e.x, e.y) < radius + e.type.radius) {
                                if (e.plagueTick <= 0f) {
                                    hitEnemy(e, WeaponBalance.PLAGUE_DMG * dmgMult, knockFrom = null)
                                    e.plagueTick = 0.5f
                                }
                                applyPoison(e, WeaponBalance.PLAGUE_DPS_PER_STACK * dmgMult)
                            }
                        }
                    }
                }
                WeaponType.NUKE -> {
                    w.timer -= dt
                    if (w.timer <= 0f && enemies.isNotEmpty()) {
                        w.timer = WeaponBalance.nukeCooldown(w.level) * cdFactor
                        nukeFlash = 0.5f
                        val dmg = WeaponBalance.nukeDamage(w.level) * dmgMult
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
                        val count = WeaponBalance.bananaCount(w.level)
                        for (i in 0 until count) {
                            val a = base + (i - (count - 1) / 2f) * 0.35f
                            val speed = 700f * player.mult(Stat.PROJ_SPEED)
                            projectiles.add(
                                Projectile(
                                    player.x, player.y, cos(a) * speed, sin(a) * speed,
                                    WeaponBalance.bananaDamage(w.level) * dmgMult,
                                    18f, pierce = Int.MAX_VALUE, life = 5f,
                                    kind = ProjKind.BANANA, color = WeaponType.BANANA.color,
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
                        firePsiBeam(w, target, dmgMult, areaMult)
                    }
                }
                WeaponType.METEOR -> {
                    w.timer -= dt
                    if (w.timer <= 0f) {
                        w.timer = WeaponBalance.meteorCooldown(w.level) * cdFactor
                        repeat(WeaponBalance.meteorCount(w.level)) {
                            val a = rng.nextFloat() * Math.PI.toFloat() * 2f
                            val r = 120f + rng.nextFloat() * 380f
                            booms.add(
                                Boom(
                                    (player.x + cos(a) * r).coerceIn(-WORLD_HALF, WORLD_HALF),
                                    (player.y + sin(a) * r).coerceIn(-WORLD_HALF, WORLD_HALF),
                                    delay = 0.7f,
                                    radius = WeaponBalance.meteorRadius(w.level) * areaMult,
                                    damage = WeaponBalance.meteorDamage(w.level) * dmgMult,
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
                        repeat(WeaponBalance.shotgunPellets(w.level)) {
                            val a = base + (rng.nextFloat() - 0.5f) * 0.9f
                            val speed = (800f + rng.nextFloat() * 150f) * player.mult(Stat.PROJ_SPEED)
                            projectiles.add(
                                Projectile(
                                    player.x, player.y, cos(a) * speed, sin(a) * speed,
                                    WeaponBalance.shotgunDamage(w.level) * dmgMult,
                                    8f, pierce = 1, life = WeaponBalance.SHOTGUN_RANGE,
                                    kind = ProjKind.PELLET, color = WeaponType.SHOTGUN.color,
                                ),
                            )
                        }
                    }
                }
                WeaponType.GREED_BLADE -> {
                    w.timer -= dt
                    if (w.timer <= 0f) {
                        val target = nearestEnemy(player.x, player.y, 1000f) ?: continue
                        w.timer = WeaponBalance.greedCooldown(w.level) * cdFactor
                        fireSpread(
                            target, 1,
                            WeaponBalance.greedDamage(w.level, w.stacks) * dmgMult,
                            820f * player.mult(Stat.PROJ_SPEED),
                            ProjKind.SLASH, WeaponType.GREED_BLADE.color,
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

    /** Общий веер снарядов в сторону цели. */
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

    /** Коса/Жнец: клинки, метущие по кругу. */
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

    private fun firePsiBeam(w: WeaponInstance, target: Enemy, dmgMult: Float, areaMult: Float) {
        val len = WeaponBalance.psiLength(w.level)
        val width = WeaponBalance.psiWidth(w.level) * areaMult
        val dmg = WeaponBalance.psiDamage(w.level) * dmgMult
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
        if (crit) {
            addText(e.x, e.y - e.type.radius - 8f, "${dmg.toInt()}!", Color.rgb(255, 213, 79), 36f)
        } else {
            addText(e.x + rng.nextFloat() * 20f - 10f, e.y - e.type.radius, dmg.toInt().toString(), Color.WHITE, 26f)
        }
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
            if (activeShrine != null && e.type != EnemyType.ELITE && rngHash(e) % 3 == 0) {
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
            if (e.freezeTimer > 0f) e.freezeTimer -= dt

            if (dist(e.x, e.y, player.x, player.y) < e.type.radius + player.radius) {
                damagePlayer(e.damage)
            }
            i--
        }
    }

    private fun rngHash(e: Enemy): Int = System.identityHashCode(e) and 0x7FFFFFFF

    private fun onEnemyDeath(e: Enemy) {
        kills++
        pickups.add(Pickup(PickupType.XP, e.x, e.y, e.type.xp))
        pickups.add(Pickup(PickupType.GOLD, e.x - 14f, e.y + 10f, e.type.gold.toFloat()))
        if (rng.nextFloat() < 0.03f) {
            pickups.add(Pickup(PickupType.HP, e.x + 20f, e.y, 20f))
        }
        // Вампиризм и стаки Клинка жажды.
        val steal = player.countSpecial(Special.LIFESTEAL)
        if (steal > 0) player.hp = min(player.maxHp, player.hp + steal)
        for (w in player.weapons) {
            if (w.type == WeaponType.GREED_BLADE) w.stacks++
        }
        // Элита всегда оставляет предмет (бесплатно).
        if (e.type == EnemyType.ELITE) {
            giveItem(ItemPool.roll(rng, minute, player.items))
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
            addText(player.x, player.y - 200f, "ЭЛИТА!", Color.rgb(255, 213, 79), 44f, 1.6f)
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
                    enemies.add(Enemy(EnemyType.RUNNER, x, y, 1f + minute * 0.4f, 1f + minute * 0.2f))
                }
            }
        }
        if (enemies.size > 220) {
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
        val x = (player.x + cos(a) * r).coerceIn(-WORLD_HALF + 60f, WORLD_HALF - 60f)
        val y = (player.y + sin(a) * r).coerceIn(-WORLD_HALF + 60f, WORLD_HALF - 60f)
        return Pair(x, y)
    }

    // ------------------------------------------------------------------
    // Снаряды, взрывы, подбираемое.
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
                            // ДПС яда зависит от уровня оружия на момент выстрела.
                            val w = player.weapons.firstOrNull { it.type == WeaponType.POISON }
                            val dps = WeaponBalance.poisonDps(w?.level ?: 1) * player.mult(Stat.DAMAGE)
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
        upgradeOptions = UpgradePool.rollOptions(player, rng, meta.unlockedWeapons)
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
                            giveItem(ItemPool.roll(rng, minute, player.items))
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
                        gainGold(obj.amount)
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
        addText(s.x, s.y - 100f, "+5% ко всем навыкам (навсегда)", Color.rgb(178, 223, 219), 34f, 2.2f)
    }

    private fun giveItem(item: ItemDef) {
        player.items.add(item)
        popupItem = item
        state = GameState.ITEM_POPUP
        checkEvolutions()
    }

    /** Оружие 20 ур. + катализатор → эволюция. */
    private fun checkEvolutions() {
        for (def in EVOLUTIONS) {
            val w = player.weapons.firstOrNull { it.type == def.base } ?: continue
            if (w.level < MAX_WEAPON_LEVEL) continue
            if (!player.items.any { it.special == def.catalyst }) continue
            w.type = def.result
            addText(
                player.x, player.y - 140f,
                "ЭВОЛЮЦИЯ: ${def.result.label}!", def.result.color, 44f, 2.5f,
            )
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
            GameState.MENU -> {
                if (menu.handleTap(x, y)) startRun()
            }
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
            GameState.RUNNING -> {
                if (pauseRect.contains(x, y)) state = GameState.PAUSED
            }
        }
    }
}
