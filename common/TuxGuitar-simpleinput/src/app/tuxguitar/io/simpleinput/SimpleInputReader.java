package app.tuxguitar.io.simpleinput;

import app.tuxguitar.io.base.TGFileFormat;
import app.tuxguitar.io.base.TGFileFormatException;
import app.tuxguitar.io.base.TGSongReader;
import app.tuxguitar.io.base.TGSongReaderHandle;

public class SimpleInputReader implements TGSongReader {

	public static final TGFileFormat FILE_FORMAT = new TGFileFormat("SimpleInput Strum", "text/x-strum", new String[]{"strum"});

	@Override
	public TGFileFormat getFileFormat() {
		return FILE_FORMAT;
	}

	@Override
	public void read(TGSongReaderHandle handle) throws TGFileFormatException {
		try {
			String source = SimpleInputTextReader.readAll(handle.getInputStream());
			SimpleInputParser parser = new SimpleInputParser();
			SimpleInputSong parsed = parser.parse(source);
			handle.setSong(new SimpleInputSongBuilder(handle.getFactory()).build(parsed));
		} catch (SimpleInputFormatException throwable) {
			throw new TGFileFormatException(throwable.getMessage(), throwable);
		} catch (Throwable throwable) {
			throw new TGFileFormatException(throwable);
		}
	}
}
