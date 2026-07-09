package com.tgac.logic.goals;

public interface Store {
	Store remove(Stored c);

	Store prepend(Stored c);

	boolean contains(Stored c);
}
