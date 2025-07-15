package com.example.windbird

import android.graphics.*
import kotlin.math.*
import kotlin.random.Random

class BirdRenderer(
    private val birdSize: Float,
    private val birdCenterX: Float,
    private val birdCenterY: Float,
    private val branchY: Float,
    private val branchStartX: Float,
    private val branchEndX: Float,
    private val screenWidth: Int,
    private val screenHeight: Int
) {
    
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
    
    // ==================== FONCTION PRINCIPALE DE DESSIN ====================
    
    fun draw(canvas: Canvas, data: BirdAnimationData) {
        when (data.birdState) {
            BirdAnimationManager.BirdState.PERCHED -> drawPerchedBird(canvas, data)
            BirdAnimationManager.BirdState.FALLING -> drawFallingBird(canvas, data)
            BirdAnimationManager.BirdState.FALLEN -> drawImpactEffect(canvas, data)
            BirdAnimationManager.BirdState.RESPAWNING -> drawRespawningBird(canvas, data)
        }
        
        drawParticleEffects(canvas, data)
    }
    
    // ==================== MODES DE RENDU ====================
    
    private fun drawPerchedBird(canvas: Canvas, data: BirdAnimationData) {
        canvas.save()
        
        // Appliquer l'inclinaison du corps
        canvas.translate(birdCenterX, birdCenterY)
        canvas.rotate(data.bodyLeanAngle)
        canvas.translate(-birdCenterX, -birdCenterY)
        
        drawBranch(canvas, data)
        drawBirdShadow(canvas)
        drawFeet(canvas, data)
        drawTail(canvas, data)
        drawBirdBody(canvas, data)
        drawWings(canvas, data)
        drawBirdHead(canvas, data)
        drawEyes(canvas, data)
        drawBeak(canvas, data)
        
        canvas.restore()
    }
    
    private fun drawFallingBird(canvas: Canvas, data: BirdAnimationData) {
        canvas.save()
        canvas.translate(birdCenterX, data.fallPositionY)
        canvas.rotate(data.fallRotation)
        canvas.translate(-birdCenterX, -data.fallPositionY)
        
        drawBirdShadow(canvas)
        drawTail(canvas, data)
        drawBirdBody(canvas, data)
        drawWings(canvas, data)
        drawBirdHead(canvas, data)
        drawEyes(canvas, data)
        drawBeak(canvas, data)
        
        canvas.restore()
    }
    
    private fun drawRespawningBird(canvas: Canvas, data: BirdAnimationData) {
        // Nouvel oiseau qui descend avec effet magique
        val respawnY = lerp(-birdSize, birdCenterY, minOf(data.respawnTimer, 1f))
        val alpha = (data.respawnTimer * 255).toInt().coerceIn(0, 255)
        
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
        
        drawBirdBody(canvas, data)
        drawBirdHead(canvas, data)
        drawEyes(canvas, data)
        drawBeak(canvas, data)
        
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
    
    // ==================== PARTIES DU CORPS ====================
    
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
    
    private fun drawBirdBody(canvas: Canvas, data: BirdAnimationData) {
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
        drawOrganicFeatherPattern(canvas, data, centerX, centerY, baseWidth, baseHeight)
    }
    
    private fun drawOrganicFeatherPattern(canvas: Canvas, data: BirdAnimationData, centerX: Float, centerY: Float, width: Float, height: Float) {
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
            val wave = sin(data.featherWavePhase + i * 0.5f) * 5f
            
            val featherPath = Path()
            featherPath.moveTo(x, y)
            featherPath.quadTo(x + wave, y - height * 0.1f, x + wave * 0.5f, y - height * 0.15f)
            canvas.drawPath(featherPath, patternPaint)
        }
    }
    
    private fun drawBirdHead(canvas: Canvas, data: BirdAnimationData) {
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
        if (data.cheekPuffLevel > 0f) {
            val puffPath = Path()
            val puffRadius = headRadius * (1f + data.cheekPuffLevel * 0.4f)
            
            puffPath.addCircle(birdCenterX, headY, puffRadius, Path.Direction.CW)
            canvas.drawPath(puffPath, bellyPaint)
        }
        
        // Tête principale
        canvas.drawPath(headPath, bodyPaint)
        
        // Crête de plumes plus naturelle
        if (data.headCrestHeight > 0f) {
            drawNaturalCrest(canvas, data, birdCenterX, headY - headRadius * 0.9f)
        }
        
        // Motifs décoratifs sur la tête plus stylisés
        drawHeadMarkings(canvas, birdCenterX, headY, headRadius)
    }
    
    private fun drawNaturalCrest(canvas: Canvas, data: BirdAnimationData, centerX: Float, topY: Float) {
        val crestHeight = data.headCrestHeight * birdSize * 0.25f
        val featherCount = 9
        
        for (i in -(featherCount/2)..(featherCount/2)) {
            val baseX = centerX + i * birdSize * 0.03f
            val waveOffset = sin(data.featherWavePhase + i * 0.7f) * birdSize * 0.04f
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
    
    private fun drawEyes(canvas: Canvas, data: BirdAnimationData) {
        val eyeRadius = birdSize * 0.09f
        val eyeY = birdCenterY - birdSize * 0.2f
        val eyeSpacing = birdSize * 0.14f
        
        for (side in arrayOf(-1, 1)) {
            val eyeX = birdCenterX + side * eyeSpacing
            
            // Ombre de l'œil
            canvas.drawCircle(eyeX + 2f, eyeY + 2f, eyeRadius, shadowPaint)
            
            // Blanc de l'œil avec forme plus réaliste
            val squintFactor = 1f - data.eyeSquintLevel * 0.6f
            val eyeHeight = eyeRadius * squintFactor
            
            canvas.drawOval(
                eyeX - eyeRadius, eyeY - eyeHeight,
                eyeX + eyeRadius, eyeY + eyeHeight,
                eyeWhitePaint
            )
            
            // Pupille avec mouvement plus naturel
            val pupilRadius = eyeRadius * 0.6f * squintFactor
            val pupilOffsetX = (data.eyeRollAngle * 0.008f) * side
            val pupilOffsetY = data.eyeRollAngle * 0.005f
            
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
            if (data.eyeSquintLevel < 0.5f) {
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
    
    private fun drawBeak(canvas: Canvas, data: BirdAnimationData) {
        val beakY = birdCenterY - birdSize * 0.05f
        val beakWidth = birdSize * 0.12f
        val beakHeight = birdSize * 0.1f * (1f + data.beakOpenness * 0.6f)
        
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
        if (data.tongueOut > 0f) {
            val tonguePaint = Paint().apply {
                color = Color.rgb(255, 100, 150)
                isAntiAlias = true
            }
            val tongueY = beakY + beakHeight + data.tongueOut * birdSize * 0.06f
            canvas.drawOval(
                birdCenterX - 4f, tongueY - 2f,
                birdCenterX + 4f, tongueY + 6f,
                tonguePaint
            )
        }
    }
    
    private fun drawWings(canvas: Canvas, data: BirdAnimationData) {
        if (data.wingOpenness <= 0f) return
        
        val wingSpan = birdSize * 0.6f * data.wingOpenness
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
    
    private fun drawTail(canvas: Canvas, data: BirdAnimationData) {
        canvas.save()
        canvas.translate(birdCenterX, birdCenterY + birdSize * 0.35f)
        canvas.rotate(data.tailCounterbalance)
        
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
    
    private fun drawFeet(canvas: Canvas, data: BirdAnimationData) {
        val footY = branchY
        val footSpacing = birdSize * 0.18f
        val slipOffset = data.footSlipProgress * birdSize * 0.12f
        
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
    
    private fun drawBranch(canvas: Canvas, data: BirdAnimationData) {
        canvas.save()
        
        // Vibration de la branche
        val vibrateOffset = sin(System.currentTimeMillis() * 0.06f) * data.branchVibrateIntensity
        canvas.translate(0f, vibrateOffset)
        canvas.rotate(data.branchOscillateAngle, screenWidth / 2f, branchY)
        
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
    
    // ==================== EFFETS VISUELS ====================
    
    private fun drawParticleEffects(canvas: Canvas, data: BirdAnimationData) {
        // Larmes plus belles
        val tearPaint = Paint().apply {
            isAntiAlias = true
            shader = RadialGradient(0f, 0f, 8f, Color.CYAN, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        }
        data.tears.forEach { tear ->
            canvas.save()
            canvas.translate(tear.x, tear.y)
            canvas.drawCircle(0f, 0f, 6f * tear.life, tearPaint)
            canvas.restore()
        }
        
        // Plumes volantes plus détaillées
        data.flyingFeathers.forEach { feather ->
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
        data.dustParticles.forEach { particle ->
            val alpha = (particle.life * 80).toInt().coerceIn(0, 80)
            val dustPaint = Paint().apply {
                color = Color.argb(alpha, 139, 69, 19)
                isAntiAlias = true
            }
            canvas.drawCircle(particle.x, particle.y, particle.size * particle.life, dustPaint)
        }
        
        // Feuilles plus jolies
        data.fallingLeaves.forEach { leaf ->
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
    
    private fun drawImpactEffect(canvas: Canvas, data: BirdAnimationData) {
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
    
    // ==================== FONCTIONS UTILITAIRES ====================
    
    fun cleanup() {
        // Nettoyer les ressources si nécessaire
    }
    
    private fun lerp(start: Float, end: Float, factor: Float): Float {
        return start + factor * (end - start)
    }
}
