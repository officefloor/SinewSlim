package net.officefloor.sinewslim.cli.report;

import java.util.ArrayList;
import java.util.List;

import net.officefloor.sinewslim.core.Binding;
import net.officefloor.sinewslim.core.Params;
import net.officefloor.sinewslim.core.Seam;
import net.officefloor.sinewslim.core.SlimResult;

/** The words every format uses for a method and its pieces, so they all say the same thing. */
public final class Describe {

	private Describe() {
	}

	/** One piece of a proposal, ready to show. */
	public record Piece(int lo, int hi, int lines, int saves, List<String> inputs, List<String> passed,
			List<String> outputs, List<String> moved, String binding) {
	}

	public static List<Piece> pieces(SlimResult r, boolean parameteriseFieldReads) {
		List<Piece> out = new ArrayList<>();
		for (int i = 0; i < r.cuts().size(); i++) {
			Seam s = r.cuts().get(i);
			int lines = r.pieceLines().get(i);
			List<String> computed = new ArrayList<>(s.inputs());
			computed.removeAll(s.passed());
			out.add(new Piece(s.lo(), s.hi(), lines, lines - 1, computed, s.passed(), s.outputs(), s.moved(),
					binding(s, r.params(), parameteriseFieldReads)));
		}
		return out;
	}

	/** Where the piece can live: {@code static}, {@code static if passed: cfg}, or {@code instance: writes cache}. */
	public static String binding(Seam s, Params p, boolean parameteriseFieldReads) {
		Binding b = s.binding(p);
		if (b == Binding.FREE) {
			return "static";
		}
		if (b == Binding.BOUND) {
			boolean fits = p.maxInputs() < 0 || s.inputs().size() + s.fieldsRead().size() <= p.maxInputs();
			return parameteriseFieldReads && fits ? "static if passed: " + String.join(", ", s.fieldsRead())
					: "instance: reads " + String.join(", ", s.fieldsRead());
		}
		List<String> why = new ArrayList<>();
		if (!s.fieldsWritten().isEmpty()) {
			why.add("writes " + String.join(", ", s.fieldsWritten()));
		}
		if (s.ownCalls() > 0) {
			why.add("calls " + s.ownCalls() + " own method" + (s.ownCalls() == 1 ? "" : "s"));
		}
		if (why.isEmpty()) {
			why.add("reads " + String.join(", ", s.fieldsRead()));
		}
		return "instance: " + String.join("; ", why);
	}

	/** What crosses out: {@code -}, the one value, or a record of several. */
	public static String outputs(List<String> outputs) {
		if (outputs.isEmpty()) {
			return "-";
		}
		return outputs.size() == 1 ? outputs.get(0) : "record(" + String.join(", ", outputs) + ")";
	}

	public static String list(List<String> xs) {
		return xs.isEmpty() ? "-" : String.join(", ", xs);
	}

	/** The one-line verdict for a method over the cap. */
	public static String verdict(MethodReport m) {
		return m.length() + " lines; " + assessment(m);
	}

	/** The verdict without the length, for where the length is already said (the review's movement). */
	public static String assessment(MethodReport m) {
		SlimResult r = m.proposal();
		String pieces = r.pieces() + " piece" + (r.pieces() == 1 ? "" : "s");
		return switch (m.status()) {
		case UNDER_CAP -> m.headroom() + (m.headroom() == 1 ? " line" : " lines") + " from the cap";
		case SLIMMABLE -> "slimmable at cost " + r.width() + " (" + pieces + ", remainder " + r.remainder() + " lines)";
		case SLIMMABLE_WITH_RECORD -> "slimmable only by returning a record (cost " + r.width() + ", " + pieces + "): "
				+ m.assessment().reason();
		case SLIMMABLE_BUT_LOCKED -> "slimmable at cost " + r.width() + " (" + pieces
				+ "), but every piece stays bound to the class: the class, not the method, is the problem";
		case NOT_SLIMMABLE -> "cannot be slimmed: " + m.assessment().reason() + ". Investigate for redesign";
		};
	}

	/** A proposal as plain lines, one per piece and one for the remainder, as the terminal shows it. */
	public static List<String> proposalLines(MethodReport m, boolean parameteriseFieldReads) {
		List<String> out = new ArrayList<>();
		for (Piece p : pieces(m.proposal(), parameteriseFieldReads)) {
			out.add(String.format("lines %d-%d  %3d lines  in: %s%s  out: %s%s  %s  saves %d", p.lo(), p.hi(), p.lines(),
					list(p.inputs()), p.passed().isEmpty() ? "" : "  passed: " + list(p.passed()), outputs(p.outputs()),
					p.moved().isEmpty() ? "" : "  declares: " + list(p.moved()), p.binding(), p.saves()));
		}
		out.add("remainder " + remainder(m));
		return out;
	}

	/** What the remainder of a proposal is, against what the method was. */
	public static String remainder(MethodReport m) {
		SlimResult r = m.proposal();
		return r.remainder() + " lines (was " + m.length() + ")" + (r.remainderLocked() ? ", bound to the class" : "");
	}

	public static String percent(double share) {
		return Math.round(share * 100) + "%";
	}
}
