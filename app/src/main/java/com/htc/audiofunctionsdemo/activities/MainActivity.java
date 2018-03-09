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
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import com.htc.audiofunctionsdemo.R;
import com.htc.audiofunctionsdemo.controllers.Controllable;
import com.htc.audiofunctionsdemo.controllers.PlaybackController;
import com.htc.audiofunctionsdemo.controllers.RecordController;
import com.htc.audiofunctionsdemo.controllers.VOIPController;
import com.htc.audiofunctionsdemo.utils.AudioSignalFrameLogger;
import com.htc.audiofunctionsdemo.utils.Constants;
import com.htc.audiofunctionsdemo.utils.DataView;
import com.htc.audiofunctionsdemo.utils.FFT;
import com.htc.audiofunctionsdemo.utils.RecorderIO;
import com.htc.audiofunctionsdemo.utils.WatchDog;

import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = Constants.packageTag("MainActivity");
    private static final String VERSION = "1.3.2";

    private Spinner mIntentSpinner;
    private Button mSendBtn;
    private String mCurrentIntentName;
    private TextView mStateView;
    private DataView mSignalView;
    private DataViewConfig mSignalViewConfig;
    private DataView mSpectrumView;
    private DataViewConfig mSpectrumViewConfig;

    private AudioSignalFrameLogger mSignalLogger;

    private boolean printProperties = false;

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
                        mSignalLogger.clear();
                        idx = intent.getIntExtra("idx", 0);
                        mSignalViewConfig.xmin = intent.getIntExtra("sig_xmin", -1);
                        mSignalViewConfig.xmax = intent.getIntExtra("sig_xmax", -1);
                        mSpectrumViewConfig.xmin = intent.getIntExtra("spt_xmin", -1);
                        mSpectrumViewConfig.xmax = intent.getIntExtra("spt_xmax", -1);
                        if (mRecordController != null)
                            mRecordController.startpcm(idx);
                        break;
                    case Constants.AudioIntentNames.INTENT_RECORD_STOP:
                        idx = intent.getIntExtra("idx", 0);
                        if (mRecordController != null)
                            mRecordController.stop(idx);
                        break;
                    case Constants.AudioIntentNames.INTENT_RECORD_DUMP_BUFFER:
                        String path = intent.getStringExtra("path");
                        mSignalLogger.dumpTo(path);
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
                    case Constants.AudioIntentNames.INTENT_KEEP_PRINTING_PROPERTIES:
                        printProperties = intent.getBooleanExtra("v", false);
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
                double[] signal;
                int samplingRate;
                if (bytesPerSample == 2) {
                    short[] signal_int16 = new short[data.length / 2];
                    ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(signal_int16);
                    signal = new double[signal_int16.length / numChannels];

                    for (int i = 0; i < signal.length; i++) {
                        double v = (double) signal_int16[i*numChannels] / Constants.AudioRecordConfig.NORMALIZATION_FACTOR;
                        signal[i] = v;
                    }
                    samplingRate = Constants.AudioRecordConfig.SAMPLING_RATE;
                } else {
                    int ratio = Constants.AudioRecordConfig.SAMPLING_RATE_HD / Constants.AudioRecordConfig.SAMPLING_RATE;
                    int[] signal_int32 = new int[data.length / 4];
                    ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(signal_int32);
                    signal = new double[signal_int32.length / (numChannels*ratio)];

                    for (int i = 0; i < signal.length; i++) {
                        double v = (double) signal_int32[i*numChannels*ratio] / Constants.AudioRecordConfig.NORMALIZATION_FACTOR_HD;
                        signal[i] = v;
                    }
                    samplingRate = Constants.AudioRecordConfig.SAMPLING_RATE_HD / ratio;
                }
                double[] spectrum = FFT.transformAbs(signal);
                updateDataView(signal, spectrum, samplingRate);
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

        mSignalLogger = new AudioSignalFrameLogger();

        mWatchDog.addMonitor("Class.PlaybackController", mPlaybackController.thread);
        mWatchDog.addMonitor("Class.RecordController", mRecordController.thread);
        mWatchDog.addMonitor("Class.VOIPController", mVOIPController.thread);
    }

    private void initUI() {
        ((TextView) findViewById(R.id.app_info)).setText("MS Audio Functions Demo (ver." + VERSION + "_" + FFT.getVersion() + ") ");
        mIntentSpinner = (Spinner) findViewById(R.id.intent_list);
        mSendBtn = (Button) findViewById(R.id.send_intent_btn);
        mStateView = (TextView) findViewById(R.id.current_state);
        mSignalView = (DataView) findViewById(R.id.signal_view);
        mSpectrumView = (DataView) findViewById(R.id.spectrum_view);
        mRecordConsole = (TextView) findViewById(R.id.record_console);
        mSignalViewConfig = new DataViewConfig();
        mSpectrumViewConfig = new DataViewConfig();
        mSignalView.setGridSlotsY(4);
        mSignalView.setGridSlotsX(10);
        mSpectrumView.setGridSlotsX(10);

        mSendBtn.setText("Send");
        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(this,
                android.R.layout.simple_spinner_item, Constants.AudioIntentNames.INTENT_NAMES);
        mIntentSpinner.setAdapter(adapter);
        mIntentSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mCurrentIntentName = Constants.AudioIntentNames.INTENT_NAMES[position];
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                mCurrentIntentName = null;
            }
        });
        mSendBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mCurrentIntentName == null)
                    return;

                Intent intent = new Intent();

                switch (mCurrentIntentName) {
                    case Constants.AudioIntentNames.INTENT_PLAYBACK_START_NONOFFLOAD:
                        intent.putExtra("file", "440Hz_wav.wav");
                        break;
                    case Constants.AudioIntentNames.INTENT_PLAYBACK_START_OFFLOAD:
                        intent.putExtra("file", "440Hz_mp3.mp3");
                        break;
                }

                intent.setAction(mCurrentIntentName);
                sendBroadcast(intent);
            }
        });
    }

    private void updateTextView(final int id, String text) {
        ((TextView) findViewById(id)).setText(text);
    }

    private void updateDataView(double[] signal, double[] spectrum, int samplingRate) {
        mSignalLogger.push("signal", samplingRate, signal);
        mSignalLogger.push("spectrum", spectrum);

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

        double lastDetectedFreq;
        try {
            lastDetectedFreq = Double.valueOf(System.getProperty(Constants.AudioRecordConfig.DETECTED_TONE_FREQ_PROP));
        } catch (Exception e) {
            lastDetectedFreq = -1;
        }
        if (printProperties && lastDetectedFreq != detectedFreq) {
            Log.i(TAG + "::properties", detectedFreq + "," + detectedAmp);
        }

        try {
            PrintWriter pw = new PrintWriter(Constants.AudioRecordConfig.PROP_FILE_PATH);
            pw.write(detectedFreq + "," + detectedAmp + "\n");
            pw.close();
            if (lastDetectedFreq != detectedFreq) {
                Log.i(TAG, "the detected frequency has been changed to " + detectedFreq);
            }
        } catch (Exception e) {
            Log.e(TAG, "write the file \"" + Constants.AudioRecordConfig.PROP_FILE_PATH + "\" failed.");
            e.printStackTrace();
        }

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
