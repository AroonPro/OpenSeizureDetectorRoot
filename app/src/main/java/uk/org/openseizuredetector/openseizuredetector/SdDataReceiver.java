package uk.org.openseizuredetector.openseizuredetector;

/**
 * Receiver interface for data and fault events.
 */
public interface SdDataReceiver {
    void onSdDataReceived(SdData sdData);
    void onSdDataFault(SdData sdData);
}
