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

		String positionSyntax = "M:4/4\nL:1/8\nEm@3(4) d2u2d2u2\n";
		SimpleInputSong.ChordSegment positionSegment = parser.parse(positionSyntax).measures.get(0).segments.get(0);
		if (!"Em".equals(positionSegment.chordName) || positionSegment.position == null
				|| positionSegment.position != 3 || positionSegment.durationUnits != 8) {
			throw new AssertionError("chord position syntax was parsed incorrectly");
		}
		System.out.println("OK: chord position syntax");

		// 括號群組接 *|/ 時值調整，如 (6321)*2
		String groupDurationSyntax = "M:4/4\nL:1/8\nG (6321)*2 (23)/2 1\n";
		SimpleInputSong.ChordSegment groupSegment = parser.parse(groupDurationSyntax).measures.get(0).segments.get(0);
		if (groupSegment.events.size() != 3
				|| groupSegment.events.get(0).durationUnits != 2.0
				|| groupSegment.events.get(1).durationUnits != 0.5
				|| groupSegment.events.get(2).durationUnits != 1.0) {
			throw new AssertionError("parenthesized group duration syntax was parsed incorrectly");
		}
		System.out.println("OK: parenthesized group duration syntax");

		// 括號群組內數字可空格分隔，如 (6 3 2 1)*2
		String groupSpaceSyntax = "M:4/4\nL:1/8\nG (6 3 2 1)*2 (2 3)/2 1\n";
		SimpleInputSong.ChordSegment groupSpaceSegment = parser.parse(groupSpaceSyntax).measures.get(0).segments.get(0);
		if (groupSpaceSegment.events.size() != 3
				|| groupSpaceSegment.events.get(0).durationUnits != 2.0
				|| groupSpaceSegment.events.get(0).strings.size() != 4
				|| groupSpaceSegment.events.get(1).durationUnits != 0.5
				|| groupSpaceSegment.events.get(1).strings.size() != 2
				|| groupSpaceSegment.events.get(2).durationUnits != 1.0) {
			throw new AssertionError("parenthesized group with spaces was parsed incorrectly");
		}
		System.out.println("OK: parenthesized group with spaces syntax");

		// 括號群組內各弦指定品位，如 (6 3=4 2=5 1=3)*2
		String groupForcedSyntax = "M:4/4\nL:1/8\nG (6 3=4 2=5 1=3)*2 (2=3 3)/2 1\n";
		SimpleInputSong.ChordSegment groupForcedSegment = parser.parse(groupForcedSyntax).measures.get(0).segments.get(0);
		if (groupForcedSegment.events.size() != 3
				|| groupForcedSegment.events.get(0).durationUnits != 2.0
				|| groupForcedSegment.events.get(0).strings.size() != 4
				|| groupForcedSegment.events.get(0).forcedFrets.get(3) != 4
				|| groupForcedSegment.events.get(0).forcedFrets.get(2) != 5
				|| groupForcedSegment.events.get(0).forcedFrets.get(1) != 3
				|| groupForcedSegment.events.get(0).forcedFrets.containsKey(6)
				|| groupForcedSegment.events.get(1).durationUnits != 0.5
				|| groupForcedSegment.events.get(1).forcedFrets.get(2) != 3
				|| groupForcedSegment.events.get(1).strings.size() != 2) {
			throw new AssertionError("parenthesized group with forced frets was parsed incorrectly");
		}
		System.out.println("OK: parenthesized group with forced frets syntax");

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
