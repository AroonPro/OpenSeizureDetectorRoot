package uk.org.openseizuredetector.openseizuredetector.datasource;

import uk.org.openseizuredetector.openseizuredetector.data.SdData;

public interface SdDataReceiver {
    public void onSdDataReceived(SdData sdData);

    public void onSdDataFault(SdData sdData);
}
