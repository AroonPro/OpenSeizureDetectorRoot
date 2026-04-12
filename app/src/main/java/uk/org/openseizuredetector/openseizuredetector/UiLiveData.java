package uk.org.openseizuredetector.openseizuredetector;

import androidx.lifecycle.LiveData;

/**
 * UiLiveData - Een aangepaste LiveData klasse die de UI een seintje geeft
 * zodra er nieuwe sensordata of statuswijzigingen zijn.
 */
public class UiLiveData extends LiveData<Long> {
    private static final String TAG = "UiLiveData";

    public UiLiveData() {
        // We initialiseren met de huidige tijd als dummy waarde
        setValue(System.currentTimeMillis());
    }

    /**
     * Deze methode wordt aangeroepen vanuit SdServiceConnection of SdServer.
     * Het dwingt de observers (de UI) om de data opnieuw op te halen.
     */
    public void signalChangedData() {
        // Door de waarde te veranderen (bijv. naar de huidige tijd),
        // worden alle actieve observers getriggerd om hun UI te verversen.
        postValue(System.currentTimeMillis());
    }
}