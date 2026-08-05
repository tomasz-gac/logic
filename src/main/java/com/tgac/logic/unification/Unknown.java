package com.tgac.logic.unification;

// ABOUTME: A NAME for something not yet determined — live (LVar, identity-named)
// ABOUTME: or canonical (Hole, number-named). The substitution's key type.

/**
 * The one kind of thing a substitution may bind: a name. Two worlds name
 * their unknowns differently — a live {@link LVar} is a binding site
 * identified by object identity, a canonical {@link Hole} is a token
 * identified by its number — but the binding map, walking, and the
 * renaming seeds work over names as such. Unification remains stricter
 * than naming: only live vars may be BOUND by it ({@code extend} keeps its
 * {@link LVar} signature, and reified terms refuse to re-enter
 * unification); an {@code Unknown} is walkable, never unifiable.
 */
public interface Unknown<T> extends Term<T> {
}
