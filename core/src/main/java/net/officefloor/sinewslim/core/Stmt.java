package net.officefloor.sinewslim.core;

import java.util.List;

/**
 * A node of a method's statement tree: a sequence (a block, a switch entry, a lambda's block
 * body), whose children are statements in order and any contiguous run of which Extract Method
 * can move; or a statement, whose children are the sequences and statements directly inside it
 * (an if's arms, a loop's body, a try's block and catch bodies).
 *
 * @param start the first line
 * @param end   the last line
 * @param seq   whether this is a sequence
 * @param kind  what the node is
 * @param kids  the children, in order
 */
public record Stmt(int start, int end, boolean seq, StmtKind kind, List<Stmt> kids) {

	public Stmt {
		kids = List.copyOf(kids);
	}
}
