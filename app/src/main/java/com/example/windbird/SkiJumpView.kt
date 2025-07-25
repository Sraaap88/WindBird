package com.example.windbird

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

class SkiJumpView(context: Context, private val activity: SkiJumpActivity) : View(context) {
    private val paint = Paint()
    
    private var skierBitmap: Bitmap? = null
    private var skierJumpBitmap: Bitmap? = null
    private var skierFlightBitmap: Bitmap? = null
    private var skierLand1Bitmap: Bitmap? = null
    private var skierLand2Bitmap: Bitmap? = null
    private var skierLand3Bitmap: Bitmap? = null
    
    private var preparationBitmap: Bitmap? = null
    
    private var flagCanadaBitmap: Bitmap? = null
    private var flagUsaBitmap: Bitmap? = null
    private var flagFranceBitmap: Bitmap? = null
    private var flagNorvegeBitmap: Bitmap? = null
    private var flagJapanBitmap: Bitmap? = null
    
    init {
        try {
            skierBitmap = BitmapFactory.decodeResource(resources, R.drawable.skier_approach)
            skierJumpBitmap = BitmapFactory.decodeResource(resources, R.drawable.skier_jump)
            skierFlightBitmap = BitmapFactory.decodeResource(resources, R.drawable.skier_flight)
            skierLand1Bitmap = BitmapFactory.decodeResource(resources, R.drawable.skier_land1)
            skierLand2Bitmap = BitmapFactory.decodeResource(resources, R.drawable.skier_land2)
            skierLand3Bitmap = BitmapFactory.decodeResource(resources, R.drawable.skier_land3)
            
            preparationBitmap = BitmapFactory.decodeResource(resources, R.drawable.ski_jump_preparation)
            
            flagCanadaBitmap = BitmapFactory.decodeResource(resources, R.drawable.flag_canada)
            flagUsaBitmap = BitmapFactory.decodeResource(resources, R.drawable.flag_usa)
            flagFranceBitmap = BitmapFactory.decodeResource(resources, R.drawable.flag_france)
            flagNorvegeBitmap = BitmapFactory.decodeResource(resources, R.drawable.flag_norvege)
            flagJapanBitmap = BitmapFactory.decodeResource(resources, R.drawable.flag_japan)
            
        } catch (e: Exception) {
            createFallbackSkierBitmaps()
            createFallbackFlagBitmaps()
            createFallbackPreparationBitmap()
        }
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            activity.handleScreenTap()
            return true
        }
        return super.onTouchEvent(event)
    }
    
    private fun createFallbackFlagBitmaps() {
        flagCanadaBitmap = createFallbackFlag(Color.RED, Color.WHITE)
        flagUsaBitmap = createFallbackFlag(Color.BLUE, Color.RED, Color.WHITE)
        flagFranceBitmap = createFallbackFlag(Color.BLUE, Color.WHITE, Color.RED)
        flagNorvegeBitmap = createFallbackFlag(Color.RED, Color.WHITE, Color.BLUE)
        flagJapanBitmap = createFallbackFlag(Color.WHITE, Color.RED)
    }
    
    private fun createFallbackPreparationBitmap() {
        preparationBitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(preparationBitmap!!)
        val tempPaint = Paint().apply { style = Paint.Style.FILL }
        
        tempPaint.color = Color.parseColor("#87CEEB")
        canvas.drawRect(0f, 0f, 800f, 600f, tempPaint)
        
        tempPaint.color = Color.WHITE
        val path = Path()
        path.moveTo(50f, 500f)
        path.lineTo(200f, 300f)
        path.lineTo(700f, 400f)
        path.lineTo(750f, 500f)
        path.lineTo(750f, 600f)
        path.lineTo(50f, 600f)
        path.close()
        canvas.drawPath(path, tempPaint)
        
        tempPaint.color = Color.WHITE
        canvas.drawRect(600f, 50f, 750f, 150f, tempPaint)
    }
    
    private fun createFallbackFlag(vararg colors: Int): Bitmap {
        val flagBitmap = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(flagBitmap)
        val tempPaint = Paint().apply { style = Paint.Style.FILL }
        
        when (colors.size) {
            2 -> {
                tempPaint.color = colors[0]
                canvas.drawRect(0f, 0f, 300f, 200f, tempPaint)
                tempPaint.color = colors[1]
                canvas.drawCircle(150f, 100f, 50f, tempPaint)
            }
            3 -> {
                tempPaint.color = colors[0]
                canvas.drawRect(0f, 0f, 100f, 200f, tempPaint)
                tempPaint.color = colors[1]
                canvas.drawRect(100f, 0f, 200f, 200f, tempPaint)
                tempPaint.color = colors[2]
                canvas.drawRect(200f, 0f, 300f, 200f, tempPaint)
            }
            else -> {
                tempPaint.color = colors[0]
                canvas.drawRect(0f, 0f, 300f, 200f, tempPaint)
            }
        }
        
        return flagBitmap
    }
    
    private fun createFallbackSkierBitmaps() {
        skierBitmap = Bitmap.createBitmap(60, 80, Bitmap.Config.ARGB_8888)
        val canvas1 = Canvas(skierBitmap!!)
        val tempPaint = Paint().apply {
            color = Color.parseColor("#FF4444")
            style = Paint.Style.FILL
        }
        
        canvas1.drawRect(20f, 20f, 40f, 60f, tempPaint)
        canvas1.drawCircle(30f, 15f, 10f, tempPaint)
        
        tempPaint.color = Color.YELLOW
        canvas1.drawRect(15f, 55f, 18f, 75f, tempPaint)
        canvas1.drawRect(42f, 55f, 45f, 75f, tempPaint)
        
        skierJumpBitmap = Bitmap.createBitmap(100, 60, Bitmap.Config.ARGB_8888)
        val canvas2 = Canvas(skierJumpBitmap!!)
        tempPaint.color = Color.parseColor("#FF4444")
        canvas2.drawRect(20f, 20f, 80f, 40f, tempPaint)
        canvas2.drawCircle(15f, 30f, 10f, tempPaint)
        
        skierFlightBitmap = Bitmap.createBitmap(120, 50, Bitmap.Config.ARGB_8888)
        val canvas3 = Canvas(skierFlightBitmap!!)
        tempPaint.color = Color.parseColor("#FF4444")
        canvas3.drawRect(30f, 15f, 90f, 35f, tempPaint)
        canvas3.drawCircle(25f, 25f, 10f, tempPaint)
        
        skierLand1Bitmap = Bitmap.createBitmap(80, 70, Bitmap.Config.ARGB_8888)
        val canvas4 = Canvas(skierLand1Bitmap!!)
        tempPaint.color = Color.parseColor("#FF4444")
        canvas4.drawRect(20f, 30f, 60f, 50f, tempPaint)
        canvas4.drawCircle(15f, 35f, 10f, tempPaint)
        
        skierLand2Bitmap = Bitmap.createBitmap(90, 80, Bitmap.Config.ARGB_8888)
        val canvas5 = Canvas(skierLand2Bitmap!!)
        canvas5.drawRect(25f, 40f, 65f, 70f, tempPaint)
        canvas5.drawCircle(45f, 30f, 10f, tempPaint)
        
        skierLand3Bitmap = Bitmap.createBitmap(70, 90, Bitmap.Config.ARGB_8888)
        val canvas6 = Canvas(skierLand3Bitmap!!)
        canvas6.drawRect(25f, 30f, 45f, 70f, tempPaint)
        canvas6.drawCircle(35f, 20f, 10f, tempPaint)
        canvas6.drawCircle(15f, 25f, 5f, tempPaint)
        canvas6.drawCircle(55f, 25f, 5f, tempPaint)
    }

    override fun onDraw(canvas: Canvas) {
        val w = canvas.width
        val h = canvas.height
        
        if (activity.getCameraShake() > 0f) {
            canvas.save()
            canvas.translate(
                (kotlin.random.Random.nextFloat() - 0.5f) * activity.getCameraShake() * 10f,
                (kotlin.random.Random.nextFloat() - 0.5f) * activity.getCameraShake() * 10f
            )
        }
        
        when (activity.getGameState()) {
            SkiJumpActivity.GameState.PREPARATION -> drawPreparation(canvas, w, h)
            SkiJumpActivity.GameState.APPROACH -> drawApproach(canvas, w, h)
            SkiJumpActivity.GameState.TAKEOFF -> drawTakeoff(canvas, w, h)
            SkiJumpActivity.GameState.FLIGHT -> drawFlight(canvas, w, h)
            SkiJumpActivity.GameState.LANDING -> drawLanding(canvas, w, h)
            SkiJumpActivity.GameState.RESULTS -> drawResults(canvas, w, h)
            SkiJumpActivity.GameState.FINISHED -> drawResults(canvas, w, h)
        }
        
        drawSnowParticles(canvas, w, h)
        
        if (activity.getCameraShake() > 0f) {
            canvas.restore()
        }
    }
    
    private fun getPlayerFlagBitmap(): Bitmap? {
        if (activity.getPracticeMode()) {
            return flagCanadaBitmap
        }
        
        val playerCountry = activity.getTournamentData().playerCountries[activity.getCurrentPlayerIndex()]
        return when (playerCountry.uppercase()) {
            "CANADA" -> flagCanadaBitmap
            "USA", "ÉTATS-UNIS", "ETATS-UNIS" -> flagUsaBitmap
            "FRANCE" -> flagFranceBitmap
            "NORVÈGE", "NORWAY" -> flagNorvegeBitmap
            "JAPON", "JAPAN" -> flagJapanBitmap
            else -> flagCanadaBitmap
        }
    }
    
    private fun drawPreparation(canvas: Canvas, w: Int, h: Int) {
        preparationBitmap?.let { prep ->
            val dstRect = RectF(0f, 0f, w.toFloat(), h.toFloat())
            canvas.drawBitmap(prep, null, dstRect, paint)
        }
        
        // Rectangle et drapeau parfaitement centrés
        val flagRectWidth = w * 0.22f
        val flagRectHeight = h * 0.22f
        val flagRectX = w * 0.39f
        val flagRectY = h * 0.08f
        
        val flagBitmap = getPlayerFlagBitmap()
        flagBitmap?.let { flag ->
            val flagWidth = flagRectWidth * 0.9f
            val flagHeight = flagRectHeight * 0.8f
            
            val flagX = flagRectX + (flagRectWidth - flagWidth) / 2f
            val flagY = flagRectY + (flagRectHeight - flagHeight) / 2f
            
            val flagDstRect = RectF(flagX, flagY, flagX + flagWidth, flagY + flagHeight)
            canvas.drawBitmap(flag, null, flagDstRect, paint)
        }
        
        paint.color = Color.WHITE
        paint.textSize = 112f
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("🎿 SAUT À SKI 🎿", w/2f, h * 0.35f, paint)
        
        paint.textSize = 80f
        canvas.drawText("Préparez-vous...", w/2f, h * 0.47f, paint) // Plus espacé
        
        paint.textSize = 72f
        paint.color = Color.YELLOW
        canvas.drawText("Dans ${(activity.getPreparationDuration() - activity.getPhaseTimer()).toInt() + 1} secondes", w/2f, h * 0.57f, paint) // Plus espacé
        
        paint.textSize = 80f
        paint.color = Color.parseColor("#00FF00") // Vert brillant au lieu de turquoise
        canvas.drawText("📱 2 TAPS sur l'écran pour démarrer", w/2f, h * 0.72f, paint) // Plus espacé
        canvas.drawText("📱 SUIVEZ la zone verte qui descend", w/2f, h * 0.80f, paint) // Plus espacé
        canvas.drawText("📱 COUP DE FOUET au moment du saut", w/2f, h * 0.88f, paint) // Plus espacé
        
        paint.typeface = Typeface.DEFAULT
    }
    
    private fun drawApproach(canvas: Canvas, w: Int, h: Int) {
        paint.color = Color.parseColor("#87CEEB")
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        
        // Tremplin avec côtés en arc pour la perspective
        paint.color = Color.WHITE
        val jumpPath = Path()
        
        jumpPath.moveTo(w * 0.15f, h * 0.95f)
        jumpPath.lineTo(w * 0.85f, h * 0.95f)
        jumpPath.quadTo(w * 0.75f, h * 0.5f, w * 0.55f, h * 0.05f)
        jumpPath.lineTo(w * 0.45f, h * 0.05f)
        jumpPath.quadTo(w * 0.25f, h * 0.5f, w * 0.15f, h * 0.95f)
        jumpPath.close()
        canvas.drawPath(jumpPath, paint)
        
        // Lignes de guidage courbées
        paint.color = Color.parseColor("#CCCCCC")
        paint.strokeWidth = 4f
        paint.style = Paint.Style.STROKE
        for (i in 1..12) {
            val progress = i / 12f
            val lineY = h * (0.95f - progress * 0.9f)
            
            val widthFactor = 1f - progress * 0.6f
            val leftX = w * (0.15f + progress * 0.3f) * widthFactor + w * (1f - widthFactor) * 0.5f
            val rightX = w * (0.85f - progress * 0.3f) * widthFactor + w * (1f - widthFactor) * 0.5f
            canvas.drawLine(leftX, lineY, rightX, lineY, paint)
        }
        paint.style = Paint.Style.FILL
        
        if (activity.getTapCount() >= 2) {
            drawAngleControlBar(canvas, w, h)
        }
        
        val skierX = w / 2f
        val skierY: Float
        val scale: Float
        
        if (activity.getTapCount() < 2) {
            skierY = h * 0.9f
            scale = 0.84f
        } else {
            val zoneProgress = activity.getZoneProgress()
            skierY = h * (0.9f - zoneProgress * 0.85f)
            scale = 0.84f - zoneProgress * 0.72f
        }
        
        skierBitmap?.let { bmp ->
            val dstRect = RectF(
                skierX - bmp.width * scale / 2f,
                skierY - bmp.height * scale / 2f,
                skierX + bmp.width * scale / 2f,
                skierY + bmp.height * scale / 2f
            )
            canvas.drawBitmap(bmp, null, dstRect, paint)
        }
        
        if (activity.getTapCount() < 2) {
            paint.color = Color.RED
            paint.textSize = 120f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("👆 TAPPEZ L'ÉCRAN! 👆", w/2f, h * 0.15f, paint)
            
            paint.color = Color.YELLOW
            paint.textSize = 80f
            canvas.drawText("Poussées: ${activity.getTapCount()}/2", w/2f, h * 0.25f, paint)
            
            if (activity.getTapCount() == 1) {
                paint.color = Color.CYAN
                paint.textSize = 60f
                canvas.drawText("ENCORE UNE FOIS!", w/2f, h * 0.32f, paint)
            }
        } else {
            paint.color = Color.BLUE
            paint.textSize = 110f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("📐 SUIVEZ LA ZONE VERTE!", w/2f, h * 0.15f, paint)
            
            paint.color = if (activity.getInTargetZone()) Color.GREEN else Color.RED
            paint.textSize = 70f
            canvas.drawText("${activity.getSpeed().toInt()} KM/H ${if (activity.getInTargetZone()) "✅" else "❌"}", w/2f, h * 0.25f, paint)
            
            paint.color = Color.CYAN
            paint.textSize = 50f
            canvas.drawText("Angle: ${activity.getIntegratedTiltY().toInt()}°", w/2f, h * 0.32f, paint)
        }
        
        drawSpeedMeter(canvas, w, h)
    }
    
    private fun drawAngleControlBar(canvas: Canvas, w: Int, h: Int) {
        val barWidth = 80f
        val barHeight = h * 0.42f // 30% plus court (0.6f * 0.7)
        val barX = 50f
        val barY = h * 0.2f
        
        paint.color = Color.parseColor("#333333")
        paint.style = Paint.Style.FILL
        canvas.drawRect(barX, barY, barX + barWidth, barY + barHeight, paint)
        
        val zoneCenter = activity.getTargetZoneCenter()
        val zoneSize = activity.getTargetZoneSize()
        
        val maxAngle = 50f
        val zoneCenterPos = (zoneCenter / maxAngle) * barHeight
        val zoneSizePos = (zoneSize / maxAngle) * barHeight
        
        val zoneTop = barY + zoneCenterPos - zoneSizePos / 2f
        val zoneBottom = barY + zoneCenterPos + zoneSizePos / 2f
        
        paint.color = if (activity.getInTargetZone()) Color.GREEN else Color.parseColor("#006600")
        canvas.drawRect(barX + 5f, zoneTop, barX + barWidth - 5f, zoneBottom, paint)
        
        val currentAngle = activity.getIntegratedTiltY().coerceIn(0f, maxAngle)
        val currentPos = (currentAngle / maxAngle) * barHeight
        
        paint.color = if (activity.getInTargetZone()) Color.YELLOW else Color.RED
        val indicatorY = barY + currentPos
        canvas.drawRect(barX, indicatorY - 8f, barX + barWidth, indicatorY + 8f, paint)
        
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        canvas.drawRect(barX, barY, barX + barWidth, barY + barHeight, paint)
        paint.style = Paint.Style.FILL
        
        paint.color = Color.WHITE
        paint.textSize = 24f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("ANGLE", barX, barY - 20f, paint)
        canvas.drawText("${currentAngle.toInt()}°", barX, barY + barHeight + 30f, paint)
        
        paint.textSize = 18f
        paint.color = Color.CYAN
        for (i in 0..4) {
            val angle = i * 10f
            val pos = (angle / maxAngle) * barHeight
            val y = barY + pos
            canvas.drawText("${angle.toInt()}°", barX + barWidth + 10f, y + 6f, paint)
        }
    }
    
    private fun drawTakeoff(canvas: Canvas, w: Int, h: Int) {
        paint.color = Color.parseColor("#87CEEB")
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        
        paint.color = Color.WHITE
        val rampPath = Path()
        rampPath.moveTo(0f, h * 0.9f)
        rampPath.quadTo(w * 0.6f, h * 0.7f, w * 0.8f, h * 0.5f)
        rampPath.lineTo(w * 0.85f, h * 0.52f)
        rampPath.lineTo(w * 0.85f, h.toFloat())
        rampPath.lineTo(0f, h.toFloat())
        rampPath.close()
        canvas.drawPath(rampPath, paint)
        
        val takeoffProgress = activity.getPhaseTimer() / activity.getTakeoffDuration()
        val criticalZone = takeoffProgress >= 0.85f
        val userIsPulling = activity.getTiltY() < -0.15f
        
        val skierX = w * (0.1f + takeoffProgress * 0.7f)
        
        val skierY = when {
            takeoffProgress < 0.5f -> {
                val rampProgress = takeoffProgress / 0.5f
                h * (0.9f - rampProgress * 0.3f)
            }
            takeoffProgress < 0.8f -> {
                val curveProgress = (takeoffProgress - 0.5f) / 0.3f
                h * (0.6f - curveProgress * 0.15f)
            }
            else -> {
                val jumpProgress = (takeoffProgress - 0.8f) / 0.2f
                val baseY = h * 0.45f
                baseY - jumpProgress * h * 0.25f
            }
        }
        
        val rotation = when {
            takeoffProgress < 0.5f -> -takeoffProgress * 20f
            takeoffProgress < 0.8f -> -10f
            else -> (activity.getTakeoffPower() / 120f) * 15f - 10f
        }
        
        canvas.save()
        canvas.translate(skierX, skierY)
        canvas.rotate(rotation)
        
        val scale = 0.4f
        
        val currentBitmap = if (activity.getHasUsedJumpImage()) {
            skierFlightBitmap
        } else if (criticalZone && userIsPulling) {
            skierFlightBitmap
        } else {
            skierJumpBitmap
        }
        
        currentBitmap?.let { bmp ->
            val dstRect = RectF(
                -bmp.width * scale / 2f,
                -bmp.height * scale / 2f,
                bmp.width * scale / 2f,
                bmp.height * scale / 2f
            )
            canvas.drawBitmap(bmp, null, dstRect, paint)
        }
        
        canvas.restore()
        
        if (criticalZone) {
            paint.color = Color.RED
            paint.textSize = 140f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("🚀 MAINTENANT ! 🚀", w/2f, h * 0.15f, paint)
            
            paint.color = Color.YELLOW
            paint.textSize = 90f
            canvas.drawText("TIREZ FORT VERS VOUS!", w/2f, h * 0.25f, paint)
            
            if (userIsPulling) {
                paint.color = Color.GREEN
                paint.textSize = 60f
                canvas.drawText("✅ PARFAIT!", w/2f, h * 0.32f, paint)
            }
        } else {
            paint.color = Color.WHITE
            paint.textSize = 70f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("🏁 Maintenez l'angle...", w/2f, h * 0.15f, paint)
            
            paint.color = Color.CYAN
            paint.textSize = 50f
            canvas.drawText("${activity.getSpeed().toInt()} km/h", w/2f, h * 0.22f, paint)
        }
        
        drawTakeoffPowerMeter(canvas, w, h, criticalZone)
    }
    
    private fun drawFlight(canvas: Canvas, w: Int, h: Int) {
        paint.color = Color.parseColor("#87CEEB")
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        
        paint.color = Color.parseColor("#DDDDDD")
        val mountainPath = Path()
        val scrollOffset = (activity.getPhaseTimer() * 25f) % 200f
        mountainPath.moveTo(-scrollOffset, h * 0.7f)
        mountainPath.lineTo(w * 0.2f - scrollOffset, h * 0.4f)
        mountainPath.lineTo(w * 0.5f - scrollOffset, h * 0.6f)
        mountainPath.lineTo(w * 0.8f - scrollOffset, h * 0.3f)
        mountainPath.lineTo(w + 100f - scrollOffset, h * 0.5f)
        mountainPath.lineTo(w + 100f, h.toFloat())
        mountainPath.lineTo(-100f, h.toFloat())
        mountainPath.close()
        canvas.drawPath(mountainPath, paint)
        
        val flightProgress = activity.getPhaseTimer() / activity.getFlightDuration()
        val skierX = w * (-0.1f + flightProgress * 1.2f)
        
        val baseY = h * 0.4f
        val verticalOffset = activity.getTiltY() * 80f
        val skierY = baseY + verticalOffset
        
        canvas.save()
        canvas.translate(skierX, skierY)
        
        val skierRotation = activity.getTiltY() * 15f
        canvas.rotate(skierRotation)
        
        val windEffect = activity.getWindDirection() * activity.getWindStrength() * activity.getWindTransition()
        canvas.rotate(windEffect * 8f)
        
        val scale = 0.48f // 4 fois plus gros (0.12f * 4)
        
        skierFlightBitmap?.let { bmp ->
            val dstRect = RectF(
                -bmp.width * scale / 2f,
                -bmp.height * scale / 2f,
                bmp.width * scale / 2f,
                bmp.height * scale / 2f
            )
            canvas.drawBitmap(bmp, null, dstRect, paint)
        }
        
        canvas.restore()
        
        paint.color = Color.WHITE
        paint.alpha = 100
        for (i in 1..3) {
            val trailX = skierX - i * 30f
            val trailY = skierY + kotlin.random.Random.nextFloat() * 20f - 10f
            canvas.drawCircle(trailX, trailY, 6f, paint)
        }
        paint.alpha = 255
        
        val optimalAngle = 0.1f
        val currentAngle = activity.getTiltY()
        val angleError = abs(currentAngle - optimalAngle)
        
        if (angleError > 0.3f) {
            paint.color = Color.RED
            paint.textSize = 100f
            paint.textAlign = Paint.Align.CENTER
            if (currentAngle > optimalAngle) {
                canvas.drawText("⬇️ PENCHEZ VERS L'AVANT!", w/2f, h * 0.15f, paint)
            } else {
                canvas.drawText("⬆️ REDRESSEZ!", w/2f, h * 0.15f, paint)
            }
        } else {
            paint.color = Color.GREEN
            paint.textSize = 80f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("✅ ANGLE PARFAIT!", w/2f, h * 0.15f, paint)
        }
        
        drawWindIndicatorImproved(canvas, w, h)
        drawStabilityIndicators(canvas, w, h)
    }
    
    private fun drawLanding(canvas: Canvas, w: Int, h: Int) {
        paint.color = Color.parseColor("#87CEEB")
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        
        paint.color = Color.WHITE
        canvas.drawRect(0f, h * 0.8f, w.toFloat(), h.toFloat(), paint)
        
        paint.color = Color.parseColor("#666666")
        paint.textSize = 24f
        paint.textAlign = Paint.Align.CENTER
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        for (i in 1..5) {
            val markX = w * 0.1f + i * (w * 0.8f / 5f)
            val markY = h * 0.8f
            canvas.drawLine(markX, markY, markX, markY + 20f, paint)
            canvas.drawText("${i * 25}m", markX, markY + 40f, paint)
        }
        paint.style = Paint.Style.FILL
        
        val landingProgress = activity.getPhaseTimer() / activity.getLandingDuration()
        val scale = 0.96f // 8 fois plus gros (0.12f * 8)
        
        val skierX: Float
        val skierY: Float
        val currentBitmap: Bitmap?
        
        when {
            landingProgress < 0.3f -> {
                val descentProgress = landingProgress / 0.3f
                skierX = w * (0.2f + descentProgress * 0.3f)
                skierY = h * (0.3f + descentProgress * 0.45f)
                currentBitmap = skierLand1Bitmap
            }
            landingProgress < 0.82f -> {
                val impactProgress = (landingProgress - 0.3f) / 0.52f
                skierX = w * (0.5f + impactProgress * 0.1f)
                skierY = h * 0.75f
                currentBitmap = skierLand2Bitmap
                
                paint.color = Color.WHITE
                paint.alpha = 180
                for (i in 1..12) {
                    val angle = i * 30f
                    val particleX = skierX + cos(Math.toRadians(angle.toDouble())).toFloat() * 60f
                    val particleY = skierY + sin(Math.toRadians(angle.toDouble())).toFloat() * 30f
                    canvas.drawCircle(particleX, particleY, 12f, paint)
                }
                paint.alpha = 255
            }
            else -> {
                val standingProgress = (landingProgress - 0.82f) / 0.18f
                skierX = w * (0.6f + standingProgress * 0.1f)
                skierY = h * 0.75f
                currentBitmap = skierLand3Bitmap
            }
        }
        
        currentBitmap?.let { bmp ->
            val dstRect = RectF(
                skierX - bmp.width * scale / 4f,
                skierY - bmp.height * scale / 4f,
                skierX + bmp.width * scale / 4f,
                skierY + bmp.height * scale / 4f
            )
            canvas.drawBitmap(bmp, null, dstRect, paint)
        }
        
        paint.color = Color.YELLOW
        paint.textSize = 130f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("${activity.getJumpDistance().toInt()}m", w/2f, h * 0.15f, paint)
        
        when (activity.getLandingPhase()) {
            0 -> {
                paint.color = Color.parseColor("#80000000")
                canvas.drawRect(w * 0.3f, h * 0.05f, w.toFloat(), h * 0.45f, paint)
                
                paint.color = Color.parseColor("#00FFFF")
                paint.textSize = 120f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("🎯 PRÉPAREZ!", w * 0.65f, h * 0.15f, paint)
                
                paint.color = Color.WHITE
                paint.textSize = 80f
                canvas.drawText("GARDEZ LE", w * 0.65f, h * 0.23f, paint)
                canvas.drawText("TÉLÉPHONE", w * 0.65f, h * 0.31f, paint)
                canvas.drawText("STABLE", w * 0.65f, h * 0.39f, paint)
                
                paint.color = Color.YELLOW
                paint.textSize = 60f
                canvas.drawText("📱 NE BOUGEZ PAS!", w * 0.65f, h * 0.45f, paint)
            }
            1 -> {
                paint.color = Color.parseColor("#80000000")
                canvas.drawRect(w * 0.3f, h * 0.05f, w.toFloat(), h * 0.45f, paint)
                
                paint.color = Color.RED
                paint.textSize = 130f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("💥 IMPACT!", w * 0.65f, h * 0.15f, paint)
                
                paint.color = Color.WHITE
                paint.textSize = 90f
                canvas.drawText("PENCHEZ", w * 0.65f, h * 0.25f, paint)
                canvas.drawText("VERS VOUS", w * 0.65f, h * 0.33f, paint)
                canvas.drawText("AMORTIR!", w * 0.65f, h * 0.41f, paint)
                
                paint.color = Color.YELLOW
                paint.textSize = 70f
                canvas.drawText("⬇️ TIREZ! ⬇️", w * 0.65f, h * 0.48f, paint)
            }
            2 -> {
                paint.color = Color.parseColor("#80000000")
                canvas.drawRect(w * 0.3f, h * 0.05f, w.toFloat(), h * 0.45f, paint)
                
                paint.color = Color.GREEN
                paint.textSize = 120f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("⚖️ STABILISEZ!", w * 0.65f, h * 0.15f, paint)
                
                paint.color = Color.WHITE
                paint.textSize = 80f
                canvas.drawText("REMETTEZ", w * 0.65f, h * 0.25f, paint)
                canvas.drawText("TOUT", w * 0.65f, h * 0.33f, paint)
                canvas.drawText("STABLE!", w * 0.65f, h * 0.41f, paint)
                
                paint.color = Color.YELLOW
                paint.textSize = 60f
                canvas.drawText("📱 AUCUN MOUVEMENT!", w * 0.65f, h * 0.48f, paint)
            }
        }
        
        paint.color = Color.parseColor("#80000000")
        canvas.drawRect(0f, h * 0.5f, w.toFloat(), h * 0.65f, paint)
        
        paint.color = when {
            activity.getLandingStability() > 1.5f -> Color.parseColor("#00FF00")
            activity.getLandingStability() > 1f -> Color.YELLOW
            else -> Color.RED
        }
        paint.textSize = 80f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("PERFORMANCE: ${(activity.getLandingStability() * 50).toInt()}%", w/2f, h * 0.55f, paint)
        
        val statusText = when {
            activity.getLandingStability() > 1.5f -> "✅ EXCELLENT!"
            activity.getLandingStability() > 1f -> "👍 BIEN!"
            else -> "❌ RATEZ PAS!"
        }
        paint.textSize = 60f
        canvas.drawText(statusText, w/2f, h * 0.62f, paint)
    }
    
    private fun drawResults(canvas: Canvas, w: Int, h: Int) {
        paint.color = Color.parseColor("#87CEEB")
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        
        paint.color = Color.parseColor("#FFD700")
        canvas.drawRect(0f, 0f, w.toFloat(), h * 0.4f, paint)
        
        paint.color = Color.BLACK
        paint.textSize = 80f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("${activity.getFinalScore()}", w/2f, h * 0.2f, paint)
        
        paint.textSize = 40f
        canvas.drawText("POINTS", w/2f, h * 0.3f, paint)
        
        paint.color = Color.WHITE
        paint.textSize = 50f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("🎿 Distance: ${activity.getJumpDistance().toInt()}m", w/2f, h * 0.5f, paint)
        canvas.drawText("⚡ Vitesse: ${activity.getSpeed().toInt()} km/h", w/2f, h * 0.55f, paint)
        canvas.drawText("⚖️ Stabilité: ${(activity.getStability() * 100).toInt()}%", w/2f, h * 0.6f, paint)
        canvas.drawText("🎯 Atterrissage: ${activity.getLandingBonus().toInt()} pts", w/2f, h * 0.65f, paint)
        canvas.drawText("🏁 Finition: ${(activity.getLandingStability() * 50).toInt()}%", w/2f, h * 0.7f, paint)
        canvas.drawText("👆 Bonus Taps: ${(activity.getTapBonus() * 15).toInt()} pts", w/2f, h * 0.75f, paint)
        
        paint.color = Color.YELLOW
        for (i in 1..10) {
            val starX = kotlin.random.Random.nextFloat() * w
            val starY = kotlin.random.Random.nextFloat() * h * 0.4f
            drawStar(canvas, starX, starY, 12f)
        }
    }
    
    private fun drawSpeedMeter(canvas: Canvas, w: Int, h: Int) {
        paint.color = Color.parseColor("#333333")
        paint.style = Paint.Style.FILL
        canvas.drawRect(w - 110f, 140f, w - 30f, h - 140f, paint)
        
        paint.color = when {
            activity.getSpeed() > 100f -> Color.parseColor("#00FF00")
            activity.getSpeed() > 70f -> Color.GREEN
            activity.getSpeed() > 40f -> Color.YELLOW
            else -> Color.RED
        }
        val speedHeight = (activity.getSpeed() / activity.getMaxSpeed()) * (h - 280f)
        canvas.drawRect(w - 105f, h - 140f - speedHeight, w - 35f, h - 140f, paint)
        
        paint.color = Color.WHITE
        paint.textSize = 28f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("VITESSE", w - 70f, 120f, paint)
        canvas.drawText("${activity.getSpeed().toInt()}", w - 70f, h - 90f, paint)
        canvas.drawText("km/h", w - 70f, h - 60f, paint)
    }
    
    private fun drawTakeoffPowerMeter(canvas: Canvas, w: Int, h: Int, criticalZone: Boolean = false) {
        paint.color = Color.parseColor("#333333")
        paint.style = Paint.Style.FILL
        canvas.drawRect(140f, h - 120f, w - 140f, h - 30f, paint)
        
        if (criticalZone) {
            paint.color = when {
                activity.getTakeoffPower() > 100f -> Color.parseColor("#00FF00")
                activity.getTakeoffPower() > 70f -> Color.GREEN
                activity.getTakeoffPower() > 40f -> Color.YELLOW
                else -> Color.RED
            }
            val powerWidth = (activity.getTakeoffPower() / 120f) * (w - 280f)
            canvas.drawRect(140f, h - 115f, 140f + powerWidth, h - 35f, paint)
        }
        
        paint.color = Color.WHITE
        paint.textSize = 28f
        paint.textAlign = Paint.Align.CENTER
        
        if (criticalZone) {
            canvas.drawText("PUISSANCE: ${activity.getTakeoffPower().toInt()}%", w/2f, h - 130f, paint)
            
            if (activity.getTakeoffPower() > 100f) {
                paint.color = Color.parseColor("#00FF00")
                paint.textSize = 24f
                canvas.drawText("PARFAIT! ⭐", w/2f, h - 150f, paint)
            }
        } else {
            canvas.drawText("PUISSANCE: ATTENDEZ LA ZONE!", w/2f, h - 130f, paint)
        }
    }
    
    private fun drawWindIndicatorImproved(canvas: Canvas, w: Int, h: Int) {
        val windX = w - 150f
        val windY = 150f
        
        paint.color = Color.parseColor("#333333")
        paint.style = Paint.Style.FILL
        canvas.drawRect(windX - 80f, windY - 60f, windX + 80f, windY + 60f, paint)
        
        val currentWind = activity.getWindDirection() * activity.getWindTransition()
        
        paint.color = when {
            abs(currentWind) < 0.1f -> Color.GREEN
            abs(currentWind) < 0.5f -> Color.YELLOW
            else -> Color.RED
        }
        paint.textSize = 48f
        paint.textAlign = Paint.Align.CENTER
        
        val windText = when {
            currentWind < -0.1f -> "⬅️"
            currentWind > 0.1f -> "➡️"
            else -> "⏸️"
        }
        canvas.drawText(windText, windX, windY - 10f, paint)
        
        paint.textSize = 24f
        paint.color = Color.WHITE
        canvas.drawText("VENT", windX, windY - 35f, paint)
        canvas.drawText("${(activity.getWindStrength() * 100).toInt()}%", windX, windY + 25f, paint)
        
        paint.textSize = 20f
        paint.color = Color.CYAN
        val instruction = when {
            currentWind < -0.1f -> "Penchez à DROITE"
            currentWind > 0.1f -> "Penchez à GAUCHE"
            else -> "Restez stable"
        }
        canvas.drawText(instruction, windX, windY + 50f, paint)
    }
    
    private fun drawStabilityIndicators(canvas: Canvas, w: Int, h: Int) {
        val baseY = h - 220f
        
        val idealTiltX = -activity.getWindDirection() * activity.getWindStrength() * 0.5f
        val tiltXError = abs(activity.getTiltX() - idealTiltX)
        
        paint.color = Color.parseColor("#333333")
        paint.style = Paint.Style.FILL
        canvas.drawRect(80f, baseY, 340f, baseY + 60f, paint)
        
        paint.color = if (activity.getStability() > 0.8f) Color.GREEN else if (activity.getStability() > 0.5f) Color.YELLOW else Color.RED
        canvas.drawRect(80f, baseY, 80f + activity.getStability() * 260f, baseY + 60f, paint)
        
        paint.color = Color.WHITE
        paint.textSize = 30f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("STABILITÉ: ${(activity.getStability() * 100).toInt()}%", 80f, baseY - 20f, paint)
        
        paint.textSize = 24f
        canvas.drawText("Compensation vent: ${if (tiltXError < 0.3f) "✅" else "❌"}", 80f, baseY + 90f, paint)
        canvas.drawText("Avant/Arrière: ${if (abs(activity.getTiltY()) < 0.3f) "✅" else "❌"}", 80f, baseY + 120f, paint)
        canvas.drawText("Rotation: ${if (abs(activity.getTiltZ()) < 0.3f) "✅" else "❌"}", 80f, baseY + 150f, paint)
    }
    
    private fun drawSnowParticles(canvas: Canvas, w: Int, h: Int) {
        paint.color = Color.WHITE
        paint.alpha = 180
        paint.style = Paint.Style.FILL
        for (particle in activity.getParticles()) {
            canvas.drawCircle(particle.x, particle.y, particle.size, paint)
        }
        paint.alpha = 255
    }
    
    private fun drawStar(canvas: Canvas, x: Float, y: Float, size: Float) {
        val path = Path()
        for (i in 0..4) {
            val angle = i * 72f - 90f
            val outerX = x + cos(Math.toRadians(angle.toDouble())).toFloat() * size
            val outerY = y + sin(Math.toRadians(angle.toDouble())).toFloat() * size
            
            if (i == 0) path.moveTo(outerX, outerY) else path.lineTo(outerX, outerY)
            
            val innerAngle = angle + 36f
            val innerX = x + cos(Math.toRadians(innerAngle.toDouble())).toFloat() * size * 0.4f
            val innerY = y + sin(Math.toRadians(innerAngle.toDouble())).toFloat() * size * 0.4f
            path.lineTo(innerX, innerY)
        }
        path.close()
        canvas.drawPath(path, paint)
    }
}
