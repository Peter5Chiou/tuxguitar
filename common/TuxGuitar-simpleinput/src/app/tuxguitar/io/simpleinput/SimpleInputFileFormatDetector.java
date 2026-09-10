package app.tuxguitar.io.simpleinput;

import app.tuxguitar.io.base.TGFileFormatDetector;
import app.tuxguitar.io.base.TGFileFormat;
import java.io.InputStream;

public class SimpleInputFileFormatDetector implements TGFileFormatDetector {

	@Override
	public TGFileFormat getFileFormat(InputStream stream) {
		// .strum 是純文字；偵測交由副檔名（TGFileFormat 的 extensions）處理
		return SimpleInputReader.FILE_FORMAT;
	}
}
