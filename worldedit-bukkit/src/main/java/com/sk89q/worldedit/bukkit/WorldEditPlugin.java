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

package com.sk89q.worldedit.bukkit;

import com.boydti.fawe.Fawe;
import com.boydti.fawe.bukkit.FaweBukkit;
import com.google.common.base.Joiner;
import com.sk89q.util.yaml.YAMLProcessor;
import com.sk89q.wepif.PermissionsResolverManager;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.adapter.AdapterLoadException;
import com.sk89q.worldedit.bukkit.adapter.BukkitImplAdapter;
import com.sk89q.worldedit.bukkit.adapter.BukkitImplLoader;
import com.sk89q.worldedit.bukkit.adapter.impl.FAWE_Spigot_v1_15_R2;
import com.sk89q.worldedit.bukkit.adapter.impl.FAWE_Spigot_v1_16_R1;
import com.sk89q.worldedit.bukkit.adapter.impl.FAWE_Spigot_v1_16_R2;
import com.sk89q.worldedit.bukkit.adapter.impl.FAWE_Spigot_v1_16_R3;
import com.sk89q.worldedit.event.platform.CommandEvent;
import com.sk89q.worldedit.event.platform.CommandSuggestionEvent;
import com.sk89q.worldedit.event.platform.PlatformReadyEvent;
import com.sk89q.worldedit.event.platform.PlatformUnreadyEvent;
import com.sk89q.worldedit.event.platform.PlatformsRegisteredEvent;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extension.platform.Capability;
import com.sk89q.worldedit.extension.platform.Platform;
import com.sk89q.worldedit.extent.inventory.BlockBag;
import com.sk89q.worldedit.internal.anvil.ChunkDeleter;
import com.sk89q.worldedit.internal.command.CommandUtil;
import com.sk89q.worldedit.internal.util.LogManagerCompat;
import com.sk89q.worldedit.util.lifecycle.Lifecycled;
import com.sk89q.worldedit.util.lifecycle.SimpleLifecycled;
import com.sk89q.worldedit.world.biome.BiomeType;
import com.sk89q.worldedit.world.block.BlockCategory;
import com.sk89q.worldedit.world.entity.EntityType;
import com.sk89q.worldedit.world.gamemode.GameModes;
import com.sk89q.worldedit.world.item.ItemCategory;
import com.sk89q.worldedit.world.weather.WeatherTypes;
import io.papermc.lib.PaperLib;
import org.apache.logging.log4j.Logger;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Biome;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.incendo.serverlib.ServerLib;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.sk89q.worldedit.internal.anvil.ChunkDeleter.DELCHUNKS_FILE_NAME;

/**
 * Plugin for Bukkit.
 */
public class WorldEditPlugin extends JavaPlugin { //implements TabCompleter

    private static final Logger LOGGER = LogManagerCompat.getLogger();
    public static final String CUI_PLUGIN_CHANNEL = "worldedit:cui";
    private static WorldEditPlugin INSTANCE;
    private static final int BSTATS_ID = 1403;

    private final SimpleLifecycled<BukkitImplAdapter> adapter =
            SimpleLifecycled.invalid();
    private BukkitServerInterface platform;
    private BukkitConfiguration config;
    private BukkitPermissionAttachmentManager permissionAttachmentManager;

    @Override
    public void onLoad() {

        // FAWE start
        // This is already covered by Spigot, however, a more pesky warning with a proper explanation over "Ambiguous plugin name..." can't hurt.
        Plugin[] plugins = Bukkit.getServer().getPluginManager().getPlugins();
        for (Plugin p : plugins) {
            if (p.getName().equals("WorldEdit")) {
                LOGGER.warn("You installed WorldEdit alongside FastAsyncWorldEdit. That is unneeded and will cause unforeseen issues, " +
                        "because FastAsyncWorldEdit already provides WorldEdit. " +
                        "Stop your server and delete the 'worldedit-bukkit' jar from your plugins folder.");
            }
        }
        // FAWE end

        INSTANCE = this;

        //noinspection ResultOfMethodCallIgnored
        getDataFolder().mkdirs();

        WorldEdit worldEdit = WorldEdit.getInstance();

        // Setup platform
        platform = new BukkitServerInterface(this, getServer());
        worldEdit.getPlatformManager().register(platform);

        createDefaultConfiguration("config-legacy.yml"); // Create the default configuration file for WorldEdit, for it's config-legacy.yml

        config = new BukkitConfiguration(new YAMLProcessor(new File(getDataFolder(), "config-legacy.yml"), true), this);
        
        permissionAttachmentManager = new BukkitPermissionAttachmentManager(this);

        Path delChunks = Paths.get(getDataFolder().getPath(), DELCHUNKS_FILE_NAME);
        if (Files.exists(delChunks)) {
            ChunkDeleter.runFromFile(delChunks, true);
        }

        if (this.getDataFolder().getParentFile().listFiles(file -> {
            if (file.getName().equals("DummyFawe.jar")) {
                file.delete();
                return true;
            }
            return false;
        }).length > 0) {
            LOGGER.warn("DummyFawe detected and automatically deleted! This file is no longer necessary.");
        }
    }

    /**
     * Called on plugin enable.
     */
    @Override
    public void onEnable() {

        new FaweBukkit(this);

        WorldEdit.getInstance().getEventBus().post(new PlatformsRegisteredEvent());

        PermissionsResolverManager.initialize(this); // Setup permission resolver

        // Register CUI
        fail(() -> {
            getServer().getMessenger().registerIncomingPluginChannel(this, CUI_PLUGIN_CHANNEL, new CUIChannelListener(this));
            getServer().getMessenger().registerOutgoingPluginChannel(this, CUI_PLUGIN_CHANNEL);
        }, "Failed to register CUI");

        // Now we can register events
        getServer().getPluginManager().registerEvents(new WorldEditListener(this), this);
        // register async tab complete, if available
        if (PaperLib.isPaper()) {
            getServer().getPluginManager().registerEvents(new AsyncTabCompleteListener(), this);
        }

        initializeRegistries(); // this creates the objects matching Bukkit's enums - but doesn't fill them with data yet
        if (Bukkit.getWorlds().isEmpty()) {
            setupPreWorldData();
            // register this so we can load world-dependent data right as the first world is loading
            getServer().getPluginManager().registerEvents(new WorldInitListener(), this);
        } else {
            LOGGER.warn("Server reload detected. This may cause various issues with FastAsyncWorldEdit and dependent plugins. Reloading the server is not advised.");
            LOGGER.warn("For more information why reloading is bad, see https://madelinemiller.dev/blog/problem-with-reload/");
            try {
                setupPreWorldData();
                // since worlds are loaded already, we can do this now
                setupWorldData();
            } catch (Throwable ignored) {
            }
        }

        // Setup metrics
        new Metrics(this, BSTATS_ID);

        // Check whether the server runs on 11 or greater
        ServerLib.checkJavaLTS();
        // Check if we are in a safe environment
        ServerLib.checkUnsafeForks();
    }

    private void setupPreWorldData() {
        loadAdapter();
        config.load();
        WorldEdit.getInstance().loadMappings();
    }

    private void setupWorldData() {
        // datapacks aren't loaded until just before the world is, and bukkit has no event for this
        // so the earliest we can do this is in WorldInit
        setupTags();
        WorldEdit.getInstance().getEventBus().post(new PlatformReadyEvent(platform));
    }

    @SuppressWarnings({ "deprecation", "unchecked" })
    private void initializeRegistries() {
        // Biome
        for (Biome biome : Biome.values()) {
            String lowerCaseBiomeName = biome.name().toLowerCase(Locale.ROOT);
            BiomeType.REGISTRY.register("minecraft:" + lowerCaseBiomeName, new BiomeType("minecraft:" + lowerCaseBiomeName));
        }
        /*

        // Block & Item
        for (Material material : Material.values()) {
            if (material.isBlock() && !material.isLegacy()) {
                BlockType.REGISTRY.register(material.getKey().toString(), new BlockType(material.getKey().toString(), blockState -> {
                    // TODO Use something way less hacky than this.
                    ParserContext context = new ParserContext();
                    context.setPreferringWildcard(true);
                    context.setTryLegacy(false);
                    context.setRestricted(false);
                    try {
                        FuzzyBlockState state = (FuzzyBlockState) WorldEdit.getInstance().getBlockFactory().parseFromInput(
                                BukkitAdapter.adapt(blockState.getBlockType()).createBlockData().getAsString(), context
                        ).toImmutableState();
                        BlockState defaultState = blockState.getBlockType().getAllStates().get(0);
                        for (Map.Entry<Property<?>, Object> propertyObjectEntry : state.getStates().entrySet()) {
                            //noinspection unchecked
                            defaultState = defaultState.with((Property<Object>) propertyObjectEntry.getKey(), propertyObjectEntry.getValue());
                        }
                        return defaultState;
                    } catch (InputParseException e) {
                        LOGGER.warn("Error loading block state for " + material.getKey(), e);
                        return blockState;
                    }
                }));
            }
            if (material.isItem() && !material.isLegacy()) {
                ItemType.REGISTRY.register(material.getKey().toString(), new ItemType(material.getKey().toString()));
            }
        }
*/

        // Entity
        for (org.bukkit.entity.EntityType entityType : org.bukkit.entity.EntityType.values()) {
            String mcid = entityType.getName();
            if (mcid != null) {
                String lowerCaseMcId = mcid.toLowerCase(Locale.ROOT);
                EntityType.REGISTRY.register("minecraft:" + lowerCaseMcId, new EntityType("minecraft:" + lowerCaseMcId));
            }
        }
        // ... :|
        GameModes.get("");
        WeatherTypes.get("");
    }

    private void setupTags() {
        // Tags
        try {
            for (Tag<Material> blockTag : Bukkit.getTags(Tag.REGISTRY_BLOCKS, Material.class)) {
                BlockCategory.REGISTRY.register(blockTag.getKey().toString(), new BlockCategory(blockTag.getKey().toString()));
            }
            for (Tag<Material> itemTag : Bukkit.getTags(Tag.REGISTRY_ITEMS, Material.class)) {
                ItemCategory.REGISTRY.register(itemTag.getKey().toString(), new ItemCategory(itemTag.getKey().toString()));
            }
        } catch (NoSuchMethodError ignored) {
            LOGGER.warn("The version of Spigot/Paper you are using doesn't support Tags. The usage of tags with WorldEdit will not work until you update.");
        }
    }

    private void fail(Runnable run, String message) {
        try {
            run.run();
        } catch (Throwable e) {
            getLogger().severe(message);
            e.printStackTrace();
        }
    }

    private void loadAdapter() {
        WorldEdit worldEdit = WorldEdit.getInstance();

        // Attempt to load a Bukkit adapter
        BukkitImplLoader adapterLoader = new BukkitImplLoader();
        try {
            adapterLoader.addClass(FAWE_Spigot_v1_15_R2.class);
            adapterLoader.addClass(FAWE_Spigot_v1_16_R1.class);
            adapterLoader.addClass(FAWE_Spigot_v1_16_R2.class);
            adapterLoader.addClass(FAWE_Spigot_v1_16_R3.class);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }

        try {
            adapterLoader.addFromPath(getClass().getClassLoader());
        } catch (IOException e) {
            LOGGER.warn("Failed to search path for Bukkit adapters");
        }

        try {
            adapterLoader.addFromJar(getFile());
        } catch (IOException e) {
            LOGGER.warn("Failed to search " + getFile() + " for Bukkit adapters", e);
        }
        try {
            BukkitImplAdapter bukkitAdapter = adapterLoader.loadAdapter();
            LOGGER.info("Using " + bukkitAdapter.getClass().getCanonicalName() + " as the Bukkit adapter");
            this.adapter.newValue(bukkitAdapter);
        } catch (AdapterLoadException e) {
            Platform platform = worldEdit.getPlatformManager().queryCapability(Capability.WORLD_EDITING);
            if (platform instanceof BukkitServerInterface) {
                LOGGER.warn(e.getMessage());
            } else {
                LOGGER.info("FastAsyncWorldEdit could not find a Bukkit adapter for this MC version, "
                             + "but it seems that you have another implementation of FastAsyncWorldEdit installed (" + platform.getPlatformName() + ") "
                             + "that handles the world editing.");
            }
            this.adapter.invalidate();
        }
    }

    /**
     * Called on plugin disable.
     */
    @Override
    public void onDisable() {
        Fawe.get().onDisable();
        WorldEdit worldEdit = WorldEdit.getInstance();
        worldEdit.getSessionManager().unload();
        if (platform != null) {
            worldEdit.getEventBus().post(new PlatformUnreadyEvent(platform));
            worldEdit.getPlatformManager().unregister(platform);
            platform.unregisterCommands();
        }
        if (config != null) {
            config.unload();
        }
        this.getServer().getScheduler().cancelTasks(this);
    }

    /**
     * Loads and reloads all configuration.
     */
    protected void loadConfiguration() {
        config.unload();
        config.load();
        getPermissionsResolver().load();
    }

    /**
     * Create a default configuration file from the .jar.
     *
     * @param name the filename
     */
    protected void createDefaultConfiguration(String name) {
        File actual = new File(getDataFolder(), name);
        if (!actual.exists()) {
            try (InputStream stream = getResource("defaults/" + name)) {
                if (stream == null) {
                    throw new FileNotFoundException();
                }
                copyDefaultConfig(stream, actual, name);
            } catch (IOException e) {
                LOGGER.error("Unable to read default configuration: " + name);
            }
        }
    }

    private void copyDefaultConfig(InputStream input, File actual, String name) {
        try (FileOutputStream output = new FileOutputStream(actual)) {
            byte[] buf = new byte[8192];
            int length;
            while ((length = input.read(buf)) > 0) {
                output.write(buf, 0, length);
            }

            LOGGER.info("Default configuration file written: " + name);
        } catch (IOException e) {
            LOGGER.warn("Failed to write default config file", e);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
        // Add the command to the array because the underlying command handling
        // code of WorldEdit expects it
        String[] split = new String[args.length + 1];
        System.arraycopy(args, 0, split, 1, args.length);
        split[0] = commandLabel;

        CommandEvent event = new CommandEvent(wrapCommandSender(sender), Joiner.on(" ").join(split));
        getWorldEdit().getEventBus().post(event);

        return true;
    }

    /*
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String commandLabel, String[] args) {
        int plSep = commandLabel.indexOf(":");
        if (plSep >= 0 && plSep < commandLabel.length() + 1) {
            commandLabel = commandLabel.substring(plSep + 1);
        }

        StringBuilder sb = new StringBuilder("/").append(commandLabel);
        if (args.length > 0) {
            sb.append(" ");
        }
        String arguments = Joiner.on(" ").appendTo(sb, args).toString();
        CommandSuggestionEvent event = new CommandSuggestionEvent(wrapCommandSender(sender), arguments);
        getWorldEdit().getEventBus().post(event);
        return CommandUtil.fixSuggestions(arguments, event.getSuggestions());
    }
    */

    /**
     * Gets the session for the player.
     *
     * @param player a player
     * @return a session
     */
    public LocalSession getSession(Player player) {
        return WorldEdit.getInstance().getSessionManager().get(wrapPlayer(player));
    }

    /**
     * Gets the session for the player.
     *
     * @param player a player
     * @return a session
     */
    public EditSession createEditSession(Player player) {
        com.sk89q.worldedit.entity.Player wePlayer = wrapPlayer(player);
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(wePlayer);
        BlockBag blockBag = session.getBlockBag(wePlayer);

        EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
            .locatableActor(wePlayer)
            .maxBlocks(session.getBlockChangeLimit())
            .blockBag(blockBag)
            .build();
        editSession.enableStandardMode();

        return editSession;
    }

    /**
     * Remember an edit session.
     *
     * @param player a player
     * @param editSession an edit session
     */
    public void remember(Player player, EditSession editSession) {
        com.sk89q.worldedit.entity.Player wePlayer = wrapPlayer(player);
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(wePlayer);

        session.remember(editSession);
        editSession.close();

        WorldEdit.getInstance().flushBlockBag(wePlayer, editSession);
    }

    /**
     * Returns the configuration used by WorldEdit.
     *
     * @return the configuration
     */
    public BukkitConfiguration getLocalConfiguration() {
        return config;
    }

    /**
     * Get the permissions resolver in use.
     *
     * @return the permissions resolver
     */
    public PermissionsResolverManager getPermissionsResolver() {
        return PermissionsResolverManager.getInstance();
    }
    
    /**
     * Get the permissions resolver in use.
     *
     * @return the permissions resolver
     */
    public BukkitPermissionAttachmentManager getPermissionAttachmentManager() {
        return permissionAttachmentManager;
    }

    /**
     * Used to wrap a Bukkit Player as a WorldEdit Player.
     *
     * @param player a player
     * @return a wrapped player
     */
    public BukkitPlayer wrapPlayer(Player player) {
        BukkitPlayer wePlayer = getCachedPlayer(player);
        if (wePlayer == null) {
            synchronized (player) {
                wePlayer = getCachedPlayer(player);
                if (wePlayer == null) {
                    wePlayer = new BukkitPlayer(this, player);
                    player.setMetadata("WE", new FixedMetadataValue(this, wePlayer));
                    return wePlayer;
                }
            }
        }
        return wePlayer;
    }

    public BukkitPlayer getCachedPlayer(Player player) {
        List<MetadataValue> meta = player.getMetadata("WE");
        if (meta.isEmpty()) {
            return null;
        }
        return (BukkitPlayer) meta.get(0).value();
    }

    public Actor wrapCommandSender(CommandSender sender) {
        if (sender instanceof Player) {
            return wrapPlayer((Player) sender);
        } else if (config.commandBlockSupport && sender instanceof BlockCommandSender) {
            return new BukkitBlockCommandSender(this, (BlockCommandSender) sender);
        }

        return new BukkitCommandSender(this, sender);
    }

    public BukkitServerInterface getInternalPlatform() {
        return platform;
    }

    /**
     * Get WorldEdit.
     *
     * @return an instance
     */
    public WorldEdit getWorldEdit() {
        return WorldEdit.getInstance();
    }

    /**
     * Gets the instance of this plugin.
     *
     * @return an instance of the plugin
     * @throws NullPointerException if the plugin hasn't been enabled
     */
    public static WorldEditPlugin getInstance() {
        return checkNotNull(INSTANCE);
    }

    /**
     * Get the Bukkit implementation adapter.
     *
     * @return the adapter
     */
    Lifecycled<BukkitImplAdapter> getLifecycledBukkitImplAdapter() {
        return adapter;
    }

    public BukkitImplAdapter getBukkitImplAdapter() {
        return adapter.value().orElse(null);
    }

    private class WorldInitListener implements Listener {
        private boolean loaded = false;

        @EventHandler(priority = EventPriority.LOWEST)
        public void onWorldInit(@SuppressWarnings("unused") WorldInitEvent event) {
            if (loaded) {
                return;
            }
            loaded = true;
            setupWorldData();
        }
    }

    private class AsyncTabCompleteListener implements Listener {
        AsyncTabCompleteListener() {
        }

        @SuppressWarnings("UnnecessaryFullyQualifiedName")
        @EventHandler(ignoreCancelled = true)
        public void onAsyncTabComplete(com.destroystokyo.paper.event.server.AsyncTabCompleteEvent event) {
            if (!event.isCommand()) {
                return;
            }

            String buffer = event.getBuffer();
            int firstSpace = buffer.indexOf(' ');
            if (firstSpace < 0) {
                return;
            }
            String label = buffer.substring(0, firstSpace);
            // Strip leading slash, if present.
            label = label.startsWith("/") ? label.substring(1) : label;
            final Optional<org.enginehub.piston.Command> command
                    = WorldEdit.getInstance().getPlatformManager().getPlatformCommandManager().getCommandManager().getCommand(label);
            if (!command.isPresent()) {
                return;
            }

            CommandSuggestionEvent suggestEvent = new CommandSuggestionEvent(wrapCommandSender(event.getSender()), buffer);
            getWorldEdit().getEventBus().post(suggestEvent);

            event.setCompletions(CommandUtil.fixSuggestions(buffer, suggestEvent.getSuggestions()));
            event.setHandled(true);
        }
    }
}
