// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.feature;

import io.github.silentcall2598.focalis.render.lifecycle.RenderCheckpointListener;
import io.github.silentcall2598.focalis.render.lifecycle.RenderLifecycle;
import io.github.silentcall2598.focalis.render.lifecycle.RenderStage;
import io.github.silentcall2598.focalis.render.lifecycle.RenderStageListener;
import org.apache.logging.log4j.Logger;

/**
 * Everything registered through a context belongs to its feature and is detached when the feature fails or is
 * turned off. After that the context is closed, so a late registration can't leave a listener behind.
 */
public final class FeatureContext {

    private final String featureId;
    private final RenderLifecycle renderLifecycle;
    private final Logger logger;
    private boolean closed;

    FeatureContext(String featureId, RenderLifecycle renderLifecycle, Logger logger) {
        this.featureId = featureId;
        this.renderLifecycle = renderLifecycle;
        this.logger = logger;
    }

    public Logger logger() {
        return logger;
    }

    public void addRenderListener(RenderStage stage, RenderStageListener listener) {
        if (closed) {
            throw new IllegalStateException("Feature '" + featureId + "' is no longer active");
        }
        renderLifecycle.register(stage, featureId, listener);
    }

    public void addCheckpointListener(RenderCheckpointListener listener) {
        if (closed) {
            throw new IllegalStateException("Feature '" + featureId + "' is no longer active");
        }
        renderLifecycle.registerCheckpoint(featureId, listener);
    }

    void close() {
        closed = true;
    }
}
