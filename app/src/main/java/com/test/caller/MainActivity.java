package com.test.caller;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.widget.TextView;
import android.widget.Toast;

import org.sipmedia.manage.Receiver;
import org.sipmedia.manage.SipService;
import org.zoolu.sip.address.NameAddress;

public class MainActivity extends FragmentActivity implements CallFragment.ICallListener{

    private final static String SIP_DOMAIN = "XX.XXX.XXX.XXX";
    private final static String SIP_USERNAME = "XXXXXXX";
    private final static String SIP_PASSWORD = "XXXXXXX";
    private final static String SIP_SERVER_HOST = "XX.XXX.XXX.XXX";
    private final static String SIP_SERVER_PORT = "XXXX";
    private final static String SIP_SERVER_PROTOCOL = "udp";

    public static final String PHONE_NUMBER_POSTFIX = "@XX.XXX.XXX.XXX";

    public Context ctx = this;

    BroadcastReceiver callBackReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals("org.sipmedia.manage.endOutCall")){
                System.out.println("+++ sip Call ending ");
                updateStatus("end call");
            }
            else if (intent.getAction().equals("org.sipmedia.manage.outCallAccepted")){
                System.out.println("+++ sip Call Accepted ");
                updateStatus("call accept");
            }
            else if (intent.getAction().equals("org.sipmedia.manage.outCallConfirmed")){
                System.out.println("+++ sip Call Confirmed ");
                updateStatus("call confirmed");
            }
            else if (intent.getAction().equals("org.sipmedia.manage.outCallRinging")){
                System.out.println("+++ sip Call Ringing");
                updateStatus("calling...");
            }
            else if (intent.getAction().equals("org.sipmedia.manage.endInCall")){
                System.out.println("+++ sip End call ");
                Log.d("InCall", "End call");
            }
            else if (intent.getAction().equals("org.sipmedia.manage.outCallTimeOut")){
                System.out.println("+++ sip Call Timed Out ");
                Log.d("InCall", "Call Timed Out");
                updateStatus("call timeout");
                endCall();
            }
            else if (intent.getAction().equals("org.sipmedia.manage.registrationSuccess")){
                System.out.println("+++ sip registrationSuccess ");
                updateStatus("registration done");
            }
            else if (intent.getAction().equals("org.sipmedia.manage.registrationFailure")){
                System.out.println("+++ sip registrationFailure ");
                updateStatus("registration failure");
            }
            else if (intent.getAction().equals("org.sipmedia.manage.endCall")){
                System.out.println("+++ sip end call ");
                updateStatus("end call");
            }
            else if (intent.getAction().equals("org.sipmedia.manage.progressCall")){
                System.out.println("+++ sip progress call ");
                updateStatus("call progress...");
            }
            else if (intent.getAction().equals("org.sipmedia.manage.refusedCall")){
                System.out.println("+++ sip refused call ");
                String reason = intent.getStringExtra("reason");
                updateStatus("call refused " + reason);
                endCall();
            }
            else if (intent.getAction().equals("org.sipmedia.manage.byeCall")){
                System.out.println("+++ sip bye call ");
            }

        }
    };

    @Override
    public void onStart() {
        super.onStart();
        IntentFilter iF = new IntentFilter();
        iF.addAction("org.sipmedia.manage.endOutCall");
        iF.addAction("org.sipmedia.manage.outCallConfirmed");
        iF.addAction("org.sipmedia.manage.outCallRinging");
        iF.addAction("org.sipmedia.manage.outCallTimeOut");
        iF.addAction("org.sipmedia.manage.outCallAccepted");
        iF.addAction("org.sipmedia.manage.endInCall");
        iF.addAction("org.sipmedia.manage.startInCall");
        iF.addAction("org.sipmedia.manage.registrationSuccess");
        iF.addAction("org.sipmedia.manage.registrationFailure");
        iF.addAction("org.sipmedia.manage.endCall");
        iF.addAction("org.sipmedia.manage.progressCall");
        iF.addAction("org.sipmedia.manage.refusedCall");
        iF.addAction("org.sipmedia.manage.byeCall");
        registerReceiver(callBackReceiver, iF);

        Receiver.setCurrentSettings(SIP_SERVER_HOST, SIP_USERNAME, SIP_DOMAIN, SIP_SERVER_PORT, SIP_SERVER_PROTOCOL, SIP_PASSWORD);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!Receiver.sipStarted) startService(new Intent(this,SipService.class));

        if (findViewById(R.id.Dailpadframe) != null)
        {
            if (savedInstanceState == null) {
                CallFragment callFragment = new CallFragment();
                getSupportFragmentManager().beginTransaction().add(R.id.Dailpadframe, callFragment, "call_fragment").commit();
            }
        }
    }

    @Override
    public void onResume(){
        super.onResume();
    }


    public boolean startCall(String phoneNumber) {
        return Receiver.sipCore.call(new NameAddress(phoneNumber + PHONE_NUMBER_POSTFIX));
    }

    public void stopCall() {
        Receiver.sipCore.rejectCall();
        Receiver.sipCore.listen();
        updateStatus("ready");
    }

    public void endCall(){
        this.runOnUiThread(new Runnable() {
            public void run() {
                FragmentManager fm = getSupportFragmentManager();
                CallFragment fragment = (CallFragment)fm.findFragmentByTag("call_fragment");
                fragment.resetCall();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(callBackReceiver);
        Receiver.engine(getApplicationContext()).halt();
        stopService(new Intent(this, SipService.class));
    }


    public Context getContext(){return ctx;}

    public void updateStatus(final String status){
        this.runOnUiThread(new Runnable() {
            public void run() {
                setStatus(status);
            }
        });
    }

    public void setStatus(String status){
        TextView labelView = (TextView) findViewById(R.id.test_status);
        if(labelView != null)
            labelView.setText(status);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        FragmentManager fm = getSupportFragmentManager();
        Fragment fragment = fm.findFragmentByTag("call_fragment");
        if(fragment != null && fragment.isVisible())
            fragment.onActivityResult(requestCode, resultCode, data);
        super.onActivityResult(requestCode, resultCode, data);
    }
}
