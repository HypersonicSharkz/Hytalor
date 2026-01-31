package com.hypersonicsharkz.commands;

import com.hypersonicsharkz.HytalorPlugin;
import com.hypersonicsharkz.PatchManager;
import com.hypixel.hytale.assetstore.AssetPack;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.AssetModule;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ReloadPatchesCommand extends AbstractAsyncCommand {
    public ReloadPatchesCommand() {
        super("reload", "reloads, and reapplies, all patches");
    }

    @Nonnull
    @Override
    protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext commandContext) {
        commandContext.sendMessage(Message.raw("Unload existing Patches..."));

        HytalorPlugin.get().clearOverrideDirectory(false);

        List<AssetPack> assetPacks = AssetModule.get().getAssetPacks();

        for (AssetPack assetPack : assetPacks) {
            if (assetPack.getName().equals("com.hypersonicsharkz:Hytalor-Overrides")) {
                continue;
            }

            PatchManager.get().unloadPatchAssets(assetPack);
        }

        PatchManager.get().clear();

        commandContext.sendMessage(Message.raw("Loading Patches..."));
        for (AssetPack assetPack : assetPacks) {
            if (assetPack.getName().equals("com.hypersonicsharkz:Hytalor-Overrides")) {
                continue;
            }

            PatchManager.get().loadPatchAssets(assetPack);
        }

        commandContext.sendMessage(Message.raw("Applying Patches..."));

        PatchManager.get().applyAllPatches();

        commandContext.sendMessage(Message.raw("Finished!"));
        return CompletableFuture.completedFuture(null);
    }
}
