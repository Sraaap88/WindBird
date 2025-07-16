package com.example.windbird
 
import android.graphics.*
import kotlin.math.*
import kotlin.random.Random

class BirdRenderer(private val screenWidth: Float, private val screenHeight: Float) {
    
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    // Couleurs sombres et réalistes
    private val darkFeatherColor = Color.rgb(28, 28, 35)
    private val midFeatherColor = Color.rgb(45, 45, 55)
    private val lightFeatherColor = Color.rgb(65, 65, 75)
    private val bellyColor = Color.rgb(85, 85, 95)
    private val beakColor = Color.rgb(40, 40, 45)
    private val eyeColor = Color.rgb(15, 15, 20)
    private val bloodRedColor = Color.rgb(120, 20, 20)
    private val ashGray = Color.rgb(50, 50, 55)
    
    // Gradients pour plus de réalisme
    private var bodyGradient: LinearGradient? = null
    private var wingGradient: RadialGradient? = null
    
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
        
        // Ombre projetée
        drawBirdShadow(canvas, centerX, centerY + size * 0.1f, size)
        
        // Corps principal avec texture réaliste
        drawRealisticBody(canvas, centerX, centerY, bodyWidth, bodyHeight, windForce)
        
        // Ailes avec détails de plumes
        drawDetailedWings(canvas, centerX, centerY, size, windForce)
        
        // Tête avec expression sinistre
        drawDarkHead(canvas, centerX, centerY - bodyHeight * 0.3f, headSize, eyeState, windForce)
        
        // Queue avec plumes individuelles
        drawRealisticTail(canvas, centerX, centerY, size, windForce)
        
        // Pattes agrippées à la branche
        drawClaws(canvas, centerX, centerY + bodyHeight * 0.4f, size * 0.15f)
        
        // Branche morte
        drawDeadBranch(canvas, centerY + bodyHeight * 0.5f)
    }
    
    private fun drawRealisticBody(canvas: Canvas, centerX: Float, centerY: Float, width: Float, height: Float, windForce: Float) {
        // Gradient du corps
        bodyGradient = LinearGradient(
            centerX - width/2, centerY - height/2,
            centerX + width/2, centerY + height/2,
            intArrayOf(darkFeatherColor, midFeatherColor, lightFeatherColor),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.shader = bodyGradient
        
        // Corps principal avec déformation selon le vent
        val bodyPath = Path()
        val deformation = windForce * 15f
        
        bodyPath.moveTo(centerX - width/2 + deformation, centerY - height/2)
        bodyPath.quadTo(centerX + width/2 + deformation, centerY - height/4, centerX + width/3, centerY)
        bodyPath.quadTo(centerX + width/4, centerY + height/2, centerX, centerY + height/3)
        bodyPath.quadTo(centerX - width/4, centerY + height/2, centerX - width/3, centerY)
        bodyPath.quadTo(centerX - width/2 + deformation, centerY - height/4, centerX - width/2 + deformation, centerY - height/2)
        bodyPath.close()
        
        canvas.drawPath(bodyPath, paint)
        paint.shader = null
        
        // Texture de plumes
        drawFeatherTexture(canvas, centerX, centerY, width, height)
        
        // Ventre plus clair avec des stries
        paint.color = bellyColor
        val bellyOval = RectF(centerX - width*0.2f, centerY - height*0.1f, centerX + width*0.2f, centerY + height*0.3f)
        canvas.drawOval(bellyOval, paint)
        
        // Stries sombres sur le ventre
        paint.color = Color.argb(80, 30, 30, 40)
        paint.strokeWidth = 2f
        paint.style = Paint.Style.STROKE
        for (i in 0..4) {
            val y = centerY + (i - 2) * height * 0.08f
            canvas.drawLine(centerX - width*0.15f, y, centerX + width*0.15f, y, paint)
        }
        paint.style = Paint.Style.FILL
    }
    
    private fun drawFeatherTexture(canvas: Canvas, centerX: Float, centerY: Float, width: Float, height: Float) {
        paint.color = Color.argb(40, 20, 20, 25)
        paint.strokeWidth = 1f
        paint.style = Paint.Style.STROKE
        
        // Motifs de plumes subtils
        for (i in 0..8) {
            for (j in 0..6) {
                val x = centerX - width/2 + (i * width/8f)
                val y = centerY - height/2 + (j * height/6f)
                val featherPath = Path()
                featherPath.moveTo(x, y)
                featherPath.quadTo(x + 8f, y - 4f, x + 12f, y)
                featherPath.quadTo(x + 8f, y + 4f, x, y)
                canvas.drawPath(featherPath, paint)
            }
        }
        paint.style = Paint.Style.FILL
    }
    
    private fun drawDetailedWings(canvas: Canvas, centerX: Float, centerY: Float, size: Float, windForce: Float) {
        val wingSpan = size * 0.35f
        val wingHeight = size * 0.4f
        val wingFlap = sin(System.currentTimeMillis() * 0.01f + windForce * 20f) * windForce * 10f
        
        // Aile gauche
        drawSingleWing(canvas, centerX - wingSpan/2, centerY, wingSpan, wingHeight, -wingFlap, true)
        
        // Aile droite  
        drawSingleWing(canvas, centerX + wingSpan/2, centerY, wingSpan, wingHeight, wingFlap, false)
    }
    
    private fun drawSingleWing(canvas: Canvas, x: Float, y: Float, width: Float, height: Float, angle: Float, isLeft: Boolean) {
        canvas.save()
        canvas.rotate(angle, x, y)
        
        // Gradient de l'aile
        wingGradient = RadialGradient(
            x, y, width,
            intArrayOf(darkFeatherColor, midFeatherColor, ashGray),
            floatArrayOf(0f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.shader = wingGradient
        
        // Forme de l'aile
        val wingPath = Path()
        val direction = if (isLeft) -1f else 1f
        
        wingPath.moveTo(x, y)
        wingPath.quadTo(x + direction * width * 0.3f, y - height * 0.4f, x + direction * width, y - height * 0.2f)
        wingPath.quadTo(x + direction * width * 0.8f, y, x + direction * width, y + height * 0.3f)
        wingPath.quadTo(x + direction * width * 0.4f, y + height * 0.4f, x, y + height * 0.1f)
        wingPath.close()
        
        canvas.drawPath(wingPath, paint)
        paint.shader = null
        
        // Plumes primaires
        paint.color = Color.argb(120, 25, 25, 30)
        paint.strokeWidth = 3f
        paint.style = Paint.Style.STROKE
        
        for (i in 1..5) {
            val featherX = x + direction * width * (0.4f + i * 0.12f)
            val featherY1 = y - height * 0.2f
            val featherY2 = y + height * 0.2f
            canvas.drawLine(featherX, featherY1, featherX, featherY2, paint)
        }
        
        paint.style = Paint.Style.FILL
        canvas.restore()
    }
    
    private fun drawDarkHead(canvas: Canvas, centerX: Float, centerY: Float, size: Float, eyeState: EyeState, windForce: Float) {
        // Tête avec gradient sombre
        val headGradient = RadialGradient(
            centerX, centerY, size,
            intArrayOf(midFeatherColor, darkFeatherColor, Color.rgb(15, 15, 20)),
            floatArrayOf(0f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.shader = headGradient
        
        // Déformation de la tête selon le vent
        val headDeform = windForce * 8f
        val headOval = RectF(
            centerX - size/2 - headDeform, centerY - size/2,
            centerX + size/2 + headDeform, centerY + size/2
        )
        canvas.drawOval(headOval, paint)
        paint.shader = null
        
        // Crête hérissée
        drawSpikyCrest(canvas, centerX, centerY - size/2, size, windForce)
        
        // Bec menaçant
        drawMenacingBeak(canvas, centerX + size/2, centerY, size * 0.3f)
        
        // Yeux sinistres
        drawDarkEyes(canvas, centerX, centerY, size, eyeState, windForce)
    }
    
    private fun drawSpikyCrest(canvas: Canvas, centerX: Float, topY: Float, headSize: Float, windForce: Float) {
        paint.color = darkFeatherColor
        
        val crestPath = Path()
        val spikes = 7
        val crestWidth = headSize * 0.6f
        val crestHeight = headSize * 0.3f * (1f + windForce * 0.5f)
        
        crestPath.moveTo(centerX - crestWidth/2, topY)
        
        for (i in 0..spikes) {
            val x = centerX - crestWidth/2 + (i * crestWidth / spikes)
            val spikeHeight = crestHeight * (0.5f + Random.nextFloat() * 0.5f)
            val windSway = sin(i.toFloat()) * windForce * 5f
            
            if (i % 2 == 0) {
                crestPath.lineTo(x + windSway, topY - spikeHeight)
            } else {
                crestPath.lineTo(x + windSway, topY - spikeHeight * 0.6f)
            }
        }
        
        crestPath.lineTo(centerX + crestWidth/2, topY)
        canvas.drawPath(crestPath, paint)
    }
    
    private fun drawMenacingBeak(canvas: Canvas, startX: Float, startY: Float, length: Float) {
        // Bec crochu et pointu
        paint.color = beakColor
        
        val beakPath = Path()
        beakPath.moveTo(startX, startY - length * 0.2f)
        beakPath.lineTo(startX + length, startY - length * 0.1f)
        beakPath.quadTo(startX + length * 1.1f, startY, startX + length * 0.9f, startY + length * 0.15f)
        beakPath.lineTo(startX, startY + length * 0.2f)
        beakPath.close()
        
        canvas.drawPath(beakPath, paint)
        
        // Ligne de séparation du bec
        paint.color = Color.argb(100, 20, 20, 25)
        paint.strokeWidth = 2f
        paint.style = Paint.Style.STROKE
        canvas.drawLine(startX, startY, startX + length * 0.8f, startY, paint)
        paint.style = Paint.Style.FILL
        
        // Narine
        paint.color = Color.argb(150, 10, 10, 15)
        canvas.drawCircle(startX + length * 0.2f, startY - length * 0.08f, 3f, paint)
    }
    
    private fun drawDarkEyes(canvas: Canvas, centerX: Float, centerY: Float, headSize: Float, eyeState: EyeState, windForce: Float) {
        val eyeSize = headSize * 0.12f
        val leftEyeX = centerX - headSize * 0.2f
        val rightEyeX = centerX + headSize * 0.2f
        val eyeY = centerY - headSize * 0.1f
        
        for ((eyeX, isLeft) in listOf(leftEyeX to true, rightEyeX to false)) {
            // Contour de l'œil
            paint.color = Color.rgb(10, 10, 15)
            canvas.drawCircle(eyeX, eyeY, eyeSize, paint)
            
            // Blanc de l'œil (très réduit pour un look sinistre)
            paint.color = Color.rgb(180, 180, 185)
            val whiteSize = when (eyeState) {
                EyeState.NORMAL -> eyeSize * 0.7f
                EyeState.SQUINTING -> eyeSize * 0.4f
                EyeState.STRUGGLING -> eyeSize * 0.3f
                EyeState.PANICKED -> eyeSize * 0.9f
            }
            canvas.drawCircle(eyeX, eyeY, whiteSize, paint)
            
            // Pupille dilatée et menaçante
            paint.color = eyeColor
            val pupilSize = when (eyeState) {
                EyeState.NORMAL -> whiteSize * 0.6f
                EyeState.SQUINTING -> whiteSize * 0.8f
                EyeState.STRUGGLING -> whiteSize * 0.9f
                EyeState.PANICKED -> whiteSize * 0.4f
            }
            
            val pupilOffsetX = windForce * (if (isLeft) -3f else 3f)
            canvas.drawCircle(eyeX + pupilOffsetX, eyeY, pupilSize, paint)
            
            // Reflet inquiétant
            paint.color = Color.argb(100, 200, 200, 210)
            canvas.drawCircle(eyeX + pupilSize * 0.3f, eyeY - pupilSize * 0.3f, pupilSize * 0.2f, paint)
            
            // Vaisseaux sanguins si stressé
            if (windForce > 0.7f) {
                paint.color = bloodRedColor
                paint.strokeWidth = 1f
                paint.style = Paint.Style.STROKE
                for (i in 0..2) {
                    val angle = i * 120f + Random.nextFloat() * 20f
                    val endX = eyeX + cos(Math.toRadians(angle.toDouble())) * whiteSize * 0.8f
                    val endY = eyeY + sin(Math.toRadians(angle.toDouble())) * whiteSize * 0.8f
                    canvas.drawLine(eyeX, eyeY, endX.toFloat(), endY.toFloat(), paint)
                }
                paint.style = Paint.Style.FILL
            }
        }
    }
    
    private fun drawRealisticTail(canvas: Canvas, centerX: Float, centerY: Float, size: Float, windForce: Float) {
        val tailFeathers = 5
        val tailLength = size * 0.3f
        val tailSpread = size * 0.15f
        
        for (i in 0 until tailFeathers) {
            val angle = -30f + (i * 15f) + windForce * 20f
            val length = tailLength * (0.8f + Random.nextFloat() * 0.4f)
            
            canvas.save()
            canvas.rotate(angle, centerX, centerY)
            
            paint.color = when (i % 3) {
                0 -> darkFeatherColor
                1 -> midFeatherColor
                else -> lightFeatherColor
            }
            
            val featherPath = Path()
            featherPath.moveTo(centerX - tailSpread/2, centerY + size * 0.2f)
            featherPath.quadTo(centerX - tailSpread/4, centerY + size * 0.2f + length/2, centerX, centerY + size * 0.2f + length)
            featherPath.quadTo(centerX + tailSpread/4, centerY + size * 0.2f + length/2, centerX + tailSpread/2, centerY + size * 0.2f)
            featherPath.close()
            
            canvas.drawPath(featherPath, paint)
            canvas.restore()
        }
    }
    
    private fun drawClaws(canvas: Canvas, centerX: Float, y: Float, size: Float) {
        paint.color = Color.rgb(25, 25, 30)
        paint.strokeWidth = 4f
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        
        // Pattes agrippées
        val clawPositions = arrayOf(-size, -size/3, size/3, size)
        
        for (clawX in clawPositions) {
            val x = centerX + clawX
            // Patte qui descend
            canvas.drawLine(x, y - size/2, x, y + size/2, paint)
            // Griffe recourbée
            canvas.drawLine(x, y + size/2, x - 8f, y + size/2 + 12f, paint)
            canvas.drawLine(x, y + size/2, x + 8f, y + size/2 + 12f, paint)
        }
        
        paint.style = Paint.Style.FILL
    }
    
    private fun drawDeadBranch(canvas: Canvas, y: Float) {
        paint.color = Color.rgb(40, 35, 30)
        paint.strokeWidth = 12f
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        
        // Branche principale
        canvas.drawLine(0f, y, screenWidth, y, paint)
        
        // Écorce rugueuse
        paint.strokeWidth = 2f
        paint.color = Color.rgb(30, 25, 20)
        for (i in 0..20) {
            val x = i * screenWidth / 20f
            canvas.drawLine(x, y - 6f, x, y + 6f, paint)
        }
        
        paint.style = Paint.Style.FILL
    }
    
    private fun drawFallingBird(canvas: Canvas, centerX: Float, centerY: Float, size: Float, fallProgress: Float) {
        // Oiseau qui tombe avec ailes déployées dans la panique
        val wingSpread = size * (0.6f + fallProgress * 0.4f)
        val panicFlap = sin(fallProgress * 30f) * 20f
        
        // Corps en chute
        paint.color = darkFeatherColor
        val bodyOval = RectF(centerX - size*0.2f, centerY - size*0.3f, centerX + size*0.2f, centerY + size*0.3f)
        canvas.drawOval(bodyOval, paint)
        
        // Ailes battant frénétiquement
        drawPanicWings(canvas, centerX, centerY, wingSpread, panicFlap)
        
        // Tête en panique
        drawDarkHead(canvas, centerX, centerY - size*0.3f, size*0.3f, EyeState.PANICKED, 1f)
        
        // Traînée de plumes
        paint.color = Color.argb((100 * (1f - fallProgress)).toInt(), 45, 45, 55)
        for (i in 0..5) {
            val trailY = centerY - i * 20f * fallProgress
            canvas.drawCircle(centerX + Random.nextFloat() * 20f - 10f, trailY, 5f * (1f - fallProgress), paint)
        }
    }
    
    private fun drawPanicWings(canvas: Canvas, centerX: Float, centerY: Float, spread: Float, flap: Float) {
        paint.color = midFeatherColor
        
        // Aile gauche battant
        canvas.save()
        canvas.rotate(-30f + flap, centerX - spread/3, centerY)
        val leftWing = RectF(centerX - spread, centerY - spread/3, centerX - spread/3, centerY + spread/3)
        canvas.drawOval(leftWing, paint)
        canvas.restore()
        
        // Aile droite battant
        canvas.save()
        canvas.rotate(30f - flap, centerX + spread/3, centerY)
        val rightWing = RectF(centerX + spread/3, centerY - spread/3, centerX + spread, centerY + spread/3)
        canvas.drawOval(rightWing, paint)
        canvas.restore()
    }
    
    private fun drawFallenBird(canvas: Canvas, centerX: Float, groundY: Float, size: Float) {
        // Oiseau au sol, ailes étalées
        paint.color = Color.argb(200, 45, 45, 55)
        
        // Corps effondré
        val bodyOval = RectF(centerX - size*0.3f, groundY - size*0.1f, centerX + size*0.3f, groundY + size*0.1f)
        canvas.drawOval(bodyOval, paint)
        
        // Ailes étalées au sol
        paint.color = Color.argb(150, 35, 35, 45)
        canvas.drawOval(centerX - size*0.6f, groundY - size*0.2f, centerX - size*0.1f, groundY + size*0.2f, paint)
        canvas.drawOval(centerX + size*0.1f, groundY - size*0.2f, centerX + size*0.6f, groundY + size*0.2f, paint)
        
        // Tête au sol
        paint.color = darkFeatherColor
        canvas.drawCircle(centerX + size*0.2f, groundY, size*0.15f, paint)
    }
    
    private fun drawBirdShadow(canvas: Canvas, centerX: Float, centerY: Float, size: Float) {
        shadowPaint.color = Color.argb(40, 0, 0, 0)
        val shadowOval = RectF(centerX - size*0.3f, centerY - size*0.1f, centerX + size*0.3f, centerY + size*0.1f)
        canvas.drawOval(shadowOval, shadowPaint)
    }
    
    fun drawParticles(canvas: Canvas, tears: List<Tear>, feathers: List<FlyingFeather>, dust: List<DustParticle>, leaves: List<FallingLeaf>) {
        // Larmes de sang
        paint.color = bloodRedColor
        for (tear in tears) {
            val alpha = (tear.life * 255).toInt().coerceIn(0, 255)
            paint.alpha = alpha
            canvas.drawCircle(tear.x, tear.y, 4f, paint)
        }
        
        // Plumes sombres volantes
        paint.color = darkFeatherColor
        for (feather in feathers) {
            val alpha = (feather.life * 200).toInt().coerceIn(0, 200)
            paint.alpha = alpha
            canvas.save()
            canvas.rotate(feather.rotation, feather.x, feather.y)
            canvas.drawOval(feather.x - 8f, feather.y - 3f, feather.x + 8f, feather.y + 3f, paint)
            canvas.restore()
        }
        
        // Poussière grise
        paint.color = ashGray
        for (particle in dust) {
            val alpha = (particle.life * 180).toInt().coerceIn(0, 180)
            paint.alpha = alpha
            canvas.drawCircle(particle.x, particle.y, particle.size, paint)
        }
        
        // Feuilles mortes
        paint.color = Color.rgb(60, 50, 40)
        for (leaf in leaves) {
            val alpha = (leaf.life * 150).toInt().coerceIn(0, 150)
            paint.alpha = alpha
            canvas.save()
            canvas.rotate(leaf.rotation, leaf.x, leaf.y)
            
            val leafPath = Path()
            leafPath.moveTo(leaf.x, leaf.y - 6f)
            leafPath.quadTo(leaf.x + 4f, leaf.y - 3f, leaf.x + 3f, leaf.y)
            leafPath.quadTo(leaf.x + 4f, leaf.y + 3f, leaf.x, leaf.y + 6f)
            leafPath.quadTo(leaf.x - 4f, leaf.y + 3f, leaf.x - 3f, leaf.y)
            leafPath.quadTo(leaf.x - 4f, leaf.y - 3f, leaf.x, leaf.y - 6f)
            canvas.drawPath(leafPath, paint)
            canvas.restore()
        }
        
        paint.alpha = 255
    }
}
