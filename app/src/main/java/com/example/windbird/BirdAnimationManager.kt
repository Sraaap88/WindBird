package com.example.windbird

import android.graphics.*
import kotlin.math.*
import kotlin.random.Random

class BirdAnimationManager(private val screenWidth: Int, private val screenHeight: Int) {
    
    // ==================== PROPRIÉTÉS DE BASE ====================
    
    private val birdSize = screenWidth * 0.7f // 70% de l'écran (plus gros!)
    private val birdCenterX = screenWidth / 2f
    private val birdCenterY = screenHeight * 0.4f // Un peu plus haut
    
    // Branche
    private val branchY = birdCenterY + birdSize * 0.5f
    private val branchStartX = screenWidth * 0.05f
    private val branchEndX = screenWidth * 0.95f
    
    // ==================== ÉTAT DU VENT (SUPER FACILE) ====================
    
    private var currentWindForce = 0f
    private var windForceSmoothed = 0f
    private var sustainedWindTime = 0f
    private var extremeWindStartTime = 0f
    private val EXTREME_WIND_THRESHOLD = 0.3f // SUPER FACILE: 30% seulement !
    private val EXTREME_WIND_DURATION = 800f // SUPER FACILE: 0.8 sec seulement !
    
    // ==================== ANIMATION DES YEUX ====================
    
    private var eyeSquintLevel = 0f
    private var tearAnimationTime = 0f
    private var eyeRollAngle = 0f
    private var blinkSpeed = 1f
    private var blinkPhase = 0f
    private val tears = mutableListOf<Tear>()
    
    // ==================== ANIMATION DES PLUMES ====================
    
    private var featherWavePhase = 0f
    private var featherRuffleIntensity = 0f
    private var headCrestHeight = 0f
    private val flyingFeathers = mutableListOf<FlyingFeather>()
    private var dustCloudAlpha = 0f
    private var dustParticles = mutableListOf<DustParticle>()
    
    // ==================== ANIMATION DU CORPS ====================
    
    private var bodyLeanAngle = 0f
    private var wingOpenness = 0f
    private var tailCounterbalance = 0f
    private var footSlipProgress = 0f
    private var bodyShakeIntensity = 0f
    private var cheekPuffLevel = 0f
    private var beakOpenness = 0f
    private var tongueOut = 0f
    
    // ==================== ANIMATION DE LA BRANCHE ====================
    
    private var branchVibrateIntensity = 0f
    private var branchOscillateAngle = 0f
    private val fallingLeaves = mutableListOf<FallingLeaf>()
    
    // ==================== ÉTAT DE CHUTE ====================
    
    private var birdState = BirdState.PERCHED
    private var fallAnimationTime = 0f
    private var fallRotation = 0f
    private var fallPositionY = 0f
    private var respawnTimer = 0f
    private var impactEffectTime = 0f
    private var screenShakeIntensity = 0f
    
    init {
        fallPositionY = birdCenterY
    }
    
    enum class BirdState {
        PERCHED, FALLING, FALLEN, RESPAWNING
    }
    
    // ==================== CLASSES POUR EFFETS ====================
    
    data class Tear(var x: Float, var y: Float, var velocityX: Float, var velocityY: Float, var life: Float)
    data class FlyingFeather(var x: Float, var y: Float, var vx: Float, var vy: Float, var rotation: Float, var life: Float)
    data class DustParticle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, var size: Float)
    data class FallingLeaf(var x: Float, var y: Float, var vx: Float, var vy: Float, var rotation: Float, var life: Float)
    
    // ==================== COMPOSANTS EXTERNES ====================
    
    private val birdRenderer = BirdRenderer(birdSize, birdCenterX, birdCenterY, branchY, branchStartX, branchEndX, screenWidth, screenHeight)
    private val windGauge = WindGauge(screenWidth, screenHeight)
    
    // ==================== MISE À JOUR PRINCIPALE ====================
    
    fun updateWind(windForce: Float) {
        currentWindForce = windForce
        
        // Lissage du vent pour éviter les saccades
        windForceSmoothed = lerp(windForceSmoothed, windForce, 0.15f)
        
        // Gestion du vent extrême soutenu - CORRECTION DU BUG !
        if (windForceSmoothed >= EXTREME_WIND_THRESHOLD && birdState == BirdState.PERCHED) {
            if (extremeWindStartTime == 0f) {
                // PREMIER MOMENT où on dépasse le seuil
                extremeWindStartTime = System.currentTimeMillis().toFloat()
            }
            // TOUJOURS calculer le temps écoulé
            sustainedWindTime = System.currentTimeMillis() - extremeWindStartTime
            
            // Déclencher la chute après 0.8 secondes à 30% !
            if (sustainedWindTime >= EXTREME_WIND_DURATION) {
                startFalling()
            }
        } else {
            // Reset complet quand le vent baisse
            sustainedWindTime = 0f
            extremeWindStartTime = 0f
        }
        
        updateAnimations()
    }
    
    private fun updateAnimations() {
        val deltaTime = 0.033f // 30 FPS
        
        when (birdState) {
            BirdState.PERCHED -> updatePerchedAnimations(deltaTime)
            BirdState.FALLING -> updateFallingAnimations(deltaTime)
            BirdState.FALLEN -> updateFallenState(deltaTime)
            BirdState.RESPAWNING -> updateRespawning(deltaTime)
        }
        
        updateParticleEffects(deltaTime)
    }
    
    private fun updatePerchedAnimations(deltaTime: Float) {
        val force = windForceSmoothed
        
        // Animation des yeux plus expressive
        eyeSquintLevel = force
        blinkSpeed = 1f + force * 4f
        blinkPhase += deltaTime * blinkSpeed
        
        if (force > 0.4f) { // Plus sensible
            eyeRollAngle = (force - 0.4f) * 200f
            
            // Génération de larmes plus fréquente
            if (Random.nextFloat() < force * 0.15f) {
                val eyeX = birdCenterX + (Random.nextFloat() - 0.5f) * birdSize * 0.3f
                val eyeY = birdCenterY - birdSize * 0.15f
                tears.add(Tear(eyeX, eyeY, Random.nextFloat() * 80f - 40f, -Random.nextFloat() * 30f, 1.5f))
            }
        }
        
        // Animation des plumes plus fluide
        featherWavePhase += deltaTime * (1f + force * 3f)
        featherRuffleIntensity = force
        headCrestHeight = min(force * 2f, 1f)
        
        // Plus de plumes qui s'envolent
        if (force > 0.3f && Random.nextFloat() < force * 0.08f) {
            val featherX = birdCenterX + (Random.nextFloat() - 0.5f) * birdSize * 1.2f
            val featherY = birdCenterY + (Random.nextFloat() - 0.5f) * birdSize
            flyingFeathers.add(FlyingFeather(
                featherX, featherY,
                Random.nextFloat() * 250f - 125f,
                -Random.nextFloat() * 120f - 60f,
                Random.nextFloat() * 360f, 2f
            ))
        }
        
        // Animation du corps plus dramatique
        bodyLeanAngle = force * 35f
        wingOpenness = max(0f, force - 0.2f)
        tailCounterbalance = force * 40f
        footSlipProgress = max(0f, force - 0.4f) * 2f
        
        if (force > 0.5f) { // Plus sensible
            cheekPuffLevel = (force - 0.5f) * 2f
            beakOpenness = force * 1.2f
            tongueOut = max(0f, force - 0.6f) * 4f
        }
        
        // Plus d'effets de poussière
        if (force > 0.6f) {
            dustCloudAlpha = (force - 0.6f) * 2.5f
            if (Random.nextFloat() < 0.4f) {
                dustParticles.add(DustParticle(
                    birdCenterX + Random.nextFloat() * 150f - 75f,
                    branchY + Random.nextFloat() * 30f,
                    Random.nextFloat() * 120f - 60f,
                    -Random.nextFloat() * 60f,
                    1.5f, Random.nextFloat() * 12f + 6f
                ))
            }
        }
        
        // Animation de la branche plus visible
        branchVibrateIntensity = force * 8f
        branchOscillateAngle = sin(System.currentTimeMillis() * 0.015f) * force * 5f
        
        // Plus de feuilles
        if (force > 0.4f && Random.nextFloat() < force * 0.05f) {
            fallingLeaves.add(FallingLeaf(
                Random.nextFloat() * screenWidth,
                -20f,
                Random.nextFloat() * 120f - 60f,
                Random.nextFloat() * 120f + 80f,
                Random.nextFloat() * 360f, 2f
            ))
        }
    }
    
    private fun updateFallingAnimations(deltaTime: Float) {
        fallAnimationTime += deltaTime
        
        // Animation de chute plus spectaculaire
        fallRotation += deltaTime * 900f // Plus de rotation
        fallPositionY += deltaTime * 1000f // Plus rapide
        
        // Ailes qui battent frénétiquement
        wingOpenness = 1.2f + sin(fallAnimationTime * 25f) * 0.4f
        
        // Yeux paniqués
        eyeSquintLevel = 0.2f + sin(fallAnimationTime * 15f) * 0.3f
        eyeRollAngle = sin(fallAnimationTime * 10f) * 90f
        
        // Impact au sol
        if (fallPositionY >= screenHeight - 80f) {
            birdState = BirdState.FALLEN
            impactEffectTime = System.currentTimeMillis().toFloat()
            screenShakeIntensity = 1.5f
            
            // Explosion de poussière plus spectaculaire
            for (i in 0..30) {
                dustParticles.add(DustParticle(
                    birdCenterX + Random.nextFloat() * 300f - 150f,
                    screenHeight - 40f,
                    Random.nextFloat() * 400f - 200f,
                    -Random.nextFloat() * 250f - 120f,
                    2f, Random.nextFloat() * 20f + 8f
                ))
            }
        }
    }
    
    private fun updateFallenState(deltaTime: Float) {
        respawnTimer += deltaTime
        screenShakeIntensity = max(0f, screenShakeIntensity - deltaTime * 2f)
        
        if (respawnTimer >= 2.5f) {
            birdState = BirdState.RESPAWNING
            respawnTimer = 0f
        }
    }
    
    private fun updateRespawning(deltaTime: Float) {
        respawnTimer += deltaTime
        
        if (respawnTimer >= 1.5f) {
            reset()
        }
    }
    
    private fun updateParticleEffects(deltaTime: Float) {
        // Mise à jour des larmes
        tears.removeAll { tear ->
            tear.x += tear.velocityX * deltaTime
            tear.y += tear.velocityY * deltaTime
            tear.velocityY += 150f * deltaTime
            tear.life -= deltaTime
            tear.life <= 0f
        }
        
        // Mise à jour des plumes volantes
        flyingFeathers.removeAll { feather ->
            feather.x += feather.vx * deltaTime
            feather.y += feather.vy * deltaTime
            feather.vy += 50f * deltaTime // Légère gravité
            feather.rotation += 120f * deltaTime
            feather.life -= deltaTime
            feather.life <= 0f
        }
        
        // Mise à jour des particules de poussière
        dustParticles.removeAll { particle ->
            particle.x += particle.vx * deltaTime
            particle.y += particle.vy * deltaTime
            particle.vy += 100f * deltaTime // Gravité
            particle.life -= deltaTime
            particle.life <= 0f
        }
        
        // Mise à jour des feuilles qui tombent
        fallingLeaves.removeAll { leaf ->
            leaf.x += leaf.vx * deltaTime
            leaf.y += leaf.vy * deltaTime
            leaf.vx *= 0.99f // Résistance de l'air
            leaf.rotation += 60f * deltaTime
            leaf.life -= deltaTime
            leaf.life <= 0f || leaf.y > screenHeight
        }
    }
    
    // ==================== DESSIN PRINCIPAL ====================
    
    fun draw(canvas: Canvas) {
        val shakeX = if (screenShakeIntensity > 0) Random.nextFloat() * screenShakeIntensity * 15f - 7.5f else 0f
        val shakeY = if (screenShakeIntensity > 0) Random.nextFloat() * screenShakeIntensity * 15f - 7.5f else 0f
        
        canvas.save()
        canvas.translate(shakeX, shakeY)
        
        // Préparer les données pour le renderer
        val animationData = BirdAnimationData(
            birdState = birdState,
            bodyLeanAngle = bodyLeanAngle,
            fallPositionY = fallPositionY,
            fallRotation = fallRotation,
            wingOpenness = wingOpenness,
            eyeSquintLevel = eyeSquintLevel,
            eyeRollAngle = eyeRollAngle,
            cheekPuffLevel = cheekPuffLevel,
            beakOpenness = beakOpenness,
            tongueOut = tongueOut,
            headCrestHeight = headCrestHeight,
            featherWavePhase = featherWavePhase,
            tailCounterbalance = tailCounterbalance,
            footSlipProgress = footSlipProgress,
            branchVibrateIntensity = branchVibrateIntensity,
            branchOscillateAngle = branchOscillateAngle,
            respawnTimer = respawnTimer,
            tears = tears,
            flyingFeathers = flyingFeathers,
            dustParticles = dustParticles,
            fallingLeaves = fallingLeaves,
            dustCloudAlpha = dustCloudAlpha
        )
        
        // Dessiner l'oiseau
        birdRenderer.draw(canvas, animationData)
        
        // Dessiner la jauge de vent
        windGauge.draw(
            canvas,
            currentWindForce,
            windForceSmoothed,
            sustainedWindTime,
            EXTREME_WIND_THRESHOLD,
            EXTREME_WIND_DURATION
        )
        
        canvas.restore()
    }
    
    // ==================== FONCTIONS UTILITAIRES ====================
    
    private fun startFalling() {
        // FORCER la chute peu importe l'état
        birdState = BirdState.FALLING
        fallAnimationTime = 0f
        fallRotation = 0f
        fallPositionY = birdCenterY
    }
    
    fun reset() {
        birdState = BirdState.PERCHED
        currentWindForce = 0f
        windForceSmoothed = 0f
        sustainedWindTime = 0f
        extremeWindStartTime = 0f
        
        // Reset animations
        eyeSquintLevel = 0f
        eyeRollAngle = 0f
        bodyLeanAngle = 0f
        wingOpenness = 0f
        tailCounterbalance = 0f
        footSlipProgress = 0f
        cheekPuffLevel = 0f
        beakOpenness = 0f
        tongueOut = 0f
        branchVibrateIntensity = 0f
        branchOscillateAngle = 0f
        fallPositionY = birdCenterY
        respawnTimer = 0f
        impactEffectTime = 0f
        screenShakeIntensity = 0f
        
        // Clear particles
        tears.clear()
        flyingFeathers.clear()
        dustParticles.clear()
        fallingLeaves.clear()
    }
    
    fun cleanup() {
        // Nettoyer les ressources si nécessaire
        birdRenderer.cleanup()
        windGauge.cleanup()
    }
    
    fun getCurrentState(): String {
        return "État: $birdState, Vent: ${(currentWindForce * 100).toInt()}%/${(windForceSmoothed * 100).toInt()}%, Soutenu: ${(sustainedWindTime / 1000f).toInt()}s, Seuil: ${(EXTREME_WIND_THRESHOLD * 100).toInt()}%"
    }
    
    private fun lerp(start: Float, end: Float, factor: Float): Float {
        return start + factor * (end - start)
    }
}

// ==================== CLASSE DE DONNÉES POUR ANIMATIONS ====================

data class BirdAnimationData(
    val birdState: BirdAnimationManager.BirdState,
    val bodyLeanAngle: Float,
    val fallPositionY: Float,
    val fallRotation: Float,
    val wingOpenness: Float,
    val eyeSquintLevel: Float,
    val eyeRollAngle: Float,
    val cheekPuffLevel: Float,
    val beakOpenness: Float,
    val tongueOut: Float,
    val headCrestHeight: Float,
    val featherWavePhase: Float,
    val tailCounterbalance: Float,
    val footSlipProgress: Float,
    val branchVibrateIntensity: Float,
    val branchOscillateAngle: Float,
    val respawnTimer: Float,
    val tears: List<BirdAnimationManager.Tear>,
    val flyingFeathers: List<BirdAnimationManager.FlyingFeather>,
    val dustParticles: List<BirdAnimationManager.DustParticle>,
    val fallingLeaves: List<BirdAnimationManager.FallingLeaf>,
    val dustCloudAlpha: Float
)
