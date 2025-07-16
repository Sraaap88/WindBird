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
                bodyLean = force * 5f
            }
            force < 1.0f -> {
                eyeState = EyeState.STRUGGLING
                bodyLean = force * 10f
                if (force > 0.9f) {
                    addTears()
                }
            }
        }
    }
    
    private fun startFalling() {
        currentState = BirdState.FALLING
        fallTimer = 0f
        eyeState = EyeState.PANICKED
        
        repeat(8) {
            addFlyingFeather()
        }
        
        repeat(3) {
            addTears()
        }
    }
    
    private fun land() {
        currentState = BirdState.FALLEN
        impactTimer = 0f
        
        repeat(15) {
            addDustParticle()
        }
        
        repeat(5) {
            addFallingLeaf()
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
        
        tears.clear()
        flyingFeathers.clear()
        dustParticles.clear()
        fallingLeaves.clear()
    }
    
    private fun addTears() {
        val tearX = birdCenterX + Random.nextFloat() * 20f - 10f
        val tearY = birdCenterY + Random.nextFloat() * 20f - 10f
        tears.add(Tear(
            x = tearX,
            y = tearY,
            velocityX = Random.nextFloat() * 4f - 2f,
            velocityY = Random.nextFloat() * 2f + 1f,
            life = 1f
        ))
    }
    
    private fun addFlyingFeather() {
        flyingFeathers.add(FlyingFeather(
            x = birdCenterX + Random.nextFloat() * birdSize * 0.3f - birdSize * 0.15f,
            y = birdCenterY + Random.nextFloat() * birdSize * 0.3f - birdSize * 0.15f,
            vx = Random.nextFloat() * 6f - 3f,
            vy = Random.nextFloat() * 4f - 2f,
            rotation = Random.nextFloat() * 360f,
            life = 1f
        ))
    }
    
    private fun addDustParticle() {
        dustParticles.add(DustParticle(
            x = birdCenterX + Random.nextFloat() * 60f - 30f,
            y = branchY + Random.nextFloat() * 20f,
            vx = Random.nextFloat() * 8f - 4f,
            vy = Random.nextFloat() * 6f - 3f,
            life = 1f,
            size = Random.nextFloat() * 8f + 2f
        ))
    }
    
    private fun addFallingLeaf() {
        fallingLeaves.add(FallingLeaf(
            x = Random.nextFloat() * screenWidth,
            y = -50f,
            vx = Random.nextFloat() * 2f - 1f,
            vy = Random.nextFloat() * 3f + 2f,
            rotation = Random.nextFloat() * 360f,
            life = 1f
        ))
    }
    
    private fun updateFallingParticles(deltaTime: Float) {
        if (Random.nextFloat() < 0.3f) {
            addFlyingFeather()
        }
        if (Random.nextFloat() < 0.2f) {
            addTears()
        }
    }
    
    private fun updateImpactParticles(deltaTime: Float) {
        if (impactTimer < 500f && Random.nextFloat() < 0.1f) {
            addDustParticle()
        }
    }
    
    private fun updateAllParticles(deltaTime: Float) {
        tears.removeAll { tear ->
            tear.x += tear.velocityX * deltaTime / 16f
            tear.y += tear.velocityY * deltaTime / 16f
            tear.velocityY += 0.3f * deltaTime / 16f
            tear.life -= deltaTime / 1000f
            tear.life <= 0f || tear.y > screenHeight
        }
        
        flyingFeathers.removeAll { feather ->
            feather.x += feather.vx * deltaTime / 16f
            feather.y += feather.vy * deltaTime / 16f
            feather.vy += 0.1f * deltaTime / 16f
            feather.rotation += 3f * deltaTime / 16f
            feather.life -= deltaTime / 2000f
            feather.life <= 0f || feather.y > screenHeight
        }
        
        dustParticles.removeAll { dust ->
            dust.x += dust.vx * deltaTime / 16f
            dust.y += dust.vy * deltaTime / 16f
            dust.vx *= 0.98f
            dust.vy *= 0.98f
            dust.life -= deltaTime / 1500f
            dust.life <= 0f
        }
        
        fallingLeaves.removeAll { leaf ->
            leaf.x += leaf.vx * deltaTime / 16f
            leaf.y += leaf.vy * deltaTime / 16f
            leaf.vx += (Random.nextFloat() - 0.5f) * 0.1f * deltaTime / 16f
            leaf.rotation += 2f * deltaTime / 16f
            leaf.life -= deltaTime / 3000f
            leaf.life <= 0f || leaf.y > screenHeight
        }
    }
    
    fun draw(canvas: Canvas) {
        if (::birdRenderer.isInitialized) {
            birdRenderer.drawBird(canvas, this)
            birdRenderer.drawParticles(canvas, tears, flyingFeathers, dustParticles, fallingLeaves)
        }
    }
    
    fun getFallProgress(): Float = if (currentState == BirdState.FALLING) fallTimer / fallDuration else 0f
    fun getRespawnProgress(): Float = if (currentState == BirdState.RESPAWNING) respawnTimer / respawnDuration else 0f
    fun getBodyLean(): Float = bodyLean
    fun getEyeState(): EyeState = eyeState
    fun getLastWindForce(): Float = lastWindForce
    
    fun getCurrentState(): String {
        return "État: ${currentState.name}\n" +
                "Force vent: ${(lastWindForce * 100).toInt()}%\n" +
                "Sensibilité: 80%\n" +
                "Inclinaison: ${bodyLean.toInt()}°\n" +
                "Yeux: ${eyeState.name}"
    }
    
    fun resetBird() {
        respawn()
    }
}
