package com.htc.audiofunctionsdemo;


import android.content.DialogInterface;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class WatchDog extends Thread {

    public final class HandlerChecker implements Runnable {
        private final static String TAG = "HandlerChecker";
        private final String mName;
        private final long mWaitMax;
        private boolean mCompleted;
        private long mStartTime;
        private final ArrayList<Monitor> mMonitors = new ArrayList<Monitor>();
        private Monitor mCurMonitor = null;
        private boolean pExit;

        HandlerChecker(String name, long waitMaxMillis) {
            mName = name;
            mWaitMax = waitMaxMillis;
            mCompleted = true;
            pExit = false;
        }

        public void addMonitor(Monitor monitor) {
            mMonitors.add(monitor);
        }

        public void scheduleCheckLocked() {
            if (mMonitors.size() == 0) {
                mCompleted = true;
                return;
            }
            if (!mCompleted) {
                /* no need to update time for thread with stuck suspect */
                return;
            }
            mCompleted = false;
            mCurMonitor = null;
            mStartTime = SystemClock.uptimeMillis();
        }

        public boolean isOverdueLocked() {
            return (!mCompleted) && (SystemClock.uptimeMillis() > mStartTime + mWaitMax);
        }

        public int getCompletionStateLocked() {
            if (mCompleted) {
                return COMPLETED;
            } else {
                long latency = SystemClock.uptimeMillis() - mStartTime;
                if (latency < mWaitMax / 2) {
                    return WAITING;
                } else if (latency < mWaitMax) {
                    return WAITED_HALF;
                }
            }
            return OVERDUE;
        }

        public String describeBlockedStateLocked() {
            return mName;
        }

        public void threadExit() {
            this.pExit = true;
        }

        @Override
        public void run() {
            while (!pExit) {
                final int size = mMonitors.size();

                for (int i = 0; i < size; i++) {
                    synchronized (WatchDog.this) {
                        mCurMonitor = mMonitors.get(i);
                    }
                    mCurMonitor.monitor();
                }
                synchronized (WatchDog.this) {
                    mCompleted = true;
                    mCurMonitor = null;
                }

            }
            Log.d(TAG, this.mName + " monitor thread exit");
        }
    } // HandlerChecker class end

    private class BlockWindowChecker {
        MainActivity ma;

        BlockWindowChecker(MainActivity mainActivity) {
            ma = mainActivity;
        }

        public void showDialog() {
            showDialog("Test\n");
        }

        public void showDialog(String NoResClass) {
            android.support.v7.app.AlertDialog.Builder dialog = new android.support.v7.app.AlertDialog.Builder(ma);
            dialog.setTitle("SSD_AAT isn't Responding");
            String msg = "Object: " + NoResClass + "Do you want to close it?";
            dialog.setMessage(msg);

            dialog.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface arg0, int arg1) {
                    Log.d(TAG, "*** WATCHDOG KILLING SYSTEM PROCESS ( " + Process.myPid() + ") ***");
                    Process.killProcess(Process.myPid());
                }
            });
            dialog.show();
        }
    }

    private final static String TAG = "Watchdog";
    private static WatchDog watchdogIns;
    final private ArrayList<HandlerChecker> mHandlerCheckers = new ArrayList<>();
    private static final long DEFAULT_TIMEOUT = 6 * 1000;
    private final long CHECK_INTERVAL = DEFAULT_TIMEOUT / 2;

    private static final int COMPLETED = 0;
    private static final int WAITING = 1;
    private static final int WAITED_HALF = 2;
    private static final int OVERDUE = 3;

    BlockWindowChecker bwc;
    StringBuilder mBlockerMsgBuilder;
    private boolean mThreadExit;

    private Handler mPopUpHandler = new WatchDogHandler(this);

    private static class WatchDogHandler extends Handler {
        WeakReference<WatchDog> mWatchDogRef;
        WatchDogHandler(WatchDog wd) {
            mWatchDogRef = new WeakReference<>(wd);
        }

        @Override
        public void handleMessage(Message msg) {
            WatchDog wd = mWatchDogRef.get();
            if (wd != null) {
                if (msg.what == 1) {
                    if (wd.mBlockerMsgBuilder != null) {
                        wd.bwc.showDialog(wd.mBlockerMsgBuilder.toString());
                    }
                }
            }
            super.handleMessage(msg);
        }
    }

    public interface Monitor {
        void monitor();
    }

    private WatchDog(MainActivity ma) {
        bwc = new BlockWindowChecker(ma);
        mThreadExit = false;
        mBlockerMsgBuilder = null;
    }

    public static WatchDog getInstance(MainActivity ma) {
        if (watchdogIns == null)
            watchdogIns = new WatchDog(ma);

        return watchdogIns;
    }

    public void addMonitor(String name, Monitor monitor) {
        synchronized (this) {
            if (isAlive()) {
                throw new RuntimeException("Monitors can't be added once the Watchdog is running");
            }
            HandlerChecker hc = new HandlerChecker(name, DEFAULT_TIMEOUT);
            hc.addMonitor(monitor);
            Thread thread = new Thread(hc, name);
            thread.start();
            mHandlerCheckers.add(hc);
            Log.d(TAG, "add monitor " + name);
        }
    }

    private int evaluateCheckerCompletionLocked() {
        int state = COMPLETED;
        for (int i = 0; i < mHandlerCheckers.size(); i++) {
            HandlerChecker hc = mHandlerCheckers.get(i);
            state = hc.getCompletionStateLocked() > state ? hc.getCompletionStateLocked() : state;
            if (state > COMPLETED)
                return state;
        }
        return state;
    }

    private ArrayList<HandlerChecker> getBlockedCheckersLocked() {
        ArrayList<HandlerChecker> blockers = new ArrayList<HandlerChecker>();
        for (int i = 0; i < mHandlerCheckers.size(); i++) {
            HandlerChecker hc = mHandlerCheckers.get(i);
            if (hc.isOverdueLocked()) {
                blockers.add(hc);
            }
        }
        return blockers;
    }

    private void UIShowCheckersLocked(ArrayList<HandlerChecker> checkers) {
        mBlockerMsgBuilder = new StringBuilder(256);
        for (int i = 0; i < checkers.size(); i++) {
            mBlockerMsgBuilder.append(checkers.get(i).describeBlockedStateLocked()).append("\n");
        }
        stopAllMonitorThreaed();
        //bwc.showDialog(mBlockerMsgBuilder.toString());
        Message msg = mPopUpHandler.obtainMessage();
        msg.what = 1;
        msg.sendToTarget();
    }

    public void stopAllMonitorThreaed() {
        for (int i = 0; i < mHandlerCheckers.size(); i++) {
            HandlerChecker hc = mHandlerCheckers.get(i);
            hc.threadExit();
        }
        mThreadExit = true;
    }

    @Override
    public void run() {
        Log.d(TAG, "Watchdog thread start!");
        while (!mThreadExit) {
            final ArrayList<HandlerChecker> blockedCheckers;

            synchronized (this) {
                long timeout = CHECK_INTERVAL;
                for (int i = 0; i < mHandlerCheckers.size(); i++) {
                    HandlerChecker hc = mHandlerCheckers.get(i);
                    hc.scheduleCheckLocked();
                }

                long start = SystemClock.uptimeMillis();
                while (timeout > 0) {
                    try {
                        wait(timeout);
                    } catch (InterruptedException e) {
                        Log.d(TAG, "interrupt exception: " + e);
                    }
                    timeout = CHECK_INTERVAL - (SystemClock.uptimeMillis() - start);
                }
                final int waitState = evaluateCheckerCompletionLocked();
                if (waitState != OVERDUE)
                    continue;
            }
            /* If we got here means AAT process is most likely hung.
                         * Somebody should collect dumpstate/ bugreport of the device and re-start it.
                         */
            blockedCheckers = getBlockedCheckersLocked();
            UIShowCheckersLocked(blockedCheckers);
        }
        Log.d(TAG, "WatchDog thread exit");
    }

    /* Test */
    public void test() {
        stopAllMonitorThreaed();
        bwc.showDialog();
    }
}
