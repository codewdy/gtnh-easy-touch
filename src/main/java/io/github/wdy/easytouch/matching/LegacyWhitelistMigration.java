package io.github.wdy.easytouch.matching;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

/** One-time 0.2.0 configuration conversion. New matcher types do not need to be added here. */
public final class LegacyWhitelistMigration {
    private LegacyWhitelistMigration() { }

    public static void apply(Configuration file) {
        if (!file.hasCategory("whitelist")) return;
        Property migrated = file.get("general", "legacyWhitelistMigrated", false);
        if (migrated.getBoolean()) return;

        Property blocks = file.getCategory("whitelist").get("blocks");
        if (blocks != null) {
            Map<String, List<String>> split = new LinkedHashMap<String, List<String>>();
            for (String id : new String[] {"vanilla_blocks", "iron_chests", "tinkers_construct", "applied_energistics"}) {
                split.put(id, new ArrayList<String>());
            }
            for (String value : blocks.getStringList()) {
                String entry = value.trim();
                String id = entry.startsWith("IronChest:") ? "iron_chests"
                    : entry.startsWith("TConstruct:") ? "tinkers_construct"
                    : entry.startsWith("appliedenergistics2:") ? "applied_energistics" : "vanilla_blocks";
                split.get(id).add(entry);
            }
            for (Map.Entry<String, List<String>> entry : split.entrySet()) {
                putIfMissing(file, entry.getKey(), "blocks", entry.getValue().toArray(new String[0]));
            }
        }
        copy(file, "entities", "vanilla_entities", "entities");
        copy(file, "tamedEntities", "vanilla_entities", "tamedEntities");
        copy(file, "tileEntities", "thaumcraft", "tileEntities");
        copy(file, "gregTechMachines", "gregtech", "machines");
        copy(file, "ae2Parts", "applied_energistics", "parts");
        migrated.set(true);
        file.addCustomCategoryComment("whitelist", "Migrated to matchers.*; these old lists are no longer read.");
    }

    private static void copy(Configuration file, String oldKey, String matcher, String key) {
        Property value = file.getCategory("whitelist").get(oldKey);
        if (value != null) putIfMissing(file, matcher, key, value.getStringList());
    }

    private static void putIfMissing(Configuration file, String matcher, String key, String[] values) {
        String category = "matchers." + matcher;
        if (!file.hasKey(category, key)) file.get(category, key, values).set(values);
    }
}
