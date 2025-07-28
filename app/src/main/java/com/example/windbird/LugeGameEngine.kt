package com.example.windbird

import android.graphics.Color
import kotlin.math.*
import kotlin.random.Random

class LugeGameEngine {
    
    // Variables de luge avec physique avancée
    var lugerX = 0.5f
    var lugerY = 0.75f
    var speed = 0f
    var maxSpeed = 140f
    var distance = 0f
    var totalDistance = 4000f
    var raceTime = 0f
    var altitude = 1000f
    
    // Physique avancée
    var velocity = Vector3(0f, 0f, 0f)
    var acceleration = Vector3(0f, 0f, 0f)
    var lugerRotation = 0f
    var lugerTilt = 0f
    var airResistance = 0f
    var groundFriction = 0f
    
    // Système de piste
    val trackCurves = mutableListOf<TrackCurve>()
    val trackElements = mutableListOf<TrackElement>()
    val trackSectors = mutableListOf<TrackSector>()
    var nextCurveIndex = 0
    var currentCurveStrength = 0f
    var curveDirection = 0f
    var currentSector = 0
    
    // Caméra 3D et effets visuels
    var cameraDistance = 150f
    var cameraHeight = 100f
    var cameraRotation = 0f
    var cameraTilt = 0f
    var fovEffect = 1f
    var perspectiveShift = 0f
    
    // Contrôles
    var gyroSensitivity = 1f
    
    // Performance
    var wallHits = 0
    var perfectCurves = 0
    var aerodynamics = 100f
    var precision = 100f
    var momentum = 0f
    var gForce = 0f
    var topSpeed = 0f
    var heartRate = 60f
    var adrenaline = 0f
    var stamina = 100f
    
    // Freinage
    var brakingPower = 0f
    var lastBrakeTime = 0L
    var brakingUsed = false
    var steeringPrecision = 100f
    var lugerMomentum = Vector3(0f, 0f, 0f)
    
    // Effets visuels
    var cameraShake = 0f
    var speedBlur = 0f
    var motionBlur = 0f
    var chromaticAberration = 0f
    var vignetting = 0f
    var bloom = 0f
    
    // Systèmes de particules
    val snowParticles3D = mutableListOf<SnowParticle3D>()
    val iceChips = mutableListOf<IceChip>()
    val speedStreaks = mutableListOf<SpeedStreak>()
    val wallSparks = mutableListOf<WallSpark>()
    val windTrails = mutableListOf<WindTrail>()
    val groundImpacts = mutableListOf<GroundImpact>()
    val sparkles = mutableListOf<IceSparkle>()
    val aeroTrails = mutableListOf<AeroTrail>()
    val curveIndicators = mutableListOf<CurveIndicator>()
    val speedZones = mutableListOf<SpeedZone>()
    val checkpoints = mutableListOf<Checkpoint>()
    
    // Score
    var finalScore = 0
    var scoreCalculated = false
    var sectorTimes = mutableListOf<Float>()
    var performanceMetrics = PerformanceMetrics()
    
    // Entrées capteurs
    private var tiltX = 0f
    private var tiltY = 0f
    private var tiltZ = 0f
    private var accelX = 0f
    private var accelY = 0f
    private var accelZ = 0f
    
    fun initialize() {
        resetAllParameters()
        generateAdvancedLugeTrack()
        initializeParticleSystems()
    }
    
    private fun resetAllParameters() {
        lugerX = 0.5f
        lugerY = 0.75f
        speed = 0f
        distance = 0f
        raceTime = 0f
        altitude = 1000f
        nextCurveIndex = 0
        currentCurveStrength = 0f
        curveDirection = 0f
        currentSector = 0
        
        velocity = Vector3(0f, 0f, 0f)
        acceleration = Vector3(0f, 0f, 0f)
        lugerRotation = 0f
        lugerTilt = 0f
        airResistance = 0f
        groundFriction = 0f
        
        cameraDistance = 150f
        cameraHeight = 100f
        cameraRotation = 0f
        cameraTilt = 0f
        fovEffect = 1f
        perspectiveShift = 0f
        
        gyroSensitivity = 1f
        wallHits = 0
        perfectCurves = 0
        aerodynamics = 100f
        precision = 100f
        momentum = 0f
        gForce = 0f
        topSpeed = 0f
        heartRate = 60f
        adrenaline = 0f
        stamina = 100f
        
        brakingPower = 0f
        lastBrakeTime = 0L
        brakingUsed = false
        steeringPrecision = 100f
        lugerMomentum = Vector3(0f, 0f, 0f)
        
        cameraShake = 0f
        speedBlur = 0f
        motionBlur = 0f
        chromaticAberration = 0f
        vignetting = 0f
        bloom = 0f
        
        finalScore = 0
        scoreCalculated = false
        
        clearAllLists()
    }
    
    private fun clearAllLists() {
        trackCurves.clear()
        trackElements.clear()
        trackSectors.clear()
        snowParticles3D.clear()
        iceChips.clear()
        speedStreaks.clear()
        wallSparks.clear()
        windTrails.clear()
        groundImpacts.clear()
        sparkles.clear()
        aeroTrails.clear()
        curveIndicators.clear()
        speedZones.clear()
        checkpoints.clear()
        sectorTimes.clear()
    }
    
    fun startRacing() {
        speed = 20f
    }
    
    fun updateInputs(tX: Float, tY: Float, tZ: Float, aX: Float, aY: Float, aZ: Float) {
        tiltX = tX
        tiltY = tY
        tiltZ = tZ
        accelX = aX
        accelY = aY
        accelZ = aZ
    }
    
    fun updateRiding(deltaTime: Float) {
        handleAdvancedLugeMovement(deltaTime)
        handleAdvancedTrackCurves()
        handleAdvancedFootBraking()
        updateAdvancedPhysics(deltaTime)
        updateSectorProgression()
        updateCheckpoints()
        updateAdvancedRaceProgress(deltaTime)
        updateAdvancedEffects(deltaTime)
        updateCameraSystem(deltaTime)
    }
    
    private fun generateAdvancedLugeTrack() {
        var currentDistance = 0f
        
        val sectorTypes = listOf(
            TrackSector.SectorType.GENTLE_START,
            TrackSector.SectorType.SPEED_ZONE,
            TrackSector.SectorType.TECHNICAL_CURVES,
            TrackSector.SectorType.EXTREME_DESCENT,
            TrackSector.SectorType.TUNNEL_SECTION,
            TrackSector.SectorType.CHICANE_COMPLEX,
            TrackSector.SectorType.FINAL_SPRINT
        )
        
        for ((index, sectorType) in sectorTypes.withIndex()) {
            val sectorLength = when (sectorType) {
                TrackSector.SectorType.GENTLE_START -> 400f
                TrackSector.SectorType.SPEED_ZONE -> 800f
                TrackSector.SectorType.TECHNICAL_CURVES -> 600f
                TrackSector.SectorType.EXTREME_DESCENT -> 700f
                TrackSector.SectorType.TUNNEL_SECTION -> 500f
                TrackSector.SectorType.CHICANE_COMPLEX -> 600f
                TrackSector.SectorType.FINAL_SPRINT -> 400f
            }
            
            trackSectors.add(TrackSector(
                name = sectorType.displayName,
                startDistance = currentDistance,
                length = sectorLength,
                type = sectorType,
                difficulty = index * 0.2f + 0.3f
            ))
            
            generateSectorElements(currentDistance, sectorLength, sectorType)
            currentDistance += sectorLength
        }
        
        // Génération des checkpoints
        for (i in 1..6) {
            checkpoints.add(Checkpoint(
                distance = i * (totalDistance / 7f),
                name = "CP$i",
                targetTime = i * 18f
            ))
        }
    }
    
    private fun generateSectorElements(startDistance: Float, length: Float, sectorType: TrackSector.SectorType) {
        val curveCount = when (sectorType) {
            TrackSector.SectorType.GENTLE_START -> 3
            TrackSector.SectorType.SPEED_ZONE -> 2
            TrackSector.SectorType.TECHNICAL_CURVES -> 8
            TrackSector.SectorType.EXTREME_DESCENT -> 5
            TrackSector.SectorType.TUNNEL_SECTION -> 4
            TrackSector.SectorType.CHICANE_COMPLEX -> 12
            TrackSector.SectorType.FINAL_SPRINT -> 2
        }
        
        // Génération des virages
        for (i in 0 until curveCount) {
            val curveDistance = startDistance + (i + 1) * (length / (curveCount + 1))
            val curveType = getCurveTypeForSector(sectorType, i)
            
            trackCurves.add(TrackCurve(
                distance = curveDistance,
                type = curveType,
                direction = if (Random.nextBoolean()) -1f else 1f,
                length = getCurveLengthForType(curveType),
                banking = getBankingForSector(sectorType)
            ))
        }
        
        // Génération des éléments de décor
        val elementCount = (length / 80f).toInt()
        for (i in 0 until elementCount) {
            val elementDistance = startDistance + i * 80f + Random.nextFloat() * 40f
            val elementType = getElementTypeForSector(sectorType, i)
            
            trackElements.add(TrackElement(
                distance = elementDistance,
                sideOffset = if (Random.nextBoolean()) -1f else 1f,
                offsetDistance = Random.nextFloat() * 100f + 50f,
                type = elementType,
                scale = Random.nextFloat() * 0.5f + 0.8f,
                height = getElementHeight(elementType)
            ))
        }
    }
    
    private fun getCurveTypeForSector(sectorType: TrackSector.SectorType, index: Int): TrackCurve.Type {
        return when (sectorType) {
            TrackSector.SectorType.GENTLE_START -> TrackCurve.Type.GENTLE
            TrackSector.SectorType.SPEED_ZONE -> if (index % 2 == 0) TrackCurve.Type.GENTLE else TrackCurve.Type.MEDIUM
            TrackSector.SectorType.TECHNICAL_CURVES -> when (index % 3) {
                0 -> TrackCurve.Type.MEDIUM
                1 -> TrackCurve.Type.SHARP
                else -> TrackCurve.Type.HAIRPIN
            }
            TrackSector.SectorType.EXTREME_DESCENT -> if (index % 2 == 0) TrackCurve.Type.SHARP else TrackCurve.Type.MEDIUM
            TrackSector.SectorType.TUNNEL_SECTION -> TrackCurve.Type.MEDIUM
            TrackSector.SectorType.CHICANE_COMPLEX -> if (index % 2 == 0) TrackCurve.Type.CHICANE else TrackCurve.Type.SHARP
            TrackSector.SectorType.FINAL_SPRINT -> TrackCurve.Type.GENTLE
        }
    }
    
    private fun getCurveLengthForType(type: TrackCurve.Type): Float {
        return when (type) {
            TrackCurve.Type.GENTLE -> 150f
            TrackCurve.Type.MEDIUM -> 120f
            TrackCurve.Type.SHARP -> 80f
            TrackCurve.Type.HAIRPIN -> 60f
            TrackCurve.Type.CHICANE -> 200f
        }
    }
    
    private fun getBankingForSector(sectorType: TrackSector.SectorType): Float {
        return when (sectorType) {
            TrackSector.SectorType.EXTREME_DESCENT -> 15f
            TrackSector.SectorType.SPEED_ZONE -> 8f
            else -> 0f
        }
    }
    
    private fun getElementTypeForSector(sectorType: TrackSector.SectorType, index: Int): TrackElement.ElementType {
        return when (sectorType) {
            TrackSector.SectorType.TUNNEL_SECTION -> TrackElement.ElementType.TUNNEL_WALL
            TrackSector.SectorType.SPEED_ZONE -> if (index % 3 == 0) TrackElement.ElementType.SPEED_MARKER else TrackElement.ElementType.SAFETY_BARRIER
            else -> when (Random.nextInt(5)) {
                0 -> TrackElement.ElementType.TREE
                1 -> TrackElement.ElementType.SAFETY_BARRIER
                2 -> TrackElement.ElementType.SPECTATOR
                3 -> TrackElement.ElementType.FLAG
                else -> TrackElement.ElementType.ROCK
            }
        }
    }
    
    private fun getElementHeight(elementType: TrackElement.ElementType): Float {
        return when (elementType) {
            TrackElement.ElementType.TREE -> Random.nextFloat() * 80f + 120f
            TrackElement.ElementType.SAFETY_BARRIER -> 60f
            TrackElement.ElementType.SPECTATOR -> 80f
            TrackElement.ElementType.FLAG -> 200f
            TrackElement.ElementType.TUNNEL_WALL -> 300f
            TrackElement.ElementType.SPEED_MARKER -> 100f
            TrackElement.ElementType.ROCK -> Random.nextFloat() * 40f + 30f
        }
    }
    
    private fun initializeParticleSystems() {
        for (i in 0..299) {
            snowParticles3D.add(SnowParticle3D(
                x = Random.nextFloat() * 2000f - 1000f,
                y = Random.nextFloat() * 1000f,
                z = Random.nextFloat() * 2000f + 500f,
                vx = (Random.nextFloat() - 0.5f) * 20f,
                vy = Random.nextFloat() * 30f + 10f,
                vz = Random.nextFloat() * 40f + 20f,
                size = Random.nextFloat() * 3f + 1f,
                life = Random.nextFloat() * 5f + 3f
            ))
        }
    }
    
    private fun handleAdvancedLugeMovement(deltaTime: Float) {
        // Contrôle de direction avec physique réaliste
        val steeringInput = tiltX * 0.6f
        val curveEffect = currentCurveStrength * curveDirection * 0.3f
        val totalSteering = steeringInput + curveEffect
        
        // Inertie du lugeur
        lugerMomentum.x = lugerMomentum.x * 0.95f + totalSteering * 0.05f
        lugerX += lugerMomentum.x * deltaTime * (speed / 50f)
        lugerX = lugerX.coerceIn(0.05f, 0.95f)
        
        // Effets visuels de rotation
        lugerRotation = lugerMomentum.x * 15f
        lugerTilt = gForce * 8f
        
        // Système aérodynamique avancé
        handleAerodynamics(deltaTime)
        
        // Physique de vitesse
        handleSpeedPhysics(deltaTime)
        
        // Gestion des collisions avec les murs
        if (lugerX <= 0.1f || lugerX >= 0.9f) {
            handleAdvancedWallContact()
        }
        
        // Calculs des forces et effets
        updateForces()
        
        // Génération d'effets selon la vitesse
        if (speed > 50f) {
            generateAdvancedSpeedEffects(deltaTime)
        }
        
        if (speed > 90f) {
            cameraShake = (speed - 90f) / 50f * 0.4f
        }
    }
    
    private fun handleAerodynamics(deltaTime: Float) {
        val aeroPosition = tiltY.coerceIn(-1.5f, 1.5f)
        
        when {
            aeroPosition < -0.8f -> {
                // Position très aérodynamique
                acceleration.z += 4f
                aerodynamics += 0.1f
                airResistance = speed * speed * 0.001f * 0.7f
            }
            aeroPosition < -0.3f -> {
                // Position aérodynamique normale
                acceleration.z += 2.5f
                aerodynamics += 0.05f
                airResistance = speed * speed * 0.001f * 0.85f
            }
            aeroPosition > 0.8f -> {
                // Position de freinage aéro
                acceleration.z -= 2f
                aerodynamics -= 0.08f
                airResistance = speed * speed * 0.001f * 1.5f
            }
            else -> {
                // Position neutre
                acceleration.z += 1.5f
                airResistance = speed * speed * 0.001f
            }
        }
    }
    
    private fun handleSpeedPhysics(deltaTime: Float) {
        // Effet de la gravité selon l'altitude
        val gravityEffect = 2f * (1f + (1000f - altitude) / 1000f)
        acceleration.z += gravityEffect
        
        // Friction du sol
        groundFriction = speed * 0.02f * (1f - (aerodynamics - 100f) / 100f)
        
        // Application de la physique
        velocity.z += (acceleration.z - airResistance - groundFriction) * deltaTime
        velocity.z = velocity.z.coerceIn(0f, maxSpeed)
        speed = velocity.z
        
        // Reset de l'accélération
        acceleration = Vector3(0f, 0f, 0f)
    }
    
    private fun updateForces() {
        momentum = speed / maxSpeed
        gForce = abs(lugerMomentum.x) * speed * 0.03f + abs(currentCurveStrength) * speed * 0.02f
        topSpeed = maxOf(topSpeed, speed)
        
        // Mise à jour des métriques biologiques
        heartRate = 60f + speed * 0.8f + gForce * 20f + adrenaline * 30f
        adrenaline = (speed / maxSpeed * 0.7f + gForce * 0.3f).coerceIn(0f, 1f)
        
        // Stamina selon l'effort
        val steeringInput = tiltX * 0.6f
        stamina -= (gForce * 0.1f + abs(steeringInput) * 0.05f) * 0.01f
        stamina = stamina.coerceIn(0f, 100f)
        
        // Ajustement de la sensibilité selon la fatigue
        gyroSensitivity = 1f - (100f - stamina) / 200f
        
        // Effets visuels selon la vitesse
        updateVisualEffects()
    }
    
    private fun updateVisualEffects() {
        speedBlur = (speed / maxSpeed * 0.9f).coerceIn(0f, 1f)
        motionBlur = speedBlur * 0.8f
        chromaticAberration = (speed - 100f) / 40f * 0.3f
        vignetting = speedBlur * 0.6f
        bloom = (speed / maxSpeed) * 0.4f
        fovEffect = 1f + speedBlur * 0.3f
    }
    
    private fun handleAdvancedWallContact() {
        wallHits++
        
        val wallSide = if (lugerX <= 0.1f) -1f else 1f
        lugerMomentum.x = -lugerMomentum.x * 0.6f // Rebond avec perte d'énergie
        
        val impactForce = abs(lugerMomentum.x) + speed * 0.01f
        speed *= (1f - impactForce * 0.15f).coerceIn(0.7f, 1f)
        
        precision -= 8f * impactForce
        steeringPrecision -= 5f
        
        // Correction de position
        lugerX = if (wallSide < 0) 0.15f else 0.85f
        
        // Effets du contact
        cameraShake = 0.5f
        generateAdvancedWallSparks(wallSide)
        aerodynamics -= 2f
        adrenaline += 0.2f
    }
    
    private fun handleAdvancedTrackCurves() {
        while (nextCurveIndex < trackCurves.size) {
            val curve = trackCurves[nextCurveIndex]
            val curveDistance = curve.distance - distance
            
            if (curveDistance < -curve.length) {
                // Virage terminé
                nextCurveIndex++
                currentCurveStrength = 0f
                curveDirection = 0f
                continue
            }
            
            if (curveDistance <= 200f && curveDistance >= -curve.length) {
                // Dans la zone d'influence du virage
                val curveProgress = when {
                    curveDistance > 0 -> 0f
                    curveDistance >= -curve.length -> (-curveDistance) / curve.length
                    else -> 1f
                }
                
                // Application de la force du virage
                if (curveDistance <= 0f && curveDistance >= -curve.length) {
                    currentCurveStrength = sin(curveProgress * PI).toFloat() * curve.intensity
                    curveDirection = curve.direction
                    
                    // Effet du banking
                    val bankingEffect = curve.banking * sin(curveProgress * PI).toFloat()
                    lugerY += bankingEffect * 0.001f
                }
                
                // Évaluation de la performance
                if (curveProgress > 0.2f && curveProgress < 0.8f) {
                    evaluateAdvancedCurvePerformance(curve, curveProgress)
                }
                
                // Ajout d'indicateurs visuels
                if (curveDistance > 0f && curveDistance < 120f) {
                    addAdvancedCurveIndicator(curve, curveDistance)
                }
                
                break
            } else {
                break
            }
        }
    }
    
    private fun evaluateAdvancedCurvePerformance(curve: TrackCurve, progress: Float) {
        val idealPosition = 0.5f + curve.direction * curve.intensity * 0.3f
        val positionError = abs(lugerX - idealPosition)
        
        val idealSpeed = maxSpeed * (1f - curve.intensity * 0.4f)
        val speedError = abs(speed - idealSpeed) / idealSpeed
        
        when {
            positionError < 0.15f && speedError < 0.2f -> {
                // Virage parfait !
                perfectCurves++
                precision += 4f
                aerodynamics += 2f
                generatePerfectCurveEffect()
                
                val difficultyBonus = curve.intensity * 2f
                precision += difficultyBonus
            }
            positionError < 0.25f && speedError < 0.35f -> {
                // Bon virage
                precision += 1f
                generateGoodCurveEffect()
            }
            positionError > 0.4f || speedError > 0.5f -> {
                // Virage raté
                precision -= 3f
                aerodynamics -= 1f
            }
        }
        
        // Pénalité pour vitesse excessive
        if (curve.intensity > 0.7f && speed > idealSpeed * 1.3f) {
            steeringPrecision -= 10f
            lugerMomentum.x += curve.direction * 0.1f // Dérapage
        }
    }
    
    private fun handleAdvancedFootBraking() {
        val currentTime = System.currentTimeMillis()
        
        val brakeThreshold = 2.5f - (stamina / 100f) * 0.5f
        val brakeInput = abs(tiltZ) > brakeThreshold || 
                        sqrt(accelX*accelX + accelY*accelY + accelZ*accelZ) > 18f
        
        if (brakeInput && currentTime - lastBrakeTime > 400) {
            val brakeEfficiency = stamina / 100f * steeringPrecision / 100f
            val brakeForce = 15f * brakeEfficiency
            
            brakingPower += brakeForce
            speed *= (1f - brakeForce * 0.008f).coerceIn(0.85f, 1f)
            brakingUsed = true
            lastBrakeTime = currentTime
            
            // Coûts du freinage
            stamina -= 2f
            steeringPrecision -= 0.5f
            
            generateAdvancedBrakingEffect()
        }
        
        brakingPower = brakingPower.coerceIn(0f, 100f)
        brakingPower *= 0.96f
    }
    
    private fun updateAdvancedPhysics(deltaTime: Float) {
        val windResistance = 0.015f * (speed / maxSpeed)
        val fatigueEffect = (100f - stamina) / 1000f
        
        // Dégradation naturelle
        aerodynamics -= windResistance + fatigueEffect
        precision -= 0.025f + fatigueEffect * 2f
        steeringPrecision -= 0.01f + fatigueEffect
        
        // Récupération avec bonne technique
        if (speed > 60f && abs(tiltX) < 0.3f && gForce < 0.5f) {
            aerodynamics += 0.04f
            precision += 0.02f
        }
        
        // Bonus de stamina en position aérodynamique
        if (tiltY < -0.5f) {
            stamina += 0.1f * deltaTime
        }
        
        // Effet de l'altitude
        val altitudeEffect = altitude / 1000f
        aerodynamics *= (1f + altitudeEffect * 0.02f)
        
        // Contraintes réalistes
        aerodynamics = aerodynamics.coerceIn(60f, 140f)
        precision = precision.coerceIn(40f, 140f)
        steeringPrecision = steeringPrecision.coerceIn(30f, 120f)
        stamina = stamina.coerceIn(0f, 100f)
    }
    
    private fun updateSectorProgression() {
        val currentSectorIndex = trackSectors.indexOfFirst { 
            distance >= it.startDistance && distance < it.startDistance + it.length 
        }
        
        if (currentSectorIndex != -1 && currentSectorIndex != currentSector) {
            if (currentSector < trackSectors.size) {
                sectorTimes.add(raceTime)
            }
            currentSector = currentSectorIndex
            
            // Effets spéciaux selon le secteur
            val sector = trackSectors[currentSector]
            when (sector.type) {
                TrackSector.SectorType.SPEED_ZONE -> maxSpeed += 10f
                TrackSector.SectorType.TUNNEL_SECTION -> {
                    aerodynamics += 5f
                    precision -= 10f
                }
                TrackSector.SectorType.EXTREME_DESCENT -> altitude -= 200f
                else -> {}
            }
        }
    }
    
    private fun updateCheckpoints() {
        for (checkpoint in checkpoints) {
            if (!checkpoint.passed && distance >= checkpoint.distance) {
                checkpoint.passed = true
                checkpoint.actualTime = raceTime
                
                val timeDifference = checkpoint.actualTime - checkpoint.targetTime
                if (timeDifference < 0) {
                    // En avance !
                    precision += 10f
                    aerodynamics += 5f
                } else if (timeDifference > 5f) {
                    // En retard
                    precision -= 5f
                }
                
                generateCheckpointEffect(checkpoint)
            }
        }
    }
    
    private fun updateAdvancedRaceProgress(deltaTime: Float) {
        val progressMultiplier = speed / 60f
        distance += speed * progressMultiplier * deltaTime * 2f
        altitude = maxOf(0f, 1000f - (distance / totalDistance) * 1000f)
    }
    
    private fun updateAdvancedEffects(deltaTime: Float) {
        updateParticles(deltaTime)
        updateCameraEffects(deltaTime)
    }
    
    private fun updateParticles(deltaTime: Float) {
        // Particules de neige 3D - mise à jour sans removeAll
        val particlesToUpdate = snowParticles3D.toList()
        for (particle in particlesToUpdate) {
            particle.x += particle.vx * deltaTime
            particle.y += particle.vy * deltaTime
            particle.z += particle.vz * deltaTime
            particle.life -= deltaTime
            
            if (particle.life <= 0f || particle.z < -100f) {
                // Recycler la particule
                particle.x = Random.nextFloat() * 1000f - 500f
                particle.y = Random.nextFloat() * 500f + 200f
                particle.z = Random.nextFloat() * 200f + 1000f
                particle.life = Random.nextFloat() * 5f + 3f
            }
        }
        
        // Mise à jour des autres particules
        updateOtherParticles(deltaTime)
    }
    
    private fun updateOtherParticles(deltaTime: Float) {
        // Mise à jour des copeaux de glace
        val chipsToRemove = mutableListOf<IceChip>()
        for (chip in iceChips) {
            chip.x += chip.vx * deltaTime
            chip.y += chip.vy * deltaTime
            chip.z += chip.vz * deltaTime
            chip.life -= deltaTime
            if (chip.life <= 0f || chip.z < -200f) {
                chipsToRemove.add(chip)
            }
        }
        iceChips.removeAll(chipsToRemove)
        
        // Mise à jour des traînées de vitesse
        val streaksToRemove = mutableListOf<SpeedStreak>()
        for (streak in speedStreaks) {
            streak.x += streak.vx * deltaTime
            streak.y += streak.vy * deltaTime
            streak.life -= deltaTime
            if (streak.life <= 0f || streak.x < -200f) {
                streaksToRemove.add(streak)
            }
        }
        speedStreaks.removeAll(streaksToRemove)
        
        // Mise à jour des étincelles de mur
        val sparksToRemove = mutableListOf<WallSpark>()
        for (spark in wallSparks) {
            spark.x += spark.vx * deltaTime
            spark.y += spark.vy * deltaTime
            spark.z += spark.vz * deltaTime
            spark.life -= deltaTime
            if (spark.life <= 0f || spark.y > 900f) {
                sparksToRemove.add(spark)
            }
        }
        wallSparks.removeAll(sparksToRemove)
        
        // Mise à jour des traînées de vent
        val windToRemove = mutableListOf<WindTrail>()
        for (trail in windTrails) {
            trail.x += trail.vx * deltaTime
            trail.y += trail.vy * deltaTime
            trail.z += trail.vz * deltaTime
            trail.life -= deltaTime
            if (trail.life <= 0f || trail.z < -300f) {
                windToRemove.add(trail)
            }
        }
        windTrails.removeAll(windToRemove)
        
        // Mise à jour des impacts au sol
        val impactsToRemove = mutableListOf<GroundImpact>()
        for (impact in groundImpacts) {
            impact.x += impact.vx * deltaTime
            impact.y += impact.vy * deltaTime
            impact.z += impact.vz * deltaTime
            impact.life -= deltaTime
            if (impact.life <= 0f) {
                impactsToRemove.add(impact)
            }
        }
        groundImpacts.removeAll(impactsToRemove)
        
        // Mise à jour des étincelles
        val sparklesToRemove = mutableListOf<IceSparkle>()
        for (sparkle in sparkles) {
            sparkle.x += sparkle.vx * deltaTime
            sparkle.y += sparkle.vy * deltaTime
            sparkle.z += sparkle.vz * deltaTime
            sparkle.life -= deltaTime
            if (sparkle.life <= 0f) {
                sparklesToRemove.add(sparkle)
            }
        }
        sparkles.removeAll(sparklesToRemove)
        
        // Mise à jour des traînées aéro
        val aeroToRemove = mutableListOf<AeroTrail>()
        for (trail in aeroTrails) {
            trail.life -= deltaTime
            if (trail.life <= 0f) {
                aeroToRemove.add(trail)
            }
        }
        aeroTrails.removeAll(aeroToRemove)
        
        // Mise à jour des indicateurs de virage
        val indicatorsIterator = curveIndicators.iterator()
        while (indicatorsIterator.hasNext()) {
            val indicator = indicatorsIterator.next()
            indicator.life -= deltaTime
            indicator.urgency = minOf(1f, indicator.urgency + deltaTime * 0.5f)
            if (indicator.life <= 0f) {
                indicatorsIterator.remove()
            }
        }
    }
    
    private fun updateCameraEffects(deltaTime: Float) {
        cameraShake = maxOf(0f, cameraShake - deltaTime * 2f)
        perspectiveShift = sin(raceTime * 0.5f) * speedBlur * 5f
    }
    
    private fun updateCameraSystem(deltaTime: Float) {
        val targetCameraRotation = lugerMomentum.x * 5f
        cameraRotation = cameraRotation * 0.9f + targetCameraRotation * 0.1f
        
        val targetCameraTilt = -gForce * 3f
        cameraTilt = cameraTilt * 0.95f + targetCameraTilt * 0.05f
        
        val targetDistance = 150f + speed * 0.5f
        cameraDistance = cameraDistance * 0.98f + targetDistance * 0.02f
        
        val targetHeight = 100f + abs(currentCurveStrength) * 30f
        cameraHeight = cameraHeight * 0.95f + targetHeight * 0.05f
    }
    
    // Génération d'effets et particules
    private fun generateAdvancedSpeedEffects(deltaTime: Float) {
        val effectIntensity = speed / maxSpeed
        
        // Particules de neige selon la vitesse
        if (Random.nextFloat() < effectIntensity * 0.8f) {
            snowParticles3D.add(SnowParticle3D(
                x = Random.nextFloat() * 1000f - 500f,
                y = Random.nextFloat() * 500f + 200f,
                z = Random.nextFloat() * 200f + 1000f,
                vx = (Random.nextFloat() - 0.5f) * speed * 0.1f,
                vy = Random.nextFloat() * speed * 0.05f,
                vz = -speed * 2f,
                size = Random.nextFloat() * 4f + 2f,
                life = 2f + effectIntensity
            ))
        }
        
        // Copeaux de glace
        if (Random.nextFloat() < 0.6f) {
            iceChips.add(IceChip(
                x = Random.nextFloat() * 400f + 300f,
                y = Random.nextFloat() * 300f + 400f,
                z = 50f,
                vx = (Random.nextFloat() - 0.5f) * speed * 0.15f,
                vy = Random.nextFloat() * speed * 0.1f + 20f,
                vz = -speed * 0.8f,
                life = 1.5f,
                sparkle = speed > 80f
            ))
        }
        
        // Traînées aérodynamiques
        if (speed > 70f && Random.nextFloat() < 0.4f) {
            aeroTrails.add(AeroTrail(
                x = Random.nextFloat() * 200f + 400f,
                y = Random.nextFloat() * 100f + 450f,
                z = 30f,
                length = speed * 2f,
                intensity = effectIntensity,
                life = 1f
            ))
        }
        
        // Traînées de vent
        if (speed > 90f) {
            windTrails.add(WindTrail(
                x = Random.nextFloat() * 600f + 200f,
                y = Random.nextFloat() * 400f + 300f,
                z = Random.nextFloat() * 100f + 80f,
                vx = (Random.nextFloat() - 0.5f) * 30f,
                vy = Random.nextFloat() * 20f,
                vz = -speed * 1.5f,
                life = 2f,
                intensity = effectIntensity
            ))
        }
        
        // Limitation du nombre de particules
        limitParticleCount()
    }
    
    private fun limitParticleCount() {
        // Limitation du nombre de particules avec suppression des plus anciens
        while (snowParticles3D.size > 300) {
            snowParticles3D.removeFirstOrNull()
        }
        while (iceChips.size > 50) {
            iceChips.removeFirstOrNull()
        }
        while (aeroTrails.size > 30) {
            aeroTrails.removeFirstOrNull()
        }
        while (windTrails.size > 20) {
            windTrails.removeFirstOrNull()
        }
    }
    
    private fun generateAdvancedWallSparks(wallSide: Float) {
        for (i in 0..11) {
            wallSparks.add(WallSpark(
                x = if (wallSide < 0) 100f else 700f,
                y = Random.nextFloat() * 200f + 400f,
                z = Random.nextFloat() * 50f + 30f,
                vx = wallSide * Random.nextFloat() * 80f + 40f,
                vy = Random.nextFloat() * -60f - 20f,
                vz = Random.nextFloat() * 40f + 10f,
                life = 1.5f,
                color = if (Random.nextFloat() < 0.3f) Color.RED else Color.YELLOW,
                intensity = speed / maxSpeed
            ))
        }
    }
    
    private fun generatePerfectCurveEffect() {
        for (i in 0..14) {
            sparkles.add(IceSparkle(
                x = Random.nextFloat() * 200f + 350f,
                y = Random.nextFloat() * 150f + 375f,
                z = Random.nextFloat() * 80f + 40f,
                vx = (Random.nextFloat() - 0.5f) * 40f,
                vy = Random.nextFloat() * -30f - 10f,
                vz = Random.nextFloat() * 20f,
                life = 2f,
                color = Color.CYAN,
                sparkleRate = 0.1f
            ))
        }
    }
    
    private fun generateGoodCurveEffect() {
        for (i in 0..7) {
            sparkles.add(IceSparkle(
                x = Random.nextFloat() * 150f + 375f,
                y = Random.nextFloat() * 100f + 400f,
                z = Random.nextFloat() * 60f + 30f,
                vx = (Random.nextFloat() - 0.5f) * 25f,
                vy = Random.nextFloat() * -20f - 5f,
                vz = Random.nextFloat() * 15f,
                life = 1.2f,
                color = Color.WHITE,
                sparkleRate = 0.2f
            ))
        }
    }
    
    private fun generateAdvancedBrakingEffect() {
        for (i in 0..7) {
            groundImpacts.add(GroundImpact(
                x = Random.nextFloat() * 100f + 450f,
                y = Random.nextFloat() * 60f + 530f,
                z = 10f,
                vx = (Random.nextFloat() - 0.5f) * 30f,
                vy = Random.nextFloat() * 40f + 10f,
                vz = Random.nextFloat() * 20f,
                life = 1.3f,
                size = Random.nextFloat() * 8f + 5f
            ))
        }
    }
    
    private fun generateCheckpointEffect(checkpoint: Checkpoint) {
        for (i in 0..19) {
            sparkles.add(IceSparkle(
                x = Random.nextFloat() * 300f + 350f,
                y = Random.nextFloat() * 200f + 300f,
                z = Random.nextFloat() * 100f + 50f,
                vx = (Random.nextFloat() - 0.5f) * 60f,
                vy = Random.nextFloat() * -40f - 20f,
                vz = Random.nextFloat() * 30f + 10f,
                life = 3f,
                color = if (checkpoint.actualTime < checkpoint.targetTime) Color.GREEN else Color.YELLOW,
                sparkleRate = 0.05f
            ))
        }
    }
    
    private fun addAdvancedCurveIndicator(curve: TrackCurve, distance: Float) {
        val urgency = (120f - distance) / 120f
        
        curveIndicators.add(CurveIndicator(
            direction = curve.direction,
            intensity = curve.intensity,
            type = curve.type,
            distance = distance,
            urgency = urgency,
            life = 4f,
            banking = curve.banking
        ))
        
        while (curveIndicators.size > 3) {
            curveIndicators.removeFirstOrNull()
        }
    }
    
    fun calculateFinalScore() {
        if (!scoreCalculated) {
            // Mise à jour des métriques finales
            performanceMetrics.finalTime = raceTime
            performanceMetrics.topSpeed = topSpeed
            performanceMetrics.averageSpeed = distance / raceTime
            performanceMetrics.perfectCurves = perfectCurves
            performanceMetrics.wallHits = wallHits
            performanceMetrics.finalAerodynamics = aerodynamics
            performanceMetrics.finalPrecision = precision
            performanceMetrics.brakingUsed = brakingUsed
            performanceMetrics.stamina = stamina
            
            // Calcul de score complexe
            val timeBonus = maxOf(0, (180 - raceTime).toInt()) * 3
            val speedBonus = (performanceMetrics.averageSpeed / maxSpeed * 100).toInt()
            val topSpeedBonus = (topSpeed / maxSpeed * 80).toInt()
            val precisionBonus = ((precision - 100f) * 2f).toInt()
            val aerodynamicsBonus = ((aerodynamics - 100f) * 1.8f).toInt()
            val perfectCurveBonus = perfectCurves * 35
            val sectorBonus = sectorTimes.size * 15
            val checkpointBonus = checkpoints.count { it.passed && it.actualTime < it.targetTime } * 25
            val staminaBonus = (stamina * 0.5f).toInt()
            
            val wallPenalty = wallHits * 20
            val brakingPenalty = if (brakingUsed) 15 else 0
            val fatigePenalty = ((100f - stamina) * 0.3f).toInt()
            
            finalScore = maxOf(100, timeBonus + speedBonus + topSpeedBonus + precisionBonus + 
                             aerodynamicsBonus + perfectCurveBonus + sectorBonus + checkpointBonus + 
                             staminaBonus - wallPenalty - brakingPenalty - fatigePenalty)
            
            scoreCalculated = true
        }
    }
}
