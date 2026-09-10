package app.tuxguitar.io.simpleinput;

/** 解析/驗證錯誤。訊息格式：「第 N 行、第 M 小節：描述」 */
public class SimpleInputFormatException extends Exception {

	private static final long serialVersionUID = 1L;

	public SimpleInputFormatException(String message) {
		super(message);
	}

	public SimpleInputFormatException(String message, Throwable cause) {
		super(message, cause);
	}
}
