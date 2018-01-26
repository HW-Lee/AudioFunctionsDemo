package com.htc.audiofunctionsdemo.utils;

/**
 * Created by HWLee on 28/10/2017.
 */

public class FFT {
    final static private String TAG = Constants.packageTag("FFT");

    static {
        System.loadLibrary("native-fft");
    }

    native static public double[] transformAbs(double[] signal);
    native static public String getVersion();
}
