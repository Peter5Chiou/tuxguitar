package app.tuxguitar.app.view.component.tab;

import app.tuxguitar.app.system.config.TGConfigKeys;
import app.tuxguitar.app.system.config.TGConfigManager;
import app.tuxguitar.app.system.icons.TGSkinManager;
import app.tuxguitar.app.system.properties.TGPropertiesUIUtil;
import app.tuxguitar.graphics.control.TGLayoutStyles;
import app.tuxguitar.util.properties.TGProperties;
import app.tuxguitar.ui.resource.UIColorModel;

public class TablatureStyles extends TGLayoutStyles {

	public TablatureStyles(TGConfigManager config) {
		TGProperties skinProperties = TGSkinManager.getInstance(config.getContext()).getCurrentSkinProperties();
		this.setBufferEnabled(true);
		this.setStringSpacing(config.getIntegerValue(TGConfigKeys.STYLE_STRING_SPACING));
		this.setScoreLineSpacing(config.getIntegerValue(TGConfigKeys.STYLE_SCORE_LINE_SPACING));
		this.setFirstMeasureSpacing(config.getIntegerValue(TGConfigKeys.STYLE_FIRST_MEASURE_SPACING));
		this.setMinBufferSeparator(config.getIntegerValue(TGConfigKeys.STYLE_MIN_BUFFER_SEPARATOR));
		this.setMinTopSpacing(config.getIntegerValue(TGConfigKeys.STYLE_MIN_TOP_SPACING));
		this.setMinScoreTabSpacing(config.getIntegerValue(TGConfigKeys.STYLE_MIN_SCORE_TAB_SPACING));
		this.setFirstTrackSpacing(config.getIntegerValue(TGConfigKeys.STYLE_FIRST_TRACK_SPACING));
		this.setTrackSpacing(config.getIntegerValue(TGConfigKeys.STYLE_TRACK_SPACING));
		this.setFirstNoteSpacing(config.getIntegerValue(TGConfigKeys.STYLE_FIRST_NOTE_SPACING));
		this.setMeasureLeftSpacing(config.getIntegerValue(TGConfigKeys.STYLE_MEASURE_LEFT_SPACING));
		this.setMeasureRightSpacing(config.getIntegerValue(TGConfigKeys.STYLE_MEASURE_RIGHT_SPACING));
		this.setClefSpacing(config.getIntegerValue(TGConfigKeys.STYLE_CLEF_SPACING));
		this.setKeySignatureSpacing(config.getIntegerValue(TGConfigKeys.STYLE_KEY_SIGNATURE_SPACING));
		this.setTimeSignatureSpacing(config.getIntegerValue(TGConfigKeys.STYLE_TIME_SIGNATURE_SPACING));
		this.setChordFretIndexSpacing(config.getIntegerValue(TGConfigKeys.STYLE_CHORD_FRET_INDEX_SPACING));
		this.setChordStringSpacing(config.getIntegerValue(TGConfigKeys.STYLE_CHORD_STRING_SPACING));
		this.setChordFretSpacing(config.getIntegerValue(TGConfigKeys.STYLE_CHORD_FRET_SPACING));
		this.setChordNoteSize(config.getIntegerValue(TGConfigKeys.STYLE_CHORD_NOTE_SIZE));
		this.setChordLineWidth(config.getIntegerValue(TGConfigKeys.STYLE_CHORD_LINE_WIDTH));
		this.setRepeatEndingSpacing(config.getIntegerValue(TGConfigKeys.STYLE_REPEAT_ENDING_SPACING));
		this.setTextSpacing(config.getIntegerValue(TGConfigKeys.STYLE_TEXT_SPACING));
		this.setMarkerSpacing(config.getIntegerValue(TGConfigKeys.STYLE_MARKER_SPACING));
		this.setLoopMarkerSpacing(config.getIntegerValue(TGConfigKeys.STYLE_LOOP_MARKER_SPACING));
		this.setDivisionTypeSpacing(config.getIntegerValue(TGConfigKeys.STYLE_DIVISION_TYPE_SPACING));
		this.setPickStrokeSpacing(config.getIntegerValue(TGConfigKeys.STYLE_PICK_STROKE_SPACING));
		this.setBendSpacing(config.getIntegerValue(TGConfigKeys.STYLE_BEND_SPACING));
		this.setEffectSpacing(config.getIntegerValue(TGConfigKeys.STYLE_EFFECT_SPACING));
		this.setLineWidths(config.getFloatArrayValue(TGConfigKeys.STYLE_LINE_WIDTHS));
		this.setDurationWidths(config.getFloatArrayValue(TGConfigKeys.STYLE_DURATION_WIDTHS));

		this.setDefaultFont(config.getFontModelConfigValue(TGConfigKeys.FONT_DEFAULT));
		this.setNoteFont(config.getFontModelConfigValue(TGConfigKeys.FONT_NOTE));
		this.setLyricFont(config.getFontModelConfigValue(TGConfigKeys.FONT_LYRIC));
		this.setTextFont(config.getFontModelConfigValue(TGConfigKeys.FONT_TEXT));
		this.setMarkerFont(config.getFontModelConfigValue(TGConfigKeys.FONT_MARKER));
		this.setGraceFont(config.getFontModelConfigValue(TGConfigKeys.FONT_GRACE));
		this.setChordFont(config.getFontModelConfigValue(TGConfigKeys.FONT_CHORD));
		this.setChordFretFont(config.getFontModelConfigValue(TGConfigKeys.FONT_CHORD_FRET));
		this.setForegroundColor(getColor(config, skinProperties, "color.foreground", TGConfigKeys.COLOR_FOREGROUND));
		this.setBackgroundColor(getColor(config, skinProperties, "color.background", TGConfigKeys.COLOR_BACKGROUND));
		this.setBackgroundColorPlaying(getColor(config, skinProperties, "color.background.playing", TGConfigKeys.COLOR_BACKGROUND_PLAYING));
		this.setLineColor(getColor(config, skinProperties, "color.line", TGConfigKeys.COLOR_LINE));
		this.setLineColorInvalid(getColor(config, skinProperties, "color.line.invalid", TGConfigKeys.COLOR_LINE_INVALID));
		this.setScoreNoteColor(getColor(config, skinProperties, "color.score.note", TGConfigKeys.COLOR_SCORE_NOTE));
		this.setTabNoteColor(getColor(config, skinProperties, "color.tab.note", TGConfigKeys.COLOR_TAB_NOTE));
		this.setPlayNoteColor(getColor(config, skinProperties, "color.play.note", TGConfigKeys.COLOR_PLAY_NOTE));
		this.setSelectionColor(getColor(config, skinProperties, "color.selection", TGConfigKeys.COLOR_SELECTION));
		this.setLoopSMarkerColor(getColor(config, skinProperties, "color.loop.s.marker", TGConfigKeys.COLOR_LOOP_S_MARKER));
		this.setLoopEMarkerColor(getColor(config, skinProperties, "color.loop.e.marker", TGConfigKeys.COLOR_LOOP_E_MARKER));
		this.setMeasureNumberColor(getColor(config, skinProperties, "color.measure.number", TGConfigKeys.COLOR_MEASURE_NUMBER));
	}

	private static UIColorModel getColor(TGConfigManager config, TGProperties skinProperties, String skinKey, String configKey) {
		return TGPropertiesUIUtil.getColorModelValue(config.getContext(), skinProperties, skinKey, config.getColorModelConfigValue(configKey));
	}
}
