package com.tgac.logic.finitedomain.parameters;
import com.tgac.logic.ckanren.PackageAccessor;
import com.tgac.logic.ckanren.RunnableConstraint;
import com.tgac.logic.finitedomain.FiniteDomainConstraints;
import com.tgac.logic.finitedomain.domains.Domain;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.MiniKanren;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import static com.tgac.logic.ckanren.CKanren.runConstraints;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ProcessPrefixFd {

	public static PackageAccessor processPrefix(HashMap<LVar<?>, Unifiable<?>> newSubstitutions, List<RunnableConstraint> constraints) {
		return s -> MiniKanren.prefixS(s.getSubstitutions(), newSubstitutions)
				.toJavaStream()
				.<PackageAccessor> map(ht -> ht
						.apply((x, v) ->
								FiniteDomainConstraints.getDom(s, x)
										.map(Domain.class::cast)
										.map(dom -> dom.processDom(v))
										.getOrElse(PackageAccessor.identity())
										.compose(runConstraints(x, constraints))))
				.reduce(PackageAccessor.identity(), PackageAccessor::compose)
				.apply(s.withSubstitutions(newSubstitutions));
	}
}
