package org.sipmedia.manage;


import org.zoolu.net.IpAddress;
import org.zoolu.sip.address.NameAddress;
import org.zoolu.sip.address.SipURL;
import org.zoolu.sip.authentication.DigestAuthentication;
import org.zoolu.sip.header.AuthorizationHeader;
import org.zoolu.sip.header.ExpiresHeader;
import org.zoolu.sip.header.MultipleHeader;
import org.zoolu.sip.header.ProxyAuthenticateHeader;
import org.zoolu.sip.header.ProxyAuthorizationHeader;
import org.zoolu.sip.header.StatusLine;
import org.zoolu.sip.header.ViaHeader;
import org.zoolu.sip.header.WwwAuthenticateHeader;
import org.zoolu.sip.message.Message;
import org.zoolu.sip.message.MessageFactory;
import org.zoolu.sip.message.SipMethods;
import org.zoolu.sip.provider.SipProvider;
import org.zoolu.sip.provider.SipStack;
import org.zoolu.sip.transaction.TransactionClient;
import org.zoolu.sip.transaction.TransactionClientListener;

import android.util.Log;

public class RegisterAgent implements TransactionClientListener {

	/** Max number of registration attempts. */
	static final int MAX_ATTEMPTS = 3;

	public static final int UNKNWON = 0;
	public static final int UNREGISTERED = 1;
	public static final int REGISTERING = 2;
	public static final int REGISTERED = 3;
	
	
	public int status=0;
	public String reason;
	private int attempts;
	/** Qop for the next authentication. */
	private String qop;
	

	private NameAddress toAddress = null;
	private NameAddress fromAddress = null;
	private NameAddress contactAddress = null;
	private SipURL sipURL = null;
	private String uri = null;
	private String password = null;
	private String username = null;
	private String realm = null;
	private SipProvider sipProvider=null;

	private RegisterAgentListener listener = null;
	
	public RegisterAgent(SipProvider sip_provider, SipURL registrar, NameAddress target_url, NameAddress from_url, String username, String realm, String passwd, RegisterAgentListener listener){
		this.username=username;
        this.password=passwd;
        this.realm=realm;
        this.toAddress = target_url;
        this.sipProvider=sip_provider;
        uri="sip:"+username+"@"+realm;
        this.fromAddress = from_url;
        this.contactAddress = new NameAddress(
        		"sip:"+username+"@"+sipProvider.getViaAddress()); //+":"+sipProvider.getPort()
        		
        this.sipURL = registrar;
		this.listener = listener;
		this.qop = null;
		this.attempts = 0;
       // Log.d("RegisterAgent","IPv4="+sip_provider.getInterfaceAddress().toString());
	}
	
	public RegisterAgent(String username, String password, String realm){
			uri="sip:"+username+"@"+realm;
				SipStack.init(null);
		        SipStack.debug_level =0;
		        //SipStack.log_path = "~/d/log";
		        //int expire_time = 1500;
		     SipStack.max_retransmission_timeout = SipStack.default_expires;
		     SipStack.default_transport_protocols = new String[1];
		     SipStack.default_transport_protocols[0] = "udp";
		     SipStack.ua_info="AndroSIP";
		        sipProvider = new SipProvider(
		                IpAddress.getLocalHostAddress().toString(),11111);  	
        this.username=username;
        this.password=password;
        this.realm=realm;
        this.toAddress = new NameAddress(
               uri);
        
        this.fromAddress = new NameAddress(
                uri);
        this.contactAddress = new NameAddress(
        		"sip:"+username+"@"+sipProvider.getViaAddress()); //+":"+sipProvider.getPort()
        
        this.sipURL = new SipURL("sip:"+realm);

    Log.d("RegisterAgent","IPv4="+IpAddress.getLocalHostAddress().toString());
	}

	public void halt() {
		this.listener = null;
	}

	TransactionClient t;
	
	public void register(){
		attempts = 0;

		if (sipProvider==null)
			Log.d("RegisterAgent","sipProvider=null");
		else if (sipURL==null)
			Log.d("RegisterAgent","sipURL=null");
		else if (toAddress==null)
			Log.d("RegisterAgent","toAddress=null");
		else if (fromAddress==null)
			Log.d("RegisterAgent","fromAddress=null");
		else if (contactAddress==null)
		Log.d("RegisterAgent","contactAddress=null");

	   if (status!=REGISTERED){
		  Message rMsg = MessageFactory.createRegisterRequest(
				  sipProvider,
				  sipURL,
				  toAddress,
				  fromAddress,
				  contactAddress);
		  //rMsg.setRoutes(new MultipleHeader());
		  rMsg.setExpiresHeader(new ExpiresHeader(SipStack.default_expires));
		  Log.d("RegisterAgent", "rMsg=" + rMsg.toString());
		  t = new TransactionClient(sipProvider,rMsg,this);
		  t.request();
			status = REGISTERING;
	   }
	}

	@Override
	public void onTransFailureResponse(TransactionClient transClt, Message resp) {
		// TODO complete other 4xx responses
		int code = resp.getStatusLine().getCode();
		Log.d("RegisterAgent", "AltResp code="+code+" reason="+resp.getStatusLine().getReason());

		boolean processAuthenticationResponse = false;
		
		if (code==401){  
			
			if (resp.hasWwwAuthenticateHeader())
            {	         
				Message temp = transClt.getRequestMessage();

				String nonce = resp.getWwwAuthenticateHeader().getNonceParam();
				realm = resp.getWwwAuthenticateHeader().getRealmParam();

				AuthorizationHeader ah = new AuthorizationHeader("Digest");
				temp.setCSeqHeader(temp.getCSeqHeader().incSequenceNumber());
				ViaHeader vh=temp.getViaHeader();
				String newbranch = SipProvider.pickBranch();
				vh.setBranch(newbranch);
				temp.removeViaHeader();
				temp.addViaHeader(vh);

				ah.addUsernameParam(username);
				ah.addAlgorithParam("MD5");
				ah.addRealmParam(realm);
				ah.addNonceParam(nonce);
				ah.addUriParam(uri);

				DigestAuthentication x=new DigestAuthentication(resp.getTransactionMethod(),
						ah, null, password);

				String response = x.getResponse();

				ah.addResponseParam(response);

				temp.setAuthorizationHeader(ah);

				TransactionClient t = new TransactionClient(sipProvider,temp,this);
				t.request();
				processAuthenticationResponse = true;
            }
			 
           
		}
		if (code==407){
			Log.d("RegisterAgent","proxy autho");
		}
		if (code==403){
			Log.d("","");
		}
		if (code==403){
			Log.d("","");
		}

		if (transClt.getTransactionMethod().equals(SipMethods.REGISTER)) {
			StatusLine statusLine = resp.getStatusLine();
			String result = statusLine.getCode() + " " + statusLine.getReason();
			//if (!processAuthenticationResponse(transClt, resp, code)) {
			if(!processAuthenticationResponse) {
				if (status == REGISTERING) {
					status = UNREGISTERED;
					if (listener != null) {
						listener.onUaRegistrationFailure(this, fromAddress, toAddress, result);
					}
				}
			}
			//}
		}

	}
	
		public void deregister(){
			/************************************************************************
			 * Alternatively, when your Phone gets killed, it can as well send a
		     * Deregister request as part of the close up tasks.
		     * For DEREGISTER Request, Contains the following header field/values.
		     * Expires: 0 
		     * that's the trick
			 ************************************************************************/
		if (status==REGISTERED) {
  
        Message rMsg;
        
        rMsg = MessageFactory.createRegisterRequest(
                sipProvider, 
                sipURL,
                toAddress, 
                fromAddress, 
                contactAddress);
        
        rMsg.setExpiresHeader(new ExpiresHeader(0));
        Log.d("RegisterAgent","rMsg="+rMsg.toString());
        TransactionClient tC = new TransactionClient(sipProvider,rMsg,this);
        tC.request();
		}
		
	}
	
	
	@Override
	public void onTransProvisionalResponse(TransactionClient arg0, Message arg1) {
		
	//	Log.d("RegisterAgent", "Tryin");
			status = REGISTERING;
	}

	@Override
	public void onTransSuccessResponse(TransactionClient arg0, Message arg1) {
		if (arg0.getTransactionMethod().equals(SipMethods.REGISTER)) {
			StatusLine statusLine = arg1.getStatusLine();
			String result = statusLine.getCode() + " " + statusLine.getReason();
			if (status == REGISTERING) {
				status = REGISTERED;
				if (listener != null) {
					listener.onUaRegistrationSuccess(this, fromAddress, toAddress, result);
				}
			}
		}

	}

	@Override
	public void onTransTimeout(TransactionClient arg0) {
		// TODO Auto-generated method stub
		Log.d("RegisterAgent", "RegistrationTimeOut");
		status = UNREGISTERED;
		reason = "TimeOut";

		if (arg0 == null) return;
		if (arg0.getTransactionMethod().equals(SipMethods.REGISTER)) {
			if (status == REGISTERING) {
				status = UNREGISTERED;
				if (listener != null) {
					listener.onUaRegistrationFailure(this, fromAddress, toAddress, reason);
				}
			}
		}
		
	}

	private boolean generateRequestWithProxyAuthorizationheader(
			Message resp, Message req){
		if(resp.hasProxyAuthenticateHeader()
				&& resp.getProxyAuthenticateHeader().getRealmParam()
				.length() > 0){
			realm = resp.getProxyAuthenticateHeader().getRealmParam();
			ProxyAuthenticateHeader pah = resp.getProxyAuthenticateHeader();
			String qop_options = pah.getQopOptionsParam();

			//printLog("DEBUG: qop-options: " + qop_options, LogLevel.MEDIUM);

			qop = (qop_options != null) ? "auth" : null;

			ProxyAuthorizationHeader ah = (new DigestAuthentication(req.getTransactionMethod(), req.getRequestLine().getAddress().toString(), pah, qop, null, 0, null, username, password))
					.getProxyAuthorizationHeader();
			req.setProxyAuthorizationHeader(ah);

			return true;
		}
		return false;
	}

	private boolean generateRequestWithWwwAuthorizationheader(
			Message resp, Message req){
		if(resp.hasWwwAuthenticateHeader()
				&& resp.getWwwAuthenticateHeader().getRealmParam()
				.length() > 0){
			realm = resp.getWwwAuthenticateHeader().getRealmParam();
			WwwAuthenticateHeader wah = resp.getWwwAuthenticateHeader();
			String qop_options = wah.getQopOptionsParam();

			qop = (qop_options != null) ? "auth" : null;

			AuthorizationHeader ah = (new DigestAuthentication(req.getTransactionMethod(), req.getRequestLine().getAddress().toString(), wah, qop, null, 0, null, username, password))
					.getAuthorizationHeader();
			req.setAuthorizationHeader(ah);
			return true;
		}
		return false;
	}

	private boolean handleAuthentication(int respCode, Message resp,
										 Message req) {
		switch (respCode) {
			case 407:
				return generateRequestWithProxyAuthorizationheader(resp, req);
			case 401:
				return generateRequestWithWwwAuthorizationheader(resp, req);
		}
		return false;
	}


	private boolean processAuthenticationResponse(TransactionClient transaction,
												  Message resp, int respCode){
		if (attempts < MAX_ATTEMPTS){
			attempts++;
			Message req = transaction.getRequestMessage();
			req.setCSeqHeader(req.getCSeqHeader().incSequenceNumber());
			ViaHeader vh=req.getViaHeader();
			String newbranch = SipProvider.pickBranch();
			vh.setBranch(newbranch);
			req.removeViaHeader();
			req.addViaHeader(vh);

			if (handleAuthentication(respCode, resp, req)) {
				t = new TransactionClient(sipProvider, req, this);

				t.request();
				return true;
			}
		}
		return false;
	}



}
