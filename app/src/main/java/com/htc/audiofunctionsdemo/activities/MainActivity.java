package com.htc.audiofunctionsdemo.activities;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.htc.audiofunctionsdemo.R;
import com.htc.audiofunctionsdemo.controllers.Controllable;
import com.htc.audiofunctionsdemo.controllers.PlaybackController;
import com.htc.audiofunctionsdemo.controllers.RecordController;
import com.htc.audiofunctionsdemo.controllers.VOIPController;
import com.htc.audiofunctionsdemo.utils.Constants;
import com.htc.audiofunctionsdemo.utils.DataView;
import com.htc.audiofunctionsdemo.utils.FFT;
import com.htc.audiofunctionsdemo.utils.RecorderIO;
import com.htc.audiofunctionsdemo.utils.WatchDog;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "AudioFunctionsDemo";
    private static final String VERSION = "1.0.0";

    private TextView mStateView;
    private DataView mSignalView;
    private DataViewConfig mSignalViewConfig;
    private DataView mSpectrumView;
    private DataViewConfig mSpectrumViewConfig;

    private class DataViewConfig {
        int xmin = -1;
        int xmax = -1;
        boolean needRefreshed = true;
    }

    private TextView mRecordConsole;

    private WatchDog mWatchDog;
    private BroadcastReceiver mBroadcastReceiver;
    private AudioManager mAudioManager;
    private PlaybackController mPlaybackController;
    private RecordController mRecordController;
    private VOIPController mVOIPController;
    private MainHandler mHandler;

    private ArrayList<WeakReference<Controllable>> mControllers;

    private static String[] PERMISSIONS_REQUIRED = {
            Manifest.permission.INTERNET,
            Manifest.permission.MODIFY_AUDIO_SETTINGS,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initPermissionCheck();
        initControllers();
        initUI();
    }

    private void initPermissionCheck() {
        for (String permission : PERMISSIONS_REQUIRED) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, PERMISSIONS_REQUIRED, 1);
                break;
            }
        }
    }

    private void initControllers() {
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mWatchDog = WatchDog.getInstance(MainActivity.this);
        mControllers = new ArrayList<>(10);

        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int idx;
                String fileName;
                String action = intent.getAction();
                Log.d(TAG, "intent:" + action);
                if (action == null) {
                    return;
                }
                switch (action) {
                    case Constants.AudioIntentNames.INTENT_PLAYBACK_START_NONOFFLOAD:
                        idx = intent.getIntExtra("idx", 0);
                        fileName = intent.getStringExtra("file");
                        if (mPlaybackController != null)
                            mPlaybackController.start_name(idx, PlaybackController.MODE_NONOFFLOAD, fileName);
                        break;
                    case Constants.AudioIntentNames.INTENT_PLAYBACK_START_OFFLOAD:
                        idx = intent.getIntExtra("idx", 0);
                        fileName = intent.getStringExtra("file");
                        if (mPlaybackController != null)
                            mPlaybackController.start_name(idx, PlaybackController.MODE_OFFLOAD, fileName);
                        break;
                    case Constants.AudioIntentNames.INTENT_PLAYBACK_SEEK:
                        idx = intent.getIntExtra("idx", 0);
                        if (mPlaybackController != null)
                            mPlaybackController.seek(idx);
                        break;
                    case Constants.AudioIntentNames.INTENT_PLAYBACK_PAUSE_RESUME:
                        idx = intent.getIntExtra("idx", 0);
                        if (mPlaybackController != null)
                            mPlaybackController.pause_resume(idx);
                        break;
                    case Constants.AudioIntentNames.INTENT_PLAYBACK_STOP:
                        idx = intent.getIntExtra("idx", 0);
                        if (mPlaybackController != null)
                            mPlaybackController.stop(idx);
                        break;

                    case Constants.AudioIntentNames.INTENT_RECORD_START:
                        idx = intent.getIntExtra("idx", 0);
                        mSignalViewConfig.xmin = intent.getIntExtra("sig_xmin", -1);
                        mSignalViewConfig.xmax = intent.getIntExtra("sig_xmax", -1);
                        mSpectrumViewConfig.xmin = intent.getIntExtra("spt_xmin", -1);
                        mSpectrumViewConfig.xmax = intent.getIntExtra("spt_xmax", -1);
                        if (mRecordController != null)
                            mRecordController.startwav(idx);
                        break;
                    case Constants.AudioIntentNames.INTENT_RECORD_START_24BIT:
                        break;
                    case Constants.AudioIntentNames.INTENT_RECORD_STOP:
                        idx = intent.getIntExtra("idx", 0);
                        if (mRecordController != null)
                            mRecordController.stop(idx);
                        break;

                    case Constants.AudioIntentNames.INTENT_VOIP_START:
                        mSignalViewConfig.xmin = intent.getIntExtra("sig_xmin", -1);
                        mSignalViewConfig.xmax = intent.getIntExtra("sig_xmax", -1);
                        mSpectrumViewConfig.xmin = intent.getIntExtra("spt_xmin", -1);
                        mSpectrumViewConfig.xmax = intent.getIntExtra("spt_xmax", -1);
                        if (mVOIPController != null)
                            mVOIPController.start();
                        break;
                    case Constants.AudioIntentNames.INTENT_VOIP_MUTE_OUTPUT:
                        idx = intent.getIntExtra("idx", 0);
                        if (mVOIPController != null)
                            mVOIPController.muteRx(idx);
                        break;
                    case Constants.AudioIntentNames.INTENT_VOIP_SWITCH_SPKR:
                        if (mVOIPController != null) {
                            boolean useSpeaker = intent.getBooleanExtra("use", false);
                            mVOIPController.switchToSpeaker(useSpeaker);
                        }
                        break;
                    case Constants.AudioIntentNames.INTENT_VOIP_STOP:
                        if (mVOIPController != null)
                            mVOIPController.stop();
                        break;

                    case Constants.AudioIntentNames.INTENT_LOG_PRINT:
                        String severity = intent.getStringExtra("sv");
                        if (severity == null) severity = "d";
                        String tag = intent.getStringExtra("tag");
                        if (tag == null) tag = "";
                        else tag = "::" + tag;
                        tag = TAG + tag;
                        String logText = intent.getStringExtra("log");
                        if (logText != null) {
                            switch (severity) {
                                case "i":
                                    Log.i(tag, logText);
                                    break;
                                case "e":
                                    Log.e(tag, logText);
                                    break;
                                case "w":
                                    Log.w(tag, logText);
                                    break;
                                case "d":
                                    Log.d(tag, logText);
                                    break;
                            }
                        }
                        break;
                    case Constants.AudioIntentNames.INTENT_PRINT_PROPERTIES:
                        String logtext = "";
                        logtext += System.getProperty(Constants.AudioRecordConfig.DETECTED_TONE_FREQ_PROP) + ",";
                        logtext += System.getProperty(Constants.AudioRecordConfig.DETECTED_TONE_AMP_PROP);
                        Log.i(TAG + "::properties", logtext);
                        break;
                    case Constants.AudioIntentNames.INTENT_DATA_VIEW_SETTINGS:
                        boolean refresh = intent.getBooleanExtra("refresh", true);
                        mSignalViewConfig.needRefreshed = refresh;
                        mSpectrumViewConfig.needRefreshed = refresh;
                        break;
                }
            }
        };

        // registering our receiver
        IntentFilter intentFilter = new IntentFilter("android.intent.action.MAIN");
        this.registerReceiver(mBroadcastReceiver, intentFilter);
        for (String intentName : Constants.AudioIntentNames.INTENT_NAMES) {
            intentFilter = new IntentFilter(intentName);
            this.registerReceiver(mBroadcastReceiver, intentFilter);
        }

        RecorderIO.RecorderIOListener listener = new RecorderIO.RecorderIOListener() {
            @Override
            public void onDataRead(byte[] data, int bytesPerSample, int numChannels) {
                short[] signal_int16 = new short[data.length/2];
                ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(signal_int16);
                double[] signal = new double[signal_int16.length];
                for (int i = 0; i < signal.length; i++) {
                    double v = (double) signal_int16[i] / Constants.AudioRecordConfig.NORMALIZATION_FACTOR;
                    signal[i] = v;
                }
                double[] spectrum = FFT.transformAbs(signal);
                updateDataView(signal, spectrum);
            }
        };

        mHandler = new MainHandler(this);
        mPlaybackController = new PlaybackController(mAudioManager, mHandler);
        mControllers.add(new WeakReference<Controllable>(mPlaybackController));
        mRecordController = new RecordController(mAudioManager, mHandler);
        mControllers.add(new WeakReference<Controllable>(mRecordController));
        mRecordController.setRecorderIOListener(listener);
        mVOIPController = new VOIPController(mAudioManager, mHandler);
        mVOIPController.setRecorderIOListener(listener);
        mControllers.add(new WeakReference<Controllable>(mVOIPController));

        mWatchDog.addMonitor("Class.PlaybackController", mPlaybackController.thread);
        mWatchDog.addMonitor("Class.RecordController", mRecordController.thread);
        mWatchDog.addMonitor("Class.VOIPController", mVOIPController.thread);
    }

    private void initUI() {
        ((TextView) findViewById(R.id.app_info)).setText("MS Audio Functions Demo (ver." + VERSION + "_" + FFT.getVersion() + ") ");
        mStateView = (TextView) findViewById(R.id.current_state);
        mSignalView = (DataView) findViewById(R.id.signal_view);
        mSpectrumView = (DataView) findViewById(R.id.spectrum_view);
        mRecordConsole = (TextView) findViewById(R.id.record_console);
        mSignalViewConfig = new DataViewConfig();
        mSpectrumViewConfig = new DataViewConfig();
        mSignalView.setGridSlotsY(4);
        mSignalView.setGridSlotsX(10);
        mSpectrumView.setGridSlotsX(10);
    }

    private void updateTextView(final int id, String text) {
        ((TextView) findViewById(id)).setText(text);
    }

    private void updateDataView(double[] signal, double[] spectrum) {
        int samplingRate = Constants.AudioRecordConfig.SAMPLING_RATE;
        if (mSignalViewConfig.xmin < 0) mSignalViewConfig.xmin = 0;
        if (mSignalViewConfig.xmax < 0) mSignalViewConfig.xmax = (int) Math.round(1000.0 * signal.length / samplingRate);
        if (mSpectrumViewConfig.xmin < 0) mSpectrumViewConfig.xmin = 0;
        if (mSpectrumViewConfig.xmax < 0) mSpectrumViewConfig.xmax = (int) Math.round(1.0 * samplingRate / 2);

        ArrayList<Double> signalToPlot, spectrumToPlot;
        int signalIdxMin = (int) Math.round((double) mSignalViewConfig.xmin / 1000.0 * samplingRate);
        int signalIdxMax = (int) Math.round((double) mSignalViewConfig.xmax / 1000.0 * samplingRate);
        int spectrumIdxMin = (int) Math.round(mSpectrumViewConfig.xmin / ((double) samplingRate / spectrum.length));
        int spectrumIdxMax = (int) Math.round(mSpectrumViewConfig.xmax / ((double) samplingRate / spectrum.length));
        signalToPlot = new ArrayList<>(signalIdxMax-signalIdxMin+1);
        spectrumToPlot = new ArrayList<>(spectrumIdxMax-spectrumIdxMin+1);
        int maxIdx = spectrumIdxMin-1;
        double maxValue = -1;

        for (int i = signalIdxMin; i <= signalIdxMax; i++) {
            double v = (i < signal.length) ? signal[i] : 0;
            signalToPlot.add(v);
        }
        for (int i = spectrumIdxMin; i <= spectrumIdxMax; i++) {
            double v = (i < spectrum.length) ? spectrum[i] : 0;
            spectrumToPlot.add(v/5);
            if (v > maxValue || maxIdx < spectrumIdxMin) {
                maxIdx = i;
                maxValue = v;
            }
        }

        if (mSignalViewConfig.needRefreshed)
            mSignalView.plot(signalToPlot);
        if (mSpectrumViewConfig.needRefreshed)
            mSpectrumView.plot(spectrumToPlot);

        double detectedFreq = (double) maxIdx * samplingRate / spectrum.length;
        detectedFreq = Math.round(detectedFreq * 100) / 100.0;
        double detectedAmp = 20*Math.log10(maxValue);
        detectedAmp = Math.round(detectedAmp * 100) / 100.0;
        System.setProperty(Constants.AudioRecordConfig.DETECTED_TONE_FREQ_PROP, "" + detectedFreq);
        System.setProperty(Constants.AudioRecordConfig.DETECTED_TONE_AMP_PROP, "" + detectedAmp);

        if (mSignalViewConfig.needRefreshed && mSpectrumViewConfig.needRefreshed) {
            Message msg = mHandler.obtainMessage();
            msg.what = R.id.record_console;
            msg.obj = "Signal show        [" + mSignalViewConfig.xmin + "~" + mSignalViewConfig.xmax + " ms]";
            msg.obj += "\n";
            msg.obj += "Spectrum show [" + mSpectrumViewConfig.xmin + "~" + mSpectrumViewConfig.xmax + " Hz]";
            msg.obj += "\n";
            msg.obj += "Detected Tone                   : " + detectedFreq + " Hz";
            msg.obj += "\n";
            msg.obj += "Corresponded Amplitude: " + detectedAmp + " dB";
            msg.sendToTarget();
        }
    }

    private static class MainHandler extends Handler {
        private WeakReference<MainActivity> actRef;

        MainHandler(MainActivity act) {
            actRef = new WeakReference<>(act);
        }

        @Override
        public void handleMessage(Message msg) {
            if (actRef.get() != null) {
                String text = (String) msg.obj;
                int id = msg.what;
                actRef.get().updateTextView(id, text);
            }
            super.handleMessage(msg);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.unregisterReceiver(mBroadcastReceiver);
        for (WeakReference<Controllable> controller : mControllers) {
            if (controller.get() != null) {
                controller.get().destroy();
            }
        }
    }
}
