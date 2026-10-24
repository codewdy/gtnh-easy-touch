package io.github.wdy.easytouch;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Map;
import io.github.wdy.easytouch.input.MouseGesture;
import io.github.wdy.easytouch.matching.MatcherRegistry;
import io.github.wdy.easytouch.matching.TargetIdentity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.util.RegistrySimple;
import net.minecraftforge.common.config.Configuration;
import org.junit.BeforeClass;
import org.junit.AfterClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class TargetToggleTest extends MatcherTestSupport {
    private static Item pickedItem;
    private static Map<Object, Object> registryNames;

    @BeforeClass
    @SuppressWarnings("unchecked")
    public static void registerPickItem() throws Exception {
        pickedItem = new Item();
        // Populate only the name lookup needed by these tests; GameRegistry requires LaunchWrapper.
        Field names = RegistrySimple.class.getDeclaredField("registryObjects");
        names.setAccessible(true);
        registryNames = (Map<Object, Object>) names.get(Item.itemRegistry);
        registryNames.put("easytouch_test:target", pickedItem);
    }

    @AfterClass
    public static void removePickItem() {
        if (registryNames != null) registryNames.remove("easytouch_test:target");
    }

    private static TestTarget picked(String blockId, int variant, int metadata, int x) {
        return picked(blockId, new ItemStack(pickedItem, 1, variant), metadata, x);
    }

    private static TestTarget picked(String blockId, ItemStack stack, int metadata, int x) {
        TestTarget target = new TestTarget() {
            @Override public ItemStack pickedBlock() { return stack; }
        };
        target.blockId = blockId;
        target.metadata = metadata;
        target.hit = new MovingObjectPosition(x, 64, -7, 1, Vec3.createVectorHelper(x + 0.5, 64.5, -6.5));
        return target;
    }

    private static TestTarget mob(String id, boolean tamed) {
        TestTarget target = entity(id, tamed);
        target.hit = new MovingObjectPosition(0, 64, 0, 1, Vec3.createVectorHelper(0, 64, 0));
        target.hit.typeOfHit = MovingObjectPosition.MovingObjectType.ENTITY;
        return target;
    }

    @Test
    public void disablingBuiltInMatchOverridesEveryAllowRuleAndSurvivesReload() throws Exception {
        File file = temp.newFile("overrides.cfg");
        TouchConfig config = new TouchConfig(file);
        TestTarget chest = picked("minecraft:chest", 0, 2, 12);
        assertTrue(config.matchers.matches(chest));
        assertFalse(config.matchers.toggleTarget(chest).match);
        config.matchers.register("late_allow", settings -> context -> true);
        assertFalse(config.matchers.matches(chest));
        TouchConfig reloaded = new TouchConfig(file);
        assertFalse(reloaded.matchers.matches(picked("minecraft:chest", 0, 5, 200)));
        assertTrue(reloaded.matchers.toggleTarget(chest).match);
        assertTrue(new TouchConfig(file).matchers.matches(chest));
    }

    @Test
    public void enablingUnknownTypeDoesNotChangeGlobalModeOrOtherVariants() throws Exception {
        File file = temp.newFile("disabled-global.cfg");
        Configuration saved = new Configuration(file);
        saved.get("general", "enabled", false);
        saved.get("matchers.vanilla_blocks", "blocks", new String[] {"custom:box"});
        saved.save();
        TouchConfig config = new TouchConfig(file);
        TestTarget unknown = picked("example:unknown", 23, 0, 1);
        assertFalse(config.matchers.matches(unknown));
        assertTrue(config.matchers.toggleTarget(unknown).match);
        TouchConfig reloaded = new TouchConfig(file);
        assertFalse(reloaded.enabled);
        assertTrue(reloaded.matchers.matches(picked("example:unknown", 23, 4, 100)));
        assertFalse(reloaded.matchers.matches(picked("example:unknown", 24, 4, 100)));
        Configuration check = new Configuration(file);
        assertArrayEquals(new String[] {"custom:box"}, check.getCategory("matchers.vanilla_blocks").get("blocks").getStringList());
    }

    @Test
    public void pickItemDistinguishesMachinesAndPartsSharingOneWorldBlock() throws Exception {
        MatcherRegistry rules = new MatcherRegistry(configuration()).register("default", settings -> context -> true);
        TestTarget machine = picked("gregtech:gt.blockmachines", 101, 0, 1);
        assertFalse(rules.toggleTarget(machine).match);
        assertFalse(rules.matches(picked("gregtech:gt.blockmachines", 101, 8, 30)));
        assertTrue(rules.matches(picked("gregtech:gt.blockmachines", 102, 0, 1)));
        TestTarget terminal = picked("appliedenergistics2:tile.BlockCableBus", 481, 0, 2);
        assertFalse(rules.toggleTarget(terminal).match);
        assertFalse(rules.matches(picked("appliedenergistics2:tile.BlockCableBus", 481, 0, 20)));
        assertTrue(rules.matches(picked("appliedenergistics2:tile.BlockCableBus", 0, 0, 2)));
    }

    @Test
    public void countCustomNbtAndWorldStateDoNotChangePickedTypeIdentity() {
        ItemStack a = new ItemStack(pickedItem, 1, 12);
        ItemStack b = new ItemStack(pickedItem, 64, 12);
        b.setTagCompound(new NBTTagCompound());
        b.stackTagCompound.setString("owner", "someone else");
        assertEquals(TargetIdentity.resolve(picked("minecraft:furnace", a, 2, 1)).key,
            TargetIdentity.resolve(picked("minecraft:lit_furnace", b, 5, 30)).key);
    }

    @Test
    public void entityToggleAppliesToTheWholeSpeciesAndOverridesTamingDefaults() throws Exception {
        TouchConfig config = new TouchConfig(temp.newFile("entities.cfg"));
        TestTarget wild = mob("Wolf", false);
        TestTarget pet = mob("Wolf", true);
        assertFalse(config.matchers.matches(wild));
        assertTrue(config.matchers.matches(pet));
        assertTrue(config.matchers.toggleTarget(wild).match);
        assertTrue(config.matchers.matches(wild));
        assertTrue(config.matchers.matches(pet));
        assertFalse(config.matchers.toggleTarget(pet).match);
        assertFalse(config.matchers.matches(wild));
        assertFalse(config.matchers.matches(pet));
        assertTrue(config.matchers.matches(mob("Cow", false)));
        assertFalse(config.matchers.matches(mob("Zombie", false)));
    }

    @Test
    public void unpickableBlockFallbackDoesNotOverrideSelectableMultipartContents() throws Exception {
        MatcherRegistry rules = new MatcherRegistry(configuration());
        TestTarget fallback = picked("example:multipart", null, 0, 1);
        assertTrue(rules.toggleTarget(fallback).match);
        assertTrue(rules.matches(picked("example:multipart", null, 0, 2)));
        assertFalse(rules.matches(picked("example:multipart", 5, 0, 1)));
        assertFalse(rules.matches(picked("example:multipart", null, 1, 1)));
    }

    @Test
    public void missingOrMissedTargetsDoNotWriteOverrides() throws Exception {
        Configuration file = configuration();
        MatcherRegistry rules = new MatcherRegistry(file);
        assertNull(rules.toggleTarget(new TestTarget()));
        TestTarget miss = picked("example:box", 1, 0, 1);
        miss.hit.typeOfHit = MovingObjectPosition.MovingObjectType.MISS;
        assertNull(rules.toggleTarget(miss));
        assertNull(rules.toggleTarget(picked("minecraft:air", null, 0, 1)));
        assertEquals(0, file.getCategory("target_overrides").get("allow").getStringList().length);
        assertEquals(0, file.getCategory("target_overrides").get("deny").getStringList().length);
    }

    @Test
    public void entityOnlyOverridesDoNotQueryBlockPickHandlers() throws Exception {
        MatcherRegistry rules = MatcherRegistry.builtIns(configuration());
        rules.toggleTarget(mob("Cow", false));
        TestTarget chest = new TestTarget() {
            @Override public ItemStack pickedBlock() { throw new AssertionError("No block overrides to check"); }
        };
        chest.blockId = "minecraft:chest";
        chest.hit = picked("minecraft:chest", 0, 0, 0).hit;
        assertTrue(rules.matches(chest));
    }

    @Test
    public void conflictingManualListsPreferDenyAndNextToggleRemovesConflict() throws Exception {
        Configuration file = configuration();
        TestTarget target = picked("minecraft:chest", 0, 0, 0);
        String key = TargetIdentity.resolve(target).key;
        file.get("target_overrides", "allow", new String[] {key});
        file.get("target_overrides", "deny", new String[] {key});
        MatcherRegistry rules = MatcherRegistry.builtIns(file);
        assertFalse(rules.matches(target));
        assertTrue(rules.toggleTarget(target).match);
        assertArrayEquals(new String[] {key}, file.getCategory("target_overrides").get("allow").getStringList());
        assertEquals(0, file.getCategory("target_overrides").get("deny").getStringList().length);
    }

    @Test
    public void changingRuleDoesNotFlipAnOngoingMouseGesture() throws Exception {
        MatcherRegistry rules = MatcherRegistry.builtIns(configuration());
        TestTarget target = picked("minecraft:chest", 0, 0, 0);
        MouseGesture gesture = new MouseGesture();
        assertEquals(Action.USE, gesture.press(0, rules.matches(target) ? Action.USE : Action.ATTACK));
        rules.toggleTarget(target);
        assertEquals(Action.ATTACK, gesture.press(1, rules.matches(target) ? Action.USE : Action.ATTACK));
        gesture.release(0);
        gesture.release(1);
        assertEquals(Action.ATTACK, gesture.press(0, rules.matches(target) ? Action.USE : Action.ATTACK));
    }
}
