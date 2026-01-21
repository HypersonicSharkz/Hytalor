package com.hypersonicsharkz;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hypersonicsharkz.util.IndexNode;
import com.hypersonicsharkz.util.JSONUtil;
import com.hypixel.hytale.assetstore.AssetPack;
import com.hypixel.hytale.common.util.FormatUtil;
import com.hypixel.hytale.logger.sentry.SkipSentryException;
import com.hypixel.hytale.server.core.asset.AssetModule;
import com.hypixel.hytale.server.core.asset.monitor.AssetMonitor;
import com.hypixel.hytale.server.core.asset.monitor.AssetMonitorHandler;
import com.hypixel.hytale.server.core.asset.monitor.EventKind;
import com.hypixel.hytale.server.core.util.io.FileUtil;

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
import java.util.Map;
import java.util.logging.Level;

public class PatchManager {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, List<Path>> patchesMap = new HashMap<>(); //Cache for base -> patches
    private final Map<Path, String> cachedPatchtoBaseMap = new HashMap<>(); //Cache for patchPath -> baseName

    public void addPatchAsset(String basePath, Path patchPath) {

        String cachedBase = cachedPatchtoBaseMap.get(patchPath);
        if (cachedBase != null && !cachedBase.equals(basePath)) {
            //Cached base is different, remove from old base
            List<Path> oldPatches = patchesMap.get(cachedBase);
            if (oldPatches != null) {
                oldPatches.remove(patchPath);
            }
            cachedPatchtoBaseMap.remove(patchPath);
        }

        if (patchesMap.containsKey(basePath)) {
            List<Path> patches = patchesMap.get(basePath);
            int index = patches.indexOf(patchPath);

            if (index != -1) { //Place at same index to retain load order
                patches.remove(index);
                patches.add(index, patchPath);
                patchesMap.put(basePath, patches);
            } else { //Otherwise just append
                patches.add(patchPath);
            }
        } else {
            patchesMap.put(basePath, new ArrayList<>(List.of(patchPath)));
        }

        cachedPatchtoBaseMap.put(patchPath, basePath);
    }

    private Path getBaseAssetPath(String baseAssetPath) {
        return AssetModule.get().getBaseAssetPack().getRoot().resolve("Server").resolve(baseAssetPath + ".json");
    }

    public void loadPatchAssets(AssetPack pack) {
        Path path = pack.getRoot().resolve(HytalorPlugin.PATCHES_ASSET_PATH);

        HytalorPlugin.get().getLogger().at(Level.FINE).log(
                "Loading patch assets for pack: " + pack.getName()
        );

        try {
            AssetMonitor assetMonitor = AssetModule.get().getAssetMonitor();
            if (assetMonitor != null && !pack.isImmutable() && Files.isDirectory(path)) {
                assetMonitor.removeMonitorDirectoryFiles(path, pack);
                assetMonitor.monitorDirectoryFiles(path, new PatchAssetMonitorHandler("PatchMonitor_" + pack.getName(), this));
            }

            if (Files.isDirectory(path)) {
                Files.walkFileTree(path, FileUtil.DEFAULT_WALK_TREE_OPTIONS_SET, Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {
                    @Nonnull
                    public FileVisitResult visitFile(@Nonnull Path file, @Nonnull BasicFileAttributes attrs) {
                        if (PatchManager.isJsonFile(file) && !PatchManager.isIgnoredFile(file)) {
                            PatchManager.this.loadPatch(file, false);
                        }

                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        } catch (IOException e) {
            throw new SkipSentryException(new RuntimeException(e));
        }

    }

    public void unloadPatchAssets(AssetPack pack) {
        Path path = pack.getRoot().resolve(HytalorPlugin.PATCHES_ASSET_PATH);

        HytalorPlugin.get().getLogger().at(Level.FINE).log(
                "Unloading patch assets for pack: " + pack.getName()
        );

        AssetMonitor assetMonitor = AssetModule.get().getAssetMonitor();
        if (assetMonitor != null && !pack.isImmutable() && Files.isDirectory(path)) {
            assetMonitor.removeMonitorDirectoryFiles(path, pack);
        }

        if (Files.isDirectory(path)) {
            try {
                Files.walkFileTree(path, FileUtil.DEFAULT_WALK_TREE_OPTIONS_SET, Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {
                    @Nonnull
                    public FileVisitResult visitFile(@Nonnull Path file, @Nonnull BasicFileAttributes attrs) {
                        if (PatchManager.isJsonFile(file) && !PatchManager.isIgnoredFile(file)) {
                            PatchManager.this.unloadPatch(file, false);
                        }

                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                throw new SkipSentryException(new RuntimeException(e));
            }
        }
    }

    public void loadPatch(Path path, boolean refresh) {
        JsonObject data = JSONUtil.readJSON(path);
        if (data == null)
            return;

        JsonElement basePath = data.get("BaseAssetPath");
        if (basePath == null)
            return;

        addPatchAsset(basePath.getAsString(), path);

        if (refresh)
            applyPatches(basePath.getAsString());
    }

    public void savePatchAsset(JsonObject combined, String baseAssetPath) {
        Path basePath = getBaseAssetPath(baseAssetPath);

        Path patchPath = HytalorPlugin.OVERRIDES_TEMP_PATH.resolve(basePath.getParent().toString().substring(1));

        try {
            Files.createDirectories(patchPath);
            Files.writeString(patchPath.resolve(basePath.getFileName().toString()), gson.toJson(combined));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void unloadPatch(Path path, boolean refresh) {
        String basePath = cachedPatchtoBaseMap.get(path);
        if (basePath == null)
            return;

        List<Path> patches = patchesMap.get(basePath);
        if (patches != null) {
            patches.remove(path);
        }

        cachedPatchtoBaseMap.remove(path);

        if (refresh)
            applyPatches(basePath);
    }

    private static boolean isJsonFile(@Nonnull Path path) {
        return Files.isRegularFile(path) && path.toString().endsWith(".json");
    }

    private static boolean isIgnoredFile(@Nonnull Path path) {
        return !path.getFileName().toString().isEmpty() && path.getFileName().toString().charAt(0) == '!';
    }

    public void applyPatches(String basePath) {
        long start = System.nanoTime();

        JsonObject combined = JSONUtil.readJSON(getBaseAssetPath(basePath));
        if (combined == null) {
            HytalorPlugin.get().getLogger().at(Level.SEVERE).log(
                    "Base asset not found for path: " + basePath
            );
            return;
        }

        List<Path> patches = patchesMap.get(basePath);
        if (patches == null) {
            HytalorPlugin.get().getLogger().at(Level.INFO).log(
                    "No patches found for base asset path: " + basePath
            );
            return;
        }

        IndexNode rootIndexNode = new IndexNode("root", null, null);

        HytalorPlugin.get().getLogger().at(Level.INFO).log(
                "Found " + patches.size() + " patches for base asset path: " + basePath + ". Applying patches..."
        );

        for (Path patch : patches) {
            JsonObject patchData = JSONUtil.readJSON(patch);
            if (patchData == null) {
                HytalorPlugin.get().getLogger().at(Level.SEVERE).log(
                        "Failed to read patch file at path: " + patch
                );
                continue;
            }

            JSONUtil.deepMerge(patchData, combined, rootIndexNode);

            HytalorPlugin.get().getLogger().at(Level.INFO).log(
                    "Applied patch: " + patch
            );
        }

        HytalorPlugin.get().getLogger().at(Level.INFO).log(
                "Applied " + patches.size() + " patches for base asset path: " + basePath + ". Took %s",
                FormatUtil.nanosToString(System.nanoTime() - start)
        );

        savePatchAsset(combined, basePath);
    }

    public void applyAllPatches() {
        for (String basePath : patchesMap.keySet()) {
            applyPatches(basePath);
        }
    }

    public static class PatchAssetMonitorHandler implements AssetMonitorHandler {
        private final String key;
        private final PatchManager overloadManager;

        public PatchAssetMonitorHandler(String key, PatchManager overloadManager) {
            this.key = key;
            this.overloadManager = overloadManager;
        }

        @Override
        public Object getKey() {
            return this.key;
        }

        @Override
        public boolean test(Path path, EventKind eventKind) {
            return path.toString().endsWith(".json");
        }

        @Override
        public void accept(Map<Path, EventKind> pathEventKindMap) {
            for (Map.Entry<Path, EventKind> entry : pathEventKindMap.entrySet()) {
                Path path = entry.getKey();
                EventKind eventKind = entry.getValue();
                if (eventKind == EventKind.ENTRY_CREATE || eventKind == EventKind.ENTRY_MODIFY) {
                    overloadManager.loadPatch(path, true);
                } else if (eventKind == EventKind.ENTRY_DELETE) {
                    overloadManager.unloadPatch(path, true);
                }
            }
        }
    }
}
