package io.github.wdy.easytouch;

import java.lang.reflect.Field;
import cpw.mods.fml.relauncher.FMLInjectionData;
import io.github.wdy.easytouch.matching.TargetContext;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraftforge.common.config.Configuration;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

/** Forge's configuration needs a game directory, but matcher tests do not need a running client. */
public abstract class MatcherTestSupport {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private Field minecraftHome;
    private Object originalHome;

    @Before
    public void provideForgeConfigRoot() throws Exception {
        minecraftHome = FMLInjectionData.class.getDeclaredField("minecraftHome");
        minecraftHome.setAccessible(true);
        originalHome = minecraftHome.get(null);
        minecraftHome.set(null, temp.getRoot());
    }

    @After
    public void restoreForgeConfigRoot() throws Exception { minecraftHome.set(null, originalHome); }

    protected Configuration configuration() throws Exception { return new Configuration(temp.newFile()); }

    protected static TestTarget block(String id, int metadata) {
        TestTarget target = new TestTarget(); target.blockId = id; target.metadata = metadata; return target;
    }

    protected static TestTarget entity(String id, boolean tamed) {
        TestTarget target = new TestTarget(); target.entityId = id; target.tamed = tamed; return target;
    }

    protected static TestTarget tile(TileEntity tile) {
        TestTarget target = new TestTarget(); target.tile = tile; return target;
    }

    protected static class TestTarget implements TargetContext {
        String blockId, entityId;
        int metadata;
        boolean tamed;
        TileEntity tile;
        MovingObjectPosition hit;
        public World world() { return null; }
        public EntityPlayer player() { return null; }
        public MovingObjectPosition hit() { return hit; }
        public String blockId() { return blockId; }
        public int blockMetadata() { return metadata; }
        public String entityId() { return entityId; }
        public boolean entityTamed() { return tamed; }
        public TileEntity tileEntity() { return tile; }
    }
}
