package app.tuxguitar.io.synth;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.Iterator;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import app.tuxguitar.io.base.TGFileFormat;
import app.tuxguitar.io.base.TGFileFormatException;
import app.tuxguitar.io.base.TGSongWriter;
import app.tuxguitar.io.base.TGSongWriterHandle;
import app.tuxguitar.midi.synth.TGAudioBuffer;
import app.tuxguitar.midi.synth.TGAudioBufferProcessor;
import app.tuxguitar.midi.synth.TGAudioLine;
import app.tuxguitar.midi.synth.TGSynthModel;
import app.tuxguitar.midi.synth.TGSynthSettings;
import app.tuxguitar.player.base.MidiChannel;
import app.tuxguitar.player.base.MidiParameters;
import app.tuxguitar.player.base.MidiPlayerException;
import app.tuxguitar.player.base.MidiSequenceParser;
import app.tuxguitar.song.managers.TGSongManager;
import app.tuxguitar.song.models.TGChannel;
import app.tuxguitar.song.models.TGChannelParameter;
import app.tuxguitar.song.models.TGSong;
import app.tuxguitar.util.TGContext;

public class TGSynthSongWriter implements TGSongWriter {

	public static final TGFileFormat FILE_FORMAT = new TGSynthAudioFormat();

	// Peak level used when normalizing the exported audio (90% of full scale).
	private static final float NORMALIZE_LEVEL = 0.9f;

	private TGContext context;

	public TGSynthSongWriter(TGContext context) {
		this.context = context;
	}

	public TGFileFormat getFileFormat() {
		return FILE_FORMAT;
	}

	public void write(TGSongWriterHandle handle) throws TGFileFormatException {
		try{
			TGSynthAudioSettings settings = handle.getContext().getAttribute(TGSynthAudioSettings.class.getName());
			if( settings == null ) {
				settings = new TGSynthAudioSettings();
			}

			OutputStream out = handle.getOutputStream();
			TGSong tgSong = handle.getSong();
			TGSongManager tgSongManager = new TGSongManager();

			MidiSequenceParser midiSequenceParser = new MidiSequenceParser(tgSong, tgSongManager, MidiSequenceParser.DEFAULT_EXPORT_FLAGS | MidiSequenceParser.ADD_BANK_SELECT);
			TGSynthSequenceHandler midiSequenceHandler = new TGSynthSequenceHandler(tgSong.countTracks());
			midiSequenceParser.parse(midiSequenceHandler);
			if(!midiSequenceHandler.getEvents().isEmpty()) {
				TGSynthModel synthModel = new TGSynthModel(this.context);
				TGAudioBufferProcessor audioProcessor = new TGAudioBufferProcessor(synthModel);
				ByteArrayOutputStream audioBuffer = new ByteArrayOutputStream();
				TGSynthSequencer sequence = new TGSynthSequencer(synthModel, midiSequenceHandler.getEvents());

				this.loadSynthPrograms(synthModel, tgSong);

				sequence.start();
				long duration = 0;
				while(!sequence.isEnded()) {
					sequence.dispatchEvents();

					audioProcessor.process();
					audioBuffer.write(audioProcessor.getBuffer().getBuffer(), 0, audioProcessor.getBuffer().getLength());
					duration += audioProcessor.getBuffer().getLength();
					sequence.forward();
				}
				duration = duration / TGAudioBuffer.CHANNELS;
				byte[] audioData = this.normalize(audioBuffer.toByteArray());
				ByteArrayInputStream byteBuffer = new ByteArrayInputStream(audioData);
				AudioInputStream sourceStream = new AudioInputStream(byteBuffer, TGAudioLine.AUDIO_FORMAT, duration);
				AudioInputStream targetStream = AudioSystem.getAudioInputStream(settings.getFormat(), sourceStream);
				AudioSystem.write(targetStream, settings.getType(), out);
			}
		} catch(Throwable throwable) {
			throw new TGFileFormatException(throwable);
		}
	}

	/**
	 * Normalizes 16-bit signed little-endian PCM data so that its peak sample
	 * reaches {@link #NORMALIZE_LEVEL} of the full scale. This makes the
	 * exported audio file noticeably louder without clipping.
	 */
	private byte[] normalize(byte[] data) {
		int sampleCount = data.length / 2;
		if( sampleCount == 0 ) {
			return data;
		}

		int peak = 0;
		for(int i = 0; i < sampleCount; i ++) {
			int sample = (short) (((data[(i * 2) + 1] & 0xFF) << 8) | (data[i * 2] & 0xFF));
			int abs = Math.abs(sample);
			if( abs > peak ) {
				peak = abs;
			}
		}

		if( peak == 0 ) {
			return data;
		}

		double gain = (NORMALIZE_LEVEL * Short.MAX_VALUE) / peak;
		for(int i = 0; i < sampleCount; i ++) {
			int sample = (short) (((data[(i * 2) + 1] & 0xFF) << 8) | (data[i * 2] & 0xFF));
			int scaled = (int) Math.round(sample * gain);
			if( scaled > Short.MAX_VALUE ) {
				scaled = Short.MAX_VALUE;
			} else if( scaled < Short.MIN_VALUE ) {
				scaled = Short.MIN_VALUE;
			}
			data[i * 2] = (byte) (scaled & 0xFF);
			data[(i * 2) + 1] = (byte) ((scaled >> 8) & 0xFF);
		}
		return data;
	}

	private void loadSynthPrograms(TGSynthModel tgSynthModel, TGSong tgSong) throws MidiPlayerException {
		TGSynthSettings tgSynthSettings = new TGSynthSettings(this.context);
		tgSynthSettings.loadPrograms(tgSynthModel);

		// Add channels
		Iterator<TGChannel> tgChannels = tgSong.getChannels();
		while( tgChannels.hasNext() ){
			TGChannel tgChannel = tgChannels.next();
			if( tgChannel.getChannelId() > 0 ) {
				MidiChannel midiChannel = tgSynthModel.openChannel(tgChannel.getChannelId());
				// send parameters
				midiChannel.sendParameter(MidiParameters.SENDING_PARAMS, Boolean.TRUE.toString());

				Iterator<TGChannelParameter> parameters = tgChannel.getParameters();
				while( parameters.hasNext() ){
					TGChannelParameter parameter = parameters.next();
					midiChannel.sendParameter(parameter.getKey(), parameter.getValue());
				}

				midiChannel.sendParameter(MidiParameters.SENDING_PARAMS, Boolean.FALSE.toString());
			}
		}
	}
}
