import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** Full installed production dependencies, one real native world and selected actual operations. */
public final class FoundationClientAudit implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext test) {
        var report = new LinkedHashMap<String,Object>();
        var checks = new ArrayList<String>(); report.put("checks", checks);
        var selection = System.getProperty("foundation.case", "imgui"); report.put("selection", selection);
        try {
            test.runOnClient(client -> {
                report.put("backend", RenderSystem.getDevice().getDeviceInfo().backendName());
                report.put("device", RenderSystem.getDevice().getDeviceInfo().name());
                var loaded = new TreeMap<String,String>();
                for (var name : List.of("sable", "veil", "imguimc", "simulated", "aeronautics", "offroad")) {
                    var container = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(name).orElseThrow();
                    loaded.put(name, container.getMetadata().getVersion().getFriendlyString());
                }
                report.put("installedProductionMods", loaded);
            });
            try (var world = System.getProperty("port.smoke.resumeWorld") == null ? test.worldBuilder().create()
                    : new net.fabricmc.fabric.impl.client.gametest.world.TestWorldSaveImpl(test,
                            Path.of(System.getProperty("port.smoke.resumeWorld"))).open()) {
                test.waitFor(client -> client.level != null && client.player != null
                        && client.gameRenderer.gameRenderState().levelRenderState.cameraRenderState.initialized, 400);
                checks.add("Actual installed production stack, integrated world and extracted camera");
                var helper = switch (selection) {
                    case "imgui" -> "FoundationImGuiChecks";
                    case "imgui-components" -> "FoundationImGuiComponentChecks";
                    case "editor", "graphics" -> "FoundationVeilChecks";
                    case "veil-passes" -> "FoundationVeilPassChecks";
                    case "physics", "save", "restore", "dimensions" -> "FoundationSableChecks";
                    default -> throw new IllegalArgumentException("Unknown foundation selection: " + selection);
                };
                try {
                    Class.forName(helper).getMethod("run", ClientGameTestContext.class, TestSingleplayerContext.class,
                            Map.class, List.class).invoke(null, test, world, report, checks);
                } catch (Throwable failure) {
                    report.put("liveFailure",test.computeOnClient(client->Map.of("player",client.player.position().toString(),
                            "eye",client.player.getEyePosition().toString(),"hit",String.valueOf(client.hitResult),
                            "screen",String.valueOf(client.gui.screen()),"mainHand",client.player.getMainHandItem().toString())));
                    report.put("liveFailureScreenshot",test.takeScreenshot("foundation-live-failure-"+selection).toString());
                    throw failure;
                }
                world.getServer().runOnServer(server -> server.saveEverything(false, true, true));
                report.put("savedWorld", world.getServer().computeOnServer(server -> server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toString()));
            }
            test.waitFor(client -> client.level == null, 200);
            checks.add("Native world save, disconnect and renderer retirement");
            report.put("status", "passed");
        } catch (Throwable failure) {
            var cause = failure instanceof java.lang.reflect.InvocationTargetException wrapped ? wrapped.getCause() : failure;
            report.put("status", "failed"); report.put("error", cause.toString());
            try { report.put("failureScreenshot", test.takeScreenshot("foundation-failure-" + selection).toString()); }
            catch (Throwable ignored) {}
            throw new RuntimeException(cause);
        } finally {
            try { Files.writeString(Path.of(System.getProperty("port.smoke.report")), new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n"); }
            catch (java.io.IOException failure) { throw new RuntimeException(failure); }
        }
    }
    /** GameTest's frame hook precedes ImGui. Read the last composited native target explicitly. */
    public static Path captureComposited(ClientGameTestContext test, String name) {
        return test.computeOnClient(client -> {
            var target = client.gameRenderer.mainRenderTarget();
            var texture = target.getColorTexture();
            check(texture.getFormat() == com.mojang.renderpearl.api.GpuFormat.RGBA8_UNORM,
                    "Composited audit capture requires the native RGBA8 target");
            var device = RenderSystem.getDevice();
            var path = Path.of(System.getProperty("port.smoke.report")).resolveSibling("composited-screenshots").resolve(name + ".png");
            try (var buffer = device.createBuffer(() -> "Foundation composited native capture",
                    com.mojang.renderpearl.api.buffers.GpuBuffer.USAGE_COPY_DST | com.mojang.renderpearl.api.buffers.GpuBuffer.USAGE_MAP_READ,
                    (long)target.width * target.height * 4);
                 var output = new com.mojang.blaze3d.platform.NativeImage(target.width, target.height, false)) {
                var encoder = device.createCommandEncoder();
                encoder.copyTextureToBuffer(texture, buffer, 0, () -> {}, 0);
                try (var fence = encoder.createFence()) {
                    encoder.submit();
                    check(fence.awaitCompletion(10_000_000_000L), "Composited native capture timed out");
                }
                try (var mapped = buffer.map(true, false)) {
                    var pixels = mapped.data();
                    for (int y = 0; y < target.height; y++) for (int x = 0; x < target.width; x++) {
                        int r = pixels.get() & 255, g = pixels.get() & 255, b = pixels.get() & 255, a = pixels.get() & 255;
                        // RenderTarget storage uses the Minecraft bottom-left convention;
                        // native surface presentation and vanilla Screenshot both flip it.
                        output.setPixel(x, target.height - 1 - y, a << 24 | r << 16 | g << 8 | b);
                    }
                }
                Files.createDirectories(path.getParent()); output.writeToFile(path);
                return path;
            } catch (java.io.IOException failure) { throw new RuntimeException(failure); }
        });
    }
    public static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
