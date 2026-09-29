import app.tuxguitar.io.simpleinput.*;
import app.tuxguitar.song.factory.TGFactory;
import app.tuxguitar.song.models.*;

public class BuildTest {
	public static void main(String[] args) throws Exception {
		String src = String.join("\n",
			"M:4/4",
			"L:1/8",
			"Q:1/4=90",
			"",
			"G(4) t 1 2=3 1 3 1 2 1 |",
			"");

		SimpleInputSong parsed = new SimpleInputParser().parse(src);
		System.out.println("parse OK: measures=" + parsed.measures.size());
		TGSong song = new SimpleInputSongBuilder(new TGFactory()).build(parsed);
		boolean forcedFretFound = false;
		TGMeasure builtMeasure = song.getTrack(0).getMeasure(0);
		for (int i = 0; i < builtMeasure.countBeats(); i++) {
			TGVoice voice = builtMeasure.getBeat(i).getVoice(0);
			for (int j = 0; j < voice.countNotes(); j++) {
				TGNote note = voice.getNote(j);
				if (note.getString() == 2 && note.getValue() == 3) {
					forcedFretFound = true;
				}
			}
		}
		if (!forcedFretFound) {
			throw new AssertionError("forced fret was not applied to user string 2");
		}

		// 刷速指定 ~N：驗證 TGStroke value 依 ~N 設定
		String strokeSrc = String.join("\n",
			"M:4/4",
			"L:1/8",
			"",
			"G(4) d~16 u~8 d~32 x~64 d~16 u~8 d~32 x~64 |",
			"");
		SimpleInputSong strokeParsed = new SimpleInputParser().parse(strokeSrc);
		TGSong strokeSong = new SimpleInputSongBuilder(new TGFactory()).build(strokeParsed);
		TGMeasure strokeMeasure = strokeSong.getTrack(0).getMeasure(0);
		int[] expected = {TGDuration.SIXTEENTH, TGDuration.EIGHTH, TGDuration.THIRTY_SECOND, TGDuration.SIXTY_FOURTH};
		if (strokeMeasure.countBeats() != 8) {
			throw new AssertionError("expected 8 beats for stroke test, got " + strokeMeasure.countBeats());
		}
		for (int i = 0; i < 4; i++) {
			TGBeat b = strokeMeasure.getBeat(i);
			if (b.getStroke().getValue() != expected[i]) {
				throw new AssertionError("beat " + i + " stroke value = " + b.getStroke().getValue() + ", expected " + expected[i]);
			}
		}
		System.out.println("OK: stroke speed ~N applied to TGStroke");

		// 刷速繼承：d~32 u d u → 後續 udu 都是 32；d~16 後回到 16
		String inheritSrc = String.join("\n",
			"M:4/4",
			"L:1/8",
			"",
			"G(4) d~32 u d u d~16 u d u |",
			"");
		SimpleInputSong inheritParsed = new SimpleInputParser().parse(inheritSrc);
		TGSong inheritSong = new SimpleInputSongBuilder(new TGFactory()).build(inheritParsed);
		TGMeasure inheritMeasure = inheritSong.getTrack(0).getMeasure(0);
		int[] inheritExpected = {TGDuration.THIRTY_SECOND, TGDuration.THIRTY_SECOND, TGDuration.THIRTY_SECOND, TGDuration.THIRTY_SECOND,
			TGDuration.SIXTEENTH, TGDuration.SIXTEENTH, TGDuration.SIXTEENTH, TGDuration.SIXTEENTH};
		if (inheritMeasure.countBeats() != 8) {
			throw new AssertionError("expected 8 beats for inheritance test, got " + inheritMeasure.countBeats());
		}
		for (int i = 0; i < 8; i++) {
			TGBeat b = inheritMeasure.getBeat(i);
			if (b.getStroke().getValue() != inheritExpected[i]) {
				throw new AssertionError("inherit beat " + i + " stroke value = " + b.getStroke().getValue() + ", expected " + inheritExpected[i]);
			}
		}
		System.out.println("OK: stroke speed inheritance applied to TGStroke");

		// 連音群組 [..]：驗證 TGDivisionType 套用（三連音 3/2、六連音 6/4）
		String tupletSrc = String.join("\n",
			"M:4/4",
			"L:1/8",
			"",
			"G(4) [t 1 2] [d u d u d u] [t 1 2] [d u d u d u] |",
			"");
		SimpleInputSong tupletParsed = new SimpleInputParser().parse(tupletSrc);
		TGSong tupletSong = new SimpleInputSongBuilder(new TGFactory()).build(tupletParsed);
		TGMeasure tupletMeasure = tupletSong.getTrack(0).getMeasure(0);
		if (tupletMeasure.countBeats() != 18) {
			throw new AssertionError("expected 18 beats for tuplet test, got " + tupletMeasure.countBeats());
		}
		// 前 3 個 beat：三連音（enters=3, times=2, base value=EIGHTH）
		for (int i = 0; i < 3; i++) {
			TGVoice v = tupletMeasure.getBeat(i).getVoice(0);
			TGDivisionType dt = v.getDuration().getDivision();
			if (dt.getEnters() != 3 || dt.getTimes() != 2) {
				throw new AssertionError("triplet beat " + i + " division enters=" + dt.getEnters() + " times=" + dt.getTimes());
			}
			if (v.getDuration().getValue() != TGDuration.EIGHTH) {
				throw new AssertionError("triplet beat " + i + " duration value=" + v.getDuration().getValue() + ", expected " + TGDuration.EIGHTH);
			}
		}
		// 接著 6 個 beat：六連音（enters=6, times=4, base value=SIXTEENTH）
		for (int i = 3; i < 9; i++) {
			TGVoice v = tupletMeasure.getBeat(i).getVoice(0);
			TGDivisionType dt = v.getDuration().getDivision();
			if (dt.getEnters() != 6 || dt.getTimes() != 4) {
				throw new AssertionError("sextuplet beat " + i + " division enters=" + dt.getEnters() + " times=" + dt.getTimes());
			}
			if (v.getDuration().getValue() != TGDuration.SIXTEENTH) {
				throw new AssertionError("sextuplet beat " + i + " duration value=" + v.getDuration().getValue() + ", expected " + TGDuration.SIXTEENTH);
			}
		}
		System.out.println("OK: tuplet division type and duration value applied to TGDuration");

		// Bm7 及 Em7 和弦測試
		String chordTestSrc = String.join("\n",
			"M:4/4",
			"L:1/8",
			"",
			"Bm7(4) d2u2d2u2 | Em7(4) d2u2d2u2 |",
			"");
		SimpleInputSong chordTestParsed = new SimpleInputParser().parse(chordTestSrc);
		TGSong chordTestSong = new SimpleInputSongBuilder(new TGFactory()).build(chordTestParsed);
		TGMeasure mBm7 = chordTestSong.getTrack(0).getMeasure(0);
		TGChord bm7Chord = mBm7.getBeat(0).getChord();
		if (bm7Chord == null || !"Bm7".equals(bm7Chord.getName())) {
			throw new AssertionError("Bm7 chord was not attached to beat 0");
		}
		int[] expectedBm7 = {-1, 2, 4, 2, 3, 2}; // 弦6到弦1
		for (int s = 1; s <= 6; s++) {
			int actual = bm7Chord.getFretValue(s - 1);
			int expFret = expectedBm7[6 - s];
			if (actual != expFret) {
				throw new AssertionError("Bm7 string " + s + " fret=" + actual + ", expected " + expFret);
			}
		}

		TGMeasure mEm7 = chordTestSong.getTrack(0).getMeasure(1);
		TGChord em7Chord = mEm7.getBeat(0).getChord();
		if (em7Chord == null || !"Em7".equals(em7Chord.getName())) {
			throw new AssertionError("Em7 chord was not attached to beat 0 of measure 1");
		}
		int[] expectedEm7 = {0, 2, 0, 0, 0, 0}; // 弦6到弦1
		for (int s = 1; s <= 6; s++) {
			int actual = em7Chord.getFretValue(s - 1);
			int expFret = expectedEm7[6 - s];
			if (actual != expFret) {
				throw new AssertionError("Em7 string " + s + " fret=" + actual + ", expected " + expFret);
			}
		}
		System.out.println("OK: Bm7 and Em7 chords verified");

		// 測試刷法 t d u d u：d/u 必須排除根音弦（Em 根音弦 6 不發音，只發音 1~5 弦）
		String rootStrumSrc = String.join("\n",
			"M:3/4",
			"L:1/8",
			"",
			"Em(3) t2 d u d u |",
			"");
		SimpleInputSong rootStrumParsed = new SimpleInputParser().parse(rootStrumSrc);
		TGSong rootStrumSong = new SimpleInputSongBuilder(new TGFactory()).build(rootStrumParsed);
		TGMeasure rootStrumMeasure = rootStrumSong.getTrack(0).getMeasure(0);
		// beat 0 是 t2 (根音弦 6)
		TGVoice v0 = rootStrumMeasure.getBeat(0).getVoice(0);
		if (v0.countNotes() != 1 || v0.getNote(0).getString() != 6) {
			throw new AssertionError("beat 0 should be single root note on string 6");
		}
		// 後續 beat 1~4 是 d u d u，發音弦應該是 1~5 弦，不包含根音弦 6
		for (int bi = 1; bi <= 4; bi++) {
			TGVoice vb = rootStrumMeasure.getBeat(bi).getVoice(0);
			for (int ni = 0; ni < vb.countNotes(); ni++) {
				if (vb.getNote(ni).getString() == 6) {
					throw new AssertionError("beat " + bi + " strum should NOT contain root string 6");
				}
			}
			if (vb.countNotes() != 5) {
				throw new AssertionError("beat " + bi + " should have 5 sounding strings, got " + vb.countNotes());
			}
		}
		System.out.println("OK: strum d/u excludes root string when pattern contains t");

		// 測試無 t 的純刷法：Em(4) d2u2d2u2 依然刷全和弦（所有 6 根弦）
		String fullStrumSrc = String.join("\n",
			"M:4/4",
			"L:1/8",
			"",
			"Em(4) d2 u2 d2 u2 |",
			"");
		SimpleInputSong fullStrumParsed = new SimpleInputParser().parse(fullStrumSrc);
		TGSong fullStrumSong = new SimpleInputSongBuilder(new TGFactory()).build(fullStrumParsed);
		TGMeasure fullStrumMeasure = fullStrumSong.getTrack(0).getMeasure(0);
		for (int bi = 0; bi < 4; bi++) {
			TGVoice vb = fullStrumMeasure.getBeat(bi).getVoice(0);
			if (vb.countNotes() != 6) {
				throw new AssertionError("pure strum beat " + bi + " should contain all 6 strings, got " + vb.countNotes());
			}
		}
		System.out.println("OK: pure strum without t keeps all sounding strings");

		System.out.println("tracks=" + song.countTracks());
		TGTrack track = song.getTrack(0);
		System.out.println("measures=" + track.countMeasures() + " strings=" + track.stringCount());
		for (int mi = 0; mi < track.countMeasures(); mi++) {
			TGMeasure m = track.getMeasure(mi);
			System.out.println("measure" + mi + " beats=" + m.countBeats() + " tempo=" + m.getTempo().getQuarterValue() + " ts=" + m.getTimeSignature().getNumerator() + "/" + m.getTimeSignature().getDenominator().getValue());
			for (int i = 0; i < m.countBeats(); i++) {
				TGBeat b = m.getBeat(i);
				TGVoice v = b.getVoice(0);
				System.out.println("  beat" + i + " start=" + b.getStart() + " notes=" + v.countNotes() + " dur=" + v.getDuration().getValue());
			}
		}
	}
}
