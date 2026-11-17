import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * 小工具：把 Ample Guitar 的 .strumseq 轉成 SimpleInput 的「符號表示法」。
 *
 * 只輸出符號字串（例如 `mu md:321 md X X u d:321 d z8`），不含和弦、不寫檔，
 * 方便直接貼進 .strum 的 @ 定義或譜面。需要完整 .strum 檔（含和弦）請用 StrumSeqImporter。
 *
 * 用法:
 *   java StrumSeqToSymbols.java <input.strumseq> [--name 名稱] [--full] [--no-rests] [--velocity]
 *
 *   --name 名稱   輸出 `@名稱 = ...`（僅限單一小節）
 *   --full        加上 M: / L: / Q: 標頭（隱含 --name，未給時用檔名）
 *   --no-rests    省略休止符 z（輸出較精簡，但時值不再湊滿小節）
 *   --velocity    加上 !ppp~!fff 力度
 */
public class StrumSeqToSymbols {

	private static final int[] VEL_LEVELS = { 20, 35, 50, 68, 85, 100, 115, 127 };
	private static final String[] VEL_NAMES = { "ppp", "pp", "p", "mp", "mf", "f", "ff", "fff" };

	private static class Ev {
		int onset;
		int dur;
		String token;
		Integer velocity;

		Ev(int onset, int dur, String token, Integer velocity) {
			this.onset = onset;
			this.dur = dur;
			this.token = token;
			this.velocity = velocity;
		}
	}

	public static void main(String[] args) throws Exception {
		File input = null;
		String name = null;
		boolean full = false;
		boolean rests = true;
		boolean velocity = false;

		for (int i = 0; i < args.length; i++) {
			switch (args[i]) {
				case "--name":
					name = args[++i];
					break;
				case "--full":
					full = true;
					break;
				case "--no-rests":
					rests = false;
					break;
				case "--velocity":
					velocity = true;
					break;
				case "-h":
				case "--help":
					usage();
					return;
				default:
					input = new File(args[i]);
					break;
			}
		}
		if (input == null) {
			usage();
			System.exit(1);
		}

		Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(input);
		Element pattern = doc.getDocumentElement();

		String timeSig = pattern.getAttribute("Time-Signature");
		String quantize = pattern.getAttribute("Quantize");
		String bpm = pattern.getAttribute("BPM");

		int[] ts = fraction(timeSig, "Time-Signature");
		int[] qz = fraction(quantize, "Quantize");
		int num = ts[0];
		int den = ts[1];
		int qNum = qz[0];
		int qDen = qz[1];
		if (qDen % den != 0) {
			throw new IllegalArgumentException("Quantize " + quantize + " 無法被拍號 " + timeSig
					+ " 整除（SimpleInput 要求 L: 能整除 M:）");
		}
		int unitsPerBeat = qDen / den;
		int totalUnits = num * unitsPerBeat;

		List<int[]> notes = new ArrayList<>(); // {note, velocity, begin, end}
		NodeList nl = pattern.getElementsByTagName("Note");
		for (int i = 0; i < nl.getLength(); i++) {
			Element e = (Element) nl.item(i);
			notes.add(new int[] {
				Integer.parseInt(e.getAttribute("Note").trim()),
				Integer.parseInt(e.getAttribute("Velocity").trim()),
				Integer.parseInt(e.getAttribute("Begin").trim()),
				Integer.parseInt(e.getAttribute("End").trim())
			});
		}
		notes.sort(Comparator.comparingInt(n -> n[2]));

		List<Ev> events = new ArrayList<>();
		int cursor = 0;
		for (int[] n : notes) {
			String token = tokenFor(n[0]);
			if (token == null) {
				continue;
			}
			int onset = n[2] - 1;
			int dur = n[3] - n[2] + 1;
			if (dur <= 0) {
				dur = 1;
			}
			if (onset < cursor) {
				// SimpleInput 事件不能重疊：截斷前一個事件
				Ev prev = events.get(events.size() - 1);
				prev.dur = onset - prev.onset;
				if (prev.dur <= 0) {
					events.remove(events.size() - 1);
				}
				cursor = onset;
			}
			if (onset > cursor) {
				events.add(new Ev(cursor, onset - cursor, "z", null));
			}
			events.add(new Ev(onset, dur, token, n[1]));
			cursor = onset + dur;
		}

		int total = 0;
		for (Ev e : events) {
			total = Math.max(total, e.onset + e.dur);
		}
		int measureCount = Math.max(1, (total + totalUnits - 1) / totalUnits);

		List<List<Ev>> measures = new ArrayList<>();
		for (int m = 0; m < measureCount; m++) {
			measures.add(new ArrayList<>());
		}
		for (Ev e : events) {
			int start = e.onset;
			int end = e.onset + e.dur;
			while (start < end) {
				int m = start / totalUnits;
				int segEnd = Math.min(end, (m + 1) * totalUnits);
				measures.get(m).add(new Ev(start - m * totalUnits, segEnd - start, e.token, e.velocity));
				start = segEnd;
			}
		}

		List<String> rendered = new ArrayList<>();
		for (List<Ev> measure : measures) {
			rendered.add(render(measure, totalUnits, rests, velocity));
		}

		if (full && name == null) {
			String base = input.getName();
			if (base.toLowerCase().endsWith(".strumseq")) {
				base = base.substring(0, base.length() - 9);
			}
			name = base;
		}
		if (name != null && rendered.size() > 1) {
			throw new IllegalArgumentException("多小節 pattern 無法用單一 @ 定義表示；請改用 StrumSeqImporter（需指定和弦）");
		}

		StringBuilder out = new StringBuilder();
		if (full) {
			out.append("M:").append(num).append('/').append(den).append('\n');
			out.append("L:").append(qNum).append('/').append(qDen).append('\n');
			if (bpm != null && !bpm.isEmpty()) {
				out.append("Q:1/4=").append(bpm).append('\n');
			}
			out.append('\n');
		}
		String joined = String.join(" | ", rendered);
		if (name != null) {
			out.append('@').append(sanitize(name)).append(" = ").append(joined).append('\n');
		} else {
			out.append(joined).append('\n');
		}
		System.out.print(out);
	}

	private static String render(List<Ev> evs, int totalUnits, boolean rests, boolean velocity) {
		evs.sort(Comparator.comparingInt(e -> e.onset));
		StringBuilder sb = new StringBuilder();
		int cursor = 0;
		for (Ev e : evs) {
			if (e.onset > cursor && rests) {
				appendRest(sb, e.onset - cursor);
			}
			if (e.token.startsWith("@seq")) {
				// @seqN 不吃時值，只能單獨佔滿整個小節
				if (evs.size() == 1) {
					return e.token;
				}
				cursor = e.onset + e.dur;
				continue;
			}
			sb.append(withDuration(e.token, e.dur));
			if (velocity && e.velocity != null) {
				String vn = velocityName(e.velocity);
				if (!"f".equals(vn)) {
					sb.append('!').append(vn);
				}
			}
			sb.append(' ');
			cursor = e.onset + e.dur;
		}
		if (rests && cursor < totalUnits) {
			appendRest(sb, totalUnits - cursor);
		}
		return sb.toString().trim();
	}

	/** 把時值插在 token 的冒號之前：d:321 + 4 → d4:321；mu + 2 → mu2 */
	private static String withDuration(String token, int dur) {
		if (dur == 1) {
			return token;
		}
		int colon = token.indexOf(':');
		if (colon >= 0) {
			return token.substring(0, colon) + dur + token.substring(colon);
		}
		return token + dur;
	}

	private static void appendRest(StringBuilder sb, int units) {
		sb.append('z');
		if (units != 1) {
			sb.append(units);
		}
		sb.append(' ');
	}

	/** .strumseq Note → SimpleInput token；null = 忽略 */
	private static String tokenFor(int note) {
		switch (note) {
			case 72: return "d";            // Open Down Bass/Full
			case 76: return "u";            // Open Up Full
			case 74: return "d:321";        // Open Down Treble
			case 83: return "mu";           // Mute Up Full
			case 81: return "md:321";       // Mute Down Treble
			case 79: return "md";           // Mute Down Bass
			case 78: return "X";            // Mute 2（TuxGuitar 無法與 Mute 1 區分）
			case 77: return "X";            // Mute 1
			case 62: return String.valueOf(toUserString(6));
			case 64: return String.valueOf(toUserString(5));
			case 65: return String.valueOf(toUserString(4));
			case 67: return String.valueOf(toUserString(3));
			case 69: return String.valueOf(toUserString(2));
			case 71: return String.valueOf(toUserString(1));
			case 60: return "@seq1";
			case 61: return "@seq2";
			case 63: return "@seq3";
			case 66: return "@seq4";
			case 68: return "@seq5";
			case 70: return "@seq6";
			case 73: return "@seq7";
			case 75: return "@seq8";
			default:
				return null; // 36~59 左手和弦槽位、55 Bass I、其他未知 Note
		}
	}

	/** 物理弦號 → SimpleInput 使用者弦號（1 與 3 對調） */
	private static int toUserString(int realString) {
		if (realString == 1) {
			return 3;
		}
		if (realString == 3) {
			return 1;
		}
		return realString;
	}

	private static String velocityName(int velocity) {
		int best = 0;
		for (int i = 1; i < VEL_LEVELS.length; i++) {
			if (Math.abs(VEL_LEVELS[i] - velocity) < Math.abs(VEL_LEVELS[best] - velocity)) {
				best = i;
			}
		}
		return VEL_NAMES[best];
	}

	private static int[] fraction(String value, String label) {
		if (value == null || !value.matches("\\d+\\s*/\\s*\\d+")) {
			throw new IllegalArgumentException(label + " 格式不正確: " + value);
		}
		String[] parts = value.split("/");
		return new int[] { Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()) };
	}

	private static String sanitize(String name) {
		String s = name.replaceAll("[^A-Za-z0-9_]", "_");
		if (s.isEmpty() || Character.isDigit(s.charAt(0))) {
			s = "p" + s;
		}
		return s;
	}

	private static void usage() {
		System.out.println("用法: java StrumSeqToSymbols.java <input.strumseq> [--name 名稱] [--full] [--no-rests] [--velocity]");
		System.out.println("  --name 名稱   輸出 `@名稱 = ...`（僅限單一小節）");
		System.out.println("  --full        加上 M: / L: / Q: 標頭（隱含 --name，未給時用檔名）");
		System.out.println("  --no-rests    省略休止符 z");
		System.out.println("  --velocity    加上 !ppp~!fff 力度");
	}
}
