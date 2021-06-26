package com.boydti.fawe.object.brush;

import com.boydti.fawe.object.collection.LocalBlockVectorSet;
import com.boydti.fawe.util.StringMan;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.event.platform.CommandEvent;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extension.platform.PlatformCommandManager;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.selector.CuboidRegionSelector;

import java.util.List;

public class ScatterCommand extends ScatterBrush {
    private final String command;

    public ScatterCommand(int count, int distance, String command) {
        super(count, distance);
        this.command = command;
    }

    @Override
    public void apply(EditSession editSession, LocalBlockVectorSet placed, BlockVector3 position, Pattern p, double size) throws MaxChangedBlocksException {
        int radius = getDistance();
        CuboidRegionSelector selector = new CuboidRegionSelector(editSession.getWorld(), position.subtract(radius, radius, radius), position.add(radius, radius, radius));
        String replaced = command.replace("{x}", position.getBlockX() + "")
                .replace("{y}", Integer.toString(position.getBlockY()))
                .replace("{z}", Integer.toString(position.getBlockZ()))
                .replace("{world}", editSession.getWorld().getName())
                .replace("{size}", Integer.toString(radius));

        Actor actor = editSession.getActor();
        if (!(actor instanceof Player)) {
            return; //todo throw error
        }
        Player player = (Player) actor;
        player.setSelection(selector);
        List<String> cmds = StringMan.split(replaced, ';');
        for (String cmd : cmds) {
            CommandEvent event = new CommandEvent(player, cmd);
            PlatformCommandManager.getInstance().handleCommandOnCurrentThread(event);
        }
    }
}
