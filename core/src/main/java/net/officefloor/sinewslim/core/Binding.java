package net.officefloor.sinewslim.core;

/** What a piece of a method depends on in the object it lives in ({@code SPLIT.md}, Binding to the class). */
public enum Binding {

	/** No field referenced, no call to the class's own methods: a stateless utility, movable anywhere. */
	FREE,

	/** Reads fields, writes none, calls none of the class's own methods: static if the fields are passed in. */
	BOUND,

	/** Writes a field, or calls one of the class's own methods: an instance method of this class. */
	LOCKED
}
