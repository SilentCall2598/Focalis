// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.diagnostics.qa;

import java.util.function.Consumer;
import java.util.function.Predicate;

/** One step of a scenario. It runs on client ticks until it says it's done, or fails the run on timeout. */
abstract class QaStep {

    final String name;
    final int timeoutTicks;

    QaStep(String name, int timeoutTicks) {
        this.name = name;
        this.timeoutTicks = timeoutTicks;
    }

    /** @param ticks client ticks since this step started, 0 on the first call */
    abstract boolean tick(QaProbe probe, int ticks);

    static QaStep action(String name, Consumer<QaProbe> action) {
        return new QaStep(name, 1) {
            @Override
            boolean tick(QaProbe probe, int ticks) {
                action.accept(probe);
                return true;
            }
        };
    }

    static QaStep until(String name, int timeoutTicks, Predicate<QaProbe> done) {
        return new QaStep(name, timeoutTicks) {
            @Override
            boolean tick(QaProbe probe, int ticks) {
                return done.test(probe);
            }
        };
    }

    static QaStep waitTicks(int count) {
        return new QaStep("wait " + count + " ticks", count + 1) {
            @Override
            boolean tick(QaProbe probe, int ticks) {
                return ticks >= count;
            }
        };
    }
}
