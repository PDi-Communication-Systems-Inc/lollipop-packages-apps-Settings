
    package com.android.settings.notification;

    public class InternalSpeakersJNI {  
      
    /*Load the static/shared library */
    static {  
    System.loadLibrary("internalspeakers_jni");  
    }  
     
    /* Declare the native function toggleInternalSpeakers() and 
     *have the implenetation in .c file under  jni folder
     */
    public static native void toggleInternalSpeakers(boolean val);  
      

    /*
     * return's the firmware version of the pillow speakers
     */
    public static native String returnFirmwareVersion();  
    }  
