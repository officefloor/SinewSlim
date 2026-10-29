package net.officefloor.sinewslim.core;

import net.officefloor.sinewslim.core.Assessment.Obstacle;
import net.officefloor.sinewslim.core.Assessment.Status;

/**
 * Reads a method under the default search and the variants beside it, and gives its status
 * (DESIGN.md, The result).
 *
 * @param params                  the limits and weights
 * @param records                 when nothing is feasible with one return, try records
 * @param structural              also solve with whole if, loop, try and switch statements only
 * @param preferStructuralWithin  show the structural proposal when it costs at most this much
 *                                more than the best; -1 never to prefer it
 */
public record Assessor(Params params, boolean records, boolean structural, int preferStructuralWithin) {

	/** SinewSlim's defaults (DESIGN.md, Configuration). */
	public static final Assessor DEFAULTS = new Assessor(Params.DEFAULTS, true, true, 1);

	public Assessment assess(MethodFacts facts) {
		SlimResult best = Slimmer.slim(facts, params);
		if (!best.applicable()) {
			return new Assessment(Status.UNDER_CAP, null, best, best, null);
		}
		SlimResult struct = structural && !params.structuralOnly() ? Slimmer.slim(facts, params.structural()) : null;
		if (best.feasible()) {
			SlimResult proposal = best;
			if (struct != null && struct.feasible() && preferStructuralWithin >= 0
					&& struct.cost() <= best.cost() + preferStructuralWithin) {
				proposal = struct;
			}
			Status status = locked(proposal) ? Status.SLIMMABLE_BUT_LOCKED : Status.SLIMMABLE;
			return new Assessment(status, null, proposal, best, struct);
		}
		Obstacle obstacle = obstacle(facts);
		if (records && (obstacle == Obstacle.OUTPUTS) && params.maxOutputs() >= 0) {
			SlimResult rec = Slimmer.slim(facts, params.records());
			return new Assessment(Status.SLIMMABLE_WITH_RECORD, obstacle, rec, best, struct);
		}
		return new Assessment(Status.NOT_SLIMMABLE, obstacle, best, best, struct);
	}

	/** Every piece and the remainder bound to the class: splitting moves nothing out of it. */
	private static boolean locked(SlimResult r) {
		return r.pieces() > 0 && r.remainderLocked() && r.count(Binding.LOCKED) == r.pieces();
	}

	/** Which limit, lifted, makes a segmentation feasible. */
	private Obstacle obstacle(MethodFacts facts) {
		boolean outputsLimited = params.maxOutputs() >= 0, inputsLimited = params.maxInputs() >= 0;
		if (outputsLimited && Slimmer.slim(facts, params.records()).feasible()) {
			return Obstacle.OUTPUTS;
		}
		if (inputsLimited && Slimmer.slim(facts, params.unlimitedInputs()).feasible()) {
			return Obstacle.INPUTS;
		}
		if (outputsLimited && inputsLimited
				&& Slimmer.slim(facts, params.records().toBuilder().maxInputs(-1).build()).feasible()) {
			return Obstacle.INPUTS_AND_OUTPUTS;
		}
		return Obstacle.STRUCTURE;
	}
}
