import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

import app.tuxguitar.graphics.control.TGFactoryImpl;
import app.tuxguitar.io.base.TGSongReaderHandle;
import app.tuxguitar.io.simpleinput.SimpleInputReader;

public class ReaderTest {
	public static void main(String[] args) throws Exception {
		String path = args.length > 0 ? args[0] : "G:\\App\\tuxguitar\\SimpleInput\\examples\\demo2.strum";
		byte[] data = Files.readAllBytes(Paths.get(path));

		System.out.println("start read...");
		TGSongReaderHandle handle = new TGSongReaderHandle();
		handle.setFactory(new TGFactoryImpl());
		InputStream in = new ByteArrayInputStream(data);
		handle.setInputStream(in);

		long t0 = System.currentTimeMillis();
		new SimpleInputReader().read(handle);
		long t1 = System.currentTimeMillis();

		System.out.println("read done in " + (t1 - t0) + " ms");
		if (handle.getSong() != null) {
			app.tuxguitar.song.models.TGSong s = handle.getSong();
			System.out.println("song tracks=" + s.countTracks() + " headers=" + s.countMeasureHeaders());
			app.tuxguitar.song.models.TGTrack tr = s.getTrack(0);
			System.out.println("track measures=" + tr.countMeasures());
			for (int i = 0; i < tr.countMeasures(); i++) {
				app.tuxguitar.song.models.TGMeasure mm = tr.getMeasure(i);
				System.out.println("  m" + i + " beats=" + mm.countBeats() + " pstart=" + mm.getPreciseStart() + " plen=" + mm.getPreciseLength());
			}
		} else {
			System.out.println("song is NULL");
		}
	}
}
