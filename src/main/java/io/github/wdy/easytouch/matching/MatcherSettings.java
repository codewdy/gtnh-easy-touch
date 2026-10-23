package io.github.wdy.easytouch.matching;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraftforge.common.config.Configuration;

/** Arbitrary settings scoped to one matcher; there is no central list of rule categories. */
public final class MatcherSettings {
    private final Configuration file;
    private final String category;

    public MatcherSettings(Configuration file, String id) {
        this.file = file;
        category = "matchers." + id;
    }

    public Set<String> strings(String key, String comment, String... defaults) {
        Set<String> result = new LinkedHashSet<String>();
        for (String entry : file.getStringList(key, category, defaults, comment)) {
            if (!entry.trim().isEmpty()) result.add(entry.trim());
        }
        return Collections.unmodifiableSet(result);
    }

    public boolean flag(String key, boolean fallback, String comment) {
        return file.getBoolean(key, category, fallback, comment);
    }

    public int integer(String key, int fallback, int min, int max, String comment) {
        return file.getInt(key, category, fallback, min, max, comment);
    }

    public String text(String key, String fallback, String comment) {
        return file.getString(key, category, fallback, comment);
    }
}
