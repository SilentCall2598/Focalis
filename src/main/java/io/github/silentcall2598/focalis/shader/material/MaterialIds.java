// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.material;

import io.github.silentcall2598.focalis.shader.pack.MaterialMappings;
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
    private final Map<Item, Integer> items;
    private final Map<Class<?>, Integer> entities;
    private final int player;
    private final int lightningBolt;
    private final MaterialReport report;

    private MaterialIds(MaterialMappings mappings, Map<IBlockState, Integer> blocks, Map<Item, Integer> items,
            Map<Class<?>, Integer> entities, int player, int lightningBolt, MaterialReport report) {
        this.mappings = mappings;
        this.blocks = blocks;
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
        return new MaterialIds(mappings, blocks, items, entities, player == null ? UNMAPPED : player,
                lightningBolt == null ? UNMAPPED : lightningBolt, report.build(System.nanoTime() - start));
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
     * The ID of a block state, normally the actual state the renderer draws. Forge's extended states count as their
     * clean state, since unlisted values don't take part in matching.
     */
    public int block(IBlockState state) {
        IBlockState listed = state instanceof IExtendedBlockState ? ((IExtendedBlockState) state).getClean() : state;
        Integer id = blocks.get(listed);
        return id == null ? UNMAPPED : id;
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
