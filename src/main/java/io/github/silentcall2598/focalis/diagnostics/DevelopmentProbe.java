// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.diagnostics;

import io.github.silentcall2598.focalis.feature.FeatureStatus;
import io.github.silentcall2598.focalis.render.lifecycle.RenderLifecycle;
import io.github.silentcall2598.focalis.render.state.GlContextInfo;
import io.github.silentcall2598.focalis.render.target.WorldTargetMonitor;
import io.github.silentcall2598.focalis.shader.WorldProgramMonitor;
import io.github.silentcall2598.focalis.shader.post.PostPassMonitor;

import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.function.Supplier;

/**
 * An optional instrumentation hook for local development. Only a launch with {@code -Dfocalis.probe=<class>} gets
 * one, and that class needs a public constructor taking the feature statuses and the GL context, both as
 * suppliers. Normal play never has a probe.
 */
public interface DevelopmentProbe {

    String CLASS_PROPERTY = "focalis.probe";

    WorldTargetMonitor worldTargetMonitor();

    WorldProgramMonitor worldProgramMonitor();

    PostPassMonitor postPassMonitor();

    /** Registers the listeners that have to run before the features' own. Called before features register. */
    void installBefore(RenderLifecycle lifecycle);

    /** Registers the listeners that have to run after the features' own. */
    void installAfter(RenderLifecycle lifecycle);

    /**
     * The probe the launch asked for, or null when it didn't ask for one.
     *
     * @throws IllegalStateException when the named probe can't be created, since a launch asking for one is a
     *     development launch that should stop rather than run unwatched
     */
    @Nullable
    static DevelopmentProbe fromSystemProperty(Supplier<List<FeatureStatus>> features,
            Supplier<GlContextInfo> glContext) {
        String name = System.getProperty(CLASS_PROPERTY);
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        try {
            Class<? extends DevelopmentProbe> type = Class.forName(name.trim(), true,
                    DevelopmentProbe.class.getClassLoader()).asSubclass(DevelopmentProbe.class);
            return type.getConstructor(Supplier.class, Supplier.class).newInstance(features, glContext);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("Development probe " + name.trim() + " failed to start", e.getCause());
        } catch (ReflectiveOperationException | ClassCastException | LinkageError e) {
            throw new IllegalStateException("Development probe " + name.trim() + " can't be created", e);
        }
    }
}
