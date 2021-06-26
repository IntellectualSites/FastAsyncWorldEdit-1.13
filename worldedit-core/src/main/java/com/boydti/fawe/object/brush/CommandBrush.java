package com.boydti.fawe.object.brush;

import com.boydti.fawe.util.StringMan;
import com.boydti.fawe.wrappers.AsyncPlayer;
import com.boydti.fawe.wrappers.LocationMaskedPlayerWrapper;
import com.boydti.fawe.wrappers.SilentPlayerWrapper;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.command.tool.brush.Brush;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.event.platform.CommandEvent;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extension.platform.PlatformCommandManager;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.selector.CuboidRegionSelector;
import com.sk89q.worldedit.util.Location;

import java.util.List;

public class CommandBrush implements Brush {

    private final String command;

    public CommandBrush(String command) {
        this.command = command.charAt(0) == '/' ? "/" + command : command;
    }

    @Override
    public void build(EditSession editSession, BlockVector3 position, Pattern pattern, double size) throws MaxChangedBlocksException {
        int radius = (int) size;
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
        Location face = player.getBlockTraceFace(256, true);
        if (face == null) {
            position = position.add(0, 1, 1);
        } else {
            position = position.add(face.getDirection().toBlockPoint());
        }
        player.setSelection(selector);
        AsyncPlayer wePlayer = new SilentPlayerWrapper(new LocationMaskedPlayerWrapper(player, new Location(player.getExtent(), position.toVector3())));
        List<String> cmds = StringMan.split(replaced, ';');
        for (String cmd : cmds) {
            CommandEvent event = new CommandEvent(wePlayer, cmd);
            PlatformCommandManager.getInstance().handleCommandOnCurrentThread(event);
        }
    }
}
