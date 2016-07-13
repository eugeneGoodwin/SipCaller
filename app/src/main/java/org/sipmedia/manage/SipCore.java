package org.sipmedia.manage;

import org.zoolu.net.IpAddress;
import org.zoolu.sip.address.NameAddress;
import org.zoolu.sip.address.SipURL;
import org.zoolu.sip.provider.SipProvider;
import org.zoolu.sip.provider.SipStack;

import android.content.Context;
import android.util.Log;

public class SipCore implements RegisterAgentListener {
	private RegisterAgent rA;
	private SipProvider sipProvider;
	public UA ua;
	private String username = null;
	private String passwd = null;
	private String realm = null;
	private String contact_url = null;
	private String from_url = null;
	
	public void listen(){
		if (ua!=null && sipProvider!=null)
		ua.listen();
	}
	
	public void acceptCall(){
		Log.d("SipCore","accepting call");
		ua.accept();
	}
	
	public void rejectCall(){
		ua.reject();
	}
	
	public boolean call(NameAddress callee){
		return ua.call(callee);
	}
	
	
	public SipCore(){
		System.out.println("+++ SipCore ");
		SipStack.init(null);
        SipStack.debug_level =0;
        SipStack.max_retransmission_timeout = SipStack.default_expires;
        SipStack.default_transport_protocols = new String[1];
        SipStack.default_transport_protocols[0] = "udp";
        SipStack.ua_info="SIP";
	}
	
	public void init(Context context){
		Log.d("SipCore", "init sipCore(context)");
		sipProvider = new SipProvider(
	                IpAddress.getLocalHostAddress().toString(),0);
		if (sipProvider==null) System.out.println("+++ SipCore sipProvider=null");
		//this.ua=new UA();
		getCredentials(context);
		String uri = "sip:" + username + "@" + realm;

		UserAgentProfile user_profile = new UserAgentProfile(null);
		user_profile.username = username;
		user_profile.passwd = passwd;
		user_profile.realm = realm;

		//this.rA=new RegisterAgent(username,passwd,realm);
		this.rA= new RegisterAgent(sipProvider, new SipURL("sip:"+realm), new NameAddress(uri), new NameAddress(uri),username,realm,passwd, this);
		this.ua=new UA(sipProvider, user_profile);
	}
	
	public void init(String user,String password,String domain){
		Log.d("SipCore", "init(u,p,d)");
		sipProvider = new SipProvider(
				IpAddress.getLocalHostAddress().toString(),0);
		if (sipProvider==null) Log.d("SipCore", "sipProvider=null");
		//this.ua=new UA();
		//getCredentials(context);
		setCredentials(user, password, domain);
		String uri="sip:"+user+"@"+domain;

		UserAgentProfile user_profile = new UserAgentProfile(null);
		user_profile.username = user;
		user_profile.passwd = password;
		user_profile.realm = domain;

		this.rA= new RegisterAgent(sipProvider, new SipURL("sip:"+realm), new NameAddress(uri),
				new NameAddress(uri),user,domain,password, this);
		this.ua=new UA(sipProvider, user_profile);
		// this.rA= new RegisterAgent(user,domain,password);
	}
	
	public void setCredentials(String user,String password,String domain){
		Log.d("SipCore","setting credentials");
		this.username=user;
		this.passwd=password;
		this.realm=domain;
	}
	
	private void getCredentials(Context context){
		Log.d("SipCore","getting credentials");
		this.username = Receiver.getStringValue(Receiver.PREF_USERNAME, null);
		Log.d("SipCore","username="+username);
		this.realm = Receiver.getStringValue(Receiver.PREF_DOMAIN, null);
		Log.d("SipCore", "realm=" + realm);
		this.passwd = Receiver.getStringValue(Receiver.PREF_PASSWORD, null);
		Log.d("SipCore","pass="+passwd);
	}
	
    private void init_addresses() {
        if (realm == null && contact_url != null)
                realm = new NameAddress(contact_url).getAddress().getHost();
        if (username == null)
                username = (contact_url != null) ? new NameAddress(contact_url)
                                .getAddress().getUserName() : "user";
}
	
    /**
     * Sets contact_url and from_url with transport information. <p/> This
     * method actually sets contact_url and from_url only if they haven't still
     * been explicitly initialized.
     */
    public void initContactAddress(SipProvider sip_provider) { // contact_url
            if (contact_url == null) {
                    contact_url = "sip:" + username + "@"
                                    + sip_provider.getViaAddress();
                    if (sip_provider.getPort() != SipStack.default_port)
                            contact_url += ":" + sip_provider.getPort();
                    if (!sip_provider.getDefaultTransport().equals(
                                    SipProvider.PROTO_UDP))
                            contact_url += ";transport="
                                            + sip_provider.getDefaultTransport();
            }
            // from_url
            if (from_url == null)
				from_url = contact_url;
    }
	
	public boolean register(){
		Log.d("SipCore","registering");
		rA.register();
		return (rA.status==RegisterAgent.REGISTERED);
	}

	public boolean isRegistered() {
		return rA.status==RegisterAgent.REGISTERED;
	}

	public void halt() {
		if (rA != null)
			rA.halt();
		if (ua != null)
			ua.hangup();
		if (sipProvider != null)
			sipProvider.halt();
	}


	public void onUaRegistrationSuccess(RegisterAgent ra, NameAddress target,
										NameAddress contact, String result){
		Receiver.registerSuccess(true);
	}

	/** When a UA failed on (un)registering. */
	public void onUaRegistrationFailure(RegisterAgent ra, NameAddress target,
										NameAddress contact, String result){
		Receiver.registerSuccess(false);
	}
}
