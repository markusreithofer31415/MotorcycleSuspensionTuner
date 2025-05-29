package com.example.motorcyclesuspensiontuner

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private val accelerometerReadings = mutableListOf<AccelerometerData>()
    private var isRecording = false

    // UI Elements
    private lateinit var buttonStartStopTestRide: Button
    private lateinit var textViewCharacteristicVibration: TextView
    private lateinit var lineChartVibrations: LineChart
    private lateinit var textViewCurrentRebound: TextView
    private lateinit var textViewCurrentCompression: TextView
    private lateinit var textViewRecommendedRebound: TextView
    private lateinit var textViewRecommendedCompression: TextView

    private var previousRmsValue: Float? = null

    // EMA filter alpha
    private val alpha = 0.8f // Instance alpha for live filtering during recording
    private val gravity = floatArrayOf(0f, 0f, 0f) // Instance gravity for live filtering

    // Data class for returning recommendation strings, making it easier to test/manage
    data class RecommendationStrings(
        val recRebound: String,
        val recCompression: String,
        val currentReboundInfo: String,
        val currentCompressionInfo: String
    )

    companion object {
        private const val LOW_VIBRATION_THRESHOLD = 0.5f
        private const val HIGH_VIBRATION_THRESHOLD = 1.5f
        private const val SIGNIFICANT_IMPROVEMENT_DELTA = -0.1f // Negative for improvement
        private const val SIGNIFICANT_WORSENING_DELTA = 0.1f
        private const val DEFAULT_EMA_ALPHA = 0.8f // Default alpha for processing, can be different from live

        fun calculateRmsForData(readings: List<AccelerometerData>, emaAlpha: Float = DEFAULT_EMA_ALPHA): Float? {
            if (readings.isEmpty()) {
                return null
            }

            val dynamicMagnitudes = mutableListOf<Float>()
            val processingGravity = floatArrayOf(0f, 0f, 0f)

            processingGravity[0] = readings.firstOrNull()?.x ?: 0f
            processingGravity[1] = readings.firstOrNull()?.y ?: 0f
            processingGravity[2] = readings.firstOrNull()?.z ?: 0f

            for (data in readings) {
                processingGravity[0] = emaAlpha * processingGravity[0] + (1 - emaAlpha) * data.x
                processingGravity[1] = emaAlpha * processingGravity[1] + (1 - emaAlpha) * data.y
                processingGravity[2] = emaAlpha * processingGravity[2] + (1 - emaAlpha) * data.z

                val dynamicAccX = data.x - processingGravity[0]
                val dynamicAccY = data.y - processingGravity[1]
                val dynamicAccZ = data.z - processingGravity[2]

                val magnitude = sqrt(dynamicAccX * dynamicAccX + dynamicAccY * dynamicAccY + dynamicAccZ * dynamicAccZ)
                dynamicMagnitudes.add(magnitude)
            }

            if (dynamicMagnitudes.isEmpty()) {
                return null
            }

            var sumOfSquares = 0.0
            for (magnitude in dynamicMagnitudes) {
                sumOfSquares += magnitude * magnitude
            }
            return sqrt(sumOfSquares / dynamicMagnitudes.size).toFloat()
        }

        fun generateStandardRecommendations(rmsValue: Float): Pair<String, String> {
            return when {
                rmsValue < LOW_VIBRATION_THRESHOLD -> {
                    Pair("Rebound: Good. Consider minor refinement.", "Compression: Good. Consider minor refinement.")
                }
                rmsValue < HIGH_VIBRATION_THRESHOLD -> {
                    Pair("Rebound: Try reducing by 1 click.", "Compression: Try reducing by 1 click.")
                }
                else -> {
                    Pair("Rebound: Try reducing by 1-2 clicks.", "Compression: Try reducing by 1-2 clicks.")
                }
            }
        }

        fun determineRecommendationStrings(currentRms: Float?, previousRms: Float?): RecommendationStrings {
            var recReboundText = "Rebound: N/A (no data)"
            var recCompressionText = "Compression: N/A (no data)"
            var currentReboundInfo = "Current Rebound: N/A"
            var currentCompressionInfo = "Current Compression: N/A"

            if (currentRms != null && currentRms > 0f) {
                val (stdReboundBase, stdCompressionBase) = generateStandardRecommendations(currentRms)
                if (previousRms != null && previousRms > 0f) {
                    val deltaRms = currentRms - previousRms
                    when {
                        deltaRms < SIGNIFICANT_IMPROVEMENT_DELTA -> {
                            val improvedMsg = "Much better! System improved."
                            recReboundText = "Rebound: $improvedMsg ($stdReboundBase)"
                            recCompressionText = "Compression: $improvedMsg ($stdCompressionBase)"
                            currentReboundInfo = "Current Rebound: App noted: $improvedMsg Advised: $stdReboundBase"
                            currentCompressionInfo = "Current Compression: App noted: $improvedMsg Advised: $stdCompressionBase"
                        }
                        deltaRms > SIGNIFICANT_WORSENING_DELTA -> {
                            val worsenedMsg = "Worse! Revert last change?"
                            recReboundText = "Rebound: $worsenedMsg ($stdReboundBase)"
                            recCompressionText = "Compression: $worsenedMsg ($stdCompressionBase)"
                            currentReboundInfo = "Current Rebound: App noted: $worsenedMsg Advised: $stdReboundBase"
                            currentCompressionInfo = "Current Compression: App noted: $worsenedMsg Advised: $stdCompressionBase"
                        }
                        else -> { // Minor change or roughly the same
                            val similarMsg = "Similar to last run."
                            recReboundText = "Rebound: $similarMsg $stdReboundBase"
                            recCompressionText = "Compression: $similarMsg $stdCompressionBase"
                            currentReboundInfo = "Current Rebound: App noted: $similarMsg Advised: $stdReboundBase"
                            currentCompressionInfo = "Current Compression: App noted: $similarMsg Advised: $stdCompressionBase"
                        }
                    }
                } else {
                    // This is the first run with valid data, or previous data was invalid
                    recReboundText = stdReboundBase
                    recCompressionText = stdCompressionBase
                    currentReboundInfo = "Current Rebound: App advised: $stdReboundBase"
                    currentCompressionInfo = "Current Compression: App advised: $stdCompressionBase"
                }
            }
            return RecommendationStrings(recReboundText, recCompressionText, currentReboundInfo, currentCompressionInfo)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI components
        buttonStartStopTestRide = findViewById(R.id.buttonStartStopTestRide)
        textViewCharacteristicVibration = findViewById(R.id.textViewCharacteristicVibration)
        lineChartVibrations = findViewById(R.id.lineChartVibrations)
        textViewCurrentRebound = findViewById(R.id.textViewCurrentRebound)
        textViewCurrentCompression = findViewById(R.id.textViewCurrentCompression)
        textViewRecommendedRebound = findViewById(R.id.textViewRecommendedRebound)
        textViewRecommendedCompression = findViewById(R.id.textViewRecommendedCompression)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        buttonStartStopTestRide.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                startRecording()
            }
        }
        setupChart() // Initial chart setup
        // Initial call in onCreate:
        updateSuspensionRecommendations(null, previousRmsValue) // Use positional args or match names if defined in updateSuspensionRecommendations
    }

    private fun startRecording() {
        accelerometerReadings.clear()
        gravity[0] = 0f // Reset gravity estimate
        gravity[1] = 0f
        gravity[2] = 0f
        accelerometer?.also { acc ->
            sensorManager.registerListener(this, acc, SensorManager.SENSOR_DELAY_GAME)
        }
        isRecording = true
        buttonStartStopTestRide.text = "Stop Ride"
        textViewCharacteristicVibration.text = "Characteristic Vibration: Recording..."
        // Clear previous recommendations and current settings text
        textViewCurrentRebound.text = "Current Rebound: Recording..."
        textViewCurrentCompression.text = "Current Compression: Recording..."
        textViewRecommendedRebound.text = "Rebound: Recording..."
        textViewRecommendedCompression.text = "Compression: Recording..."
        lineChartVibrations.clear()
        lineChartVibrations.invalidate()
    }

    private fun stopRecording() {
        sensorManager.unregisterListener(this)
        isRecording = false
        buttonStartStopTestRide.text = "Start Test Ride"
        // processAndDisplayVibrationData() returns current RMS or null
        val currentRms = processAndDisplayVibrationData()
        updateSuspensionRecommendations(currentRms, previousRmsValue)
        if (currentRms != null) {
            previousRmsValue = currentRms // Store for next run
        }
    }

    private fun setupChart() {
        lineChartVibrations.description.text = "Vibration Magnitude"
        lineChartVibrations.setTouchEnabled(true)
        lineChartVibrations.isDragEnabled = true
        lineChartVibrations.setScaleEnabled(true)
        lineChartVibrations.setPinchZoom(true)
        lineChartVibrations.data = LineData() // Initialize with empty data
        lineChartVibrations.invalidate()
    }

    private fun processAndDisplayVibrationData(): Float? {
        if (accelerometerReadings.isEmpty()) {
            textViewCharacteristicVibration.text = "Characteristic Vibration: No data"
            lineChartVibrations.clear() // Clear chart if no data
            lineChartVibrations.invalidate()
            return null
        }

        // Call the static helper for RMS calculation
        val rms = Companion.calculateRmsForData(accelerometerReadings, this.alpha) // Use instance alpha for live data processing

        if (rms == null) {
            textViewCharacteristicVibration.text = "Characteristic Vibration: Not enough data"
            lineChartVibrations.clear()
            lineChartVibrations.invalidate()
            return null
        }

        textViewCharacteristicVibration.text = String.format("Characteristic Vibration: %.3f", rms)

        // Charting logic (remains the same, uses dynamicMagnitudes derived inside calculateRmsForData if needed,
        // but for now, we only need RMS for this function. Charting might need separate dynamic data access)
        // For simplicity, we'll chart the raw accelerometer data's magnitudes for now, or pass dynamicMagnitudes out of calculateRms
        // Let's chart the dynamic magnitudes. This requires calculateRmsForData to return them or be part of a broader data processing result.
        // For now, let's focus on RMS and defer detailed charting of dynamic magnitudes to a potential further refactor.
        // Simplified: If RMS is calculated, we assume some data was processable for charting.
        // We will re-calculate dynamic magnitudes here for charting to keep calculateRmsForData focused.
        
        val chartEntries = mutableListOf<Entry>()
        if (accelerometerReadings.isNotEmpty()) {
            val dynamicMagnitudesForChart = mutableListOf<Float>()
            val chartProcessingGravity = floatArrayOf(0f,0f,0f)
            chartProcessingGravity[0] = accelerometerReadings.first().x
            chartProcessingGravity[1] = accelerometerReadings.first().y
            chartProcessingGravity[2] = accelerometerReadings.first().z

            for(data in accelerometerReadings){
                chartProcessingGravity[0] = this.alpha * chartProcessingGravity[0] + (1 - this.alpha) * data.x
                chartProcessingGravity[1] = this.alpha * chartProcessingGravity[1] + (1 - this.alpha) * data.y
                chartProcessingGravity[2] = this.alpha * chartProcessingGravity[2] + (1 - this.alpha) * data.z
                val dx = data.x - chartProcessingGravity[0]
                val dy = data.y - chartProcessingGravity[1]
                val dz = data.z - chartProcessingGravity[2]
                dynamicMagnitudesForChart.add(sqrt(dx*dx + dy*dy + dz*dz))
            }
            dynamicMagnitudesForChart.forEachIndexed { index, magnitude ->
                chartEntries.add(Entry(index.toFloat(), magnitude))
            }
        }

        if (chartEntries.isNotEmpty()) {
            val lineDataSet = LineDataSet(chartEntries, "Vibration Magnitude")
            val lineData = LineData(lineDataSet)
            lineChartVibrations.data = lineData
        } else {
            lineChartVibrations.clear()
        }
        lineChartVibrations.invalidate() // Refresh chart
        return rms
    }

    // Changed parameter names to currentRms and previousRms for clarity at call site
    private fun updateSuspensionRecommendations(currentRms: Float?, previousRms: Float?) { 
        val recommendations = Companion.determineRecommendationStrings(currentRms, previousRms)

        textViewRecommendedRebound.text = recommendations.recRebound
        textViewRecommendedCompression.text = recommendations.recCompression
        textViewCurrentRebound.text = recommendations.currentReboundInfo
        textViewCurrentCompression.text = recommendations.currentCompressionInfo

        // Clear chart and characteristic value if current RMS is null (handled by determineRecommendationStrings logic for text, do UI here)
        if (currentRms == null) {
            textViewCharacteristicVibration.text = "Characteristic Vibration: N/A"
            lineChartVibrations.clear()
            lineChartVibrations.invalidate()
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        // Entire body is commented out from previous debugging step, which is fine for now.
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            // Log.d("MainActivity", "Sensor data: ${event.values[0]}, ${event.values[1]}, ${event.values[2]}") // Test log
            val timestamp = event.timestamp
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            accelerometerReadings.add(AccelerometerData(timestamp, x, y, z))

            // Update live gravity estimate during recording (optional, but done here for the EMA)
            // This gravity estimate is what's used if processing happened live.
            // For post-processing as done in processAndDisplayVibrationData,
            // it's often better to re-filter the whole dataset for consistency.
            // However, the current processAndDisplayVibrationData re-initializes and re-filters.
            gravity[0] = alpha * gravity[0] + (1 - alpha) * x
            gravity[1] = alpha * gravity[1] + (1 - alpha) * y
            gravity[2] = alpha * gravity[2] + (1 - alpha) * z
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Can be left empty for now
    }

    override fun onResume() {
        super.onResume()
        if (isRecording) {
            accelerometer?.also { acc ->
                sensorManager.registerListener(this, acc, SensorManager.SENSOR_DELAY_GAME)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (isRecording) {
            sensorManager.unregisterListener(this)
        }
    }
}
