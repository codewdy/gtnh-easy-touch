package io.github.wdy.easytouch.map;

/** Independent multiplier for JourneyMap 5.2 map icons, in framebuffer pixels. */
public final class JourneyMapScale {
    private static volatile float multiplier = 1.0f;
    private JourneyMapScale() {}
    public static void configure(double value) {
        multiplier = Double.isFinite(value) ? (float) Math.max(1.0, Math.min(8.0, value)) : 2.0f;
    }
    public static float scale(float original) { return original * multiplier; }
    private static boolean supportedOverlay(Object step) {
        String name = step.getClass().getName();
        return name.equals("com.sinthoras.visualprospecting.integration.model.render.OreVeinRenderStep")
            || name.equals("com.sinthoras.visualprospecting.integration.model.render.UndergroundFluidRenderStep")
            || name.equals("com.dyonovan.tcnodetracker.integration.navigator.ThaumcraftNodeRenderStep");
    }
    public static float overlayScale(float original, Object step) {
        return supportedOverlay(step) ? scale(original) : original;
    }
    public static double overlayFontScale(double original, Object step) {
        return supportedOverlay(step) ? original * multiplier : original;
    }
    public static double nodeAspectScale(double original, boolean journeyMap) {
        return journeyMap ? original * multiplier : original;
    }
    public static int minimapDimension(int original, int width, int height) {
        return Math.max(1, Math.min(dimension(original), (int) (Math.min(width, height) * 0.8)));
    }
    public static int dimension(int original) { return Math.round(scale(original)); }
}
