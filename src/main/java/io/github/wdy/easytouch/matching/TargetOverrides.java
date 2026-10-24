package io.github.wdy.easytouch.matching;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraftforge.common.config.Configuration;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

/** Explicit allow/deny decisions take precedence over every built-in matcher. */
public final class TargetOverrides {
    private static final String CATEGORY = "target_overrides";
    private final Configuration file;
    private final Set<String> allowed;
    private final Set<String> denied;
    private boolean blocks, entities;

    public TargetOverrides(Configuration file) {
        this.file = file;
        allowed = read("allow", "Target type keys explicitly enabled by the pointed-target shortcut.");
        denied = read("deny", "Target type keys explicitly disabled. Deny wins if present in both lists.");
        for (String key : allowed) includeKind(key);
        for (String key : denied) includeKind(key);
    }

    private Set<String> read(String property, String comment) {
        Set<String> result = new LinkedHashSet<String>();
        for (String value : file.getStringList(property, CATEGORY, new String[0], comment)) {
            if (!value.trim().isEmpty()) result.add(value.trim());
        }
        return result;
    }

    private void includeKind(String key) {
        blocks |= key.startsWith("block_item:") || key.startsWith("block_state:");
        entities |= key.startsWith("entity:") || key.startsWith("entity_class:");
    }

    public boolean appliesTo(TargetContext context) {
        if ((!blocks && !entities) || context.hit() == null) return false;
        return context.isBlock() ? blocks : entities && context.hit().typeOfHit == MovingObjectType.ENTITY;
    }

    public Boolean get(String key) {
        return denied.contains(key) ? Boolean.FALSE : allowed.contains(key) ? Boolean.TRUE : null;
    }

    public void set(String key, boolean match) {
        includeKind(key);
        allowed.remove(key);
        denied.remove(key);
        (match ? allowed : denied).add(key);
        write("allow", allowed);
        write("deny", denied);
        file.save();
    }

    private void write(String property, Set<String> values) {
        String[] entries = values.toArray(new String[0]);
        Arrays.sort(entries);
        file.get(CATEGORY, property, new String[0]).set(entries);
    }
}
