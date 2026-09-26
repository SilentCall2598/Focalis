// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.feature;

import javax.annotation.Nullable;

// Immutable so crash reports can read it from any thread.
public final class FeatureStatus {

    private final String featureId;
    private final FeatureState state;
    @Nullable
    private final String detail;

    FeatureStatus(String featureId, FeatureState state, @Nullable String detail) {
        this.featureId = featureId;
        this.state = state;
        this.detail = detail;
    }

    public String featureId() {
        return featureId;
    }

    public FeatureState state() {
        return state;
    }

    @Nullable
    public String detail() {
        return detail;
    }

    @Override
    public String toString() {
        return detail == null ? featureId + "=" + state : featureId + "=" + state + " (" + detail + ")";
    }
}
