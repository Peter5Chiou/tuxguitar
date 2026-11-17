import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import app.tuxguitar.io.simpleinput.SimpleInputParser;
import app.tuxguitar.io.simpleinput.SimpleInputSong;
import app.tuxguitar.io.simpleinput.SimpleStringUtil;

/**
 * Ample Guitar Strummer Pattern (.strumseq) → SimpleInput (.strum) 轉換工具.
 *
 * .strumseq 的 Note 編號與 AmpleGuitarExporter 的右手觸發音符相同：
 *   72 = Down Full, 76 = Up Full, 74 = Down Mid/Treble,
 *   62/64/65/67/69/71 = 物理弦 6/5/4/3/2/1,
 *   60/61/63/66/68/70/73/75 = SEQ 1~8, 55 = Bass I,
 *   36~59 = 左手和弦槽位（忽略）.
 *
 * Begin/End 是以 Quantize 為單位的 1-based 格線位置，End 為含括（inclusive），
 * 所以時值 = End - Begin + 1 格。
 *
 * 用法:
 *   java -cp "...;tools" StrumSeqImporter <in.strumseq> [--chords "G,C,Am,F"]
 *        [--bars N] [--out out.strum] [--validate]
 */
public class StrumSeqImporter {

	/** 8 段力度對應的 MIDI velocity（與 AmpleGuitarExporter.velocityOf 一致） */
	private static final int[] VEL_LEVELS = { 20, 35, 50, 68, 85, 100, 115, 127 };
	private static final String[] VEL_NAMES = { "ppp", "pp", "p", "mp", "mf", "f", "ff", "fff" };

	/** 一個事件（以格線單位表示） */
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

	private static class SeqNote {
		int note;
		int velocity;
		int begin;
		int end;
	}

	public static void main(String[] args) {
		File input = null;
		File out = null;
		List<String> chords = new ArrayList<>();
		int repeatBars = 1;
		boolean validate = false;

		for (int i = 0; i < args.length; i++) {
			switch (args[i]) {
				case "--chords":
					for (String c : args[++i].split(",")) {
						if (!c.trim().isEmpty()) {
							chords.add(c.trim());
						}
					}
					break;
				case "--bars":
					repeatBars = Integer.parseInt(args[++i]);
					break;
				case "--out":
					out = new File(args[++i]);
					break;
				case "--validate":
					validate = true;
					break;
				default:
					input = new File(args[i]);
					break;
			}
		}
		if (input == null) {
			System.err.println("用法: java StrumSeqImporter <in.strumseq> [--chords \"G,C,Am,F\"] [--bars N] [--out out.strum] [--validate]");
			System.exit(1);
		}
		if (chords.isEmpty()) {
			chords.add("G");
		}
		try {
			String text = convert(input, chords, repeatBars);
			if (out == null) {
				String name = input.getName();
				String base = name.toLowerCase().endsWith(".strumseq")
						? name.substring(0, name.length() - 9) : name;
				out = new File(input.getParentFile(), base + ".strum");
			}
			Files.write(out.toPath(), text.getBytes(StandardCharsets.UTF_8));
			System.out.println("產生 .strum 成功: " + out.getAbsolutePath());
			System.out.println("----");
			System.out.println(text);
			System.out.println("----");
			if (validate) {
				SimpleInputSong song = new SimpleInputParser().parse(text);
				System.out.println("驗證通過: " + song.measures.size() + " 小節, 拍號 "
						+ song.header.numerator + "/" + song.header.denominator
						+ ", L:1/" + song.header.unitDenominator
						+ ", tempo=" + song.header.tempo);
			}
		} catch (Exception e) {
			System.err.println("轉換失敗: " + e.getMessage());
			e.printStackTrace();
			System.exit(2);
		}
	}

	public static String convert(File input, List<String> chords, int repeatBars) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = factory.newDocumentBuilder();
		Document doc = builder.parse(input);
		Element pattern = doc.getDocumentElement();

		String timeSig = pattern.getAttribute("Time-Signature");
		String quantize = pattern.getAttribute("Quantize");
		String bpm = pattern.getAttribute("BPM");
		String author = pattern.getAttribute("Author");
		String category = pattern.getAttribute("Category");

		int[] ts = parseFraction(timeSig, "Time-Signature");
		int[] qz = parseFraction(quantize, "Quantize");
		int num = ts[0];
		int den = ts[1];
		int qNum = qz[0];
		int qDen = qz[1];

		if (qDen % den != 0) {
			throw new IllegalArgumentException("Quantize " + quantize + " 無法被拍號 " + timeSig
					+ " 整除（SimpleInput 要求 L: 能整除 M:）");
		}
		int unitsPerBeat = qDen / den;
		int totalUnitsPerMeasure = num * unitsPerBeat;

		List<SeqNote> notes = new ArrayList<>();
		NodeList nl = pattern.getElementsByTagName("Note");
		for (int i = 0; i < nl.getLength(); i++) {
			Element e = (Element) nl.item(i);
			SeqNote n = new SeqNote();
			n.note = Integer.parseInt(e.getAttribute("Note").trim());
			n.velocity = Integer.parseInt(e.getAttribute("Velocity").trim());
			n.begin = Integer.parseInt(e.getAttribute("Begin").trim());
			n.end = Integer.parseInt(e.getAttribute("End").trim());
			notes.add(n);
		}
		notes.sort(Comparator.comparingInt(n -> n.begin));

		List<String> warnings = new ArrayList<>();
		List<Ev> events = new ArrayList<>();
		int cursor = 0;
		for (SeqNote n : notes) {
			String token = tokenFor(n.note, warnings);
			if (token == null) {
				continue;
			}
			int onset = n.begin - 1;
			int dur = n.end - n.begin + 1;
			if (dur <= 0) {
				warnings.add("Note " + n.note + " 的 End(" + n.end + ") < Begin(" + n.begin + ")，以 1 格處理");
				dur = 1;
			}
			if (onset < cursor) {
				// SimpleInput 事件是嚴格序列，無法表達重疊：截斷前一個事件
				Ev prev = events.get(events.size() - 1);
				prev.dur = onset - prev.onset;
				warnings.add("重疊事件 (Begin=" + n.begin + ")，已截斷前一個事件");
				if (prev.dur <= 0) {
					events.remove(events.size() - 1);
				}
				cursor = onset;
			}
			if (onset > cursor) {
				events.add(new Ev(cursor, onset - cursor, "z", null));
			}
			events.add(new Ev(onset, dur, token, n.velocity));
			cursor = onset + dur;
		}

		int total = 0;
		for (Ev e : events) {
			total = Math.max(total, e.onset + e.dur);
		}
		int measureCount = Math.max(1, (total + totalUnitsPerMeasure - 1) / totalUnitsPerMeasure);

		// 依小節切分（跨小節的延音會被切成兩個事件）
		List<List<Ev>> measures = new ArrayList<>();
		for (int m = 0; m < measureCount; m++) {
			measures.add(new ArrayList<>());
		}
		for (Ev e : events) {
			int start = e.onset;
			int end = e.onset + e.dur;
			while (start < end) {
				int m = start / totalUnitsPerMeasure;
				int segEnd = Math.min(end, (m + 1) * totalUnitsPerMeasure);
				measures.get(m).add(new Ev(start - m * totalUnitsPerMeasure, segEnd - start, e.token, e.velocity));
				start = segEnd;
			}
		}

		List<String> rendered = new ArrayList<>();
		for (List<Ev> measure : measures) {
			rendered.add(renderMeasure(measure, totalUnitsPerMeasure, warnings));
		}

		String baseName = input.getName();
		if (baseName.toLowerCase().endsWith(".strumseq")) {
			baseName = baseName.substring(0, baseName.length() - 9);
		}
		String patternName = sanitize(baseName);

		StringBuilder sb = new StringBuilder();
		sb.append("% 由 ").append(input.getName()).append(" 轉換（StrumSeqImporter）\n");
		sb.append("% Author=").append(author).append(", Category=").append(category)
		  .append(", Quantize=").append(quantize).append(", BPM=").append(bpm).append('\n');
		if (!warnings.isEmpty()) {
			for (String w : warnings) {
				sb.append("% WARN: ").append(w).append('\n');
			}
		}
		sb.append("M:").append(num).append('/').append(den).append('\n');
		sb.append("L:").append(qNum).append('/').append(qDen).append('\n');
		if (bpm != null && !bpm.isEmpty()) {
			sb.append("Q:1/4=").append(bpm).append('\n');
		}
		sb.append('\n');

		boolean allSame = true;
		for (String r : rendered) {
			if (!r.equals(rendered.get(0))) {
				allSame = false;
				break;
			}
		}

		if (allSame) {
			sb.append('@').append(patternName).append(" = ").append(rendered.get(0)).append('\n');
			sb.append('\n');
			for (int b = 0; b < repeatBars; b++) {
				String chord = chords.get(b % chords.size());
				sb.append(chord).append('(').append(num).append(") @").append(patternName);
				sb.append(b == repeatBars - 1 ? "\n" : " |\n");
			}
		} else {
			for (int b = 0; b < repeatBars; b++) {
				for (int m = 0; m < rendered.size(); m++) {
					String chord = chords.get((b * rendered.size() + m) % chords.size());
					sb.append(chord).append('(').append(num).append(") ").append(rendered.get(m));
					boolean last = (b == repeatBars - 1 && m == rendered.size() - 1);
					sb.append(last ? "\n" : " |\n");
				}
			}
		}
		return sb.toString();
	}

	/** 把一個小節的事件串成 SimpleInput 事件序列（空隙補 z） */
	private static String renderMeasure(List<Ev> evs, int totalUnits, List<String> warnings) {
		evs.sort(Comparator.comparingInt(e -> e.onset));
		StringBuilder sb = new StringBuilder();
		int cursor = 0;
		for (Ev e : evs) {
			if (e.onset > cursor) {
				appendRest(sb, e.onset - cursor);
			}
			if (e.token.startsWith("@seq")) {
				// @seqN 不吃時值，只能單獨佔滿整個小節
				if (evs.size() == 1) {
					return e.token;
				}
				warnings.add(e.token + " 與其他事件混用，已略過");
				cursor = e.onset + e.dur;
				continue;
			}
			sb.append(withDuration(e.token, e.dur));
			if (e.velocity != null) {
				String vn = velocityName(e.velocity);
				if (!"f".equals(vn)) {
					sb.append('!').append(vn);
				}
			}
			sb.append(' ');
			cursor = e.onset + e.dur;
		}
		if (cursor < totalUnits) {
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

	/** 休止：1 格寫 z，多格寫 zN */
	private static void appendRest(StringBuilder sb, int units) {
		sb.append('z');
		if (units != 1) {
			sb.append(units);
		}
		sb.append(' ');
	}

	/** .strumseq Note → SimpleInput token；null = 忽略 */
	private static String tokenFor(int note, List<String> warnings) {
		switch (note) {
			case 72: return "d";            // Open Down Bass/Full
			case 76: return "u";            // Open Up Full
			case 74: return "d:321";        // Open Down Treble（只刷高音 3 弦）
			case 83: return "mu";           // Mute Up Full
			case 81: return "md:321";       // Mute Down Treble
			case 79: return "md";           // Mute Down Bass
			case 78:
				warnings.add("Note 78 (Mute 2) 與 Mute 1 在 TuxGuitar 無法區分，以 X 表示");
				return "X";
			case 77: return "X";            // Mute 1 (Slap/Choke 1)
			case 62: return String.valueOf(SimpleStringUtil.toUserString(6));
			case 64: return String.valueOf(SimpleStringUtil.toUserString(5));
			case 65: return String.valueOf(SimpleStringUtil.toUserString(4));
			case 67: return String.valueOf(SimpleStringUtil.toUserString(3));
			case 69: return String.valueOf(SimpleStringUtil.toUserString(2));
			case 71: return String.valueOf(SimpleStringUtil.toUserString(1));
			case 60: return "@seq1";
			case 61: return "@seq2";
			case 63: return "@seq3";
			case 66: return "@seq4";
			case 68: return "@seq5";
			case 70: return "@seq6";
			case 73: return "@seq7";
			case 75: return "@seq8";
			case 55:
				warnings.add("Note 55 (Bass I) 無對應事件，已略過");
				return null;
			default:
				if (note >= 36 && note <= 59) {
					return null; // 左手和弦槽位切換，非節奏
				}
				warnings.add("未知 Note " + note + "，已略過");
				return null;
		}
	}

	/** 原始 velocity → 最接近的 8 段力度名稱 */
	private static String velocityName(int velocity) {
		int best = 0;
		for (int i = 1; i < VEL_LEVELS.length; i++) {
			if (Math.abs(VEL_LEVELS[i] - velocity) < Math.abs(VEL_LEVELS[best] - velocity)) {
				best = i;
			}
		}
		return VEL_NAMES[best];
	}

	private static int[] parseFraction(String value, String label) {
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
}
