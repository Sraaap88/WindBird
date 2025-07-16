package com.example.windbird

import android.graphics.*
import kotlin.math.*
import kotlin.random.Random

class BirdRenderer(private val screenWidth: Float, private val screenHeight: Float) {
    
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    // Couleurs réalistes d'oiseau
    private val darkBrownColor = Color.rgb(101, 67, 33)
    private val mediumBrownColor = Color.rgb(139, 69, 19)
    private val lightBrownColor = Color.rgb(160, 82, 45)
    private val bellyColor = Color.rgb(255, 248, 220)
    private val beakColor = Color.rgb(255, 140, 0)
    private val eyeColor = Color.rgb(25, 25, 25)
    private val tearColor = Color.rgb(135, 206, 235)
    private val featherTipColor = Color.rgb(85, 60, 40)
    
    init {
        paint.style = Paint.Style.FILL
        shadowPaint.style = Paint.Style.FILL
        shadowPaint.color = Color.argb(60, 0, 0, 0)
    }
    
    fun drawBird(canvas: Canvas, animationManager: BirdAnimationManager) {
        val birdSize = animationManager.birdSize
        val centerX = animationManager.birdCenterX
        val centerY = animationManager.birdCenterY
        val lean = animationManager.getBodyLean()
        val windForce = animationManager.getLastWindForce()
        val eyeState = animationManager.getEyeState()
        val state = animationManager.currentState
        
        canvas.save()
        
        when (state) {
            BirdState.PERCHED -> {
                canvas.translate(lean * 2f, 0f)
                canvas.rotate(lean * 0.5f, centerX, centerY)
                drawPerchedBird(canvas, centerX, centerY, birdSize, windForce, eyeState)
            }
            BirdState.FALLING -> {
                val fallProgress = animationManager.getFallProgress()
                val rotation = fallProgress * 180f + sin(fallProgress * 12f) * 30f
                val dropY = fallProgress * (screenHeight - centerY) * 0.6f
                canvas.rotate(rotation, centerX, centerY + dropY)
                drawFallingBird(canvas, centerX, centerY + dropY, birdSize, fallProgress)
            }
            BirdState.FALLEN -> {
                val groundY = animationManager.branchY + birdSize * 0.3f
                drawFallenBird(canvas, centerX, groundY, birdSize)
            }
            BirdState.RESPAWNING -> {
                val respawnProgress = animationManager.getRespawnProgress()
                val alpha = (sin(respawnProgress * PI * 3).toFloat() * 128 + 127).toInt()
                paint.alpha = alpha
                drawPerchedBird(canvas, centerX, centerY, birdSize, 0f, EyeState.NORMAL)
                paint.alpha = 255
            }
        }
        
        canvas.restore()
    }
    
    private fun drawPerchedBird(canvas: Canvas, centerX: Float, centerY: Float, size: Float, windForce: Float, eyeState: EyeState) {
        val headSize = size * 0.35f
        val bodyWidth = size * 0.4f
        val bodyHeight = size * 0.6f
        
        // Ombre sous l'oiseau
        drawBirdShadow(canvas, centerX, centerY + size * 0.1f, size)
        
        // Branche réaliste
        drawWoodBranch(canvas, centerY + bodyHeight * 0.5f)
        
        // Pattes d'oiseau
        drawBirdLegs(canvas, centerX, centerY + bodyHeight * 0.4f, size * 0.12f)
        
        // Corps principal avec plumage
        drawRealisticBody(canvas, centerX, centerY, bodyWidth, bodyHeight, windForce)
        
        // Queue avec plumes individuelles
        drawFeatheredTail(canvas, centerX, centerY, size, windForce)
        
        // Ailes détaillées
        drawRealisticWings(canvas, centerX, centerY, size, windForce)
        
        // Tête avec plumage
        drawBirdHead(canvas, centerX, centerY - bodyHeight * 0.3f, headSize, eyeState, windForce)
    }
    
    private fun drawRealisticBody(canvas: Canvas, centerX: Float, centerY: Float, width: Float, height: Float, windForce: Float) {
        // Gradient naturel du corps
        val bodyGradient = LinearGradient(
            centerX - width/2, centerY - height/2,
            centerX + width/2, centerY + height/2,
            intArrayOf(darkBrownColor, mediumBrownColor, lightBrownColor),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.shader = bodyGradient
        
        // Forme corporelle organique
        val bodyPath = Path()
        val windDeform = windForce * 8f
        
        bodyPath.moveTo(centerX - width/2 + windDeform, centerY - height/2)
        bodyPath.quadTo(centerX + width/2, centerY - height/3, centerX + width/3, centerY)
        bodyPath.quadTo(centerX + width/4, centerY + height/2, centerX, centerY + height/3)
        bodyPath.quadTo(centerX - width/4, centerY + height/2, centerX - width/3, centerY)
        bodyPath.quadTo(centerX - width/2 + windDeform, centerY - height/3, centerX - width/2 + windDeform, centerY - height/2)
        bodyPath.close()
        
        canvas.drawPath(bodyPath, paint)
        paint.shader = null
        
        // Ventre plus clair
        paint.color = bellyColor
        val bellyOval = RectF(centerX - width*0.25f, centerY - height*0.1f, centerX + width*0.25f, centerY + height*0.35f)
        canvas.drawOval(bellyOval, paint)
        
        // Texture de plumes sur le corps
        drawBodyFeatherTexture(canvas, centerX, centerY, width, height)
    }
    
    private fun drawBodyFeatherTexture(canvas: Canvas, centerX: Float, centerY: Float, width: Float, height: Float) {
        paint.color = Color.argb(50, 60, 40, 20)
        paint.strokeWidth = 1.5f
        paint.style = Paint.Style.STROKE
        
        // Motifs de plumes réalistes
        for (i in 0..6) {
            for (j in 0..4) {
                val x = centerX - width/3 + (i * width/7f)
                val y = centerY - height/3 + (j * height/5f)
                
                val featherPath = Path()
                featherPath.moveTo(x, y + 8f)
                featherPath.quadTo(x + 3f, y, x + 6f, y + 2f)
                featherPath.quadTo(x + 8f, y + 4f, x + 6f, y + 8f)
                featherPath.quadTo(x + 3f, y + 6f, x, y + 8f)
                canvas.drawPath(featherPath, paint)
            }
        }
        paint.style = Paint.Style.FILL
    }
    
    private fun drawRealisticWings(canvas: Canvas, centerX: Float, centerY: Float, size: Float, windForce: Float) {
        val wingSpan = size * 0.3f
        val wingHeight = size * 0.35f
        val wingFlutter = sin(System.currentTimeMillis() * 0.008f + windForce * 15f) * windForce * 8f
        
        // Aile gauche
        drawDetailedWing(canvas, centerX - wingSpan/2, centerY, wingSpan, wingHeight, -wingFlutter, true)
        
        // Aile droite
        drawDetailedWing(canvas, centerX + wingSpan/2, centerY, wingSpan, wingHeight, wingFlutter, false)
    }
    
    private fun drawDetailedWing(canvas: Canvas, x: Float, y: Float, width: Float, height: Float, angle: Float, isLeft: Boolean) {
        canvas.save()
        canvas.rotate(angle, x, y)
        
        val direction = if (isLeft) -1f else 1f
        
        // Gradient de l'aile
        val wingGradient = RadialGradient(
            x, y, width * 0.8f,
            intArrayOf(mediumBrownColor, lightBrownColor, featherTipColor),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.shader = wingGradient
        
        // Forme d'aile réaliste
        val wingPath = Path()
        wingPath.moveTo(x, y)
        wingPath.quadTo(x + direction * width * 0.4f, y - height * 0.3f, x + direction * width * 0.9f, y - height * 0.1f)
        wingPath.quadTo(x + direction * width, y + height * 0.1f, x + direction * width * 0.8f, y + height * 0.4f)
        wingPath.quadTo(x + direction * width * 0.3f, y + height * 0.3f, x, y + height * 0.1f)
        wingPath.close()
        
        canvas.drawPath(wingPath, paint)
        paint.shader = null
        
        // Plumes primaires individuelles
        paint.color = featherTipColor
        paint.strokeWidth = 2f
        paint.style = Paint.Style.STROKE
        
        for (i in 1..4) {
            val featherX = x + direction * width * (0.5f + i * 0.1f)
            val featherY1 = y - height * 0.1f
            val featherY2 = y + height * 0.3f
            canvas.drawLine(featherX, featherY1, featherX, featherY2, paint)
        }
        
        paint.style = Paint.Style.FILL
        canvas.restore()
    }
    
    private fun drawFeatheredTail(canvas: Canvas, centerX: Float, centerY: Float, size: Float, windForce: Float) {
        val tailFeathers = 7
        val tailLength = size * 0.25f
        val baseY = centerY + size * 0.2f
        
        for (i in 0 until tailFeathers) {
            val angle = -25f + (i * 8f) + windForce * 15f + sin(i.toFloat()) * 5f
            val length = tailLength * (0.8f + Random.nextFloat() * 0.3f)
            val thickness = 8f - i * 0.8f
            
            canvas.save()
            canvas.rotate(angle, centerX, baseY)
            
            // Couleur alternée des plumes
            paint.color = if (i % 2 == 0) mediumBrownColor else lightBrownColor
            
            // Forme de plume réaliste
            val featherPath = Path()
            featherPath.moveTo(centerX, baseY)
            featherPath.quadTo(centerX - thickness/2, baseY + length/3, centerX - thickness/3, baseY + length)
            featherPath.quadTo(centerX, baseY + length + 3f, centerX + thickness/3, baseY + length)
            featherPath.quadTo(centerX + thickness/2, baseY + length/3, centerX, baseY)
            canvas.drawPath(featherPath, paint)
            
            // Rachis (tige centrale)
            paint.color = featherTipColor
            paint.strokeWidth = 1f
            paint.style = Paint.Style.STROKE
            canvas.drawLine(centerX, baseY, centerX, baseY + length, paint)
            paint.style = Paint.Style.FILL
            
            canvas.restore()
        }
    }
    
    private fun drawBirdHead(canvas: Canvas, centerX: Float, centerY: Float, size: Float, eyeState: EyeState, windForce: Float) {
        // Gradient de la tête
        val headGradient = RadialGradient(
            centerX, centerY, size * 0.7f,
            intArrayOf(lightBrownColor, mediumBrownColor, darkBrownColor),
            floatArrayOf(0f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.shader = headGradient
        
        // Forme de tête déformée par le vent
        val headDeform = windForce * 5f
        val headOval = RectF(
            centerX - size/2 - headDeform, centerY - size/2,
            centerX + size/2 + headDeform, centerY + size/2
        )
        canvas.drawOval(headOval, paint)
        paint.shader = null
        
        // Petite crête de plumes
        drawHeadCrest(canvas, centerX, centerY - size/2, size, windForce)
        
        // Bec d'oiseau réaliste
        drawRealisticBeak(canvas, centerX + size/2, centerY, size * 0.25f)
        
        // Yeux expressifs
        drawBirdEyes(canvas, centerX, centerY, size, eyeState, windForce)
        
        // Joues gonflées si vent fort
        if (windForce > 0.7f) {
            drawPuffedCheeks(canvas, centerX, centerY, size, windForce)
        }
    }
    
    private fun drawHeadCrest(canvas: Canvas, centerX: Float, topY: Float, headSize: Float, windForce: Float) {
        paint.color = mediumBrownColor
        
        val crestFeathers = 5
        val crestWidth = headSize * 0.4f
        val crestHeight = headSize * 0.2f * (1f + windForce * 0.3f)
        
        for (i in 0 until crestFeathers) {
            val x = centerX - crestWidth/2 + (i * crestWidth / (crestFeathers - 1))
            val sway = sin(i * 0.5f) * windForce * 3f
            val height = crestHeight * (0.7f + Random.nextFloat() * 0.6f)
            
            val featherPath = Path()
            featherPath.moveTo(x, topY)
            featherPath.quadTo(x + sway - 2f, topY - height/2, x + sway, topY - height)
            featherPath.quadTo(x + sway + 2f, topY - height/2, x + 4f, topY)
            canvas.drawPath(featherPath, paint)
        }
    }
    
    private fun drawRealisticBeak(canvas: Canvas, startX: Float, startY: Float, length: Float) {
        // Gradient du bec
        val beakGradient = LinearGradient(
            startX, startY, startX + length, startY,
            intArrayOf(beakColor, Color.rgb(255, 160, 20)),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.shader = beakGradient
        
        // Forme de bec d'oiseau
        val beakPath = Path()
        beakPath.moveTo(startX, startY - length * 0.15f)
        beakPath.lineTo(startX + length, startY - length * 0.08f)
        beakPath.quadTo(startX + length * 1.05f, startY, startX + length, startY + length * 0.08f)
        beakPath.lineTo(startX, startY + length * 0.15f)
        beakPath.close()
        
        canvas.drawPath(beakPath, paint)
        paint.shader = null
        
        // Détails du bec
        paint.color = Color.rgb(200, 120, 10)
        paint.strokeWidth = 1f
        paint.style = Paint.Style.STROKE
        canvas.drawLine(startX, startY, startX + length * 0.8f, startY, paint)
        paint.style = Paint.Style.FILL
        
        // Narine
        paint.color = Color.rgb(100, 60, 30)
        canvas.drawCircle(startX + length * 0.2f, startY - length * 0.06f, 2f, paint)
    }
    
    private fun drawBirdEyes(canvas: Canvas, centerX: Float, centerY: Float, headSize: Float, eyeState: EyeState, windForce: Float) {
        val eyeSize = headSize * 0.15f
        val leftEyeX = centerX - headSize * 0.18f
        val rightEyeX = centerX + headSize * 0.18f
        val eyeY = centerY - headSize * 0.08f
        
        for ((eyeX, isLeft) in listOf(leftEyeX to true, rightEyeX to false)) {
            // Contour de l'œil
            paint.color = Color.rgb(40, 40, 40)
            canvas.drawCircle(eyeX, eyeY, eyeSize + 1f, paint)
            
            // Blanc de l'œil
            paint.color = Color.rgb(250, 250, 255)
            val whiteSize = when (eyeState) {
                EyeState.NORMAL -> eyeSize * 0.9f
                EyeState.SQUINTING -> eyeSize * 0.6f
                EyeState.STRUGGLING -> eyeSize * 0.5f
                EyeState.PANICKED -> eyeSize
            }
            
            if (eyeState == EyeState.SQUINTING || eyeState == EyeState.STRUGGLING) {
                // Yeux plissés
                val eyeRect = RectF(eyeX - whiteSize, eyeY - whiteSize/3, eyeX + whiteSize, eyeY + whiteSize/3)
                canvas.drawOval(eyeRect, paint)
            } else {
                canvas.drawCircle(eyeX, eyeY, whiteSize, paint)
            }
            
            // Pupille
            paint.color = eyeColor
            val pupilSize = when (eyeState) {
                EyeState.NORMAL -> whiteSize * 0.5f
                EyeState.SQUINTING -> whiteSize * 0.7f
                EyeState.STRUGGLING -> whiteSize * 0.8f
                EyeState.PANICKED -> whiteSize * 0.3f
            }
            
            val pupilOffsetX = windForce * (if (isLeft) -2f else 2f)
            canvas.drawCircle(eyeX + pupilOffsetX, eyeY, pupilSize, paint)
            
            // Reflet dans l'œil
            paint.color = Color.argb(180, 255, 255, 255)
            canvas.drawCircle(eyeX + pupilSize * 0.3f, eyeY - pupilSize * 0.3f, pupilSize * 0.25f, paint)
        }
    }
    
    private fun drawPuffedCheeks(canvas: Canvas, centerX: Float, centerY: Float, headSize: Float, windForce: Float) {
        val puffSize = headSize * 0.2f * windForce
        paint.color = Color.argb(100, 255, 200, 200)
        
        // Joue gauche gonflée
        canvas.drawCircle(centerX - headSize * 0.3f, centerY + headSize * 0.1f, puffSize, paint)
        
        // Joue droite gonflée
        canvas.drawCircle(centerX + headSize * 0.3f, centerY + headSize * 0.1f, puffSize, paint)
    }
    
    private fun drawBirdLegs(canvas: Canvas, centerX: Float, y: Float, size: Float) {
        paint.color = Color.rgb(255, 140, 0)
        paint.strokeWidth = 3f
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        
        // Pattes d'oiseau réalistes
        val legPositions = arrayOf(-size/2, size/2)
        
        for (legX in legPositions) {
            val x = centerX + legX
            // Patte principale
            canvas.drawLine(x, y - size/2, x, y + size/2, paint)
            
            // Orteils
            canvas.drawLine(x, y + size/2, x - 6f, y + size/2 + 8f, paint)
            canvas.drawLine(x, y + size/2, x, y + size/2 + 10f, paint)
            canvas.drawLine(x, y + size/2, x + 6f, y + size/2 + 8f, paint)
            
            // Orteil arrière
            canvas.drawLine(x, y + size/4, x - 4f, y + size/2 + 4f, paint)
        }
        
        paint.style = Paint.Style.FILL
    }
    
    private fun drawWoodBranch(canvas: Canvas, y: Float) {
        paint.color = Color.rgb(101, 67, 33)
        paint.strokeWidth = 10f
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        
        // Branche principale
        canvas.drawLine(0f, y, screenWidth, y, paint)
        
        // Texture d'écorce
        paint.strokeWidth = 1f
        paint.color = Color.rgb(85, 55, 25)
        for (i in 0..15) {
            val x = i * screenWidth / 15f
            canvas.drawLine(x, y - 5f, x + 2f, y + 5f, paint)
        }
        
        paint.style = Paint.Style.FILL
    }
    
    private fun drawFallingBird(canvas: Canvas, centerX: Float, centerY: Float, size: Float, fallProgress: Float) {
        // Oiseau qui tombe avec ailes déployées
        val wingSpread = size * (0.5f + fallProgress * 0.3f)
        val panicFlap = sin(fallProgress * 25f) * 15f
        
        // Corps en chute
        paint.color = mediumBrownColor
        val bodyOval = RectF(centerX - size*0.2f, centerY - size*0.3f, centerX + size*0.2f, centerY + size*0.3f)
        canvas.drawOval(bodyOval, paint)
        
        // Ailes battant en panique
        drawPanicWings(canvas, centerX, centerY, wingSpread, panicFlap)
        
        // Tête en panique
        drawBirdHead(canvas, centerX, centerY - size*0.3f, size*0.3f, EyeState.PANICKED, 1f)
    }
    
    private fun drawPanicWings(canvas: Canvas, centerX: Float, centerY: Float, spread: Float, flap: Float) {
        paint.color = lightBrownColor
        
        // Aile gauche battant
        canvas.save()
        canvas.rotate(-20f + flap, centerX - spread/3, centerY)
        val leftWing = RectF(centerX - spread, centerY - spread/4, centerX - spread/3, centerY + spread/4)
        canvas.drawOval(leftWing, paint)
        canvas.restore()
        
        // Aile droite battant
        canvas.save()
        canvas.rotate(20f - flap, centerX + spread/3, centerY)
        val rightWing = RectF(centerX + spread/3, centerY - spread/4, centerX + spread, centerY + spread/4)
        canvas.drawOval(rightWing, paint)
        canvas.restore()
    }
    
    private fun drawFallenBird(canvas: Canvas, centerX: Float, groundY: Float, size: Float) {
        // Oiseau au sol
        paint.color = Color.argb(220, 139, 69, 19)
        
        // Corps effondré
        val bodyOval = RectF(centerX - size*0.3f, groundY - size*0.1f, centerX + size*0.3f, groundY + size*0.1f)
        canvas.drawOval(bodyOval, paint)
        
        // Ailes étalées
        paint.color = Color.argb(180, 160, 82, 45)
        canvas.drawOval(centerX - size*0.5f, groundY - size*0.15f, centerX - size*0.1f, groundY + size*0.15f, paint)
        canvas.drawOval(centerX + size*0.1f, groundY - size*0.15f, centerX + size*0.5f, groundY + size*0.15f, paint)
        
        // Tête au sol
        paint.color = mediumBrownColor
        canvas.drawCircle(centerX + size*0.2f, groundY, size*0.12f, paint)
    }
    
    private fun drawBirdShadow(canvas: Canvas, centerX: Float, centerY: Float, size: Float) {
        shadowPaint.color = Color.argb(50, 0, 0, 0)
        val shadowOval = RectF(centerX - size*0.25f, centerY - size*0.08f, centerX + size*0.25f, centerY + size*0.08f)
        canvas.drawOval(shadowOval, shadowPaint)
    }
    
    fun drawParticles(canvas: Canvas, tears: List<Tear>, feathers: List<FlyingFeather>, dust: List<DustParticle>, leaves: List<FallingLeaf>) {
        // Larmes réalistes
        paint.color = tearColor
        for (tear in tears) {
            val alpha = (tear.life * 255).toInt().coerceIn(0, 255)
            paint.alpha = alpha
            canvas.drawCircle(tear.x, tear.y, 3f, paint)
            
            // Petit reflet sur la larme
            paint.color = Color.argb(alpha/2, 255, 255, 255)
            canvas.drawCircle(tear.x + 1f, tear.y - 1f, 1f, paint)
            paint.color = tearColor
        }
        
        // Plumes volantes
        paint.color = mediumBrownColor
        for (feather in feathers) {
            val alpha = (feather.life * 200).toInt().coerceIn(0, 200)
            paint.alpha = alpha
            canvas.save()
            canvas.rotate(feather.rotation, feather.x, feather.y)
            canvas.drawOval(feather.x - 6f, feather.y - 2f, feather.x + 6f, feather.y + 2f, paint)
            canvas.restore()
        }
        
        // Poussière
        paint.color = Color.rgb(139, 119, 101)
        for (particle in dust) {
            val alpha = (particle.life * 180).toInt().coerceIn(0, 180)
            paint.alpha = alpha
            canvas.drawCircle(particle.x, particle.y, particle.size, paint)
        }
        
        // Feuilles qui tombent
        paint.color = Color.rgb(101, 83, 65)
        for (leaf in leaves) {
            val alpha = (leaf.life * 150).toInt().coerceIn(0, 150)
            paint.alpha = alpha
            canvas.save()
            canvas.rotate(leaf.rotation, leaf.x, leaf.y)
            
            val leafPath = Path()
            leafPath.moveTo(leaf.x, leaf.y - 5f)
            leafPath.quadTo(leaf.x + 3f, leaf.y - 2f, leaf.x + 2f, leaf.y)
            leafPath.quadTo(leaf.x + 3f, leaf.y + 2f, leaf.x, leaf.y + 5f)
            leafPath.quadTo(leaf.x - 3f, leaf.y + 2f, leaf.x - 2f, leaf.y)
            leafPath.quadTo(leaf.x - 3f, leaf.y - 2f, leaf.x, leaf.y - 5f)
            canvas.drawPath(leafPath, paint)
            canvas.restore()
        }
        
        paint.alpha = 255
    }
}
