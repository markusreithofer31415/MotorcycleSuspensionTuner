package com.example.motorcyclesuspensiontuner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.sqrt

// Using AccelerometerData from the main app for consistency in data structure
// import com.example.motorcyclesuspensiontuner.AccelerometerData 
// No, we should use a local test data class or map directly to avoid main source dependency issues in pure unit tests.

class MainLogicTests {

    // --- RMS Calculation Tests ---
    @Test
    fun testRmsCalculation_EmptyData() {
        // Use MainActivity's static method
        val rms = MainActivity.Companion.calculateRmsForData(emptyList())
        assertNull(rms)
    }

    @Test
    fun testRmsCalculation_ConstantData_ZeroDynamicAcc() {
        val readings = listOf(
            AccelerometerData(0L,0f, 0f, 9.8f),
            AccelerometerData(1L,0f, 0f, 9.8f),
            AccelerometerData(2L,0f, 0f, 9.8f),
            AccelerometerData(3L,0f, 0f, 9.8f),
            AccelerometerData(4L,0f, 0f, 9.8f)
        )
        // Use MainActivity's static method
        val rms = MainActivity.Companion.calculateRmsForData(readings)
        assertNotNull(rms)
        assertEquals(0.0f, rms!!, 0.1f) // Expecting RMS close to 0
    }

    @Test
    fun testRmsCalculation_SimpleVibration() {
        val readings = mutableListOf<AccelerometerData>()
        for (i in 0..10) { // Initial stabilization
            readings.add(AccelerometerData(i.toLong(),0f, 0f, 9.8f))
        }
        readings.add(AccelerometerData(11L,0f, 0f, 10.0f))
        readings.add(AccelerometerData(12L,0f, 0f, 9.6f))
        readings.add(AccelerometerData(13L,0f, 0f, 10.0f))
        readings.add(AccelerometerData(14L,0f, 0f, 9.6f))
        
        val rms = MainActivity.Companion.calculateRmsForData(readings)
        assertNotNull(rms)
        assert(rms!! > 0.05f) // Check for positive RMS, specific value depends on filter dynamics
    }

    // --- generateStandardRecommendations Tests ---
    @Test
    fun testGenerateStandardRecommendations_LowVibration() {
        // Use MainActivity's static method
        val (rebound, compression) = MainActivity.Companion.generateStandardRecommendations(0.2f)
        assertEquals("Rebound: Good. Consider minor refinement.", rebound)
        assertEquals("Compression: Good. Consider minor refinement.", compression)
    }

    @Test
    fun testGenerateStandardRecommendations_ModerateVibration() {
        val (rebound, compression) = MainActivity.Companion.generateStandardRecommendations(1.0f)
        assertEquals("Rebound: Try reducing by 1 click.", rebound)
        assertEquals("Compression: Try reducing by 1 click.", compression)
    }

    @Test
    fun testGenerateStandardRecommendations_HighVibration() {
        val (rebound, compression) = MainActivity.Companion.generateStandardRecommendations(2.0f)
        assertEquals("Rebound: Try reducing by 1-2 clicks.", rebound)
        assertEquals("Compression: Try reducing by 1-2 clicks.", compression)
    }

    // --- determineRecommendationStrings Tests (from MainActivity.Companion) ---
    @Test
    fun testDetermineRecommendations_NoCurrentRms() {
        // Use MainActivity's static method
        val output = MainActivity.Companion.determineRecommendationStrings(null, 0.5f)
        assertEquals("Rebound: N/A (no data)", output.recRebound)
        assertEquals("Compression: N/A (no data)", output.recCompression)
        assertEquals("Current Rebound: N/A", output.currentReboundInfo)
        assertEquals("Current Compression: N/A", output.currentCompressionInfo)
    }

    @Test
    fun testDetermineRecommendations_FirstRun_Low() {
        val currentRms = 0.3f
        val (stdRebound, stdComp) = MainActivity.Companion.generateStandardRecommendations(currentRms)
        val output = MainActivity.Companion.determineRecommendationStrings(currentRms, null)
        
        assertEquals(stdRebound, output.recRebound)
        assertEquals(stdComp, output.recCompression)
        assertEquals("Current Rebound: App advised: $stdRebound", output.currentReboundInfo)
        assertEquals("Current Compression: App advised: $stdComp", output.currentCompressionInfo)
    }
    
    @Test
    fun testDetermineRecommendations_FirstRun_Moderate() {
        val currentRms = 1.0f
        val (stdRebound, stdComp) = MainActivity.Companion.generateStandardRecommendations(currentRms)
        val output = MainActivity.Companion.determineRecommendationStrings(currentRms, null)

        assertEquals(stdRebound, output.recRebound)
        assertEquals(stdComp, output.recCompression)
        assertEquals("Current Rebound: App advised: $stdRebound", output.currentReboundInfo)
        assertEquals("Current Compression: App advised: $stdComp", output.currentCompressionInfo)
    }

    @Test
    fun testDetermineRecommendations_FirstRun_High() {
        val currentRms = 2.0f
        val (stdRebound, stdComp) = MainActivity.Companion.generateStandardRecommendations(currentRms)
        val output = MainActivity.Companion.determineRecommendationStrings(currentRms, null)
        
        assertEquals(stdRebound, output.recRebound)
        assertEquals(stdComp, output.recCompression)
        assertEquals("Current Rebound: App advised: $stdRebound", output.currentReboundInfo)
        assertEquals("Current Compression: App advised: $stdComp", output.currentCompressionInfo)
    }

    @Test
    fun testDetermineRecommendations_Improvement() {
        val currentRms = 0.3f
        val previousRms = 0.8f
        val (stdRebound, stdComp) = MainActivity.Companion.generateStandardRecommendations(currentRms)
        val improvedMsg = "Much better! System improved."
        val output = MainActivity.Companion.determineRecommendationStrings(currentRms, previousRms)
        
        assertEquals("Rebound: $improvedMsg ($stdRebound)", output.recRebound)
        assertEquals("Compression: $improvedMsg ($stdComp)", output.recCompression)
        assertEquals("Current Rebound: App noted: $improvedMsg Advised: $stdRebound", output.currentReboundInfo)
        assertEquals("Current Compression: App noted: $improvedMsg Advised: $stdComp", output.currentCompressionInfo)
    }

    @Test
    fun testDetermineRecommendations_Worsening() {
        val currentRms = 0.8f
        val previousRms = 0.3f
        val (stdRebound, stdComp) = MainActivity.Companion.generateStandardRecommendations(currentRms)
        val worsenedMsg = "Worse! Revert last change?"
        val output = MainActivity.Companion.determineRecommendationStrings(currentRms, previousRms)

        assertEquals("Rebound: $worsenedMsg ($stdRebound)", output.recRebound)
        assertEquals("Compression: $worsenedMsg ($stdComp)", output.recCompression)
        assertEquals("Current Rebound: App noted: $worsenedMsg Advised: $stdRebound", output.currentReboundInfo)
        assertEquals("Current Compression: App noted: $worsenedMsg Advised: $stdComp", output.currentCompressionInfo)
    }

    @Test
    fun testDetermineRecommendations_Similar() {
        val currentRms = 0.5f
        val previousRms = 0.52f 
        val (stdRebound, stdComp) = MainActivity.Companion.generateStandardRecommendations(currentRms)
        val similarMsg = "Similar to last run."
        val output = MainActivity.Companion.determineRecommendationStrings(currentRms, previousRms)

        assertEquals("Rebound: $similarMsg $stdRebound", output.recRebound)
        assertEquals("Compression: $similarMsg $stdComp", output.recCompression)
        assertEquals("Current Rebound: App noted: $similarMsg Advised: $stdRebound", output.currentReboundInfo)
        assertEquals("Current Compression: App noted: $similarMsg Advised: $stdComp", output.currentCompressionInfo)
    }
}
