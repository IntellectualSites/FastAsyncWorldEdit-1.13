/*
 * WorldEdit, a Minecraft world manipulation toolkit
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldEdit team and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.sk89q.worldedit.command;

import com.boydti.fawe.Fawe;
import com.boydti.fawe.FaweVersion;
import com.boydti.fawe.config.Settings;
import com.intellectualsites.paster.IncendoPaster;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.command.util.CommandPermissions;
import com.sk89q.worldedit.command.util.CommandPermissionsConditionGenerator;
import com.sk89q.worldedit.command.util.HookMode;
import com.sk89q.worldedit.command.util.PrintCommandHelp;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.event.platform.ConfigurationLoadEvent;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extension.platform.Capability;
import com.sk89q.worldedit.extension.platform.Platform;
import com.sk89q.worldedit.extension.platform.PlatformManager;
import com.sk89q.worldedit.util.formatting.component.MessageBox;
import com.sk89q.worldedit.util.formatting.component.TextComponentProducer;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.util.formatting.text.TranslatableComponent;
import com.sk89q.worldedit.util.formatting.text.event.ClickEvent;
import com.sk89q.worldedit.util.formatting.text.format.TextColor;
import org.enginehub.piston.annotation.Command;
import org.enginehub.piston.annotation.CommandContainer;
import org.enginehub.piston.annotation.param.Arg;
import org.enginehub.piston.annotation.param.ArgFlag;
import org.enginehub.piston.annotation.param.Switch;

import java.io.File;
import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.zone.ZoneRulesException;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;

@CommandContainer(superTypes = {CommandPermissionsConditionGenerator.Registration.class})
public class WorldEditCommands {
    private static final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

    private final WorldEdit we;

    public WorldEditCommands(WorldEdit we) {
        this.we = we;
    }

    @Command(
        name = "version",
        aliases = { "ver" },
        desc = "Get the FAWE version"
    )
    @CommandPermissions(queued = false)
    public void version(Actor actor) {
        FaweVersion fVer = Fawe.get().getVersion();
        String fVerStr = fVer == null ? "unknown" : "-" + fVer.build;
        actor.print(TextComponent.of("FastAsyncWorldEdit" + fVerStr + " created by Empire92, MattBDev, IronApollo, dordsor21 and NotMyFault"));

        if (fVer != null) {
            FaweVersion version = Fawe.get().getVersion();
            Date date = new GregorianCalendar(2000 + version.year, version.month - 1, version.day)
                .getTime();

            TextComponent dateArg = TextComponent.of(date.toLocaleString());
            TextComponent commitArg = TextComponent.of(Integer.toHexString(version.hash));
            TextComponent buildArg = TextComponent.of(version.build);
            TextComponent platformArg = TextComponent.of(Settings.IMP.PLATFORM);

            actor.printInfo(TranslatableComponent.of("worldedit.version.version", dateArg, commitArg, buildArg, platformArg));
        }

        actor.printInfo(TextComponent.of("Wiki: https://wiki.intellectualsites.com/FastAsyncWorldEdit/index"));

        PlatformManager pm = we.getPlatformManager();

        TextComponentProducer producer = new TextComponentProducer();
        for (Platform platform : pm.getPlatforms()) {
            producer.append(
                    TextComponent.of("* ", TextColor.GRAY)
                    .append(TextComponent.of(platform.getPlatformName()))
                    .append(TextComponent.of("(" + platform.getPlatformVersion() + ")"))
            ).newline();
        }
        actor.print(new MessageBox("Platforms", producer, TextColor.GRAY).create());

        producer.reset();
        for (Capability capability : Capability.values()) {
            Platform platform = pm.queryCapability(capability);
            producer.append(
                TextComponent.of(capability.name(), TextColor.GRAY)
                    .append(TextComponent.of(": ")
                    .append(TextComponent.of(platform != null ? platform.getPlatformName() : "NONE")))
            ).newline();
        }
        actor.print(new MessageBox("Capabilities", producer, TextColor.GRAY).create());

    }

    @Command(
        name = "reload",
        desc = "Reload configuration and translations"
    )
    @CommandPermissions("worldedit.reload")
    public void reload(Actor actor) {
        we.getPlatformManager().queryCapability(Capability.CONFIGURATION).reload();
        we.getEventBus().post(new ConfigurationLoadEvent(we.getPlatformManager().queryCapability(Capability.CONFIGURATION).getConfiguration()));
        Fawe.get().setupConfigs();
        actor.printInfo(TranslatableComponent.of("worldedit.reload.config"));
    }

    @Command(
        name = "debugpaste",
        desc = "Writes a report of latest.log, config.yml, config-legacy.yml, strings.json to https://athion.net/ISPaster/paste"
    )
    @CommandPermissions(value = {"worldedit.report", "worldedit.debugpaste"}, queued = false)
    public void report(Actor actor) throws WorldEditException {
        String dest;
        try {
            final File logFile = new File(Fawe.imp().getDirectory(), "../../logs/latest.log");
            final File config = new File(Fawe.imp().getDirectory(), "config.yml");
            final File legacyConfig = new File(Fawe.imp().getDirectory(), "config-legacy.yml");
            dest = IncendoPaster.debugPaste(logFile, Fawe.imp().getDebugInfo(), config, legacyConfig);
        } catch (IOException e) {
            actor.printInfo(TextComponent.of(e.getMessage()));
            return;
        }
        actor.printInfo(TranslatableComponent.of("worldedit.report.written", TextComponent.of(dest).clickEvent(
            ClickEvent.openUrl(dest))));
    }

    @Command(
        name = "threads",
        desc = "Print all thread stacks"
    )
    @CommandPermissions(value = "worldedit.threads", queued = false)
    public void threads(Actor actor) throws WorldEditException {
        Map<Thread, StackTraceElement[]> stacks = Thread.getAllStackTraces();
        for (Map.Entry<Thread, StackTraceElement[]> entry : stacks.entrySet()) {
            Thread thread = entry.getKey();
            actor.printDebug(
                "--------------------------------------------------------------------------------------------");
            actor.printDebug(
                "Thread: " + thread.getName() + " | Id: " + thread.getId() + " | Alive: " + thread
                    .isAlive());
            for (StackTraceElement elem : entry.getValue()) {
                actor.printDebug(elem.toString());
            }
        }
    }

    @Command(
        name = "trace",
        desc = "Toggles trace hook"
    )
    void trace(Actor actor, LocalSession session,
               @Arg(desc = "The mode to set the trace hook to", def = "")
                   HookMode hookMode) {
        boolean previousMode = session.isTracingActions();
        boolean newMode;
        if (hookMode != null) {
            newMode = hookMode == HookMode.ACTIVE;
            if (newMode == previousMode) {
                actor.printError(TranslatableComponent.of(previousMode ? "worldedit.trace.active.already" : "worldedit.trace.inactive.already"));
                return;
            }
        } else {
            newMode = !previousMode;
        }
        session.setTracingActions(newMode);
        actor.printInfo(TranslatableComponent.of(newMode ? "worldedit.trace.active" : "worldedit.trace.inactive"));
    }

    @Command(
        name = "cui",
        desc = "Complete CUI handshake (internal usage)"
    )
    @CommandPermissions()
    public void cui(Player player, LocalSession session) {
        session.setCUISupport(true);
        session.dispatchCUISetup(player);
    }

    @Command(
        name = "tz",
        desc = "Set your timezone for snapshots"
    )
    public void tz(Actor actor, LocalSession session,
                   @Arg(desc = "The timezone to set")
                       String timezone) {
        try {
            ZoneId tz = ZoneId.of(timezone);
            session.setTimezone(tz);
            actor.printInfo(TranslatableComponent.of("worldedit.timezone.set", TextComponent.of(tz.getDisplayName(
                    TextStyle.FULL, actor.getLocale()
            ))));
            actor.printInfo(TranslatableComponent.of("worldedit.timezone.current",
                    TextComponent.of(dateFormat.withLocale(actor.getLocale()).format(ZonedDateTime.now(tz)))));
        } catch (ZoneRulesException e) {
            actor.printError(TranslatableComponent.of("worldedit.timezone.invalid"));
        }
    }

    @Command(
        name = "help",
        desc = "Displays help for WorldEdit commands"
    )
    @CommandPermissions(value = "worldedit.help", queued = false)
    public void help(Actor actor,
                     @Switch(name = 's', desc = "List sub-commands of the given command, if applicable")
                         boolean listSubCommands,
                     @ArgFlag(name = 'p', desc = "The page to retrieve", def = "1")
                         int page,
                     @Arg(desc = "The command to retrieve help for", def = "", variable = true)
                         List<String> command) throws WorldEditException {
        PrintCommandHelp.help(command, page, listSubCommands,
                we.getPlatformManager().getPlatformCommandManager().getCommandManager(), actor, "/worldedit help");
    }
}
