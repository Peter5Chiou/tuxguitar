package app.tuxguitar.io.simpleinput;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 解析後的中間表示（IR），與 TuxGuitar 模型解耦 */
public class SimpleInputSong {

	public static class Header {
		public int numerator = 4;
		public int denominator = 4;
		public int unitNumerator = 1;    // L: 分子，如 1/8 → 1
		public int unitDenominator = 8;  // L: 分母
		public Integer tempo;            // Q: BPM，可為 null
		public String key;               // K:，可為 null
	}

	/** 一個刷法/單音/休止事件（@ 展開後） */
	public static class Event {
		public char type;          // 'd','u','t','n','c'(chord-pick),'r'(rest),'x'(muted strum)
		public double durationUnits;  // 單位數（縮放後）
		public List<Integer> strings; // 使用者編號（1~6），null = 全和弦
		public Map<Integer, Integer> forcedFrets; // 使用者弦號 → 強制品位（如 2:3），可為 null
		public Integer strokeDenominator; // 刷速（~N，N=4~64 分音符）；null = 依事件時值自動推導
		public int tuplet;        // 連音數（0 = 非連音；3/5/6 = 三/五/六連音）
		public int line;           // 來源行（錯誤報告用）
	}

	/** 小節內一個和弦段落：和弦名 + 持續單位 + 事件序列 */
	public static class ChordSegment {
		public String chordName;
		public Integer position; // 指定把位；null = 使用和弦字典原始按型
		public int durationUnits;
		public List<Event> events = new ArrayList<>();
		public int line;
		public int measureNumber;
	}

	public static class Measure {
		public List<ChordSegment> segments = new ArrayList<>();
		public int line;
	}

	public Header header = new Header();
	public Map<String, String> definitions = new LinkedHashMap<>(); // @名稱 → 原始內容
	public List<Measure> measures = new ArrayList<>();
	public boolean pickup; // 第一小節為弱起
}
