package com.hypersonicsharkz.commands;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

import javax.annotation.Nonnull;

public class HytalorCommandCollection extends AbstractCommandCollection {
    public HytalorCommandCollection() {
        super("hytalor", "Hytalor Commands");
        addSubCommand(new ReloadPatchesCommand());
    }
}
