package com.tgac.logic.unification;
public interface Store {
	Store remove(Stored c);

	Store prepend(Stored c);

	boolean contains(Stored c);
}
