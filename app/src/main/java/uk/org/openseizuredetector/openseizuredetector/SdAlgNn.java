package uk.org.openseizuredetector.openseizuredetector;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;

import org.tensorflow.lite.InterpreterApi;

import java.io.FileInputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * SdAlgNn - Unit Regtien Optimized.
 * High-speed NN inference with pre-allocated buffers and memory-mapped model.
 */
public class SdAlgNn {
    private static final String TAG = "SdAlgNn";
    private static final String MODEL_PATH = "osd_model.tflite";
    
    private InterpreterApi mInterpreter;
    
    // Pre-allocated hot path buffers to prevent GC pressure
    private final float[][] mInputBuffer = new float[1][250];
    private final float[][] mOutputBuffer = new float[1][1];

    public SdAlgNn(Context context) {
        try {
            mInterpreter = InterpreterApi.create(loadModelFile(context), new InterpreterApi.Options());
        } catch (Exception e) {
            Log.e(TAG, "Model Load Fail: " + e.getMessage());
        }
    }

    private MappedByteBuffer loadModelFile(Context context) throws Exception {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(MODEL_PATH);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    /**
     * Run inference with zero redundant allocation.
     */
    public float analyze(double[] rawMagnitudeData) {
        if (mInterpreter == null || rawMagnitudeData == null || rawMagnitudeData.length < 250) return 0;

        // Zero-copy input alignment
        for (int i = 0; i < 250; i++) {
            mInputBuffer[0][i] = (float) rawMagnitudeData[i];
        }

        mInterpreter.run(mInputBuffer, mOutputBuffer);
        return mOutputBuffer[0][0];
    }

    public void close() {
        if (mInterpreter != null) {
            mInterpreter.close();
            mInterpreter = null;
        }
    }
}
