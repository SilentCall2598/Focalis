// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.routing;

import io.github.silentcall2598.focalis.render.lifecycle.RenderDrawKind;
import io.github.silentcall2598.focalis.render.lifecycle.RenderStage;
import org.junit.jupiter.api.Test;

import static io.github.silentcall2598.focalis.shader.routing.ShaderProgramRouter.route;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ShaderProgramRouterTest {

    @Test
    void boundariesThatDrawNothingHaveNoRole() {
        assertEquals(ShaderProgramRole.NONE, route(RenderStage.FRAME, RenderDrawKind.DEFAULT));
        assertEquals(ShaderProgramRole.NONE, route(RenderStage.WORLD, RenderDrawKind.DEFAULT));
        assertEquals(ShaderProgramRole.NONE, route(RenderStage.GUI, RenderDrawKind.DEFAULT));
    }

    @Test
    void everySupportedContextHasItsRole() {
        assertEquals(ShaderProgramRole.SKY, route(RenderStage.SKY, RenderDrawKind.DEFAULT));
        assertEquals(ShaderProgramRole.TERRAIN_SOLID, route(RenderStage.TERRAIN, RenderDrawKind.TERRAIN_SOLID));
        assertEquals(ShaderProgramRole.TERRAIN_CUTOUT_MIPPED,
                route(RenderStage.TERRAIN, RenderDrawKind.TERRAIN_CUTOUT_MIPPED));
        assertEquals(ShaderProgramRole.TERRAIN_CUTOUT, route(RenderStage.TERRAIN, RenderDrawKind.TERRAIN_CUTOUT));
        assertEquals(ShaderProgramRole.TERRAIN_TRANSLUCENT,
                route(RenderStage.TRANSLUCENT, RenderDrawKind.TERRAIN_TRANSLUCENT));
        assertEquals(ShaderProgramRole.ENTITIES, route(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_0));
        assertEquals(ShaderProgramRole.ENTITIES, route(RenderStage.ENTITIES, RenderDrawKind.ENTITY_PASS_1));
        assertEquals(ShaderProgramRole.PARTICLES_LIT, route(RenderStage.PARTICLES, RenderDrawKind.PARTICLES_LIT));
        assertEquals(ShaderProgramRole.PARTICLES_NORMAL, route(RenderStage.PARTICLES, RenderDrawKind.PARTICLES_NORMAL));
        assertEquals(ShaderProgramRole.WEATHER, route(RenderStage.WEATHER, RenderDrawKind.DEFAULT));
        assertEquals(ShaderProgramRole.CLOUDS, route(RenderStage.CLOUDS, RenderDrawKind.DEFAULT));
        assertEquals(ShaderProgramRole.HAND, route(RenderStage.HAND, RenderDrawKind.DEFAULT));
    }

    @Test
    void malformedContextIsUnclassified() {
        assertEquals(ShaderProgramRole.UNCLASSIFIED, route(RenderStage.TERRAIN, RenderDrawKind.DEFAULT));
        assertEquals(ShaderProgramRole.UNCLASSIFIED, route(RenderStage.TERRAIN, RenderDrawKind.TERRAIN_TRANSLUCENT));
        assertEquals(ShaderProgramRole.UNCLASSIFIED, route(RenderStage.TRANSLUCENT, RenderDrawKind.TERRAIN_SOLID));
        assertEquals(ShaderProgramRole.UNCLASSIFIED, route(RenderStage.ENTITIES, RenderDrawKind.DEFAULT));
        assertEquals(ShaderProgramRole.UNCLASSIFIED, route(RenderStage.PARTICLES, RenderDrawKind.ENTITY_PASS_0));
        assertEquals(ShaderProgramRole.UNCLASSIFIED, route(RenderStage.WEATHER, RenderDrawKind.PARTICLES_NORMAL));
        assertEquals(ShaderProgramRole.UNCLASSIFIED, route(RenderStage.WORLD, RenderDrawKind.TERRAIN_SOLID));
        assertEquals(ShaderProgramRole.UNCLASSIFIED, route(RenderStage.FRAME, RenderDrawKind.ENTITY_PASS_1));
        assertEquals(ShaderProgramRole.UNCLASSIFIED, route(RenderStage.GUI, RenderDrawKind.PARTICLES_LIT));
        assertEquals(ShaderProgramRole.UNCLASSIFIED, route(RenderStage.SKY, RenderDrawKind.TERRAIN_SOLID));
        assertEquals(ShaderProgramRole.UNCLASSIFIED, route(RenderStage.CLOUDS, RenderDrawKind.ENTITY_PASS_0));
        assertEquals(ShaderProgramRole.UNCLASSIFIED, route(RenderStage.HAND, RenderDrawKind.TERRAIN_CUTOUT));
    }

    @Test
    void neverReturnsNull() {
        for (RenderStage stage : RenderStage.values()) {
            for (RenderDrawKind kind : RenderDrawKind.values()) {
                assertNotNull(route(stage, kind), stage + "/" + kind);
            }
        }
        assertEquals(ShaderProgramRole.UNCLASSIFIED, route(null, RenderDrawKind.DEFAULT));
        assertEquals(ShaderProgramRole.UNCLASSIFIED, route(RenderStage.SKY, null));
    }

    // START and END of one stage carry the same stage and kind, so they always land on the same role.
    @Test
    void sameContextAlwaysGivesTheSameRole() {
        for (RenderStage stage : RenderStage.values()) {
            for (RenderDrawKind kind : RenderDrawKind.values()) {
                assertSame(route(stage, kind), route(stage, kind), stage + "/" + kind);
            }
        }
    }
}
