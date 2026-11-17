import app.tuxguitar.song.factory.TGFactory;
import app.tuxguitar.song.models.TGDuration;

/** 臨時診斷：檢查 TGDuration 對 3/16 等時值的換算。 */
public class DurationProbe {
	public static void main(String[] args) {
		TGFactory f = new TGFactory();
		long[] times = { 240, 480, 720, 960, 1200, 1440, 1920 };
		for (long t : times) {
			long approx = TGDuration.toTime(t);
			TGDuration d = TGDuration.fromTime(f, approx);
			System.out.println("precise=" + t + " toTime=" + approx
					+ " -> value=" + d.getValue()
					+ " dotted=" + d.isDotted()
					+ " doubleDotted=" + d.isDoubleDotted()
					+ " getTime=" + d.getTime()
					+ " getPreciseTime=" + d.getPreciseTime());
		}
		System.out.println("QUARTER_TIME=" + TGDuration.QUARTER_TIME);
	}
}
