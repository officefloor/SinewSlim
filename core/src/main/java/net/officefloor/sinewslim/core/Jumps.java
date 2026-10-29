package net.officefloor.sinewslim.core;

import java.util.List;

/**
 * The jump rule: a run can be moved when no {@code break}, {@code continue} or {@code yield}
 * leaves it, and no {@code return} does except one that ends it, when trailing returns are
 * allowed. A block directly under an expression or a declaration is a lambda's body, and
 * nothing in it leaves the run.
 */
final class Jumps {

	private Jumps() {
	}

	private static final class Acc {
		int returns;
		boolean leaves;
	}

	static boolean ok(List<Stmt> run, Params p) {
		Acc acc = new Acc();
		for (Stmt node : run) {
			walk(node, false, false, false, acc);
		}
		if (acc.leaves) {
			return false;
		}
		if (acc.returns == 0) {
			return true;
		}
		return p.trailingReturn() && acc.returns == 1 && run.get(run.size() - 1).kind() == StmtKind.RETURN;
	}

	private static void walk(Stmt node, boolean inLoop, boolean inSwitch, boolean inLambda, Acc acc) {
		if (inLambda) {
			return;
		}
		StmtKind k = node.kind();
		if (k == StmtKind.RETURN) {
			acc.returns++;
		} else if (k == StmtKind.BREAK && !(inLoop || inSwitch)) {
			acc.leaves = true;
		} else if (k == StmtKind.CONTINUE && !inLoop) {
			acc.leaves = true;
		} else if (k == StmtKind.YIELD && !inSwitch) {
			acc.leaves = true;
		}
		boolean loop = inLoop || k == StmtKind.LOOP;
		boolean sw = inSwitch || k == StmtKind.SWITCH;
		for (Stmt c : node.kids()) {
			walk(c, loop, sw, k == StmtKind.OTHER && c.kind() == StmtKind.BLOCK, acc);
		}
	}
}
