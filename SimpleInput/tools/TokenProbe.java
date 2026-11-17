import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 臨時診斷：印出 parseMeasure 的 tokenizer 結果。 */
public class TokenProbe {
	public static void main(String[] args) {
		String text = args.length > 0 ? args[0] : "G(4) [d u d] d3 u3";
		Matcher m = Pattern.compile("([A-G][A-Za-z0-9#b]*(?:@\\d+)?(?:\\(\\d+\\))?)|(@[A-Za-z0-9_]+(?:!\\w+)?)|(\\([1-6](?:\\s*=\\s*\\d+)?(?:\\s*[1-6](?:\\s*=\\s*\\d+)?)*\\)(?:\\d*(?:[*/]\\d+)?)?(?:!(?:fff|ff|f|ppp|pp|p|mp|mf))?)|(\\[[^\\]]*\\])|([^\\s]+)").matcher(text);
		while (m.find()) {
			System.out.println("[" + m.group() + "]");
		}
	}
}
