package com.tgac.logic.ckanren;

import static com.tgac.logic.ckanren.StoreSupport.isAssociated;
import static com.tgac.logic.ckanren.StoreSupport.withConstraint;

import com.tgac.functional.category.Nothing;
import com.tgac.functional.monad.Cont;
import com.tgac.logic.goals.Goal;
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
public class Constraint implements Stored, Goal {
	Goal constraintOp;
	Class<? extends Store> storeClass;
	List<? extends Unifiable<?>> args;

	@Override
	public Cont<Package, Nothing> apply(Package p) {
		return constraintOp.apply(p);
	}

	public Package addTo(Package p) {
		boolean atLeaseOneIsBound = args.stream()
				.anyMatch(c -> !isAssociated(p, c));

		return atLeaseOneIsBound ? withConstraint(p, this) : p;
	}
}