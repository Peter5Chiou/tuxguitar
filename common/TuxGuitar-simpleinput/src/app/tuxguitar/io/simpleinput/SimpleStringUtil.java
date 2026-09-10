package app.tuxguitar.io.simpleinput;

import java.util.ArrayList;
import java.util.List;

/** 弦編號映射：使用者編號 → 實際弦（1↔3 對調，其餘相同） */
public class SimpleStringUtil {

	public static int toRealString(int userString) {
		switch (userString) {
			case 1: return 3;
			case 3: return 1;
			default: return userString;
		}
	}

	/** 實際弦號 → 使用者弦號；目前 1、3 對調，其餘不變。 */
	public static int toUserString(int realString) {
		return toRealString(realString);
	}

	public static List<Integer> toRealStrings(List<Integer> userStrings) {
		List<Integer> result = new ArrayList<>();
		for (Integer s : userStrings) {
			result.add(toRealString(s));
		}
		return result;
	}
}
