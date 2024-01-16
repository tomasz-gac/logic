package com.tgac.logic.step;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.Iterator;
import java.util.NoSuchElementException;

@RequiredArgsConstructor
public class StepIterator<A> implements Iterator<A> {
	@NonNull
	private Step<A> step;

	@Override
	public boolean hasNext() {
		return !step.isEmpty();
	}

	@Override
	public A next() {
		return step.accept(new Step.Visitor<A, A>() {
			@Override
			public A visit(Empty<A> empty) {
				throw new NoSuchElementException();
			}
			@Override
			public A visit(Incomplete<A> inc) {
				// this should be evaluated because of hasNext
				step = inc.getOrEvaluate();
				return step.accept(this);
			}
			@Override
			public A visit(Single<A> single) {
				step = Empty.instance();
				return single.getHead();
			}
			@Override
			public A visit(Cons<A> cons) {
				step = cons.getTail();
				return cons.getHead();
			}
		});
	}
}
