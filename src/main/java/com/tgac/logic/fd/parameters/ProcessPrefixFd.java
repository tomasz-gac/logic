package com.tgac.logic.fd.parameters;
import com.tgac.logic.LVar;
import com.tgac.logic.Unifiable;
import com.tgac.logic.cKanren.CKanren;
import com.tgac.logic.cKanren.Constraint;
import com.tgac.logic.cKanren.PackageAccessor;
import com.tgac.logic.cKanren.parameters.ProcessPrefix;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
public class ProcessPrefixFd implements ProcessPrefix {

	@Override
	public PackageAccessor processPrefix(HashMap<LVar<?>, Unifiable<?>> prefix, List<Constraint> constraints) {
		if (prefix.isEmpty()) {
			return PackageAccessor.identity();
		}
		return prefix.toJavaStream()
				.<PackageAccessor> map(ht -> ht.apply((x, v) ->
						s -> s.getDomain(x)
								.map(dom -> dom.processDom(v)
										.compose(CKanren.runConstraints(x, constraints))
										.apply(s))
								.getOrElse(() -> CKanren.runConstraints(x, constraints).apply(s))))
				.reduce(PackageAccessor.identity(), PackageAccessor::compose);
	}
}
