package appeng.api.parts;

import net.minecraft.util.Vec3;

/** Test double for the optional AE2 read API; never included in the mod jar. */
public interface IPartHost {
    Object selectPart(Vec3 localPosition);
}
