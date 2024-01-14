package com.tgac.logic.separate;
import com.tgac.logic.unification.LVar;
import com.tgac.logic.unification.Stored;
import com.tgac.logic.unification.Unifiable;
import io.vavr.collection.HashMap;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor(staticName = "of")
class NeqConstraint implements Stored {
	HashMap<LVar<?>, Unifiable<?>> separate;
}
