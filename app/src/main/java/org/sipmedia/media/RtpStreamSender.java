package org.sipmedia.media;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Random;

import org.sipmedia.net.RtpPacket;
import org.sipmedia.net.RtpSocket;
import org.sipmedia.net.SipSocket;
import org.sipmedia.codecs.Codecs;

import org.sipmedia.manage.Receiver;
import org.sipmedia.manage.UA;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;

/**
 * RtpStreamSender is a generic stream sender. It takes an InputStream and sends
 * it through RTP.
 */
public class RtpStreamSender extends Thread {
	/** Whether working in debug mode. */
	public static boolean DEBUG = true;

	/** The RtpSocket */
	RtpSocket rtp_socket = null;

	/** Payload type */
	Codecs.Map p_type;

	/** Number of frame per second */
	int frame_rate;

	/** Number of bytes per frame */
	int frame_size;

	/**
	 * Whether it works synchronously with a local clock, or it it acts as slave
	 * of the InputStream
	 */
	boolean do_sync = true;

	/**
	 * Synchronization correction value, in milliseconds. It accellarates the
	 * sending rate respect to the nominal value, in order to compensate program
	 * latencies.
	 */
	int sync_adj = 0;

	/** Whether it is running */
	boolean running = false;
	boolean muted = false;
	
	//DTMF change
	char dtmf = 0; 
	int dtmf_payload_type = 101;
	
	private static HashMap<Character, Byte> rtpEventMap = new HashMap<Character,Byte>(){/**
		 * 
		 */
		private static final long serialVersionUID = -8122219298547359613L;

	{
		put('0',(byte)0);
		put('1',(byte)1);
		put('2',(byte)2);
		put('3',(byte)3);
		put('4',(byte)4);
		put('5',(byte)5);
		put('6',(byte)6);
		put('7',(byte)7);
		put('8',(byte)8);
		put('9',(byte)9);
		put('*',(byte)10);
		put('#',(byte)11);
		put('A',(byte)12);
		put('B',(byte)13);
		put('C',(byte)14);
		put('D',(byte)15);
	}};
	//DTMF change 
	
	/**
	 * Constructs a RtpStreamSender.
	 *
	 * @param do_sync
	 *            whether time synchronization must be performed by the
	 *            RtpStreamSender, or it is performed by the InputStream (e.g.
	 *            the system audio input)
	 * @param payload_type
	 *            the payload type
	 * @param frame_rate
	 *            the frame rate, i.e. the number of frames that should be sent
	 *            per second; it is used to calculate the nominal packet time
	 *            and,in case of do_sync==true, the next departure time
	 * @param frame_size
	 *            the size of the payload
	 * @param src_socket
	 *            the socket used to send the RTP packet
	 * @param dest_addr
	 *            the destination address
	 * @param dest_port
	 *            the destination port
	 */
	public RtpStreamSender(boolean do_sync, Codecs.Map payload_type,
			       long frame_rate, int frame_size,
			       SipSocket src_socket, String dest_addr,
			       int dest_port) {
		init(do_sync, payload_type, frame_rate, frame_size,
				src_socket, dest_addr, dest_port);
	}

	/** Inits the RtpStreamSender */
	private void init(boolean do_sync, Codecs.Map payload_type,
					  long frame_rate, int frame_size,
					  SipSocket src_socket, String dest_addr,
					  int dest_port) {
		this.p_type = payload_type;
		this.frame_rate = (int)frame_rate;
		if (Receiver.getServerName().equals(Receiver.DEFAULT_SERVER))
			switch (payload_type.codec.number()) {
			case 0:
			case 8:
				this.frame_size = 1024;
				break;
			case 9:
				this.frame_size = 960;
				break;
			default:
				this.frame_size = frame_size;
				break;
			}
		else
			this.frame_size = frame_size;
		this.do_sync = do_sync;
		try {
			rtp_socket = new RtpSocket(src_socket, InetAddress
					.getByName(dest_addr), dest_port);
		} catch (Exception e) {
		}
	}

	/** Sets the synchronization adjustment time (in milliseconds). */
	public void setSyncAdj(int millisecs) {
		sync_adj = millisecs;
	}

	/** Whether is running */
	public boolean isRunning() {
		return running;
	}
	
	public boolean mute() {
		return muted = !muted;
	}

	public static int delay = 0;
	
	/** Stops running */
	public void halt() {
		running = false;
	}

	Random random;
	double smin = 200,s;
	int nearend;
	
	void calc(short[] lin,int off,int len) {
		int i,j;
		double sm = 30000,r;
		
		for (i = 0; i < len; i += 5) {
			j = lin[i+off];
			s = 0.03*Math.abs(j) + 0.97*s;
			if (s < sm) sm = s;
			if (s > smin) nearend = 3000*mu/5;
			else if (nearend > 0) nearend--;
		}
		r = (double)len/(100000*mu);
		smin = sm*r + smin*(1-r);
	}

	void calc1(short[] lin,int off,int len) {
		int i,j;
		
		for (i = 0; i < len; i++) {
			j = lin[i+off];
			lin[i+off] = (short)(j>>1);
		}
	}

	void calc5(short[] lin,int off,int len) {
		int i,j;
		
		for (i = 0; i < len; i++) {
			j = lin[i+off];
			if (j > 16350)
				lin[i+off] = 16350<<1;
			else if (j < -16350)
				lin[i+off] = -16350<<1;
			else
				lin[i+off] = (short)(j<<1);
		}
	}

	void calc10(short[] lin,int off,int len) {
		int i,j;
		
		for (i = 0; i < len; i++) {
			j = lin[i+off];
			if (j > 8150)
				lin[i+off] = 8150<<2;
			else if (j < -8150)
				lin[i+off] = -8150<<2;
			else
				lin[i+off] = (short)(j<<2);
		}
	}

	void noise(short[] lin,int off,int len,double power) {
		int i,r = (int)(power*2);
		short ran;

		if (r == 0) r = 1;
		for (i = 0; i < len; i += 4) {
			ran = (short)(random.nextInt(r*2)-r);
			lin[i+off] = ran;
			lin[i+off+1] = ran;
			lin[i+off+2] = ran;
			lin[i+off+3] = ran;
		}
	}
	
	public static int m;
	int mu;
	
	/** Runs it in a new Thread. */
	public void run() {
		try {
			if (rtp_socket == null)
				return;
			android.util.Log.d("RtpStreamSender", "start thread");
			int seqn = 0;
			long time = 0;
			double p = 0;
			boolean improve = Receiver.getImprove();
			int micgain = (int) (Receiver.getMicGain() * 10);
			long last_tx_time = 0;
			long next_tx_delay;
			long now;
			running = true;
			m = 1;
			int dtframesize = 4;

			android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
			mu = p_type.codec.samp_rate() / 8000;
			int min = AudioRecord.getMinBufferSize(p_type.codec.samp_rate(),
					AudioFormat.CHANNEL_IN_MONO,
					AudioFormat.ENCODING_PCM_16BIT);
			android.util.Log.d("RtpStreamSender", "min buff size: " + String.valueOf(min));
			if (min < 4096) {
				min = min * 20;
				//min = 4096 * 3 / 2;
				if (min <= 2048 && frame_size == 1024) frame_size /= 2;
			} else if (min == 4096) {
				min *= 3 / 2;
				if (frame_size == 960) frame_size = 320;
			} else {
				if (frame_size == 960) frame_size = 320;
				if (frame_size == 1024) frame_size *= 2;
			}
			frame_rate = p_type.codec.samp_rate() / frame_size;
			long frame_period = 1000 / frame_rate;
			frame_rate *= 1.5;
			byte[] buffer = new byte[frame_size + 12];
			RtpPacket rtp_packet = new RtpPacket(buffer, 0);
			rtp_packet.setPayloadType(p_type.number);

			android.util.Log.d("RtpStreamSender", "buff size: " + String.valueOf(min));
			AudioRecord record = new AudioRecord(MediaRecorder.AudioSource.MIC, p_type.codec.samp_rate(), AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
					min);

			short[] lin = new short[frame_size * (frame_rate + 1)];
			int num, ring = 0, pos;
			random = new Random();
			InputStream alerting = null;
			try {
				alerting = Receiver.mContext.getAssets().open("alerting");
			} catch (IOException e2) {
			}
			p_type.codec.init();
			record.startRecording();
			while (running) {
				if (muted || Receiver.call_state == UA.UA_STATE_HOLD) {
					if (Receiver.call_state == UA.UA_STATE_HOLD)
						RtpStreamReceiver.restoreMode();
					record.stop();
					while (running && (muted || Receiver.call_state == UA.UA_STATE_HOLD)) {
						try {
							sleep(1000);
						} catch (InterruptedException e1) {
						}
					}
					record.startRecording();
				}
				//DTMF change start
				if (dtmf != 0) {
					record.stop();
					byte[] dtmfbuf = new byte[dtframesize + 12];
					RtpPacket dt_packet = new RtpPacket(dtmfbuf, 0);
					dt_packet.setPayloadType(dtmf_payload_type);
					dt_packet.setPayloadLength(dtframesize);
					dt_packet.setSscr(rtp_packet.getSscr());
					long dttime = time;
					int duration;

					for (int i = 0; i < 6; i++) {
						time += 160;
						duration = (int) (time - dttime);
						dt_packet.setSequenceNumber(seqn++);
						dt_packet.setTimestamp(dttime);
						dtmfbuf[12] = rtpEventMap.get(dtmf);
						dtmfbuf[13] = (byte) 0x0a;
						dtmfbuf[14] = (byte) (duration >> 8);
						dtmfbuf[15] = (byte) duration;
						try {
							rtp_socket.send(dt_packet);
							sleep(20);
						} catch (IOException e1) {
						} catch (InterruptedException e1) {
						}
					}
					for (int i = 0; i < 3; i++) {
						duration = (int) (time - dttime);
						dt_packet.setSequenceNumber(seqn);
						dt_packet.setTimestamp(dttime);
						dtmfbuf[12] = rtpEventMap.get(dtmf);
						dtmfbuf[13] = (byte) 0x8a;
						dtmfbuf[14] = (byte) (duration >> 8);
						dtmfbuf[15] = (byte) duration;
						try {
							rtp_socket.send(dt_packet);
						} catch (IOException e1) {
						}
					}
					time += 160;
					seqn++;
					dtmf = 0;
					record.startRecording();
				}
				//DTMF change end
				if (frame_size < 480) {
					now = System.currentTimeMillis();
					next_tx_delay = frame_period - (now - last_tx_time);
					last_tx_time = now;
					if (next_tx_delay > 0) {
						try {
							sleep(next_tx_delay);
						} catch (InterruptedException e1) {
						}
						last_tx_time += next_tx_delay - sync_adj;
					}
				}
				pos = (ring + delay * frame_rate * frame_size) % (frame_size * (frame_rate + 1));
				android.util.Log.d("RtpStreamSender", "start record read pos: " + String.valueOf(pos) + ", frame_size: " + String.valueOf(frame_size) + ", lin size: " + String.valueOf(lin.length));
				num = record.read(lin, pos, frame_size);
				android.util.Log.d("RtpStreamSender", "end record read");
				if (num <= 0)
					continue;
				if (!p_type.codec.isValid())
					continue;

				if (RtpStreamReceiver.speakermode == AudioManager.MODE_NORMAL) {
					calc(lin, pos, num);
					if (RtpStreamReceiver.nearend != 0)
						noise(lin, pos, num, p / 2);
					else if (nearend == 0)
						p = 0.9 * p + 0.1 * s;
				} else switch (micgain) {
					case 1:
						calc1(lin, pos, num);
						break;
					case 5:
						calc5(lin, pos, num);
						break;
					case 10:
						calc10(lin, pos, num);
						break;
				}
				if (Receiver.call_state != UA.UA_STATE_INCALL &&
						Receiver.call_state != UA.UA_STATE_OUTGOING_CALL && alerting != null) {
					try {
						if (alerting.available() < num / mu)
							alerting.reset();
						alerting.read(buffer, 12, num / mu);
					} catch (IOException e) {
					}
					if (p_type.codec.number() != 8) {
						G711.alaw2linear(buffer, lin, num, mu);
						num = p_type.codec.encode(lin, 0, buffer, num);
					}
				} else {
					num = p_type.codec.encode(lin, ring % (frame_size * (frame_rate + 1)), buffer, num);
				}
				ring += frame_size;
				rtp_packet.setSequenceNumber(seqn++);
				rtp_packet.setTimestamp(time);
				rtp_packet.setPayloadLength(num);
				try {
					rtp_socket.send(rtp_packet);
					if (m == 2)
						rtp_socket.send(rtp_packet);
				} catch (IOException e) {
				}
				if (p_type.codec.number() == 9)
					time += frame_size / 2;
				else
					time += frame_size;
				if (improve && delay == 0 && RtpStreamReceiver.good != 0 &&
						RtpStreamReceiver.loss / RtpStreamReceiver.good > 0.01 &&
						(p_type.codec.number() == 0 || p_type.codec.number() == 8 || p_type.codec.number() == 9))
					m = 2;
				else
					m = 1;
			}
			if (Integer.parseInt(Build.VERSION.SDK) < 5)
				while (RtpStreamReceiver.getMode() == AudioManager.MODE_IN_CALL)
					try {
						sleep(1000);
					} catch (InterruptedException e) {
					}
			record.stop();
			record.release();
			m = 0;

			p_type.codec.close();
			rtp_socket.close();
			rtp_socket = null;
		}catch(Exception ex){
			ex.printStackTrace();
		}
	}

	/** Set RTP payload type of outband DTMF packets. **/  
	public void setDTMFpayloadType(int payload_type){
		dtmf_payload_type = payload_type; 
	}
	
	/** Send outband DTMF packets */
	public void sendDTMF(char c) {
		dtmf = c; // will be set to 0 after sending tones
	}
	//DTMF change
}
