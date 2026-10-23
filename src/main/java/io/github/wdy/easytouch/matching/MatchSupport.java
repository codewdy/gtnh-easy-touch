package io.github.wdy.easytouch.matching;

import java.util.Set;

/** Optional helpers, not a rule dispatcher: a matcher can use these or implement entirely different logic. */
public final class MatchSupport {
    private MatchSupport() { }

    public static boolean block(Set<String> allowed, TargetContext context) {
        String id = context.blockId();
        return id != null && (allowed.contains(id) || allowed.contains(id + "@" + context.blockMetadata()));
    }

    public static boolean exactClass(Set<String> allowed, Object value) {
        return value != null && allowed.contains(value.getClass().getName());
    }

    public static boolean classFamily(Set<String> allowed, Object value) {
        for (Class<?> type = value == null ? null : value.getClass(); type != null; type = type.getSuperclass()) {
            if (allowed.contains(type.getName())) return true;
        }
        return false;
    }

    public static boolean hasType(Class<?> type, String name) {
        if (type == null) return false;
        if (type.getName().equals(name)) return true;
        for (Class<?> iface : type.getInterfaces()) if (hasType(iface, name)) return true;
        return hasType(type.getSuperclass(), name);
    }
}
