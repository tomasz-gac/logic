package com.tgac.logic.fd.parameters;
import com.tgac.functional.recursion.MRecur;
import com.tgac.logic.LVar;
import com.tgac.logic.Unifiable;
import com.tgac.logic.cKanren.CKanren;
import com.tgac.logic.cKanren.Constraint;
import com.tgac.logic.cKanren.PackageAccessor;
import com.tgac.logic.cKanren.parameters.ProcessPrefix;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;

import static com.tgac.functional.recursion.MRecur.mdone;
public class ProcessPrefixFd implements ProcessPrefix {
	@Override
	public MRecur<PackageAccessor> processPrefix(HashMap<LVar<?>, Unifiable<?>> prefix, List<Constraint> constraints) {
		if (prefix.isEmpty()) {
			return mdone(PackageAccessor.identity());
		}
		LVar<?> x = prefix.head()._1;
		Unifiable<?> v = prefix.head()._2;

		MRecur<PackageAccessor> validator = constraintValidator(prefix, constraints, x);

		return MRecur.mdone(s ->
				s.getDomain(x)
						.map(dom -> processDomAndValidate(dom.processDom(v), validator)
								.flatMap(op -> op.apply(s)))
						.getOrElse(MRecur::none)
						.orElse(() -> validator.flatMap(op -> op.apply(s))));
	}

	private static MRecur<PackageAccessor> processDomAndValidate(
			PackageAccessor processDom,
			MRecur<PackageAccessor> validator) {
		return validator
				.map(validatorOp ->
						PackageAccessor.of(s1 ->
								processDom.apply(s1).flatMap(validatorOp)));
	}

	private MRecur<PackageAccessor> constraintValidator(
			HashMap<LVar<?>, Unifiable<?>> prefix,
			List<Constraint> constraints,
			LVar<?> x) {
		return MRecur.ofRecur(CKanren.runConstraints(x, constraints))
				.flatMap(rc -> processPrefix(prefix.tail(), constraints)
						.map(rc::compose));
	}
}
