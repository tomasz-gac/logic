package com.tgac.logic.fd;
import com.tgac.functional.recursion.MRecur;
import com.tgac.logic.LVar;
import com.tgac.logic.Unifiable;
import com.tgac.logic.cKanren.CKanren;
import com.tgac.logic.cKanren.Constraint;
import com.tgac.logic.cKanren.PackageOp;
import com.tgac.logic.cKanren.parameters.ProcessPrefix;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;

import static com.tgac.functional.recursion.MRecur.mdone;
public class ProcessPrefixFd implements ProcessPrefix {
	@Override
	public MRecur<PackageOp> process(HashMap<LVar<?>, Unifiable<?>> prefix, List<Constraint> constraints) {
		if (prefix.isEmpty()) {
			return mdone(PackageOp.identity());
		}
		LVar<?> x = prefix.head()._1;
		Unifiable<?> v = prefix.head()._2;

		MRecur<PackageOp> validator = constraintValidator(prefix, constraints, x);

		return MRecur.mdone(s ->
				s.getDomain(x)
						.map(dom -> applyDomainAndValidate(dom.processDom(v), validator)
								.flatMap(op -> op.apply(s)))
						.getOrElse(MRecur::none)
						.orElse(() -> validator.flatMap(op -> op.apply(s))));
	}

	private static MRecur<PackageOp> applyDomainAndValidate(PackageOp dom, MRecur<PackageOp> validator) {
		return validator
				.map(validatorOp ->
						PackageOp.of(s1 ->
								dom.apply(s1).flatMap(validatorOp)));
	}

	private MRecur<PackageOp> constraintValidator(HashMap<LVar<?>, Unifiable<?>> prefix, List<Constraint> constraints, LVar<?> x) {
		return MRecur.ofRecur(CKanren.runConstraints(x, constraints))
				.flatMap(rc -> process(prefix.tail(), constraints)
						.map(rc::compose));
	}
}
