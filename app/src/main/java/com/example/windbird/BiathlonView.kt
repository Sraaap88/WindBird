package com.example.windbird

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.*

class BiathlonView(private val activity: BiathlonActivity) : View(activity) {
    private val paint = Paint()

    override fun onDraw(canvas: Canvas) {
        val w = canvas.width
        val h = canvas.height
        
        when (activity.currentScreen) {
            0 -> drawPreparationScreen(canvas, w, h)
            1 -> drawForestScreen(canvas, w, h)
            2 -> drawMountainScreen(canvas, w, h) 
            3 -> drawShootingScreen(canvas, w, h)
            4 -> drawValleyScreen(canvas, w, h)
            5 -> drawFinishScreen(canvas, w, h)
            6 -> drawStatsScreen(canvas, w, h)
        }
    }
    
    // NOUVEAU - Écran de préparation avec image en ratio original et texte sur les côtés
    private fun drawPreparationScreen(canvas: Canvas, w: Int, h: Int) {
        // Fond blanc pour les côtés
        paint.color = Color.WHITE
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        
        // Charger et afficher l'image biathlon_track.png EN RATIO ORIGINAL
        try {
            val trackImage = BitmapFactory.decodeResource(context.resources, R.drawable.biathlon_track)
            val originalWidth = trackImage.width
            val originalHeight = trackImage.height
            val originalRatio = originalWidth.toFloat() / originalHeight.toFloat()
            
            // Calculer les dimensions pour garder le ratio et centrer
            val imageHeight = h
            val imageWidth = (imageHeight * originalRatio).toInt()
            val imageX = (w - imageWidth) / 2f // Centrer horizontalement
            
            val scaledTrack = Bitmap.createScaledBitmap(trackImage, imageWidth, imageHeight, true)
            canvas.drawBitmap(scaledTrack, imageX, 0f, null)
        } catch (e: Exception) {
            // Si l'image n'existe pas, fond bleu hivernal centré
            paint.color = Color.parseColor("#87CEEB")
            canvas.drawRect(w * 0.2f, 0f, w * 0.8f, h.toFloat(), paint)
        }
        
        // DRAPEAU DU PAYS en haut à gauche SUR LA ZONE BLANCHE - 4 FOIS PLUS GROS
        val flagSize = 480f  // 4 fois plus gros (était 120f)
        val flagX = 30f
        val flagY = 50f
        
        // Dessiner le drapeau selon le pays du joueur
        val playerCountry = activity.tournamentData.playerCountries[activity.currentPlayerIndex]
        drawCountryFlag(canvas, flagX, flagY, flagSize, playerCountry)
        
        // TEXTE SUR LES CÔTÉS BLANCS - CÔTÉ GAUCHE (ajusté pour le gros drapeau)
        val leftTextX = w * 0.1f
        
        // Temps restant en très gros (plus bas à cause du gros drapeau)
        val timeLeft = 7 - (System.currentTimeMillis() - activity.preparationTimer) / 1000  // 7 secondes
        paint.color = Color.parseColor("#FF0000") // ROUGE
        paint.textSize = 120f
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true
        canvas.drawText("$timeLeft", leftTextX, h * 0.45f, paint)  // Plus bas (était 0.3f)
        
        // TITRE PRINCIPAL SUR LE CÔTÉ DROIT
        val rightTextX = w * 0.9f
        paint.color = Color.parseColor("#FF0000")
        paint.textSize = 60f
        canvas.drawText("🎿 BIATHLON", rightTextX, h * 0.2f, paint)
        canvas.drawText("🎯", rightTextX, h * 0.3f, paint)
        
        // INSTRUCTIONS SUR LES CÔTÉS
        paint.color = Color.parseColor("#FF0000")
        paint.textSize = 40f
        paint.isFakeBoldText = true
        
        // Instructions côté gauche (ajustées pour le gros drapeau)
        canvas.drawText("📱 TOURNEZ", leftTextX, h * 0.6f, paint)  // Plus bas
        canvas.drawText("LE TÉLÉPHONE", leftTextX, h * 0.65f, paint)
        
        canvas.drawText("🔄 ALTERNEZ", leftTextX, h * 0.75f, paint)  // Plus bas
        canvas.drawText("GAUCHE-DROITE", leftTextX, h * 0.8f, paint)
        
        // Instructions côté droit
        canvas.drawText("🎯 VISEZ LE", rightTextX, h * 0.5f, paint)
        canvas.drawText("CENTRE ROUGE", rightTextX, h * 0.55f, paint)
        
        canvas.drawText("🏃 GARDEZ", rightTextX, h * 0.65f, paint)
        canvas.drawText("LE RYTHME!", rightTextX, h * 0.7f, paint)
        
        // Message de départ EN BAS AU CENTRE
        paint.color = Color.parseColor("#FF0000")
        paint.textSize = 60f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("PRÉPAREZ-VOUS!", w/2f, h * 0.9f, paint)
        
        paint.isFakeBoldText = false
    }
    
    // NOUVEAU - Fonction pour dessiner les drapeaux de pays
    private fun drawCountryFlag(canvas: Canvas, x: Float, y: Float, size: Float, country: String) {
        when (country.uppercase()) {
            "FRANCE", "FR" -> {
                // Drapeau français
                paint.color = Color.parseColor("#0055A4") // Bleu
                canvas.drawRect(x, y, x + size/3, y + size * 0.7f, paint)
                paint.color = Color.WHITE // Blanc
                canvas.drawRect(x + size/3, y, x + 2*size/3, y + size * 0.7f, paint)
                paint.color = Color.parseColor("#EF4135") // Rouge
                canvas.drawRect(x + 2*size/3, y, x + size, y + size * 0.7f, paint)
            }
            "CANADA", "CA" -> {
                // Drapeau canadien
                paint.color = Color.parseColor("#FF0000") // Rouge
                canvas.drawRect(x, y, x + size/4, y + size * 0.7f, paint)
                canvas.drawRect(x + 3*size/4, y, x + size, y + size * 0.7f, paint)
                paint.color = Color.WHITE // Blanc
                canvas.drawRect(x + size/4, y, x + 3*size/4, y + size * 0.7f, paint)
                // Feuille d'érable simplifiée
                paint.color = Color.parseColor("#FF0000")
                canvas.drawCircle(x + size/2, y + size * 0.35f, 15f, paint)
            }
            "USA", "US" -> {
                // Drapeau américain simplifié
                paint.color = Color.parseColor("#B22234") // Rouge
                canvas.drawRect(x, y, x + size, y + size * 0.7f, paint)
                paint.color = Color.WHITE // Bandes blanches
                for (i in 1..6) {
                    canvas.drawRect(x, y + i * size * 0.7f / 13 * 2, x + size, y + (i * 2 + 1) * size * 0.7f / 13, paint)
                }
                paint.color = Color.parseColor("#3C3B6E") // Bleu
                canvas.drawRect(x, y, x + size * 0.4f, y + size * 0.35f, paint)
            }
            "GERMANY", "DE" -> {
                // Drapeau allemand
                paint.color = Color.BLACK
                canvas.drawRect(x, y, x + size, y + size * 0.7f / 3, paint)
                paint.color = Color.parseColor("#DD0000") // Rouge
                canvas.drawRect(x, y + size * 0.7f / 3, x + size, y + 2 * size * 0.7f / 3, paint)
                paint.color = Color.parseColor("#FFCE00") // Jaune
                canvas.drawRect(x, y + 2 * size * 0.7f / 3, x + size, y + size * 0.7f, paint)
            }
            else -> {
                // Drapeau générique (bleu-blanc-rouge)
                paint.color = Color.parseColor("#0055A4")
                canvas.drawRect(x, y, x + size/3, y + size * 0.7f, paint)
                paint.color = Color.WHITE
                canvas.drawRect(x + size/3, y, x + 2*size/3, y + size * 0.7f, paint)
                paint.color = Color.parseColor("#EF4135")
                canvas.drawRect(x + 2*size/3, y, x + size, y + size * 0.7f, paint)
            }
        }
        
        // Bordure du drapeau
        paint.color = Color.BLACK
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        canvas.drawRect(x, y, x + size, y + size * 0.7f, paint)
        paint.style = Paint.Style.FILL
    }
    
    private fun drawForestScreen(canvas: Canvas, w: Int, h: Int) {
        // Ciel de forêt
        paint.color = Color.parseColor("#87CEEB")
        canvas.drawRect(0f, 0f, w.toFloat(), h * 0.5f, paint)
        
        // Sol neigeux
        paint.color = Color.parseColor("#F0F8FF")
        canvas.drawRect(0f, h * 0.5f, w.toFloat(), h.toFloat(), paint)
        
        // Montagnes au loin
        paint.color = Color.parseColor("#B0C4DE")
        val mountainPath = Path()
        mountainPath.moveTo(0f, h * 0.15f)
        mountainPath.lineTo(w * 0.3f, h * 0.05f)
        mountainPath.lineTo(w * 0.6f, h * 0.12f)
        mountainPath.lineTo(w.toFloat(), h * 0.08f)
        mountainPath.lineTo(w.toFloat(), h * 0.5f)
        mountainPath.lineTo(0f, h * 0.5f)
        mountainPath.close()
        canvas.drawPath(mountainPath, paint)
        
        // Arbres de forêt
        for (i in 0..15) {
            val treeX = (activity.backgroundOffset * 0.5f + i * 60) % (w + 120) - 120
            val treeHeight = 80f + (i % 3) * 20f
            
            // Tronc
            paint.color = Color.parseColor("#8B4513")
            canvas.drawRect(treeX - 5f, h * 0.5f - treeHeight, treeX + 5f, h * 0.5f, paint)
            
            // Feuillage
            paint.color = Color.parseColor("#228B22")
            canvas.drawCircle(treeX, h * 0.5f - treeHeight, 25f, paint)
        }
        
        drawSkierAndUI(canvas, w, h)
    }
    
    private fun drawMountainScreen(canvas: Canvas, w: Int, h: Int) {
        // Ciel de montagne
        paint.color = Color.parseColor("#B0C4DE")
        canvas.drawRect(0f, 0f, w.toFloat(), h * 0.5f, paint)
        
        // Sol neigeux
        paint.color = Color.parseColor("#FFFAFA")
        canvas.drawRect(0f, h * 0.5f, w.toFloat(), h.toFloat(), paint)
        
        // Chaîne de montagnes
        paint.color = Color.parseColor("#4682B4")
        val mountainPath = Path()
        mountainPath.moveTo(0f, h * 0.5f)
        mountainPath.lineTo(w * 0.2f, h * 0.05f)
        mountainPath.lineTo(w * 0.4f, h * 0.15f)
        mountainPath.lineTo(w * 0.6f, h * 0.02f)
        mountainPath.lineTo(w * 0.8f, h * 0.12f)
        mountainPath.lineTo(w.toFloat(), h * 0.08f)
        mountainPath.lineTo(w.toFloat(), h * 0.5f)
        mountainPath.close()
        canvas.drawPath(mountainPath, paint)
        
        // Sommets enneigés
        paint.color = Color.WHITE
        for (i in arrayOf(0.2f, 0.6f)) {
            val snowPath = Path()
            snowPath.moveTo(w * (i - 0.03f), h * 0.1f)
            snowPath.lineTo(w * i, h * 0.02f)
            snowPath.lineTo(w * (i + 0.03f), h * 0.1f)
            canvas.drawPath(snowPath, paint)
        }
        
        // Rochers
        paint.color = Color.parseColor("#696969")
        for (i in 0..8) {
            val rockX = (activity.backgroundOffset * 0.4f + i * 100) % (w + 200) - 200
            canvas.drawCircle(rockX, h * 0.45f, 12f + (i % 3) * 5f, paint)
        }
        
        drawSkierAndUI(canvas, w, h)
    }
    
    private fun drawShootingScreen(canvas: Canvas, w: Int, h: Int) {
        // NOUVEAU - Fond hivernal au lieu de noir
        // Ciel hivernal
        paint.color = Color.parseColor("#E6F3FF")
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        
        // Montagnes enneigées au loin
        paint.color = Color.parseColor("#B0C4DE")
        val mountainPath = Path()
        mountainPath.moveTo(0f, h * 0.4f)
        mountainPath.lineTo(w * 0.25f, h * 0.2f)
        mountainPath.lineTo(w * 0.5f, h * 0.3f)
        mountainPath.lineTo(w * 0.75f, h * 0.15f)
        mountainPath.lineTo(w.toFloat(), h * 0.35f)
        mountainPath.lineTo(w.toFloat(), h.toFloat())
        mountainPath.lineTo(0f, h.toFloat())
        mountainPath.close()
        canvas.drawPath(mountainPath, paint)
        
        // Sol neigeux
        paint.color = Color.parseColor("#FFFAFA")
        canvas.drawRect(0f, h * 0.6f, w.toFloat(), h.toFloat(), paint)
        
        // Clôtures pour les cibles
        paint.color = Color.parseColor("#8B4513")
        canvas.drawRect(0f, h * 0.5f, w.toFloat(), h * 0.55f, paint)
        
        // Poteaux de clôture
        for (i in 0..6) {
            val postX = i * w / 6f
            canvas.drawRect(postX - 5f, h * 0.45f, postX + 5f, h * 0.6f, paint)
        }
        
        // Arbres hivernaux en arrière-plan
        paint.color = Color.parseColor("#228B22")
        for (i in 0..8) {
            val treeX = (i * 120f) % w
            val treeHeight = 60f + (i % 3) * 15f
            
            // Tronc
            paint.color = Color.parseColor("#8B4513")
            canvas.drawRect(treeX - 3f, h * 0.4f - treeHeight, treeX + 3f, h * 0.4f, paint)
            
            // Feuillage enneigé
            paint.color = Color.parseColor("#228B22")
            canvas.drawCircle(treeX, h * 0.4f - treeHeight, 15f, paint)
            paint.color = Color.WHITE
            canvas.drawCircle(treeX, h * 0.4f - treeHeight, 12f, paint)
        }
        
        // Instructions
        paint.color = Color.parseColor("#003366")
        paint.textSize = 50f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("🎯 ZONE DE TIR 🎯", w/2f, 80f, paint)
        
        paint.textSize = 30f
        paint.color = Color.parseColor("#CC0000")
        canvas.drawText("TÉLÉPHONE À PLAT - VISEZ LE CENTRE ROUGE!", w/2f, 130f, paint)
        
        if (activity.shotsFired >= 5) {
            paint.color = Color.parseColor("#006600")
            paint.textSize = 40f
            canvas.drawText("TIR TERMINÉ - Continuez le parcours...", w/2f, 180f, paint)
        }
        
        // Cibles sur les clôtures
        for (i in activity.targetPositions.indices) {
            val px = activity.targetPositions[i].x * w
            val py = h * 0.4f // Positionnées sur la clôture
            
            drawTarget(canvas, px, py, i)
        }
        
        // Mire
        drawCrosshair(canvas, w, h)
        
        // Interface de tir
        drawShootingUI(canvas, w, h)
    }
    
    private fun drawValleyScreen(canvas: Canvas, w: Int, h: Int) {
        // Ciel de vallée
        paint.color = Color.parseColor("#87CEFA")
        canvas.drawRect(0f, 0f, w.toFloat(), h * 0.5f, paint)
        
        // Sol neigeux
        paint.color = Color.parseColor("#F5FFFA")
        canvas.drawRect(0f, h * 0.5f, w.toFloat(), h.toFloat(), paint)
        
        // Collines douces
        paint.color = Color.parseColor("#90EE90")
        val hillPath = Path()
        hillPath.moveTo(0f, h * 0.35f)
        hillPath.quadTo(w * 0.3f, h * 0.25f, w * 0.6f, h * 0.3f)
        hillPath.quadTo(w * 0.8f, h * 0.35f, w.toFloat(), h * 0.25f)
        hillPath.lineTo(w.toFloat(), h * 0.5f)
        hillPath.lineTo(0f, h * 0.5f)
        hillPath.close()
        canvas.drawPath(hillPath, paint)
        
        // Rivière serpentante
        paint.color = Color.parseColor("#4682B4")
        val riverPath = Path()
        riverPath.moveTo(0f, h * 0.42f)
        riverPath.quadTo(w * 0.3f, h * 0.38f, w * 0.6f, h * 0.45f)
        riverPath.lineTo(w.toFloat(), h * 0.43f)
        riverPath.lineTo(w.toFloat(), h * 0.47f)
        riverPath.quadTo(w * 0.6f, h * 0.49f, w * 0.3f, h * 0.42f)
        riverPath.lineTo(0f, h * 0.46f)
        riverPath.close()
        canvas.drawPath(riverPath, paint)
        
        // Bouleaux
        for (i in 0..12) {
            val treeX = (activity.backgroundOffset * 0.3f + i * 80) % (w + 160) - 160
            val treeHeight = 70f
            
            // Tronc de bouleau
            paint.color = Color.parseColor("#F5F5DC")
            canvas.drawRect(treeX - 3f, h * 0.5f - treeHeight, treeX + 3f, h * 0.5f, paint)
            
            // Marques noires
            paint.color = Color.BLACK
            for (j in 1..3) {
                val markY = h * 0.5f - treeHeight * j / 4f
                canvas.drawRect(treeX - 3f, markY, treeX + 3f, markY + 2f, paint)
            }
            
            // Feuillage
            paint.color = Color.parseColor("#90EE90")
            canvas.drawCircle(treeX, h * 0.5f - treeHeight, 18f, paint)
        }
        
        drawSkierAndUI(canvas, w, h)
    }
    
    private fun drawFinishScreen(canvas: Canvas, w: Int, h: Int) {
        // Ciel doré de victoire
        val gradient = LinearGradient(0f, 0f, 0f, h * 0.5f,
            Color.parseColor("#FFD700"), Color.parseColor("#FFA500"), Shader.TileMode.CLAMP)
        paint.shader = gradient
        canvas.drawRect(0f, 0f, w.toFloat(), h * 0.5f, paint)
        paint.shader = null
        
        // Sol neigeux doré
        paint.color = Color.parseColor("#FFFAF0")
        canvas.drawRect(0f, h * 0.5f, w.toFloat(), h.toFloat(), paint)
        
        // Montagnes dorées
        paint.color = Color.parseColor("#DAA520")
        val goldenMountain = Path()
        goldenMountain.moveTo(0f, h * 0.3f)
        goldenMountain.lineTo(w * 0.3f, h * 0.15f)
        goldenMountain.lineTo(w * 0.7f, h * 0.1f)
        goldenMountain.lineTo(w.toFloat(), h * 0.2f)
        goldenMountain.lineTo(w.toFloat(), h * 0.5f)
        goldenMountain.lineTo(0f, h * 0.5f)
        goldenMountain.close()
        canvas.drawPath(goldenMountain, paint)
        
        // Drapeaux de victoire
        for (i in 0..10) {
            val flagX = (activity.backgroundOffset * 0.5f + i * 80) % (w + 160) - 160
            val flagHeight = 60f
            
            // Mât
            paint.color = Color.parseColor("#8B4513")
            canvas.drawRect(flagX - 2f, h * 0.5f - flagHeight, flagX + 2f, h * 0.5f, paint)
            
            // Drapeau coloré
            val flagColors = arrayOf("#FF0000", "#FFFFFF", "#0000FF")
            paint.color = Color.parseColor(flagColors[i % 3])
            canvas.drawRect(flagX + 2f, h * 0.5f - flagHeight, flagX + 30f, h * 0.5f - flagHeight + 20f, paint)
        }
        
        // Ligne d'arrivée si proche
        if (activity.skierX > 0.8f) {
            paint.color = Color.BLACK
            for (i in 0..20) {
                val y = h * 0.75f + i * 6f
                val color = if (i % 2 == 0) Color.BLACK else Color.WHITE
                paint.color = color
                canvas.drawRect(w * 0.9f, y, w.toFloat(), y + 6f, paint)
            }
        }
        
        drawSkierAndUI(canvas, w, h)
    }
    
    private fun drawStatsScreen(canvas: Canvas, w: Int, h: Int) {
        // FOND JAUNE COMPLET
        paint.color = Color.parseColor("#FFD700")
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        
        // Skieur au centre (utilise happyFrame automatiquement) - PLUS PETIT ET PLUS HAUT
        val skierScreenX = w * activity.skierX
        val skierY = h * 0.6f  // Plus haut (était 0.7f)
        
        activity.currentFrame?.let { frame ->
            val destX = skierScreenX - frame.width / 2f
            canvas.drawBitmap(frame, destX, skierY, null)
        }
        
        // TITRE PRINCIPAL
        paint.color = Color.parseColor("#8B0000")
        paint.textSize = 60f
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true
        canvas.drawText("📊 RÉSULTATS 📊", w/2f, 100f, paint)
        
        // STATISTIQUES EN GROS
        paint.textSize = 45f
        paint.color = Color.parseColor("#000080")
        
        val stats = listOf(
            "🎯 Tirs réussis: ${activity.targetsHit}/5",
            "💥 Score tir: ${activity.totalScore} pts",
            "🏃 Poussées: ${activity.pushCount}",
            "⚡ Rythme moyen: ${(activity.averageRhythm * 100).toInt()}%"
        )
        
        var yPosition = 200f
        for (stat in stats) {
            canvas.drawText(stat, w/2f, yPosition, paint)
            yPosition += 70f
        }
        
        // SCORE FINAL EN DOUBLE GROSSEUR
        paint.color = Color.parseColor("#8B0000")
        paint.textSize = 90f  // DOUBLE (était 45f)
        paint.isFakeBoldText = true
        canvas.drawText("🏆 SCORE FINAL: ${activity.calculateScore()} pts", w/2f, yPosition, paint)
        
        // Message de fin
        paint.color = Color.parseColor("#006400")
        paint.textSize = 35f
        paint.isFakeBoldText = false
        if (!activity.autoMovement && activity.skierX >= 0.5f) {
            canvas.drawText("Analyse terminée dans ${5 - (System.currentTimeMillis() - activity.finalScreenTimer) / 1000}s", 
                w/2f, h - 100f, paint)
        }
    }
    
    private fun drawSkierAndUI(canvas: Canvas, w: Int, h: Int) {
        // Piste
        paint.color = Color.parseColor("#DCDCDC")
        canvas.drawRect(0f, h * 0.75f, w.toFloat(), h.toFloat(), paint)
        
        // Skieur
        val skierScreenX = w * activity.skierX
        val skierY = h * 0.7f
        
        activity.currentFrame?.let { frame ->
            val destX = skierScreenX - frame.width / 2f
            canvas.drawBitmap(frame, destX, skierY, null)
        }
        
        // Instructions au début - DÉCENTRÉES VERS LA GAUCHE
        if (activity.pushCount < 3 && (activity.gameState == BiathlonActivity.GameState.SKIING || activity.gameState == BiathlonActivity.GameState.FINAL_SKIING)) {
            paint.color = Color.BLACK
            paint.textSize = 50f
            paint.textAlign = Paint.Align.LEFT // Alignement à gauche
            canvas.drawText("🎿 TOURNEZ COMME UN VOLANT", 50f, h * 0.15f, paint)
            
            paint.color = Color.RED
            paint.textSize = 40f
            canvas.drawText("+ ALTERNEZ GAUCHE-DROITE!", 50f, h * 0.22f, paint)
            
            paint.color = Color.parseColor("#FF6600")
            paint.textSize = 30f
            canvas.drawText("ATTENDEZ QUE ÇA RALENTISSE!", 50f, h * 0.28f, paint)
        }
        
        // Barre de performance - DÉCENTRÉE VERS LA DROITE
        if (activity.gameState == BiathlonActivity.GameState.SKIING || activity.gameState == BiathlonActivity.GameState.FINAL_SKIING) {
            drawPerformanceBand(canvas, w * 0.55f, 80f, w * 0.4f, 60f) // Décalée vers la droite
        }
        
        // Barre de progression de l'écran
        drawScreenProgress(canvas, w, h)
    }
    
    private fun drawPerformanceBand(canvas: Canvas, x: Float, y: Float, width: Float, height: Float) {
        // Fond
        paint.color = Color.parseColor("#333333")
        paint.style = Paint.Style.FILL
        canvas.drawRect(x, y, x + width, y + height, paint)
        
        if (activity.pushCount < 2) {
            // Zones d'explication
            val zoneWidth = width / 5f
            val colors = arrayOf("#FF0000", "#FF8800", "#FFFF00", "#88FF00", "#00FF00")
            val labels = arrayOf("Lent", "Faible", "Moyen", "Bon", "Parfait")
            
            for (i in 0..4) {
                paint.color = Color.parseColor(colors[i])
                canvas.drawRect(x + i * zoneWidth, y, x + (i + 1) * zoneWidth, y + height, paint)
                
                paint.color = Color.BLACK
                paint.textSize = 24f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText(labels[i], x + i * zoneWidth + zoneWidth/2f, y + height/2f + 8f, paint)
            }
            
            paint.color = Color.WHITE
            paint.textSize = 32f
            canvas.drawText("QUALITÉ POUSSÉES", x + width/2f, y - 20f, paint)
        } else {
            // Performance en temps réel
            val pushColor = getPerformanceColor(activity.currentPushQuality)
            paint.color = pushColor
            canvas.drawRect(x, y, x + width/2f, y + height, paint)
            
            val rhythmColor = getPerformanceColor(activity.currentRhythmQuality)
            paint.color = rhythmColor
            canvas.drawRect(x + width/2f, y, x + width, y + height, paint)
            
            paint.color = Color.WHITE
            paint.textSize = 28f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("FORCE", x + width/4f, y - 15f, paint)
            canvas.drawText("RYTHME", x + 3*width/4f, y - 15f, paint)
            
            paint.color = Color.BLACK
            paint.textSize = 32f
            canvas.drawText("${(activity.currentPushQuality * 100).toInt()}%", x + width/4f, y + height/2f + 12f, paint)
            canvas.drawText("${(activity.currentRhythmQuality * 100).toInt()}%", x + 3*width/4f, y + height/2f + 12f, paint)
        }
        
        // Bordure
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        canvas.drawRect(x, y, x + width, y + height, paint)
        paint.style = Paint.Style.FILL
    }
    
    private fun getPerformanceColor(performance: Float): Int {
        return when {
            performance >= 0.8f -> Color.parseColor("#00FF00")
            performance >= 0.6f -> Color.parseColor("#88FF00")
            performance >= 0.4f -> Color.parseColor("#FFFF00")
            performance >= 0.2f -> Color.parseColor("#FF8800")
            else -> Color.parseColor("#FF0000")
        }
    }
    
    private fun drawScreenProgress(canvas: Canvas, w: Int, h: Int) {
        // Barre de progression de l'écran
        paint.color = Color.BLACK
        canvas.drawRect(w * 0.1f, 20f, w * 0.9f, 35f, paint)
        
        // Progression actuelle sur l'écran
        paint.color = Color.CYAN
        canvas.drawRect(w * 0.1f, 22f, w * 0.1f + (activity.skierX.coerceIn(0f, 1f) * w * 0.8f), 33f, paint)
        
        // Indicateur d'écran
        paint.color = Color.WHITE
        paint.textSize = 20f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("Écran ${activity.currentScreen}/6", w * 0.5f, 50f, paint)
    }
    
    private fun drawTarget(canvas: Canvas, px: Float, py: Float, index: Int) {
        if (activity.targetHitStatus[index]) {
            paint.color = Color.parseColor("#00aa00")
            canvas.drawCircle(px, py, 60f, paint)
            
            paint.color = Color.WHITE
            paint.textSize = 30f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("+${activity.targetScores[index]}", px, py + 10f, paint)
            
            paint.textSize = 20f
            canvas.drawText("TOUCHÉ", px, py + 70f, paint)
        } else {
            // VRAIE CIBLE DE BIATHLON avec cercles concentriques et points différents
            
            // Cercle extérieur - 1 point (blanc)
            paint.color = Color.parseColor("#FFFFFF")
            canvas.drawCircle(px, py, 60f, paint)
            
            // Cercle 9 - 2 points (noir)
            paint.color = Color.parseColor("#000000")
            canvas.drawCircle(px, py, 54f, paint)
            
            // Cercle 8 - 3 points (blanc)
            paint.color = Color.parseColor("#FFFFFF")
            canvas.drawCircle(px, py, 48f, paint)
            
            // Cercle 7 - 4 points (noir)
            paint.color = Color.parseColor("#000000")
            canvas.drawCircle(px, py, 42f, paint)
            
            // Cercle 6 - 5 points (blanc)
            paint.color = Color.parseColor("#FFFFFF")
            canvas.drawCircle(px, py, 36f, paint)
            
            // Cercle 5 - 6 points (noir)
            paint.color = Color.parseColor("#000000")
            canvas.drawCircle(px, py, 30f, paint)
            
            // Cercle 4 - 7 points (blanc)
            paint.color = Color.parseColor("#FFFFFF")
            canvas.drawCircle(px, py, 24f, paint)
            
            // Cercle 3 - 8 points (noir)
            paint.color = Color.parseColor("#000000")
            canvas.drawCircle(px, py, 18f, paint)
            
            // Cercle 2 - 9 points (blanc)
            paint.color = Color.parseColor("#FFFFFF")
            canvas.drawCircle(px, py, 12f, paint)
            
            // CENTRE ROUGE - 10 points (le jackpot !)
            paint.color = Color.parseColor("#FF0000")
            canvas.drawCircle(px, py, 6f, paint)
            
            // Numéro de la cible
            paint.color = Color.WHITE
            paint.textSize = 16f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${index + 1}", px, py + 80f, paint)
        }
    }
    
    private fun drawCrosshair(canvas: Canvas, w: Int, h: Int) {
        val crossX = activity.crosshair.x * w
        val crossY = activity.crosshair.y * h + 100
        
        paint.color = Color.BLACK
        paint.strokeWidth = 6f
        canvas.drawLine(crossX - 25, crossY, crossX + 25, crossY, paint)
        canvas.drawLine(crossX, crossY - 25, crossX, crossY + 25, paint)
        
        paint.color = Color.RED
        paint.strokeWidth = 2f
        canvas.drawLine(crossX - 20, crossY, crossX + 20, crossY, paint)
        canvas.drawLine(crossX, crossY - 20, crossX, crossY + 20, paint)
        
        paint.style = Paint.Style.FILL
        paint.color = Color.YELLOW
        canvas.drawCircle(crossX, crossY, 3f, paint)
    }
    
    private fun drawShootingUI(canvas: Canvas, w: Int, h: Int) {
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#003366")
        paint.textSize = 30f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("🔫 Munitions: ${5-activity.shotsFired}/5", 30f, h - 200f, paint) // Plus haut pour faire place aux gros rectangles
        canvas.drawText("🎯 Score: ${activity.totalScore} pts", 30f, h - 160f, paint)
        
        // RECTANGLES DE MUNITIONS 5 FOIS PLUS GROS
        val rectWidth = 200f  // 5 fois plus large (était 40f)
        val rectHeight = 100f // 5 fois plus haut (était 30f)
        val rectSpacing = 220f // Espacement ajusté
        
        for (i in 0 until 5) {
            paint.color = if (i < activity.shotsFired) Color.GRAY else Color.YELLOW
            val rectX = w - 1200f + i * rectSpacing // Ajusté pour les gros rectangles
            canvas.drawRect(rectX, h - 120f, rectX + rectWidth, h - 20f, paint)
            
            // Numéro sur chaque rectangle
            paint.color = Color.BLACK
            paint.textSize = 50f // Texte plus gros aussi
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${i+1}", rectX + rectWidth/2f, h - 60f, paint)
        }
    }
}
