package com.tgac.logic.ckanren;

import static com.tgac.logic.ckanren.StoreSupport.isAssociated;
import static com.tgac.logic.ckanren.StoreSupport.withConstraint;

import com.tgac.logic.unification.Package;
import com.tgac.logic.unification.Store;
import com.tgac.logic.unification.Stored;
import com.tgac.logic.unification.Unifiable;
import io.vavr.control.Option;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor(staticName = "of")
public class Constraint implements Stored, PackageAccessor {
	PackageAccessor constraintOp;
	Class<? extends Store> storeClass;
	List<? extends Unifiable<?>> args;

	@Override
	public Option<Package> apply(Package p) {
		return constraintOp.apply(p);
	}

	public Package addTo(Package p) {
		boolean atLeaseOneIsBound = args.stream()
				.anyMatch(c -> !isAssociated(p, c));

		return atLeaseOneIsBound ? withConstraint(p, this) : p;
	}
}