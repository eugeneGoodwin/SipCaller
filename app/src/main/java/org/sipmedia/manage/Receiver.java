package org.sipmedia.manage;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import org.sipmedia.media.RtpStreamReceiver;
import org.zoolu.sip.address.NameAddress;

import java.util.Map;


public class Receiver extends BroadcastReceiver {
	public static SipCore sipCore;
	public static Context mContext;

	public static boolean sipStarted = false;
	public static boolean hasAccount = false;

	public static final String PREF_SERVER = "server";
	public static final String PREF_USERNAME = "username";
	public static final String PREF_PASSWORD = "password";
	public static final String PREF_DOMAIN = "domain";
	public static final String PREF_PROTOCOL = "protocol";
	public static final String PREF_PORT = "port";
	public static final String	DEFAULT_SERVER = "pbxes.org";

	public static final String PREF_IMPROVE = "improve";
	public static final boolean	DEFAULT_IMPROVE = false;
	public static final String PREF_EARGAIN = "eargain";
	public static final String PREF_MICGAIN = "micgain";
	public static final String PREF_HEARGAIN = "heargain";
	public static final String PREF_HMICGAIN = "hmicgain";
	public static final String PREF_SETMODE = "setmode";
	//public static final float	DEFAULT_EARGAIN = (float) 0.25;
	public static final float	DEFAULT_EARGAIN = (float) 0.70;
	public static final float	DEFAULT_MICGAIN = (float) 0.25;
	public static final float	DEFAULT_HEARGAIN = (float) 0.25;
	public static final String PREF_NODATA = "nodata";
	public static final boolean	DEFAULT_NODATA = false;
	public static final String PREF_OLDVALID = "oldvalid";
	public static final String PREF_OLDVIBRATE = "oldvibrate";
	public static final String PREF_OLDVIBRATE2 = "oldvibrate2";
	public static final String PREF_OLDPOLICY = "oldpolicy";
	public static final String PREF_OLDRING = "oldring";
	public static final boolean	DEFAULT_OLDVALID = false;
	public static final int		DEFAULT_OLDVIBRATE = 0;
	public static final int		DEFAULT_OLDVIBRATE2 = 0;
	public static final int		DEFAULT_OLDPOLICY = 0;
	public static final int		DEFAULT_OLDRING = 0;
	public static final boolean	DEFAULT_SETMODE = false;

	public static final String PREF_CODECS = "codecs_new";
	public static final String	DEFAULT_CODECS = null;

	private static String SIP_DOMAIN;
	private static String SIP_USERNAME;
	private static String SIP_PASSWORD;
	private static String SIP_SERVER_HOST;
	private static String SIP_SERVER_PORT;
	private static String SIP_SERVER_PROTOCOL;


	final static long[] vibratePattern = {0,1000,1000};

	public static String pstn_state;

	public static int call_state;
	public static int call_end_reason = -1;
	 
	 //synchronized why ??
	 public static synchronized SipCore engine(Context context) {
		 if (context==null) {
			 sipCore = new SipCore();
			 return sipCore;
		 }
         if (mContext == null)
                 mContext = context;
         if (sipCore == null) {
                 sipCore = new SipCore();
                 if (sipCore!=null) Log.d("Receiver","SipCore created");
                 //sipCore.StartEngine();
         }
         return sipCore;
 }
	 
	 public static void register(){
		sipCore.register();
	 }
	 

	 

	@Override
	public void onReceive(Context arg0, Intent intent) {
		// TODO Auto-generated method stub
		Log.d("Receiver",intent.getAction().toString());
	}
	
	public boolean isRegistered(){
	return sipCore.isRegistered();
	}

	public static Ringtone oRingtone;
	static android.os.Vibrator v;

	public static void stopRingtone() {
		if (v != null)
			v.cancel();
		if (Receiver.oRingtone != null) {
			Ringtone ringtone = Receiver.oRingtone;
			oRingtone = null;
			ringtone.stop();
		}
	}
	public static int speakermode() {
		//if (docked > 0 && headset <= 0)
		//	return AudioManager.MODE_NORMAL;
		//else
			return AudioManager.MODE_IN_CALL;
	}

	public static void onState(int state,String caller) {
		if (call_state != state) {
			if (state != UA.UA_STATE_IDLE)
				call_end_reason = -1;
			call_state = state;
			switch(call_state)
			{
				case UA.UA_STATE_INCOMING_CALL:
					RtpStreamReceiver.good = RtpStreamReceiver.lost = RtpStreamReceiver.loss = RtpStreamReceiver.late = 0;
					RtpStreamReceiver.speakermode = speakermode();

					AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
					int rm = am.getRingerMode();
					int vs = am.getVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER);
					if (v == null) v = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
					if ((pstn_state == null || pstn_state.equals("IDLE")))
						v.vibrate(vibratePattern,1);
					else {
						if ((pstn_state == null || pstn_state.equals("IDLE")) &&
								(rm == AudioManager.RINGER_MODE_VIBRATE ||
										(rm == AudioManager.RINGER_MODE_NORMAL && vs == AudioManager.VIBRATE_SETTING_ON)))
							v.vibrate(vibratePattern,1);
						if (am.getStreamVolume(AudioManager.STREAM_RING) > 0) {
							String sUriSipRingtone = Settings.System.DEFAULT_RINGTONE_URI.toString();
							if(!TextUtils.isEmpty(sUriSipRingtone)) {
								oRingtone = RingtoneManager.getRingtone(mContext, Uri.parse(sUriSipRingtone));
								if (oRingtone != null) oRingtone.play();
							}
						}
					}
					break;
				case UA.UA_STATE_OUTGOING_CALL:
					RtpStreamReceiver.good = RtpStreamReceiver.lost = RtpStreamReceiver.loss = RtpStreamReceiver.late = 0;
					RtpStreamReceiver.speakermode = speakermode();
					break;
				case UA.UA_STATE_IDLE:
					stopRingtone();
					engine(mContext).listen();
					break;
				case UA.UA_STATE_INCALL:
					stopRingtone();
					break;
				case UA.UA_STATE_HOLD:
					break;
			}
			RtpStreamReceiver.ringback(false);
		}
	}

	/****** Incoming call methods ***********/
	 public static void inCallScreen(String caller){
		 if(mContext==null) Log.d("SipCore","context null");
	 }
	
	public static void inCallEnd() {
		// TODO Auto-generated method stub
		sendIntent("org.sipmedia.manage.endInCall");
	}

	 
	/****** Outgoing call methods ***********/


	
	public static void outCallAccepted() {
		// TODO Auto-generated method stub
		sendIntent("org.sipmedia.manage.outCallAccepted");
	}

	public static void outCallEnd() {
		// TODO Auto-generated method stub
		sendIntent("org.sipmedia.manage.endOutCall");
	}

	public static void outCallConfirmed() {
		// TODO Auto-generated method stub
		sendIntent("org.sipmedia.manage.outCallConfirmed");
	}

	public static void outCallRinging() {
		// TODO Auto-generated method stub
		sendIntent("org.sipmedia.manage.outCallRinging");
	}

	public static void outCallTimeOut() {
		// TODO Auto-generated method stub
		sendIntent("org.sipmedia.manage.outCallTimeOut");
	}

	public static void progressCall() {
		// TODO Auto-generated method stub
		sendIntent("org.sipmedia.manage.progressCall");
	}

	public static void modifyCall() {
		// TODO Auto-generated method stub
		sendIntent("org.sipmedia.manage.modifyCall");
	}

	public static void byeCall() {
		// TODO Auto-generated method stub
		sendIntent("org.sipmedia.manage.byeCall");
	}

	public static void refusedCall(String reason) {
		// TODO Auto-generated method stub
		Intent intent = new Intent("com.test.caller.refusedCall");
		intent.putExtra("reason", reason);
		mContext.sendBroadcast(intent);
	}

	public static void registerSuccess(boolean isReg){
		if(isReg){
			sendIntent("org.sipmedia.manage.registrationSuccess");
		} else {
			sendIntent("org.sipmedia.manage.registrationFailure");
		}
	}

	public static void endCall() {
		sendIntent("org.sipmedia.manage.endCall");
	}

	public static void sendIntent(String name){
		Intent intent = new Intent(name);
		mContext.sendBroadcast(intent);
	}


	//Settings

	public static String getServerName(){
		return PreferenceManager.getDefaultSharedPreferences(Receiver.mContext).getString(Receiver.PREF_SERVER, "");
	}

	public static boolean getImprove(){
		return PreferenceManager.getDefaultSharedPreferences(Receiver.mContext).getBoolean(Receiver.PREF_IMPROVE, Receiver.DEFAULT_IMPROVE);
	}

	public static float getMicGain() {
		return Float.valueOf(PreferenceManager.getDefaultSharedPreferences(Receiver.mContext).getString(Receiver.PREF_MICGAIN, "" + Receiver.DEFAULT_MICGAIN));
	}

	public static float getEarGain() {
		try {
			return Float.valueOf(PreferenceManager.getDefaultSharedPreferences(Receiver.mContext).getString(Receiver.PREF_EARGAIN, "" + Receiver.DEFAULT_EARGAIN));
		} catch (NumberFormatException i) {
			return Receiver.DEFAULT_EARGAIN;
		}
	}

	public static boolean getNodata() {
		return PreferenceManager.getDefaultSharedPreferences(Receiver.mContext).getBoolean(Receiver.PREF_NODATA, Receiver.DEFAULT_NODATA);
	}

	public static boolean getOldValid() {
		return PreferenceManager.getDefaultSharedPreferences(Receiver.mContext).getBoolean(Receiver.PREF_OLDVALID, Receiver.DEFAULT_OLDVALID);
	}

	public static int getOldVibrate() {
		return PreferenceManager.getDefaultSharedPreferences(Receiver.mContext).getInt(Receiver.PREF_OLDVIBRATE, Receiver.DEFAULT_OLDVIBRATE);
	}

	public static int getOldVibrate2() {
		return PreferenceManager.getDefaultSharedPreferences(Receiver.mContext).getInt(Receiver.PREF_OLDVIBRATE2, Receiver.DEFAULT_OLDVIBRATE2);
	}

	public static int getOldPolicy() {
		return PreferenceManager.getDefaultSharedPreferences(Receiver.mContext).getInt(Receiver.PREF_OLDPOLICY, Receiver.DEFAULT_OLDPOLICY);
	}

	public static int getOldRing() {
		return PreferenceManager.getDefaultSharedPreferences(Receiver.mContext).getInt(Receiver.PREF_OLDRING, Receiver.DEFAULT_OLDRING);
	}

	public static boolean getSetMode() {
		return PreferenceManager.getDefaultSharedPreferences(Receiver.mContext).getBoolean(Receiver.PREF_SETMODE, Receiver.DEFAULT_SETMODE);
	}

	public static int getIntValue(String key, int defValue) {
		return PreferenceManager.getDefaultSharedPreferences(Receiver.mContext).getInt(key, defValue);
	}

	public static void setBooleanValue(String key, boolean value) {
		SharedPreferences.Editor edit = PreferenceManager.getDefaultSharedPreferences(Receiver.mContext).edit();
		edit.putBoolean(key, value);
		edit.commit();
	}


	public static void setIntValue(String key, int value) {
		SharedPreferences.Editor edit = PreferenceManager.getDefaultSharedPreferences(Receiver.mContext).edit();
		edit.putInt(key, value);
		edit.commit();
	}

	public static void setMapIntValue(Map<String, Integer> mapValue) {
		SharedPreferences.Editor edit = PreferenceManager.getDefaultSharedPreferences(Receiver.mContext).edit();
		for(Map.Entry<String, Integer> entry : mapValue.entrySet()) {
			edit.putInt(entry.getKey(), entry.getValue());
		}
		edit.commit();
	}

	public static boolean containsValueByKey(String key) {
		return PreferenceManager.getDefaultSharedPreferences(Receiver.mContext).contains(key);
	}

	public static String getStringValue(String key, String defValue) {
		return PreferenceManager.getDefaultSharedPreferences(Receiver.mContext).getString(key, defValue);
	}

	public static void setStringValue(String key, String value) {
		SharedPreferences.Editor edit = PreferenceManager.getDefaultSharedPreferences(Receiver.mContext).edit();
		edit.putString(key, value);
		edit.commit();
	}

	public static boolean setCurrentSettings(String server, String user, String domain, String port, String protocol, String password){
		SIP_DOMAIN = domain;
		SIP_USERNAME = user;
		SIP_PASSWORD = password;
		SIP_SERVER_HOST = server;
		SIP_SERVER_PORT = port;
		SIP_SERVER_PROTOCOL = protocol;
		return true;
	}

	public static void loadCurrentSettings(){
		if(Receiver.mContext != null) {
			SharedPreferences.Editor edit = PreferenceManager.getDefaultSharedPreferences(Receiver.mContext).edit();
			edit.putString(Receiver.PREF_SERVER, SIP_SERVER_HOST);
			edit.putString(Receiver.PREF_USERNAME, SIP_USERNAME);
			edit.putString(Receiver.PREF_DOMAIN, SIP_DOMAIN);
			edit.putString(Receiver.PREF_PORT, SIP_SERVER_PORT);
			edit.putString(Receiver.PREF_PROTOCOL, SIP_SERVER_PROTOCOL);
			edit.putString(Receiver.PREF_PASSWORD, SIP_PASSWORD);
			edit.commit();
		}
	}
}
