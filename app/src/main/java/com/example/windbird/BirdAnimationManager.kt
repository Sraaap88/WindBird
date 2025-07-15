package com.example.windbird

import android.graphics.*
import kotlin.math.*
import kotlin.random.Random

class BirdAnimationManager(private val screenWidth: Int, private val screenHeight: Int) {
    
    // ==================== PROPRIÉTÉS DE BASE ====================
    
    private val birdSize = screenWidth * 0.4f // 40% de l'écran
    private val birdCenterX = screenWidth / 2f
    private val birdCenterY = screenHeight * 0.45f
    
    // Branche
    private val branchY = birdCenterY + birdSize * 0.6f
    private val branchStartX = screenWidth * 0.1f
    private val branchEndX = screenWidth * 0.9f
    
    // ==================== ÉTAT DU VENT ====================
    
    private var currentWindForce = 0f
    private var windForceSmoothed = 0f
    private var sustainedWindTime = 0f
    private var extremeWindStartTime = 0f
    private val EXTREME_WIND_THRESHOLD = 0.85f
    private val EXTREME_WIND_DURATION = 3000f // 3 secondes
    
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
    private var fallPositionY = birdCenterY
    private var respawnTimer = 0f
    private var impactEffectTime = 0f
    private var screenShakeIntensity = 0f
    
    enum class BirdState {
        PERCHED, FALLING, FALLEN, RESPAWNING
    }
    
    // ==================== CLASSES POUR EFFETS ====================
    
    private data class Tear(var x: Float, var y: Float, var velocityX: Float, var velocityY: Float, var life: Float)
    private data class FlyingFeather(var x: Float, var y: Float, var vx: Float, var vy: Float, var rotation: Float, var life: Float)
    private data class DustParticle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, var size: Float)
    private data class FallingLeaf(var x: Float, var y: Float, var vx: Float, var vy: Float, var rotation: Float, var life: Float)
    
    // ==================== COULEURS ET PINCEAUX ====================
    
    private val bodyPaint = Paint().apply {
        color = Color.rgb(139, 69, 19) // Brun
        isAntiAlias = true
    }
    
    private val bellyPaint = Paint().apply {
        color = Color.rgb(255, 228, 181) // Beige clair
        isAntiAlias = true
    }
    
    private val eyePaint = Paint().apply {
        color = Color.BLACK
        isAntiAlias = true
    }
    
    private val eyeWhitePaint = Paint().apply {
        color = Color.WHITE
        isAntiAlias = true
    }
    
    private val beakPaint = Paint().apply {
        color = Color.rgb(255, 140, 0) // Orange
        isAntiAlias = true
    }
    
    private val branchPaint = Paint().apply {
        color = Color.rgb(101, 67, 33) // Brun foncé
        isAntiAlias = true
        strokeWidth = 15f
        style = Paint.Style.STROKE
    }
    
    private val featherPaint = Paint().apply {
        color = Color.rgb(160, 82, 45) // Brun selle
        isAntiAlias = true
    }
    
    // ==================== MISE À JOUR PRINCIPALE ====================
    
    fun updateWind(windForce: Float) {
        currentWindForce = windForce
        
        // Lissage du vent pour éviter les saccades
        windForceSmoothed = lerp(windForceSmoothed, windForce, 0.15f)
        
        // Gestion du vent extrême soutenu
        if (windForceSmoothed >= EXTREME_WIND_THRESHOLD) {
            if (sustainedWindTime == 0f) {
                extremeWindStartTime = System.currentTimeMillis().toFloat()
            }
            sustainedWindTime = System.currentTimeMillis() - extremeWindStartTime
            
            // Déclencher la chute après 3 secondes de vent extrême
            if (sustainedWindTime >= EXTREME_WIND_DURATION && birdState == BirdState.PERCHED) {
                startFalling()
            }
        } else {
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
        
        // Animation des yeux
        eyeSquintLevel = force
        blinkSpeed = 1f + force * 3f
        blinkPhase += deltaTime * blinkSpeed
        
        if (force > 0.6f) {
            eyeRollAngle = (force - 0.6f) * 180f
            
            // Génération de larmes
            if (Random.nextFloat() < force * 0.1f) {
                val eyeX = birdCenterX + (Random.nextFloat() - 0.5f) * birdSize * 0.3f
                val eyeY = birdCenterY - birdSize * 0.1f
                tears.add(Tear(eyeX, eyeY, Random.nextFloat() * 100f - 50f, -Random.nextFloat() * 50f, 1f))
            }
        }
        
        // Animation des plumes
        featherWavePhase += deltaTime * (1f + force * 2f)
        featherRuffleIntensity = force
        headCrestHeight = min(force * 1.5f, 1f)
        
        // Plumes qui s'envolent
        if (force > 0.4f && Random.nextFloat() < force * 0.05f) {
            val featherX = birdCenterX + (Random.nextFloat() - 0.5f) * birdSize
            val featherY = birdCenterY + (Random.nextFloat() - 0.5f) * birdSize * 0.8f
            flyingFeathers.add(FlyingFeather(
                featherX, featherY,
                Random.nextFloat() * 200f - 100f,
                -Random.nextFloat() * 100f - 50f,
                Random.nextFloat() * 360f, 1f
            ))
        }
        
        // Animation du corps
        bodyLeanAngle = force * 25f
        wingOpenness = max(0f, force - 0.3f)
        tailCounterbalance = force * 30f
        footSlipProgress = max(0f, force - 0.5f) * 2f
        
        if (force > 0.7f) {
            cheekPuffLevel = (force - 0.7f) * 3.33f
            beakOpenness = force
            tongueOut = max(0f, force - 0.8f) * 5f
        }
        
        // Nuage de poussière
        if (force > 0.8f) {
            dustCloudAlpha = (force - 0.8f) * 5f
            if (Random.nextFloat() < 0.3f) {
                dustParticles.add(DustParticle(
                    birdCenterX + Random.nextFloat() * 100f - 50f,
                    branchY + Random.nextFloat() * 20f,
                    Random.nextFloat() * 100f - 50f,
                    -Random.nextFloat() * 50f,
                    1f, Random.nextFloat() * 10f + 5f
                ))
            }
        }
        
        // Animation de la branche
        branchVibrateIntensity = force * 5f
        branchOscillateAngle = sin(System.currentTimeMillis() * 0.01f) * force * 3f
        
        // Feuilles qui tombent
        if (force > 0.5f && Random.nextFloat() < force * 0.03f) {
            fallingLeaves.add(FallingLeaf(
                Random.nextFloat() * screenWidth,
                0f,
                Random.nextFloat() * 100f - 50f,
                Random.nextFloat() * 100f + 50f,
                Random.nextFloat() * 360f, 1f
            ))
        }
    }
    
    private fun updateFallingAnimations(deltaTime: Float) {
        fallAnimationTime += deltaTime
        
        // Animation de chute
        fallRotation += deltaTime * 720f // 2 tours par seconde
        fallPositionY += deltaTime * 800f // Vitesse de chute
        
        // Ailes qui battent frénétiquement
        wingOpenness = 1f + sin(fallAnimationTime * 20f) * 0.3f
        
        // Yeux paniqués
        eyeSquintLevel = 0f
        eyeRollAngle = 0f
        
        // Impact au sol
        if (fallPositionY >= screenHeight - 100f) {
            birdState = BirdState.FALLEN
            impactEffectTime = System.currentTimeMillis().toFloat()
            screenShakeIntensity = 1f
            
            // Explosion de poussière
            for (i in 0..20) {
                dustParticles.add(DustParticle(
                    birdCenterX + Random.nextFloat() * 200f - 100f,
                    screenHeight - 50f,
                    Random.nextFloat() * 300f - 150f,
                    -Random.nextFloat() * 200f - 100f,
                    1f, Random.nextFloat() * 15f + 10f
                ))
            }
        }
    }
    
    private fun updateFallenState(deltaTime: Float) {
        respawnTimer += deltaTime
        screenShakeIntensity = max(0f, screenShakeIntensity - deltaTime * 2f)
        
        if (respawnTimer >= 2f) {
            birdState = BirdState.RESPAWNING
            respawnTimer = 0f
        }
    }
    
    private fun updateRespawning(deltaTime: Float) {
        respawnTimer += deltaTime
        
        if (respawnTimer >= 1f) {
            reset()
        }
    }
    
    private fun updateParticleEffects(deltaTime: Float) {
        // Mise à jour des larmes
        tears.removeAll { tear ->
            tear.x += tear.velocityX * deltaTime
            tear.y += tear.velocityY * deltaTime
            tear.velocityY += 200f * deltaTime // Gravité
            tear.life -= deltaTime
            tear.life <= 0f
        }
        
        // Mise à jour des plumes volantes
        flyingFeathers.removeAll { feather ->
            feather.x += feather.vx * deltaTime
            feather.y += feather.vy * deltaTime
            feather.rotation += 180f * deltaTime
            feather.life -= deltaTime
            feather.life <= 0f
        }
        
        // Mise à jour des particules de poussière
        dustParticles.removeAll { particle ->
            particle.x += particle.vx * deltaTime
            particle.y += particle.vy * deltaTime
            particle.life -= deltaTime
            particle.life <= 0f
        }
        
        // Mise à jour des feuilles qui tombent
        fallingLeaves.removeAll { leaf ->
            leaf.x += leaf.vx * deltaTime
            leaf.y += leaf.vy * deltaTime
            leaf.rotation += 90f * deltaTime
            leaf.life -= deltaTime
            leaf.life <= 0f || leaf.y > screenHeight
        }
    }
    
    // ==================== DESSIN ====================
    
    fun draw(canvas: Canvas) {
        val shakeX = if (screenShakeIntensity > 0) Random.nextFloat() * screenShakeIntensity * 20f - 10f else 0f
        val shakeY = if (screenShakeIntensity > 0) Random.nextFloat() * screenShakeIntensity * 20f - 10f else 0f
        
        canvas.save()
        canvas.translate(shakeX, shakeY)
        
        when (birdState) {
            BirdState.PERCHED -> drawPerchedBird(canvas)
            BirdState.FALLING -> drawFallingBird(canvas)
            BirdState.FALLEN -> drawImpactEffect(canvas)
            BirdState.RESPAWNING -> drawRespawningBird(canvas)
        }
        
        drawParticleEffects(canvas)
        canvas.restore()
    }
    
    private fun drawPerchedBird(canvas: Canvas) {
        canvas.save()
        
        // Appliquer l'inclinaison du corps
        canvas.translate(birdCenterX, birdCenterY)
        canvas.rotate(bodyLeanAngle)
        canvas.translate(-birdCenterX, -birdCenterY)
        
        drawBranch(canvas)
        drawBirdBody(canvas)
        drawBirdHead(canvas)
        drawEyes(canvas)
        drawBeak(canvas)
        drawWings(canvas)
        drawTail(canvas)
        drawFeet(canvas)
        
        canvas.restore()
    }
    
    private fun drawFallingBird(canvas: Canvas) {
        canvas.save()
        canvas.translate(birdCenterX, fallPositionY)
        canvas.rotate(fallRotation)
        canvas.translate(-birdCenterX, -fallPositionY)
        
        drawBirdBody(canvas)
        drawBirdHead(canvas)
        drawEyes(canvas)
        drawBeak(canvas)
        drawWings(canvas)
        drawTail(canvas)
        
        canvas.restore()
    }
    
    private fun drawBirdBody(canvas: Canvas) {
        val bodyRect = RectF(
            birdCenterX - birdSize * 0.3f,
            birdCenterY - birdSize * 0.2f,
            birdCenterX + birdSize * 0.3f,
            birdCenterY + birdSize * 0.3f
        )
        
        // Corps principal
        canvas.drawOval(bodyRect, bodyPaint)
        
        // Ventre
        val bellyRect = RectF(
            birdCenterX - birdSize * 0.2f,
            birdCenterY - birdSize * 0.1f,
            birdCenterX + birdSize * 0.2f,
            birdCenterY + birdSize * 0.2f
        )
        canvas.drawOval(bellyRect, bellyPaint)
    }
    
    private fun drawBirdHead(canvas: Canvas) {
        val headRadius = birdSize * 0.25f
        val headY = birdCenterY - birdSize * 0.15f
        
        // Joues gonflées
        if (cheekPuffLevel > 0f) {
            val puffSize = headRadius * (1f + cheekPuffLevel * 0.3f)
            canvas.drawCircle(birdCenterX, headY, puffSize, bellyPaint)
        }
        
        canvas.drawCircle(birdCenterX, headY, headRadius, bodyPaint)
        
        // Crête de plumes
        if (headCrestHeight > 0f) {
            drawHeadCrest(canvas, birdCenterX, headY - headRadius)
        }
    }
    
    private fun drawHeadCrest(canvas: Canvas, centerX: Float, topY: Float) {
        val crestHeight = headCrestHeight * birdSize * 0.15f
        for (i in -2..2) {
            val x = centerX + i * birdSize * 0.05f
            val waveOffset = sin(featherWavePhase + i * 0.5f) * birdSize * 0.02f
            canvas.drawLine(x, topY, x + waveOffset, topY - crestHeight, featherPaint)
        }
    }
    
    private fun drawEyes(canvas: Canvas) {
        val eyeRadius = birdSize * 0.08f
        val eyeY = birdCenterY - birdSize * 0.18f
        val eyeSpacing = birdSize * 0.12f
        
        for (side in arrayOf(-1, 1)) {
            val eyeX = birdCenterX + side * eyeSpacing
            
            // Blanc de l'œil
            val squintFactor = 1f - eyeSquintLevel * 0.7f
            canvas.drawCircle(eyeX, eyeY, eyeRadius * squintFactor, eyeWhitePaint)
            
            // Pupille avec roulement des yeux
            val pupilOffset = eyeRollAngle * 0.01f
            canvas.drawCircle(
                eyeX + pupilOffset * side,
                eyeY + pupilOffset,
                eyeRadius * 0.6f * squintFactor,
                eyePaint
            )
        }
    }
    
    private fun drawBeak(canvas: Canvas) {
        val beakY = birdCenterY - birdSize * 0.05f
        val beakWidth = birdSize * 0.1f
        val beakHeight = birdSize * 0.08f * (1f + beakOpenness * 0.5f)
        
        val beakPath = Path()
        beakPath.moveTo(birdCenterX, beakY)
        beakPath.lineTo(birdCenterX - beakWidth, beakY + beakHeight)
        beakPath.lineTo(birdCenterX + beakWidth, beakY + beakHeight)
        beakPath.close()
        
        canvas.drawPath(beakPath, beakPaint)
        
        // Langue qui sort
        if (tongueOut > 0f) {
            val tonguePaint = Paint().apply {
                color = Color.MAGENTA
                isAntiAlias = true
            }
            canvas.drawCircle(
                birdCenterX,
                beakY + beakHeight + tongueOut * birdSize * 0.05f,
                birdSize * 0.02f,
                tonguePaint
            )
        }
    }
    
    private fun drawWings(canvas: Canvas) {
        if (wingOpenness <= 0f) return
        
        val wingWidth = birdSize * 0.4f * wingOpenness
        val wingHeight = birdSize * 0.3f
        
        for (side in arrayOf(-1, 1)) {
            val wingX = birdCenterX + side * birdSize * 0.3f
            val wingRect = RectF(
                wingX - wingWidth * 0.5f,
                birdCenterY - wingHeight * 0.5f,
                wingX + wingWidth * 0.5f,
                birdCenterY + wingHeight * 0.5f
            )
            canvas.drawOval(wingRect, featherPaint)
        }
    }
    
    private fun drawTail(canvas: Canvas) {
        canvas.save()
        canvas.translate(birdCenterX, birdCenterY + birdSize * 0.3f)
        canvas.rotate(tailCounterbalance)
        
        val tailWidth = birdSize * 0.15f
        val tailHeight = birdSize * 0.25f
        
        val tailRect = RectF(-tailWidth, 0f, tailWidth, tailHeight)
        canvas.drawOval(tailRect, featherPaint)
        
        canvas.restore()
    }
    
    private fun drawFeet(canvas: Canvas) {
        val footY = branchY
        val footSpacing = birdSize * 0.15f
        val slipOffset = footSlipProgress * birdSize * 0.1f
        
        for (side in arrayOf(-1, 1)) {
            val footX = birdCenterX + side * footSpacing + side * slipOffset
            
            // Pied
            canvas.drawCircle(footX, footY, birdSize * 0.03f, beakPaint)
            
            // Griffes
            for (i in 0..2) {
                val clawAngle = (i - 1) * 20f
                val clawLength = birdSize * 0.05f
                val endX = footX + sin(Math.toRadians(clawAngle.toDouble())).toFloat() * clawLength
                val endY = footY + cos(Math.toRadians(clawAngle.toDouble())).toFloat() * clawLength
                canvas.drawLine(footX, footY, endX, endY, eyePaint)
            }
        }
    }
    
    private fun drawBranch(canvas: Canvas) {
        canvas.save()
        
        // Vibration de la branche
        val vibrateOffset = sin(System.currentTimeMillis() * 0.05f) * branchVibrateIntensity
        canvas.translate(0f, vibrateOffset)
        canvas.rotate(branchOscillateAngle, screenWidth / 2f, branchY)
        
        canvas.drawLine(branchStartX, branchY, branchEndX, branchY, branchPaint)
        
        canvas.restore()
    }
    
    private fun drawParticleEffects(canvas: Canvas) {
        // Larmes
        val tearPaint = Paint().apply {
            color = Color.CYAN
            isAntiAlias = true
        }
        tears.forEach { tear ->
            canvas.drawCircle(tear.x, tear.y, 5f, tearPaint)
        }
        
        // Plumes volantes
        flyingFeathers.forEach { feather ->
            canvas.save()
            canvas.translate(feather.x, feather.y)
            canvas.rotate(feather.rotation)
            canvas.drawOval(-8f, -15f, 8f, 15f, featherPaint)
            canvas.restore()
        }
        
        // Particules de poussière
        val dustPaint = Paint().apply {
            color = Color.argb((dustCloudAlpha * 100).toInt(), 139, 69, 19)
            isAntiAlias = true
        }
        dustParticles.forEach { particle ->
            canvas.drawCircle(particle.x, particle.y, particle.size, dustPaint)
        }
        
        // Feuilles qui tombent
        val leafPaint = Paint().apply {
            color = Color.GREEN
            isAntiAlias = true
        }
        fallingLeaves.forEach { leaf ->
            canvas.save()
            canvas.translate(leaf.x, leaf.y)
            canvas.rotate(leaf.rotation)
            canvas.drawOval(-5f, -10f, 5f, 10f, leafPaint)
            canvas.restore()
        }
    }
    
    private fun drawImpactEffect(canvas: Canvas) {
        // Effet d'impact avec nuage de poussière
        val impactPaint = Paint().apply {
            color = Color.argb(100, 139, 69, 19)
            isAntiAlias = true
        }
        canvas.drawCircle(birdCenterX, screenHeight - 50f, 100f, impactPaint)
    }
    
    private fun drawRespawningBird(canvas: Canvas) {
        // Nouvel oiseau qui descend du haut
        val respawnY = lerp(-birdSize, birdCenterY, respawnTimer)
        
        canvas.save()
        canvas.translate(birdCenterX, respawnY)
        
        drawBirdBody(canvas)
        drawBirdHead(canvas)
        drawEyes(canvas)
        drawBeak(canvas)
        
        canvas.restore()
        
        // Redessiner la branche
        canvas.drawLine(branchStartX, branchY, branchEndX, branchY, branchPaint)
    }
    
    // ==================== FONCTIONS UTILITAIRES ====================
    
    private fun startFalling() {
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
    }
    
    fun getCurrentState(): String {
        return "État: $birdState, Vent: ${(windForceSmoothed * 100).toInt()}%, Soutenu: ${(sustainedWindTime / 1000f).toInt()}s"
    }
    
    private fun lerp(start: Float, end: Float, factor: Float): Float {
        return start + factor * (end - start)
    }
}