package org.sipmedia.manage;

import java.util.Enumeration;
import java.util.Vector;

import org.sipmedia.codecs.Codec;
import org.sipmedia.codecs.Codecs;
import org.sipmedia.media.JAudioLauncher;
import org.sipmedia.media.MediaLauncher;
import org.sipmedia.media.RtpStreamReceiver;
import org.zoolu.net.IpAddress;
import org.zoolu.sdp.AttributeField;
import org.zoolu.sdp.MediaDescriptor;
import org.zoolu.sdp.MediaField;
import org.zoolu.sdp.SessionDescriptor;
import org.zoolu.sip.address.NameAddress;
import org.zoolu.sip.call.Call;
import org.zoolu.sip.call.CallWatcher;
import org.zoolu.sip.call.CallWatcherListener;
import org.zoolu.sip.call.ExtendedCall;
import org.zoolu.sip.call.SdpTools;
import org.zoolu.sip.dialog.ExtendedInviteDialog;
import org.zoolu.sip.message.Message;
import org.zoolu.sip.provider.SipProvider;
import org.zoolu.tools.Parser;

import android.util.Log;


/*
 * This class implements the SIP UserAgent (both UA Server and UA Client)
 */
public class UA implements CallWatcherListener {
	//private CallWatcher receptionDeamon;
	protected SipProvider sipProvider;
	protected ExtendedCall call;
   // protected Call incomingCall;
	public UserAgentProfile user_profile;
	protected ExtendedInviteDialog extDlg; // 2B or not 2B used
	
	protected String user;
	protected String pass;
	protected String realm;
	protected String callee;
	
	protected static int remote_media_port;
	protected static int local_media_port=7777;
	
	protected String remote_addr;
	
	protected SessionDescriptor local_sdp;
	protected SessionDescriptor remote_sdp;

	/** Local sdp */
	protected String local_session = null;

	/** Audio application */
	public MediaLauncher audio_app = null;
	String remote_media_address;

	org.zoolu.tools.Log log;

	int avp=0;
	String default_codec="PCMU";
	int rate=8000;

	public static final int UA_STATE_IDLE = 0;
	public static final int UA_STATE_INCOMING_CALL = 1;
	public static final int UA_STATE_OUTGOING_CALL = 2;
	public static final int UA_STATE_INCALL = 3;
	public static final int UA_STATE_HOLD = 4;

	int call_state = UA_STATE_IDLE;
		

	public static void setLocalMediaPort(int port){
		UA.local_media_port=port;
		
	}
	
	public static int getRemoteMediaPort(){
		return remote_media_port;
		
	}

	/** Changes the call state */
	protected synchronized void changeStatus(int state,String caller) {
		call_state = state;
		Receiver.onState(state, caller);
	}

	protected void changeStatus(int state) {
		changeStatus(state, null);
	}

	protected boolean statusIs(int state) {
		return (call_state == state);
	}
	
	
	public UA(SipProvider sipProvider, String u, String p, String r){
		this.sipProvider=sipProvider;
		this.user=u;
		this.pass=p;
		this.realm=r;
		log = sipProvider.getLog();
		
	};

	public UA(SipProvider sip_provider, UserAgentProfile user_profile) {
		this.sipProvider = sip_provider;
		this.user_profile = user_profile;
		this.user=user_profile.username;
		this.pass=user_profile.passwd;
		this.realm = user_profile.realm;
		log = sipProvider.getLog();

		// if no contact_url and/or from_url has been set, create it now
		user_profile.initContactAddress(sip_provider);
	}

	/** Gets the local SDP */
	public String getSessionDescriptor() {
		return local_session;
	}

	public void initSessionDescriptor(Codecs.Map c) {
		SessionDescriptor sdp = new SessionDescriptor(
				user_profile.from_url,
				sipProvider.getViaAddress());

		local_session = sdp.toString();

		//We will have at least one media line, and it will be
		//audio
		if (user_profile.audio || !user_profile.video)
		{
//			addMediaDescriptor("audio", user_profile.audio_port, c, user_profile.audio_sample_rate);
			addMediaDescriptor("audio", user_profile.audio_port, c);
		}

		if (user_profile.video)
		{
			addMediaDescriptor("video", user_profile.video_port,
					user_profile.video_avp, "h263-1998", 90000);
		}
	}
	//change end

	/** Adds a single media to the SDP */
	private void addMediaDescriptor(String media, int port, int avp,
									String codec, int rate) {
		SessionDescriptor sdp = new SessionDescriptor(local_session);

		String attr_param = String.valueOf(avp);

		if (codec != null)
		{
			attr_param += " " + codec + "/" + rate;
		}
		sdp.addMedia(new MediaField(media, port, 0, "RTP/AVP",
						String.valueOf(avp)),
				new AttributeField("rtpmap", attr_param));

		local_session = sdp.toString();
	}

	/** Adds a set of media to the SDP */
//	private void addMediaDescriptor(String media, int port, Codecs.Map c,int rate) {
	private void addMediaDescriptor(String media, int port, Codecs.Map c) {
		SessionDescriptor sdp = new SessionDescriptor(local_session);

		Vector<String> avpvec = new Vector<String>();
		Vector<AttributeField> afvec = new Vector<AttributeField>();
		if (c == null) {
			// offer all known codecs
			for (int i : Codecs.getCodecs()) {
				Codec codec = Codecs.get(i);
				if (i == 0) codec.init();
				avpvec.add(String.valueOf(i));
				if (codec.number() == 9)
					afvec.add(new AttributeField("rtpmap", String.format("%d %s/%d", i, codec.userName(), 8000))); // kludge for G722. See RFC3551.
				else
					afvec.add(new AttributeField("rtpmap", String.format("%d %s/%d", i, codec.userName(), codec.samp_rate())));
			}
		} else {
			c.codec.init();
			avpvec.add(String.valueOf(c.number));
			if (c.codec.number() == 9)
				afvec.add(new AttributeField("rtpmap", String.format("%d %s/%d", c.number, c.codec.userName(), 8000))); // kludge for G722. See RFC3551.
			else
				afvec.add(new AttributeField("rtpmap", String.format("%d %s/%d", c.number, c.codec.userName(), c.codec.samp_rate())));
		}
		if (user_profile.dtmf_avp != 0){
			avpvec.add(String.valueOf(user_profile.dtmf_avp));
			afvec.add(new AttributeField("rtpmap", String.format("%d telephone-event/%d", user_profile.dtmf_avp, user_profile.audio_sample_rate)));
			afvec.add(new AttributeField("fmtp", String.format("%d 0-15", user_profile.dtmf_avp)));
		}

		//String attr_param = String.valueOf(avp);

		sdp.addMedia(new MediaField(media, port, 0, "RTP/AVP", avpvec), afvec);

		local_session = sdp.toString();
	}
	
	public boolean listen(){
		if (Receiver.call_state != UA_STATE_IDLE)
		{
			return false;
		}

		hangup();

		Log.d("UA","listening");
		call = new ExtendedCall(sipProvider,  new NameAddress("sip:"+user+"@"+IpAddress.getLocalHostAddress().toString()), 
        		user,realm,pass,this);
		call.listen();
		return true;
	}

	/** Closes an ongoing, incoming, or pending call */
	public void hangup()
	{
		closeMediaApplication();

		if (call != null)
		{
			call.hangup();
		}

		changeStatus(UA_STATE_IDLE);
	}

	public boolean accept()
	{
		if (call == null)
		{
			return false;
		}

		changeStatus(UA_STATE_INCALL);

		call.accept(local_session);

		return true;
	}
	
	public void reject(){
		Log.d("UA", "rejecting call");

		if (call.isIncoming()) {
			call.hangup();
		}
		else {
			call.hangup();
			call.refuse();
		}
		closeMediaApplication();
		changeStatus(UA_STATE_IDLE);
	}

	public boolean call(NameAddress callee){
		Log.d("UA","call start");
		this.callee=callee.getAddress().toString();
		if (Receiver.call_state != UA_STATE_IDLE)
			return false;
		changeStatus(UA_STATE_OUTGOING_CALL,this.callee);
		//String from_url;


		//from_url = user_profile.from_url;
		createOffer();
		//change end
		call = new ExtendedCall(sipProvider, new NameAddress("sip:"+user+"@"+IpAddress.getLocalHostAddress().toString()),
				user_profile.username, user_profile.realm, user_profile.passwd, this);

		//target_url = sip_provider.completeNameAddress(target_url).toString();

		if (user_profile.no_offer)
		{
			call.call(callee);
		}
		else
		{
			call.call(callee, local_session);
		}
		return true;
	}

	private void createOffer() {
		initSessionDescriptor(null);
	}

	/** Launches the Media Application (currently, the RAT audio tool) */
	protected void launchMediaApplication() {
		// exit if the Media Application is already running
		if (audio_app != null) {
			return;
		}
		Codecs.Map c;
		// parse local sdp
		SessionDescriptor local_sdp = new SessionDescriptor(call
				.getLocalSessionDescriptor());
		int local_audio_port = 0;
		int dtmf_pt = 0;
		c = Codecs.getCodec(local_sdp);
		if (c == null) {
			hangup();
			return;
		}
		MediaDescriptor m = local_sdp.getMediaDescriptor("audio");
		if (m != null) {
			local_audio_port = m.getMedia().getPort();
			if (m.getMedia().getFormatList().contains(String.valueOf(user_profile.dtmf_avp)))
				dtmf_pt = user_profile.dtmf_avp;
		}
		// parse remote sdp
		SessionDescriptor remote_sdp = new SessionDescriptor(call
				.getRemoteSessionDescriptor());
		remote_media_address = (new Parser(remote_sdp.getConnection()
				.toString())).skipString().skipString().getString();
		int remote_audio_port = 0;
		for (Enumeration<MediaDescriptor> e = remote_sdp.getMediaDescriptors()
				.elements(); e.hasMoreElements();) {
			MediaField media = e.nextElement().getMedia();
			if (media.getMedia().equals("audio"))
				remote_audio_port = media.getPort();
		}

		Log.d("UA","local audio port " + local_audio_port);
		Log.d("UA","remote audio port " + remote_audio_port);
		Log.d("UA","map codecs : " + c.toString());

		// select the media direction (send_only, recv_ony, fullduplex)
		int dir = 0;
		if (user_profile.recv_only)
			dir = -1;
		else if (user_profile.send_only)
			dir = 1;

		if (user_profile.audio && local_audio_port != 0
				&& remote_audio_port != 0) { // create an audio_app and start
			// it

			if (audio_app == null) { // for testing..
				String audio_in = null;
				if (user_profile.send_tone) {
					audio_in = JAudioLauncher.TONE;
				} else if (user_profile.send_file != null) {
					audio_in = user_profile.send_file;
				}
				String audio_out = null;
				if (user_profile.recv_file != null) {
					audio_out = user_profile.recv_file;
				}

				audio_app = new JAudioLauncher(local_audio_port,
						remote_media_address, remote_audio_port, dir, audio_in,
						audio_out, c.codec.samp_rate(),
						user_profile.audio_sample_size,
						c.codec.frame_size(), log, c, dtmf_pt);
			}
			audio_app.startMedia();
		}
	}

	/** Close the Media Application */
	protected void closeMediaApplication() {
		if (audio_app != null) {
			audio_app.stopMedia();
			audio_app = null;
		}
	}

	public boolean muteMediaApplication() {
		if (audio_app != null)
			return audio_app.muteMedia();
		return false;
	}

	public int speakerMediaApplication(int mode) {
		int old;

		if (audio_app != null)
			return audio_app.speakerMedia(mode);
		old = RtpStreamReceiver.speakermode;
		RtpStreamReceiver.speakermode = mode;
		return old;
	}

	private void createAnswer(SessionDescriptor remote_sdp) {

		Codecs.Map c = Codecs.getCodec(remote_sdp);
		if (c == null)
			throw new RuntimeException("Failed to get CODEC: AVAILABLE : " + remote_sdp);
		initSessionDescriptor(c);
		sessionProduct(remote_sdp);
	}

	private void sessionProduct(SessionDescriptor remote_sdp) {
		SessionDescriptor local_sdp = new SessionDescriptor(local_session);
		SessionDescriptor new_sdp = new SessionDescriptor(local_sdp
				.getOrigin(), local_sdp.getSessionName(), local_sdp
				.getConnection(), local_sdp.getTime());
		new_sdp.addMediaDescriptors(local_sdp.getMediaDescriptors());
		new_sdp = SdpTools.sdpMediaProduct(new_sdp, remote_sdp.getMediaDescriptors());
		//new_sdp = SdpTools.sdpAttirbuteSelection(new_sdp, "rtpmap"); ////change multi codecs
		local_session = new_sdp.toString();
		if (call!=null) call.setLocalSessionDescriptor(local_session);
	}
	
	
	@Override
	public void onCallAttendedTransfer(ExtendedCall arg0, NameAddress arg1,
			NameAddress arg2, String arg3, Message arg4) {
		// TODO Auto-generated method stub
	}

	@Override
	public void onCallTransfer(ExtendedCall arg0, NameAddress arg1,
			NameAddress arg2, Message arg3) {
		// TODO Auto-generated method stub
		Log.d("UA","call transfer");
	}

	@Override
	public void onCallTransferAccepted(ExtendedCall arg0, Message arg1) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onCallTransferFailure(ExtendedCall arg0, String arg1,
			Message arg2) {
		// TODO Auto-generated method stub
	}

	@Override
	public void onCallTransferRefused(ExtendedCall arg0, String arg1,
			Message arg2) {
		// TODO Auto-generated method stub
	}

	@Override
	public void onCallTransferSuccess(ExtendedCall arg0, Message arg1) {
		// TODO Auto-generated method stub
	}


	@Override
	public void onCallAccepted(Call arg0, String arg1, Message arg2) {
		// TODO Auto-generated method stub
		Log.d("UA","call accepted");
		if (arg0 != this.call) {
			return;
		}
		if (!statusIs(UA_STATE_OUTGOING_CALL)) {
			hangup();
			return;
		}
		changeStatus(UA_STATE_INCALL);

		SessionDescriptor remote_sdp = new SessionDescriptor(arg1);
		if (user_profile.no_offer) {
			// answer with the local sdp
			createAnswer(remote_sdp);
			call.ackWithAnswer(local_session);
		} else {
			// Update the local SDP along with offer/answer
			sessionProduct(remote_sdp);
		}
		launchMediaApplication();
		Receiver.outCallAccepted();
	}

	@Override
	public void onCallBye(Call arg0, Message arg1) {
		// TODO Auto-generated method stub
		Log.d("UA","call bye");
		listen();
		changeStatus(UA_STATE_IDLE);
		Receiver.byeCall();
	}

	@Override
	public void onCallCancel(Call arg0, Message arg1) {
		// TODO Auto-generated method stub
		Log.d("UA", "call canceled");
		listen();
		changeStatus(UA_STATE_IDLE);
	}

	@Override
	public void onCallClosed(Call arg0, Message arg1) {
		// TODO Auto-generated method stub
		Log.d("UA","call closed");
		listen();
		if (call.isIncoming())
			Receiver.inCallEnd();
		if (call.isOutgoing())
			Receiver.outCallEnd();
		Receiver.endCall();
		closeMediaApplication();
		changeStatus(UA_STATE_IDLE);
	}

	@Override
	public void onCallConfirmed(Call arg0, String arg1, Message arg2) {
		// TODO Auto-generated method stub
		Log.d("UA","call confirmed");
		Receiver.outCallConfirmed();
	}

	@Override
	public void onCallInvite(Call cal, NameAddress callee, NameAddress caller,
			String sdp, Message msg) {
		// TODO Auto-generated method stub
		if (cal != this.call) {
			return;
		}

		Log.d("UA","call invite");

		cal.ring();
		
		remote_sdp=new SessionDescriptor(cal.getRemoteSessionDescriptor());
		remote_media_port=remote_sdp.getMediaDescriptor("audio").getMedia().getPort();
		remote_addr=remote_sdp.getOrigin().getAddress();
		Log.d("UA", "address=" + remote_addr + " port=" + remote_media_port);
		Receiver.inCallScreen(caller.toString());

		changeStatus(UA_STATE_INCOMING_CALL, caller.toString());
		//launchMediaApplication();
	}

	@Override
	public void onCallModify(Call arg0, String arg1, Message arg2) {
		// TODO Auto-generated method stub
		Log.d("UA","call modify");
		Receiver.modifyCall();
	}

	@Override
	public void onCallProgress(Call arg0, Message arg1) {
		// TODO Auto-generated method stub
		Log.d("UA","call progress");
		Receiver.progressCall();
	}

	@Override
	public void onCallReInviteAccepted(Call arg0, String arg1, Message arg2) {
		// TODO Auto-generated method stub
		if (statusIs(UA_STATE_HOLD))
			changeStatus(UA_STATE_INCALL);
		else
			changeStatus(UA_STATE_HOLD);
	}

	@Override
	public void onCallReInviteRefused(Call arg0, String arg1, Message arg2) {
		// TODO Auto-generated method stub
		Log.d("UA","call reinvite refused");
	}

	@Override
	public void onCallReInviteTimeout(Call arg0) {
		// TODO Auto-generated method stub
		Log.d("UA","call reinvite timeout");
	}

	@Override
	public void onCallRedirected(Call arg0, String arg1, Vector arg2,
			Message arg3) {
		// TODO Auto-generated method stub
		Log.d("UA","call redirected");
	}

	@Override
	public void onCallRefused(Call l, String reason, Message resp) {
		
		Log.d("UA","call refused "+reason+" msg= "+resp.toString());
		changeStatus(UA_STATE_IDLE);
		// TODO 401 and 407 authorization
		Receiver.refusedCall(reason);
		if (resp.getStatusLine().getCode()==486){
			Log.d("UA", "callee busy");
		}
	}

	@Override
	public void onCallRinging(Call call, Message msg) {
		// TODO Auto-generated method stub
		Log.d("UA","CallRinging");
		//this.incomingCall=call;
		Receiver.outCallRinging();
	}

	@Override
	public void onCallTimeout(Call arg0) {
		// TODO Auto-generated method stub
		Log.d("UA","Call Time Out");
		Receiver.outCallTimeOut();
		changeStatus(UA_STATE_IDLE);
	}

	@Override
	public void onNewIncomingCall(CallWatcher arg0, ExtendedCall arg1,
			NameAddress arg2, NameAddress arg3, String arg4, Message arg5) {
		// TODO Auto-generated method stub
		Log.d("UA","new incoming call");
	}
}
