package io.github.wdy.easytouch.matching;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** OR composition. Registration flattens this tree so nested simple rules can share the same index. */
public class AnyOfMatcher implements TargetMatcher {
    private final List<TargetMatcher> alternatives;

    public AnyOfMatcher(TargetMatcher... alternatives) {
        TargetMatcher[] copy = alternatives.clone();
        for (TargetMatcher matcher : copy) Objects.requireNonNull(matcher, "matcher");
        this.alternatives = Collections.unmodifiableList(Arrays.asList(copy));
    }

    final List<TargetMatcher> alternatives() { return alternatives; }

    @Override
    public final boolean matches(TargetContext context) {
        for (TargetMatcher matcher : alternatives) if (matcher.matches(context)) return true;
        return false;
    }
}
