private fun startFalling() {
        // FORCER la chute même si on est déjà en trainpackage com.example.windbird

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
    
    private data class Tear(var x: Float, var y: Float, var velocityX: Float, var velocityY: Float, var life: Float)
    private data class FlyingFeather(var x: Float, var y: Float, var vx: Float, var vy: Float, var rotation: Float, var life: Float)
    private data class DustParticle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, var size: Float)
    private data class FallingLeaf(var x: Float, var y: Float, var vx: Float, var vy: Float, var rotation: Float, var life: Float)
    
    // ==================== COULEURS ET PINCEAUX AMÉLIORÉS ====================
    
    // Corps avec dégradé
    private val bodyPaint = Paint().apply {
        isAntiAlias = true
        shader = RadialGradient(
            0f, 0f, birdSize * 0.3f,
            Color.rgb(160, 82, 45), Color.rgb(101, 67, 33),
            Shader.TileMode.CLAMP
        )
    }
    
    // Ventre doux
    private val bellyPaint = Paint().apply {
        color = Color.rgb(255, 248, 220) // Crème
        isAntiAlias = true
    }
    
    // Yeux expressifs
    private val eyePaint = Paint().apply {
        color = Color.rgb(20, 20, 20)
        isAntiAlias = true
    }
    
    private val eyeWhitePaint = Paint().apply {
        color = Color.rgb(255, 255, 250)
        isAntiAlias = true
    }
    
    // Reflet dans les yeux
    private val eyeShimmerPaint = Paint().apply {
        color = Color.rgb(255, 255, 255)
        isAntiAlias = true
    }
    
    // Bec avec dégradé
    private val beakPaint = Paint().apply {
        isAntiAlias = true
        shader = LinearGradient(
            0f, 0f, birdSize * 0.1f, birdSize * 0.08f,
            Color.rgb(255, 165, 0), Color.rgb(255, 140, 0),
            Shader.TileMode.CLAMP
        )
    }
    
    // Branche texturée
    private val branchPaint = Paint().apply {
        color = Color.rgb(92, 51, 23)
        isAntiAlias = true
        strokeWidth = 25f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    
    // Détails de la branche
    private val branchDetailPaint = Paint().apply {
        color = Color.rgb(139, 69, 19)
        isAntiAlias = true
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }
    
    // Plumes détaillées
    private val featherPaint = Paint().apply {
        color = Color.rgb(139, 90, 43)
        isAntiAlias = true
    }
    
    private val featherDetailPaint = Paint().apply {
        color = Color.rgb(101, 67, 33)
        isAntiAlias = true
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    
    // Ombres douces
    private val shadowPaint = Paint().apply {
        color = Color.argb(50, 0, 0, 0)
        isAntiAlias = true
        maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
    }
    
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
    
    // ==================== DESSIN AMÉLIORÉ ====================
    
    fun draw(canvas: Canvas) {
        val shakeX = if (screenShakeIntensity > 0) Random.nextFloat() * screenShakeIntensity * 15f - 7.5f else 0f
        val shakeY = if (screenShakeIntensity > 0) Random.nextFloat() * screenShakeIntensity * 15f - 7.5f else 0f
        
        canvas.save()
        canvas.translate(shakeX, shakeY)
        
        when (birdState) {
            BirdState.PERCHED -> drawPerchedBird(canvas)
            BirdState.FALLING -> drawFallingBird(canvas)
            BirdState.FALLEN -> drawImpactEffect(canvas)
            BirdState.RESPAWNING -> drawRespawningBird(canvas)
        }
        
        drawParticleEffects(canvas)
        drawWindGauge(canvas) // NOUVELLE JAUGE !
        canvas.restore()
    }
    
    private fun drawPerchedBird(canvas: Canvas) {
        canvas.save()
        
        // Appliquer l'inclinaison du corps
        canvas.translate(birdCenterX, birdCenterY)
        canvas.rotate(bodyLeanAngle)
        canvas.translate(-birdCenterX, -birdCenterY)
        
        drawBranch(canvas)
        drawBirdShadow(canvas)
        drawFeet(canvas)
        drawTail(canvas)
        drawBirdBody(canvas)
        drawWings(canvas)
        drawBirdHead(canvas)
        drawEyes(canvas)
        drawBeak(canvas)
        
        canvas.restore()
    }
    
    private fun drawFallingBird(canvas: Canvas) {
        canvas.save()
        canvas.translate(birdCenterX, fallPositionY)
        canvas.rotate(fallRotation)
        canvas.translate(-birdCenterX, -fallPositionY)
        
        drawBirdShadow(canvas)
        drawTail(canvas)
        drawBirdBody(canvas)
        drawWings(canvas)
        drawBirdHead(canvas)
        drawEyes(canvas)
        drawBeak(canvas)
        
        canvas.restore()
    }
    
    private fun drawBirdShadow(canvas: Canvas) {
        // Ombre douce sous l'oiseau
        val shadowOffset = 15f
        canvas.drawOval(
            birdCenterX - birdSize * 0.35f,
            birdCenterY - birdSize * 0.1f + shadowOffset,
            birdCenterX + birdSize * 0.35f,
            birdCenterY + birdSize * 0.35f + shadowOffset,
            shadowPaint
        )
    }
    
    private fun drawBirdBody(canvas: Canvas) {
        val centerX = birdCenterX
        val centerY = birdCenterY
        val baseWidth = birdSize * 0.35f
        val baseHeight = birdSize * 0.3f
        
        // Corps principal avec forme organique (path courbe)
        val bodyPath = Path()
        
        // Forme de goutte/oeuf naturelle
        bodyPath.moveTo(centerX, centerY - baseHeight * 0.8f) // Haut
        bodyPath.cubicTo(
            centerX + baseWidth * 0.8f, centerY - baseHeight * 0.6f, // Contrôle haut droit
            centerX + baseWidth, centerY + baseHeight * 0.2f,        // Contrôle milieu droit
            centerX + baseWidth * 0.6f, centerY + baseHeight * 1.2f  // Bas droit
        )
        bodyPath.cubicTo(
            centerX + baseWidth * 0.2f, centerY + baseHeight * 1.4f, // Contrôle bas
            centerX - baseWidth * 0.2f, centerY + baseHeight * 1.4f, // Contrôle bas
            centerX - baseWidth * 0.6f, centerY + baseHeight * 1.2f  // Bas gauche
        )
        bodyPath.cubicTo(
            centerX - baseWidth, centerY + baseHeight * 0.2f,        // Contrôle milieu gauche
            centerX - baseWidth * 0.8f, centerY - baseHeight * 0.6f, // Contrôle haut gauche
            centerX, centerY - baseHeight * 0.8f                     // Retour au haut
        )
        bodyPath.close()
        
        // Ombre du corps
        canvas.save()
        canvas.translate(4f, 4f)
        canvas.drawPath(bodyPath, shadowPaint)
        canvas.restore()
        
        // Corps principal
        canvas.drawPath(bodyPath, bodyPaint)
        
        // Ventre avec forme naturelle
        val bellyPath = Path()
        val bellyWidth = baseWidth * 0.6f
        val bellyHeight = baseHeight * 0.8f
        
        bellyPath.moveTo(centerX, centerY - bellyHeight * 0.2f)
        bellyPath.cubicTo(
            centerX + bellyWidth * 0.7f, centerY - bellyHeight * 0.1f,
            centerX + bellyWidth * 0.8f, centerY + bellyHeight * 0.8f,
            centerX, centerY + bellyHeight * 1.1f
        )
        bellyPath.cubicTo(
            centerX - bellyWidth * 0.8f, centerY + bellyHeight * 0.8f,
            centerX - bellyWidth * 0.7f, centerY - bellyHeight * 0.1f,
            centerX, centerY - bellyHeight * 0.2f
        )
        bellyPath.close()
        
        canvas.drawPath(bellyPath, bellyPaint)
        
        // Motifs de plumage organiques
        drawOrganicFeatherPattern(canvas, centerX, centerY, baseWidth, baseHeight)
    }
    
    private fun drawOrganicFeatherPattern(canvas: Canvas, centerX: Float, centerY: Float, width: Float, height: Float) {
        // Motifs de plumes stylisés avec courbes naturelles
        val patternPaint = Paint().apply {
            color = Color.argb(80, 101, 67, 33)
            isAntiAlias = true
            strokeWidth = 3f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        
        // Plumes latérales courbes
        for (side in arrayOf(-1, 1)) {
            for (i in 0..3) {
                val startX = centerX + side * width * 0.6f
                val startY = centerY - height * 0.3f + i * height * 0.3f
                
                val path = Path()
                path.moveTo(startX, startY)
                path.quadTo(
                    startX + side * width * 0.3f, startY + height * 0.1f,
                    startX + side * width * 0.2f, startY + height * 0.2f
                )
                canvas.drawPath(path, patternPaint)
            }
        }
        
        // Plumes du dos
        for (i in 0..4) {
            val x = centerX - width * 0.3f + i * width * 0.15f
            val y = centerY - height * 0.5f
            val wave = sin(featherWavePhase + i * 0.5f) * 5f
            
            val featherPath = Path()
            featherPath.moveTo(x, y)
            featherPath.quadTo(x + wave, y - height * 0.1f, x + wave * 0.5f, y - height * 0.15f)
            canvas.drawPath(featherPath, patternPaint)
        }
    }
    
    private fun drawBirdHead(canvas: Canvas) {
        val headRadius = birdSize * 0.28f
        val headY = birdCenterY - birdSize * 0.18f
        
        // Forme de tête organique au lieu d'un cercle parfait
        val headPath = Path()
        headPath.moveTo(birdCenterX, headY - headRadius)
        headPath.cubicTo(
            birdCenterX + headRadius * 0.9f, headY - headRadius * 0.8f,
            birdCenterX + headRadius, headY + headRadius * 0.2f,
            birdCenterX + headRadius * 0.7f, headY + headRadius * 0.9f
        )
        headPath.cubicTo(
            birdCenterX + headRadius * 0.3f, headY + headRadius * 1.1f,
            birdCenterX - headRadius * 0.3f, headY + headRadius * 1.1f,
            birdCenterX - headRadius * 0.7f, headY + headRadius * 0.9f
        )
        headPath.cubicTo(
            birdCenterX - headRadius, headY + headRadius * 0.2f,
            birdCenterX - headRadius * 0.9f, headY - headRadius * 0.8f,
            birdCenterX, headY - headRadius
        )
        headPath.close()
        
        // Ombre de la tête
        canvas.save()
        canvas.translate(3f, 3f)
        canvas.drawPath(headPath, shadowPaint)
        canvas.restore()
        
        // Joues gonflées avec forme naturelle
        if (cheekPuffLevel > 0f) {
            val puffPath = Path()
            val puffRadius = headRadius * (1f + cheekPuffLevel * 0.4f)
            
            puffPath.addCircle(birdCenterX, headY, puffRadius, Path.Direction.CW)
            canvas.drawPath(puffPath, bellyPaint)
        }
        
        // Tête principale
        canvas.drawPath(headPath, bodyPaint)
        
        // Crête de plumes plus naturelle
        if (headCrestHeight > 0f) {
            drawNaturalCrest(canvas, birdCenterX, headY - headRadius * 0.9f)
        }
        
        // Motifs décoratifs sur la tête plus stylisés
        drawHeadMarkings(canvas, birdCenterX, headY, headRadius)
    }
    
    private fun drawNaturalCrest(canvas: Canvas, centerX: Float, topY: Float) {
        val crestHeight = headCrestHeight * birdSize * 0.25f
        val featherCount = 9
        
        for (i in -(featherCount/2)..(featherCount/2)) {
            val baseX = centerX + i * birdSize * 0.03f
            val waveOffset = sin(featherWavePhase + i * 0.7f) * birdSize * 0.04f
            val individualHeight = crestHeight * (1f - abs(i) * 0.08f)
            
            // Plume avec forme courbe naturelle
            val featherPath = Path()
            featherPath.moveTo(baseX, topY)
            featherPath.quadTo(
                baseX + waveOffset * 0.5f, topY - individualHeight * 0.6f,
                baseX + waveOffset, topY - individualHeight
            )
            
            // Épaisseur de plume variable
            val featherPaint = Paint().apply {
                color = Color.rgb(139 + i * 2, 90 + i, 43)
                isAntiAlias = true
                strokeWidth = 4f - abs(i) * 0.3f
                strokeCap = Paint.Cap.ROUND
                style = Paint.Style.STROKE
            }
            
            canvas.drawPath(featherPath, featherPaint)
            
            // Détails de barbules sur chaque plume
            if (abs(i) < 3) {
                val barbuleY = topY - individualHeight * 0.3f
                val barbulePath = Path()
                barbulePath.moveTo(baseX - 2f, barbuleY)
                barbulePath.lineTo(baseX + 2f, barbuleY - 3f)
                
                val barbulePaint = Paint().apply {
                    color = Color.argb(150, 101, 67, 33)
                    strokeWidth = 1f
                    isAntiAlias = true
                    style = Paint.Style.STROKE
                }
                canvas.drawPath(barbulePath, barbulePaint)
            }
        }
    }
    
    private fun drawHeadMarkings(canvas: Canvas, centerX: Float, centerY: Float, radius: Float) {
        // Motifs stylisés plus artistiques
        val markingPaint = Paint().apply {
            color = Color.argb(120, 101, 67, 33)
            isAntiAlias = true
            strokeWidth = 2.5f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        
        // Motifs en forme de croissant près des yeux
        for (side in arrayOf(-1, 1)) {
            val crescentPath = Path()
            val startX = centerX + side * radius * 0.4f
            val startY = centerY - radius * 0.3f
            
            crescentPath.moveTo(startX, startY)
            crescentPath.quadTo(
                startX + side * radius * 0.2f, startY - radius * 0.1f,
                startX + side * radius * 0.15f, startY + radius * 0.1f
            )
            canvas.drawPath(crescentPath, markingPaint)
        }
        
        // Motif central décoratif
        val centralPath = Path()
        centralPath.moveTo(centerX, centerY - radius * 0.6f)
        centralPath.quadTo(centerX - radius * 0.1f, centerY - radius * 0.4f, centerX, centerY - radius * 0.3f)
        centralPath.quadTo(centerX + radius * 0.1f, centerY - radius * 0.4f, centerX, centerY - radius * 0.6f)
        
        val centralPaint = Paint().apply {
            color = Color.argb(80, 160, 82, 45)
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        canvas.drawPath(centralPath, centralPaint)
    }
    
    private fun drawEyes(canvas: Canvas) {
        val eyeRadius = birdSize * 0.09f
        val eyeY = birdCenterY - birdSize * 0.2f
        val eyeSpacing = birdSize * 0.14f
        
        for (side in arrayOf(-1, 1)) {
            val eyeX = birdCenterX + side * eyeSpacing
            
            // Ombre de l'œil
            canvas.drawCircle(eyeX + 2f, eyeY + 2f, eyeRadius, shadowPaint)
            
            // Blanc de l'œil avec forme plus réaliste
            val squintFactor = 1f - eyeSquintLevel * 0.6f
            val eyeHeight = eyeRadius * squintFactor
            
            canvas.drawOval(
                eyeX - eyeRadius, eyeY - eyeHeight,
                eyeX + eyeRadius, eyeY + eyeHeight,
                eyeWhitePaint
            )
            
            // Pupille avec mouvement plus naturel
            val pupilRadius = eyeRadius * 0.6f * squintFactor
            val pupilOffsetX = (eyeRollAngle * 0.008f) * side
            val pupilOffsetY = eyeRollAngle * 0.005f
            
            canvas.drawCircle(
                eyeX + pupilOffsetX,
                eyeY + pupilOffsetY,
                pupilRadius,
                eyePaint
            )
            
            // Reflet dans l'œil
            canvas.drawCircle(
                eyeX + pupilOffsetX - pupilRadius * 0.3f,
                eyeY + pupilOffsetY - pupilRadius * 0.3f,
                pupilRadius * 0.25f,
                eyeShimmerPaint
            )
            
            // Cils
            if (eyeSquintLevel < 0.5f) {
                drawEyelashes(canvas, eyeX, eyeY - eyeHeight, eyeRadius)
            }
        }
    }
    
    private fun drawEyelashes(canvas: Canvas, eyeX: Float, eyeY: Float, radius: Float) {
        val lashPaint = Paint().apply {
            color = Color.rgb(50, 50, 50)
            isAntiAlias = true
            strokeWidth = 1.5f
            strokeCap = Paint.Cap.ROUND
        }
        
        for (i in -2..2) {
            val angle = i * 15f
            val startX = eyeX + cos(Math.toRadians((angle + 270).toDouble())).toFloat() * radius
            val startY = eyeY + sin(Math.toRadians((angle + 270).toDouble())).toFloat() * radius * 0.3f
            val endX = startX + cos(Math.toRadians((angle + 270).toDouble())).toFloat() * radius * 0.3f
            val endY = startY + sin(Math.toRadians((angle + 270).toDouble())).toFloat() * radius * 0.3f
            
            canvas.drawLine(startX, startY, endX, endY, lashPaint)
        }
    }
    
    private fun drawBeak(canvas: Canvas) {
        val beakY = birdCenterY - birdSize * 0.05f
        val beakWidth = birdSize * 0.12f
        val beakHeight = birdSize * 0.1f * (1f + beakOpenness * 0.6f)
        
        // Ombre du bec
        val shadowPath = Path()
        shadowPath.moveTo(birdCenterX + 2f, beakY + 2f)
        shadowPath.lineTo(birdCenterX - beakWidth + 2f, beakY + beakHeight + 2f)
        shadowPath.lineTo(birdCenterX + beakWidth + 2f, beakY + beakHeight + 2f)
        shadowPath.close()
        canvas.drawPath(shadowPath, shadowPaint)
        
        // Bec principal avec forme plus naturelle
        val beakPath = Path()
        beakPath.moveTo(birdCenterX, beakY)
        beakPath.quadTo(birdCenterX - beakWidth * 0.7f, beakY + beakHeight * 0.3f, birdCenterX - beakWidth, beakY + beakHeight)
        beakPath.lineTo(birdCenterX + beakWidth, beakY + beakHeight)
        beakPath.quadTo(birdCenterX + beakWidth * 0.7f, beakY + beakHeight * 0.3f, birdCenterX, beakY)
        beakPath.close()
        
        canvas.drawPath(beakPath, beakPaint)
        
        // Détails du bec
        val detailPaint = Paint().apply {
            color = Color.rgb(200, 120, 0)
            isAntiAlias = true
            strokeWidth = 1f
        }
        canvas.drawLine(birdCenterX, beakY + beakHeight * 0.7f, birdCenterX, beakY + beakHeight, detailPaint)
        
        // Narines
        val nostrilPaint = Paint().apply {
            color = Color.rgb(150, 100, 0)
            isAntiAlias = true
        }
        canvas.drawCircle(birdCenterX - beakWidth * 0.2f, beakY + beakHeight * 0.3f, 2f, nostrilPaint)
        canvas.drawCircle(birdCenterX + beakWidth * 0.2f, beakY + beakHeight * 0.3f, 2f, nostrilPaint)
        
        // Langue qui sort plus détaillée
        if (tongueOut > 0f) {
            val tonguePaint = Paint().apply {
                color = Color.rgb(255, 100, 150)
                isAntiAlias = true
            }
            val tongueY = beakY + beakHeight + tongueOut * birdSize * 0.06f
            canvas.drawOval(
                birdCenterX - 4f, tongueY - 2f,
                birdCenterX + 4f, tongueY + 6f,
                tonguePaint
            )
        }
    }
    
    private fun drawWings(canvas: Canvas) {
        if (wingOpenness <= 0f) return
        
        val wingSpan = birdSize * 0.6f * wingOpenness
        val wingHeight = birdSize * 0.4f
        
        for (side in arrayOf(-1, 1)) {
            val wingBaseX = birdCenterX + side * birdSize * 0.32f
            val wingTipX = wingBaseX + side * wingSpan
            val wingY = birdCenterY
            
            // Aile avec forme organique (pas ovale)
            val wingPath = Path()
            wingPath.moveTo(wingBaseX, wingY - wingHeight * 0.3f)
            wingPath.cubicTo(
                wingBaseX + side * wingSpan * 0.3f, wingY - wingHeight * 0.5f,
                wingBaseX + side * wingSpan * 0.8f, wingY - wingHeight * 0.2f,
                wingTipX, wingY
            )
            wingPath.cubicTo(
                wingBaseX + side * wingSpan * 0.9f, wingY + wingHeight * 0.3f,
                wingBaseX + side * wingSpan * 0.4f, wingY + wingHeight * 0.4f,
                wingBaseX, wingY + wingHeight * 0.2f
            )
            wingPath.close()
            
            // Ombre de l'aile
            canvas.save()
            canvas.translate(3f, 3f)
            canvas.drawPath(wingPath, shadowPaint)
            canvas.restore()
            
            // Aile principale
            canvas.drawPath(wingPath, featherPaint)
            
            // Plumes primaires individuelles
            drawWingFeathers(canvas, wingBaseX, wingTipX, wingY, wingHeight, side)
        }
    }
    
    private fun drawWingFeathers(canvas: Canvas, baseX: Float, tipX: Float, wingY: Float, height: Float, side: Int) {
        val featherCount = 7
        val span = abs(tipX - baseX)
        
        for (i in 0 until featherCount) {
            val progress = i.toFloat() / (featherCount - 1)
            val featherX = baseX + side * span * progress
            val featherLength = height * (0.6f - progress * 0.3f)
            val featherY = wingY - height * 0.2f + i * height * 0.08f
            
            // Plume individuelle avec forme naturelle
            val featherPath = Path()
            featherPath.moveTo(featherX, featherY)
            featherPath.quadTo(
                featherX + side * featherLength * 0.3f, featherY - featherLength * 0.3f,
                featherX + side * featherLength * 0.8f, featherY - featherLength * 0.1f
            )
            featherPath.quadTo(
                featherX + side * featherLength, featherY,
                featherX + side * featherLength * 0.8f, featherY + featherLength * 0.1f
            )
            featherPath.quadTo(
                featherX + side * featherLength * 0.3f, featherY + featherLength * 0.3f,
                featherX, featherY
            )
            
            val featherPaint = Paint().apply {
                color = Color.argb(200 - i * 15, 139 - i * 5, 90 - i * 3, 43)
                isAntiAlias = true
                style = Paint.Style.FILL
            }
            
            canvas.drawPath(featherPath, featherPaint)
            
            // Rachis (tige centrale de la plume)
            val rachisPaint = Paint().apply {
                color = Color.argb(150, 80, 50, 25)
                strokeWidth = 1.5f
                isAntiAlias = true
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawLine(
                featherX, featherY,
                featherX + side * featherLength * 0.9f, featherY,
                rachisPaint
            )
        }
    }
    
    private fun drawTail(canvas: Canvas) {
        canvas.save()
        canvas.translate(birdCenterX, birdCenterY + birdSize * 0.35f)
        canvas.rotate(tailCounterbalance)
        
        val tailWidth = birdSize * 0.2f
        val tailLength = birdSize * 0.35f
        
        // Ombre de la queue
        canvas.save()
        canvas.translate(2f, 2f)
        drawTailFeathers(canvas, tailWidth, tailLength, shadowPaint)
        canvas.restore()
        
        // Queue avec plumes individuelles
        drawTailFeathers(canvas, tailWidth, tailLength, featherPaint)
        
        canvas.restore()
    }
    
    private fun drawTailFeathers(canvas: Canvas, width: Float, length: Float, paint: Paint) {
        val featherCount = 7
        
        for (i in 0 until featherCount) {
            val progress = (i - (featherCount - 1) / 2f) / (featherCount - 1) * 2f // -1 à 1
            val featherX = progress * width * 0.8f
            val featherLength = length * (1f - abs(progress) * 0.2f) // Plumes centrales plus longues
            
            // Forme de plume naturelle
            val featherPath = Path()
            featherPath.moveTo(featherX, 0f)
            featherPath.quadTo(
                featherX - width * 0.08f, featherLength * 0.3f,
                featherX - width * 0.05f, featherLength * 0.7f
            )
            featherPath.quadTo(
                featherX, featherLength,
                featherX + width * 0.05f, featherLength * 0.7f
            )
            featherPath.quadTo(
                featherX + width * 0.08f, featherLength * 0.3f,
                featherX, 0f
            )
            
            // Couleur variable par plume
            val featherPaint = Paint(paint).apply {
                if (paint != shadowPaint) {
                    color = Color.argb(
                        200,
                        139 + (i * 5),
                        90 + (i * 3),
                        43 + (i * 2)
                    )
                }
            }
            
            canvas.drawPath(featherPath, featherPaint)
            
            // Rachis de la plume
            if (paint != shadowPaint) {
                val rachisP = Paint().apply {
                    color = Color.argb(150, 80, 50, 25)
                    strokeWidth = 1.5f
                    isAntiAlias = true
                    strokeCap = Paint.Cap.ROUND
                }
                canvas.drawLine(featherX, 0f, featherX, featherLength * 0.9f, rachisP)
            }
        }
    }
    
    private fun drawFeet(canvas: Canvas) {
        val footY = branchY
        val footSpacing = birdSize * 0.18f
        val slipOffset = footSlipProgress * birdSize * 0.12f
        
        for (side in arrayOf(-1, 1)) {
            val footX = birdCenterX + side * footSpacing + side * slipOffset
            
            // Ombre du pied
            canvas.drawCircle(footX + 1f, footY + 1f, birdSize * 0.04f, shadowPaint)
            
            // Pied plus détaillé
            canvas.drawCircle(footX, footY, birdSize * 0.04f, beakPaint)
            
            // Griffes plus réalistes
            for (i in 0..2) {
                val clawAngle = (i - 1) * 25f
                val clawLength = birdSize * 0.06f
                val clawWidth = 2f
                
                val endX = footX + sin(Math.toRadians(clawAngle.toDouble())).toFloat() * clawLength
                val endY = footY + cos(Math.toRadians(clawAngle.toDouble())).toFloat() * clawLength
                
                val clawPaint = Paint().apply {
                    color = Color.rgb(100, 60, 30)
                    isAntiAlias = true
                    strokeWidth = clawWidth
                    strokeCap = Paint.Cap.ROUND
                }
                
                canvas.drawLine(footX, footY, endX, endY, clawPaint)
            }
        }
    }
    
    private fun drawBranch(canvas: Canvas) {
        canvas.save()
        
        // Vibration de la branche
        val vibrateOffset = sin(System.currentTimeMillis() * 0.06f) * branchVibrateIntensity
        canvas.translate(0f, vibrateOffset)
        canvas.rotate(branchOscillateAngle, screenWidth / 2f, branchY)
        
        // Ombre de la branche
        canvas.drawLine(branchStartX + 3f, branchY + 3f, branchEndX + 3f, branchY + 3f, shadowPaint)
        
        // Branche principale
        canvas.drawLine(branchStartX, branchY, branchEndX, branchY, branchPaint)
        
        // Détails d'écorce
        drawBarkDetails(canvas)
        
        canvas.restore()
    }
    
    private fun drawBarkDetails(canvas: Canvas) {
        val detailCount = 8
        for (i in 0 until detailCount) {
            val x = branchStartX + (i * (branchEndX - branchStartX) / detailCount)
            val variation = sin(i * 0.5f) * 3f
            
            canvas.drawLine(x, branchY - 8f + variation, x, branchY + 8f + variation, branchDetailPaint)
            
            // Petites irrégularités
            if (Random.nextFloat() < 0.3f) {
                canvas.drawCircle(x + Random.nextFloat() * 20f - 10f, branchY + Random.nextFloat() * 6f - 3f, 2f, branchDetailPaint)
            }
        }
    }
    
    private fun drawParticleEffects(canvas: Canvas) {
        // Larmes plus belles
        val tearPaint = Paint().apply {
            isAntiAlias = true
            shader = RadialGradient(0f, 0f, 8f, Color.CYAN, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        }
        tears.forEach { tear ->
            canvas.save()
            canvas.translate(tear.x, tear.y)
            canvas.drawCircle(0f, 0f, 6f * tear.life, tearPaint)
            canvas.restore()
        }
        
        // Plumes volantes plus détaillées
        flyingFeathers.forEach { feather ->
            canvas.save()
            canvas.translate(feather.x, feather.y)
            canvas.rotate(feather.rotation)
            
            val alpha = (feather.life * 255).toInt().coerceIn(0, 255)
            val featherFlyPaint = Paint().apply {
                color = Color.argb(alpha, 139, 90, 43)
                isAntiAlias = true
            }
            
            canvas.drawOval(-6f, -12f, 6f, 12f, featherFlyPaint)
            canvas.drawLine(0f, -12f, 0f, 12f, featherDetailPaint)
            canvas.restore()
        }
        
        // Particules de poussière améliorées
        dustParticles.forEach { particle ->
            val alpha = (particle.life * 80).toInt().coerceIn(0, 80)
            val dustPaint = Paint().apply {
                color = Color.argb(alpha, 139, 69, 19)
                isAntiAlias = true
            }
            canvas.drawCircle(particle.x, particle.y, particle.size * particle.life, dustPaint)
        }
        
        // Feuilles plus jolies
        fallingLeaves.forEach { leaf ->
            canvas.save()
            canvas.translate(leaf.x, leaf.y)
            canvas.rotate(leaf.rotation)
            
            val alpha = (leaf.life * 200).toInt().coerceIn(0, 200)
            val leafPaint = Paint().apply {
                color = Color.argb(alpha, 34, 139, 34)
                isAntiAlias = true
            }
            
            // Forme de feuille plus réaliste
            val leafPath = Path()
            leafPath.moveTo(0f, -8f)
            leafPath.quadTo(-6f, -4f, -4f, 0f)
            leafPath.quadTo(-6f, 4f, 0f, 8f)
            leafPath.quadTo(6f, 4f, 4f, 0f)
            leafPath.quadTo(6f, -4f, 0f, -8f)
            
            canvas.drawPath(leafPath, leafPaint)
            canvas.drawLine(0f, -8f, 0f, 8f, featherDetailPaint)
            canvas.restore()
        }
    }
    
    private fun drawImpactEffect(canvas: Canvas) {
        // Effet d'impact spectaculaire
        val impactPaint = Paint().apply {
            isAntiAlias = true
            shader = RadialGradient(
                birdCenterX, screenHeight - 40f, 150f,
                Color.argb(150, 139, 69, 19),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(birdCenterX, screenHeight - 40f, 150f, impactPaint)
        
        // Lignes d'impact
        val impactLines = 12
        for (i in 0 until impactLines) {
            val angle = i * 360f / impactLines
            val startRadius = 50f
            val endRadius = 120f
            
            val startX = birdCenterX + cos(Math.toRadians(angle.toDouble())).toFloat() * startRadius
            val startY = screenHeight - 40f + sin(Math.toRadians(angle.toDouble())).toFloat() * startRadius
            val endX = birdCenterX + cos(Math.toRadians(angle.toDouble())).toFloat() * endRadius
            val endY = screenHeight - 40f + sin(Math.toRadians(angle.toDouble())).toFloat() * endRadius
            
            val linePaint = Paint().apply {
                color = Color.argb(100, 139, 69, 19)
                strokeWidth = 3f
                isAntiAlias = true
            }
            
            canvas.drawLine(startX, startY, endX, endY, linePaint)
        }
    }
    
    private fun drawRespawningBird(canvas: Canvas) {
        // Nouvel oiseau qui descend avec effet magique
        val respawnY = lerp(-birdSize, birdCenterY, min(respawnTimer, 1f))
        val alpha = (respawnTimer * 255).toInt().coerceIn(0, 255)
        
        // Effet de matérialisation
        val materializePaint = Paint().apply {
            color = Color.argb(alpha / 2, 255, 255, 255)
            isAntiAlias = true
            maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)
        }
        
        canvas.save()
        canvas.translate(birdCenterX, respawnY)
        
        // Aura de respawn
        canvas.drawCircle(0f, 0f, birdSize * 0.6f, materializePaint)
        
        // Oiseau qui se matérialise
        bodyPaint.alpha = alpha
        bellyPaint.alpha = alpha
        eyePaint.alpha = alpha
        eyeWhitePaint.alpha = alpha
        beakPaint.alpha = alpha
        featherPaint.alpha = alpha
        
        drawBirdBody(canvas)
        drawBirdHead(canvas)
        drawEyes(canvas)
        drawBeak(canvas)
        
        // Restaurer l'alpha
        bodyPaint.alpha = 255
        bellyPaint.alpha = 255
        eyePaint.alpha = 255
        eyeWhitePaint.alpha = 255
        beakPaint.alpha = 255
        featherPaint.alpha = 255
        
        canvas.restore()
        
        // Redessiner la branche
        canvas.drawLine(branchStartX, branchY, branchEndX, branchY, branchPaint)
        drawBarkDetails(canvas)
    }
    
    // ==================== JAUGE DE VENT ====================
    
    private fun drawWindGauge(canvas: Canvas) {
        val gaugeX = screenWidth * 0.05f
        val gaugeY = screenHeight * 0.05f
        val gaugeWidth = screenWidth * 0.3f
        val gaugeHeight = 40f
        
        // Fond de la jauge
        val backgroundPaint = Paint().apply {
            color = Color.argb(150, 50, 50, 50)
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        val backgroundRect = RectF(gaugeX, gaugeY, gaugeX + gaugeWidth, gaugeY + gaugeHeight)
        canvas.drawRoundRect(backgroundRect, 20f, 20f, backgroundPaint)
        
        // Zones colorées de la jauge
        drawGaugeZones(canvas, gaugeX, gaugeY, gaugeWidth, gaugeHeight)
        
        // Aiguille principale (vent lissé)
        val needlePosition = (windForceSmoothed * gaugeWidth).coerceIn(0f, gaugeWidth)
        val needlePaint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
            strokeWidth = 4f
            style = Paint.Style.STROKE
        }
        canvas.drawLine(
            gaugeX + needlePosition, gaugeY - 5f,
            gaugeX + needlePosition, gaugeY + gaugeHeight + 5f,
            needlePaint
        )
        
        // Aiguille secondaire (vent brut) plus fine
        val rawNeedlePosition = (currentWindForce * gaugeWidth).coerceIn(0f, gaugeWidth)
        val rawNeedlePaint = Paint().apply {
            color = Color.YELLOW
            isAntiAlias = true
            strokeWidth = 2f
            style = Paint.Style.STROKE
            alpha = 150
        }
        canvas.drawLine(
            gaugeX + rawNeedlePosition, gaugeY,
            gaugeX + rawNeedlePosition, gaugeY + gaugeHeight,
            rawNeedlePaint
        )
        
        // Texte d'information
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 24f
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
        }
        
        // Pourcentage actuel
        val percentText = "${(windForceSmoothed * 100).toInt()}%"
        canvas.drawText(percentText, gaugeX, gaugeY - 10f, textPaint)
        
        // Compteur de temps soutenu
        if (sustainedWindTime > 0f) {
            val timeText = "${(sustainedWindTime / 1000f).toInt()}s"
            val timeTextWidth = textPaint.measureText(timeText)
            canvas.drawText(timeText, gaugeX + gaugeWidth - timeTextWidth, gaugeY - 10f, textPaint)
            
            // Barre de progression du temps
            val timeProgress = (sustainedWindTime / EXTREME_WIND_DURATION).coerceIn(0f, 1f)
            val progressWidth = gaugeWidth * timeProgress
            val progressPaint = Paint().apply {
                color = Color.argb(200, 255, 100, 100)
                isAntiAlias = true
            }
            canvas.drawRect(gaugeX, gaugeY + gaugeHeight + 10f, gaugeX + progressWidth, gaugeY + gaugeHeight + 20f, progressPaint)
        }
        
        // Légendes
        drawGaugeLegends(canvas, gaugeX, gaugeY + gaugeHeight + 35f)
    }
    
    private fun drawGaugeZones(canvas: Canvas, startX: Float, startY: Float, width: Float, height: Float) {
        // Zone verte (0-20%) - Calme
        val greenZone = RectF(startX, startY, startX + width * 0.2f, startY + height)
        val greenPaint = Paint().apply {
            color = Color.argb(120, 0, 200, 0)
            isAntiAlias = true
        }
        canvas.drawRect(greenZone, greenPaint)
        
        // Zone jaune (20-30%) - Léger vent, animations commencent
        val yellowZone = RectF(startX + width * 0.2f, startY, startX + width * 0.3f, startY + height)
        val yellowPaint = Paint().apply {
            color = Color.argb(120, 255, 255, 0)
            isAntiAlias = true
        }
        canvas.drawRect(yellowZone, yellowPaint)
        
        // Zone rouge (30%+) - ZONE DE CHUTE !
        val redZone = RectF(startX + width * 0.3f, startY, startX + width, startY + height)
        val redPaint = Paint().apply {
            color = Color.argb(120, 255, 0, 0)
            isAntiAlias = true
        }
        canvas.drawRect(redZone, redPaint)
        
        // Ligne de seuil critique
        val thresholdX = startX + width * EXTREME_WIND_THRESHOLD
        val thresholdPaint = Paint().apply {
            color = Color.RED
            strokeWidth = 3f
            isAntiAlias = true
        }
        canvas.drawLine(thresholdX, startY - 10f, thresholdX, startY + height + 10f, thresholdPaint)
    }
    
    private fun drawGaugeLegends(canvas: Canvas, startX: Float, startY: Float) {
        val legendPaint = Paint().apply {
            color = Color.WHITE
            textSize = 18f
            isAntiAlias = true
        }
        
        canvas.drawText("🟢 Calme", startX, startY, legendPaint)
        canvas.drawText("🟡 Léger", startX + 80f, startY, legendPaint)
        canvas.drawText("🔴 CHUTE!", startX + 160f, startY, legendPaint)
        canvas.drawText("⚪ Lissé  🟡 Brut", startX, startY + 25f, legendPaint)
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
    }
    
    fun getCurrentState(): String {
        return "État: $birdState, Vent: ${(currentWindForce * 100).toInt()}%/${(windForceSmoothed * 100).toInt()}%, Soutenu: ${(sustainedWindTime / 1000f).toInt()}s, Seuil: ${(EXTREME_WIND_THRESHOLD * 100).toInt()}%"
    }
    
    private fun lerp(start: Float, end: Float, factor: Float): Float {
        return start + factor * (end - start)
    }
}
