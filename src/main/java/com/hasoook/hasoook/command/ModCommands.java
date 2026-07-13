package com.hasoook.hasoook.command;

import net.neoforged.neoforge.event.RegisterCommandsEvent;

public class ModCommands {
    public static void register(RegisterCommandsEvent event) {
        PaintQuestCommand.register(event.getDispatcher());
    }
}