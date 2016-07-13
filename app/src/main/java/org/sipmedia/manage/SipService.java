package org.sipmedia.manage;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;


public class SipService extends Service {
	Receiver sReceiver;
	
	@Override
	public IBinder onBind(Intent arg0) {
		// TODO Auto-generated method stub
		Log.d("SipService","onBind");
		return null;
	}
	@Override
	public void onCreate()
	{	super.onCreate();
		Log.d("SipService", "onCreate()");
		Receiver.sipStarted=true;
		Receiver.engine(this);
		Receiver.loadCurrentSettings();
		Receiver.sipCore.init(getApplicationContext());
		Receiver.register();
		Receiver.sipCore.listen();

	}
	@Override 
	public void onDestroy(){
		super.onDestroy();
		Log.d("SipService","onDestroy()");
		if (sReceiver!=null	) unregisterReceiver(sReceiver);
		Receiver.sipStarted=false;
	}
	

}
