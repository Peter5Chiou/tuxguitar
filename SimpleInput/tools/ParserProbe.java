import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import app.tuxguitar.io.simpleinput.SimpleInputParser;
import app.tuxguitar.io.simpleinput.SimpleInputSong;

/** 臨時診斷工具：印出 parser 解析後的事件。 */
public class ParserProbe {
	public static void main(String[] args) throws Exception {
		String src;
		if (args.length > 0) {
			src = new String(Files.readAllBytes(Paths.get(args[0])), StandardCharsets.UTF_8);
		} else {
			src = "M:4/4\nL:1/16\nG(4) d2:321!mf d2:321!mf d2:321!mf d2:321!mf d2:321!mf d2:321!mf d2:321!mf d2:321!mf\n";
		}
		SimpleInputSong s = new SimpleInputParser().parse(src);
		for (SimpleInputSong.Measure m : s.measures) {
			System.out.println("measure units=" + m.units);
			for (SimpleInputSong.ChordSegment seg : m.segments) {
				System.out.println(" seg chord=" + seg.chordName + " dur=" + seg.durationUnits);
				for (SimpleInputSong.Event e : seg.events) {
					System.out.println("   type=" + e.type + " dur=" + e.durationUnits
							+ " strings=" + e.strings + " vel=" + e.velocity + " muted=" + e.muted);
				}
			}
		}
	}
}
