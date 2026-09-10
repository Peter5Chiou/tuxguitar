package app.tuxguitar.io.simpleinput;

import app.tuxguitar.io.base.TGFileFormatDetector;
import app.tuxguitar.io.base.TGSongReader;
import app.tuxguitar.io.plugin.TGSongReaderPlugin;
import app.tuxguitar.util.TGContext;
import app.tuxguitar.util.plugin.TGPluginException;

public class SimpleInputReaderPlugin extends TGSongReaderPlugin {

	public static final String MODULE_ID = "tuxguitar-simpleinput";

	public SimpleInputReaderPlugin() {
		super(false); // 非 common 格式 → 列入 File→Import 選單（同 MIDI）
	}

	@Override
	protected TGSongReader createInputStream(TGContext context) {
		return new SimpleInputReader();
	}

	@Override
	protected TGFileFormatDetector createFileFormatDetector(TGContext context) throws TGPluginException {
		return new SimpleInputFileFormatDetector();
	}

	@Override
	public String getModuleId() {
		return MODULE_ID;
	}
}
