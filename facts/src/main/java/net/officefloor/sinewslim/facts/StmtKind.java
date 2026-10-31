package net.officefloor.sinewslim.facts;

/**
 * What a node of the statement tree is. The order is the study's facts schema (schema 3), so a
 * kind's ordinal is its number there. The jumps say whether a run can be moved; {@link #IF} to
 * {@link #SWITCH} are the structural cues developers cut at.
 */
public enum StmtKind {

	/** An expression, a declaration, an assert: anything not named below. */
	OTHER, RETURN, BREAK, CONTINUE, THROW, YIELD, IF,
	/** A for, for-each, while or do loop. */
	LOOP, TRY, SWITCH, SYNCHRONIZED, LABELED, BLOCK,
	/** A switch entry: a sequence of statements. */
	ENTRY;

	private static final StmtKind[] VALUES = values();

	/** The kind with the schema's number. */
	public static StmtKind of(int number) {
		return VALUES[number];
	}

	/** Whether a run made of whole statements of this kind is where developers cut. */
	public boolean structural() {
		return this == IF || this == LOOP || this == TRY || this == SWITCH;
	}
}
