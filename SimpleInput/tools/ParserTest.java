import app.tuxguitar.io.simpleinput.SimpleInputParser;
import app.tuxguitar.io.simpleinput.SimpleInputSong;
import app.tuxguitar.io.simpleinput.SimpleInputFormatException;

public class ParserTest {
	public static void main(String[] args) throws Exception {
		String demo1 = String.join("\n",
			"% comment",
			"M:4/4",
			"L:1/8",
			"Q:1/4=90",
			"K:C",
			"",
			"@basic  = d2u2d2u2",
			"@arp    = t (23) 1 (23) 1 (23) 1 (23)",
			"",
			"G(4) @basic | C(4) @basic | Am(4) @arp | F(4) @basic ||",
			"");

		SimpleInputParser parser = new SimpleInputParser();
		SimpleInputSong song = parser.parse(demo1);
		System.out.println("OK: measures=" + song.measures.size() + " defs=" + song.definitions.keySet() + " tempo=" + song.header.tempo);

		String durationSyntax = "M:4/4\nL:1/8\nG d/2 2=3*2 2/2 x2 x*2 2\n";
		SimpleInputSong syntaxSong = parser.parse(durationSyntax);
		SimpleInputSong.ChordSegment syntaxSegment = syntaxSong.measures.get(0).segments.get(0);
		if (syntaxSegment.events.size() != 6
				|| syntaxSegment.events.get(0).durationUnits != 0.5
				|| syntaxSegment.events.get(1).durationUnits != 2.0
				|| syntaxSegment.events.get(1).forcedFrets.get(2) != 3
				|| syntaxSegment.events.get(2).durationUnits != 0.5
				|| syntaxSegment.events.get(3).durationUnits != 2.0
				|| syntaxSegment.events.get(4).durationUnits != 2.0) {
			throw new AssertionError("duration syntax was parsed incorrectly");
		}
		System.out.println("OK: duration and forced-fret syntax");

		String chordDurationSyntax = "M:4/4\nL:1/8\nEm9(4) d2u2d2u2\n";
		SimpleInputSong.ChordSegment chordSegment = parser.parse(chordDurationSyntax).measures.get(0).segments.get(0);
		if (!"Em9".equals(chordSegment.chordName) || chordSegment.durationUnits != 8.0) {
			throw new AssertionError("parenthesized chord duration was parsed incorrectly");
		}
		System.out.println("OK: parenthesized chord duration syntax");

		// 錯誤案例：過拍
		String bad = "M:4/4\nL:1/8\n@basic = d2u2d2u2\nC(2) @basic @basic | G(4) d4 ||\n";
		try {
			parser.parse(bad);
			System.out.println("FAIL: should have thrown");
		} catch (SimpleInputFormatException e) {
			System.out.println("OK (error expected): " + e.getMessage());
		}

		// 錯誤案例：未識別和弦
		String badChord = "M:4/4\nL:1/8\nH7 d4 u4 | G(8) ||\n";
		try {
			parser.parse(badChord);
			System.out.println("FAIL: should have thrown chord error at build; parse passed as expected here");
		} catch (SimpleInputFormatException e) {
			System.out.println("OK (error expected): " + e.getMessage());
		}
	}
}
