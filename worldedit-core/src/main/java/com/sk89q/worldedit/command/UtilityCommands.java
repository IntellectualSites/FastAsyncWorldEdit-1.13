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
import com.boydti.fawe.config.Caption;
import com.boydti.fawe.config.Settings;
import com.boydti.fawe.object.DelegateConsumer;
import com.boydti.fawe.object.function.QuadFunction;
import com.boydti.fawe.util.MainUtil;
import com.boydti.fawe.util.image.ImageUtil;
import com.google.common.base.Function;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalConfiguration;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.command.argument.HeightConverter;
import com.sk89q.worldedit.command.util.CommandPermissions;
import com.sk89q.worldedit.command.util.CommandPermissionsConditionGenerator;
import com.sk89q.worldedit.command.util.CreatureButcher;
import com.sk89q.worldedit.command.util.EntityRemover;
import com.sk89q.worldedit.command.util.Logging;
import com.sk89q.worldedit.command.util.PrintCommandHelp;
import com.sk89q.worldedit.command.util.WorldEditAsyncCommandBuilder;
import com.sk89q.worldedit.entity.Entity;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.function.EntityFunction;
import com.sk89q.worldedit.function.mask.BlockTypeMask;
import com.sk89q.worldedit.function.mask.ExistingBlockMask;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.function.visitor.EntityVisitor;
import com.sk89q.worldedit.internal.annotation.Direction;
import com.sk89q.worldedit.internal.annotation.VertHeight;
import com.sk89q.worldedit.internal.expression.EvaluationException;
import com.sk89q.worldedit.internal.expression.Expression;
import com.sk89q.worldedit.internal.expression.ExpressionException;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector2;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.CylinderRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.util.formatting.component.SubtleFormat;
import com.sk89q.worldedit.util.formatting.text.Component;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.util.formatting.text.format.TextColor;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BlockTypes;
import org.enginehub.piston.annotation.Command;
import org.enginehub.piston.annotation.CommandContainer;
import org.enginehub.piston.annotation.param.Arg;
import org.enginehub.piston.annotation.param.ArgFlag;
import org.enginehub.piston.annotation.param.Switch;
import org.enginehub.piston.exception.StopExecutionException;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;

import static com.sk89q.worldedit.command.util.Logging.LogMode.PLACEMENT;

/**
 * Utility commands.
 */
@CommandContainer(superTypes = CommandPermissionsConditionGenerator.Registration.class)
public class UtilityCommands {

    private final WorldEdit we;

    public UtilityCommands(WorldEdit we) {
        this.we = we;
    }

    @Command(
        name = "/macro",
        desc = "Generate or run a macro"
    )
    @CommandPermissions("worldedit.macro")
    public void macro(Player player, LocalSession session, String name, String argument) throws IOException {

    }

    @Command(
        name = "/heightmapinterface",
            aliases = { "/hmi", "hmi" },
        desc = "Generate the heightmap interface: https://github.com/IntellectualSites/HeightMap"
    )
    @CommandPermissions("fawe.admin")
    public void heightmapInterface(Player player, @Arg(name = "min", desc = "int", def = "100") int min, @Arg(name = "max", desc = "int", def = "200") int max) throws IOException {
        player.print(TextComponent.of("Please wait while we generate the minified heightmaps."));
        File srcFolder = MainUtil.getFile(Fawe.imp().getDirectory(), Settings.IMP.PATHS.HEIGHTMAP);

        File webSrc = new File(Fawe.imp().getDirectory(), "web" + File.separator + "heightmap");
        File minImages = new File(webSrc, "images" + File.separator + "min");
        File maxImages = new File(webSrc, "images" + File.separator + "max");
        final int sub = srcFolder.getAbsolutePath().length();
        List<String> images = new ArrayList<>();
        MainUtil.iterateFiles(srcFolder, file -> {
            switch (file.getName().substring(file.getName().lastIndexOf('.')).toLowerCase(Locale.ROOT)) {
                case ".png":
                case ".jpeg":
                    break;
                default:
                    return;
            }
            try {
                String name = file.getAbsolutePath().substring(sub);
                if (name.startsWith(File.separator)) {
                    name = name.replaceFirst(java.util.regex.Pattern.quote(File.separator), "");
                }
                BufferedImage img = MainUtil.readImage(file);
                BufferedImage minImg = ImageUtil.getScaledInstance(img, min, min, RenderingHints.VALUE_INTERPOLATION_BILINEAR, true);
                BufferedImage maxImg = max == -1 ? img : ImageUtil.getScaledInstance(img, max, max, RenderingHints.VALUE_INTERPOLATION_BILINEAR, true);
                player.print(TextComponent.of(String.format("Writing %s", name)));
                File minFile = new File(minImages, name);
                File maxFile = new File(maxImages, name);
                minFile.getParentFile().mkdirs();
                maxFile.getParentFile().mkdirs();
                ImageIO.write(minImg, "png", minFile);
                ImageIO.write(maxImg, "png", maxFile);
                images.add(name);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        StringBuilder config = new StringBuilder();
        config.append("var images = [\n");
        for (String image : images) {
            config.append('"' + image.replace(File.separator, "/") + "\",\n");
        }
        config.append("];\n");
        config.append("// The low res images (they should all be the same size)\n");
        config.append("var src_min = \"images/min/\";\n");
        config.append("// The max resolution images (Use the same if there are no higher resolution ones available)\n");
        config.append("var src_max = \"images/max/\";\n");
        config.append("// The local source for the image (used in commands)\n");
        config.append("var src_local = \"file://\";\n");
        File configFile = new File(webSrc, "config.js");
        player.print(TextComponent.of(String.format("Writing %s", configFile)));
        Files.write(configFile.toPath(), config.toString().getBytes());
        player.print(TextComponent.of("Done! See: `FastAsyncWorldEdit/web/heightmap`"));
    }

    @Command(
        name = "/cancel",
        aliases = {"fcancel"},
        desc = "Cancel your current command"
    )
    @CommandPermissions(value = "fawe.cancel", queued = false)
    public void cancel(Player player) {
        int cancelled = player.cancel(false);
        player.print(Caption.of("fawe.cancel.worldedit.cancel.count", cancelled));
    }

    @Command(
        name = "/fill",
        desc = "Fill a hole"

    )
    @CommandPermissions("worldedit.fill")
    @Logging(PLACEMENT)
    public int fill(Actor actor, LocalSession session, EditSession editSession,
                    @Arg(desc = "The blocks to fill with")
                        Pattern pattern,
                    @Arg(desc = "The radius to fill in")
                        Expression radiusExp,
                    @Arg(desc = "The depth to fill", def = "1")
                        int depth,
                    @Arg(desc = "The direction to move", def = "down")
                        @Direction BlockVector3 direction) throws WorldEditException, EvaluationException {
        double radius = radiusExp.evaluate();
        radius = Math.max(1, radius);
        we.checkMaxRadius(radius);
        depth = Math.max(1, depth);

        BlockVector3 pos = session.getPlacementPosition(actor);
        int affected = editSession.fillDirection(pos, pattern, radius, depth, direction);
        actor.print(Caption.of("worldedit.fill.created", TextComponent.of(affected)));
        return affected;
    }

/*
    @Command(
        name = "/fillr",
        desc = "Fill a hole recursively"
        name = "patterns",
        desc = "View help about patterns",
        descFooter = "Patterns determine what blocks are placed\n" +
            " - Use [brackets] for arguments\n" +
            " - Use , to OR multiple\n" +
            "e.g., #surfacespread[10][#existing],andesite\n" +
            "More Info: https://git.io/vSPmA"
    )
    @CommandQueued(false)
    @CommandPermissions("worldedit.patterns")
    public void patterns(Player player, LocalSession session, InjectedValueAccess args) throws WorldEditException {
        displayModifierHelp(player, DefaultPatternParser.class, args);
    }

    @Command(
        name = "masks",
        desc = "View help about masks",
        descFooter = "Masks determine if a block can be placed\n" +
            " - Use [brackets] for arguments\n" +
            " - Use , to OR multiple\n" +
            " - Use & to AND multiple\n" +
            "e.g., >[stone,dirt],#light[0][5],$jungle\n" +
            "More Info: https://git.io/v9r4K"
    )
    @CommandQueued(false)
    @CommandPermissions("worldedit.masks")
    public void masks(Player player, LocalSession session, InjectedValueAccess args) throws WorldEditException {
        displayModifierHelp(player, DefaultMaskParser.class, args);
    }

    @Command(
        name = "transforms",
        desc = "View help about transforms",
        descFooter = "Transforms modify how a block is placed\n" +
            " - Use [brackets] for arguments\n" +
            " - Use , to OR multiple\n" +
            " - Use & to AND multiple\n" +
            "More Info: https://git.io/v9KHO",
    )
    @CommandQueued(false)
    @CommandPermissions("worldedit.transforms")
    public void transforms(Player player, LocalSession session, InjectedValueAccess args) throws WorldEditException {
        displayModifierHelp(player, DefaultTransformParser.class, args);
    }

    private void displayModifierHelp(Player player, Class<? extends FaweParser> clazz, InjectedValueAccess args) {
        FaweParser parser = FaweAPI.getParser(clazz);
        if (args.argsLength() == 0) {
            String base = getCommand().aliases()[0];
            UsageMessage msg = new UsageMessage(getCallable(), "/" + base, args.getLocals());
            msg.newline().paginate(base, 0, 1).send(player);
            return;
        }
        if (parser != null) {
            CommandMapping mapping = parser.getDispatcher().get(args.getString(0));
            if (mapping != null) {
                new UsageMessage(mapping.getCallable(), args.getString(0), args.getLocals()) {
                    @Override
                    public String separateArg(String arg) {
                        return "&7[" + arg + "&7]";
                    }
                }.send(player);
            } else {
                UtilityCommands.help(args, player, getCommand().aliases()[0] + " ", parser.getDispatcher());
            }
        }
    }
*/

    @Command(
        name = "/fillr",
        desc = "Fill a hole recursively"
    )
    @CommandPermissions("worldedit.fill.recursive")
    @Logging(PLACEMENT)
    public int fillr(Actor actor, LocalSession session, EditSession editSession,
                     @Arg(desc = "The blocks to fill with")
                         Pattern pattern,
                     @Arg(desc = "The radius to fill in")
                         Expression radiusExp,
                     @Arg(desc = "The depth to fill", def = "")
                         Integer depth) throws WorldEditException {
        double radius = radiusExp.evaluate();
        radius = Math.max(1, radius);
        we.checkMaxRadius(radius);
        depth = depth == null ? Integer.MAX_VALUE : Math.max(1, depth);
        we.checkMaxRadius(radius);

        BlockVector3 pos = session.getPlacementPosition(actor);
        int affected = editSession.fillXZ(pos, pattern, radius, depth, true);
        actor.print(Caption.of("worldedit.fillr.created", TextComponent.of(affected)));
        return affected;
    }

    @Command(
        name = "/drain",
        desc = "Drain a pool"
    )
    @CommandPermissions("worldedit.drain")
    @Logging(PLACEMENT)
    public int drain(Actor actor, LocalSession session, EditSession editSession,
                     @Arg(desc = "The radius to drain")
                         Expression radiusExp,
                     @Switch(name = 'w', desc = "Also un-waterlog blocks")
                         boolean waterlogged,
                     @Switch(name = 'p', desc = "Also remove water plants")
                         boolean plants) throws WorldEditException {
        double radius = radiusExp.evaluate();
        radius = Math.max(0, radius);
        we.checkMaxRadius(radius);
        int affected = editSession.drainArea(session.getPlacementPosition(actor), radius, waterlogged, plants);
        actor.print(Caption.of("worldedit.drain.drained", TextComponent.of(affected)));
        return affected;
    }

    @Command(
        name = "fixlava",
        aliases = { "/fixlava" },
        desc = "Fix lava to be stationary"
    )
    @CommandPermissions("worldedit.fixlava")
    @Logging(PLACEMENT)
    public int fixLava(Actor actor, LocalSession session, EditSession editSession,
                       @Arg(desc = "The radius to fix in")
                           double radius) throws WorldEditException {
        radius = Math.max(0, radius);
        we.checkMaxRadius(radius);
        int affected = editSession.fixLiquid(session.getPlacementPosition(actor), radius, BlockTypes.LAVA);
        actor.print(Caption.of("worldedit.fixlava.fixed", TextComponent.of(affected)));
        return affected;
    }

    @Command(
        name = "fixwater",
        aliases = { "/fixwater" },
        desc = "Fix water to be stationary"
    )
    @CommandPermissions("worldedit.fixwater")
    @Logging(PLACEMENT)
    public int fixWater(Actor actor, LocalSession session, EditSession editSession,
                        @Arg(desc = "The radius to fix in")
                            double radius) throws WorldEditException {
        radius = Math.max(0, radius);
        we.checkMaxRadius(radius);
        int affected = editSession.fixLiquid(session.getPlacementPosition(actor), radius, BlockTypes.WATER);
        actor.print(Caption.of("worldedit.fixwater.fixed", TextComponent.of(affected)));
        return affected;
    }

    @Command(
        name = "removeabove",
        aliases = { "/removeabove" },
        desc = "Remove blocks above your head."
    )
    @CommandPermissions("worldedit.removeabove")
    @Logging(PLACEMENT)
    public int removeAbove(Actor actor, World world, LocalSession session, EditSession editSession,
                           @Arg(desc = "The apothem of the square to remove from", def = "1")
                               int size,
                           @Arg(desc = "The maximum height above you to remove from", def = "")
                               Integer height) throws WorldEditException {
        size = Math.max(1, size);
        we.checkMaxRadius(size);
        height = height != null ? Math.min((world.getMaxY() + 1), height + 1) : (world.getMaxY() + 1);

        int affected = editSession.removeAbove(session.getPlacementPosition(actor), size, height);
        actor.print(Caption.of("worldedit.removeabove.removed", TextComponent.of(affected)));
        return affected;
    }

    @Command(
        name = "removebelow",
        aliases = { "/removebelow" },
        desc = "Remove blocks below you."
    )
    @CommandPermissions("worldedit.removebelow")
    @Logging(PLACEMENT)
    public int removeBelow(Actor actor, World world, LocalSession session, EditSession editSession,
                           @Arg(desc = "The apothem of the square to remove from", def = "1")
                               int size,
                           @Arg(desc = "The maximum height below you to remove from", def = "")
                               Integer height) throws WorldEditException {
        size = Math.max(1, size);
        we.checkMaxRadius(size);
        height = height != null ? Math.min((world.getMaxY() + 1), height + 1) : (world.getMaxY() + 1);

        int affected = editSession.removeBelow(session.getPlacementPosition(actor), size, height);
        actor.print(Caption.of("worldedit.removebelow.removed", TextComponent.of(affected)));
        return affected;
    }

    @Command(
        name = "removenear",
        aliases = { "/removenear" },
        desc = "Remove blocks near you."
    )
    @CommandPermissions("worldedit.removenear")
    @Logging(PLACEMENT)
    public int removeNear(Actor actor, LocalSession session, EditSession editSession,
                          @Arg(desc = "The mask of blocks to remove")
                              Mask mask,
                          @Arg(desc = "The radius of the square to remove from", def = "50")
                              int radius) throws WorldEditException {
        radius = Math.max(1, radius);
        we.checkMaxRadius(radius);

        int affected = editSession.removeNear(session.getPlacementPosition(actor), mask, radius);
        actor.print(Caption.of("worldedit.removenear.removed", TextComponent.of(affected)));
        return affected;
    }

    @Command(
        name = "replacenear",
        aliases = { "/replacenear" },
        desc = "Replace nearby blocks"
    )
    @CommandPermissions("worldedit.replacenear")
    @Logging(PLACEMENT)
    public int replaceNear(Actor actor, World world, LocalSession session, EditSession editSession,
                           @Arg(desc = "The radius of the square to remove in")
                               int radius,
                           @Arg(desc = "The mask matching blocks to remove", def = "")
                               Mask from,
                           @Arg(desc = "The pattern of blocks to replace with")
                               Pattern to) throws WorldEditException {
        radius = Math.max(1, radius);
        we.checkMaxRadius(radius);

        BlockVector3 base = session.getPlacementPosition(actor);
        BlockVector3 min = base.subtract(radius, radius, radius);
        BlockVector3 max = base.add(radius, radius, radius);
        Region region = new CuboidRegion(world, min, max);

        if (from == null) {
            from = new ExistingBlockMask(editSession);
        }

        int affected = editSession.replaceBlocks(region, from, to);
        actor.print(Caption.of("worldedit.replacenear.replaced", TextComponent.of(affected)));
        return affected;
    }


    @Command(
        name = "snow",
        aliases = { "/snow" },
        desc = "Simulates snow"
    )
    @CommandPermissions("worldedit.snow")
    @Logging(PLACEMENT)
    public int snow(Actor actor, LocalSession session, EditSession editSession,
                    @Arg(desc = "The radius of the cylinder to snow in", def = "10")
                        double size,
                    @Arg(
                        desc = "The height of the cylinder to snow in",
                        def = HeightConverter.DEFAULT_VALUE
                    )
                    @VertHeight
                        int height,
                    @Switch(name = 's', desc = "Stack snow layers")
                        boolean stack) throws WorldEditException {
        size = Math.max(1, size);
        height = Math.max(1, height);
        we.checkMaxRadius(size);

        BlockVector3 position = session.getPlacementPosition(actor);

        CylinderRegion region = new CylinderRegion(position, Vector2.at(size, size), position.getBlockY() - height, position.getBlockY() + height);
        int affected = editSession.simulateSnow(region, stack);
        actor.print(Caption.of(
            "worldedit.snow.created", TextComponent.of(affected)
        ));
        return affected;
    }

    @Command(
        name = "thaw",
        aliases = { "/thaw" },
        desc = "Thaws the area"
    )
    @CommandPermissions("worldedit.thaw")
    @Logging(PLACEMENT)
    public int thaw(Actor actor, LocalSession session, EditSession editSession,
                    @Arg(desc = "The radius of the cylinder to thaw in", def = "10")
                        double size,
                    @Arg(
                        desc = "The height of the cylinder to thaw in",
                        def = HeightConverter.DEFAULT_VALUE
                    )
                    @VertHeight
                        int height) throws WorldEditException {
        size = Math.max(1, size);
        height = Math.max(1, height);
        we.checkMaxRadius(size);

        int affected = editSession.thaw(session.getPlacementPosition(actor), size, height);
        actor.print(Caption.of(
            "worldedit.thaw.removed", TextComponent.of(affected)
        ));
        return affected;
    }

    @Command(
        name = "green",
        aliases = { "/green" },
        desc = "Converts dirt to grass blocks in the area"
    )
    @CommandPermissions("worldedit.green")
    @Logging(PLACEMENT)
    public int green(Actor actor, LocalSession session, EditSession editSession,
                     @Arg(desc = "The radius of the cylinder to convert in", def = "10")
                         double size,
                     @Arg(
                         desc = "The height of the cylinder to convert in",
                         def = HeightConverter.DEFAULT_VALUE
                     )
                     @VertHeight
                         int height,
                     @Switch(name = 'f', desc = "Also convert coarse dirt")
                         boolean convertCoarse) throws WorldEditException {
        size = Math.max(1, size);
        height = Math.max(1, height);
        we.checkMaxRadius(size);
        final boolean onlyNormalDirt = !convertCoarse;

        final int affected = editSession.green(
            session.getPlacementPosition(actor), size, height, onlyNormalDirt
        );
        actor.print(Caption.of(
            "worldedit.green.changed", TextComponent.of(affected)
        ));
        return affected;
    }

    @Command(
        name = "extinguish",
        aliases = { "/ex", "/ext", "/extinguish", "ex", "ext" },
        desc = "Extinguish nearby fire"
    )
    @CommandPermissions("worldedit.extinguish")
    @Logging(PLACEMENT)
    public int extinguish(Actor actor, LocalSession session, EditSession editSession,
                           @Arg(desc = "The radius of the square to remove in", def = "")
                               Integer radius) throws WorldEditException {

        LocalConfiguration config = we.getConfiguration();

        int defaultRadius = config.maxRadius != -1 ? Math.min(40, config.maxRadius) : 40;
        int size = radius != null ? Math.max(1, radius) : defaultRadius;
        we.checkMaxRadius(size);

        Mask mask = new BlockTypeMask(editSession, BlockTypes.FIRE);
        int affected = editSession.removeNear(session.getPlacementPosition(actor), mask, size);
        actor.print(Caption.of("worldedit.extinguish.removed", TextComponent.of(affected)));
        return affected;
    }

    @Command(
        name = "butcher",
        aliases = { "/butcher" },
        desc = "Kill all or nearby mobs"
    )
    @CommandPermissions("worldedit.butcher")
    @Logging(PLACEMENT)
    public int butcher(Actor actor,
                       @Arg(desc = "Radius to kill mobs in", def = "")
                           Integer radius,
                       @Switch(name = 'p', desc = "Also kill pets")
                           boolean killPets,
                       @Switch(name = 'n', desc = "Also kill NPCs")
                           boolean killNpcs,
                       @Switch(name = 'g', desc = "Also kill golems")
                           boolean killGolems,
                       @Switch(name = 'a', desc = "Also kill animals")
                           boolean killAnimals,
                       @Switch(name = 'b', desc = "Also kill ambient mobs")
                           boolean killAmbient,
                       @Switch(name = 't', desc = "Also kill mobs with name tags")
                           boolean killWithName,
                       @Switch(name = 'f', desc = "Also kill all friendly mobs (Applies the flags `-abgnpt`)")
                           boolean killFriendly,
                       @Switch(name = 'r', desc = "Also destroy armor stands")
                                   boolean killArmorStands,
                       @Switch(name = 'w', desc = "Also kill water mobs")
                                   boolean killWater) throws WorldEditException {
        LocalConfiguration config = we.getConfiguration();

        if (radius == null) {
            radius = config.butcherDefaultRadius;
        } else if (radius < -1) {
            actor.print(Caption.of("worldedit.butcher.explain-all"));
            return 0;
        } else if (radius == -1) {
            if (config.butcherMaxRadius != -1) {
                radius = config.butcherMaxRadius;
            }
        }
        if (config.butcherMaxRadius != -1) {
            radius = Math.min(radius, config.butcherMaxRadius);
        }

        CreatureButcher flags = new CreatureButcher(actor);
        flags.or(CreatureButcher.Flags.FRIENDLY, killFriendly); // No permission check here. Flags will instead be filtered by the subsequent calls.
        flags.or(CreatureButcher.Flags.PETS, killPets, "worldedit.butcher.pets");
        flags.or(CreatureButcher.Flags.NPCS, killNpcs, "worldedit.butcher.npcs");
        flags.or(CreatureButcher.Flags.GOLEMS, killGolems, "worldedit.butcher.golems");
        flags.or(CreatureButcher.Flags.ANIMALS, killAnimals, "worldedit.butcher.animals");
        flags.or(CreatureButcher.Flags.AMBIENT, killAmbient, "worldedit.butcher.ambient");
        flags.or(CreatureButcher.Flags.TAGGED, killWithName, "worldedit.butcher.tagged");
        flags.or(CreatureButcher.Flags.ARMOR_STAND, killArmorStands, "worldedit.butcher.armorstands");
        flags.or(CreatureButcher.Flags.WATER, killWater, "worldedit.butcher.water");

        int killed = killMatchingEntities(radius, actor, flags::createFunction);

        actor.print(Caption.of(
                "worldedit.butcher.killed",
                TextComponent.of(killed),
                TextComponent.of(radius)
        ));

        return killed;
    }

    @Command(
        name = "remove",
        aliases = { "rem", "rement", "/remove", "/rem", "/rement" },
        desc = "Remove all entities of a type"
    )
    @CommandPermissions("worldedit.remove")
    @Logging(PLACEMENT)
    public int remove(Actor actor,
                      @Arg(desc = "The type of entity to remove")
                          EntityRemover remover,
                      @Arg(desc = "The radius of the cuboid to remove from")
                          int radius) throws WorldEditException {
        if (radius < -1) {
            actor.print(Caption.of("worldedit.remove.explain-all"));
            return 0;
        }

        int removed = killMatchingEntities(radius, actor, remover::createFunction);
        actor.print(Caption.of("worldedit.remove.removed", TextComponent.of(removed)));
        return removed;
    }

    private int killMatchingEntities(Integer radius, Actor actor, Supplier<EntityFunction> func) throws IncompleteRegionException,
            MaxChangedBlocksException {
        List<EntityVisitor> visitors = new ArrayList<>();

        LocalSession session = we.getSessionManager().get(actor);
        BlockVector3 center = session.getPlacementPosition(actor);
        EditSession editSession = session.createEditSession(actor);
        List<? extends Entity> entities;
        if (radius >= 0) {
            CylinderRegion region = CylinderRegion.createRadius(editSession, center, radius);
            entities = editSession.getEntities(region);
        } else {
            entities = editSession.getEntities();
        }
        visitors.add(new EntityVisitor(entities.iterator(), func.get()));

        int killed = 0;
        for (EntityVisitor visitor : visitors) {
            Operations.completeLegacy(visitor);
            killed += visitor.getAffected();
        }

        session.remember(editSession);
        editSession.close();
        return killed;
    }

    private DecimalFormat formatForLocale(Locale locale) {
        DecimalFormat format = (DecimalFormat) NumberFormat.getInstance(locale);
        format.applyPattern("#,##0.#####");
        return format;
    }

    @Command(
        name = "/calculate",
        aliases = { "/calc", "/eval", "/evaluate", "/solve" },
        desc = "Evaluate a mathematical expression"
    )
    @CommandPermissions("worldedit.calc")
    public void calc(Actor actor,
                     @Arg(desc = "Expression to evaluate", variable = true)
                         List<String> input) {
        Expression expression;
        try {
            expression = Expression.compile(String.join(" ", input));
        } catch (ExpressionException e) {
            actor.print(Caption.of(
                    "worldedit.calc.invalid.with-error",
                    TextComponent.of(String.join(" ", input)),
                    TextComponent.of(e.getMessage())
            ));
            return;
        }
        WorldEditAsyncCommandBuilder.createAndSendMessage(actor, () -> {
            double result = expression.evaluate(
                    new double[]{}, WorldEdit.getInstance().getSessionManager().get(actor).getTimeout());
            String formatted = Double.isNaN(result) ? "NaN" : formatForLocale(actor.getLocale()).format(result);
            return SubtleFormat.wrap(input + " = ").append(TextComponent.of(formatted, TextColor.LIGHT_PURPLE));
        }, (Component) null);
    }

    @Command(
        name = "/help",
        desc = "Displays help for WorldEdit commands"
    )
    @CommandPermissions("worldedit.help")
    public void help(Actor actor,
                     @Switch(name = 's', desc = "List sub-commands of the given command, if applicable")
                         boolean listSubCommands,
                     @ArgFlag(name = 'p', desc = "The page to retrieve", def = "1")
                         int page,
                     @Arg(desc = "The command to retrieve help for", def = "", variable = true)
                         List<String> command) throws WorldEditException {
        PrintCommandHelp.help(command, page, listSubCommands,
                we.getPlatformManager().getPlatformCommandManager().getCommandManager(), actor, "//help");
    }


    @Command(
        name = "/confirm",
        desc = "Confirm a command"
    )
    @CommandPermissions(value = "fawe.confirm", queued = false)
    public void confirm(Player player) throws WorldEditException {
        if (!player.confirm()) {
            player.print(Caption.of("fawe.worldedit.utility.nothing.confirmed"));
        }
    }

    public static List<Map.Entry<URI, String>> filesToEntry(final File root, final List<File> files, final UUID uuid) {
        return files.stream()
            .map(input -> { // Keep this functional, as transform is evaluated lazily
                URI uri = input.toURI();
                String path = getPath(root, input, uuid);
                return new SimpleEntry<>(uri, path);
            }).collect(Collectors.toList());
    }

    public static enum URIType {
        URL,
        FILE,
        DIRECTORY,
        OTHER
    }

    public static List<Component> entryToComponent(File root, List<Map.Entry<URI, String>> entries, Function<URI, Boolean> isLoaded, QuadFunction<String, String, URIType, Boolean, Component> adapter) {
        return entries.stream().map(input -> {
            URI uri = input.getKey();
            String path = input.getValue();

            boolean url = false;
            boolean loaded = isLoaded.apply(uri);

            URIType type = URIType.FILE;

            String name = path;
            String uriStr = uri.toString();
            if (uriStr.startsWith("file:/")) {
                File file = new File(uri.getPath());
                name = file.getName();
                if (file.isDirectory()) {
                    type = URIType.DIRECTORY;
                } else {
                    if (name.indexOf('.') != -1) {
                        name = name.substring(0, name.lastIndexOf('.'));
                    }
                }
                try {
                    if (!MainUtil.isInSubDirectory(root, file)) {
                        throw new RuntimeException(
                            new StopExecutionException(TextComponent.of("Invalid path")));
                    }
                } catch (IOException ignored) {
                }
            } else if (uriStr.startsWith("http://") || uriStr.startsWith("https://")) {
                type = URIType.URL;
            } else {
                type = URIType.OTHER;
            }

            return adapter.apply(name, path, type, loaded);
        }).collect(Collectors.toList());
    }

    public static List<File> getFiles(File dir, Actor actor, List<String> args, String formatName, boolean playerFolder, boolean oldFirst, boolean newFirst) {
        List<File> fileList = new LinkedList<>();
        getFiles(dir, actor, args, formatName, playerFolder, fileList::add);

        if (fileList.isEmpty()) {
            actor.print(Caption.of("fawe.worldedit.schematic.schematic.none"));
            return Collections.emptyList();
        }

        final int sortType = oldFirst ? -1 : newFirst ? 1 : 0;
        // cleanup file list
        fileList.sort((f1, f2) -> {
            boolean dir1 = f1.isDirectory();
            boolean dir2 = f2.isDirectory();
            if (dir1 != dir2) {
                return dir1 ? -1 : 1;
            }
            int res;
            if (sortType == 0) { // use name by default
                int p = f1.getParent().compareTo(f2.getParent());
                if (p == 0) { // same parent, compare names
                    res = f1.getName().compareTo(f2.getName());
                } else { // different parent, sort by that
                    res = p;
                }
            } else {
                res = Long.compare(f1.lastModified(), f2.lastModified()); // use date if there is a flag
                if (sortType == 1) {
                    res = -res; // flip date for newest first instead of oldest first
                }
            }
            return res;
        });

        return fileList;
    }

    public static void getFiles(File dir, Actor actor, List<String> args, String formatName, boolean playerFolder, Consumer<File> forEachFile) {
        Consumer<File> rootFunction = forEachFile;
        //schem list all <path>

        int len = args.size();
        List<String> filters = new ArrayList<>();

        String dirFilter = File.separator;

        boolean listMine = false;
        boolean listGlobal = !Settings.IMP.PATHS.PER_PLAYER_SCHEMATICS;
        if (len > 0) {
            for (String arg : args) {
                switch (arg.toLowerCase(Locale.ROOT)) {
                    case "me":
                    case "mine":
                    case "local":
                    case "private":
                        listMine = true;
                        break;
                    case "public":
                    case "global":
                        listGlobal = true;
                        break;
                    case "all":
                        listMine = true;
                        listGlobal = true;
                        break;
                    default:
                        if (arg.endsWith("/") || arg.endsWith(File.separator)) {
                            arg = arg.replace("/", File.separator);
                            String newDirFilter = dirFilter + arg;
                            boolean exists =
                                new File(dir, newDirFilter).exists() || playerFolder && MainUtil
                                    .resolveRelative(
                                        new File(dir, actor.getUniqueId() + newDirFilter)).exists();
                            if (!exists) {
                                arg = arg.substring(0, arg.length() - File.separator.length());
                                if (arg.length() > 3 && arg.length() <= 16) {
                                    UUID fromName = Fawe.imp().getUUID(arg);
                                    if (fromName != null) {
                                        newDirFilter = dirFilter + fromName + File.separator;
                                        listGlobal = true;
                                    }
                                }
                            }
                            dirFilter = newDirFilter;
                        } else {
                            filters.add(arg);
                        }
                        break;
                }
            }
        }
        if (!listMine && !listGlobal) {
            listMine = true;
        }

        List<File> toFilter = new ArrayList<>();
        if (!filters.isEmpty()) {
            forEachFile = new DelegateConsumer<File>(forEachFile) {
                @Override
                public void accept(File file) {
                    toFilter.add(file);
                }
            };
        }

        if (formatName != null) {
            final ClipboardFormat cf = ClipboardFormats.findByAlias(formatName);
            forEachFile = new DelegateConsumer<File>(forEachFile) {
                @Override
                public void accept(File file) {
                    if (cf.isFormat(file)) {
                        super.accept(file);
                    }
                }
            };
        } else {
            forEachFile = new DelegateConsumer<File>(forEachFile) {
                @Override
                public void accept(File file) {
                    if (!file.toString().endsWith(".cached")) {
                        super.accept(file);
                    }
                }
            };
        }
        if (playerFolder) {
            if (listMine) {
                File playerDir = MainUtil.resolveRelative(new File(dir, actor.getUniqueId() + dirFilter));
                if (playerDir.exists()) {
                    allFiles(playerDir.listFiles(), false, forEachFile);
                }
            }
            if (listGlobal) {
                File rel = MainUtil.resolveRelative(new File(dir, dirFilter));
                forEachFile = new DelegateConsumer<File>(forEachFile) {
                    @Override
                    public void accept(File f) {
                        try {
                            if (f.isDirectory()) {
                                UUID.fromString(f.getName());
                                return;
                            }
                        } catch (IllegalArgumentException ignored) {
                        }
                        super.accept(f);
                    }
                };
                if (rel.exists()) {
                    allFiles(rel.listFiles(), false, forEachFile);
                }
            }
        } else {
            File rel = MainUtil.resolveRelative(new File(dir, dirFilter));
            if (rel.exists()) {
                allFiles(rel.listFiles(), false, forEachFile);
            }
        }
        if (!filters.isEmpty() && !toFilter.isEmpty()) {
            List<File> result = filter(toFilter, filters);
            for (File file : result) {
                rootFunction.accept(file);
            }
        }
    }

    private static List<File> filter(List<File> fileList, List<String> filters) {
        String[] normalizedNames = new String[fileList.size()];
        for (int i = 0; i < fileList.size(); i++) {
            String normalized = fileList.get(i).getName().toLowerCase(Locale.ROOT);
            if (normalized.startsWith("../")) {
                normalized = normalized.substring(3);
            }
            normalizedNames[i] = normalized.replace("/", File.separator);
        }

        for (String filter : filters) {
            if (fileList.isEmpty()) {
                return fileList;
            }
            String lowerFilter = filter.toLowerCase(Locale.ROOT).replace("/", File.separator);
            List<File> newList = new ArrayList<>();

            for (int i = 0; i < normalizedNames.length; i++) {
                if (normalizedNames[i].startsWith(lowerFilter)) {
                    newList.add(fileList.get(i));
                }
            }
            if (newList.isEmpty()) {
                for (int i = 0; i < normalizedNames.length; i++) {
                    if (normalizedNames[i].contains(lowerFilter)) {
                        newList.add(fileList.get(i));
                    }
                }

                if (newList.isEmpty()) {
                    String checkName = filter.replace("\\", "/").split("/")[0];
                    if (checkName.length() > 3 && checkName.length() <= 16) {
                        UUID fromName = Fawe.imp().getUUID(checkName);
                        if (fromName != null) {
                            lowerFilter = filter.replaceFirst(checkName, fromName.toString()).toLowerCase(Locale.ROOT);
                            for (int i = 0; i < normalizedNames.length; i++) {
                                if (normalizedNames[i].startsWith(lowerFilter)) {
                                    newList.add(fileList.get(i));
                                }
                            }
                        }
                    }
                }
            }
            fileList = newList;
        }
        return fileList;
    }

    public static void allFiles(File[] files, boolean recursive, Consumer<File> task) {
        if (files == null || files.length == 0) {
            return;
        }
        for (File f : files) {
            if (f.isDirectory()) {
                if (recursive) {
                    allFiles(f.listFiles(), recursive, task);
                } else {
                    task.accept(f);
                }
            } else {
                task.accept(f);
            }
        }
    }

    public static String getPath(File root, File file, UUID uuid) {
        File dir;
        if (uuid != null) {
            dir = new File(root, uuid.toString());
        } else {
            dir = root;
        }

        ClipboardFormat format = ClipboardFormats.findByFile(file);
        URI relative = dir.toURI().relativize(file.toURI());
        StringBuilder name = new StringBuilder();
        if (relative.isAbsolute()) {
            relative = root.toURI().relativize(file.toURI());
            name.append(".." + File.separator);
        }
        name.append(relative.getPath());
        return name.toString();
    }

}
