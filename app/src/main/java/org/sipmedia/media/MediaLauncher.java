package org.sipmedia.media;

/** Interface for classes that start media application such as for audio or video */
public interface MediaLauncher {
	/** Starts media application */
	public boolean startMedia();

	/** Stops media application */
	public boolean stopMedia();
	
	public boolean muteMedia();
	public int speakerMedia(int mode);

	public boolean sendDTMF(char c);
}