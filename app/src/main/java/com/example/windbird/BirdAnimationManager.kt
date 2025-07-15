package com.example.windbird

import android.graphics.Canvas
import kotlin.math.*
import kotlin.random.Random

// Classes de données publiques
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
    
    // Paramètres principaux - NOUVEAU SYSTÈME
    private val FALL_THRESHOLD = 1.0f  // 100% pour faire tomber
    private val SENSITIVITY_MULTIPLIER = 0.8f  // Réduit de 20% (était 1.0f)
    
    // Timing des animations
    private val fallDuration = 800f  // 0.8 secondes de chute
    private val impactDuration = 2500f  // 2.5 secondes au sol
    private val respawnDuration = 1500f  // 1.5 secondes de respawn
    
    // État actuel
    var currentState = BirdState.PERCHED
        private set
    
    // Variables d'animation
    private var fallTimer = 0f
    private var impactTimer = 0f
    private var respawnTimer = 0f
    private var lastWindForce = 0f
    private var bodyLean = 0f
    private var eyeState = EyeState.NORMAL
    
    // Position et taille
    val birdSize = screenWidth * 0.7f
    val birdCenterX = screenWidth / 2f
    val birdCenterY = screenHeight * 0.4f
    val branchY = birdCenterY + birdSize * 0.5f
    
    // Particules
    private val tears = mutableListOf<Tear>()
    private val flyingFeathers = mutableListOf<FlyingFeather>()
    private val dustParticles = mutableListOf<DustParticle>()
    private val fallingLeaves = mutableListOf<FallingLeaf>()
    
    // Renderer pour le dessin
    private lateinit var birdRenderer: BirdRenderer
    
    fun setBirdRenderer(renderer: BirdRenderer) {
        this.birdRenderer = renderer
    }
    
    fun updateWind(rawForce: Float, deltaTime: Float) {
        // Application de la sensibilité réduite
        val adjustedForce = (rawForce * SENSITIVITY_MULTIPLIER).coerceIn(0f, 1f)
        lastWindForce = adjustedForce
        
        when (currentState) {
            BirdState.PERCHED -> {
                // Chute instantanée si on atteint 100%
                if (adjustedForce >= FALL_THRESHOLD) {
                    startFalling()
                }
                // Animations visuelles selon la force
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
        
        // Mise à jour des particules
        updateAllParticles(deltaTime)
    }
    
    private fun updatePerchedAnimations(force: Float) {
        when {
            force < 0.3f -> {
                // Calme - yeux normaux
                eyeState = EyeState.NORMAL
                bodyLean = 0f
            }
            force < 0.7f -> {
                // Vent moyen - commence à plisser les yeux
                eyeState = EyeState.SQUINTING
                bodyLean = force * 5f
            }
            force < 1.0f -> {
                // Vent fort - joues gonflées, prêt à tomber
                eyeState = EyeState.STRUGGLING
                bodyLean = force * 10f
                if (force > 0.9f) {
                    // Commence les larmes avant la chute
                    addTears()
                }
            }
        }
    }
    
    private fun startFalling() {
        currentState = BirdState.FALLING
        fallTimer = 0f
        eyeState = EyeState.PANICKED
        
        // Ajouter des plumes qui s'envolent
        repeat(8) {
            addFlyingFeather()
        }
        
        // Quelques larmes de panique
        repeat(3) {
            addTears()
        }
    }
    
    private fun land() {
        currentState = BirdState.FALLEN
        impactTimer = 0f
        
        // Explosion de poussière à l'impact
        repeat(15) {
            addDustParticle()
        }
        
        // Quelques feuilles qui tombent
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
        
        // Nettoyer les particules
        tears.clear()
        flyingFeathers.clear()
        dustParticles.clear()
        fallingLeaves.clear()
    }
    
    // Gestion des particules
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
        // Mise à jour des larmes
        tears.removeAll { tear ->
            tear.x += tear.velocityX * deltaTime / 16f
            tear.y += tear.velocityY * deltaTime / 16f
            tear.velocityY += 0.3f * deltaTime / 16f // gravité
            tear.life -= deltaTime / 1000f
            tear.life <= 0f || tear.y > screenHeight
        }
        
        // Mise à jour des plumes volantes
        flyingFeathers.removeAll { feather ->
            feather.x += feather.vx * deltaTime / 16f
            feather.y += feather.vy * deltaTime / 16f
            feather.vy += 0.1f * deltaTime / 16f // légère gravité
            feather.rotation += 3f * deltaTime / 16f
            feather.life -= deltaTime / 2000f
            feather.life <= 0f || feather.y > screenHeight
        }
        
        // Mise à jour des particules de poussière
        dustParticles.removeAll { dust ->
            dust.x += dust.vx * deltaTime / 16f
            dust.y += dust.vy * deltaTime / 16f
            dust.vx *= 0.98f // friction
            dust.vy *= 0.98f
            dust.life -= deltaTime / 1500f
            dust.life <= 0f
        }
        
        // Mise à jour des feuilles qui tombent
        fallingLeaves.removeAll { leaf ->
            leaf.x += leaf.vx * deltaTime / 16f
            leaf.y += leaf.vy * deltaTime / 16f
            leaf.vx += (Random.nextFloat() - 0.5f) * 0.1f * deltaTime / 16f // flottement
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
    
    // Getters pour le renderer
    fun getFallProgress(): Float = if (currentState == BirdState.FALLING) fallTimer / fallDuration else 0f
    fun getRespawnProgress(): Float = if (currentState == BirdState.RESPAWNING) respawnTimer / respawnDuration else 0f
    fun getBodyLean(): Float = bodyLean
    fun getEyeState(): EyeState = eyeState
    fun getLastWindForce(): Float = lastWindForce
    
    fun getCurrentState(): String {
        return "État: ${currentState.name}\n" +
                "Force vent: ${(lastWindForce * 100).toInt()}%\n" +
                "Seuil chute: ${(FALL_THRESHOLD * 100).toInt()}%\n" +
                "Sensibilité: ${(SENSITIVITY_MULTIPLIER * 100).toInt()}%\n" +
                "Inclinaison: ${bodyLean.toInt()}°\n" +
                "Yeux: ${eyeState.name}"
    }
    
    fun resetBird() {
        respawn()
    }
}
