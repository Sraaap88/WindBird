package com.example.windbird

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import kotlin.math.*

class BobsledRenderer(private val context: Context, private val activity: BobsledActivity) {
    
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    // Images du bobsleigh
    private var bobsledPreparationBitmap: Bitmap? = null
    private var bobPushBitmap: Bitmap? = null
    private var bobStraightBitmap: Bitmap? = null
    private var bobLeftBitmap: Bitmap? = null
    private var bobRightBitmap: Bitmap? = null
    private var bobFinishLineBitmap: Bitmap? = null
    private var bobCelebrationBitmap: Bitmap? = null
    
    // Images de la piste sprite
    private var bobtrackLeftSpriteBitmap: Bitmap? = null
    
    // Images des drapeaux
    private var flagCanadaBitmap: Bitmap? = null
    private var flagUsaBitmap: Bitmap? = null
    private var flagFranceBitmap: Bitmap? = null
    private var flagNorvegeBitmap: Bitmap? = null
    private var flagJapanBitmap: Bitmap? = null
    
    // Variables pour découper le sprite-sheet
    private var spriteFrameWidth = 0
    private var spriteFrameHeight = 0
    private val totalFrames = 9 // Nombre d'images dans le sprite-sheet
    
    // Variables pour sprite-sheet Winter Games
    private var currentFrameIndex = 0
    private var frameTimer = 0f
    private var isReversing = false
    private var trackSection = TrackSection.STRAIGHT
    private var landscapeOffset = 0f
    
    enum class TrackSection {
        STRAIGHT, LEFT_TURN, RIGHT_TURN, LEFT_RETURN, RIGHT_RETURN
    }
    
    init {
        loadBitmaps()
    }
    
    private fun loadBitmaps() {
        try {
            bobsledPreparationBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.bobsled_preparation)
            bobPushBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.bob_push)
            bobStraightBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.bobnv_straight)
            bobLeftBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.bobnv_left)
            bobRightBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.bobnv_right)
            bobFinishLineBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.bob_finish_line)
            bobCelebrationBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.bob_celebration)
            
            // Charger le sprite-sheet de la piste
            bobtrackLeftSpriteBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.bobtrack_left_sprite)
            bobtrackLeftSpriteBitmap?.let { sprite ->
                spriteFrameWidth = sprite.width / totalFrames
                spriteFrameHeight = sprite.height
            }
            
            // Charger les drapeaux
            flagCanadaBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.flag_canada)
            flagUsaBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.flag_usa)
            flagFranceBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.flag_france)
            flagNorvegeBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.flag_norvege)
            flagJapanBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.flag_japan)
        } catch (e: Exception) {
            createFallbackBobsledBitmaps()
        }
    }
    
    private fun createFallbackBobsledBitmaps() {
        bobsledPreparationBitmap = createSubstituteBitmap(Color.parseColor("#FF4444"))
        bobPushBitmap = createSubstituteBitmap(Color.parseColor("#FF6644"))
        bobStraightBitmap = createSubstituteBitmap(Color.parseColor("#FFB444"))
        bobLeftBitmap = createSubstituteBitmap(Color.parseColor("#44FF44"))
        bobRightBitmap = createSubstituteBitmap(Color.parseColor("#4444FF"))
        bobFinishLineBitmap = createSubstituteBitmap(Color.parseColor("#44FFFF"))
        bobCelebrationBitmap = createSubstituteBitmap(Color.parseColor("#FFB444"))
    }
    
    private fun createSubstituteBitmap(color: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val tempPaint = Paint().apply {
            this.color = color
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(10f, 20f, 110f, 60f, 10f, 10f, tempPaint)
        return bitmap
    }

    // Fonction pour extraire une frame du sprite-sheet
    private fun getTrackSpriteFrame(frameIndex: Int, mirrorHorizontal: Boolean = false, reverse: Boolean = false): Bitmap? {
        return bobtrackLeftSpriteBitmap?.let { sprite ->
            val actualFrameIndex = if (reverse) {
                (totalFrames - 1 - frameIndex).coerceIn(0, totalFrames - 1)
            } else {
                frameIndex.coerceIn(0, totalFrames - 1)
            }
            
            val sourceRect = Rect(
                actualFrameIndex * spriteFrameWidth,
                0,
                (actualFrameIndex + 1) * spriteFrameWidth,
                spriteFrameHeight
            )
            
            val frameBitmap = Bitmap.createBitmap(sprite, sourceRect.left, sourceRect.top, sourceRect.width(), sourceRect.height())
            
            if (mirrorHorizontal) {
                val matrix = Matrix().apply { postScale(-1f, 1f) }
                Bitmap.createBitmap(frameBitmap, 0, 0, frameBitmap.width, frameBitmap.height, matrix, false)
            } else {
                frameBitmap
            }
        }
    }

    fun handleTouch(event: MotionEvent, width: Int, height: Int): Boolean {
        val gameData = activity.getGameData()
        
        if (gameData.gameState == BobsledActivity.GameState.PUSH_START) {
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val pushProgress = (gameData.pushPower / 100f).coerceIn(0f, 1f)
                    val bobX = 150f + pushProgress * (width - 300f)
                    val bobY = height * 0.65f
                    val touchRadius = 92f
                    
                    val touchX = event.x
                    val touchY = event.y
                    val distance = sqrt((touchX - bobX).pow(2) + (touchY - bobY).pow(2))
                    
                    if (distance <= touchRadius) {
                        activity.updatePushPower(4f)
                        return true
                    }
                }
            }
        }
        return false
    }

    fun render(canvas: Canvas, w: Int, h: Int) {
        val gameData = activity.getGameData()
        
        when (gameData.gameState) {
            BobsledActivity.GameState.PREPARATION -> drawPreparation(canvas, w, h, gameData)
            BobsledActivity.GameState.PUSH_START -> drawPushStart(canvas, w, h, gameData)
            BobsledActivity.GameState.CONTROL_DESCENT -> drawWinterGamesSystem(canvas, w, h, gameData)
            BobsledActivity.GameState.FINISH_LINE -> drawFinishLine(canvas, w, h, gameData)
            BobsledActivity.GameState.CELEBRATION -> drawCelebration(canvas, w, h, gameData)
            BobsledActivity.GameState.RESULTS -> drawResults(canvas, w, h, gameData)
            BobsledActivity.GameState.FINISHED -> drawResults(canvas, w, h, gameData)
        }
    }
    
    // SYSTÈME WINTER GAMES AMÉLIORÉ
    private fun drawWinterGamesSystem(canvas: Canvas, w: Int, h: Int, gameData: GameData) {
        val trackStartY = h * 0.15f // PISTE PREND 85% DE L'ÉCRAN (était 50%)
        
        // Mettre à jour le défilement du paysage
        updateLandscapeScrolling(gameData.speed)
        
        // 1. MONTAGNES SIMPLES EN HAUT (15% seulement)
        drawSimpleMountains(canvas, w, trackStartY.toInt(), gameData)
        
        // 2. PISTE SPRITE-SHEET (85% de l'écran)
        drawTrackSpriteBackground(canvas, w, h, trackStartY, gameData)
        
        // 3. BOBSLEIGH CENTRÉ
        drawCenteredBobsled(canvas, w, h, trackStartY, gameData)
        
        // 4. SYMBOLES DE DIRECTION (TRÈS IMPORTANTS!)
        drawTurnIndicators(canvas, w, h, gameData)
        
        // 5. Interface vitesse
        drawInterface(canvas, w, h, gameData)
    }
    
    // Montagnes simples qui bougent selon les virages
    private fun drawSimpleMountains(canvas: Canvas, w: Int, horizonHeight: Int, gameData: GameData) {
        // Ciel simple
        paint.color = Color.rgb(220, 235, 250)
        canvas.drawRect(0f, 0f, w.toFloat(), horizonHeight.toFloat(), paint)
        
        // Montagnes qui bougent selon les virages SEULEMENT
        val trackIndex = (gameData.trackPosition * (gameData.trackCurves.size - 1)).toInt()
        val currentTrackCurve = if (trackIndex < gameData.trackCurves.size) gameData.trackCurves[trackIndex] else 0f
        
        // Déplacement selon la direction du virage
        val mountainShift = currentTrackCurve * w * 0.6f // Plus prononcé
        
        paint.color = Color.rgb(180, 190, 210)
        val mountains = Path().apply {
            moveTo(-mountainShift, horizonHeight.toFloat())
            lineTo(w * 0.25f - mountainShift, horizonHeight * 0.3f)
            lineTo(w * 0.5f - mountainShift, horizonHeight * 0.1f)
            lineTo(w * 0.75f - mountainShift, horizonHeight * 0.4f)
            lineTo(w.toFloat() - mountainShift, horizonHeight * 0.2f)
            lineTo(w.toFloat(), horizonHeight.toFloat())
            close()
        }
        canvas.drawPath(mountains, paint)
    }
    
    private fun updateLandscapeScrolling(speed: Float) {
        val scrollSpeed = speed * 0.02f
        landscapeOffset += scrollSpeed
        if (landscapeOffset > 1000f) landscapeOffset -= 1000f
    }
    
    private fun drawWinterLandscape(canvas: Canvas, w: Int, horizonHeight: Int, gameData: GameData) {
        // Ciel hivernal
        val skyGradient = LinearGradient(
            0f, 0f, 0f, horizonHeight.toFloat(),
            Color.rgb(240, 248, 255),
            Color.rgb(200, 220, 245),
            Shader.TileMode.CLAMP
        )
        paint.shader = skyGradient
        canvas.drawRect(0f, 0f, w.toFloat(), horizonHeight.toFloat(), paint)
        paint.shader = null
        
        // Montagnes qui bougent selon les virages
        val trackIndex = (gameData.trackPosition * (gameData.trackCurves.size - 1)).toInt()
        val currentTrackCurve = if (trackIndex < gameData.trackCurves.size) gameData.trackCurves[trackIndex] else 0f
        val mountainShift = landscapeOffset + currentTrackCurve * w * 0.4f
        
        // Montagnes arrière
        paint.color = Color.rgb(180, 190, 210)
        val backMountains = Path().apply {
            moveTo(-mountainShift * 0.2f, horizonHeight.toFloat())
            lineTo(w * 0.3f - mountainShift * 0.2f, horizonHeight * 0.2f)
            lineTo(w * 0.7f - mountainShift * 0.2f, horizonHeight * 0.3f)
            lineTo(w + 100f - mountainShift * 0.2f, horizonHeight * 0.25f)
            lineTo(w + 100f, horizonHeight.toFloat())
            close()
        }
        canvas.drawPath(backMountains, paint)
        
        // Montagnes proches
        paint.color = Color.rgb(220, 230, 240)
        val frontMountains = Path().apply {
            moveTo(-mountainShift * 0.6f, horizonHeight.toFloat())
            lineTo(w * 0.2f - mountainShift * 0.6f, horizonHeight * 0.5f)
            lineTo(w * 0.8f - mountainShift * 0.6f, horizonHeight * 0.4f)
            lineTo(w.toFloat() - mountainShift * 0.6f, horizonHeight * 0.6f)
            lineTo(w.toFloat(), horizonHeight.toFloat())
            close()
        }
        canvas.drawPath(frontMountains, paint)
        
        // Sapins
        val treeShift = landscapeOffset * 1.5f + currentTrackCurve * w * 0.5f
        for (i in 0..10) {
            val treeX = (w * i / 6f - treeShift) % (w + 150f) - 75f
            val treeY = horizonHeight * (0.7f + sin(i.toFloat()) * 0.1f)
            
            paint.color = Color.rgb(20, 60, 20)
            val treeSize = 20f + (i % 3) * 8f
            canvas.drawRect(treeX - 1.5f, treeY, treeX + 1.5f, treeY + treeSize, paint)
            
            for (layer in 0..2) {
                val layerY = treeY + layer * treeSize / 4f
                val layerWidth = treeSize * (0.7f - layer * 0.15f)
                val trianglePath = Path().apply {
                    moveTo(treeX, layerY - layerWidth/3f)
                    lineTo(treeX - layerWidth/2f, layerY + layerWidth/3f)
                    lineTo(treeX + layerWidth/2f, layerY + layerWidth/3f)
                    close()
                }
                canvas.drawPath(trianglePath, paint)
                
                paint.color = Color.WHITE
                canvas.drawRect(treeX - layerWidth/2f, layerY - 1f, treeX + layerWidth/2f, layerY + 1f, paint)
                paint.color = Color.rgb(20, 60, 20)
            }
        }
    }
    
    private fun drawTrackSpriteBackground(canvas: Canvas, w: Int, h: Int, startY: Float, gameData: GameData) {
        updateTrackFrame(gameData)
        
        val currentFrame = getCurrentTrackFrame()
        
        // SYSTÈME HYBRIDE OPTIMISÉ
        currentFrame?.let { frame ->
            // 1. DÉCALAGE SUBTIL selon vitesse (très léger pour performance)
            val microOffsetY = (gameData.speed * 0.02f + frameTimer * 5f).toInt() % 3 // Max 3px
            val microOffsetX = if (abs(getCurrentTrackCurve(gameData)) > 0.3f) {
                ((gameData.speed * 0.01f).toInt() % 2) * if (getCurrentTrackCurve(gameData) > 0) 1 else -1
            } else 0
            
            val dstRect = RectF(
                microOffsetX.toFloat(), 
                startY + microOffsetY, 
                w.toFloat() + microOffsetX, 
                h.toFloat() + microOffsetY
            )
            canvas.drawBitmap(frame, null, dstRect, paint)
            
            // 2. LIGNES DE VITESSE OPTIMISÉES (calculées une fois)
            drawOptimizedSpeedLines(canvas, w, h, startY, gameData)
            
            // 3. ZOOM SUBTIL (transformation simple)
            if (gameData.speed > 60f) {
                addSubtleZoomEffect(canvas, w, h, startY, gameData.speed)
            }
            
        } ?: run {
            // Fallback léger
            paint.color = Color.WHITE
            canvas.drawRect(0f, startY, w.toFloat(), h.toFloat(), paint)
        }
    }
    
    // LIGNES DE VITESSE ULTRA-OPTIMISÉES
    private fun drawOptimizedSpeedLines(canvas: Canvas, w: Int, h: Int, startY: Float, gameData: GameData) {
        if (gameData.speed < 40f) return
        
        val speedFactor = (gameData.speed / 150f).coerceIn(0.3f, 1f)
        val currentCurve = getCurrentTrackCurve(gameData)
        
        // OPTIMISATION: Calculer seulement les lignes visibles
        val lineCount = (speedFactor * 8).toInt() // Max 8 lignes
        val spacing = w / (lineCount + 1f)
        
        paint.strokeWidth = 2f + speedFactor * 2f
        paint.style = Paint.Style.STROKE
        paint.color = Color.argb((60 * speedFactor).toInt(), 255, 255, 255)
        
        val scrollOffset = (landscapeOffset * speedFactor * 4f) % (h - startY)
        
        for (i in 1..lineCount) {
            val baseX = spacing * i
            
            // COURBURE selon le virage (calcul simple)
            val curveOffset = currentCurve * w * 0.1f * (i / lineCount.toFloat())
            val lineX = baseX + curveOffset
            
            // LIGNES qui défilent de haut en bas
            val startLineY = startY - scrollOffset + (i * 20f) % (h - startY)
            val endLineY = startLineY + 40f * speedFactor
            
            if (lineX > 0 && lineX < w && startLineY < h) {
                // FONDU selon la position (optimisé)
                val fadeAlpha = if (endLineY > h * 0.8f) {
                    ((h - endLineY) / (h * 0.2f)).coerceIn(0f, 1f)
                } else 1f
                
                paint.alpha = ((60 * speedFactor * fadeAlpha).toInt())
                canvas.drawLine(lineX, startLineY, lineX, minOf(endLineY, h.toFloat()), paint)
            }
        }
        
        paint.style = Paint.Style.FILL
        paint.alpha = 255
    }
    
    // ZOOM SUBTIL ULTRA-LÉGER
    private fun addSubtleZoomEffect(canvas: Canvas, w: Int, h: Int, startY: Float, speed: Float) {
        // OPTIMISATION: Zoom seulement à haute vitesse
        if (speed < 80f) return
        
        val zoomCycle = (frameTimer * 3f) % (2f * PI.toFloat())
        val zoomFactor = 1f + sin(zoomCycle) * 0.01f // ±1% seulement
        
        // Transformation simple sur le canvas (très rapide)
        val centerX = w / 2f
        val centerY = (h + startY) / 2f
        
        canvas.save()
        canvas.scale(zoomFactor, zoomFactor, centerX, centerY)
        
        // PARTICULES ULTRA-LÉGÈRES (seulement 5 max)
        paint.color = Color.argb(40, 255, 255, 255)
        repeat(5) { i ->
            val particleX = (centerX + i * w / 6f + landscapeOffset * 2f) % w
            val particleY = startY + (h - startY) * 0.7f + sin(frameTimer * 4f + i) * 10f
            canvas.drawCircle(particleX, particleY, 1.5f, paint)
        }
        
        canvas.restore()
    }
    
    // HELPER: Obtenir la courbe actuelle (évite les recalculs)
    private fun getCurrentTrackCurve(gameData: GameData): Float {
        val trackIndex = (gameData.trackPosition * (gameData.trackCurves.size - 1)).toInt()
        return if (trackIndex < gameData.trackCurves.size) gameData.trackCurves[trackIndex] else 0f
    }
    
    private fun updateTrackFrame(gameData: GameData) {
        // VITESSE FLUIDE avec interpolation
        val frameSpeed = when {
            gameData.speed > 120f -> 0.02f  // PLUS RAPIDE pour effet de vitesse
            gameData.speed > 80f -> 0.035f   
            gameData.speed > 40f -> 0.05f   
            else -> 0.08f          
        }
        
        frameTimer += frameSpeed
        
        if (frameTimer >= 1f) {
            frameTimer = 0f
            
            val trackIndex = (gameData.trackPosition * (gameData.trackCurves.size - 1)).toInt()
            val currentTrackCurve = if (trackIndex < gameData.trackCurves.size) gameData.trackCurves[trackIndex] else 0f
            
            when {
                abs(currentTrackCurve) < 0.3f -> {
                    trackSection = TrackSection.STRAIGHT
                    // VARIATION POUR MASQUER les 2 images seulement
                    val variation = (gameData.speed * 0.1f + frameTimer * 10f).toInt() % 4
                    currentFrameIndex = variation % 2 // Alterne 0,1,0,1 plus vite
                    isReversing = false
                }
                
                currentTrackCurve < -0.3f -> {
                    if (trackSection != TrackSection.LEFT_TURN && trackSection != TrackSection.LEFT_RETURN) {
                        trackSection = TrackSection.LEFT_TURN
                        currentFrameIndex = 2
                        isReversing = false
                    }
                    
                    if (trackSection == TrackSection.LEFT_TURN && !isReversing) {
                        currentFrameIndex++
                        if (currentFrameIndex >= totalFrames - 1) {
                            trackSection = TrackSection.LEFT_RETURN
                            isReversing = true
                        }
                    } else if (trackSection == TrackSection.LEFT_RETURN && isReversing) {
                        currentFrameIndex--
                        if (currentFrameIndex <= 1) {
                            trackSection = TrackSection.STRAIGHT
                            currentFrameIndex = 0
                            isReversing = false
                        }
                    }
                }
                
                currentTrackCurve > 0.3f -> {
                    if (trackSection != TrackSection.RIGHT_TURN && trackSection != TrackSection.RIGHT_RETURN) {
                        trackSection = TrackSection.RIGHT_TURN
                        currentFrameIndex = 2
                        isReversing = false
                    }
                    
                    if (trackSection == TrackSection.RIGHT_TURN && !isReversing) {
                        currentFrameIndex++
                        if (currentFrameIndex >= totalFrames - 1) {
                            trackSection = TrackSection.RIGHT_RETURN
                            isReversing = true
                        }
                    } else if (trackSection == TrackSection.RIGHT_RETURN && isReversing) {
                        currentFrameIndex--
                        if (currentFrameIndex <= 1) {
                            trackSection = TrackSection.STRAIGHT
                            currentFrameIndex = 0
                            isReversing = false
                        }
                    }
                }
            }
        }
    }
    
    // SYMBOLES DE DIRECTION ÉNORMES ET CLAIRS
    private fun drawTurnIndicators(canvas: Canvas, w: Int, h: Int, gameData: GameData) {
        val trackIndex = (gameData.trackPosition * (gameData.trackCurves.size - 1)).toInt()
        val currentTrackCurve = if (trackIndex < gameData.trackCurves.size) gameData.trackCurves[trackIndex] else 0f
        
        // Seulement si il y a un virage
        if (abs(currentTrackCurve) > 0.2f) {
            // Fond semi-transparent
            paint.color = Color.argb(180, 0, 0, 0)
            canvas.drawRoundRect(w/8f, h * 0.2f, w*7f/8f, h * 0.45f, 25f, 25f, paint)
            
            // Symbole ÉNORME selon l'intensité
            paint.textSize = 200f // ENCORE PLUS GROS
            paint.textAlign = Paint.Align.CENTER
            paint.color = Color.WHITE
            
            val directionSymbol = when {
                currentTrackCurve < -0.6f -> "⬅️🔴" // FORT gauche
                currentTrackCurve < -0.3f -> "⬅️🟡" // MOYEN gauche
                currentTrackCurve > 0.6f -> "🔴➡️"  // FORT droite
                currentTrackCurve > 0.3f -> "🟡➡️"  // MOYEN droite
                else -> ""
            }
            
            canvas.drawText(directionSymbol, w/2f, h * 0.35f, paint)
            
            // Performance avec couleur selon la qualité
            paint.textSize = 120f // PLUS GROS
            paint.color = when {
                gameData.playerReactionAccuracy > 0.8f -> Color.GREEN
                gameData.playerReactionAccuracy > 0.6f -> Color.YELLOW
                else -> Color.RED
            }
            canvas.drawText("${(gameData.playerReactionAccuracy * 100).toInt()}%", w/2f, h * 0.42f, paint)
        }
    }
    
    private fun getCurrentTrackFrame(): Bitmap? {
        return when (trackSection) {
            TrackSection.STRAIGHT -> {
                getTrackSpriteFrame(currentFrameIndex, false, false)
            }
            TrackSection.LEFT_TURN, TrackSection.LEFT_RETURN -> {
                getTrackSpriteFrame(currentFrameIndex, false, false)
            }
            TrackSection.RIGHT_TURN, TrackSection.RIGHT_RETURN -> {
                getTrackSpriteFrame(currentFrameIndex, true, false)
            }
        }
    }
    
    private fun drawCenteredBobsled(canvas: Canvas, w: Int, h: Int, trackStartY: Float, gameData: GameData) {
        val baseBobX = w / 2f
        val baseBobY = trackStartY + (h - trackStartY) * 0.7f // Plus bas dans la grande piste
        val bobScale = 0.28f // Légèrement plus gros
        
        val trackIndex = (gameData.trackPosition * (gameData.trackCurves.size - 1)).toInt()
        val currentTrackCurve = if (trackIndex < gameData.trackCurves.size) gameData.trackCurves[trackIndex] else 0f
        
        var bobOffsetX = 0f
        var bobRotation = 0f
        
        when {
            currentTrackCurve < -0.6f -> {
                bobOffsetX = w * 0.08f
                bobRotation = -20f
            }
            currentTrackCurve < -0.3f -> {
                bobOffsetX = w * 0.05f
                bobRotation = -10f
            }
            currentTrackCurve > 0.6f -> {
                bobOffsetX = -w * 0.08f
                bobRotation = 20f
            }
            currentTrackCurve > 0.3f -> {
                bobOffsetX = -w * 0.05f
                bobRotation = 10f
            }
            else -> {
                bobOffsetX = 0f
                bobRotation = 0f
            }
        }
        
        bobRotation += (gameData.tiltZ * 10f).coerceIn(-30f, 30f)
        
        val bobX = baseBobX + bobOffsetX
        val bobY = baseBobY
        
        val bobSprite = when {
            currentTrackCurve < -0.3f -> bobLeftBitmap
            currentTrackCurve > 0.3f -> bobRightBitmap
            else -> bobStraightBitmap
        }
        
        bobSprite?.let { bmp ->
            val dstRect = RectF(
                bobX - bmp.width * bobScale / 2f,
                bobY - bmp.height * bobScale / 2f,
                bobX + bmp.width * bobScale / 2f,
                bobY + bmp.height * bobScale / 2f
            )
            
            if (abs(bobRotation) > 3f) {
                canvas.save()
                canvas.rotate(bobRotation, bobX, bobY)
                canvas.drawBitmap(bmp, null, dstRect, paint)
                canvas.restore()
            } else {
                canvas.drawBitmap(bmp, null, dstRect, paint)
            }
        } ?: run {
            paint.color = when {
                currentTrackCurve < -0.3f -> Color.GREEN
                currentTrackCurve > 0.3f -> Color.BLUE
                else -> Color.YELLOW
            }
            
            if (abs(bobRotation) > 3f) {
                canvas.save()
                canvas.rotate(bobRotation, bobX, bobY)
            }
            
            canvas.drawRoundRect(bobX - 25f, bobY - 15f, bobX + 25f, bobY + 15f, 8f, 8f, paint)
            
            if (abs(bobRotation) > 3f) {
                canvas.restore()
            }
        }
        
        // Ombre plus prononcée
        paint.color = Color.argb(150, 0, 0, 0)
        canvas.drawOval(bobX - 25f, bobY + 18f, bobX + 25f, bobY + 28f, paint)
    }
    
    private fun drawInterface(canvas: Canvas, w: Int, h: Int, gameData: GameData) {
        paint.color = Color.BLACK
        paint.textSize = 60f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("${gameData.speed.toInt()} KM/H", 30f, 80f, paint)
    }
    
    private fun drawFinishLine(canvas: Canvas, w: Int, h: Int, gameData: GameData) {
        drawWinterGamesSystem(canvas, w, h, gameData)
        
        paint.color = Color.YELLOW
        paint.textSize = 80f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("🏁 FINISH! 🏁", w/2f, h * 0.3f, paint)
    }
    
    private fun drawPreparation(canvas: Canvas, w: Int, h: Int, gameData: GameData) {
        bobsledPreparationBitmap?.let { bmp ->
            val dstRect = RectF(0f, 0f, w.toFloat(), h.toFloat())
            canvas.drawBitmap(bmp, null, dstRect, paint)
        } ?: run {
            paint.color = Color.parseColor("#E0F6FF")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        }
        
        paint.color = Color.WHITE
        val flagRect = RectF(50f, 50f, 300f, 200f)
        canvas.drawRect(flagRect, paint)
        
        paint.color = Color.BLACK
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        canvas.drawRect(flagRect, paint)
        paint.style = Paint.Style.FILL
        
        val playerCountry = if (gameData.practiceMode) {
            "CANADA"
        } else {
            gameData.tournamentData.playerCountries[gameData.currentPlayerIndex]
        }
        
        val flagBitmap = when (playerCountry.uppercase()) {
            "CANADA" -> flagCanadaBitmap
            "USA" -> flagUsaBitmap
            "FRANCE" -> flagFranceBitmap
            "NORVÈGE" -> flagNorvegeBitmap
            "JAPON" -> flagJapanBitmap
            else -> flagCanadaBitmap
        }
        
        flagBitmap?.let { flag ->
            val flagWidth = flagRect.width() - 10f
            val flagHeight = flagRect.height() - 10f
            
            val imageRatio = flag.width.toFloat() / flag.height.toFloat()
            val rectRatio = flagWidth / flagHeight
            
            val finalWidth: Float
            val finalHeight: Float
            
            if (imageRatio > rectRatio) {
                finalWidth = flagWidth
                finalHeight = flagWidth / imageRatio
            } else {
                finalHeight = flagHeight
                finalWidth = flagHeight * imageRatio
            }
            
            val centerX = flagRect.centerX()
            val centerY = flagRect.centerY()
            
            val flagImageRect = RectF(
                centerX - finalWidth / 2f,
                centerY - finalHeight / 2f,
                centerX + finalWidth / 2f,
                centerY + finalHeight / 2f
            )
            canvas.drawBitmap(flag, null, flagImageRect, paint)
        } ?: run {
            val flag = getCountryFlag(playerCountry)
            paint.color = Color.BLACK
            paint.textSize = 120f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(flag, flagRect.centerX(), flagRect.centerY() + 40f, paint)
        }
        
        paint.color = Color.BLACK
        paint.textSize = 28f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(playerCountry.uppercase(), flagRect.centerX(), flagRect.bottom + 40f, paint)
        
        paint.textSize = 56f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("🛷 BOBSLEIGH 🛷", w/2f, h * 0.4f, paint)
        
        paint.textSize = 40f
        canvas.drawText("L'équipe se prépare...", w/2f, h * 0.47f, paint)
        
        paint.textSize = 36f
        paint.color = Color.YELLOW
        canvas.drawText("Dans ${(gameData.preparationDuration - gameData.phaseTimer).toInt() + 1} secondes", w/2f, h * 0.55f, paint)
    }
    
    private fun drawPushStart(canvas: Canvas, w: Int, h: Int, gameData: GameData) {
        paint.color = Color.rgb(150, 200, 255)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        
        paint.color = Color.rgb(100, 100, 100)
        val mountainPath = Path().apply {
            moveTo(0f, h * 0.4f)
            lineTo(w * 0.3f, h * 0.2f)
            lineTo(w * 0.7f, h * 0.25f)
            lineTo(w.toFloat(), h * 0.35f)
            lineTo(w.toFloat(), h * 0.4f)
            close()
        }
        canvas.drawPath(mountainPath, paint)
        
        paint.color = Color.WHITE
        val trackY = h * 0.65f
        canvas.drawRect(50f, trackY - 40f, w - 50f, trackY + 40f, paint)
        
        paint.color = Color.GRAY
        paint.strokeWidth = 4f
        paint.style = Paint.Style.STROKE
        canvas.drawLine(50f, trackY - 40f, w - 50f, trackY - 40f, paint)
        canvas.drawLine(50f, trackY + 40f, w - 50f, trackY + 40f, paint)
        paint.style = Paint.Style.FILL
        
        paint.color = Color.RED
        paint.strokeWidth = 6f
        paint.style = Paint.Style.STROKE
        canvas.drawLine(100f, trackY - 50f, 100f, trackY + 50f, paint)
        paint.style = Paint.Style.FILL
        
        val pushProgress = (gameData.pushPower / 100f).coerceIn(0f, 1f)
        val bobX = 150f + pushProgress * (w - 300f)
        val bobY = trackY
        
        paint.color = Color.argb(100, 255, 255, 0)
        canvas.drawCircle(bobX, bobY, 80f, paint)
        
        paint.color = Color.YELLOW
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        canvas.drawCircle(bobX, bobY, 80f, paint)
        paint.style = Paint.Style.FILL
        
        bobPushBitmap?.let { bmp ->
            val scale = 0.3f
            val dstRect = RectF(
                bobX - bmp.width * scale / 2f,
                bobY - bmp.height * scale / 2f,
                bobX + bmp.width * scale / 2f,
                bobY + bmp.height * scale / 2f
            )
            canvas.drawBitmap(bmp, null, dstRect, paint)
        } ?: run {
            paint.color = Color.RED
            canvas.drawRoundRect(bobX - 40f, bobY - 20f, bobX + 40f, bobY + 20f, 8f, 8f, paint)
        }
        
        paint.color = Color.argb(200, 0, 0, 0)
        canvas.drawRoundRect(w/2f - 400f, 120f, w/2f + 400f, 220f, 10f, 10f, paint)
        
        paint.color = Color.WHITE
        paint.textSize = 80f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("TAPEZ SUR LE BOBSLEIGH POUR LE POUSSER!", w/2f, 180f, paint)
        
        paint.color = Color.argb(200, 0, 0, 0)
        canvas.drawRoundRect(w/2f - 200f, h - 150f, w/2f + 200f, h - 40f, 10f, 10f, paint)
        
        paint.color = Color.GRAY
        canvas.drawRect(w/2f - 180f, h - 120f, w/2f + 180f, h - 80f, paint)
        
        paint.color = Color.GREEN
        val powerWidth = (gameData.pushPower.coerceAtMost(150f) / 150f) * 360f
        canvas.drawRect(w/2f - 180f, h - 120f, w/2f - 180f + powerWidth, h - 80f, paint)
        
        paint.color = Color.WHITE
        paint.textSize = 80f
        canvas.drawText("PUISSANCE: ${gameData.pushPower.toInt()}% | Coups: ${gameData.pushCount}", w/2f, h - 50f, paint)
        
        paint.color = Color.argb(200, 255, 0, 0)
        canvas.drawRoundRect(w - 140f, 60f, w - 20f, 160f, 10f, 10f, paint)
        
        paint.textSize = 56f
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("${(gameData.pushStartDuration - gameData.phaseTimer).toInt() + 1}s", w - 80f, 130f, paint)
    }
    
    private fun drawCelebration(canvas: Canvas, w: Int, h: Int, gameData: GameData) {
        paint.color = Color.parseColor("#FFD700")
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        
        val progress = gameData.phaseTimer / gameData.celebrationDuration
        
        val bobCelebrationX = if (progress < 0.7f) {
            val moveProgress = (progress / 0.7f)
            val easedProgress = 1f - (1f - moveProgress) * (1f - moveProgress)
            -200f + easedProgress * (w/2f + 200f)
        } else {
            w/2f
        }
        
        val bobCelebrationY = h/2f
        
        for (i in 0..15) {
            val angle = (2.0 * PI / 15 * i + progress * 6).toFloat()
            val radius = progress * 200f
            val particleX = bobCelebrationX + cos(angle) * radius
            val particleY = bobCelebrationY + sin(angle) * radius
            
            paint.color = Color.WHITE
            canvas.drawCircle(particleX, particleY, 6f, paint)
        }
        
        bobCelebrationBitmap?.let { bmp ->
            val scale = 0.4f
            val dstRect = RectF(
                bobCelebrationX - bmp.width * scale / 2f,
                bobCelebrationY - bmp.height * scale / 2f,
                bobCelebrationX + bmp.width * scale / 2f,
                bobCelebrationY + bmp.height * scale / 2f
            )
            canvas.drawBitmap(bmp, null, dstRect, paint)
        }
        
        paint.color = Color.BLACK
        paint.textSize = 60f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("🎉 BRAVO! 🎉", w/2f, 150f, paint)
        
        paint.textSize = 40f
        canvas.drawText("Temps: ${gameData.raceTime.toInt()}s", w/2f, h - 100f, paint)
        canvas.drawText("Vitesse moy: ${gameData.speed.toInt()} km/h", w/2f, h - 50f, paint)
    }
    
    private fun drawResults(canvas: Canvas, w: Int, h: Int, gameData: GameData) {
        paint.color = Color.parseColor("#E0F6FF")
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        
        paint.color = Color.parseColor("#FFD700")
        canvas.drawRect(0f, 0f, w.toFloat(), h * 0.4f, paint)
        
        paint.color = Color.BLACK
        paint.textSize = 120f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("${gameData.finalScore}", w/2f, h * 0.25f, paint)
        
        paint.textSize = 50f
        canvas.drawText("POINTS", w/2f, h * 0.35f, paint)
        
        paint.color = Color.parseColor("#001122")
        paint.textSize = 48f
        canvas.drawText("🚀 Poussée: ${(gameData.pushQuality * 100).toInt()}%", w/2f, h * 0.5f, paint)
        canvas.drawText("🎮 Réflexes: ${(gameData.playerReactionAccuracy * 100).toInt()}%", w/2f, h * 0.56f, paint)
        canvas.drawText("🏆 Virages parfaits: ${gameData.perfectTurns}", w/2f, h * 0.62f, paint)
        canvas.drawText("🕒 Temps: ${gameData.raceTime.toInt()}s", w/2f, h * 0.68f, paint)
        canvas.drawText("⚡ Vitesse moy: ${gameData.speed.toInt()} km/h", w/2f, h * 0.74f, paint)
        canvas.drawText("💥 Impacts murs: ${gameData.wallHits}", w/2f, h * 0.8f, paint)
    }
    
    private fun getCountryFlag(country: String): String {
        return when (country.uppercase()) {
            "CANADA" -> "🇨🇦"
            "FRANCE" -> "🇫🇷"
            "USA" -> "🇺🇸"
            "NORVÈGE" -> "🇳🇴"
            "JAPON" -> "🇯🇵"
            else -> "🏴"
        }
    }
}
