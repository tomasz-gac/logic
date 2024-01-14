package com.tgac.logic.ckanren;

import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Stored;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.Array;
import io.vavr.control.Option;
import lombok.RequiredArgsConstructor;
import lombok.Value;

import static com.tgac.logic.ckanren.StoreSupport.isAssociated;
import static com.tgac.logic.ckanren.StoreSupport.withConstraint;

@Value
@RequiredArgsConstructor(staticName = "of")
public class Constraint implements Stored, PackageAccessor {
	PackageAccessor constraintOp;
	Array<Unifiable<?>> args;

	@Override
	public Option<Package> apply(Package p) {
		return constraintOp.apply(p);
	}

	public Package addTo(Package p) {
		boolean atLeaseOneIsBound = args.toJavaStream()
				.anyMatch(c -> !isAssociated(p, c));

		return atLeaseOneIsBound ? withConstraint(p, this) : p;
	}
}