import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

import app.tuxguitar.io.simpleinput.SimpleInputChordDictionary;
import app.tuxguitar.io.simpleinput.SimpleInputFormatException;
import app.tuxguitar.io.simpleinput.SimpleInputParser;
import app.tuxguitar.io.simpleinput.SimpleInputSong;
import app.tuxguitar.io.simpleinput.SimpleStringUtil;
import app.tuxguitar.song.models.TGVelocities;

/**
 * SimpleInput (.strum) 轉 Ample Guitar (Select Mode / Strummer) + Reaper 匯出工具.
 * 輸出：
 *  1. <檔名>.chords (XML 格式和弦庫，供 Ample Guitar Select Mode 載入)
 *  2. <檔名>_ag.mid (標準 MIDI 檔，包含 Chord Keyswitch, Action Keyswitch, S-Strum Time CC #14)
 */
public class AmpleGuitarExporter {

	private static final int PPQ = 480; // ticks per quarter note
	private static final int MAX_SLOTS = 24;

	// Ample Guitar MIDI Keyswitch 常數定義
	// 1. 左手和弦槽位切換 (Slot 1 ~ 24 = AG 的 C1 ~ B2: Note 36 ~ 59)
	public static final int CHORD_SLOT_NOTE_BASE = 35; // Note = 35 + Slot_ID

	// 2. 右手動作觸發 (Ample Guitar Strummer: C4 ~ B4 為手動刷弦區)
	public static final int NOTE_BASS_I = 55;             // G2 - Bass I
	public static final int NOTE_STRUM_DOWN_FULL = 72;     // C4 - Open Down Full
	public static final int NOTE_STRUM_DOWN_TREBLE = 74;   // D4 - Open Down Mid/Treble
	public static final int NOTE_STRUM_UP = 76;            // E4 - Open Up Full

	// 3. 單弦觸發鍵 (Ample Guitar Strummer: D3 ~ B3 為物理第 6~1 弦單音撥弦)
	public static final int KEY_STRING_6 = 62; // D3 (物理第 6 弦)
	public static final int KEY_STRING_5 = 64; // E3 (物理第 5 弦)
	public static final int KEY_STRING_4 = 65; // F3 (物理第 4 弦)
	public static final int KEY_STRING_3 = 67; // G3 (物理第 3 弦)
	public static final int KEY_STRING_2 = 69; // A3 (物理第 2 弦)
	public static final int KEY_STRING_1 = 71; // B3 (物理第 1 弦)

	/** 和弦槽位定義 */
	public static class ChordSlot {
		public int id;          // 1 ~ 24
		public String name;    // 和弦顯示名稱（如 G, Em@3, G[2=3]）
		public String root;    // 如 "G", "C", "F#"
		public String type;    // 如 "Maj", "min", "7", "m7"
		public int pos = 1;    // 把位
		// frets[0]=物理弦6(最粗) ... frets[5]=物理弦1(最細)；-1 表示悶弦
		public int[] frets = new int[6];

		@Override
		public String toString() {
			return String.format("Slot %02d: %-10s [Root=%s, Type=%s, Pos=%d, Frets=%s]",
				id, name, root, type, pos, Arrays.toString(frets));
		}
	}

	/** 和弦槽位管理器 */
	public static class ChordSlotManager {
		private final List<ChordSlot> slots = new ArrayList<>();

		public ChordSlot registerChord(String name, String root, String type, int pos, int[] frets) throws SimpleInputFormatException {
			for (ChordSlot s : slots) {
				if (Arrays.equals(s.frets, frets)) {
					return s; // 已有相同指型，共用 Slot
				}
			}
			if (slots.size() >= MAX_SLOTS) {
				throw new SimpleInputFormatException("Ample Guitar Select Mode 最多支援 24 個和弦槽位，整首歌曲已超出 24 種不同指型！");
			}
			ChordSlot slot = new ChordSlot();
			slot.id = slots.size() + 1;
			slot.name = name;
			slot.root = root;
			slot.type = type;
			slot.pos = pos;
			slot.frets = frets.clone();
			slots.add(slot);
			return slot;
		}

		public List<ChordSlot> getPaddedSlots() {
			List<ChordSlot> padded = new ArrayList<>(slots);
			while (padded.size() < MAX_SLOTS) {
				ChordSlot defaultSlot = new ChordSlot();
				defaultSlot.id = padded.size() + 1;
				defaultSlot.name = "C";
				defaultSlot.root = "C";
				defaultSlot.type = "Maj";
				defaultSlot.pos = 1;
				// C Maj 預設指型: x32010 -> 弦6=-1, 弦5=3, 弦4=2, 弦3=0, 弦2=1, 弦1=0
				defaultSlot.frets = new int[]{-1, 3, 2, 0, 1, 0};
				padded.add(defaultSlot);
			}
			return padded;
		}

		public List<ChordSlot> getRegisteredSlots() {
			return Collections.unmodifiableList(slots);
		}
	}

	/** 解析和弦名稱為 Root 與 Type */
	public static String[] parseRootAndType(String chordName) {
		Matcher m = Pattern.compile("^([A-G][#b]?)(.*)$").matcher(chordName);
		if (!m.matches()) {
			return new String[]{chordName, "Maj"};
		}
		String root = m.group(1);
		String suffix = m.group(2);
		String type;
		if (suffix == null || suffix.isEmpty() || suffix.equalsIgnoreCase("maj")) {
			type = "Maj";
		} else if (suffix.equals("m") || suffix.equalsIgnoreCase("min")) {
			type = "min";
		} else if (suffix.equals("7")) {
			type = "7";
		} else if (suffix.equals("m7") || suffix.equals("min7")) {
			type = "m7";
		} else if (suffix.equals("sus4")) {
			type = "sus4";
		} else if (suffix.equals("add9")) {
			type = "add9";
		} else {
			type = suffix;
		}
		return new String[]{root, type};
	}

	/** 產生 Ample Guitar Select Mode 和弦庫 XML 內容 */
	public static String generateChordsXml(List<ChordSlot> slots, String key) {
		StringBuilder sb = new StringBuilder();
		sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		sb.append("<Chords Author=\"SimpleInput\" Category=\"Custom\" Key=\"")
		  .append(escapeXml(key != null ? key : "C")).append("\">\n");
		for (ChordSlot slot : slots) {
			// Type 固定輸出 "User"，避免 Ample Guitar 載入後把自訂指型改寫成它認為的標準按法
			sb.append(String.format("  <Chord ID=\"%d\" Root=\"%s\" Type=\"User\" Pos=\"%d\">\n",
				slot.id, escapeXml(slot.root), slot.pos));
			for (int s = 6; s >= 1; s--) {
				int fret = slot.frets[6 - s];
				sb.append(String.format("    <String ID=\"%d\" Fret=\"%d\"/>\n", s, fret));
			}
			sb.append("  </Chord>\n");
		}
		sb.append("</Chords>\n");
		return sb.toString();
	}

	private static String escapeXml(String s) {
		if (s == null) return "";
		return s.replace("&", "&amp;")
		        .replace("<", "&lt;")
		        .replace(">", "&gt;")
		        .replace("\"", "&quot;")
		        .replace("'", "&apos;");
	}

	/** 物理弦號轉 Ample Guitar 單弦撥弦 Note Number (D3 ~ B3) */
	public static int realStringToTriggerKey(int realString) {
		switch (realString) {
			case 6: return KEY_STRING_6; // D3 (62) - 物理第 6 弦
			case 5: return KEY_STRING_5; // E3 (64) - 物理第 5 弦
			case 4: return KEY_STRING_4; // F3 (65) - 物理第 4 弦
			case 3: return KEY_STRING_3; // G3 (67) - 物理第 3 弦
			case 2: return KEY_STRING_2; // A3 (69) - 物理第 2 弦
			case 1: return KEY_STRING_1; // B3 (71) - 物理第 1 弦
			default:
				throw new IllegalArgumentException("無效的物理弦號: " + realString);
		}
	}

	/** Ample Guitar Strummer SEQ 1 ~ 8 對應 Keyswitch Note (C3, C#3, D#3, F#3, G#3, A#3, C#4, D#4) */
	public static int seqIndexToNote(int seqIndex) {
		switch (seqIndex) {
			case 1: return 60; // C3
			case 2: return 61; // C#3
			case 3: return 63; // D#3
			case 4: return 66; // F#3
			case 5: return 68; // G#3
			case 6: return 70; // A#3
			case 7: return 73; // C#4
			case 8: return 75; // D#4
			default:
				throw new IllegalArgumentException("無效的 SEQ 編號 (支援 1~8): " + seqIndex);
		}
	}

	/** 力度轉換為 MIDI Velocity (未指定時預設 100) */
	public static int velocityOf(Integer tgVelocity) {
		if (tgVelocity == null) {
			return 100;
		}
		int v = tgVelocity;
		if (v == TGVelocities.PIANO_PIANISSIMO) return 20;   // ppp
		if (v == TGVelocities.PIANISSIMO)       return 35;   // pp
		if (v == TGVelocities.PIANO)             return 50;   // p
		if (v == TGVelocities.MEZZO_PIANO)       return 68;   // mp
		if (v == TGVelocities.MEZZO_FORTE)       return 85;   // mf
		if (v == TGVelocities.FORTE)             return 100;  // f
		if (v == TGVelocities.FORTISSIMO)        return 115;  // ff
		if (v == TGVelocities.FORTE_FORTISSIMO)  return 127;  // fff
		return Math.max(1, Math.min(127, v));
	}

	/** 刷速 (~N) 轉 CC #14 數值 (大幅調低以避免過慢變成琶音) */
	public static int strokeToCC14(Integer strokeDenominator) {
		if (strokeDenominator == null) return 10;
		switch (strokeDenominator) {
			case 64: return 0;   // 極快刷
			case 32: return 5;   // 快速刷
			case 16: return 10;  // 預設標準俐落刷弦 (不拖拍、不變琶音)
			case 8:  return 35;  // 慢刷
			case 4:  return 75;  // 琶音滾弦
			default: return 10;
		}
	}

	/** 刷速 (~N) 轉每弦延遲 Ticks (以 PPQ=480 為準) */
	public static int strokeDelayTicks(Integer strokeDenominator) {
		if (strokeDenominator == null) return 25;
		switch (strokeDenominator) {
			case 64: return 8;
			case 32: return 15;
			case 16: return 25;
			case 8:  return 45;
			case 4:  return 80;
			default: return 25;
		}
	}

	/** 反覆記號線性展開 (Unroll Repeats) */
	public static List<SimpleInputSong.Measure> unrollRepeats(List<SimpleInputSong.Measure> measures) {
		List<SimpleInputSong.Measure> unrolled = new ArrayList<>();
		int repeatStart = -1;
		for (SimpleInputSong.Measure m : measures) {
			if (m.repeatOpen) {
				repeatStart = unrolled.size();
			}
			unrolled.add(m);
			if (m.repeatCount > 0) {
				int repeatEnd = unrolled.size();
				int times = m.repeatCount;
				if (repeatStart >= 0 && repeatStart < repeatEnd) {
					List<SimpleInputSong.Measure> section = new ArrayList<>(unrolled.subList(repeatStart, repeatEnd));
					for (int t = 1; t < times; t++) {
						unrolled.addAll(section);
					}
				}
				repeatStart = -1;
			}
		}
		return unrolled;
	}

	/** 解析和弦指型資訊（支援把位與移調） */
	private static SimpleInputChordDictionary.ChordInfo resolveChordInfo(
			SimpleInputChordDictionary chordDict, String chordName, Integer position, int transposeSemitones)
			throws SimpleInputFormatException {
		if (chordName == null) return null;
		SimpleInputChordDictionary.ChordInfo source = chordDict.find(chordName);
		if (source == null) {
			throw new SimpleInputFormatException("未識別的和弦名稱「" + chordName + "」");
		}
		if (position != null) {
			source = chordDict.atPosition(source, position);
		}
		if (transposeSemitones != 0) {
			String transposedName = chordDict.transposeName(chordName, transposeSemitones);
			if (transposedName == null) {
				throw new SimpleInputFormatException("無法移調和弦「" + chordName + "」");
			}
			SimpleInputChordDictionary.ChordInfo target = chordDict.find(transposedName);
			if (target != null) {
				if (position != null) {
					target = chordDict.atPosition(target, position);
				}
				return target;
			}
			SimpleInputChordDictionary.ChordInfo transposedVoicing = chordDict.transpose(source, transposeSemitones, transposedName);
			if (transposedVoicing == null) {
				throw new SimpleInputFormatException("移調後和弦「" + transposedName + "」超出指型品位範圍");
			}
			return transposedVoicing;
		}
		return source;
	}

	/** 取得移調後的顯示和弦名 */
	private static String resolveChordDisplayName(SimpleInputChordDictionary chordDict, String chordName, int transposeSemitones) {
		if (chordName == null) return null;
		if (transposeSemitones != 0) {
			String t = chordDict.transposeName(chordName, transposeSemitones);
			return t != null ? t : chordName;
		}
		return chordName;
	}

	/** 建立強制品位衍生指型 */
	private static int[] deriveForcedFrets(int[] baseFrets, Map<Integer, Integer> forcedFrets, int transposeSemitones) throws SimpleInputFormatException {
		int[] derived = baseFrets.clone();
		for (Map.Entry<Integer, Integer> entry : forcedFrets.entrySet()) {
			int userString = entry.getKey();
			int fret = entry.getValue();
			if (transposeSemitones != 0) {
				fret += transposeSemitones;
				if (fret < 0 || fret > 24) {
					throw new SimpleInputFormatException("強制品位移調後超出範圍 (0~24 品): " + fret);
				}
			}
			int realString = SimpleStringUtil.toRealString(userString);
			derived[6 - realString] = fret;
		}
		return derived;
	}

	/** 左手和弦切換區間記錄 */
	private static class LeftHandSpan {
		ChordSlot slot;
		long startTick;
		long endTick;

		LeftHandSpan(ChordSlot slot, long startTick, long endTick) {
			this.slot = slot;
			this.startTick = startTick;
			this.endTick = endTick;
		}
	}

	/** 執行匯出工作 */
	public static void export(File inputFile, File outputDir) throws Exception {
		if (!inputFile.exists()) {
			throw new IOException("找不到輸入檔案: " + inputFile.getAbsolutePath());
		}
		if (outputDir == null) {
			outputDir = inputFile.getParentFile();
			if (outputDir == null) outputDir = new File(".");
		}
		if (!outputDir.exists()) {
			outputDir.mkdirs();
		}

		String fileName = inputFile.getName();
		String baseName = fileName.endsWith(".strum") ? fileName.substring(0, fileName.length() - 6) : fileName;
		File chordsOutputFile = new File(outputDir, baseName + ".chords");
		File midiOutputFile = new File(outputDir, baseName + "_ag.mid");

		String content = new String(Files.readAllBytes(inputFile.toPath()), StandardCharsets.UTF_8);
		SimpleInputParser parser = new SimpleInputParser();
		SimpleInputSong song = parser.parse(content);

		SimpleInputChordDictionary chordDict = new SimpleInputChordDictionary();
		ChordSlotManager slotManager = new ChordSlotManager();

		// 反覆小節展開
		List<SimpleInputSong.Measure> unrolledMeasures = unrollRepeats(song.measures);

		// 第一階段：掃描整首歌曲，註冊所有用到的主和弦與強制品位衍生和弦
		for (SimpleInputSong.Measure measure : unrolledMeasures) {
			for (SimpleInputSong.ChordSegment segment : measure.segments) {
				if (segment.chordName == null) continue;
				String chordDisplayName = resolveChordDisplayName(chordDict, segment.chordName, song.header.transposeSemitones);
				SimpleInputChordDictionary.ChordInfo chordInfo = resolveChordInfo(
					chordDict, segment.chordName, segment.position, song.header.transposeSemitones);
				String[] rt = parseRootAndType(chordDisplayName);
				int pos = segment.position != null ? segment.position : 1;

				ChordSlot baseSlot = slotManager.registerChord(chordDisplayName, rt[0], rt[1], pos, chordInfo.frets);

				// 檢查段落內事件是否有強制品位覆寫
				for (SimpleInputSong.Event ev : segment.events) {
					if (ev.forcedFrets != null && !ev.forcedFrets.isEmpty()) {
						int[] derivedFrets = deriveForcedFrets(chordInfo.frets, ev.forcedFrets, song.header.transposeSemitones);
						slotManager.registerChord(chordDisplayName + "*", rt[0], rt[1], pos, derivedFrets);
					}
				}
			}
		}

		// 輸出 .chords XML 檔案（不足 24 個補滿預設 C Maj）
		List<ChordSlot> fullSlots = slotManager.getPaddedSlots();
		String xmlContent = generateChordsXml(fullSlots, song.header.key);
		try (FileOutputStream fos = new FileOutputStream(chordsOutputFile)) {
			fos.write(xmlContent.getBytes(StandardCharsets.UTF_8));
		}
		System.out.println("產生和弦庫成功: " + chordsOutputFile.getAbsolutePath());
		System.out.println("註冊和弦槽位數量: " + slotManager.getRegisteredSlots().size() + " / 24");
		for (ChordSlot s : slotManager.getRegisteredSlots()) {
			System.out.println("  " + s);
		}

		// 第二階段：建構標準 MIDI 檔案
		Sequence sequence = new Sequence(Sequence.PPQ, PPQ);
		Track track = sequence.createTrack();

		// 設定 Track Name
		String trackName = baseName + " (Ample Guitar)";
		track.add(new MidiEvent(new MetaMessage(0x03, trackName.getBytes(StandardCharsets.UTF_8), trackName.length()), 0));

		// 設定 Tempo
		int bpm = song.header.tempo != null ? song.header.tempo : 90;
		int mpqn = 60_000_000 / bpm;
		byte[] tempoData = new byte[] {
			(byte)((mpqn >> 16) & 0xFF),
			(byte)((mpqn >> 8) & 0xFF),
			(byte)(mpqn & 0xFF)
		};
		track.add(new MidiEvent(new MetaMessage(0x51, tempoData, 3), 0));

		// 設定 Time Signature
		int num = song.header.numerator;
		int den = song.header.denominator;
		int denPower = (int)(Math.log(den) / Math.log(2));
		byte[] tsData = new byte[] { (byte)num, (byte)denPower, 24, 8 };
		track.add(new MidiEvent(new MetaMessage(0x58, tsData, 4), 0));

		// 計算基礎時值換算為 MIDI Ticks (480 ticks per quarter note)
		// 1 quarter note = 480 ticks
		// 1 whole note = 1920 ticks
		// 1 unit = 1920 / unitDenominator
		double ticksPerUnit = (1920.0) / song.header.unitDenominator;

		long currentTick = 0;
		int lastSentCC14 = -1;
		String lastMarkerChord = null;
		List<LeftHandSpan> leftHandSpans = new ArrayList<>();
		List<MidiEvent> rightHandEvents = new ArrayList<>();

		for (SimpleInputSong.Measure measure : unrolledMeasures) {
			for (SimpleInputSong.ChordSegment segment : measure.segments) {
				String chordDisplayName = resolveChordDisplayName(chordDict, segment.chordName, song.header.transposeSemitones);
				SimpleInputChordDictionary.ChordInfo chordInfo = resolveChordInfo(
					chordDict, segment.chordName, segment.position, song.header.transposeSemitones);

				ChordSlot baseSlot = null;
				if (chordInfo != null) {
					String[] rt = parseRootAndType(chordDisplayName);
					int pos = segment.position != null ? segment.position : 1;
					baseSlot = slotManager.registerChord(chordDisplayName, rt[0], rt[1], pos, chordInfo.frets);
				}

				// 在每個和弦變更的拍點插入 MIDI Marker
				if (chordDisplayName != null && !chordDisplayName.equals(lastMarkerChord)) {
					track.add(new MidiEvent(new MetaMessage(0x06, chordDisplayName.getBytes(StandardCharsets.UTF_8), chordDisplayName.length()), currentTick));
					lastMarkerChord = chordDisplayName;
				}

				List<SimpleInputSong.Event> events = segment.events;
				double patternUnits = 0;
				for (SimpleInputSong.Event ev : events) {
					patternUnits += ev.durationUnits;
				}
				double segmentUnits = segment.durationUnits > 0 ? segment.durationUnits : patternUnits;
				long segmentTicks = Math.round(segmentUnits * ticksPerUnit);

				if (events.isEmpty()) {
					// 無 pattern：一次 d 刷全和弦
					if (baseSlot != null) {
						leftHandSpans.add(new LeftHandSpan(baseSlot, currentTick, currentTick + segmentTicks));
						// 右手全刷 d
						int ccVal = strokeToCC14(16);
						if (ccVal != lastSentCC14) {
							rightHandEvents.add(createCCEvent(0, 14, ccVal, currentTick));
							lastSentCC14 = ccVal;
						}
						long noteOffTick = Math.max(currentTick + 1, currentTick + segmentTicks - 2);
						rightHandEvents.add(createNoteOnEvent(0, NOTE_STRUM_DOWN_FULL, 100, currentTick));
						rightHandEvents.add(createNoteOffEvent(0, NOTE_STRUM_DOWN_FULL, noteOffTick));
					}
					currentTick += segmentTicks;
					continue;
				}

				double scale = (segmentUnits > 0 && patternUnits > 0) ? (segmentUnits / patternUnits) : 1.0;
				long remainingTicks = segmentTicks;

				for (int i = 0; i < events.size(); i++) {
					SimpleInputSong.Event ev = events.get(i);
					boolean isLast = (i == events.size() - 1);
					long evTicks = Math.round(ev.durationUnits * scale * ticksPerUnit);
					if (isLast || (ev.tuplet == 0 && evTicks < Math.round(ticksPerUnit / 2))) {
						evTicks = remainingTicks;
					}
					if (evTicks > remainingTicks) {
						evTicks = remainingTicks;
					}
					remainingTicks -= evTicks;

					long evStartTick = currentTick;
					long evEndTick = currentTick + evTicks;
					long noteOffTick = Math.max(evStartTick + 1, evEndTick - 2);

					// 決定當前事件對應的 Chord Slot (檢查強制品位)
					ChordSlot activeSlot = baseSlot;
					if (chordInfo != null && ev.forcedFrets != null && !ev.forcedFrets.isEmpty()) {
						int[] derivedFrets = deriveForcedFrets(chordInfo.frets, ev.forcedFrets, song.header.transposeSemitones);
						String[] rt = parseRootAndType(chordDisplayName);
						int pos = segment.position != null ? segment.position : 1;
						activeSlot = slotManager.registerChord(chordDisplayName + "*", rt[0], rt[1], pos, derivedFrets);
					}
					if (activeSlot != null) {
						leftHandSpans.add(new LeftHandSpan(activeSlot, evStartTick, evEndTick));
					}

					// 右手動作觸發
					int vel = velocityOf(ev.velocity);

					switch (ev.type) {
						case 'd':
						case 'u': {
							if (ev.strings == null || ev.strings.isEmpty()) {
								// 未指定弦的一般全刷
								int note = (ev.type == 'd') ? NOTE_STRUM_DOWN_FULL : NOTE_STRUM_UP;
								int ccVal = strokeToCC14(ev.strokeDenominator);
								if (ccVal != lastSentCC14) {
									rightHandEvents.add(createCCEvent(0, 14, ccVal, evStartTick));
									lastSentCC14 = ccVal;
								}
								rightHandEvents.add(createNoteOnEvent(0, note, vel, evStartTick));
								rightHandEvents.add(createNoteOffEvent(0, note, noteOffTick));
							} else {
								// 指定弦刷法（如 d:45, u:321）
								List<Integer> realStrings = new ArrayList<>();
								for (int s : ev.strings) {
									realStrings.add(SimpleStringUtil.toRealString(s));
								}
								// 依刷弦方向排序：下刷物理弦大到小 (6->1)，上刷小到大 (1->6)
								if (ev.type == 'd') {
									realStrings.sort(Comparator.reverseOrder());
								} else {
									realStrings.sort(Comparator.naturalOrder());
								}
								emitStrumNotes(rightHandEvents, realStrings, ev.strokeDenominator, vel, evStartTick, noteOffTick);
							}
							break;
						}
						case 'x': {
							// x 短刷：根音 + 根音旁靠高音側一根弦（物理由大到小）
							int root = (chordInfo != null) ? chordInfo.rootString : 6;
							int neighbor = (root - 1 >= 1) ? root - 1 : root + 1;
							List<Integer> realStrings = new ArrayList<>();
							realStrings.add(root);
							realStrings.add(neighbor);
							// 確保由粗到細（大到小）
							realStrings.sort(Comparator.reverseOrder());
							emitStrumNotes(rightHandEvents, realStrings, ev.strokeDenominator, vel, evStartTick, noteOffTick);
							break;
						}
						case 't': {
							// 根音事件：輸出當前和弦根音物理弦號對應的單弦觸發黑鍵
							int root = (chordInfo != null) ? chordInfo.rootString : 6;
							int note = realStringToTriggerKey(root);
							rightHandEvents.add(createNoteOnEvent(0, note, vel, evStartTick));
							rightHandEvents.add(createNoteOffEvent(0, note, noteOffTick));
							break;
						}
						case 'n': {
							// 單弦或多弦事件
							if (ev.strings != null && !ev.strings.isEmpty()) {
								for (int userString : ev.strings) {
									int realString = SimpleStringUtil.toRealString(userString);
									int note = realStringToTriggerKey(realString);
									rightHandEvents.add(createNoteOnEvent(0, note, vel, evStartTick));
									rightHandEvents.add(createNoteOffEvent(0, note, noteOffTick));
								}
							}
							break;
						}
						case 'r':
							// 休止符：不發送右手音符
							break;
						case 'S': {
							// 觸發 Ample Guitar 內建 SEQ 1~8 音序器 Pattern
							int seqId = (ev.seqIndex != null) ? ev.seqIndex : 1;
							int note = seqIndexToNote(seqId);
							rightHandEvents.add(createNoteOnEvent(0, note, vel, evStartTick));
							rightHandEvents.add(createNoteOffEvent(0, note, noteOffTick));
							break;
						}
						default:
							break;
					}

					currentTick = evEndTick;
				}
			}
		}

		// 先輸出左手和弦槽位切換音符 (Slot 1~24 -> Note 36~59，Velocity 100)
		// 必須早於同一 tick 的右手音符，否則第一個音會用到尚未切換的舊和弦槽位
		// （例如預設 C Maj 的第 6 弦為悶弦，會導致 t 根音無聲）
		List<LeftHandSpan> mergedSpans = mergeLeftHandSpans(leftHandSpans);
		for (LeftHandSpan span : mergedSpans) {
			int slotNote = CHORD_SLOT_NOTE_BASE + span.slot.id;
			long noteOffTick = Math.max(span.startTick + 1, span.endTick - 2);
			track.add(createNoteOnEvent(0, slotNote, 100, span.startTick));
			track.add(createNoteOffEvent(0, slotNote, noteOffTick));
		}

		// 再輸出右手動作觸發音符
		for (MidiEvent event : rightHandEvents) {
			track.add(event);
		}

		// 結尾 End of Track
		track.add(new MidiEvent(new MetaMessage(0x2F, new byte[0], 0), currentTick + 240));

		// 寫出 MIDI 檔案
		MidiSystem.write(sequence, 1, midiOutputFile);
		System.out.println("產生 MIDI 檔案成功: " + midiOutputFile.getAbsolutePath());
		System.out.println("MIDI 總時長 Ticks: " + currentTick + " (約 " + (currentTick / (double)PPQ) + " 拍)");
	}

	/** 依延遲送出多根單弦觸發黑鍵 */
	private static void emitStrumNotes(List<MidiEvent> events, List<Integer> realStrings, Integer strokeDenominator,
			int velocity, long startTick, long noteOffTick) throws Exception {
		int delay = strokeDelayTicks(strokeDenominator);
		for (int i = 0; i < realStrings.size(); i++) {
			int realString = realStrings.get(i);
			int note = realStringToTriggerKey(realString);
			long noteOnTick = startTick + (long) i * delay;
			if (noteOnTick >= noteOffTick) {
				noteOnTick = Math.max(startTick, noteOffTick - 1);
			}
			events.add(createNoteOnEvent(0, note, velocity, noteOnTick));
			events.add(createNoteOffEvent(0, note, noteOffTick));
		}
	}

	/** 合併相鄰且同一個 Slot 的左手區間 */
	private static List<LeftHandSpan> mergeLeftHandSpans(List<LeftHandSpan> spans) {
		List<LeftHandSpan> result = new ArrayList<>();
		if (spans.isEmpty()) return result;
		LeftHandSpan current = null;
		for (LeftHandSpan span : spans) {
			if (current == null) {
				current = new LeftHandSpan(span.slot, span.startTick, span.endTick);
			} else if (current.slot.id == span.slot.id && current.endTick == span.startTick) {
				current.endTick = span.endTick;
			} else {
				result.add(current);
				current = new LeftHandSpan(span.slot, span.startTick, span.endTick);
			}
		}
		if (current != null) {
			result.add(current);
		}
		return result;
	}

	private static MidiEvent createNoteOnEvent(int channel, int note, int velocity, long tick) throws Exception {
		ShortMessage msg = new ShortMessage();
		msg.setMessage(ShortMessage.NOTE_ON, channel, note, velocity);
		return new MidiEvent(msg, tick);
	}

	private static MidiEvent createNoteOffEvent(int channel, int note, long tick) throws Exception {
		ShortMessage msg = new ShortMessage();
		msg.setMessage(ShortMessage.NOTE_OFF, channel, note, 0);
		return new MidiEvent(msg, tick);
	}

	private static MidiEvent createCCEvent(int channel, int controller, int value, long tick) throws Exception {
		ShortMessage msg = new ShortMessage();
		msg.setMessage(ShortMessage.CONTROL_CHANGE, channel, controller, value);
		return new MidiEvent(msg, tick);
	}

	public static void main(String[] args) {
		if (args.length < 1) {
			System.err.println("用法: java ... AmpleGuitarExporter <input.strum> [output_dir]");
			System.exit(1);
		}
		File inputFile = new File(args[0]);
		File outputDir = args.length > 1 ? new File(args[1]) : null;
		try {
			export(inputFile, outputDir);
		} catch (Exception e) {
			System.err.println("匯出失敗: " + e.getMessage());
			e.printStackTrace();
			System.exit(2);
		}
	}
}
