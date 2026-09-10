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
