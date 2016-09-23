package com.android.settings; 

import java.io.FileOutputStream;
import java.io.File;
import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.FileNotFoundException;

import java.lang.String;
import java.util.Calendar;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.IntentFilter;
import android.app.PendingIntent;
import android.app.AlarmManager;

import android.os.Bundle;
import android.os.SystemProperties;
import android.os.Parcelable;

import android.widget.Switch;

import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.TwoStatePreference;
import android.preference.PreferenceScreen;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.CheckBoxPreference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.Preference.OnPreferenceChangeListener;
import android.content.BroadcastReceiver;

import android.util.Log;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import android.provider.Settings;

import java.lang.System;

import java.io.FileReader;
import java.io.BufferedReader;
import java.util.Scanner;
import java.util.NoSuchElementException;
import java.io.BufferedWriter;
import java.io.FileWriter;

public class OtaSettings extends SettingsPreferenceFragment {
   private static final String LOG_TAG = "OTA_Settings";
   private static final String KEY_OTA_SERVER = "ota_server_name";
   private static final String KEY_OTA_PORT = "ota_port_name";
   private static final String KEY_OTA_PROTOCOL = "ota_protocol_name";
   private static final String KEY_OTA_BUILD = "ota_config_name";
   private static final String KEY_OTA_ARCHIVE = "ota_archive_name";
   private static final String KEY_OTA_MONTHLY = "ota_monthly_check_name";

   private static final String TAG = "OtaSettings";
   public static final long PERIOD = AlarmManager.INTERVAL_DAY * 30;
   public static final String OTA_CHECK_TIME ="monthly";

   // References to preference widgets
   private Activity activity;
   private Switch actionBarSwitch;
   private ListPreference protocol;
   private EditTextPreference server;
   private EditTextPreference port;
   private EditTextPreference build;
   private EditTextPreference archive;
   private CheckBoxPreference monthly;

   // Composed alarm opbjects
   private AlarmManager am = null;
   private Intent updInt = null;
   private PendingIntent pi = null;
   private Context mContext = null;

   // Misc
   private long activationTime = 0;

   public void reprogramAlarm() {
      long timeToActivate = 0;
      Map<String,String> entries = readSettings();
      String entry = null;
      if (entries != null) {
         entry = entries.get(OTA_CHECK_TIME);
      }
      else {
         Log.i(TAG, "No monthly check time to reprogram");
         return;
      }

      // retrieve the last stored check time if available 
      if ((entry != null) && (entry.length() > 0)){
         timeToActivate = Long.valueOf(entry);
      }

      // if there is a time, restore it, otherwise do nothing
      if (timeToActivate > 0){
	 Log.d(TAG, "Restoring alarm after reboot");
         initialSetup();
         prepareIntent();
         programAlarm(timeToActivate);
      }
      else {
	 Log.d(TAG, "No alarm to restore after reboot");
	 return;
       }
   }

   private void prepareIntent() {
      updInt = new Intent();
      updInt.setClassName("com.fsl.android.ota", "com.fsl.android.ota.OtaAutoCheck");
      updInt.addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
      updInt.addFlags(Intent.FLAG_DEBUG_LOG_RESOLUTION);
      updInt.addFlags(Intent.FLAG_FROM_BACKGROUND);
      updInt.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      updInt.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
      if (mContext != null) {
         pi = PendingIntent.getService(mContext, 0, updInt, 0);
      }
   }

   private void removeExistingAlarms() {
      // The user wants automated checks for updates
      // remove any existing alarms
      boolean alarmUp = false;
      if (mContext != null) {

         /* Trying cancelling with just the intent*/
         if ((am != null) && (pi != null)) {
            am.cancel(pi);
         }

        /* Verify the alarm still isn't hanging around, 
           if it is, we'll get rid of it in a moment */
         alarmUp = (PendingIntent.getService(mContext, 0,
                    updInt, PendingIntent.FLAG_NO_CREATE) != null);
       }
       else {
          Log.d(TAG, "Context not available for removing an alarm");
	  return;
       }

       /* Remove the alarm or determine why an alarm cannot be removed */
       if ((alarmUp) && (am != null) && (pi != null)) {
          Log.i(TAG, "Removing existing automated entry from alarm service");
                am.cancel(pi);
          }
       else if (alarmUp == false){
          Log.e(TAG, "No automated entries to remove from alarm service");
       }
       else if ((am == null) && (pi == null)) {
	  Log.e(TAG, "Alarm Manager and Pending Intent not set up properly");
       }
       else if (am == null) {
	  Log.e(TAG, "Alarm Manager not available");
       }
       else if (pi == null) {
	  Log.e(TAG, "Pending Intent has not been properly configured");
       }
   }

   private void programAlarm(long timeToActivate) {
      if ((am != null) && (pi != null)){
         Log.d(TAG, "Setting Alarm: " + pi);
         Log.d(TAG, "Update Intent Info - Action: " + updInt.getAction());
         Log.d(TAG, "Update Intent Info - URI: " + updInt.getDataString());

         /* set repeating alarm for every thirty days */
         am.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                         timeToActivate, PERIOD, pi);

         // Need to write this time out later
         activationTime = timeToActivate;
      }
      else if ((am == null) && (pi == null)) {
         Log.e(TAG, "Both alarm manager and the pending intent are not "+ 
                    "available for use");
	 activationTime = 0;
      }
      else if (am == null) {
         Log.e(TAG, "Alarm Manager was not obtained");
         activationTime = 0;
      }
      else if (pi == null) {
         Log.e(TAG, "Pending intent is not set up properly");
	 activationTime = 0;
      }
   }

   private void initialSetup() {
      activity = getActivity();

      /* Get ref to Alarm Manager for later use*/    
       mContext = (Context)activity;
       if (mContext != null) {
          am = (AlarmManager)(mContext.getSystemService(Context.ALARM_SERVICE )); 
       }  
   }

   @Override
   public void onCreate(Bundle savedInstanceState) {
       super.onCreate(savedInstanceState);

       Log.d(TAG, "Creating activity");
       addPreferencesFromResource(R.xml.ota_settings);
 
      /* Perform initial setup */
      initialSetup();

      if (activity != null) {
          actionBarSwitch = new Switch(activity);        
      }
      else {
         Log.w(TAG, "Could not get handle to activity");
      }

      /* Restore widget values as user is trying to access the 
         OTA settings */
      protocol = (ListPreference) findPreference(KEY_OTA_PROTOCOL);
      server = (EditTextPreference) findPreference(KEY_OTA_SERVER);
      port= (EditTextPreference) findPreference(KEY_OTA_PORT);
      build = (EditTextPreference) findPreference(KEY_OTA_BUILD);
      archive = (EditTextPreference) findPreference(KEY_OTA_ARCHIVE);
      monthly = (CheckBoxPreference) findPreference(KEY_OTA_MONTHLY);

      if (savedInstanceState != null) {
         activationTime = savedInstanceState.getLong("monthly", 0); 
      }
      else if ((monthly.isChecked() == true) && 
               (activationTime == 0)) {
         Map<String,String> entries = readSettings();
	 if (entries != null) {
         	activationTime = Long.valueOf(entries.get(OTA_CHECK_TIME));
	 }
      }

    /* Disabling context receiver until and if we are able to fully
       implement OTA auto checks
      mContext.registerReceiver(new OtaBroadcastReceiver(), 
                                new IntentFilter(Intent.ACTION_TIME_TICK));
   */
   }

   @Override
   public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
      if (preference.getKey().equals(KEY_OTA_MONTHLY)) {
	 Log.d(TAG, "Handling OTA monthly check from onPreferenceTreeClick");
	 return handleAutomatedCheck(preference);
      }
      else {
	 Log.d(TAG, "Received " + preference.getKey() + 
		" with title " + preference.getTitle());
	 return false;
      }
   }

   private boolean handleAutomatedCheck(Preference preference) {

      Log.d(TAG, "in handleAutomatedCheck method");
    
      // Adding monthly check with Alarm Service

      /* Need to determine if alarm was previously set prior to boot and restore 
	 that time; otherwise, use current + 30 days */
      Map<String,String> entries = readSettings();   
      String entry = null;
      if (entries != null) {
      	entry = entries.get(OTA_CHECK_TIME);
      }
      long timeToActivate = 0;
      if ((entry != null) && (entry.length() > 0)) {
         timeToActivate = Long.valueOf(entry);   
      }

      // no time set in ota.conf, specify a new one
      if (timeToActivate == 0) {
	 Log.d(TAG, "No time set, adding a month to the current time");
	 timeToActivate = System.currentTimeMillis() + PERIOD;
      }

      // Prepare interrupt for when alarm fires 
      prepareIntent();
   
      /* only set alarm if user wants the monthly check to be performed and
         everything initialized okay */
      if ((preference instanceof TwoStatePreference) && (am != null) && (pi != null)) {
 	 if (((TwoStatePreference)preference).isChecked() == true) {
                 /* Remove existing alarm */
		 removeExistingAlarms();

                 /* Prepare OTA Intent to fire when alarm goes off*/
		 prepareIntent();
 
		 /* enable the new alarm, it time to activate is in the past,
                    fires immediately */
		 programAlarm(timeToActivate);
	 }   
	 else {
	    // user remove requirement for monthly checks
	    removeExistingAlarms();
         }   
      }   
      else if ((preference instanceof TwoStatePreference) == false ) { 
         Log.e(TAG, "Not a two state preference");
         return false;
       }
       else if (am == null) {
          Log.e(TAG, "Could not obtain a handle to the alarm service");
          return false;
       }
       else if (pi == null) {
          Log.e(TAG, "Could not form a pending intent");
          return false;
       }
       return true;
   }

   @Override
   public void onSaveInstanceState(Bundle outState) {
      super.onSaveInstanceState(outState);
      outState.putLong("monthly", activationTime);
   }

   @Override
   public void onActivityCreated(Bundle savedInstanceState) {
      super.onActivityCreated(savedInstanceState);
      if (savedInstanceState != null) {
         activationTime = savedInstanceState.getLong("monthly", 0);
      }
    }

   private void saveOtaSettings(){
      File file;
      OutputStream out = null;
      final String serverTxt;
      final String portTxt;
      final String protocolTxt;
      final String buildTxt;
      final String otaTxt;
      final String monthlyTxt;

      /* Get reference to Settings SharedPreferences to preserve server and 
         port values for cloning */          
      if (protocol != null) {
          protocolTxt = "protocol=" + protocol.getValue() + "\n";
      }
      else {
          Log.w(TAG, "Protocol entry not found in preferences using default http");
          protocolTxt = "protocol=http" + "\n";
      }

      if (server != null) {
          serverTxt = "server="  + server.getText() + "\n";
      }
      else {
          Log.w(TAG, "Server entry not found in preferences: using default ota.pdiarm.com");
          serverTxt = "server=ota.pdiarm.com" + "\n";
      }

      if (port != null) {
          portTxt = "port=" + port.getText() + "\n";
      }
      else {
         Log.w(TAG, "Port entry not found in preferences using default 80");
         portTxt = "port=80" + "\n";
      }

      if (build != null) {
          buildTxt = "build=" + build.getText() + "\n";
      }
      else {
         Log.w(TAG, "Build entry not found in preferences using default build.prop");
         buildTxt = "build=build.prop" + "\n";
      }

      if (archive != null) {
          otaTxt = "ota=" + archive.getText() + "\n";
      }
      else {
         Log.w(TAG, "Archive entry not found in preferences using default .ota.zip");
         otaTxt = "ota=.ota.zip" + "\n";
      }

      if ((monthly != null) && (monthly.isChecked() == true)) {
          if (activationTime > 0) {
             Log.i(TAG, "Using calculated time " + activationTime);
             monthlyTxt = "monthly=" + activationTime + "\n";
          }
          else {
            Log.w(TAG, "Lost track of time");
            Calendar c = Calendar.getInstance(); 
            c.add(Calendar.DAY_OF_MONTH, 30);
	    monthlyTxt = "monthly=" +
                         Long.valueOf(c.getTimeInMillis()).toString() 
                         + "\n";
          }
      }
      else {
	 monthlyTxt = null;
      }
  
      try {
         file = new File("/data/system/", "ota.conf");
         if (file != null) {
	    if (file.exists()) {
               if (file.delete()) {
                  file.createNewFile();
               }
               else {
                  Log.e(TAG, "Unable to delete file");
               }
            }
            else {
               file.createNewFile();
            }
            out = new BufferedOutputStream(new FileOutputStream(file));
            out.write(protocolTxt.getBytes(), 0, protocolTxt.length());
            out.write(serverTxt.getBytes(), 0, serverTxt.length());
            out.write(portTxt.getBytes(), 0, portTxt.length());
            out.write(buildTxt.getBytes(), 0, buildTxt.length());
            out.write(otaTxt.getBytes(), 0, otaTxt.length());
	    if (monthlyTxt != null) {
	       out.write(monthlyTxt.getBytes(), 0, monthlyTxt.length());
	    }

            /* Write to SharedPreferences */
	    PreferenceScreen root = getPreferenceScreen();
            root.addPreference(protocol);
            root.addPreference(server);
            root.addPreference(port);
            root.addPreference(build);
            root.addPreference(archive);
	    root.addPreference(monthly);
         }
      }
      catch (FileNotFoundException f) {
         Log.e(TAG, "Could not find file. " + f );
      }
      catch (IOException e) {
         Log.e(TAG, "Error handling null file. " + e);
      }
      finally {
         if (out != null) {
            try {
               out.close();
            }
            catch (IOException e) {
               Log.e(TAG, "Error handling null file when closing. " + e);
            }
         }
      }
	
   }

   @Override
   public void onResume() {
      super.onResume();
      Log.d(TAG, "Resuming");

      setPreferenceScreen(null);
      addPreferencesFromResource(R.xml.ota_settings);

      activity = getActivity();
      actionBarSwitch = new Switch(activity);    
      protocol = (ListPreference) findPreference(KEY_OTA_PROTOCOL);
      server = (EditTextPreference) findPreference(KEY_OTA_SERVER);
      port= (EditTextPreference) findPreference(KEY_OTA_PORT);
      build = (EditTextPreference) findPreference(KEY_OTA_BUILD);
      archive = (EditTextPreference) findPreference(KEY_OTA_ARCHIVE);
      monthly = (CheckBoxPreference) findPreference(KEY_OTA_MONTHLY);
   }

   @Override
   public void onStart() {
      super.onStart();
      Log.d(TAG, "Starting");
   }

   @Override
   public void onPause() {
      super.onPause();
      Log.d(TAG, "Pausing");
 
      /* Save settings to Sharedpreferences and ota.conf mrobbeloth PDi*/
      saveOtaSettings();
   }

   public static Map<String,String> readSettings() {
      long timeToActivate = 0;
      Scanner scan = null;
      BufferedReader in = null;
      File file = null;

      Map<String,String> otaSettingsInMap = new TreeMap<String,String>();
      try {
         FileReader fr = new FileReader(new File("/data/system", "ota.conf"));
         if (fr != null) {
	    in = new BufferedReader(fr);
   	    if (in != null) {
	       String string;
	       while ((string = in.readLine()) != null) {

	          // ignore comment lines 
	          if (string.startsWith("#") == true ) {
                     continue;
                   }

	           scan = new Scanner(string);
	           Log.d(TAG, "Reading line: "  + string);
	           scan.useDelimiter("=");

	           try {
	              // scan key
	              String key = null;
	              if (scan.hasNext()) {
	                 key = scan.next();
	              }
	              else {
	                 Log.e(TAG, "No key to scan from line: " + string);
	                 continue;
	              }

	              // scan value
	              String val = null;
	              if (scan.hasNext()) {
	                 val = scan.next();
	              }
	              else {
		         Log.e(TAG, "No value to scan for key " + key
			          + " from line " + string);
		         continue;
	              } 

                      /* Place read entry into in-memory map for later
                         reference */
		      otaSettingsInMap.put(key,val);

	              // see if a previous time is stored in ota.conf
	              if (key.equals(OTA_CHECK_TIME)) {
		         timeToActivate = Long.decode(val);
	              }
	           } catch (NoSuchElementException e) {
	                Log.e(TAG, "Parsing Problem: " + e.toString());
	                e.printStackTrace();
	                continue;
	           }
               }
            }
         }
      if (scan != null) {
         scan.close();
      }
      if (in != null) {
         in.close();
      }
      }
      catch (FileNotFoundException fn) {
         Log.e(TAG, "OTA configuration file not found" + fn.toString());
	 fn.printStackTrace();
	 // Generate new file and return generic Map
	 file = new File("/data/system", "ota.conf");
	 try {
	    FileWriter fw = new FileWriter(file.getAbsoluteFile());
	    BufferedWriter bw = new BufferedWriter(fw);
	    bw.write("server=ota.pdiarm.com");
	    bw.write("port=80");
	    bw.write("ota=.ota.zip");
	    bw.write("build=build.prop");
	    bw.write("protocol=http");
	    Log.w(TAG, "Writing generic OTA configuration to file form source");
	    bw.close();
	 }
	 catch (IOException ioe) {
	    Log.e(TAG, "Error reading/closing new default OTA Configuration" + ioe.toString());
	 }
	 otaSettingsInMap.put("server", "ota.pdiarm.com");
	 otaSettingsInMap.put("port", "80");
	 otaSettingsInMap.put("ota", ".ota.zip");
	 otaSettingsInMap.put("build", "build.prop");
	 otaSettingsInMap.put("protocol", "http");
	 
	 return otaSettingsInMap;
      }
      catch (IOException io) {
	 Log.e(TAG, "Error reading/closing OTA Configuration" + io.toString());
         io.printStackTrace();
         return null;
      }
   return otaSettingsInMap;
   }
} 
