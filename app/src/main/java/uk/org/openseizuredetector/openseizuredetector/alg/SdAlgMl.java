package uk.org.openseizuredetector.openseizuredetector.alg;

import android.content.Context;
import android.content.SharedPreferences;
import uk.org.openseizuredetector.openseizuredetector.data.logging.Log;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tflite.java.TfLite;
import org.pytorch.executorch.EValue;
import org.pytorch.executorch.Module;
import org.pytorch.executorch.Tensor;
import org.tensorflow.lite.InterpreterApi;
import java.util.ArrayList;
import java.util.List;
import androidx.preference.PreferenceManager;
import uk.org.openseizuredetector.openseizuredetector.data.SdData;
import uk.org.openseizuredetector.openseizuredetector.utils.CircBuf;
import uk.org.openseizuredetector.openseizuredetector.data.AlarmState;

/**
 * SdAlgMl - Multi-model ML algorithm.
 * GC-Optimized: Zero-allocation analysis loop using direct pool population and pre-allocated buffers.
 * Performance: Unit Regtien 'King' Standard - SharedPreferences cached.
 */
public class SdAlgMl extends SdAlgBase {
    private final static String TAG = "SdAlgMl";
    private MlModelManager mMm;
    private double mAccelStdThresholdPct = 5.0;
    private float mSeizureProbThreshold = 0.5f;

    private static class ModelInstance {
        String label;
        String name;
        String framework;
        InterpreterApi tfliteInterpreter;
        Module pytorchModule;
        int inputSize;
        float weight = 1.0f;
        boolean isEnabled = true;
        CircBuf inputBuffer;
        
        // GC-Optimized pre-allocated buffers
        float[][][] tfliteInput;
        float[][] tfliteOutput;
        float[] pytorchInputBuffer;
        long[] pytorchInputDims; // PRE-ALLOCATED

        public void initBuffers(int size) {
            this.inputSize = size;
            tfliteInput = new float[1][size][1];
            tfliteOutput = new float[1][2];
            pytorchInputBuffer = new float[size];
            pytorchInputDims = new long[]{1, 1, size};
            inputBuffer = new CircBuf(size, Double.NaN);
        }

        public void close() {
            if (tfliteInterpreter != null) { tfliteInterpreter.close(); tfliteInterpreter = null; }
            if (pytorchModule != null) { pytorchModule.destroy(); pytorchModule = null; }
        }
    }

    private final List<ModelInstance> mModels = new ArrayList<>();

    public SdAlgMl(Context context) {
        super(context);
        mMm = new MlModelManager(mContext);
        // Cached prefs initialization
        updatePrefs();
        TfLite.initialize(mContext).addOnSuccessListener(a -> loadAllActiveModels());
    }

    private void loadAllActiveModels() {
        List<MlModelManager.ModelLoadResult> results = mMm.loadAllActiveModels();
        for (int i = 0; i < results.size(); i++) {
            MlModelManager.ModelLoadResult res = results.get(i);
            try {
                ModelInstance model = new ModelInstance();
                model.label = "ML" + (i + 1);
                model.name = res.name;
                model.framework = res.framework;
                model.initBuffers(res.inputSize);

                if ("tflite".equalsIgnoreCase(res.framework) && res.tfliteBuffer != null) {
                    model.tfliteInterpreter = InterpreterApi.create(res.tfliteBuffer,
                            new InterpreterApi.Options().setRuntime(
                                    InterpreterApi.Options.TfLiteRuntime.FROM_SYSTEM_ONLY));
                    mModels.add(model);
                } else if (res.file != null && res.file.exists()) {
                    model.pytorchModule = Module.load(res.file.getAbsolutePath());
                    mModels.add(model);
                }
            } catch (Exception e) { Log.e(TAG, "Model Load Error: " + res.name); }
        }
    }

    /**
     * populateResultsFromPool - King-Level Optimization.
     * Directly updates the SeizureDetector's pool to prevent heap churn.
     * @return The updated resultCount.
     */
    public int populateResultsFromPool(SdData sdData, List<AlgorithmResult> pool, int startIdx) {
        int currentIdx = startIdx;

        for (int i = 0; i < mModels.size(); i++) {
            ModelInstance model = mModels.get(i);
            if (!model.isEnabled) continue;
            
            float pSeizure = evaluateModel(model, sdData);
            int state = (pSeizure > mSeizureProbThreshold) ? AlarmState.ALARM : AlarmState.OK;
            
            if (currentIdx < pool.size()) {
                pool.get(currentIdx).update(model.label, state, pSeizure, model.weight);
                currentIdx++;
            }
        }
        return currentIdx;
    }

    private float evaluateModel(ModelInstance model, SdData sdData) {
        // Direct buffer injection
        for (int i = 0; i < sdData.rawData.length; i++) {
            model.inputBuffer.add(sdData.rawData[i] / 1000.0);
        }
        if (model.inputBuffer.getNumVals() < model.inputSize) return 0.0f;

        double stdDev = calcRawDataStd(sdData);
        if (stdDev < mAccelStdThresholdPct) return 0.0f;

        if ("pytorch".equalsIgnoreCase(model.framework) && model.pytorchModule != null) {
            double[] vals = model.inputBuffer.getVals();
            for(int i=0; i<model.inputSize; i++) model.pytorchInputBuffer[i] = (float)vals[i];
            
            Tensor inputTensor = Tensor.fromBlob(model.pytorchInputBuffer, model.pytorchInputDims);
            EValue[] outputs = model.pytorchModule.forward(EValue.from(inputTensor));
            if (outputs.length > 0) {
                float[] scores = outputs[0].toTensor().getDataAsFloatArray();
                return (scores.length >= 2) ? softmax(scores) : 0.0f;
            }
        } else if (model.tfliteInterpreter != null) {
            double[] vals = model.inputBuffer.getVals();
            for(int i=0; i<model.inputSize; i++) model.tfliteInput[0][i][0] = (float)vals[i];
            model.tfliteInterpreter.run(model.tfliteInput, model.tfliteOutput);
            return model.tfliteOutput[0][1];
        }
        return 0.0f;
    }

    private float softmax(float[] scores) {
        float max = Math.max(scores[0], scores[1]);
        double e0 = Math.exp(scores[0] - max);
        double e1 = Math.exp(scores[1] - max);
        return (float) (e1 / (e0 + e1));
    }

    private double calcRawDataStd(SdData sdData) {
        int len = Math.min(sdData.rawData.length, 125);
        double sum = 0.0;
        for (int j = 0; j < len; j++) sum += sdData.rawData[j];
        double mean = sum / len;
        double ss = 0.0;
        for (int j = 0; j < len; j++) ss += Math.pow(sdData.rawData[j] - mean, 2);
        return 100.0 * Math.sqrt(ss / len) / mean;
    }

    @Override
    public int processSdData(SdData sdData) { return AlarmState.OK; }

    @Override
    public String getAlarmCause() { return "ML"; }

    @Override
    public void close() {
        super.close();
        for (ModelInstance m : mModels) m.close();
        mModels.clear();
    }

    public void updatePrefs() {
        try {
            mAccelStdThresholdPct = Double.parseDouble(mSP.getString(MlModelManager.PREF_ML_ACCEL_STD_THRESHOLD_PCT, "5.0"));
            mSeizureProbThreshold = Float.parseFloat(mSP.getString(MlModelManager.PREF_ML_SEIZURE_PROB_THRESHOLD_PCT, "50.0")) / 100.0f;
        } catch (Exception e) {
            mAccelStdThresholdPct = 5.0;
            mSeizureProbThreshold = 0.5f;
        }
    }
}
