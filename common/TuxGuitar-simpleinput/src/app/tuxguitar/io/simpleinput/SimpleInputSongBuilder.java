package app.tuxguitar.io.simpleinput;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import app.tuxguitar.song.factory.TGFactory;
import app.tuxguitar.song.managers.TGSongManager;
import app.tuxguitar.song.models.TGBeat;
import app.tuxguitar.song.models.TGChannel;
import app.tuxguitar.song.models.TGChord;
import app.tuxguitar.song.models.TGDuration;
import app.tuxguitar.song.models.TGMeasure;
import app.tuxguitar.song.models.TGMeasureHeader;
import app.tuxguitar.song.models.TGNote;
import app.tuxguitar.song.models.TGSong;
import app.tuxguitar.song.models.TGString;
import app.tuxguitar.song.models.TGStroke;
import app.tuxguitar.song.models.TGTempo;
import app.tuxguitar.song.models.TGTimeSignature;
import app.tuxguitar.song.models.TGTrack;
import app.tuxguitar.song.models.TGVoice;

/** 將 SimpleInputSong IR 轉成 TGSong */
public class SimpleInputSongBuilder {

	// standard tuning：實際弦 6→1（粗→細）
	private static final int[] STANDARD_TUNING = {40, 45, 50, 55, 59, 64};

	private final TGFactory factory;
	private final SimpleInputChordDictionary chordDictionary = new SimpleInputChordDictionary();

	public SimpleInputSongBuilder(TGFactory factory) {
		this.factory = factory;
	}

	public TGSong build(SimpleInputSong src) throws SimpleInputFormatException {
		TGSongManager manager = new TGSongManager(this.factory);
		TGSong song = this.factory.newSong();

		// 拍號 / 速度
		long unitTime = unitTime(src.header.unitDenominator);

		TGTempo tempo = this.factory.newTempo();
		tempo.setQuarterValue(src.header.tempo != null ? src.header.tempo : 90);

		TGTimeSignature ts = this.factory.newTimeSignature();
		ts.setNumerator(src.header.numerator);
		ts.getDenominator().setValue(durationValue(src.header.denominator));

		// 先建立所有 measure headers（避免 addTrack 對空歌觸發 fillSong 產生預設小節）
		long measureLength = unitTime * totalUnits(src);
		long start = TGDuration.QUARTER_TIME;
		for (int i = 0; i < src.measures.size(); i++) {
			TGMeasureHeader header = this.factory.newHeader();
			header.setNumber(i + 1);
			header.setStart(start + i * measureLength);
			header.getTempo().copyFrom(tempo);
			header.getTimeSignature().copyFrom(ts);
			song.addMeasureHeader(header);
		}

		// 手動建立 track（不走 manager.addTrack：空歌會觸發 fillSong 產生多餘的預設小節）
		TGTrack track = this.factory.newTrack();
		track.setNumber(1);
		track.setName("Guitar");
		track.getColor().copyFrom(app.tuxguitar.song.models.TGColor.RED);
		for (int i = 0; i < song.countMeasureHeaders(); i++) {
			track.addMeasure(this.factory.newMeasure(song.getMeasureHeader(i)));
		}
		song.addTrack(track);
		setupTrack(song, track);
		if (track.countMeasures() != src.measures.size()) {
			throw new SimpleInputFormatException("SimpleInput: 內部錯誤：track 小節數 " + track.countMeasures() + " ≠ 期望 " + src.measures.size());
		}

		// 填入每個小節的 beat
		for (int i = 0; i < src.measures.size(); i++) {
			TGMeasure tgMeasure = track.getMeasure(i);
			if (tgMeasure == null) {
				throw new SimpleInputFormatException("SimpleInput: 內部錯誤：第 " + (i + 1) + " 小節為 null");
			}
			TGMeasureHeader header = song.getMeasureHeader(i);
			long beatStart = header.getPreciseStart();
			for (SimpleInputSong.ChordSegment segment : src.measures.get(i).segments) {
				beatStart = buildSegment(manager, song, tgMeasure, src, segment, beatStart, toPrecise(unitTime), totalUnits(src));
			}
		}

		manager.orderBeats(song);
		return song;
	}

	private int totalUnits(SimpleInputSong src) {
		return src.header.numerator * src.header.unitDenominator / src.header.denominator;
	}

	private void setupTrack(TGSong song, TGTrack track) {
		TGChannel channel = new TGSongManager(this.factory).addChannel(song);
		channel.setProgram((short) 24); // nylon guitar
		channel.setName("Guitar");
		track.setChannelId(channel.getChannelId());
		track.setName("Guitar");

		track.getStrings().clear();
		// 弦號 1（細）→ 6（粗）
		for (int i = 5; i >= 0; i--) {
			TGString string = this.factory.newString();
			string.setNumber(6 - i);
			string.setValue(STANDARD_TUNING[i]);
			track.getStrings().add(string);
		}
	}

	/** 建立一個和弦段落的所有 beat，回傳新的 beatStart */
	private long buildSegment(TGSongManager manager, TGSong song, TGMeasure measure, SimpleInputSong src,
			SimpleInputSong.ChordSegment segment, long start, long unitTime, int measureUnits) throws SimpleInputFormatException {

		SimpleInputChordDictionary.ChordInfo chordInfo = null;
		if (segment.chordName != null) {
			chordInfo = this.chordDictionary.find(segment.chordName);
			if (chordInfo == null) {
				throw new SimpleInputFormatException("SimpleInput: 第 " + segment.line + " 行：未識別的和弦「" + segment.chordName + "」");
			}
			if (segment.position != null) {
				chordInfo = this.chordDictionary.atPosition(chordInfo, segment.position);
			}
		}

		// 決定此段落各事件的實際時值
		List<SimpleInputSong.Event> events = segment.events;
		double patternUnits = 0;
		for (SimpleInputSong.Event ev : events) {
			patternUnits += ev.durationUnits;
		}

		double segmentUnits = segment.durationUnits > 0 ? segment.durationUnits : patternUnits;

		if (events.isEmpty()) {
			// 無 pattern：一次 d 刷全和弦，持續 segmentUnits
			if (chordInfo != null) {
				buildStrumBeat(manager, measure, src, chordInfo, start, Math.round(segmentUnits * unitTime), true, null, null, null, segment.line, segmentContext(segment));
				addChordToBeat(measure, start, chordInfo, segment.chordName);
			} else {
				buildRestBeat(measure, start, Math.round(segmentUnits * unitTime));
			}
			return start + Math.round(segmentUnits * unitTime);
		}

		// pattern 縮放係數
		double scale = segmentUnits > 0 && patternUnits > 0 ? (double) segmentUnits / patternUnits : 1.0;

		long current = start;
		long remaining = Math.round(segmentUnits * unitTime);
		boolean chordShown = false;

		for (int i = 0; i < events.size(); i++) {
			SimpleInputSong.Event ev = events.get(i);
			boolean isLast = i == events.size() - 1;

			long evTime = Math.round(ev.durationUnits * scale * unitTime);
			if (isLast || evTime < unitTime / 2) {
				evTime = remaining; // 誤差補償：最後一個事件補齊
			}
			if (evTime > remaining) {
				evTime = remaining;
			}
			remaining -= evTime;

			switch (ev.type) {
				case 'd':
				case 'u':
					if (chordInfo == null) {
						throw new SimpleInputFormatException("SimpleInput: 第 " + ev.line + " 行：刷弦事件需要先指定和弦");
					}
					buildStrumBeat(manager, measure, src, chordInfo, current, evTime, ev.type == 'd', ev.strings, null, ev.strokeDenominator, ev.line, segmentContext(segment));
					break;
				case 'x':
					if (chordInfo == null) {
						throw new SimpleInputFormatException("SimpleInput: 第 " + ev.line + " 行：x 事件需要先指定和弦");
					}
					buildMutedStrumBeat(manager, measure, src, chordInfo, current, evTime, ev.strings, ev.strokeDenominator, ev.line, segmentContext(segment));
					break;
				case 't':
					if (chordInfo == null) {
						throw new SimpleInputFormatException("SimpleInput: 第 " + ev.line + " 行：t 事件需要先指定和弦");
					}
					buildNoteBeat(measure, src, chordInfo, current, evTime, chordInfo.rootString, null, ev.line, segmentContext(segment));
					break;
				case 'n': {
					if (ev.strings == null || ev.strings.isEmpty()) {
						throw new SimpleInputFormatException("SimpleInput: 第 " + ev.line + " 行：無效的單音事件");
					}
					if (ev.strings.size() == 1) {
						int userString = ev.strings.get(0);
						int realString = SimpleStringUtil.toRealString(userString);
						buildNoteBeat(measure, src, chordInfo, current, evTime, realString, ev.forcedFrets, ev.line, segmentContext(segment));
					} else {
						buildMultiNoteBeat(measure, src, chordInfo, current, evTime, SimpleStringUtil.toRealStrings(ev.strings), ev.forcedFrets, ev.line, segmentContext(segment));
					}
					break;
				}
				case 'r':
					buildRestBeat(measure, current, evTime);
					break;
				default:
					throw new SimpleInputFormatException("SimpleInput: 第 " + ev.line + " 行：未知事件類型 " + ev.type);
			}
			if (!chordShown && chordInfo != null) {
				addChordToBeat(measure, current, chordInfo, segment.chordName);
				chordShown = true;
			}
			current += evTime;
		}
		return start + Math.round(segmentUnits * unitTime);
	}

	/** 下/上刷 → 多音 beat + TGStroke。userStrings 為 null 時刷全和弦；forcedFrets 覆寫個別弦品位的強制；strokeDenominator 指定刷速（~N） */
	private void buildStrumBeat(TGSongManager manager, TGMeasure measure, SimpleInputSong src,
			SimpleInputChordDictionary.ChordInfo chordInfo, long preciseStart, long preciseDuration, boolean down, List<Integer> userStrings, Map<Integer, Integer> forcedFrets, Integer strokeDenominator, int line, String context) throws SimpleInputFormatException {

		TGBeat beat = getBeat(measure, preciseStart);
		TGVoice voice = beat.getVoice(0);
		voice.setEmpty(false);
		setDuration(voice, preciseDuration);

		// 決定要刷的弦：指定弦（使用者編號→實際弦）或全和弦發音弦
		List<Integer> realStrings;
		if (userStrings != null && !userStrings.isEmpty()) {
			realStrings = SimpleStringUtil.toRealStrings(userStrings);
		} else {
			realStrings = chordInfo.soundingStrings();
		}
		for (Integer realString : realStrings) {
			int fret = chordInfo.frets[6 - realString];
			int userString = SimpleStringUtil.toUserString(realString);
			boolean deadNote = fret < 0 && (forcedFrets == null || !forcedFrets.containsKey(userString));
			fret = applyForcedFret(fret, forcedFrets, userString, line);
			TGNote note = this.factory.newNote();
			note.setString(realString);
			note.setValue(deadNote ? 0 : fret);
			note.getEffect().setDeadNote(deadNote);
			voice.addNote(note);
		}
		beat.getStroke().setDirection(down ? TGStroke.STROKE_DOWN : TGStroke.STROKE_UP);
		if (strokeDenominator != null) {
			beat.getStroke().setValue(strokeValueForDenominator(strokeDenominator));
		} else {
			beat.getStroke().setValue(strokeValue(TGDuration.toTime(preciseDuration)));
		}
	}

	/** x 短刷：刷 根音 + 根音旁靠高音側的一根弦（2 弦） */
	private void buildMutedStrumBeat(TGSongManager manager, TGMeasure measure, SimpleInputSong src,
			SimpleInputChordDictionary.ChordInfo chordInfo, long preciseStart, long preciseDuration, List<Integer> userStrings, Integer strokeDenominator, int line, String context) throws SimpleInputFormatException {

		// 根音 + 根音往高音方向的一根弦（實際弦號 = rootString - 1）
		// 注意：buildStrumBeat 的 userStrings 是「使用者編號」，實際→使用者用同一映射（1↔3 對調、其餘不變）
		int root = chordInfo.rootString;
		int neighbor = root - 1;
		List<Integer> userNoStrings = new ArrayList<>();
		userNoStrings.add(SimpleStringUtil.toRealString(root));
		if (neighbor >= 1) {
			userNoStrings.add(SimpleStringUtil.toRealString(neighbor));
		} else {
			userNoStrings.add(SimpleStringUtil.toRealString(root + 1)); // 根音已在第 1 弦時，往低音側取
		}
		buildStrumBeat(manager, measure, src, chordInfo, preciseStart, preciseDuration, true, userNoStrings, null, strokeDenominator, line, context);
	}

	/** 套用強制品位：userStringNo（使用者編號）符合時覆寫 fret */
	private int applyForcedFret(int fret, Map<Integer, Integer> forcedFrets, int userStringNo, int line) throws SimpleInputFormatException {
		if (forcedFrets != null && forcedFrets.containsKey(userStringNo)) {
			return forcedFrets.get(userStringNo);
		}
		return fret;
	}

	/** 根音/單弦單音。forcedFrets 有該弦（使用者編號）時覆寫品位 */
	private void buildNoteBeat(TGMeasure measure, SimpleInputSong src,
			SimpleInputChordDictionary.ChordInfo chordInfo, long preciseStart, long preciseDuration, int realString, Map<Integer, Integer> forcedFrets, int line, String context) throws SimpleInputFormatException {
		int fret;
		if (chordInfo != null) {
			fret = chordInfo.frets[6 - realString];
		} else {
			fret = 0;
		}
		// 和弦圖的悶弦標記只限制刷法；指法事件遇到悶弦時視為空弦。
		if (fret < 0) {
			fret = 0;
		}
		if (forcedFrets != null) {
			int userStringNo = SimpleStringUtil.toUserString(realString);
			if (forcedFrets.containsKey(userStringNo)) {
				fret = forcedFrets.get(userStringNo);
			}
		}
		TGBeat beat = getBeat(measure, preciseStart);
		TGVoice voice = beat.getVoice(0);
		voice.setEmpty(false);
		setDuration(voice, preciseDuration);
		TGNote note = this.factory.newNote();
		note.setString(realString);
		note.setValue(fret);
		voice.addNote(note);
	}

	/** 同時彈多根弦。forcedFrets（使用者弦號→品位）覆寫個別弦品位 */
	private void buildMultiNoteBeat(TGMeasure measure, SimpleInputSong src,
			SimpleInputChordDictionary.ChordInfo chordInfo, long preciseStart, long preciseDuration, List<Integer> realStrings, Map<Integer, Integer> forcedFrets, int line, String context) throws SimpleInputFormatException {
		TGBeat beat = getBeat(measure, preciseStart);
		TGVoice voice = beat.getVoice(0);
		voice.setEmpty(false);
		setDuration(voice, preciseDuration);
		for (Integer realString : realStrings) {
			int fret = 0;
			if (chordInfo != null) {
				fret = chordInfo.frets[6 - realString];
				if (fret < 0) {
					// 和弦圖的悶弦標記不套用到指法，未指定品位時使用空弦。
					fret = 0;
				}
			}
			if (forcedFrets != null) {
				int userStringNo = SimpleStringUtil.toUserString(realString);
				if (forcedFrets.containsKey(userStringNo)) {
					fret = forcedFrets.get(userStringNo);
				}
			}
			TGNote note = this.factory.newNote();
			note.setString(realString);
			note.setValue(fret);
			voice.addNote(note);
		}
	}

	/** 休止 beat */
	private void buildRestBeat(TGMeasure measure, long preciseStart, long preciseDuration) {
		TGBeat beat = getBeat(measure, preciseStart);
		TGVoice voice = beat.getVoice(0);
		voice.setEmpty(false);
		setDuration(voice, preciseDuration);
		// 無 note = rest voice
	}

	private void addChordToBeat(TGMeasure measure, long preciseStart, SimpleInputChordDictionary.ChordInfo chordInfo, String name) {
		TGBeat beat = getBeat(measure, preciseStart);
		TGChord chord = this.factory.newChord(6);
		chord.setName(name);
		// firstFret = 0 → TGChordImpl.calculateFirstFret() 自動推導（開放和弦=1，封閉=最低 fret）
		chord.setFirstFret(0);
		// addFretValue(index, fret)：index 0=弦1 … 5=弦6（對照 TGChordDialog 的 note.getString()-1）
		for (int s = 1; s <= 6; s++) {
			int fret = chordInfo.frets[6 - s];
			chord.addFretValue(s - 1, fret);
		}
		beat.setChord(chord);
	}

	private String segmentContext(SimpleInputSong.ChordSegment segment) {
		StringBuilder context = new StringBuilder("SimpleInput: 第 ").append(segment.line).append(" 行、第 ")
			.append(segment.measureNumber).append(" 小節");
		if (segment.chordName != null) {
			context.append("、和弦「").append(segment.chordName);
			if (segment.position != null) {
				context.append("@").append(segment.position);
			}
			context.append("」");
		}
		return context.toString();
	}

	private int firstFret(SimpleInputChordDictionary.ChordInfo chordInfo) {
		int first = Integer.MAX_VALUE;
		for (int f : chordInfo.frets) {
			if (f > 0 && f < first) {
				first = f;
			}
		}
		return first == Integer.MAX_VALUE ? 0 : first;
	}

	private void setDuration(TGVoice voice, long preciseTime) {
		TGDuration d = TGDuration.fromTime(this.factory, TGDuration.toTime(preciseTime));
		voice.getDuration().setValue(d.getValue());
		voice.getDuration().setDotted(d.isDotted());
		voice.getDuration().setDoubleDotted(d.isDoubleDotted());
	}

	private int strokeValue(long duration) {
		if (duration >= TGDuration.QUARTER_TIME / 4) {
			return TGDuration.QUARTER;
		}
		if (duration >= TGDuration.QUARTER_TIME / 8) {
			return TGDuration.EIGHTH;
		}
		if (duration >= TGDuration.QUARTER_TIME / 16) {
			return TGDuration.SIXTEENTH;
		}
		return TGDuration.THIRTY_SECOND;
	}

	/** 依 ~N 刷速（N=4~64 分音符）轉成 TGStroke value */
	private int strokeValueForDenominator(int denominator) {
		switch (denominator) {
			case 4: return TGDuration.QUARTER;
			case 8: return TGDuration.EIGHTH;
			case 16: return TGDuration.SIXTEENTH;
			case 32: return TGDuration.THIRTY_SECOND;
			case 64: return TGDuration.SIXTY_FOURTH;
			default: return TGDuration.SIXTEENTH;
		}
	}

	private TGBeat getBeat(TGMeasure measure, long preciseStart) {
		for (int i = 0; i < measure.countBeats(); i++) {
			TGBeat beat = measure.getBeat(i);
			if (beat.getPreciseStart() != null && beat.getPreciseStart() == preciseStart) {
				return beat;
			}
		}
		TGBeat beat = this.factory.newBeat();
		beat.setPreciseStart(preciseStart);  // 必須用 precise，legacy setStart 會讓 preciseStart=null 導致 UI 凍結
		measure.addBeat(beat);
		return beat;
	}

	private long unitTime(int unitDenominator) {
		return TGDuration.QUARTER_TIME * 4L / unitDenominator;
	}

	private int durationValue(int denominator) {
		switch (denominator) {
			case 1: return TGDuration.WHOLE;
			case 2: return TGDuration.HALF;
			case 4: return TGDuration.QUARTER;
			case 8: return TGDuration.EIGHTH;
			case 16: return TGDuration.SIXTEENTH;
			case 32: return TGDuration.THIRTY_SECOND;
			case 64: return TGDuration.SIXTY_FOURTH;
			default: return TGDuration.QUARTER;
		}
	}

	/** 將拍/基礎單位時間換算為 precise ticks */
	private long toPrecise(long time) {
		return TGDuration.toPreciseTime(time);
	}
}
