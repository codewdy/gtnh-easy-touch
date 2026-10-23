package io.github.wdy.easytouch.matching;

import io.github.wdy.easytouch.Action;
import io.github.wdy.easytouch.TouchConfig;
import net.minecraft.client.Minecraft;

public final class TargetResolver {
    private final MatcherRegistry matchers;

    public TargetResolver(TouchConfig config) {
        matchers = config.matchers;
    }

    public Action resolve(Minecraft mc) {
        if (mc.theWorld == null || mc.thePlayer == null) return Action.ATTACK;
        return matchers.matches(new MinecraftTargetContext(mc)) ? Action.USE : Action.ATTACK;
    }
}
