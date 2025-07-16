package com.example.windbird

import android.graphics.Canvas
import kotlin.math.*
import kotlin.random.Random

data class Tear(var x: Float, var y: Float, var velocityX: Float, var velocityY: Float, var life: Float)
data class FlyingFeather(var x: Float, var y: Float, var vx: Float, var vy: Float, var rotation: Float, var life: Float)
data class DustParticle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, var size: Float)
data class FallingLeaf(var x: Float, var y: Float, var vx: Float, var vy: Float, var rotation: Float, var life: Float)

enum class BirdState {
    PERCHED, FALLING, FALLEN, RESPAWNING
}

enum class EyeState {
    NORMAL, SQUINTING, STRUGGLING, PANICKED
}

class BirdAnimationManager(private val screenWidth: Float, private val screenHeight: Float) {
    
    // Paramètres - sensibilité réduite de 20%
    private val FALL_THRESHOLD = 1.0f
    private val SENSITIVITY_REDUCTION = 0.8f // 20% moins sensible
    
    private val fallDuration = 800f
    private val impactDuration = 2500f
    private val respawnDuration = 1500f
    
    var currentState = BirdState.PERCHED
        private set
    
    private var fallTimer = 0f
    private var impactTimer = 0f
    private var respawnTimer = 0f
    private var lastWindForce = 0f
    private var bodyLean = 0f
    private var eyeState = EyeState.NORMAL
    
    // Effet sinistre - intensité de l'aura sombre
    private var darkAuraIntensity = 0f
    private var menacingStare = 0f
    
    val birdSize = screenWidth * 0.7f
    val birdCenterX = screenWidth / 2f
    val birdCenterY = screenHeight * 0.4f
    val branchY = birdCenterY + birdSize * 0.5f
    
    private val tears = mutableListOf<Tear>()
    private val flyingFeathers = mutableListOf<FlyingFeather>()
    private val dustParticles = mutableListOf<DustParticle>()
    private val fallingLeaves = mutableListOf<FallingLeaf>()
    
    private lateinit var birdRenderer: BirdRenderer
    
    fun setBirdRenderer(renderer: BirdRenderer) {
        this.birdRenderer = renderer
    }
    
    fun updateWind(rawForce: Float, deltaTime: Float) {
        // Appliquer la réduction de sensibilité
        val adjustedForce = (rawForce * SENSITIVITY_REDUCTION).coerceIn(0f, 1f)
        lastWindForce = adjustedForce
        
        // Mise à jour de l'aura sombre
        darkAuraIntensity = adjustedForce * 0.8f + sin(System.currentTimeMillis() * 0.003f) * 0.2f
        menacingStare = adjustedForce * 1.2f
        
        when (currentState) {
            BirdState.PERCHED -> {
                if (adjustedForce >= FALL_THRESHOLD) {
                    startFalling()
                }
                updatePerchedAnimations(adjustedForce)
            }
            
            BirdState.FALLING -> {
                fallTimer += deltaTime
                updateFallingParticles(deltaTime)
                if (fallTimer >= fallDuration) {
                    land()
                }
            }
            
            BirdState.FALLEN -> {
                impactTimer += deltaTime
                updateImpactParticles(deltaTime)
                if (impactTimer >= impactDuration) {
                    startRespawning()
                }
            }
            
            BirdState.RESPAWNING -> {
                respawnTimer += deltaTime
                if (respawnTimer >= respawnDuration) {
                    respawn()
                }
            }
        }
        
        updateAllParticles(deltaTime)
    }
    
    private fun updatePerchedAnimations(force: Float) {
        when {
            force < 0.3f -> {
                eyeState = EyeState.NORMAL
                bodyLean = 0f
            }
            force < 0.7f -> {
                eyeState = EyeState.SQUINTING
                bodyLean = force * 8f
                // Regard plus intense et menaçant
                if (Random.nextFloat() < 0.1f) {
                    addSinisterFeather()
                }
            }
            force < 1.0f -> {
                eyeState = EyeState.STRUGGLING
                bodyLean = force * 15f
                // Corbeau devient agressif avant la chute
                if (force > 0.9f) {
                    addBloodTears()
                    addOminousFeathers()
                }
            }
        }
    }
    
    private fun startFalling() {
        currentState = BirdState.FALLING
        fallTimer = 0f
        eyeState = EyeState.PANICKED
        
        // Explosion dramatique de plumes noires
        repeat(12) {
            addDarkFlyingFeather()
        }
        
        // Cri sinistre (représenté par des particules)
        repeat(6) {
            addBloodTears()
        }
    }
    
    private fun land() {
        currentState = BirdState.FALLEN
        impactTimer = 0f
        
        // Impact dramatique avec poussière sombre
        repeat(20) {
            addDarkDustParticle()
        }
        
        // Feuilles mortes qui tombent
        repeat(8) {
            addDeadLeaf()
        }
    }
    
    private fun startRespawning() {
        currentState = BirdState.RESPAWNING
        respawnTimer = 0f
    }
    
    private fun respawn() {
        currentState = BirdState.PERCHED
        fallTimer = 0f
        impactTimer = 0f
        respawnTimer = 0f
        bodyLean = 0f
        eyeState = EyeState.NORMAL
        darkAuraIntensity = 0f
        menacingStare = 0f
        
        tears.clear()
        flyingFeathers.clear()
        dustParticles.clear()
        fallingLeaves.clear()
    }
    
    // Particules sinistres
    private fun addBloodTears() {
        val tearX = birdCenterX + Random.nextFloat() * 30f - 15f
        val tearY = birdCenterY + Random.nextFloat() * 25f - 12f
        tears.add(Tear(
            x = tearX,
            y = tearY,
            velocityX = Random.nextFloat() * 3f - 1.5f,
            velocityY = Random.nextFloat() * 4f + 2f,
            life = 1f
        ))
    }
    
    private fun addDarkFlyingFeather() {
        flyingFeathers.add(FlyingFeather(
            x = birdCenterX + Random.nextFloat() * birdSize * 0.4f - birdSize * 0.2f,
            y = birdCenterY + Random.nextFloat() * birdSize * 0.4f - birdSize * 0.2f,
            vx = Random.nextFloat() * 8f - 4f,
            vy = Random.nextFloat() * 6f - 3f,
            rotation = Random.nextFloat() * 360f,
            life = 1f
        ))
    }
    
    private fun addSinisterFeather() {
        flyingFeathers.add(FlyingFeather(
            x = birdCenterX + Random.nextFloat() * birdSize * 0.2f - birdSize * 0.1f,
            y = birdCenterY + Random.nextFloat() * birdSize * 0.2f - birdSize * 0.1f,
            vx = Random.nextFloat() * 4f - 2f,
            vy = Random.nextFloat() * 3f - 1.5f,
            rotation = Random.nextFloat() * 360f,
            life = 1f
        ))
    }
    
    private fun addOminousFeathers() {
        repeat(3) {
            addSinisterFeather()
        }
    }
    
    private fun addDarkDustParticle() {
        dustParticles.add(DustParticle(
            x = birdCenterX + Random.nextFloat() * 80f - 40f,
            y = branchY + Random.nextFloat() * 30f,
            vx = Random.nextFloat() * 10f - 5f,
            vy = Random.nextFloat() * 8f - 4f,
            life = 1f,
            size = Random.nextFloat() * 10f + 3f
        ))
    }
    
    private fun addDeadLeaf() {
        fallingLeaves.add(FallingLeaf(
            x = Random.nextFloat() * screenWidth,
            y = -60f,
            vx = Random.nextFloat() * 3f - 1.5f,
            vy = Random.nextFloat() * 4f + 3f,
            rotation = Random.nextFloat() * 360f,
            life = 1f
        ))
    }
    
    private fun updateFallingParticles(deltaTime: Float) {
        if (Random.nextFloat() < 0.4f) {
            addDarkFlyingFeather()
        }
        if (Random.nextFloat() < 0.3f) {
            addBloodTears()
        }
    }
    
    private fun updateImpactParticles(deltaTime: Float) {
        if (impactTimer < 600f && Random.nextFloat() < 0.15f) {
            addDarkDustParticle()
        }
    }
    
    private fun updateAllParticles(deltaTime: Float) {
        // Larmes de sang avec gravité
        tears.removeAll { tear ->
            tear.x += tear.velocityX * deltaTime / 16f
            tear.y += tear.velocityY * deltaTime / 16f
            tear.velocityY += 0.4f * deltaTime / 16f // gravité plus forte
            tear.life -= deltaTime / 1200f
            tear.life <= 0f || tear.y > screenHeight
        }
        
        // Plumes noires volantes
        flyingFeathers.removeAll { feather ->
            feather.x += feather.vx * deltaTime / 16f
            feather.y += feather.vy * deltaTime / 16f
            feather.vy += 0.15f * deltaTime / 16f // légère gravité
            feather.rotation += 4f * deltaTime / 16f
            feather.life -= deltaTime / 2500f
            feather.life <= 0f || feather.y > screenHeight
        }
        
        // Poussière sombre
        dustParticles.removeAll { dust ->
            dust.x += dust.vx * deltaTime / 16f
            dust.y += dust.vy * deltaTime / 16f
            dust.vx *= 0.97f // friction
            dust.vy *= 0.97f
            dust.life -= deltaTime / 1800f
            dust.life <= 0f
        }
        
        // Feuilles mortes
        fallingLeaves.removeAll { leaf ->
            leaf.x += leaf.vx * deltaTime / 16f
            leaf.y += leaf.vy * deltaTime / 16f
            leaf.vx += (Random.nextFloat() - 0.5f) * 0.15f * deltaTime / 16f
            leaf.rotation += 3f * deltaTime / 16f
            leaf.life -= deltaTime / 3500f
            leaf.life <= 0f || leaf.y > screenHeight
        }
    }
    
    fun draw(canvas: Canvas) {
        if (::birdRenderer.isInitialized) {
            birdRenderer.drawBird(canvas, this)
            birdRenderer.drawParticles(canvas, tears, flyingFeathers, dustParticles, fallingLeaves)
        }
    }
    
    // Getters pour le renderer
    fun getFallProgress(): Float = if (currentState == BirdState.FALLING) fallTimer / fallDuration else 0f
    fun getRespawnProgress(): Float = if (currentState == BirdState.RESPAWNING) respawnTimer / respawnDuration else 0f
    fun getBodyLean(): Float = bodyLean
    fun getEyeState(): EyeState = eyeState
    fun getLastWindForce(): Float = lastWindForce
    fun getDarkAuraIntensity(): Float = darkAuraIntensity
    fun getMenacingStare(): Float = menacingStare
    
    fun getCurrentState(): String {
        return "État: ${currentState.name}\n" +
                "Force vent: ${(lastWindForce * 100).toInt()}%\n" +
                "Sensibilité: 80%\n" +
                "Inclinaison: ${bodyLean.toInt()}°\n" +
                "Yeux: ${eyeState.name}\n" +
                "Aura sombre: ${(darkAuraIntensity * 100).toInt()}%"
    }
    
    fun resetBird() {
        respawn()
    }
}
