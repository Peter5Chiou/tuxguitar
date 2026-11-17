import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Iterator;

import app.tuxguitar.io.simpleinput.SimpleInputParser;
import app.tuxguitar.io.simpleinput.SimpleInputSong;
import app.tuxguitar.io.simpleinput.SimpleInputSongBuilder;
import app.tuxguitar.song.factory.TGFactory;
import app.tuxguitar.song.models.TGBeat;
import app.tuxguitar.song.models.TGMeasure;
import app.tuxguitar.song.models.TGNote;
import app.tuxguitar.song.models.TGSong;
import app.tuxguitar.song.models.TGTrack;
import app.tuxguitar.song.models.TGVoice;

/**
 * 讀 .strum → 建 TGSong → 印出每個 beat 的弦/品位/DeadNote/PalmMute/刷弦方向。
 * 用法: java BuildCheck <file.strum>
 */
public class BuildCheck {

	public static void main(String[] args) throws Exception {
		String text = new String(Files.readAllBytes(Paths.get(args[0])), StandardCharsets.UTF_8);
		SimpleInputSong ir = new SimpleInputParser().parse(text);
		TGSong song = new SimpleInputSongBuilder(new TGFactory()).build(ir);

		Iterator<TGTrack> tracks = song.getTracks();
		while (tracks.hasNext()) {
			TGTrack track = tracks.next();
			System.out.println("Track: " + track.getName() + " strings=" + track.stringCount());
			Iterator<TGMeasure> measures = track.getMeasures();
			while (measures.hasNext()) {
				TGMeasure measure = measures.next();
				System.out.println(" Measure " + measure.getNumber());
				for (TGBeat beat : measure.getBeats()) {
					TGVoice voice = beat.getVoice(0);
					if (voice.isEmpty()) {
						continue;
					}
					StringBuilder sb = new StringBuilder();
					sb.append("  start=").append(beat.getStart())
					  .append(" dur=").append(voice.getDuration().getValue())
					  .append(voice.getDuration().isDotted() ? "." : "")
					  .append(voice.getDuration().isDoubleDotted() ? ".." : "")
					  .append(" div=").append(voice.getDuration().getDivision().getEnters())
					  .append(':').append(voice.getDuration().getDivision().getTimes())
					  .append(" stroke=").append(beat.getStroke().getDirection())
					  .append(" notes=[");
					for (TGNote note : voice.getNotes()) {
						sb.append("s").append(note.getString())
						  .append(":f").append(note.getValue())
						  .append(note.getEffect().isDeadNote() ? ":DEAD" : "")
						  .append(note.getEffect().isPalmMute() ? ":PM" : "")
						  .append(" ");
					}
					sb.append("]");
					System.out.println(sb);
				}
			}
		}
	}
}
