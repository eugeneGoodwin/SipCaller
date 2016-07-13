package org.sipmedia.codecs;

import java.util.HashMap;
import java.util.Vector;

import org.zoolu.sdp.MediaField;
import org.zoolu.sdp.SessionDescriptor;
import org.zoolu.sdp.AttributeField;

import org.sipmedia.manage.Receiver;

public class Codecs {
	private static final Vector<Codec> codecs = new Vector<Codec>() {
		/**
		 *
		 */
		private static final long serialVersionUID = 5423816327968842601L;

		{
			add(new alaw());
			add(new ulaw());
		}
	};
	private static final HashMap<Integer, Codec> codecsNumbers;
	private static final HashMap<String, Codec> codecsNames;

	static {
		final int size = codecs.size();
		codecsNumbers = new HashMap<Integer, Codec>(size);
		codecsNames = new HashMap<String, Codec>(size);

		for (Codec c : codecs) {
			codecsNames.put(c.name(), c);
			codecsNumbers.put(c.number(), c);
		}

		String prefs = Receiver.getStringValue(Receiver.PREF_CODECS, Receiver.DEFAULT_CODECS);
		if (prefs == null) {
			String v = "";
			for (Codec c : codecs)
				v = v + c.number() + " ";
			Receiver.setStringValue(Receiver.PREF_CODECS, v);
		} else {
			String[] vals = prefs.split(" ");
			for (String v : vals) {
				try {
					int i = Integer.parseInt(v);
					Codec c = codecsNumbers.get(i);
					/* moves the codec to the end
					 * of the list so we end up
					 * with the new codecs (if
					 * any) at the top and the
					 * remaining ones ordered
					 * according to the user */
					codecs.remove(c);
					codecs.add(c);
				} catch (Exception e) {
					// do nothing (expecting
					// NumberFormatException and
					// indexnot found
				}
			}
		}
	}

	public static Codec get(int key) {
		return codecsNumbers.get(key);
	}

	public static Codec getName(String name) {
		return codecsNames.get(name);
	}

	public static void check() {
		HashMap<String, String> old = new HashMap<String, String>(codecs.size());

		for (Codec c : codecs) {
			old.put(c.name(), c.getValue());
			if (!c.isLoaded()) {
				Receiver.setStringValue(c.key(), "never");
			}
		}

		for (Codec c : codecs)
			if (!old.get(c.name()).equals("never")) {
				c.init();
				if (c.isLoaded()) {
					Receiver.setStringValue(c.key(), old.get(c.name()));
					c.init();
				} else
					c.fail();
			}
	}

	public static int[] getCodecs() {
		Vector<Integer> v = new Vector<Integer>(codecs.size());

		for (Codec c : codecs) {
			if (!c.isValid())
				continue;
			v.add(c.number());
		}
		int i[] = new int[v.size()];
		for (int j = 0; j < i.length; j++)
			i[j] = v.elementAt(j);
		return i;
	}

	public static class Map {
		public int number;
		public Codec codec;
		Vector<Integer> numbers;
		Vector<Codec> codecs;

		Map(int n, Codec c, Vector<Integer> ns, Vector<Codec> cs) {
			number = n;
			codec = c;
			numbers = ns;
			codecs = cs;
		}

		public boolean change(int n) {
			int i = numbers.indexOf(n);

			if (i >= 0 && codecs.elementAt(i) != null) {
				codec.close();
				number = n;
				codec = codecs.elementAt(i);
				return true;
			}
			return false;
		}

		public String toString() {
			return "Codecs.Map { " + number + ": " + codec + "}";
		}
	}

	;

	public static Map getCodec(SessionDescriptor offers) {
		MediaField m = offers.getMediaDescriptor("audio").getMedia();
		if (m == null)
			return null;

		String proto = m.getTransport();
		//see http://tools.ietf.org/html/rfc4566#page-22, paragraph 5.14, <fmt> description 
		if (proto.equals("RTP/AVP") || proto.equals("RTP/SAVP")) {
			Vector<String> formats = m.getFormatList();
			Vector<String> names = new Vector<String>(formats.size());
			Vector<Integer> numbers = new Vector<Integer>(formats.size());
			Vector<Codec> codecmap = new Vector<Codec>(formats.size());

			//add all avail formats with empty names
			for (String fmt : formats) {
				try {
					int number = Integer.parseInt(fmt);
					numbers.add(number);
					names.add("");
					codecmap.add(null);
				} catch (NumberFormatException e) {
					// continue ... remote sent bogus rtp setting
				}
			}
			;

			//if we have attrs for format -> set name
			Vector<AttributeField> attrs = offers.getMediaDescriptor("audio").getAttributes("rtpmap");
			for (AttributeField a : attrs) {
				String s = a.getValue();
				// skip over "rtpmap:"
				s = s.substring(7, s.indexOf("/"));
				int i = s.indexOf(" ");
				try {
					String name = s.substring(i + 1);
					int number = Integer.parseInt(s.substring(0, i));
					int index = numbers.indexOf(number);
					if (index >= 0)
						names.set(index, name.toLowerCase());
				} catch (NumberFormatException e) {
					// continue ... remote sent bogus rtp setting
				}
			}

			Codec codec = null;
			int index = formats.size() + 1;

			for (Codec c : codecs) {
				if (!c.isValid())
					continue;

				//search current codec in offers by name
				int i = names.indexOf(c.userName().toLowerCase());
				if (i >= 0) {
					codecmap.set(i, c);
					if ((codec == null) || (i < index)) {
						codec = c;
						index = i;
						continue;
					}
				}

				//search current codec in offers by number
				i = numbers.indexOf(c.number());
				if (i >= 0) {
					if (names.elementAt(i).equals("")) {
						codecmap.set(i, c);
						if ((codec == null) || (i < index)) {
							//fmt number has no attr with name
							codec = c;
							index = i;
							continue;
						}
					}
				}
			}
			if (codec != null)
				return new Map(numbers.elementAt(index), codec, numbers, codecmap);
			else
				// no codec found ... we can't talk
				return null;
		} else
			/*formats of other protocols not supported yet*/
			return null;
	}

}
