package io.github.wdy.easytouch.matching;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Immutable block/entity ID rules. The registry unions these indexes instead of polling each instance. */
public class SimpleMatcher implements TargetMatcher {
    private final Set<String> blocks;
    private final Set<String> entities;

    protected SimpleMatcher(Builder rules) {
        blocks = Collections.unmodifiableSet(new HashSet<String>(rules.blocks));
        entities = Collections.unmodifiableSet(new HashSet<String>(rules.entities));
    }

    public static Builder builder() { return new Builder(); }

    /** Final so merging cannot discard a subclass's additional conditions. Use AnyOfMatcher for alternatives. */
    @Override
    public final boolean matches(TargetContext context) {
        if (!blocks.isEmpty() && MatchSupport.block(blocks, context)) return true;
        if (entities.isEmpty()) return false;
        String id = context.entityId();
        return id != null && entities.contains(id);
    }

    public static final class Builder {
        private final Set<String> blocks = new HashSet<String>();
        private final Set<String> entities = new HashSet<String>();

        private Builder() { }

        /** Exact registry names, optionally suffixed with @metadata; no wildcards. */
        public Builder blocks(String... ids) { return blocks(Arrays.asList(ids)); }

        public Builder blocks(Collection<String> ids) {
            blocks.addAll(ids);
            return this;
        }

        public Builder entities(String... ids) { return entities(Arrays.asList(ids)); }

        public Builder entities(Collection<String> ids) {
            entities.addAll(ids);
            return this;
        }

        Builder include(SimpleMatcher matcher) {
            blocks.addAll(matcher.blocks);
            entities.addAll(matcher.entities);
            return this;
        }

        /** Copies the rules; later builder changes cannot change a built or registered matcher. */
        public SimpleMatcher build() { return new SimpleMatcher(this); }
    }
}
