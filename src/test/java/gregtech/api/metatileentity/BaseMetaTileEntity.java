package gregtech.api.metatileentity;

import net.minecraft.tileentity.TileEntity;

/** Test double for the optional GT read API; never included in the mod jar. */
public class BaseMetaTileEntity extends TileEntity {
    private final Object machine;
    public BaseMetaTileEntity(Object machine) { this.machine = machine; }
    public Object getMetaTileEntity() { return machine; }
}
