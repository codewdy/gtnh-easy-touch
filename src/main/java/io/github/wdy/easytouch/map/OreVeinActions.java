package io.github.wdy.easytouch.map;

import org.apache.logging.log4j.LogManager;

/** Optional VisualProspecting integration using its rendered hit boxes and persistent action. */
final class OreVeinActions {
    private static final String MANAGER = "com.sinthoras.visualprospecting.integration.model.layers.OreVeinLayerManager";
    private OreVeinActions() {}
    static Object find(int x, int y) {
        try {
            Class<?> type = Class.forName(MANAGER);
            Object manager = type.getField("instance").get(null);
            if (!(Boolean) type.getMethod("isLayerActive").invoke(manager)) return null;
            Class<?> mods = Class.forName("com.gtnewhorizons.navigator.api.model.SupportedMods");
            Object mod = mods.getField("JourneyMap").get(null);
            if (!(Boolean) type.getMethod("isEnabled", mods).invoke(manager, mod)) return null;
            Object renderer = type.getMethod("getLayerRenderer", mods).invoke(manager, mod);
            Class<?> rendererType = Class.forName("com.gtnewhorizons.navigator.api.model.layers.LayerRenderer");
            for (Object step : (Iterable<?>) rendererType.getMethod("getRenderStepsForInteraction").invoke(renderer)) {
                if ((Boolean) step.getClass().getMethod("isMouseOver", int.class, int.class).invoke(step, x, y))
                    return step.getClass().getMethod("getLocation").invoke(step);
            }
        } catch (ClassNotFoundException e) {
            // The optional mod is absent.
        } catch (ReflectiveOperationException | RuntimeException e) {
            LogManager.getLogger("GTNH Easy Touch").warn("Ore vein hit detection failed", e);
        }
        return null;
    }
    static boolean isDepleted(Object ore) throws ReflectiveOperationException {
        return (Boolean) ore.getClass().getMethod("isDepleted").invoke(ore);
    }
    static void toggleDepleted(Object ore) throws ReflectiveOperationException {
        ore.getClass().getMethod("toggleOreVein").invoke(ore);
        Class<?> type = Class.forName(MANAGER);
        type.getMethod("forceRefresh").invoke(type.getField("instance").get(null));
    }
}
