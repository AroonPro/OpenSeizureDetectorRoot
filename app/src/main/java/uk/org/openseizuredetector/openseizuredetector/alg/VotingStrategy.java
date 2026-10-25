package uk.org.openseizuredetector.openseizuredetector.alg;

import java.util.List;
import uk.org.openseizuredetector.openseizuredetector.data.AlarmState;

/**
 * VotingStrategy - Determines the combined alarm state from multiple algorithms.
 * GC-Optimized: Processes pools with activeCount to eliminate subList/Iterator overhead.
 * Compliance: Unit Regtien Forensic Standard.
 */
public class VotingStrategy {
    public enum Strategy {
        ANY_ALARM,
        MAJORITY_VOTE,
        WEIGHTED_VOTE
    }

    private final Strategy mStrategy;

    public VotingStrategy(Strategy strategy) {
        this.mStrategy = strategy;
    }

    /**
     * GC-Optimized Vote: Direct pool access to avoid allocations.
     */
    public int vote(List<AlgorithmResult> pool, int activeCount) {
        if (activeCount == 0) return AlarmState.OK;
        if (activeCount == 1) return pool.get(0).alarmState;

        switch (mStrategy) {
            case MAJORITY_VOTE:
                int alarmCount = 0;
                for (int i = 0; i < activeCount; i++) {
                    if (pool.get(i).alarmState == AlarmState.ALARM) alarmCount++;
                }
                return (alarmCount > activeCount / 2) ? AlarmState.ALARM : AlarmState.OK;

            case WEIGHTED_VOTE:
                float totalWeight = 0;
                float alarmWeight = 0;
                for (int i = 0; i < activeCount; i++) {
                    AlgorithmResult res = pool.get(i);
                    totalWeight += res.weight;
                    if (res.alarmState == AlarmState.ALARM) alarmWeight += res.weight;
                }
                return (totalWeight > 0 && (alarmWeight / totalWeight > 0.5f)) ? AlarmState.ALARM : AlarmState.OK;

            case ANY_ALARM:
            default:
                for (int i = 0; i < activeCount; i++) {
                    if (pool.get(i).alarmState == AlarmState.ALARM) return AlarmState.ALARM;
                }
                return AlarmState.OK;
        }
    }

    public static Strategy fromString(String strategyStr) {
        if (strategyStr == null) return Strategy.ANY_ALARM;
        try {
            return Strategy.valueOf(strategyStr.toUpperCase());
        } catch (Exception e) {
            return Strategy.ANY_ALARM;
        }
    }
}
