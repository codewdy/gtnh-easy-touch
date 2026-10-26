package io.github.wdy.easytouch.matching;

/**
 * A read-only smart touch rule. False is not a veto: another matcher may still accept the target.
 * Evaluation may be reordered or short-circuited when declarative rules are merged.
 */
@FunctionalInterface
public interface TargetMatcher {
    boolean matches(TargetContext context);
}
