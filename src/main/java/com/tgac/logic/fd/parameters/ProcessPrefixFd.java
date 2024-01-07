package com.tgac.logic.fd.parameters;
import com.tgac.logic.LVar;
import com.tgac.logic.Unifiable;
import com.tgac.logic.cKanren.Constraint;
import com.tgac.logic.cKanren.PackageAccessor;
import com.tgac.logic.fd.domains.FiniteDomain;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import static com.tgac.logic.cKanren.CKanren.runConstraints;
import static com.tgac.logic.fd.FiniteDomainConstraints.getDom;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ProcessPrefixFd {

	public static PackageAccessor processPrefix(HashMap<LVar<?>, Unifiable<?>> prefix, List<Constraint> constraints) {
		return prefix.toJavaStream()
				.<PackageAccessor> map(ht -> ht
						.apply((x, v) ->
								s -> getDom(s, x)
										.map(FiniteDomain.class::cast)
										.map(dom -> dom.processDom(v))
										.getOrElse(PackageAccessor::identity)
										.compose(runConstraints(x, constraints))
										.apply(s)))
				.reduce(PackageAccessor.identity(), PackageAccessor::compose);
	}
}
