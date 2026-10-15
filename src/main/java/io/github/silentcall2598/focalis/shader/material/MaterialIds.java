// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.material;

import com.google.common.collect.ImmutableMap;
import io.github.silentcall2598.focalis.shader.pack.MaterialMappings;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * One configuration's material IDs, resolved against the game's registries. Block states match by every listed
 * property, so the state the renderer works out from the world can have another ID than the stored state it came
 * from, like grass under snow. Nothing here goes through numeric IDs or metadata. Immutable, so any thread can read
 * it once it's published.
 */
public final class MaterialIds {

    /** What every lookup returns for something the pack doesn't map. Declared IDs are never negative. */
    public static final int UNMAPPED = -1;

    private static final ResourceLocation PLAYER = new ResourceLocation("player");

    private final MaterialMappings mappings;
    private final Map<IBlockState, Integer> blocks;
    // Per block with Forge extended states, the properties map of each mapped state to its ID. See extendedCopy.
    private final Map<Block, Map<ImmutableMap<IProperty<?>, Comparable<?>>, Integer>> extended;
    private final Map<Item, Integer> items;
    private final Map<Class<?>, Integer> entities;
    private final int player;
    private final int lightningBolt;
    private final MaterialReport report;

    private MaterialIds(MaterialMappings mappings, Map<IBlockState, Integer> blocks,
            Map<Block, Map<ImmutableMap<IProperty<?>, Comparable<?>>, Integer>> extended, Map<Item, Integer> items,
            Map<Class<?>, Integer> entities, int player, int lightningBolt, MaterialReport report) {
        this.mappings = mappings;
        this.blocks = blocks;
        this.extended = extended;
        this.items = items;
        this.entities = entities;
        this.player = player;
        this.lightningBolt = lightningBolt;
        this.report = report;
    }

    /**
     * Resolves the mappings against the registries as they are now. Mods register their content after pre-init, so
     * this has to wait until mod loading is complete. Only the blocks, items and entities the mappings name are read.
     */
    public static MaterialIds resolve(MaterialMappings mappings) {
        long start = System.nanoTime();
        MaterialReport.Builder report = new MaterialReport.Builder();
        Map<IBlockState, Integer> blocks = MaterialMatcher.blocks(mappings.declarations(MaterialMappings.Kind.BLOCK),
                MaterialIds::findBlock, report);
        Map<Item, Integer> items = MaterialMatcher.named(MaterialMappings.Kind.ITEM,
                mappings.declarations(MaterialMappings.Kind.ITEM), MaterialIds::findItem, report);
        Map<Class<?>, Integer> entities = MaterialMatcher.named(MaterialMappings.Kind.ENTITY,
                mappings.declarations(MaterialMappings.Kind.ENTITY), MaterialIds::findEntityType, report);
        // No player has EntityPlayer as its own class, so the player name is only checked with instanceof.
        Integer player = entities.remove(EntityPlayer.class);
        Integer lightningBolt = entities.get(EntityLightningBolt.class);
        return new MaterialIds(mappings, blocks, extendedIndex(blocks), items, entities,
                player == null ? UNMAPPED : player, lightningBolt == null ? UNMAPPED : lightningBolt,
                report.build(System.nanoTime() - start));
    }

    // Only Forge's extended states have copies, and only listed properties can leave a copy's clean state behind. Each
    // of those states has its own properties map, and blocks never share one, but the index is kept per block anyway.
    private static Map<Block, Map<ImmutableMap<IProperty<?>, Comparable<?>>, Integer>> extendedIndex(
            Map<IBlockState, Integer> blocks) {
        Map<Block, Map<ImmutableMap<IProperty<?>, Comparable<?>>, Integer>> index = new IdentityHashMap<>();
        for (Map.Entry<IBlockState, Integer> entry : blocks.entrySet()) {
            IBlockState state = entry.getKey();
            if (state instanceof IExtendedBlockState && !state.getProperties().isEmpty()) {
                index.computeIfAbsent(state.getBlock(), block -> new IdentityHashMap<>())
                        .put(state.getProperties(), entry.getValue());
            }
        }
        return index;
    }

    // Registry lookups follow Forge's aliases. getValue alone would hand back air for a name that isn't there.
    @Nullable
    private static MaterialMatcher.Block<IBlockState> findBlock(String name) {
        ResourceLocation key = new ResourceLocation(name);
        if (!ForgeRegistries.BLOCKS.containsKey(key)) {
            return null;
        }
        return new RegistryBlock(ForgeRegistries.BLOCKS.getValue(key).getBlockState());
    }

    @Nullable
    private static Item findItem(String name) {
        ResourceLocation key = new ResourceLocation(name);
        return ForgeRegistries.ITEMS.containsKey(key) ? ForgeRegistries.ITEMS.getValue(key) : null;
    }

    // Lightning bolts and players have names without a registry entry, see EntityList.isMatchingName.
    @Nullable
    private static Class<?> findEntityType(String name) {
        ResourceLocation key = new ResourceLocation(name);
        if (ForgeRegistries.ENTITIES.containsKey(key)) {
            return ForgeRegistries.ENTITIES.getValue(key).getEntityClass();
        }
        if (EntityList.LIGHTNING_BOLT.equals(key)) {
            return EntityLightningBolt.class;
        }
        return PLAYER.equals(key) ? EntityPlayer.class : null;
    }

    /** The mappings these IDs came from. */
    public MaterialMappings mappings() {
        return mappings;
    }

    public MaterialReport report() {
        return report;
    }

    /**
     * The ID of a block state, normally the actual state the renderer draws, before Forge's extended render data is
     * added. An extended copy counts as the listed state it stands for, since unlisted values don't take part in
     * matching.
     */
    public int block(IBlockState state) {
        Integer id = blocks.get(state);
        if (id == null && state instanceof IExtendedBlockState) {
            id = extendedCopy((IExtendedBlockState) state);
        }
        return id == null ? UNMAPPED : id;
    }

    // A Forge copy keeps the properties map of the listed state it stands for. Its getClean is that state until a listed
    // property changes on a copy that has unlisted values, which leaves getClean on the old state, so the map decides.
    @Nullable
    private Integer extendedCopy(IExtendedBlockState state) {
        IBlockState clean = state.getClean();
        if (clean != null && clean.getProperties() == state.getProperties()) {
            return blocks.get(clean);
        }
        Map<ImmutableMap<IProperty<?>, Comparable<?>>, Integer> byProperties = extended.get(state.getBlock());
        return byProperties == null ? null : byProperties.get(state.getProperties());
    }

    public int item(Item item) {
        Integer id = items.get(item);
        return id == null ? UNMAPPED : id;
    }

    /** The ID of an entity's type. A subclass without its own registry entry has no ID, like in vanilla. */
    public int entity(Entity entity) {
        Integer id = entities.get(entity.getClass());
        if (id != null) {
            return id;
        }
        if ((player == UNMAPPED && lightningBolt == UNMAPPED) || EntityList.getKey(entity) != null) {
            return UNMAPPED;
        }
        if (entity instanceof EntityPlayer) {
            return player;
        }
        return entity instanceof EntityLightningBolt ? lightningBolt : UNMAPPED;
    }

    // The registry's own state objects, with values written the way the game writes them in blockstate files.
    private static final class RegistryBlock implements MaterialMatcher.Block<IBlockState> {

        private final BlockStateContainer container;

        RegistryBlock(BlockStateContainer container) {
            this.container = container;
        }

        @Override
        public Collection<IBlockState> states() {
            return container.getValidStates();
        }

        @Nullable
        @Override
        public Collection<String> values(String property) {
            IProperty<?> found = container.getProperty(property);
            return found == null ? null : names(found);
        }

        @Override
        public String value(IBlockState state, String property) {
            return name(state, container.getProperty(property));
        }

        private static <T extends Comparable<T>> List<String> names(IProperty<T> property) {
            List<String> names = new ArrayList<>();
            for (T value : property.getAllowedValues()) {
                names.add(property.getName(value));
            }
            return names;
        }

        private static <T extends Comparable<T>> String name(IBlockState state, IProperty<T> property) {
            return property.getName(state.getValue(property));
        }
    }
}
