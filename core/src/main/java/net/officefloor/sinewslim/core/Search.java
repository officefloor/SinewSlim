package net.officefloor.sinewslim.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.officefloor.sinewslim.facts.Jumps;
import net.officefloor.sinewslim.facts.MethodIndex;
import net.officefloor.sinewslim.facts.Seam;
import net.officefloor.sinewslim.facts.Stmt;

/**
 * The bottom-up dynamic program over the statement tree ({@code split.py}, the search). For each
 * sequence, walk its statements in order; at each, either keep it in the remainder (as it is, or
 * reduced by extractions inside it) or close an extracted run ending there. The iteration orders
 * are {@code split.py}'s, so ties fall the same way.
 */
final class Search {

	/**
	 * One way of handling a stretch of statements: the lines left in place (each extracted piece
	 * replaced by one call line), the worst seam, the total cost, the pieces and the cuts.
	 */
	record Option(int rem, int width, int cost, int pieces, List<Seam> cuts) {

		static Option of(int rem) {
			return new Option(rem, 0, 0, 0, List.of());
		}

		/** The objective: the worst seam, then the fewest pieces, then the total cost. */
		boolean better(Option o) {
			if (width != o.width) {
				return width < o.width;
			}
			if (pieces != o.pieces) {
				return pieces < o.pieces;
			}
			return cost < o.cost;
		}

		Option shift(int by) {
			return new Option(rem + by, width, cost, pieces, cuts);
		}
	}

	static final Comparator<Option> PARETO_ORDER = Comparator.comparingInt(Option::rem).thenComparingInt(Option::width)
			.thenComparingInt(Option::pieces).thenComparingInt(Option::cost);

	private final MethodIndex m;
	private final Params p;
	private final Set<Run> excluded;

	Search(MethodIndex m, Params p, Set<Run> excluded) {
		this.m = m;
		this.p = p;
		this.excluded = excluded;
	}

	static List<Seam> concat(List<Seam> a, List<Seam> b) {
		if (b.isEmpty()) {
			return a;
		}
		if (a.isEmpty()) {
			return b;
		}
		List<Seam> out = new ArrayList<>(a.size() + b.size());
		out.addAll(a);
		out.addAll(b);
		return out;
	}

	static List<Seam> append(List<Seam> a, Seam s) {
		List<Seam> out = new ArrayList<>(a.size() + 1);
		out.addAll(a);
		out.add(s);
		return out;
	}

	static Option combine(Option a, Option b) {
		return new Option(a.rem + b.rem, Math.max(a.width, b.width), a.cost + b.cost, a.pieces + b.pieces,
				concat(a.cuts, b.cuts));
	}

	/** Every option no other beats on all of lines left, worst seam, pieces and cost. */
	static List<Option> pareto(List<Option> options) {
		List<Option> opts = new ArrayList<>(options);
		opts.sort(PARETO_ORDER); // stable, as Python's sorted
		List<Option> keep = new ArrayList<>();
		for (Option o : opts) {
			boolean dominated = false;
			for (Option k : keep) {
				if (k.rem <= o.rem && k.width <= o.width && k.pieces <= o.pieces && k.cost <= o.cost) {
					dominated = true;
					break;
				}
			}
			if (!dominated) {
				keep.add(o);
			}
		}
		return keep;
	}

	static void push(Map<Integer, Option> states, Option o) {
		Option cur = states.get(o.rem);
		if (cur == null || o.better(cur)) {
			states.put(o.rem, o); // an existing key keeps its place, as a Python dict's does
		}
	}

	/**
	 * The lines an extracted piece leaves behind: its call, and with a record, the unpacking of
	 * each value when that is counted.
	 */
	int left(Seam s) {
		int k = s.outputs().size();
		return 1 + (p.countUnpacking() && k > 1 ? k : 0);
	}

	/** The lines a node's own search accounts for: a sequence's statements, a statement's span. */
	int covered(Stmt node) {
		if (node.seq()) {
			int sum = 0;
			for (Stmt k : node.kids()) {
				sum += m.lines(k.start(), k.end());
			}
			return sum;
		}
		return m.lines(node.start(), node.end());
	}

	/** The options for one statement: as it is, or reduced by extractions inside it. */
	List<Option> solveStmt(Stmt node) {
		int total = m.lines(node.start(), node.end());
		List<Option> opts = List.of(Option.of(total));
		for (Stmt c : node.kids()) {
			List<Option> f = c.seq() ? solveSeq(c) : solveStmt(c);
			// a child's saving is read against what its own search covered: its statements' lines
			// for a sequence (its braces stay), its whole span for a statement
			int base = covered(c);
			List<Option> combined = new ArrayList<>(opts.size() * f.size());
			for (Option o : opts) {
				for (Option d : f) {
					combined.add(combine(o, d.shift(-base)));
				}
			}
			opts = pareto(combined);
		}
		return opts;
	}

	/** The Pareto front of ways to handle a sequence: lines left, worst seam, cost, pieces. */
	List<Option> solveSeq(Stmt node) {
		List<Stmt> kids = node.kids();
		int n = kids.size();
		List<List<Option>> opts = new ArrayList<>(n);
		for (Stmt k : kids) {
			opts.add(solveStmt(k));
		}
		List<Map<Integer, Option>> at = new ArrayList<>(n + 1);
		for (int i = 0; i <= n; i++) {
			at.add(new LinkedHashMap<>());
		}
		at.get(0).put(0, Option.of(0));
		for (int i = 0; i < n; i++) {
			Map<Integer, Option> next = at.get(i + 1);
			for (Option st : at.get(i).values()) {
				for (Option o : opts.get(i)) {
					push(next, combine(st, o));
				}
			}
			Stmt kid = kids.get(i);
			if (!p.structuralOnly() || kid.kind().structural()) {
				// a single statement, as it is or reduced from inside, then extracted whole
				if (p.ignoreJumps() || Jumps.ok(List.of(kid), p.trailingReturn())) {
					Seam sm = m.seam(kid.start(), kid.end());
					if (p.feasible(sm) && !excluded.contains(Run.of(sm))) {
						int w = p.width(sm), c = p.cost(sm);
						for (Option o : opts.get(i)) {
							if (p.minPiece() <= o.rem && o.rem <= p.target() && o.rem - 1 >= p.minSaving()) {
								for (Option st : List.copyOf(at.get(i).values())) {
									push(next, new Option(st.rem + left(sm), Math.max(Math.max(st.width, o.width), w),
											st.cost + o.cost + c, st.pieces + o.pieces + 1,
											append(concat(st.cuts, o.cuts), sm)));
								}
							}
						}
					}
				}
			}
			if (p.structuralOnly()) {
				continue;
			}
			// a run of several statements, as they are
			for (int j = i - 1; j >= 0; j--) {
				int lines = m.lines(kids.get(j).start(), kid.end());
				if (lines > p.target()) {
					break;
				}
				if (lines < p.minPiece() || lines - 1 < p.minSaving()) {
					continue;
				}
				if (!p.ignoreJumps() && !Jumps.ok(kids.subList(j, i + 1), p.trailingReturn())) {
					continue;
				}
				Seam sm = m.seam(kids.get(j).start(), kid.end());
				if (!p.feasible(sm) || excluded.contains(Run.of(sm))) {
					continue;
				}
				int w = p.width(sm), c = p.cost(sm);
				for (Option st : at.get(j).values()) {
					push(next, new Option(st.rem + left(sm), Math.max(st.width, w), st.cost + c, st.pieces + 1,
							append(st.cuts, sm)));
				}
			}
		}
		return pareto(new ArrayList<>(at.get(n).values()));
	}
}
