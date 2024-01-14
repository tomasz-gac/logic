package com.tgac.logic.ckanren;

import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Stored;
import com.tgac.logic.unification.Unifiable;
import io.vavr.Predicates;
import io.vavr.collection.Array;
import io.vavr.control.Option;
import lombok.RequiredArgsConstructor;
import lombok.Value;

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
				.anyMatch(Predicates.not(p::isAssociated));

		return atLeaseOneIsBound ? p.withConstraint(this) : p;
	}
}