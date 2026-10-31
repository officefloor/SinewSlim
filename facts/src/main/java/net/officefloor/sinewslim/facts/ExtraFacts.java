package net.officefloor.sinewslim.facts;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The facts consumers add about a method beside the shared schema, each under its own
 * {@link FactKey}. They never change {@link MethodFacts}, so what one consumer reads cannot move
 * another's results; SinewSlim's search reads none of them.
 */
public final class ExtraFacts {

	/** No extra facts: what a parse without extractors gives. */
	public static final ExtraFacts NONE = new ExtraFacts(Map.of());

	private final Map<FactKey<?>, Object> facts;

	private ExtraFacts(Map<FactKey<?>, Object> facts) {
		this.facts = facts;
	}

	/** The fact under the key, if it was extracted. */
	public <T> Optional<T> get(FactKey<T> key) {
		@SuppressWarnings("unchecked")
		T value = (T) facts.get(key);
		return Optional.ofNullable(value);
	}

	public boolean has(FactKey<?> key) {
		return facts.containsKey(key);
	}

	public boolean isEmpty() {
		return facts.isEmpty();
	}

	@Override
	public String toString() {
		return facts.toString();
	}

	public static Builder builder() {
		return new Builder();
	}

	/** Gathers the facts of one method; a key given twice keeps the last value. */
	public static final class Builder {

		private final Map<FactKey<?>, Object> facts = new IdentityHashMap<>();

		private Builder() {
		}

		/** Adds the fact; a null value adds nothing (the extractor found nothing to say). */
		public <T> Builder put(FactKey<T> key, T value) {
			if (value != null) {
				facts.put(key, value);
			}
			return this;
		}

		public ExtraFacts build() {
			return facts.isEmpty() ? NONE : new ExtraFacts(Collections.unmodifiableMap(new IdentityHashMap<>(facts)));
		}
	}
}
