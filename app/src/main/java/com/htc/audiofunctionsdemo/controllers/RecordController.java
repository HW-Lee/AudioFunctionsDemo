package com.htc.audiofunctionsdemo.controllers;

import android.media.AudioManager;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.htc.audiofunctionsdemo.utils.Constants;
import com.htc.audiofunctionsdemo.utils.RecorderIO;
import com.htc.audiofunctionsdemo.utils.WatchDog;

import java.io.File;
import java.lang.ref.WeakReference;

public class RecordController implements Controllable {

    enum FORMAT {
        FORMAT_16BIT, FORMAT_24BIT, FORMAT_PCM, FORMAT_PCM_24
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
                boolean isHD = false;
                if (format == FORMAT.FORMAT_PCM_24)
                    isHD = true;
                recorderio = new RecorderIO(Constants.AudioRecordConfig.BUFFER_SIZE_MILLIS, isHD);
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

    public class RecordControllerThread extends Thread implements WatchDog.Monitor {
        private final String TAG = Constants.packageTag("RecordControllerThread");

        int MAX_RECORDER = 3;
        private RecorderContainer[] mMediaRecorderContainer = null;

        private AudioManager mAudioManager;
        final private RecordController mParent;
        final private RecordControllerThread wd_lock;
        private boolean exitPending;
        private int idx, cmd;

        RecordControllerThread(AudioManager m, RecordController parnet){
            mAudioManager = m;
            mParent = parnet;
            wd_lock = this;
            exitPending = false;

            mMediaRecorderContainer = new RecorderContainer[MAX_RECORDER];
            for(int i=0; i<MAX_RECORDER; i++)
                mMediaRecorderContainer[i] = null;
        }

        private void _startpcm(int idx, RecordController.FORMAT format) {
            if (idx < 0 || idx >= MAX_RECORDER) {
                Log.d(TAG, "record startwav error: invalid index for recorder " + idx);
                return;
            }
            if(mMediaRecorderContainer[idx] == null) {
                mMediaRecorderContainer[idx] = new RecorderContainer(format);
                mMediaRecorderContainer[idx].recorderio.setRecorderIOListener(mParent.listenerCache);

                Log.d(TAG, "record wav " + idx + " start");
                synchronized(this.wd_lock) {
                    try {
                        mMediaRecorderContainer[idx].recorderio.startRecord();
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
                } else if (mMediaRecorderContainer[idx].format == RecordController.FORMAT.FORMAT_PCM || mMediaRecorderContainer[idx].format == RecordController.FORMAT.FORMAT_PCM_24) {
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
                    case CMD_STARTPCM:
                        RecordController.FORMAT format;
                        if (isHD)
                            format = RecordController.FORMAT.FORMAT_PCM_24;
                        else
                            format = RecordController.FORMAT.FORMAT_PCM;
                        this._startpcm(idx, format);
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

    final public static String TAG = Constants.packageTag("RecordController");

    final private int CMD_NONE = 0;
    final private int CMD_STARTPCM = 1;
    final private int CMD_STOP = 2;
    private int command;
    private int idx;
    private boolean isHD;
    public RecordControllerThread thread;
    private Handler commHandler;
    private RecorderIO.RecorderIOListener listenerCache;

    public RecordController(AudioManager audioManager, Handler handler)
    {
        commHandler = handler;
        thread = new RecordControllerThread(audioManager, this);
        thread.start();
    }

    /* **************************************
        *        TODO:
        *        1. add camcoder mode
        * ***************************************/
    public void startpcm24(int idx) {
        synchronized (this) { // class RecordController lock
            this.idx = idx;
            this.isHD = true;
            command = CMD_STARTPCM;
        }
    }

    public void startpcm(int idx) {
        synchronized (this) { // class RecordController lock
            this.idx = idx;
            this.isHD = false;
            command = CMD_STARTPCM;
        }
    }

    public void stop(int idx) {
        synchronized (this) { // class RecordController lock
            this.idx = idx;
            command = CMD_STOP;
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

