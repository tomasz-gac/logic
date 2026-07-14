package com.tgac.logic.goals;

public interface Store extends Packaged {
	Store remove(Stored c);

	Store prepend(Stored c);

	boolean contains(Stored c);
}
