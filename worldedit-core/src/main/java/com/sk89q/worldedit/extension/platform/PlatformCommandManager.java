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

package com.sk89q.worldedit.extension.platform;

import com.boydti.fawe.Fawe;
import com.boydti.fawe.config.Caption;
import com.boydti.fawe.config.Settings;
import com.boydti.fawe.object.exception.FaweException;
import com.boydti.fawe.object.task.ThrowableSupplier;
import com.boydti.fawe.util.StringMan;
import com.boydti.fawe.util.TaskManager;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.reflect.TypeToken;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalConfiguration;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.MissingWorldException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.command.ApplyBrushCommands;
import com.sk89q.worldedit.command.BiomeCommands;
import com.sk89q.worldedit.command.BiomeCommandsRegistration;
import com.sk89q.worldedit.command.BrushCommands;
import com.sk89q.worldedit.command.BrushCommandsRegistration;
import com.sk89q.worldedit.command.ChunkCommands;
import com.sk89q.worldedit.command.ChunkCommandsRegistration;
import com.sk89q.worldedit.command.ClipboardCommands;
import com.sk89q.worldedit.command.ClipboardCommandsRegistration;
import com.sk89q.worldedit.command.ExpandCommands;
import com.sk89q.worldedit.command.GeneralCommands;
import com.sk89q.worldedit.command.GenerationCommands;
import com.sk89q.worldedit.command.GenerationCommandsRegistration;
import com.sk89q.worldedit.command.HistoryCommands;
import com.sk89q.worldedit.command.HistoryCommandsRegistration;
import com.sk89q.worldedit.command.HistorySubCommands;
import com.sk89q.worldedit.command.HistorySubCommandsRegistration;
import com.sk89q.worldedit.command.NavigationCommands;
import com.sk89q.worldedit.command.NavigationCommandsRegistration;
import com.sk89q.worldedit.command.PaintBrushCommands;
import com.sk89q.worldedit.command.RegionCommands;
import com.sk89q.worldedit.command.RegionCommandsRegistration;
import com.sk89q.worldedit.command.SchematicCommands;
import com.sk89q.worldedit.command.SchematicCommandsRegistration;
import com.sk89q.worldedit.command.ScriptingCommands;
import com.sk89q.worldedit.command.ScriptingCommandsRegistration;
import com.sk89q.worldedit.command.SelectionCommands;
import com.sk89q.worldedit.command.SelectionCommandsRegistration;
import com.sk89q.worldedit.command.SnapshotCommands;
import com.sk89q.worldedit.command.SnapshotCommandsRegistration;
import com.sk89q.worldedit.command.SnapshotUtilCommands;
import com.sk89q.worldedit.command.SnapshotUtilCommandsRegistration;
import com.sk89q.worldedit.command.SuperPickaxeCommands;
import com.sk89q.worldedit.command.SuperPickaxeCommandsRegistration;
import com.sk89q.worldedit.command.ToolCommands;
import com.sk89q.worldedit.command.ToolCommandsRegistration;
import com.sk89q.worldedit.command.ToolUtilCommands;
import com.sk89q.worldedit.command.ToolUtilCommandsRegistration;
import com.sk89q.worldedit.command.UtilityCommands;
import com.sk89q.worldedit.command.UtilityCommandsRegistration;
import com.sk89q.worldedit.command.WorldEditCommands;
import com.sk89q.worldedit.command.WorldEditCommandsRegistration;
import com.sk89q.worldedit.command.argument.Arguments;
import com.sk89q.worldedit.command.argument.BooleanConverter;
import com.sk89q.worldedit.command.argument.Chunk3dVectorConverter;
import com.sk89q.worldedit.command.argument.CommaSeparatedValuesConverter;
import com.sk89q.worldedit.command.argument.DirectionConverter;
import com.sk89q.worldedit.command.argument.DirectionVectorConverter;
import com.sk89q.worldedit.command.argument.EntityRemoverConverter;
import com.sk89q.worldedit.command.argument.EnumConverter;
import com.sk89q.worldedit.command.argument.ExpressionConverter;
import com.sk89q.worldedit.command.argument.FactoryConverter;
import com.sk89q.worldedit.command.argument.HeightConverter;
import com.sk89q.worldedit.command.argument.LocationConverter;
import com.sk89q.worldedit.command.argument.OffsetConverter;
import com.sk89q.worldedit.command.argument.RegionFactoryConverter;
import com.sk89q.worldedit.command.argument.RegistryConverter;
import com.sk89q.worldedit.command.argument.SideEffectConverter;
import com.sk89q.worldedit.command.argument.VectorConverter;
import com.sk89q.worldedit.command.argument.WorldConverter;
import com.sk89q.worldedit.command.argument.ZonedDateTimeConverter;
import com.sk89q.worldedit.command.util.PermissionCondition;
import com.sk89q.worldedit.command.util.PrintCommandHelp;
import com.sk89q.worldedit.command.util.SubCommandPermissionCondition;
import com.sk89q.worldedit.entity.Entity;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.event.Event;
import com.sk89q.worldedit.event.platform.CommandEvent;
import com.sk89q.worldedit.event.platform.CommandSuggestionEvent;
import com.sk89q.worldedit.extension.platform.binding.Bindings;
import com.sk89q.worldedit.extension.platform.binding.ConsumeBindings;
import com.sk89q.worldedit.extension.platform.binding.PrimitiveBindings;
import com.sk89q.worldedit.extension.platform.binding.ProvideBindings;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.internal.annotation.Selection;
import com.sk89q.worldedit.internal.command.CommandArgParser;
import com.sk89q.worldedit.internal.command.CommandLoggingHandler;
import com.sk89q.worldedit.internal.command.CommandRegistrationHandler;
import com.sk89q.worldedit.internal.command.ConfirmHandler;
import com.sk89q.worldedit.internal.command.MethodInjector;
import com.sk89q.worldedit.internal.command.exception.ExceptionConverter;
import com.sk89q.worldedit.internal.command.exception.WorldEditExceptionConverter;
import com.sk89q.worldedit.internal.util.LogManagerCompat;
import com.sk89q.worldedit.internal.util.Substring;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.SessionKey;
import com.sk89q.worldedit.session.request.Request;
import com.sk89q.worldedit.util.eventbus.Subscribe;
import com.sk89q.worldedit.util.formatting.text.Component;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.util.logging.DynamicStreamHandler;
import com.sk89q.worldedit.util.logging.LogFormat;
import com.sk89q.worldedit.world.World;
import org.apache.logging.log4j.Logger;
import org.enginehub.piston.Command;
import org.enginehub.piston.CommandManager;
import org.enginehub.piston.converter.ArgumentConverter;
import org.enginehub.piston.converter.ArgumentConverters;
import org.enginehub.piston.converter.ConversionResult;
import org.enginehub.piston.exception.CommandException;
import org.enginehub.piston.exception.CommandExecutionException;
import org.enginehub.piston.exception.ConditionFailedException;
import org.enginehub.piston.exception.UsageException;
import org.enginehub.piston.gen.CommandRegistration;
import org.enginehub.piston.impl.CommandManagerServiceImpl;
import org.enginehub.piston.inject.InjectedValueAccess;
import org.enginehub.piston.inject.InjectedValueStore;
import org.enginehub.piston.inject.Key;
import org.enginehub.piston.inject.MapBackedValueStore;
import org.enginehub.piston.inject.MemoizingValueAccess;
import org.enginehub.piston.inject.MergedValueAccess;
import org.enginehub.piston.part.SubCommandPart;
import org.enginehub.piston.suggestion.Suggestion;
import org.enginehub.piston.util.HelpGenerator;
import org.enginehub.piston.util.ValueProvider;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;


/**
 * Handles the registration and invocation of commands.
 *
 * <p>This class is primarily for internal usage.</p>
 */
public final class PlatformCommandManager {

    public static final Pattern COMMAND_CLEAN_PATTERN = Pattern.compile("^[/]+");
    private static final Logger LOGGER = LogManagerCompat.getLogger();
    private static final java.util.logging.Logger COMMAND_LOG =
        java.util.logging.Logger.getLogger("com.sk89q.worldedit.CommandLog");

    private final WorldEdit worldEdit;
    private final PlatformManager platformManager;
    private final CommandManagerServiceImpl commandManagerService;
    private final CommandManager commandManager;
    private final InjectedValueStore globalInjectedValues;
    private final DynamicStreamHandler dynamicHandler = new DynamicStreamHandler();
    private final WorldEditExceptionConverter exceptionConverter;
    public final CommandRegistrationHandler registration;

    private static PlatformCommandManager INSTANCE;

    /**
     * Create a new instance.
     *
     * @param worldEdit the WorldEdit instance
     */
    public PlatformCommandManager(final WorldEdit worldEdit, PlatformManager platformManager) {
        checkNotNull(worldEdit);
        checkNotNull(platformManager);
        INSTANCE = this;

        this.worldEdit = worldEdit;
        this.platformManager = platformManager;
        this.exceptionConverter = new WorldEditExceptionConverter(worldEdit);
        this.commandManagerService = new CommandManagerServiceImpl();
        this.commandManager = commandManagerService.newCommandManager();
        this.globalInjectedValues = MapBackedValueStore.create();
        this.registration = new CommandRegistrationHandler(
            ImmutableList.of(
                new CommandLoggingHandler(worldEdit, COMMAND_LOG),
                new MethodInjector(),
                new ConfirmHandler()

            ));
        // setup separate from main constructor
        // ensures that everything is definitely assigned
        initialize();
    }

    private void initialize() {
        // Register this instance for command events
        worldEdit.getEventBus().register(this);

        // Setup the logger
        COMMAND_LOG.addHandler(dynamicHandler);

        // Set up the commands manager
        registerAlwaysInjectedValues();
        registerArgumentConverters();
        registerAllCommands();
    }

    private void registerArgumentConverters() {
        DirectionVectorConverter.register(worldEdit, commandManager);
        DirectionConverter.register(worldEdit, commandManager);
        FactoryConverter.register(worldEdit, commandManager);
        for (int count = 2; count <= 3; count++) {
            commandManager.registerConverter(Key.of(double.class, Annotations.radii(count)),
                CommaSeparatedValuesConverter.wrapAndLimit(ArgumentConverters.get(
                    TypeToken.of(double.class)
                ), count)
            );
        }
        VectorConverter.register(commandManager);
        Chunk3dVectorConverter.register(commandManager);
        EnumConverter.register(commandManager);
        RegistryConverter.register(commandManager);
        ZonedDateTimeConverter.register(commandManager);
        BooleanConverter.register(commandManager);
        EntityRemoverConverter.register(commandManager);
        RegionFactoryConverter.register(commandManager);
        WorldConverter.register(commandManager);
        LocationConverter.register(commandManager);
        ExpressionConverter.register(commandManager);
        SideEffectConverter.register(commandManager);
        HeightConverter.register(commandManager);
        OffsetConverter.register(worldEdit, commandManager);
        commandManager.registerConverter(Key.of(com.sk89q.worldedit.function.pattern.Pattern.class, Annotations.patternList()),
            CommaSeparatedValuesConverter.wrap(commandManager.getConverter(Key.of(
                com.sk89q.worldedit.function.pattern.Pattern.class)).get()));

        registerBindings(new ConsumeBindings(worldEdit, this));
        registerBindings(new PrimitiveBindings(worldEdit));
        registerBindings(new ProvideBindings(worldEdit));
    }

    private void registerBindings(Bindings bindings) {
        bindings.register(globalInjectedValues, commandManager);
    }

    private void registerAlwaysInjectedValues() {
        globalInjectedValues.injectValue(Key.of(Region.class, Selection.class),
                context -> {
                    LocalSession localSession = context.injectedValue(Key.of(LocalSession.class))
                            .orElseThrow(() -> new IllegalStateException("No LocalSession"));
                    return context.injectedValue(Key.of(World.class))
                            .map(world -> {
                                try {
                                    return localSession.getSelection(world);
                                } catch (IncompleteRegionException e) {
                                    exceptionConverter.convert(e);
                                    throw new AssertionError("Should have thrown a new exception.", e);
                                }
                            });
                });
        /*
        globalInjectedValues.injectValue(Key.of(EditSession.class),
                context -> {
                    LocalSession localSession = context.injectedValue(Key.of(LocalSession.class))
                            .orElseThrow(() -> new IllegalStateException("No LocalSession"));
                    return context.injectedValue(Key.of(Actor.class))
                            .map(actor -> {
                                EditSession editSession = localSession.createEditSession(actor);
                                editSession.enableStandardMode();
                                Request.request().setEditSession(editSession);
                                return editSession;
                            });
                });
        */
        // TODO: Ping @MattBDev to reimplement 2020-02-04
//        globalInjectedValues.injectValue(Key.of(CFICommands.CFISettings.class),
//                context -> context.injectedValue(Key.of(Actor.class))
//                        .orElseThrow(() -> new IllegalStateException("No CFI Settings")).getMeta("CFISettings"));
        globalInjectedValues.injectValue(Key.of(World.class),
                context -> {
                    LocalSession localSession = context.injectedValue(Key.of(LocalSession.class))
                            .orElseThrow(() -> new IllegalStateException("No LocalSession"));
                    return context.injectedValue(Key.of(Actor.class))
                            .map(actor -> {
                                try {
                                    if (localSession.hasWorldOverride()) {
                                        return localSession.getWorldOverride();
                                    } else if (actor instanceof Locatable && ((Locatable) actor).getExtent() instanceof World) {
                                        return (World) ((Locatable) actor).getExtent();
                                    } else {
                                        throw new MissingWorldException();
                                    }
                                } catch (MissingWorldException e) {
                                    exceptionConverter.convert(e);
                                    throw new AssertionError("Should have thrown a new exception.", e);
                                }
                            });
                });
        globalInjectedValues.injectValue(Key.of(InjectedValueAccess.class), Optional::of);
    }

    private <CI> void registerSubCommands(String name, List<String> aliases, String desc,
                                      CommandRegistration<CI> registration, CI instance) {
        registerSubCommands(name, aliases, desc, registration, instance, m -> {});
    }

    private <CI> void registerSubCommands(String name, List<String> aliases, String desc,
                                          CommandRegistration<CI> registration, CI instance,
                                          Consumer<CommandManager> additionalConfig) {
        commandManager.register(name, cmd -> {
            cmd.aliases(aliases);
            cmd.description(TextComponent.of(desc));
            cmd.action(Command.Action.NULL_ACTION);

            CommandManager manager = commandManagerService.newCommandManager();
            this.registration.register(
                manager,
                registration,
                instance
            );
            additionalConfig.accept(manager);

            final List<Command> subCommands = manager.getAllCommands().collect(Collectors.toList());
            cmd.addPart(SubCommandPart.builder(Caption.of("worldedit.argument.action"),
                TextComponent.of("Sub-command to run."))
                .withCommands(subCommands)
                .required()
                .build());

            cmd.condition(new SubCommandPermissionCondition.Generator(subCommands).build());
        });
    }

    private <CI> void registerSubCommands(String name, List<String> aliases, String desc,
                                          Consumer<BiConsumer<CommandRegistration, CI>> handlerInstance,
                                          @NotNull Consumer<CommandManager> additionalConfig) {
        commandManager.register(name, cmd -> {
            cmd.aliases(aliases);
            cmd.description(TextComponent.of(desc));
            cmd.action(Command.Action.NULL_ACTION);

            CommandManager manager = commandManagerService.newCommandManager();

            handlerInstance.accept((handler, instance) -> {
                this.registration.register(
                        manager,
                        handler,
                        instance
                );
            });
            additionalConfig.accept(manager);

            final List<Command> subCommands = manager.getAllCommands().collect(Collectors.toList());
            cmd.addPart(SubCommandPart.builder(Caption.of("worldedit.argument.action"),
                    TextComponent.of("Sub-command to run."))
                    .withCommands(subCommands)
                    .required()
                    .build());

            cmd.condition(new SubCommandPermissionCondition.Generator(subCommands).build());
        });
    }

    public void registerAllCommands() {
        if (Settings.IMP.ENABLED_COMPONENTS.COMMANDS) {
            // TODO: Ping @MattBDev to reimplement (or remove) 2020-02-04
//            registerSubCommands(
//                "patterns",
//                ImmutableList.of(),
//                "Patterns determine what blocks are placed",
//                PatternCommandsRegistration.builder(),
//                new PatternCommands()
//            );
//            registerSubCommands(
//                "masks",
//                ImmutableList.of(),
//                "Masks determine which blocks are placed",
//                MaskCommandsRegistration.builder(),
//                new MaskCommands(worldEdit)
//            );
//            registerSubCommands(
//                "transforms",
//                ImmutableList.of(),
//                "Transforms modify how a block is placed",
//                TransformCommandsRegistration.builder(),
//                new TransformCommands()
//            );
            registerSubCommands(
                "schematic",
                ImmutableList.of("schem", "/schematic", "/schem"),
                "Schematic commands for saving/loading areas",
                SchematicCommandsRegistration.builder(),
                new SchematicCommands(worldEdit)
            );
            registerSubCommands(
                "snapshot",
                ImmutableList.of("snap"),
                "Snapshot commands for restoring backups",
                SnapshotCommandsRegistration.builder(),
                new SnapshotCommands(worldEdit)
            );
            registerSubCommands(
                "superpickaxe",
                ImmutableList.of("pickaxe", "sp"),
                "Super-pickaxe commands",
                SuperPickaxeCommandsRegistration.builder(),
                new SuperPickaxeCommands(worldEdit)
            );
            registerSubCommands(
                    "brush",
                    Lists.newArrayList("br", "/brush", "/br", "/tool", "tool"),
                    "Brushing commands",
                    c -> {
                        c.accept(BrushCommandsRegistration.builder(), new BrushCommands(worldEdit));
                        c.accept(ToolCommandsRegistration.builder(), new ToolCommands(worldEdit));
                        c.accept(ToolUtilCommandsRegistration.builder(), new ToolUtilCommands(worldEdit));
                    },
                    manager -> {
                        PaintBrushCommands.register(commandManagerService, manager, registration);
                        ApplyBrushCommands.register(commandManagerService, manager, registration);
                    }
            );
            registerSubCommands(
                "worldedit",
                ImmutableList.of("we", "fawe", "fastasyncworldedit"),
                "WorldEdit commands",
                WorldEditCommandsRegistration.builder(),
                new WorldEditCommands(worldEdit)
            );
            /*
            TODO: Ping @MattBDev to reimplement 2020-02-04
            registerSubCommands(
                "cfi",
                ImmutableList.of("/cfi"),
                "CFI commands",
                CFICommandsRegistration.builder(),
                new CFICommands(worldEdit)
            );
            registerSubCommands(
                "/anvil",
                ImmutableList.of(),
                "Manipulate billions of blocks",
                AnvilCommandsRegistration.builder(),
                new AnvilCommands(worldEdit)
            );
             */
            this.registration.register(
                commandManager,
                BiomeCommandsRegistration.builder(),
                new BiomeCommands()
            );
            this.registration.register(
                commandManager,
                ChunkCommandsRegistration.builder(),
                new ChunkCommands(worldEdit)
            );
            this.registration.register(
                commandManager,
                ClipboardCommandsRegistration.builder(),
                new ClipboardCommands()
            );
            GeneralCommands.register(
                    registration,
                    commandManager,
                    commandManagerService,
                    worldEdit
            );
            this.registration.register(
                    commandManager,
                    GenerationCommandsRegistration.builder(),
                    new GenerationCommands(worldEdit)
            );
            HistoryCommands history = new HistoryCommands(worldEdit);
            this.registration.register(
                    commandManager,
                    HistoryCommandsRegistration.builder(),
                    history
            );
            registerSubCommands(
                "/history",
                ImmutableList.of("/frb"),
                "Manage your history",
                HistorySubCommandsRegistration.builder(),
                new HistorySubCommands(history)
            );
            this.registration.register(
                commandManager,
                NavigationCommandsRegistration.builder(),
                new NavigationCommands(worldEdit)
            );
            this.registration.register(
                commandManager,
                RegionCommandsRegistration.builder(),
                new RegionCommands()
            );
            this.registration.register(
                commandManager,
                ScriptingCommandsRegistration.builder(),
                new ScriptingCommands(worldEdit)
            );
            this.registration.register(
                commandManager,
                SelectionCommandsRegistration.builder(),
                new SelectionCommands(worldEdit)
            );
            ExpandCommands.register(registration, commandManager, commandManagerService);
            this.registration.register(
                commandManager,
                SnapshotUtilCommandsRegistration.builder(),
                new SnapshotUtilCommands(worldEdit)
            );
            this.registration.register(
                commandManager,
                ToolCommandsRegistration.builder(),
                new ToolCommands(worldEdit)
            );
            this.registration.register(
                    commandManager,
                    ToolUtilCommandsRegistration.builder(),
                    new ToolUtilCommands(worldEdit)
            );
            this.registration.register(
                commandManager,
                UtilityCommandsRegistration.builder(),
                new UtilityCommands(worldEdit)
            );
        }
    }

    public static PlatformCommandManager getInstance() {
        return INSTANCE;
    }

    public ExceptionConverter getExceptionConverter() {
        return exceptionConverter;
    }

    void registerCommandsWith(Platform platform) {
        LOGGER.info("Registering commands with " + platform.getClass().getCanonicalName());


        LocalConfiguration config = platform.getConfiguration();
        boolean logging = config.logCommands;
        String path = config.logFile;

        // Register log
        if (!logging || path.isEmpty()) {
            dynamicHandler.setHandler(null);
            COMMAND_LOG.setLevel(Level.OFF);
        } else {
            File file = new File(config.getWorkingDirectory(), path);
            COMMAND_LOG.setLevel(Level.ALL);

            LOGGER.info("Logging WorldEdit commands to " + file.getAbsolutePath());

            try {
                dynamicHandler.setHandler(new FileHandler(file.getAbsolutePath(), true));
            } catch (IOException e) {
                LOGGER.warn("Could not use command log file " + path + ": " + e.getMessage());
            }

            dynamicHandler.setFormatter(new LogFormat(config.logFormat));
        }

        platform.registerCommands(commandManager);
    }

    void removeCommands() {
        dynamicHandler.setHandler(null);
    }

    private Stream<Substring> parseArgs(String input) {
        return CommandArgParser.forArgString(input).parseArgs();
    }

    public int parseCommand(String args, Actor actor) {
        InjectedValueAccess context;
        if (actor == null) {
            context = globalInjectedValues;
        } else {
            context = initializeInjectedValues(args::toString, actor, null, false);
        }
        return parseCommand(args, context);
    }

    public <T> T parseConverter(String args, InjectedValueAccess access, Class<T> clazz) {
        ArgumentConverter<T> converter = commandManager.getConverter(Key.of(clazz)).orElse(null);
        if (converter != null) {
            ConversionResult<T> result = converter.convert(args, access);
            Collection<T> values = result.orElse(Collections.emptyList());
            if (!values.isEmpty()) {
                return values.iterator().next();
            }
        }
        return null;
    }

    public int parseCommand(String args, InjectedValueAccess access) {
        if (args.isEmpty()) {
            return 0;
        }
        String[] split = parseArgs(args)
                .map(Substring::getSubstring)
                .toArray(String[]::new);
        return commandManager.execute(access, ImmutableList.copyOf(split));
    }

    @Subscribe
    public void handleCommand(CommandEvent event) {
        Request.reset();
        Actor actor = event.getActor();
        String args = event.getArguments();
        TaskManager.IMP.taskNow(() -> {
            if (!Fawe.isMainThread()) {
                Thread.currentThread().setName("FAWE Thread for player: " + actor.getName());
            }
            int space0 = args.indexOf(' ');
            String arg0 = space0 == -1 ? args : args.substring(0, space0);
            Optional<Command> optional = commandManager.getCommand(arg0);
            if (!optional.isPresent()) {
                return;
            }
            Command cmd = optional.get();
            PermissionCondition queued = cmd.getCondition().as(PermissionCondition.class).orElse(null);
            if (queued != null && !queued.isQueued()) {
                handleCommandOnCurrentThread(event);
                return;
            } else {
                actor.decline();
            }
            actor.runAction(() -> {
                SessionKey key = actor.getSessionKey();
                if (key.isActive()) {
                    PlatformCommandManager.this.handleCommandOnCurrentThread(event);
                }
            }, false, true);
        }, Fawe.isMainThread());
    }

    public void handleCommandOnCurrentThread(CommandEvent event) {
        Actor actor = platformManager.createProxyActor(event.getActor());
        String[] split = parseArgs(event.getArguments())
            .map(Substring::getSubstring)
            .toArray(String[]::new);

        // No command found!
        if (!commandManager.containsCommand(split[0])) {
            return;
        }

        LocalSession session = worldEdit.getSessionManager().get(actor);
        Request.request().setSession(session);
        if (actor instanceof Entity) {
            Extent extent = ((Entity) actor).getExtent();
            if (extent instanceof World) {
                Request.request().setWorld(((World) extent));
            }
        }

        MemoizingValueAccess context = initializeInjectedValues(event::getArguments, actor, event, false);

        ThrowableSupplier<Throwable> task = () -> commandManager.execute(context, ImmutableList.copyOf(split));

        handleCommandTask(task, context, session, event);
    }

    public void handleCommandTask(ThrowableSupplier<Throwable> task, InjectedValueAccess context, @Nullable LocalSession session, CommandEvent event) {
        Actor actor = context.injectedValue(Key.of(Actor.class)).orElseThrow(() -> new IllegalStateException("No player"));

        long start = System.currentTimeMillis();

        try {
            // This is a bit of a hack, since the call method can only throw CommandExceptions
            // everything needs to be wrapped at least once. Which means to handle all WorldEdit
            // exceptions without writing a hook into every dispatcher, we need to unwrap these
            // exceptions and rethrow their converted form, if their is one.
            try {
                Object result = task.get();
            } catch (Throwable t) {
                // Use the exception converter to convert the exception if any of its causes
                // can be converted, otherwise throw the original exception
                Throwable next = t;
                do {
                    exceptionConverter.convert(next);
                    next = next.getCause();
                } while (next != null);
                throw t;
            }
        } catch (ConditionFailedException e) {
            if (e.getCondition() instanceof PermissionCondition) {
                actor.printError(Caption.of("fawe.error.no-perm", StringMan.getString(((PermissionCondition) e.getCondition()).getPermissions())));
            } else {
                actor.print(e.getRichMessage());
            }
        } catch (FaweException e) {
            actor.print(Caption.of("fawe.cancel.worldedit.cancel.reason", e.getComponent()));
        } catch (UsageException e) {
            ImmutableList<Command> cmd = e.getCommands();
            if (!cmd.isEmpty()) {
                actor.print(Caption.of("fawe.error.command.syntax", HelpGenerator.create(e.getCommandParseResult()).getFullHelp()));
            }
            actor.printError(e.getRichMessage());
        } catch (CommandExecutionException e) {
            if (e.getCause() instanceof FaweException) {
                actor.print(Caption.of("fawe.cancel.worldedit.cancel.reason", ((FaweException)e.getCause()).getComponent()));
            } else {
                handleUnknownException(actor, e.getCause());
            }
        } catch (CommandException e) {
            Component msg = e.getRichMessage();
            if (msg != TextComponent.empty()) {
                actor.print(TextComponent.builder("")
                        .append(e.getRichMessage())
                        .build());
                List<String> argList = parseArgs(event.getArguments()).map(Substring::getSubstring).collect(Collectors.toList());
                printUsage(actor, argList);
            }
        } catch (Throwable t) {
            handleUnknownException(actor, t);
        } finally {
            if (context instanceof MemoizingValueAccess) {
                context = ((MemoizingValueAccess) context).snapshotMemory();
            }
            Optional<EditSession> editSessionOpt = context.injectedValue(Key.of(EditSession.class));

            if (editSessionOpt.isPresent()) {
                EditSession editSession = editSessionOpt.get();
                editSession.flushQueue();
                session.remember(editSession);
                editSession.close();

                long time = System.currentTimeMillis() - start;
                double timeS = (time / 1000.0);
                int changed = editSession.getBlockChangeCount();
                double throughput = timeS == 0 ? changed : changed / timeS;
                if (time > 1000) {
                    actor.print(Caption.of(
                            "worldedit.command.time-elapsed",
                            TextComponent.of(timeS),
                            TextComponent.of(changed),
                            TextComponent.of(Math.round(throughput))
                    ));
                }

                worldEdit.flushBlockBag(actor, editSession);
            }
            // TODO: Ping @MattBDev to reimplement 2020-02-04
//            CFICommands.CFISettings cfi = actor.getMeta("CFISettings");
//            if (cfi != null) {
//                HeightMapMCAGenerator gen = cfi.getGenerator();
//                if (gen != null && gen.isModified()) {
//                    try {
//                        gen.update();
//                        CFIChangeSet set = new CFIChangeSet(gen, actor.getUniqueId());
//                        session.remember(actor, gen, set, actor.getLimit());
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                }
//            }
            Request.reset();
        }

        event.setCancelled(true);
    }

    private void printUsage(Actor actor, List<String> arguments) {
        PrintCommandHelp.help(arguments, 0, false,
                getCommandManager(), actor, "//help");
    }

    private MemoizingValueAccess initializeInjectedValues(Arguments arguments, Actor actor, Event event, boolean isSuggestions) {
        InjectedValueStore store = MapBackedValueStore.create();
        store.injectValue(Key.of(Actor.class), ValueProvider.constant(actor));
        if (actor instanceof Player) {
            store.injectValue(Key.of(Player.class), ValueProvider.constant((Player) actor));
        } else {
            store.injectValue(Key.of(Player.class), context -> {
                throw new CommandException(Caption.of("worldedit.command.player-only"), ImmutableList.of());
            });
        }
        store.injectValue(Key.of(Arguments.class), ValueProvider.constant(arguments));
        store.injectValue(Key.of(LocalSession.class),
            context -> {
                LocalSession localSession = worldEdit.getSessionManager().get(actor);
                localSession.tellVersion(actor);
                return Optional.of(localSession);
            });
        store.injectValue(Key.of(boolean.class), context -> Optional.of(isSuggestions));
        store.injectValue(Key.of(InjectedValueStore.class), ValueProvider.constant(store));
        store.injectValue(Key.of(Event.class), ValueProvider.constant(event));
        return MemoizingValueAccess.wrap(
                MergedValueAccess.of(store, globalInjectedValues)
        );
    }

    private void handleUnknownException(Actor actor, Throwable t) {
        actor.print(Caption.of("worldedit.command.error.report"));
        actor.print(TextComponent.of(t.getClass().getName() + ": " + t.getMessage()));
        LOGGER.error("An unexpected error while handling a WorldEdit command", t);
    }

    @Subscribe
    public void handleCommandSuggestion(CommandSuggestionEvent event) {
        try {
            String rawArgs = event.getArguments();
            // Increase the resulting positions by 1 if we remove a leading `/`
            final int posOffset = rawArgs.startsWith("/") ? 1 : 0;
            String arguments = rawArgs.startsWith("/") ? rawArgs.substring(1) : rawArgs;
            List<Substring> split = parseArgs(arguments).collect(Collectors.toList());
            List<String> argStrings = split.stream()
                .map(Substring::getSubstring)
                .collect(Collectors.toList());
            MemoizingValueAccess access = initializeInjectedValues(() -> arguments, event.getActor(), event, true);
            ImmutableSet<Suggestion> suggestions;
            try {
                suggestions = commandManager.getSuggestions(access, argStrings);
            } catch (Throwable t) { // catch errors which are *not* command exceptions generated by parsers/suggesters
                if (!(t instanceof CommandException)) {
                    LOGGER.debug("Unexpected error occurred while generating suggestions for input: " + arguments, t);
                    return;
                }
                throw t;
            }

            event.setSuggestions(suggestions.stream()
                .map(suggestion -> {
                    int noSlashLength = arguments.length();
                    Substring original = suggestion.getReplacedArgument() == split.size()
                        ? Substring.from(arguments, noSlashLength, noSlashLength)
                        : split.get(suggestion.getReplacedArgument());
                    return Substring.wrap(
                        suggestion.getSuggestion(),
                        original.getStart() + posOffset,
                        original.getEnd() + posOffset
                    );
                }).collect(Collectors.toList()));
        } catch (ConditionFailedException e) {
            if (e.getCondition() instanceof PermissionCondition) {
                event.setSuggestions(new ArrayList<>());
            }
        }
    }

    /**
     * Get the command manager instance.
     *
     * @return the command manager
     */
    public CommandManager getCommandManager() {
        return commandManager;
    }

}
