package com.hypersonicsharkz;

import com.hypixel.hytale.assetstore.AssetPack;
import com.hypixel.hytale.common.plugin.PluginManifest;
import com.hypixel.hytale.common.semver.Semver;
import com.hypixel.hytale.common.util.FormatUtil;
import com.hypixel.hytale.server.core.asset.AssetModule;
import com.hypixel.hytale.server.core.asset.AssetPackRegisterEvent;
import com.hypixel.hytale.server.core.asset.AssetPackUnregisterEvent;
import com.hypixel.hytale.server.core.asset.LoadAssetEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.server.core.util.BsonUtil;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;

public class HytalorPlugin extends JavaPlugin {
    public static String PATCHES_ASSET_PATH = "Server/Patch";
    public static Path OVERRIDES_TEMP_PATH = PluginManager.MODS_PATH.resolve("HytalorOverrides");

    private final PatchManager patchManager = new PatchManager();

    private static HytalorPlugin instance;

    public static HytalorPlugin get() {
        return instance;
    }

    public HytalorPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        instance = this;

        super.setup();

        this.initializeOverrideDirectory();

        this.getEventRegistry().register((short)128, LoadAssetEvent.class, (event) -> {
            this.getLogger().at(Level.INFO).log("Loading Hytalor Patch assets phase...");
            long start = System.nanoTime();
            List<AssetPack> assetPacks = AssetModule.get().getAssetPacks();

            for (AssetPack assetPack : assetPacks) {
                patchManager.loadPatchAssets(assetPack);
            }

            patchManager.applyAllPatches();

            this.getLogger()
                    .at(Level.INFO)
                    .log(
                            "Loading Hytalor Patch assets phase completed! Boot time %s, Took %s",
                            FormatUtil.nanosToString(System.nanoTime() - event.getBootStart()),
                            FormatUtil.nanosToString(System.nanoTime() - start)
                    );
        });

        getEventRegistry().register(AssetPackRegisterEvent.class, event -> {
            patchManager.loadPatchAssets(event.getAssetPack());
            patchManager.applyAllPatches();
        });
        getEventRegistry().register(AssetPackUnregisterEvent.class, event -> {
            patchManager.unloadPatchAssets(event.getAssetPack());
            patchManager.applyAllPatches();
        });
    }

    private void initializeOverrideDirectory() {
        try {
            Path filePath = OVERRIDES_TEMP_PATH;
            boolean exists = Files.isDirectory(filePath);

            Path targetRoot = filePath.resolve("Server");
            Files.createDirectories(targetRoot);
            Path sourceRoot = AssetModule.get().getBaseAssetPack().getRoot().resolve("Server");

            Files.walkFileTree(sourceRoot, new SimpleFileVisitor<Path>() {

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    // Resolve the relative path from source into target
                    Path relativePath = sourceRoot.relativize(dir);
                    Path targetDir = targetRoot.resolve(relativePath.toString());

                    // Create directory if it doesn't exist
                    if (Files.notExists(targetDir)) {
                        Files.createDirectories(targetDir);
                    }

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    // Skip files completely
                    return FileVisitResult.CONTINUE;
                }
            });

            PluginManifest manifest = new PluginManifest(
                    "com.hypersonicsharkz",
                    "Hytalor-Overrides",
                    Semver.fromString("1.0.0"),
                    "Temp folder for Hytalor asset overrides",
                    new ArrayList<>(),
                    "",
                    null,
                    null,
                    new HashMap<>(),
                    new HashMap<>(),
                    new HashMap<>(),
                    new ArrayList<>(),
                    false
            );

            BsonUtil.writeSync(filePath.resolve("manifest.json"), PluginManifest.CODEC, manifest, this.getLogger());

            if (!exists) {
                AssetModule.get().registerPack(
                        "com.hypersonicsharkz:Hytalor-Overrides",
                        filePath,
                        manifest
                );
            } else {
                AssetModule.get().getAssetMonitor().markChanged(filePath);
            }

        } catch (IOException e) {
            getLogger().at(Level.SEVERE).log("Failed to initialize Hytalor Overrides directory!", e);
            throw new RuntimeException(e);
        }
    }
}
