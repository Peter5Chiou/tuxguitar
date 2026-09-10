package app.tuxguitar.io.simpleinput;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 和弦字典：開放和弦 + 簡單封閉推移。
 * frets[0]=實際弦6(最粗) … frets[5]=實際弦1(最細)，-1=不發音。
 */
public class SimpleInputChordDictionary {

	public static class ChordInfo {
		public String name;
		public int[] frets = new int[6];
		public int rootString; // 實際弦編號 1~6
		public List<Integer> soundingStrings() {
			List<Integer> strings = new ArrayList<>();
			// frets 索引：0=弦6 … 5=弦1
			for (int i = 0; i < 6; i++) {
				if (frets[i] >= 0) {
					strings.add(6 - i);
				}
			}
			return strings;
		}
	}

	private final Map<String, ChordInfo> chords = new LinkedHashMap<>();

	public SimpleInputChordDictionary() {
		// C: x32010
		add("C", new int[]{-1, 3, 2, 0, 1, 0}, 5);
		// D: xx0232
		add("D", new int[]{-1, -1, 0, 2, 3, 2}, 4);
		// E: 022100
		add("E", new int[]{0, 2, 2, 1, 0, 0}, 6);
		// Em: 022000
		add("Em", new int[]{0, 2, 2, 0, 0, 0}, 6);
		// Am: x02210
		add("Am", new int[]{-1, 0, 2, 2, 1, 0}, 5);
		// A: x02220
		add("A", new int[]{-1, 0, 2, 2, 2, 0}, 5);
		// A7: x02020
		add("A7", new int[]{-1, 0, 2, 0, 2, 0}, 5);
		// D7: xx0212
		add("D7", new int[]{-1, -1, 0, 2, 1, 2}, 4);
		// E7: 020100
		add("E7", new int[]{0, 2, 0, 1, 0, 0}, 6);
		// G: 320003
		add("G", new int[]{3, 2, 0, 0, 0, 3}, 6);
		// G7: 320001
		add("G7", new int[]{3, 2, 0, 0, 0, 1}, 6);
		// F: 133211 (小封閉)
		add("F", new int[]{1, 3, 3, 2, 1, 1}, 6);
		// Cadd9: x32033
		add("Cadd9", new int[]{-1, 3, 2, 0, 3, 3}, 5);
		// Dm: xx0231
		add("Dm", new int[]{-1, -1, 0, 2, 3, 1}, 4);
		// Asus4: x02230
		add("Asus4", new int[]{-1, 0, 2, 2, 3, 0}, 5);
		// Dsus4: xx0233
		add("Dsus4", new int[]{-1, -1, 0, 2, 3, 3}, 4);
		// B7: x21202
		add("B7", new int[]{-1, 2, 1, 2, 0, 2}, 5);
	}

	private void add(String name, int[] frets, int rootString) {
		ChordInfo info = new ChordInfo();
		info.name = name;
		info.frets = frets.clone();
		info.rootString = rootString;
		chords.put(name, info);
	}

	/** 查和弦；未命中時嘗試封閉推移（E/Em/A 形） */
	public ChordInfo find(String name) {
		ChordInfo info = chords.get(name);
		if (info != null) {
			return info;
		}
		return deriveBarreChord(name);
	}

	/** 將和弦按型平移到指定把位；position 以第 1 品為起點。 */
	public ChordInfo atPosition(ChordInfo source, int position) {
		ChordInfo shifted = new ChordInfo();
		shifted.name = source.name;
		shifted.rootString = source.rootString;
		for (int i = 0; i < source.frets.length; i++) {
			shifted.frets[i] = source.frets[i] < 0 ? -1 : source.frets[i] + position;
		}
		return shifted;
	}

	/** 支援 E/Em/A/Am 形升降半音推移，如 F#m、Bb、C#m、G#7 等。
	 *  同時嘗試 E 形（根音弦6）與 A 形（根音弦5），取把位較低者。 */
	private ChordInfo deriveBarreChord(String name) {
		java.util.regex.Matcher m = java.util.regex.Pattern
			.compile("^([A-G])([#b]?)(m|maj|min|7|sus2|sus4|dim|aug)?$")
			.matcher(name);
		if (!m.matches()) {
			return null;
		}
		String suffix = m.group(3) == null ? "" : m.group(3);
		Integer semitone = semitoneOf(m.group(1) + (m.group(2) == null ? "" : m.group(2)));
		if (semitone == null) {
			return null;
		}
		boolean isMajor = suffix.isEmpty() || suffix.equals("maj");
		boolean isMinor = suffix.equals("m") || suffix.equals("min");
		boolean isSeven = suffix.equals("7");
		if (!isMajor && !isMinor && !isSeven) {
			return null;
		}

		ChordInfo best = null;
		int bestFret = Integer.MAX_VALUE;

		// E 形（根音弦 6）：開放 E/Em/E7 基型
		int eFret = (semitone - semitone("E") + 12) % 12;
		if (eFret > 0) {
			int[] shape = isMinor ? new int[]{0, 2, 2, 0, 0, 0}
				: isSeven ? new int[]{0, 2, 0, 1, 0, 0}
				: new int[]{0, 2, 2, 1, 0, 0};
			ChordInfo cand = shifted(name, shape, eFret, 6);
			if (eFret < bestFret) {
				best = cand;
				bestFret = eFret;
			}
		}
		// A 形（根音弦 5）：開放 A/Am/A7 基型
		int aFret = (semitone - semitone("A") + 12) % 12;
		if (aFret > 0) {
			int[] shape = isMinor ? new int[]{-1, 0, 2, 2, 1, 0}
				: isSeven ? new int[]{-1, 0, 2, 0, 2, 0}
				: new int[]{-1, 0, 2, 2, 2, 0};
			ChordInfo cand = shifted(name, shape, aFret, 5);
			if (aFret < bestFret) {
				best = cand;
				bestFret = aFret;
			}
		}
		return best;
	}

	private ChordInfo shifted(String name, int[] openShape, int fret, int rootString) {
		ChordInfo info = new ChordInfo();
		info.name = name;
		info.rootString = rootString;
		for (int i = 0; i < 6; i++) {
			info.frets[i] = openShape[i] < 0 ? -1 : openShape[i] + fret;
		}
		return info;
	}

	private Integer semitoneOf(String note) {
		return semitone(note);
	}

	private Integer semitone(String note) {
		// C=0 D=2 E=4 F=5 G=7 A=9 B=11
		int value;
		switch (note.charAt(0)) {
			case 'C': value = 0; break;
			case 'D': value = 2; break;
			case 'E': value = 4; break;
			case 'F': value = 5; break;
			case 'G': value = 7; break;
			case 'A': value = 9; break;
			case 'B': value = 11; break;
			default: return null;
		}
		if (note.length() > 1) {
			if (note.charAt(1) == '#') {
				value += 1;
			} else if (note.charAt(1) == 'b') {
				value -= 1;
			}
		}
		return (value + 12) % 12;
	}
}
