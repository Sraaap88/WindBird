package com.example.windbird

import android.graphics.*
import kotlin.math.*
import kotlin.random.Random

class BirdRenderer(private val screenWidth: Float, private val screenHeight: Float) {
    
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    // Palette sinistre et réaliste
    private val deepBlackColor = Color.rgb(15, 15, 20)
    private val charcoalColor = Color.rgb(25, 25, 30)
    private val darkGrayColor = Color.rgb(35, 35, 40)
    private val metallic = Color.rgb(45, 45, 55)
    private val bloodRed = Color.rgb(139, 0, 0)
    private val piercing = Color.rgb(220, 220, 230)
    private val deadBrown = Color.rgb(60, 45, 30)
    private val ashGray = Color.rgb(80, 80, 85)
    
    init {
        paint.style = Paint.Style.FILL
        shadowPaint.style = Paint.Style.FILL
        glowPaint.style = Paint.Style.FILL
    }
    
    fun drawBird(canvas: Canvas, animationManager: BirdAnimationManager) {
        val birdSize = animationManager.birdSize
        val centerX = animationManager.birdCenterX
        val centerY = animationManager.birdCenterY
        val lean = animationManager.getBodyLean()
        val windForce = animationManager.getLastWindForce()
        val eyeState = animationManager.getEyeState()
        val state = animationManager.currentState
        val darkAura = animationManager.getDarkAuraIntensity()
        val menacingStare = animationManager.getMenacingStare()
        
        canvas.save()
        
        // Aura sombre qui pulse autour du corbeau
        if (darkAura > 0.3f) {
            drawDarkAura(canvas, centerX, centerY, birdSize, darkAura)
        }
        
        when (state) {
            BirdState.PERCHED -> {
                canvas.translate(lean * 1.5f, 0f)
                canvas.rotate(lean * 0.3f, centerX, centerY)
                drawSinisterCrow(canvas, centerX, centerY, birdSize, windForce, eyeState, menacingStare)
            }
            BirdState.FALLING -> {
                val fallProgress = animationManager.getFallProgress()
                val chaosRotation = fallProgress * 270f + sin(fallProgress * 20f) * 45f
                val dropY = fallProgress * (screenHeight - centerY) * 0.7f
                canvas.rotate(chaosRotation, centerX, centerY + dropY)
                drawFallingCrow(canvas, centerX, centerY + dropY, birdSize, fallProgress)
            }
            BirdState.FALLEN -> {
                val groundY = animationManager.branchY + birdSize * 0.3f
                drawDeadCrow(canvas, centerX, groundY, birdSize)
            }
            BirdState.RESPAWNING -> {
                val respawnProgress = animationManager.getRespawnProgress()
                val ghostAlpha = (sin(respawnProgress * PI * 4).toFloat() * 100 + 155).toInt()
                paint.alpha = ghostAlpha
                drawSinisterCrow(canvas, centerX, centerY, birdSize, 0f, EyeState.NORMAL, 0f)
                paint.alpha = 255
            }
        }
        
        canvas.restore()
    }
    
    private fun drawDarkAura(canvas: Canvas, centerX: Float, centerY: Float, size: Float, intensity: Float) {
        val auraRadius = size * (0.8f + intensity * 0.4f)
        val alpha = (intensity * 60).toInt().coerceIn(0, 60)
        
        // Gradient sombre qui pulse
        val auraGradient = RadialGradient(
            centerX, centerY, auraRadius,
            intArrayOf(
                Color.argb(alpha, 20, 0, 0),
                Color.argb(alpha/2, 10, 0, 0),
                Color.argb(0, 0, 0, 0)
            ),
            floatArrayOf(0f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )
        glowPaint.shader = auraGradient
        canvas.drawCircle(centerX, centerY, auraRadius, glowPaint)
        glowPaint.shader = null
    }
    
    private fun drawSinisterCrow(canvas: Canvas, centerX: Float, centerY: Float, size: Float, windForce: Float, eyeState: EyeState, menace: Float) {
        val headSize = size * 0.4f
        val bodyWidth = size * 0.45f
        val bodyHeight = size * 0.65f
        
        // Ombre sinistre projetée
        drawOminousShadow(canvas, centerX, centerY + size * 0.15f, size, menace)
        
        // Branche morte et noueuse
        drawDeadBranch(canvas, centerY + bodyHeight * 0.5f)
        
        // Serres acérées agrippées
        drawSharpTalons(canvas, centerX, centerY + bodyHeight * 0.4f, size * 0.15f, menace)
        
        // Corps du corbeau avec plumage détaillé
        drawCrowBody(canvas, centerX, centerY, bodyWidth, bodyHeight, windForce, menace)
        
        // Ailes puissantes et menaçantes
        drawDarkWings(canvas, centerX, centerY, size, windForce, menace)
        
        // Queue en éventail sinistre
        drawOminousTail(canvas, centerX, centerY, size, windForce)
        
        // Tête de corbeau avec regard perçant
        drawCrowHead(canvas, centerX, centerY - bodyHeight * 0.3f, headSize, eyeState, windForce, menace)
    }
    
    private fun drawCrowBody(canvas: Canvas, centerX: Float, centerY: Float, width: Float, height: Float, windForce: Float, menace: Float) {
        // Gradient sombre du corps
        val bodyGradient = LinearGradient(
            centerX - width/2, centerY - height/2,
            centerX + width/2, centerY + height/2,
            intArrayOf(deepBlackColor, charcoalColor, darkGrayColor),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.shader = bodyGradient
        
        // Forme corporelle déformée par le vent et la menace
        val windDeform = windForce * 12f + menace * 8f
        val bodyPath = Path()
        
        bodyPath.moveTo(centerX - width/2 + windDeform, centerY - height/2)
        bodyPath.quadTo(centerX + width/2 + windDeform/2, centerY - height/3, centerX + width/3, centerY + height/6)
        bodyPath.quadTo(centerX + width/4, centerY + height/2, centerX - width/8, centerY + height/3)
        bodyPath.quadTo(centerX - width/3, centerY + height/2, centerX - width/2, centerY + height/4)
        bodyPath.quadTo(centerX - width/2 + windDeform, centerY - height/4, centerX - width/2 + windDeform, centerY - height/2)
        bodyPath.close()
        
        canvas.drawPath(bodyPath, paint)
        paint.shader = null
        
        // Texture de plumes noires détaillées
        drawDarkFeatherTexture(canvas, centerX, centerY, width, height, menace)
        
        // Reflets métalliques sur les plumes
        if (menace > 0.5f) {
            drawMetallicSheen(canvas, centerX, centerY, width, height, menace)
        }
    }
    
    private fun drawDarkFeatherTexture(canvas: Canvas, centerX: Float, centerY: Float, width: Float, height: Float, menace: Float) {
        paint.color = Color.argb((80 + menace * 40).toInt(), 40, 40, 45)
        paint.strokeWidth = 1.5f + menace
        paint.style = Paint.Style.STROKE
        
        // Motifs de plumes organiques et détaillés
        for (i in 0..8) {
            for (j in 0..6) {
                val x = centerX - width/2.5f + (i * width/8f)
                val y = centerY - height/2.5f + (j * height/6f)
                val featherLength = 12f + menace * 6f
                val featherWidth = 4f + menace * 2f
                
                val featherPath = Path()
                featherPath.moveTo(x, y + featherLength)
                featherPath.quadTo(x - featherWidth, y + featherLength/2, x - featherWidth/2, y)
                featherPath.quadTo(x, y - 2f, x + featherWidth/2, y)
                featherPath.quadTo(x + featherWidth, y + featherLength/2, x, y + featherLength)
                canvas.drawPath(featherPath, paint)
            }
        }
        paint.style = Paint.Style.FILL
    }
    
    private fun drawMetallicSheen(canvas: Canvas, centerX: Float, centerY: Float, width: Float, height: Float, intensity: Float) {
        paint.color = Color.argb((intensity * 60).toInt(), 70, 70, 80)
        
        // Reflets métalliques qui bougent
        val time = System.currentTimeMillis() * 0.002f
        for (i in 0..4) {
            val x = centerX - width/3 + (i * width/5f) + sin(time + i) * 8f
            val y = centerY - height/4 + sin(time * 1.5f + i) * height/6f
            
            val sheenPath = Path()
            sheenPath.moveTo(x - 8f, y)
            sheenPath.quadTo(x, y - 15f, x + 8f, y)
            sheenPath.quadTo(x, y + 5f, x - 8f, y)
            canvas.drawPath(sheenPath, paint)
        }
    }
    
    private fun drawDarkWings(canvas: Canvas, centerX: Float, centerY: Float, size: Float, windForce: Float, menace: Float) {
        val wingSpan = size * 0.4f
        val wingHeight = size * 0.45f
        val menacingSpread = menace * 15f
        val windFlutter = sin(System.currentTimeMillis() * 0.01f + windForce * 25f) * (windForce * 12f + menace * 8f)
        
        // Aile gauche - plus large et menaçante
        drawSinisterWing(canvas, centerX - wingSpan/2, centerY, wingSpan, wingHeight, -windFlutter - menacingSpread, true, menace)
        
        // Aile droite
        drawSinisterWing(canvas, centerX + wingSpan/2, centerY, wingSpan, wingHeight, windFlutter + menacingSpread, false, menace)
    }
    
    private fun drawSinisterWing(canvas: Canvas, x: Float, y: Float, width: Float, height: Float, angle: Float, isLeft: Boolean, menace: Float) {
        canvas.save()
        canvas.rotate(angle, x, y)
        
        val direction = if (isLeft) -1f else 1f
        val menaceExtension = menace * 0.3f
        
        // Gradient sombre de l'aile
        val wingGradient = RadialGradient(
            x, y, width * (1f + menaceExtension),
            intArrayOf(deepBlackColor, charcoalColor, metallic),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.shader = wingGradient
        
        // Forme d'aile menaçante et réaliste
        val wingPath = Path()
        wingPath.moveTo(x, y)
        wingPath.quadTo(x + direction * width * (0.5f + menaceExtension), y - height * 0.4f, x + direction * width * (1f + menaceExtension), y - height * 0.1f)
        wingPath.quadTo(x + direction * width * (1.1f + menaceExtension), y + height * 0.1f, x + direction * width * (0.9f + menaceExtension), y + height * 0.5f)
        wingPath.quadTo(x + direction * width * (0.4f + menaceExtension), y + height * 0.4f, x, y + height * 0.15f)
        wingPath.close()
        
        canvas.drawPath(wingPath, paint)
        paint.shader = null
        
        // Plumes primaires acérées
        paint.color = Color.argb((150 + menace * 50).toInt(), 20, 20, 25)
        paint.strokeWidth = 3f + menace
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        
        for (i in 1..6) {
            val featherX = x + direction * width * (0.5f + i * 0.1f + menaceExtension/2)
            val featherY1 = y - height * 0.1f
            val featherY2 = y + height * 0.4f
            val sharpness = menace * 5f
            
            // Plumes qui se terminent en pointe acérée
            canvas.drawLine(featherX, featherY1, featherX + direction * sharpness, featherY2 - sharpness, paint)
        }
        
        paint.style = Paint.Style.FILL
        canvas.restore()
    }
    
    private fun drawOminousTail(canvas: Canvas, centerX: Float, centerY: Float, size: Float, windForce: Float) {
        val tailFeathers = 9
        val tailLength = size * 0.3f
        val baseY = centerY + size * 0.25f
        
        for (i in 0 until tailFeathers) {
            val angle = -30f + (i * 7f) + windForce * 20f + sin(i * 0.8f) * 6f
            val length = tailLength * (0.7f + Random.nextFloat() * 0.5f)
            val darkness = 0.2f + (i % 3) * 0.3f
            
            canvas.save()
            canvas.rotate(angle, centerX, baseY)
            
            // Couleur variable selon la position de la plume
            paint.color = when (i % 3) {
                0 -> deepBlackColor
                1 -> charcoalColor
                else -> darkGrayColor
            }
            
            // Forme de plume sinistre
            val featherPath = Path()
            featherPath.moveTo(centerX, baseY)
            featherPath.quadTo(centerX - 6f, baseY + length/3, centerX - 4f, baseY + length)
            featherPath.quadTo(centerX, baseY + length + 4f, centerX + 4f, baseY + length)
            featherPath.quadTo(centerX + 6f, baseY + length/3, centerX, baseY)
            canvas.drawPath(featherPath, paint)
            
            // Rachis central plus épais
            paint.color = Color.argb(200, 15, 15, 20)
            paint.strokeWidth = 2f
            paint.style = Paint.Style.STROKE
            canvas.drawLine(centerX, baseY, centerX, baseY + length, paint)
            paint.style = Paint.Style.FILL
            
            canvas.restore()
        }
    }
    
    private fun drawCrowHead(canvas: Canvas, centerX: Float, centerY: Float, size: Float, eyeState: EyeState, windForce: Float, menace: Float) {
        // Gradient sombre de la tête
        val headGradient = RadialGradient(
            centerX, centerY, size * 0.8f,
            intArrayOf(charcoalColor, deepBlackColor, Color.rgb(10, 10, 15)),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.shader = headGradient
        
        // Forme de tête déformée par le vent et la menace
        val headDeform = windForce * 6f + menace * 8f
        val headOval = RectF(
            centerX - size/2 - headDeform, centerY - size/2,
            centerX + size/2 + headDeform, centerY + size/2
        )
        canvas.drawOval(headOval, paint)
        paint.shader = null
        
        // Crête hérissée de manière sinistre
        drawSinisterCrest(canvas, centerX, centerY - size/2, size, windForce, menace)
        
        // Bec crochu et acéré
        drawHookedBeak(canvas, centerX + size/2, centerY, size * 0.3f, menace)
        
        // Yeux perçants et inquiétants
        drawPiercingEyes(canvas, centerX, centerY, size, eyeState, windForce, menace)
    }
    
    private fun drawSinisterCrest(canvas: Canvas, centerX: Float, topY: Float, headSize: Float, windForce: Float, menace: Float) {
        paint.color = deepBlackColor
        
        val crestSpikes = 7
        val crestWidth = headSize * 0.5f
        val crestHeight = headSize * 0.25f * (1f + windForce * 0.4f + menace * 0.3f)
        
        val crestPath = Path()
        crestPath.moveTo(centerX - crestWidth/2, topY)
        
        for (i in 0..crestSpikes) {
            val x = centerX - crestWidth/2 + (i * crestWidth / crestSpikes)
            val spikeHeight = crestHeight * (0.6f + Random.nextFloat() * 0.8f)
            val windSway = sin(i * 0.7f) * windForce * 6f + menace * 4f
            val sharpness = menace * 8f
            
            if (i % 2 == 0) {
                crestPath.lineTo(x + windSway, topY - spikeHeight - sharpness)
            } else {
                crestPath.lineTo(x + windSway, topY - spikeHeight * 0.7f)
            }
        }
        
        crestPath.lineTo(centerX + crestWidth/2, topY)
        canvas.drawPath(crestPath, paint)
    }
    
    private fun drawHookedBeak(canvas: Canvas, startX: Float, startY: Float, length: Float, menace: Float) {
        // Bec crochu et menaçant
        paint.color = Color.rgb(35, 35, 40)
        
        val beakLength = length * (1f + menace * 0.3f)
        val hookSharpness = menace * 8f
        
        val beakPath = Path()
        beakPath.moveTo(startX, startY - beakLength * 0.2f)
        beakPath.lineTo(startX + beakLength, startY - beakLength * 0.1f)
        beakPath.quadTo(startX + beakLength * 1.2f + hookSharpness, startY, startX + beakLength * 0.9f, startY + beakLength * 0.2f + hookSharpness)
        beakPath.lineTo(startX, startY + beakLength * 0.2f)
        beakPath.close()
        
        canvas.drawPath(beakPath, paint)
        
        // Ligne sinistre de séparation
        paint.color = Color.argb(150, 20, 20, 25)
        paint.strokeWidth = 2f + menace
        paint.style = Paint.Style.STROKE
        canvas.drawLine(startX, startY, startX + beakLength * 0.8f, startY, paint)
        paint.style = Paint.Style.FILL
        
        // Narine sombre
        paint.color = Color.rgb(10, 10, 15)
        canvas.drawCircle(startX + beakLength * 0.25f, startY - beakLength * 0.08f, 3f + menace, paint)
    }
    
    private fun drawPiercingEyes(canvas: Canvas, centerX: Float, centerY: Float, headSize: Float, eyeState: EyeState, windForce: Float, menace: Float) {
        val eyeSize = headSize * 0.18f
        val leftEyeX = centerX - headSize * 0.22f
        val rightEyeX = centerX + headSize * 0.22f
        val eyeY = centerY - headSize * 0.08f
        val intensity = menace + windForce * 0.5f
        
        for ((eyeX, isLeft) in listOf(leftEyeX to true, rightEyeX to false)) {
            // Contour sombre et menaçant
            paint.color = Color.rgb(5, 5, 10)
            canvas.drawCircle(eyeX, eyeY, eyeSize + 2f + intensity, paint)
            
            // Blanc de l'œil (très réduit pour un look sinistre)
            paint.color = Color.rgb(180, 180, 185)
            val whiteSize = when (eyeState) {
                EyeState.NORMAL -> eyeSize * (0.6f - intensity * 0.2f)
                EyeState.SQUINTING -> eyeSize * 0.3f
                EyeState.STRUGGLING -> eyeSize * 0.2f
                EyeState.PANICKED -> eyeSize * 0.8f
            }
            
            if (eyeState == EyeState.SQUINTING || eyeState == EyeState.STRUGGLING) {
                // Yeux plissés sinistrement
                val eyeRect = RectF(eyeX - whiteSize, eyeY - whiteSize/4, eyeX + whiteSize, eyeY + whiteSize/4)
                canvas.drawOval(eyeRect, paint)
            } else {
                canvas.drawCircle(eyeX, eyeY, whiteSize, paint)
            }
            
            // Pupille dilatée et inquiétante
            paint.color = deepBlackColor
            val pupilSize = when (eyeState) {
                EyeState.NORMAL -> whiteSize * (0.7f + intensity * 0.2f)
                EyeState.SQUINTING -> whiteSize * 0.9f
                EyeState.STRUGGLING -> whiteSize * 0.95f
                EyeState.PANICKED -> whiteSize * 0.4f
            }
            
            val pupilOffsetX = windForce * (if (isLeft) -4f else 4f) + intensity * (if (isLeft) -2f else 2f)
            canvas.drawCircle(eyeX + pupilOffsetX, eyeY, pupilSize, paint)
            
            // Reflet sinistre qui pulse
            paint.color = Color.argb((120 + intensity * 80).toInt(), 200, 200, 220)
            val reflectSize = pupilSize * 0.25f * (1f + sin(System.currentTimeMillis() * 0.008f) * 0.3f)
            canvas.drawCircle(eyeX + pupilSize * 0.2f, eyeY - pupilSize * 0.2f, reflectSize, paint)
            
            // Vaisseaux sanguins si très stressé
            if (windForce > 0.8f || menace > 0.7f) {
                paint.color = bloodRed
                paint.strokeWidth = 1.5f
                paint.style = Paint.Style.STROKE
                for (i in 0..3) {
                    val angle = i * 90f + Random.nextFloat() * 30f
                    val endX = eyeX + cos(Math.toRadians(angle.toDouble())) * whiteSize * 0.9f
                    val endY = eyeY + sin(Math.toRadians(angle.toDouble())) * whiteSize * 0.9f
                    canvas.drawLine(eyeX, eyeY, endX.toFloat(), endY.toFloat(), paint)
                }
                paint.style = Paint.Style.FILL
            }
        }
    }
    
    private fun drawSharpTalons(canvas: Canvas, centerX: Float, y: Float, size: Float, menace: Float) {
        paint.color = Color.rgb(20, 20, 25)
        paint.strokeWidth = 5f + menace * 2f
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        
        val talonPositions = arrayOf(-size, -size/3, size/3, size)
        val sharpness = menace * 6f
        
        for (talonX in talonPositions) {
            val x = centerX + talonX
            // Patte qui descend
            canvas.drawLine(x, y - size/2, x, y + size/2, paint)
            
            // Griffes recourbées et acérées
            canvas.drawLine(x, y + size/2, x - 10f - sharpness, y + size/2 + 15f + sharpness, paint)
            canvas.drawLine(x, y + size/2, x + 10f + sharpness, y + size/2 + 15f + sharpness, paint)
            canvas.drawLine(x, y + size/2, x, y + size/2 + 18f + sharpness, paint)
            
            // Griffe arrière acérée
            canvas.drawLine(x, y + size/4, x - 8f - sharpness, y + size/2 + 8f + sharpness, paint)
        }
        
        paint.style = Paint.Style.FILL
    }
    
    private fun drawDeadBranch(canvas: Canvas, y: Float) {
        paint.color = deadBrown
        paint.strokeWidth = 15f
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        
        // Branche principale morte
        canvas.drawLine(0f, y, screenWidth, y, paint)
        
        // Écorce rugueuse et craquelée
        paint.strokeWidth = 2f
        paint.color = Color.rgb(40, 30, 20)
        for (i in 0..25) {
            val x = i * screenWidth / 25f
            val crackLength = Random.nextFloat() * 8f + 4f
            canvas.drawLine(x, y - crackLength, x + 2f, y + crackLength, paint)
        }
        
        // Quelques branches mortes qui dépassent
        paint.strokeWidth = 4f
        paint.color = deadBrown
        for (i in 0..3) {
            val x = Random.nextFloat() * screenWidth
            val branchLength = Random.nextFloat() * 40f + 20f
            val angle = Random.nextFloat() * 60f - 30f
            val endX = x + cos(Math.toRadians(angle.toDouble())) * branchLength
            val endY = y - sin(Math.toRadians(angle.toDouble())) * branchLength
            canvas.drawLine(x, y, endX.toFloat(), endY.toFloat(), paint)
        }
        
        paint.style = Paint.Style.FILL
    }
    
    private fun drawFallingCrow(canvas: Canvas, centerX: Float, centerY: Float, size: Float, fallProgress: Float) {
        // Corbeau en chute avec ailes déployées dans la terreur
        val wingSpread = size * (0.7f + fallProgress * 0.5f)
        val chaosFlap = sin(fallProgress * 35f) * 25f
        
        // Corps en chute libre
        paint.color = charcoalColor
        val bodyOval = RectF(centerX - size*0.25f, centerY - size*0.35f, centerX + size*0.25f, centerY + size*0.35f)
        canvas.drawOval(bodyOval, paint)
        
        // Ailes battant frénétiquement
        drawPanicWings(canvas, centerX, centerY, wingSpread, chaosFlap, fallProgress)
        
        // Tête en panique totale
        drawCrowHead(canvas, centerX, centerY - size*0.35f, size*0.35f, EyeState.PANICKED, 1f, 1f)
        
        // Traînée de plumes noires
        paint.color = Color.argb((150 * (1f - fallProgress)).toInt(), 25, 25, 30)
        for (i in 0..8) {
            val trailY = centerY - i * 25f * fallProgress
            val trailX = centerX + Random.nextFloat() * 30f - 15f
            canvas.drawCircle(trailX, trailY, (8f - i) * (1f - fallProgress), paint)
        }
    }
    
    private fun drawPanicWings(canvas: Canvas, centerX: Float, centerY: Float, spread: Float, flap: Float, chaos: Float) {
        paint.color = darkGrayColor
        
        // Aile gauche battant chaotiquement
        canvas.save()
        canvas.rotate(-40f + flap + chaos * 20f, centerX - spread/3, centerY)
        val leftWing = RectF(centerX - spread, centerY - spread/3, centerX - spread/3, centerY + spread/3)
        canvas.drawOval(leftWing, paint)
        canvas.restore()
        
        // Aile droite battant chaotiquement
        canvas.save()
        canvas.rotate(40f - flap - chaos * 20f, centerX + spread/3, centerY)
        val rightWing = RectF(centerX + spread/3, centerY - spread/3, centerX + spread, centerY + spread/3)
        canvas.drawOval(rightWing, paint)
        canvas.restore()
    }
    
    private fun drawDeadCrow(canvas: Canvas, centerX: Float, groundY: Float, size: Float) {
        // Corbeau effondré au sol
        paint.color = Color.argb(180, 35, 35, 40)
        
        // Corps effondré
        val bodyOval = RectF(centerX - size*0.4f, groundY - size*0.12f, centerX + size*0.4f, groundY + size*0.12f)
        canvas.drawOval(bodyOval, paint)
        
        // Ailes étalées au sol
        paint.color = Color.argb(140, 25, 25, 30)
        canvas.drawOval(centerX - size*0.7f, groundY - size*0.2f, centerX - size*0.1f, groundY + size*0.2f, paint)
        canvas.drawOval(centerX + size*0.1f, groundY - size*0.2f, centerX + size*0.7f, groundY + size*0.2f, paint)
        
        // Tête au sol, œil fermé
        paint.color = charcoalColor
        canvas.drawCircle(centerX + size*0.25f, groundY, size*0.15f, paint)
    }
    
    private fun drawOminousShadow(canvas: Canvas, centerX: Float, centerY: Float, size: Float, menace: Float) {
        val shadowIntensity = (80 + menace * 40).toInt().coerceIn(0, 120)
        shadowPaint.color = Color.argb(shadowIntensity, 0, 0, 0)
        val shadowSize = size * (0.4f + menace * 0.2f)
        val shadowOval = RectF(centerX - shadowSize, centerY - shadowSize*0.1f, centerX + shadowSize, centerY + shadowSize*0.1f)
        canvas.drawOval(shadowOval, shadowPaint)
    }
    
    fun drawParticles(canvas: Canvas, tears: List<Tear>, feathers: List<FlyingFeather>, dust: List<DustParticle>, leaves: List<FallingLeaf>) {
        // Larmes de sang
        paint.color = bloodRed
        for (tear in tears) {
            val alpha = (tear.life * 255).toInt().coerceIn(0, 255)
            paint.alpha = alpha
            
            // Forme de goutte de sang réaliste
            val tearPath = Path()
            tearPath.moveTo(tear.x, tear.y - 4f)
            tearPath.quadTo(tear.x + 3f, tear.y - 2f, tear.x + 2f, tear.y + 2f)
            tearPath.quadTo(tear.x, tear.y + 5f, tear.x - 2f, tear.y + 2f)
            tearPath.quadTo(tear.x - 3f, tear.y - 2f, tear.x, tear.y - 4f)
            canvas.drawPath(tearPath, paint)
        }
        
        // Plumes noires volantes
        paint.color = deepBlackColor
        for (feather in feathers) {
            val alpha = (feather.life * 220).toInt().coerceIn(0, 220)
            paint.alpha = alpha
            canvas.save()
            canvas.rotate(feather.rotation, feather.x, feather.y)
            
            // Forme de plume réaliste
            val featherPath = Path()
            featherPath.moveTo(feather.x, feather.y - 8f)
            featherPath.quadTo(feather.x - 4f, feather.y - 2f, feather.x - 2f, feather.y + 6f)
            featherPath.quadTo(feather.x, feather.y + 8f, feather.x + 2f, feather.y + 6f)
            featherPath.quadTo(feather.x + 4f, feather.y - 2f, feather.x, feather.y - 8f)
            canvas.drawPath(featherPath, paint)
            canvas.restore()
        }
        
        // Poussière sombre
        paint.color = ashGray
        for (particle in dust) {
            val alpha = (particle.life * 160).toInt().coerceIn(0, 160)
            paint.alpha = alpha
            canvas.drawCircle(particle.x, particle.y, particle.size, paint)
        }
        
        // Feuilles mortes
        paint.color = deadBrown
        for (leaf in leaves) {
            val alpha = (leaf.life * 140).toInt().coerceIn(0, 140)
            paint.alpha = alpha
            canvas.save()
            canvas.rotate(leaf.rotation, leaf.x, leaf.y)
            
            val leafPath = Path()
            leafPath.moveTo(leaf.x, leaf.y - 8f)
            leafPath.quadTo(leaf.x + 5f, leaf.y - 4f, leaf.x + 4f, leaf.y)
            leafPath.quadTo(leaf.x + 5f, leaf.y + 4f, leaf.x, leaf.y + 8f)
            leafPath.quadTo(leaf.x - 5f, leaf.y + 4f, leaf.x - 4f, leaf.y)
            leafPath.quadTo(leaf.x - 5f, leaf.y - 4f, leaf.x, leaf.y - 8f)
            canvas.drawPath(leafPath, paint)
            canvas.restore()
        }
        
        paint.alpha = 255
    }
}
