package com.tgac.logic.separate;
import com.tgac.logic.unification.Constraint;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.HashMap;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor(staticName = "of")
public class NeqConstraint implements Constraint {
	HashMap<LVar<?>, Unifiable<?>> separate;
}
