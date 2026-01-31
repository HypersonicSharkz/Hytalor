package com.hypersonicsharkz;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hypersonicsharkz.builders.PatchBuilder;
import com.hypersonicsharkz.util.JSONUtil;
import com.hypersonicsharkz.util.QueryUtil;
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
import java.util.*;
import java.util.logging.Level;
import java.util.regex.Pattern;

public class PatchManager {
    private static PatchManager instance;

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, List<Path>> patchesMap = new HashMap<>(); //Cache for base -> patches
    private final Map<Path, List<String>> cachedPatchtoBaseMap = new HashMap<>(); //Cache for patchPath -> baseName
    private final Map<String, Path> cachedBasePathMap = new HashMap<>(); //Cache for baseName -> basePath

    public static PatchManager get() {
        if (instance == null) {
            instance = new PatchManager();
        }

        return instance;
    }

    private PatchManager() {}

    public void clear() {
        patchesMap.clear();
        cachedPatchtoBaseMap.clear();
        cachedBasePathMap.clear();
    }

    public void addPatchAsset(String basePath, Path patchPath) {
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

        cachedPatchtoBaseMap.computeIfAbsent(patchPath, k -> new ArrayList<>()).add(basePath);
    }

    private List<Map.Entry<String, Path>> getBaseAssets(String pattern) {
        Pattern regex = QueryUtil.globToRegex(pattern);

        return cachedBasePathMap.entrySet()
                .stream()
                .filter(entry -> regex.matcher(entry.getKey()).matches())
                .toList();
    }

    public void loadPatchAssets(AssetPack pack) {
        Path path = pack.getRoot().resolve(HytalorPlugin.PATCHES_ASSET_PATH);

        HytalorPlugin.get().getLogger().at(Level.FINE).log(
                "Loading patch assets for pack: " + pack.getName()
        );

        if (!pack.getName().equals("com.hypersonicsharkz:Hytalor-Overrides")) {
            cacheAssetPaths(pack);
        }

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

        JsonElement basePathPattern = data.get("BaseAssetPath");
        if (basePathPattern == null)
            return;

        List<Map.Entry<String, Path>> baseAssets = getBaseAssets(basePathPattern.getAsString());
        if (baseAssets.isEmpty()) {
            HytalorPlugin.get().getLogger().at(Level.WARNING).log(
                    "No base assets found for patch using base path pattern: " + basePathPattern.getAsString()
            );
            return;
        }

        if (cachedPatchtoBaseMap.containsKey(path)) {
            unloadPatch(path, false);
        }

        for (Map.Entry<String, Path> basePath : baseAssets) {
            addPatchAsset(basePath.getKey(), path);

            if (refresh)
                applyPatches(basePath.getKey());
        }
    }

    public void savePatchAsset(JsonObject combined, Path overridePath) {
        try {
            Files.createDirectories(overridePath.getParent());
            Files.writeString(overridePath, gson.toJson(combined));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void unloadPatch(Path path, boolean refresh) {
        List<String> baseNames = cachedPatchtoBaseMap.get(path);
        if (baseNames == null)
            return;

        for (String baseName : baseNames) {
            List<Path> patches = patchesMap.get(baseName);
            if (patches != null) {
                patches.remove(path);
            }

            if (refresh)
                applyPatches(baseName);
        }

        cachedPatchtoBaseMap.remove(path);
    }

    private static boolean isJsonFile(@Nonnull Path path) {
        return Files.isRegularFile(path) && path.toString().endsWith(".json");
    }

    private static boolean isIgnoredFile(@Nonnull Path path) {
        return !path.getFileName().toString().isEmpty() && path.getFileName().toString().charAt(0) == '!';
    }

    public void applyPatches(String basePathPattern) {
        List<Map.Entry<String, Path>> baseAssets = getBaseAssets(basePathPattern);
        for (Map.Entry<String, Path> entry : baseAssets) {
            applyPatches(entry.getKey(), entry.getValue());
        }
    }

    public void applyPatches(String baseName, Path basePath) {
        long start = System.nanoTime();

        JsonObject combined = JSONUtil.readJSON(basePath);
        if (combined == null) {
            HytalorPlugin.get().getLogger().at(Level.WARNING).log(
                    "Base asset not found for path: " + baseName
            );
            return;
        }

        List<Path> patches = patchesMap.get(baseName);
        if (patches == null) {
            HytalorPlugin.get().getLogger().at(Level.INFO).log(
                    "No patches found for base asset path: " + baseName
            );
            return;
        }

        HytalorPlugin.get().getLogger().at(Level.INFO).log(
                "Found " + patches.size() + " patches for base asset path: " + baseName + ". Applying patches..."
        );

        List<PatchObject> patchesJSON = new ArrayList<>();

        for (Path patch : patches) {
            JsonObject patchData = JSONUtil.readJSON(patch);
            if (patchData == null) {
                HytalorPlugin.get().getLogger().at(Level.WARNING).log(
                        "Failed to read patch file at path: " + patch
                );
                continue;
            }

            patchesJSON.add(new PatchObject(patchData, patch));
        }

        patchesJSON.sort((a, b) -> {
            int weightA = a.patch.has("_priority") ? a.patch.get("_priority").getAsInt() : 0;
            int weightB = b.patch.has("_priority") ? b.patch.get("_priority").getAsInt() : 0;

            return Integer.compare(weightB, weightA);
        });

        for (PatchObject patchData : patchesJSON) {
            JSONUtil.deepMerge(patchData.patch, combined);

            HytalorPlugin.get().getLogger().at(Level.INFO).log(
                    "Applied patch: " + patchData.path
            );
        }

        HytalorPlugin.get().getLogger().at(Level.INFO).log(
                "Applied " + patches.size() + " patches for base asset path: " + baseName + ". Took %s",
                FormatUtil.nanosToString(System.nanoTime() - start)
        );

        Path overridePath = HytalorPlugin.OVERRIDES_TEMP_PATH.resolve("Server/" + baseName + ".json");

        savePatchAsset(combined, overridePath);
    }

    public void applyAllPatches() {
        for (String basePath : patchesMap.keySet()) {
            applyPatches(basePath);
        }
    }

    private void cacheAssetPaths(AssetPack pack) {
        Path path = pack.getRoot().resolve("Server");

        try {
            if (Files.isDirectory(path)) {
                Files.walkFileTree(path, FileUtil.DEFAULT_WALK_TREE_OPTIONS_SET, Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {
                    @Nonnull
                    public FileVisitResult visitFile(@Nonnull Path file, @Nonnull BasicFileAttributes attrs) {
                        if (PatchManager.isJsonFile(file) && !PatchManager.isIgnoredFile(file)) {
                            String relativePath = path.relativize(file).toString().replace(".json", "");
                            relativePath = relativePath.replace("\\", "/");
                            cachedBasePathMap.put(relativePath, file);
                        }

                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        } catch (IOException e) {
            throw new SkipSentryException(new RuntimeException(e));
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

    private record PatchObject(JsonObject patch, Path path) {}
}
