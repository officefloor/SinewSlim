package net.officefloor.sinewslim.core;

import java.util.List;

/**
 * What crosses the boundary of a run of statements if it is extracted.
 *
 * @param lo            the run's first line
 * @param hi            the run's last line
 * @param inputs        used inside and defined before: the new method's parameters
 * @param outputs       written inside and read after, or read earlier in an enclosing loop: its
 *                      return value
 * @param moved         declared before with no value or a literal one, used only inside: the
 *                      declaration moves into the new method
 * @param fieldsRead    fields read inside and not written
 * @param fieldsWritten fields written inside
 * @param ownCalls      calls inside to the class's own instance methods
 * @param passed        the method's own parameters handed on unchanged; in {@code inputs} too
 *                      unless they are free (the framework reading)
 */
public record Seam(int lo, int hi, List<String> inputs, List<String> outputs, List<String> moved,
		List<String> fieldsRead, List<String> fieldsWritten, int ownCalls, List<String> passed) {

	public Seam {
		inputs = List.copyOf(inputs);
		outputs = List.copyOf(outputs);
		moved = List.copyOf(moved);
		fieldsRead = List.copyOf(fieldsRead);
		fieldsWritten = List.copyOf(fieldsWritten);
		passed = List.copyOf(passed);
	}

	/** The parameters' share of the seam: one each, and the premium for each beyond the soft limit. */
	public int inputCost(Params p) {
		int k = inputs.size();
		return p.softInputs() < 0 ? k : k + p.inputPremium() * Math.max(0, k - p.softInputs());
	}

	/** The worst-seam measure: what crosses, the soft-limit premium included. */
	public int width(Params p) {
		return inputCost(p) + outputs.size();
	}

	public int outputCost(Params p) {
		int k = outputs.size();
		return k <= 1 ? p.outputWeight() * k : p.multiOutputWeight() * k;
	}

	public int cost(Params p) {
		return inputCost(p) + outputCost(p) + p.pieceCost();
	}

	public boolean feasible(Params p) {
		return (p.maxOutputs() < 0 || outputs.size() <= p.maxOutputs())
				&& (p.maxInputs() < 0 || inputs.size() <= p.maxInputs());
	}

	public Binding binding(Params p) {
		if (!fieldsWritten.isEmpty() || ownCalls > 0 || (p.lockOnAnyField() && !fieldsRead.isEmpty())) {
			return Binding.LOCKED;
		}
		return fieldsRead.isEmpty() ? Binding.FREE : Binding.BOUND;
	}
}
