// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.feature;

import javax.annotation.Nullable;
import java.util.Objects;

public final class Availability {

    public static final Availability AVAILABLE = new Availability(null);

    @Nullable
    private final String reason;

    private Availability(@Nullable String reason) {
        this.reason = reason;
    }

    // The reason shows up in the log and in crash reports.
    public static Availability unavailable(String reason) {
        return new Availability(Objects.requireNonNull(reason, "reason"));
    }

    public boolean isAvailable() {
        return reason == null;
    }

    @Nullable
    public String reason() {
        return reason;
    }
}
