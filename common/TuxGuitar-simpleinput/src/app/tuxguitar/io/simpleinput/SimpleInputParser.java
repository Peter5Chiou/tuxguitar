package app.tuxguitar.io.simpleinput;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * .strum 文字格式解析器：Lexer + Parser + Validator.
 * 規格見 SimpleInput/docs/SPEC.md.
 */
public class SimpleInputParser {

	private static final Pattern CHORD_PATTERN = Pattern.compile("^([A-G][#b]?(?:m|maj|min|sus2|sus4|add9|dim|aug|[0-9])*)$");
	private static final Pattern HEADER_M = Pattern.compile("M:\\s*(\\d+)\\s*/\\s*(\\d+)");
	private static final Pattern HEADER_L = Pattern.compile("L:\\s*(\\d+)\\s*/\\s*(\\d+)");
	private static final Pattern HEADER_Q = Pattern.compile("Q:\\s*(\\d+)\\s*/\\s*(\\d+)\\s*=\\s*(\\d+)");
	private static final Pattern HEADER_K = Pattern.compile("K:\\s*(\\S+)");
	private static final Pattern CHORD_TOKEN_PATTERN = Pattern.compile("^([A-G][A-Za-z0-9#b]*)(?:\\((\\d+)\\))?$");

	private int totalUnitsPerMeasure; // 每小節的基礎單位數
	private int unitsPerBeat;         // 每拍的基礎單位數 = unitDenominator / denominator

	public SimpleInputSong parse(String source) throws SimpleInputFormatException {
		SimpleInputSong song = new SimpleInputSong();
		List<String> bodyLines = new ArrayList<>();

		String[] lines = source.split("\n", -1);
		boolean inBody = false;
		for (int i = 0; i < lines.length; i++) {
			String raw = lines[i];
			int lineNo = i + 1;
			String line = stripComment(raw).trim();
			if (line.isEmpty()) {
				continue;
			}
			if (!inBody) {
				if (line.startsWith("@")) {
					parseDefinition(line, lineNo, song);
					continue;
				}
				if (tryParseHeader(line, lineNo, song)) {
					continue;
				}
				inBody = true;
			}
			bodyLines.add(line);
			// 保留行號：以平行結構記錄
			bodyLineNumbers.add(lineNo);
		}

		validateHeader(song);
		if (song.header.unitDenominator % song.header.denominator != 0) {
			throw new SimpleInputFormatException("SimpleInput: L: 單位必須能整除 M: 拍（如 M:4/4 + L:1/8、M:6/8 + L:1/16）");
		}
		this.unitsPerBeat = song.header.unitDenominator / song.header.denominator;
		this.totalUnitsPerMeasure = song.header.numerator * this.unitsPerBeat;

		parseBody(song, bodyLines);
		validateSegments(song);
		return song;
	}

	private final List<Integer> bodyLineNumbers = new ArrayList<>();

	private static String stripComment(String line) {
		int idx = line.indexOf('%');
		return idx >= 0 ? line.substring(0, idx) : line;
	}

	private void parseDefinition(String line, int lineNo, SimpleInputSong song) throws SimpleInputFormatException {
		int eq = line.indexOf('=');
		if (eq < 0) {
			throw new SimpleInputFormatException("SimpleInput: 第 " + lineNo + " 行：@ 定義缺少 '='");
		}
		String name = line.substring(0, eq).trim();
		String content = line.substring(eq + 1).trim();
		if (!name.startsWith("@") || name.length() < 2) {
			throw new SimpleInputFormatException("SimpleInput: 第 " + lineNo + " 行：無效的定義名稱「" + name + "」");
		}
		if (content.contains("@")) {
			throw new SimpleInputFormatException("SimpleInput: 第 " + lineNo + " 行：@ 定義不允許巢狀引用");
		}
		song.definitions.put(name.substring(1), content);
	}

	private boolean tryParseHeader(String line, int lineNo, SimpleInputSong song) throws SimpleInputFormatException {
		Matcher m;
		if ((m = HEADER_M.matcher(line)).matches()) {
			song.header.numerator = Integer.parseInt(m.group(1));
			song.header.denominator = Integer.parseInt(m.group(2));
			return true;
		}
		if ((m = HEADER_L.matcher(line)).matches()) {
			song.header.unitNumerator = Integer.parseInt(m.group(1));
			song.header.unitDenominator = Integer.parseInt(m.group(2));
			return true;
		}
		if ((m = HEADER_Q.matcher(line)).matches()) {
			// Q:1/4=90 → 以四分音符為基準的 BPM
			int num = Integer.parseInt(m.group(1));
			int den = Integer.parseInt(m.group(2));
			int bpm = Integer.parseInt(m.group(3));
			song.header.tempo = bpm * num * 4 / den; // 換算成四分音符 BPM
			return true;
		}
		if ((m = HEADER_K.matcher(line)).matches()) {
			song.header.key = m.group(1);
			return true;
		}
		return false;
	}

	private void validateHeader(SimpleInputSong song) throws SimpleInputFormatException {
		if (song.header.numerator <= 0 || song.header.denominator <= 0) {
			throw new SimpleInputFormatException("SimpleInput: 缺少必要的 M: 拍號標頭");
		}
		if (song.header.unitDenominator <= 0) {
			throw new SimpleInputFormatException("SimpleInput: 缺少必要的 L: 標頭");
		}
	}

	// ---- 譜面解析 ----

	private void parseBody(SimpleInputSong song, List<String> lines) throws SimpleInputFormatException {
		StringBuilder current = new StringBuilder();
		int currentLine = -1;

		List<String> measureTexts = new ArrayList<>();
		List<Integer> measureLineNos = new ArrayList<>();

		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			int lineNo = bodyLineNumbers.get(i);
			// 以 | 切小節；|| 或 |] 為結尾
			String[] parts = line.split("\\|", -1);
			for (int p = 0; p < parts.length; p++) {
				String part = parts[p].trim();
				boolean isFinal = p == parts.length - 1;
				if (!part.isEmpty()) {
					if (current.length() == 0) {
						currentLine = lineNo;
					}
					current.append(part);
				}
				if (!isFinal) {
					if (current.length() > 0) {
						measureTexts.add(current.toString());
						measureLineNos.add(currentLine);
						current = new StringBuilder();
					}
				}
			}
			// 行尾若以 |] 結束，視為最後小節結束
			if (line.endsWith("|]") && current.length() > 0) {
				measureTexts.add(current.toString());
				measureLineNos.add(currentLine);
				current = new StringBuilder();
			}
		}
		if (current.length() > 0) {
			measureTexts.add(current.toString());
			measureLineNos.add(currentLine);
		}

		for (int i = 0; i < measureTexts.size(); i++) {
			song.measures.add(parseMeasure(measureTexts.get(i), measureLineNos.get(i), i, song));
		}
		if (song.measures.isEmpty()) {
			throw new SimpleInputFormatException("SimpleInput: 沒有任何小節內容");
		}
	}

	private SimpleInputSong.Measure parseMeasure(String text, int lineNo, int index, SimpleInputSong song) throws SimpleInputFormatException {
		SimpleInputSong.Measure measure = new SimpleInputSong.Measure();
		measure.line = lineNo;

		// 以和弦記號切分段落：和弦名（可含數字）+ 可選括號拍數 + 後續事件
		Matcher m = Pattern.compile("([A-G][A-Za-z0-9#b]*(?:\\(\\d+\\))?)|(@[A-Za-z0-9_]+)|([^\\s]+)").matcher(text);
		List<String> tokens = new ArrayList<>();
		while (m.find()) {
			tokens.add(m.group());
		}

		int i = 0;
		while (i < tokens.size()) {
			String token = tokens.get(i);
			if (token.startsWith("@")) {
				String name = token.substring(1);
				String def = song.definitions.get(name);
				if (def == null) {
					throw new SimpleInputFormatException("SimpleInput: 第 " + lineNo + " 行、第 " + (index + 1) + " 小節：未定義的刷法「@" + name + "」");
				}
				// @ 展開：事件歸屬於前一個和弦段落（或獨立段落）
				List<SimpleInputSong.Event> events = parseEvents(def, lineNo, index, song);
				if (!measure.segments.isEmpty()) {
					measure.segments.get(measure.segments.size() - 1).events.addAll(events);
				} else {
					SimpleInputSong.ChordSegment seg = new SimpleInputSong.ChordSegment();
					seg.chordName = null;
					seg.durationUnits = 0;
					seg.events.addAll(events);
					seg.line = lineNo;
					measure.segments.add(seg);
				}
				i++;
				continue;
			}
			Matcher cm = CHORD_TOKEN_PATTERN.matcher(token);
			if (cm.matches() && CHORD_PATTERN.matcher(cm.group(1)).matches()) {
				SimpleInputSong.ChordSegment seg = new SimpleInputSong.ChordSegment();
				seg.line = lineNo;
				seg.chordName = cm.group(1);
				if (cm.group(2) != null) {
					int beats = Integer.parseInt(cm.group(2));
					seg.durationUnits = beats * this.unitsPerBeat; // 拍 → 基礎單位
				} else {
					seg.durationUnits = 0; // 無數字：由 pattern 總長決定
				}
				measure.segments.add(seg);
				i++;
				continue;
			}
			// 其他事件（d/u/t/數字/(..)/z/-）→ 歸屬前一個和弦段落
			List<SimpleInputSong.Event> events = parseEvents(token, lineNo, index, song);
			if (!measure.segments.isEmpty()) {
				measure.segments.get(measure.segments.size() - 1).events.addAll(events);
			} else {
				SimpleInputSong.ChordSegment seg = new SimpleInputSong.ChordSegment();
				seg.line = lineNo;
				seg.events.addAll(events);
				measure.segments.add(seg);
			}
			i++;
		}
		return measure;
	}

	/** 解析事件序列（pattern 字串或 token），回傳事件清單 */
	private List<SimpleInputSong.Event> parseEvents(String text, int lineNo, int measureIndex, SimpleInputSong song) throws SimpleInputFormatException {
		List<SimpleInputSong.Event> events = new ArrayList<>();
		// d/u/t/x/z/- [數字] [*|/倍數] [:弦...] 或 弦=品 強制（如 2=3）；(23) 多弦單音；單一數字=單弦
		Pattern p = Pattern.compile(
			"(d|u|t|x|z|-)(?:(\\d+)?([*/])(\\d+)|(\\d+))?(?::([1-6]+))?|" +  // 刷/根音/悶/休止
			"([1-6])=(\\d+)(?:(\\d+)?([*/])(\\d+)|(\\d+))?|" + // 弦=強制品位（如 2=3）
			"(\\()([1-6]+)(\\))|" +                   // (23)
			"([1-6])(?:(\\d+)?([*/])(\\d+)|(\\d+))?"); // 單弦及時值
		Matcher m = p.matcher(text);
		int last = 0;
		while (m.find()) {
			if (!text.substring(last, m.start()).trim().isEmpty()) {
				throw new SimpleInputFormatException("SimpleInput: 第 " + lineNo + " 行、第 " + (measureIndex + 1) + " 小節：無法解析的符號「" + text.substring(last, m.start()).trim() + "」");
			}
			last = m.end();
			if (m.group(16) != null) { // 單弦
				addEvent(events, 'n', duration(m.group(17), m.group(18), m.group(19), m.group(20)), m.group(16), lineNo, measureIndex);
				continue;
			}
			if (m.group(13) != null) { // (23)
				addEvent(events, 'n', 1, m.group(14), lineNo, measureIndex);
				continue;
			}
			if (m.group(7) != null) { // 2=3 強制品位
				addForcedEvent(events, Integer.parseInt(m.group(7)), Integer.parseInt(m.group(8)),
					duration(m.group(9), m.group(10), m.group(11), m.group(12)), lineNo, measureIndex);
				continue;
			}
			char type = m.group(1).charAt(0);
			double units = duration(m.group(2), m.group(3), m.group(4), m.group(5));
			addEvent(events, type == '-' ? 'r' : type, units, m.group(6), lineNo, measureIndex);
		}
		if (!text.substring(last).trim().isEmpty()) {
			throw new SimpleInputFormatException("SimpleInput: 第 " + lineNo + " 行、第 " + (measureIndex + 1) + " 小節：無法解析的符號「" + text.substring(last).trim() + "」");
		}
		return events;
	}

	private double duration(String base, String operator, String factor, String direct) throws SimpleInputFormatException {
		double units = direct != null ? Integer.parseInt(direct) : (base != null ? Integer.parseInt(base) : 1);
		if (operator != null) {
			int value = Integer.parseInt(factor);
			if (value <= 0) {
				throw new SimpleInputFormatException("SimpleInput: 時值倍率必須為正數");
			}
			units = operator.charAt(0) == '*' ? units * value : units / value;
		}
		return units;
	}

	private void addEvent(List<SimpleInputSong.Event> events, char type, double units, String strings, int lineNo, int measureIndex) throws SimpleInputFormatException {
		if (units <= 0) {
			throw new SimpleInputFormatException("SimpleInput: 第 " + lineNo + " 行、第 " + (measureIndex + 1) + " 小節：時值必須為正數");
		}
		SimpleInputSong.Event ev = new SimpleInputSong.Event();
		ev.type = type;
		ev.durationUnits = units;
		ev.line = lineNo;
		if (strings != null) {
			ev.strings = new ArrayList<>();
			for (char c : strings.toCharArray()) {
				ev.strings.add(Character.getNumericValue(c));
			}
		}
		events.add(ev);
	}

	/** 強制品位事件（如 2=3）：n 類型 + forcedFrets */
	private void addForcedEvent(List<SimpleInputSong.Event> events, int userString, int fret, double units, int lineNo, int measureIndex) throws SimpleInputFormatException {
		if (fret < 0 || fret > 24) {
			throw new SimpleInputFormatException("SimpleInput: 第 " + lineNo + " 行、第 " + (measureIndex + 1) + " 小節：強制品位 " + fret + " 超出範圍");
		}
		SimpleInputSong.Event ev = new SimpleInputSong.Event();
		ev.type = 'n';
		ev.durationUnits = units;
		ev.line = lineNo;
		ev.strings = new ArrayList<>();
		ev.strings.add(userString);
		ev.forcedFrets = new java.util.LinkedHashMap<>();
		ev.forcedFrets.put(userString, fret);
		events.add(ev);
	}

	// ---- 驗證 ----

	/** 計算段落 pattern 事件的總基礎單位數 */
	private double patternUnits(SimpleInputSong.ChordSegment seg) {
		double total = 0;
		for (SimpleInputSong.Event ev : seg.events) {
			total += ev.durationUnits;
		}
		return total;
	}

	private void validateSegments(SimpleInputSong song) throws SimpleInputFormatException {
		int n = song.measures.size();
		for (int i = 0; i < n; i++) {
			SimpleInputSong.Measure measure = song.measures.get(i);
			int measureNo = i + 1;
			double total = 0;
			for (SimpleInputSong.ChordSegment seg : measure.segments) {
				double pUnits = patternUnits(seg);
				if (seg.durationUnits > 0 && pUnits > 0 && !sameUnits(pUnits, seg.durationUnits)) {
					throw new SimpleInputFormatException("SimpleInput: 第 " + seg.line + " 行、第 " + measureNo + " 小節：和弦「" + seg.chordName
						+ "」持續 " + seg.durationUnits + " 單位，但 pattern 共 " + pUnits + " 單位，兩者不符");
				}
				total += Math.max(seg.durationUnits, pUnits);
			}
			boolean isFirst = i == 0;
			boolean isLast = i == n - 1;
			if (!sameUnits(total, totalUnitsPerMeasure)) {
				if (isFirst && total < totalUnitsPerMeasure) {
					song.pickup = true;
					continue; // 弱起：合計檢查在最後
				}
				String kind = isFirst ? "（弱起）" : "";
				throw new SimpleInputFormatException("SimpleInput: 第 " + measure.line + " 行、第 " + measureNo + " 小節" + kind
					+ "：總時值 " + total + " 單位，應為 " + totalUnitsPerMeasure + " 單位");
			}
			if (isFirst) {
				song.pickup = false;
			}
		}
		// 弱起 + 末小節合計檢查
		if (song.pickup && song.measures.size() >= 2) {
			double lastTotal = 0;
			SimpleInputSong.Measure last = song.measures.get(n - 1);
			for (SimpleInputSong.ChordSegment seg : last.segments) {
				lastTotal += Math.max(seg.durationUnits, patternUnits(seg));
			}
			double firstTotal = 0;
			SimpleInputSong.Measure first = song.measures.get(0);
			for (SimpleInputSong.ChordSegment seg : first.segments) {
				firstTotal += Math.max(seg.durationUnits, patternUnits(seg));
			}
			if (!sameUnits(lastTotal, totalUnitsPerMeasure - firstTotal)) {
				throw new SimpleInputFormatException("SimpleInput: 第 " + last.line + " 行、第 " + n + " 小節：末小節 " + lastTotal
					+ " 單位 + 弱起 " + firstTotal + " 單位 = " + (lastTotal + firstTotal) + " 單位，應為 " + totalUnitsPerMeasure + " 單位");
			}
		}
	}

	private boolean sameUnits(double left, double right) {
		return Math.abs(left - right) < 0.000001;
	}
}
