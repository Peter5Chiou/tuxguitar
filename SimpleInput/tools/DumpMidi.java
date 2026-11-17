import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import java.io.File;

/**
 * 簡易 MIDI dump 工具：印出 PPQ、所有事件（tick / NoteOn / NoteOff / CC / Meta）。
 * 用法: java DumpMidi.java <file.mid>
 */
public class DumpMidi {

	public static void main(String[] args) throws Exception {
		if (args.length < 1) {
			System.err.println("用法: java DumpMidi.java <file.mid>");
			System.exit(1);
		}
		Sequence seq = MidiSystem.getSequence(new File(args[0]));
		System.out.println("divisionType=" + (seq.getDivisionType() == Sequence.PPQ ? "PPQ" : "SMPTE")
				+ " resolution=" + seq.getResolution()
				+ " tracks=" + seq.getTracks().length
				+ " lengthTicks=" + seq.getTickLength());
		for (int t = 0; t < seq.getTracks().length; t++) {
			Track tr = seq.getTracks()[t];
			System.out.println("--- Track " + t + " events=" + tr.size());
			for (int i = 0; i < tr.size(); i++) {
				MidiEvent e = tr.get(i);
				MidiMessage m = e.getMessage();
				String desc;
				if (m instanceof ShortMessage sm) {
					String kind = switch (sm.getCommand()) {
						case ShortMessage.NOTE_ON -> sm.getData2() == 0 ? "NoteOn(vel0)" : "NoteOn";
						case ShortMessage.NOTE_OFF -> "NoteOff";
						case ShortMessage.CONTROL_CHANGE -> "CC";
						case ShortMessage.PROGRAM_CHANGE -> "PC";
						case ShortMessage.PITCH_BEND -> "PitchBend";
						default -> "Cmd" + sm.getCommand();
					};
					desc = kind + " ch=" + sm.getChannel() + " d1=" + sm.getData1() + " d2=" + sm.getData2();
				} else if (m instanceof MetaMessage mm) {
					desc = "Meta type=0x" + Integer.toHexString(mm.getType()) + " len=" + mm.getData().length;
					if (mm.getType() == 0x51 && mm.getData().length == 3) {
						int mpqn = ((mm.getData()[0] & 0xFF) << 16) | ((mm.getData()[1] & 0xFF) << 8) | (mm.getData()[2] & 0xFF);
						desc += " tempo=" + (60_000_000 / mpqn) + "bpm";
					}
					if (mm.getType() == 0x58 && mm.getData().length >= 2) {
						desc += " timeSig=" + (mm.getData()[0] & 0xFF) + "/" + (1 << (mm.getData()[1] & 0xFF));
					}
					if (mm.getType() == 0x03 || mm.getType() == 0x06) {
						desc += " text=\"" + new String(mm.getData(), java.nio.charset.StandardCharsets.UTF_8) + "\"";
					}
				} else {
					desc = m.getClass().getSimpleName() + " len=" + m.getLength();
				}
				System.out.printf("%6d  %s%n", e.getTick(), desc);
			}
		}
	}
}
