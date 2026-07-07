package com.tgac.logic.ckanren;

// ABOUTME: The explicit propagation worklist — pending Bind/Wake items plus the run
// ABOUTME: lane, riding the package during a drain. Its presence marks "drain in flight".

import com.tgac.logic.goals.Goal;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Store;
import com.tgac.logic.unification.Stored;
import com.tgac.logic.unification.Term;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;

/**
 * What the recursion used to keep as suspended frames, as a list
 * (docs/design/capability-constraint-api.md, Step 2.5). Two item kinds — inferred
 * bindings and watcher wake-ups — drain FIFO, one per deferred step; collected run
 * goals splice only after the items are exhausted and the agenda is removed. A
 * plain, inert store: constraint processing never sees it.
 */
final class Agenda implements Store {

	abstract static class Item {
		private Item() {
		}
	}

	/** Inferred bindings — a prefix, revalidated against the live package at pop. */
	static final class Bind extends Item {
		final com.tgac.logic.unification.Prefix prefix;

		Bind(com.tgac.logic.unification.Prefix prefix) {
			this.prefix = prefix;
		}

		@Override
		public String toString() {
			return prefix.toString();
		}
	}

	/** Wake the propagators watching a changed term. */
	static final class Wake extends Item {
		final Term<?> changed;

		Wake(Term<?> changed) {
			this.changed = changed;
		}

		@Override
		public String toString() {
			return "wake(" + changed + ")";
		}
	}

	private final List<Item> items;
	private final List<Goal> runs;

	private Agenda(List<Item> items, List<Goal> runs) {
		this.items = items;
		this.runs = runs;
	}

	static Agenda seeded(Item first) {
		return new Agenda(List.of(first), List.empty());
	}

	Agenda append(Item item) {
		return new Agenda(items.append(item), runs);
	}

	Agenda appendRun(Goal goal) {
		return new Agenda(items, runs.append(goal));
	}

	boolean itemsExhausted() {
		return items.isEmpty();
	}

	Tuple2<Item, Agenda> pop() {
		return Tuple.of(items.head(), new Agenda(items.tail(), runs));
	}

	List<Goal> runs() {
		return runs;
	}

	@Override
	public Store remove(Stored c) {
		return this;
	}

	@Override
	public Store prepend(Stored c) {
		return this;
	}

	@Override
	public boolean contains(Stored c) {
		return false;
	}

	@Override
	public String toString() {
		return "agenda" + items + (runs.isEmpty() ? "" : " runs" + runs);
	}
}
