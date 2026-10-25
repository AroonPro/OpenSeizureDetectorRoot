package uk.org.openseizuredetector.openseizuredetector.alg;

import uk.org.openseizuredetector.openseizuredetector.data.AlarmState;

/**
 * AlgorithmResult - Encapsulates the result from a single algorithm evaluation.
 * Optimized for GC: Non-final fields allow object recycling in high-frequency loops
 * to minimize Garbage Collection pressure and eliminate Stop-the-World pauses.
 *
 * Compliance: Unit Regtien Forensic Standard / Code Review Checklist.
 */
public class AlgorithmResult {
    public String algorithmName;
    public int alarmState;      // Use AlarmState constants
    public float confidence;    // 0.0 to 1.0 (probability)
    public float weight;        // Weight for weighted voting

    public AlgorithmResult(String algorithmName, int alarmState, float confidence, float weight) {
        update(algorithmName, alarmState, confidence, weight);
    }

    /**
     * Updates the existing object's state to allow recycling.
     * Call this in high-frequency analysis loops instead of 'new' to prevent memory pressure.
     */
    public void update(String algorithmName, int alarmState, float confidence, float weight) {
        this.algorithmName = algorithmName;
        this.alarmState = alarmState;
        this.confidence = confidence;
        this.weight = weight;
    }

    @Override
    public String toString() {
        return "AlgorithmResult{" +
                "name='" + algorithmName + '\'' +
                ", state=" + alarmState +
                ", confidence=" + confidence +
                ", weight=" + weight +
                '}';
    }
}
