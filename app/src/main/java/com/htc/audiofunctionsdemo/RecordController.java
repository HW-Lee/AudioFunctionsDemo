package com.htc.audiofunctionsdemo;

import android.media.AudioManager;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.File;
import java.lang.ref.WeakReference;

public class RecordController implements Controllable {

    enum FORMAT {
        FORMAT_16BIT, FORMAT_24BIT, FORMAT_WAV
    }

    private class RecorderContainer {
        //MediaRecorder
        MediaRecorder recorder;

        //AudioRecorder
        RecorderIO recorderio;

        FORMAT format;

        Exception e = null;
        String errorMsg = "";

        RecorderContainer(FORMAT f) {
            format = f;

            if ((format == FORMAT.FORMAT_16BIT) || (format == FORMAT.FORMAT_24BIT))
                recorder = new MediaRecorder();
            else {
                recorderio = new RecorderIO(Constants.AudioRecordConfig.CIRCULAR_BUFFER_SIZE_MILLIS, Constants.AudioRecordConfig.BUFFER_SIZE_MILLIS);
            }
        }

        boolean isUsingMediaRecorder() {
            return (format == FORMAT.FORMAT_16BIT || format == FORMAT.FORMAT_24BIT);
        }

        public void setException(Exception e) {
            this.e = e;
        }

        public void setErrorMsg(String msg) {
            this.errorMsg = msg;
        }
    }

    private static class RecorderControllerThreadHandler extends Handler {
        private WeakReference<RecordControllerThread> mThreadRef;

        RecorderControllerThreadHandler(RecordControllerThread th) {
            mThreadRef = new WeakReference<>(th);
        }

        @Override
        public void handleMessage(Message msg) {
            RecordControllerThread th = mThreadRef.get();
            if (th == null) {
                super.handleMessage(msg);
                return;
            }
            if(msg.what == 1) {
                // means recorderIO has error
                RecorderContainer r = null;
                for (int i=0; i< th.MAX_RECORDER; i++)
                    if (!th.mMediaRecorderContainer[i].isUsingMediaRecorder())
                        r = th.mMediaRecorderContainer[i];
                if (r != null) {
                    if (r.recorderio.excep != null)
                        r.setException(r.recorderio.excep);
                    if (r.recorderio.errorMsg != null)
                        r.setErrorMsg(r.recorderio.errorMsg);
                }
            }
            super.handleMessage(msg);
        }

    }

    public class RecordControllerThread extends Thread implements WatchDog.Monitor {
        private final static String TAG = "RecordControllerThread";

        int MAX_RECORDER = 3;
        private RecorderContainer[] mMediaRecorderContainer = null;

        private AudioManager mAudioManager;
        final private RecordController mParent;
        final private RecordControllerThread wd_lock;
        private boolean exitPending;
        private int idx, cmd;

        Handler mRecordIOErrorHandle;

        RecordControllerThread(AudioManager m, RecordController parnet){
            mAudioManager = m;
            mParent = parnet;
            wd_lock = this;
            exitPending = false;

            mMediaRecorderContainer = new RecorderContainer[MAX_RECORDER];
            for(int i=0; i<MAX_RECORDER; i++)
                mMediaRecorderContainer[i] = null;

            mRecordIOErrorHandle = new RecorderControllerThreadHandler(this);
        }

        private void _start(int idx) {
            if (idx < 0 || idx >= MAX_RECORDER) {
                Log.d(TAG, "record start error: invalid index for recorder " + idx);
                return;
            }
            if (mMediaRecorderContainer[idx] == null) {
                Log.d(TAG, "record " + idx + " start");
                File SDCardpath = Environment.getExternalStorageDirectory();
                String filename = "record" + idx + ".aac";
                File recordFile = new File(SDCardpath.getAbsolutePath() + "/Music/" + filename);
                recordFile.delete();

                synchronized(this.wd_lock) {
                    mAudioManager.setParameters("VOICE_RECORDING_MODE=ON");
                    mMediaRecorderContainer[idx] = new RecorderContainer(RecordController.FORMAT.FORMAT_16BIT);

                    try {
                        mMediaRecorderContainer[idx].recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                        mMediaRecorderContainer[idx].recorder.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS); //****
                        mMediaRecorderContainer[idx].recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                        mMediaRecorderContainer[idx].recorder.setAudioSamplingRate(8000);
                        mAudioManager.setParameters("RECORD_FORMAT=AAC");
                        mMediaRecorderContainer[idx].recorder.setOutputFile(recordFile.getAbsolutePath());
                        mMediaRecorderContainer[idx].recorder.prepare();
                        mMediaRecorderContainer[idx].recorder.start();
                    } catch (Exception e) {
                        Log.d(TAG, "record error:" + e);
                        e.printStackTrace();
                        mMediaRecorderContainer[idx].setException(e);
                    }
                }
            } else
                Log.d(TAG, "record start idx " + idx + " is using");
        }

        private void _startwav(int idx) {
            String path;
            if (!mParent.ssrFileName.equals(""))
                path = ssrFileName;
            else {
                File SDCardpath = Environment.getExternalStorageDirectory();
                path = SDCardpath.getAbsolutePath() + "/Music/" + "record" + idx;
            }

            if (idx < 0 || idx >= MAX_RECORDER) {
                Log.d(TAG, "record startwav error: invalid index for recorder " + idx);
                return;
            }
            if(mMediaRecorderContainer[idx] == null) {
                mMediaRecorderContainer[idx] = new RecorderContainer(RecordController.FORMAT.FORMAT_WAV);
                mMediaRecorderContainer[idx].recorderio.setRecorderIOListener(mParent.listenerCache);

                Log.d(TAG, "record wav " + idx + " start");
                synchronized(this.wd_lock) {
                    try {
                        mMediaRecorderContainer[idx].recorderio.startRecord(path, mRecordIOErrorHandle);
                    } catch (Exception e) {
                        Log.d(TAG, "record wav error:" + e);
                        e.printStackTrace();
                        mMediaRecorderContainer[idx].setException(e);
                    }
                }
            } else
                Log.d(TAG, "record wav idx " + idx + " is using");
        }

        private void _stop(int idx) {
            if (idx < 0 || idx >= MAX_RECORDER) {
                Log.d(TAG, "record stop24 error: invalid index for recorder " + idx);
                return;
            }
            if (mMediaRecorderContainer[idx] != null) {
                if (mMediaRecorderContainer[idx].format == RecordController.FORMAT.FORMAT_24BIT) {
                    Log.d(TAG, "record 24bit " + idx + " stop");
                    synchronized(this.wd_lock) {
                        mAudioManager.setParameters("VOICE_RECORDING_MODE=OFF");
                        mMediaRecorderContainer[idx].recorder.stop();
                        mMediaRecorderContainer[idx].recorder.release();
                    }
                    mMediaRecorderContainer[idx] = null;
                } else if (mMediaRecorderContainer[idx].format == RecordController.FORMAT.FORMAT_16BIT) {
                    Log.d(TAG, "record 16bit " + idx + " stop");
                    synchronized(this.wd_lock) {
                        mAudioManager.setParameters("VOICE_RECORDING_MODE=OFF");
                        mMediaRecorderContainer[idx].recorder.stop();
                        mMediaRecorderContainer[idx].recorder.release();
                    }
                    mMediaRecorderContainer[idx] = null;
                } else if (mMediaRecorderContainer[idx].format == RecordController.FORMAT.FORMAT_WAV) {
                    Log.d(TAG, "record wav " + idx + " stop");
                    mMediaRecorderContainer[idx].recorderio.stopRecord();
                    mMediaRecorderContainer[idx] = null;
                }
            }
        }

        private void closeAllRecorder() {
            Log.d(TAG, "record close all recoder");
            for (int i=0; i<MAX_RECORDER; i++) {
                if (mMediaRecorderContainer[i] != null) {
                    _stop(i);
                }
            }
        }

        private void setStop() {
            if (this.isAlive()){
                this.interrupt();
                exitPending = true;
            }
        }

        @Override
        public void monitor() {
            synchronized(this.wd_lock){}
        }

        @Override
        public void run(){
            while(!exitPending){
                synchronized (mParent) { // class VOIP lock
                    this.idx = mParent.idx;
                    this.cmd = mParent.command;
                    mParent.command = CMD_NONE;
                }

                switch(cmd){
                    case CMD_NONE:
                        try {
                            sleep(500); //ms
                        } catch (InterruptedException e) {}
                        break;
                    case CMD_START:
                        this._start(idx);
                        break;
                    case CMD_STARTWAV:
                        this._startwav(idx);
                        break;
                    case CMD_STOP:
                        this._stop(idx);
                        break;
                }
                cmd = CMD_NONE;
            }
            closeAllRecorder();
            Log.d(TAG, "record thraed exit");
        }
    }// end record thread

    final public static String TAG = "RecordController";

    final private int CMD_NONE = 0;
    final private int CMD_START = 1;
    final private int CMD_START24 = 2;
    final private int CMD_STARTWAV = 3;
    final private int CMD_STOP = 4;
    private int command;
    private int idx;
    public RecordControllerThread thread;
    private String ssrFileName;
    private Handler commHandler;
    private RecorderIO.RecorderIOListener listenerCache;

    public RecordController(AudioManager audioManager, Handler handler)
    {
        commHandler = handler;
        ssrFileName = "";
        thread = new RecordControllerThread(audioManager, this);
        thread.start();
    }

    /* **************************************
        *        TODO:
        *        1. add camcoder mode
        * ***************************************/
    public void start(int idx) {
        synchronized (this) { // class RecordController lock
            this.idx = idx;
            command = CMD_START;
        }
    }

    public void start24(int idx) {
        synchronized (this) { // class RecordController lock
            this.idx = idx;
            command = CMD_START24;
        }
    }

    public void startwav(int idx) {
        synchronized (this) { // class RecordController lock
            this.idx = idx;
            command = CMD_STARTWAV;
        }
    }

    public void startwav_name(int idx, String name) {
        synchronized (this) { // class RecordController lock
            this.idx = idx;
            ssrFileName = name;
            command = CMD_STARTWAV;
        }
    }

    public void stop(int idx) {
        synchronized (this) { // class RecordController lock
            this.idx = idx;
            command = CMD_STOP;
        }
    }

    public void deleteFile(int idx) {
        File SDCardpath = Environment.getExternalStorageDirectory();
        String filename = "";

        if (idx == 0)
            filename = "record" + idx + ".aac";
        else if (idx == 1)
            filename = "record" + idx + ".flac";
        else if (idx == 2)
            filename = "record" + idx + ".wav";

        File file = new File(SDCardpath.getAbsolutePath() + "/Music/" + filename);
        file.delete();
    }

    public void clearError() {
        synchronized (this) { // class RecordController lock
            for (int i = 0; i < thread.MAX_RECORDER; i++) {
                if (thread.mMediaRecorderContainer[i] != null && thread.mMediaRecorderContainer[i].e != null) {
                    thread.mMediaRecorderContainer[i].e = null;
                    stop(i);
                    thread.mMediaRecorderContainer[i] = null;
                }
            }
        }
    }

    public void setRecorderIOListener(RecorderIO.RecorderIOListener listener) {
        listenerCache = listener;
    }

    @Override
    public void destroy() {
        thread.setStop();
    }
}

