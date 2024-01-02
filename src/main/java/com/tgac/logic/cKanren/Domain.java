package com.tgac.logic.cKanren;
import com.tgac.logic.Unifiable;

import java.util.stream.Stream;
public interface Domain {
	PackageOp processDom(Unifiable<?> x);

	Stream<Object> stream();

	boolean isEmpty();

}
