package io.github.wdy.easytouch;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import net.minecraftforge.common.config.Configuration;
import org.junit.Test;
import static org.junit.Assert.*;

public class TouchConfigTest extends MatcherTestSupport {
    @Test
    public void touchForwardingDefaultsOnAndExplicitDisablePersists() throws Exception {
        File path = temp.newFile("touch.cfg");
        TouchConfig defaults = new TouchConfig(path);
        assertTrue(defaults.touchEnabled);
        assertFalse(defaults.touchLogEvents);
        Configuration file = new Configuration(path);
        file.get("touch", "enabled", true).set(false);
        file.get("touch", "logEvents", false).set(true);
        file.save();
        TouchConfig reloaded = new TouchConfig(path);
        assertFalse(reloaded.touchEnabled);
        assertTrue(reloaded.touchLogEvents);
    }

    @Test
    public void upgradeFromOriginalHeuristicsPreservesModeButIgnoresOldRules() throws Exception {
        File file = temp.newFile("upgrade.cfg");
        Files.write(file.toPath(), ("general {\n B:enabled=false\n}\n"
            + "rules {\n S:useItems <\n minecraft:bow\n >\n"
            + " S:useBlocks <\n unknown:box\n >\n S:useEntities <\n Zombie\n >\n}\n")
            .getBytes(StandardCharsets.UTF_8));
        TouchConfig config = new TouchConfig(file);
        assertFalse(config.enabled);
        assertTrue(config.matchers.matches(block("minecraft:chest", 0)));
        assertFalse(config.matchers.matches(block("unknown:box", 0)));
        assertFalse(config.matchers.matches(entity("Zombie", false)));
        config.toggle();
        TouchConfig reloaded = new TouchConfig(file);
        assertTrue(reloaded.enabled);
        assertTrue(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).contains("minecraft:bow"));
    }

    @Test
    public void emptyLegacyListsStayEmptyAcrossEveryMatcherAfterReload() throws Exception {
        File file = temp.newFile("empty.cfg");
        Configuration legacy = new Configuration(file);
        for (String key : new String[] {"blocks", "entities", "tamedEntities", "tileEntities", "gregTechMachines", "ae2Parts"}) {
            legacy.get("whitelist", key, new String[0]);
        }
        legacy.save();
        new TouchConfig(file);
        TouchConfig reloaded = new TouchConfig(file);
        for (String id : new String[] {"minecraft:chest", "IronChest:BlockIronChest", "TConstruct:CraftingStation",
            "appliedenergistics2:tile.BlockDrive"}) assertFalse(reloaded.matchers.matches(block(id, 0)));
        assertFalse(reloaded.matchers.matches(entity("Cow", false)));
        assertFalse(reloaded.matchers.matches(entity("Wolf", true)));
        Configuration saved = new Configuration(file);
        assertEquals(0, saved.getCategory("matchers.gregtech").get("machines").getStringList().length);
        assertEquals(0, saved.getCategory("matchers.applied_energistics").get("parts").getStringList().length);
        assertEquals(0, saved.getCategory("matchers.thaumcraft").get("tileEntities").getStringList().length);
    }

    @Test
    public void migrationPreservesCustomEntriesMetadataAndTamingPrecedence() throws Exception {
        File file = temp.newFile("custom.cfg");
        Configuration legacy = new Configuration(file);
        legacy.get("whitelist", "blocks", new String[] {"unknown:box@7", "IronChest:BlockIronChest@2",
            "TConstruct:ToolStationBlock@3", "appliedenergistics2:tile.BlockDrive"});
        legacy.get("whitelist", "entities", new String[] {"Wolf", "custom:pet"});
        legacy.get("whitelist", "tamedEntities", new String[] {"Wolf"});
        legacy.save();
        TouchConfig config = new TouchConfig(file);
        assertTrue(config.matchers.matches(block("unknown:box", 7)));
        assertFalse(config.matchers.matches(block("unknown:box", 6)));
        assertTrue(config.matchers.matches(block("IronChest:BlockIronChest", 2)));
        assertFalse(config.matchers.matches(block("IronChest:BlockIronChest", 3)));
        assertTrue(config.matchers.matches(block("TConstruct:ToolStationBlock", 3)));
        assertFalse(config.matchers.matches(block("TConstruct:ToolStationBlock", 0)));
        assertTrue(config.matchers.matches(block("appliedenergistics2:tile.BlockDrive", 0)));
        assertFalse(config.matchers.matches(block("minecraft:chest", 0)));
        assertFalse(config.matchers.matches(entity("Wolf", false)));
        assertTrue(config.matchers.matches(entity("Wolf", true)));
        assertTrue(config.matchers.matches(entity("custom:pet", false)));
    }

    @Test
    public void migrationDoesNotOverwriteNewSettingsOrReimportOldEntries() throws Exception {
        File file = temp.newFile("mixed.cfg");
        Configuration mixed = new Configuration(file);
        mixed.get("whitelist", "blocks", new String[] {"minecraft:chest"});
        mixed.get("matchers.vanilla_blocks", "blocks", new String[0]);
        mixed.save();
        assertFalse(new TouchConfig(file).matchers.matches(block("minecraft:chest", 0)));
        Configuration saved = new Configuration(file);
        saved.get("whitelist", "blocks", new String[0]).set(new String[] {"minecraft:furnace"});
        saved.save();
        TouchConfig reloaded = new TouchConfig(file);
        assertFalse(reloaded.matchers.matches(block("minecraft:chest", 0)));
        assertFalse(reloaded.matchers.matches(block("minecraft:furnace", 0)));
    }
}
