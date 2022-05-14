/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020-2022 tools4j.org (Marco Terzer, Anton Anufriev)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.tools4j.elara.plugin.base;

import org.tools4j.elara.plugin.api.SystemPlugin;
import org.tools4j.elara.plugin.api.TypeRange;
import org.tools4j.elara.plugin.base.BaseState.Mutable;
import org.tools4j.elara.store.EventStoreRepairer;
import org.tools4j.elara.store.MessageStore;

import java.io.IOException;

import static java.util.Objects.requireNonNull;

/**
 * Default plugin to initialise {@link BaseState}.  Another plugin can be used to initialise the base state if it
 * returns an implementation of {@link BaseConfiguration}.
 */
public enum BasePlugin implements SystemPlugin<Mutable> {
    INSTANCE;

    @Override
    public Mutable defaultPluginState() {
        return BaseConfiguration.createDefaultBaseState();
    }

    @Override
    public BaseConfiguration configuration(final org.tools4j.elara.init.Configuration appConfig,
                                           final BaseState.Mutable baseState) {
        requireNonNull(appConfig);
        requireNonNull(baseState);
        repairEventStoreIfNeeded(appConfig);
        return () -> baseState;
    }

    @Override
    public TypeRange typeRange() {
        return TypeRange.BASE;
    }

    /**
     * Base context to initialise base state.  Other plugins can implement this
     * context if they want to replace the default base plugin and extend the base
     * state.
     */
    @FunctionalInterface
    public interface BaseConfiguration extends Configuration.Default {
        static BaseState.Mutable createDefaultBaseState() {
            return new DefaultBaseState();
        }

        BaseState.Mutable baseState();
    }

    private void repairEventStoreIfNeeded(final org.tools4j.elara.init.Configuration appConfig) {
        final MessageStore eventStore = appConfig.eventStore();
        final EventStoreRepairer eventStoreRepairer = new EventStoreRepairer(eventStore);
        if (eventStoreRepairer.isCorrupted()) {
            appConfig.exceptionHandler().handleException("Repairing corrupted event store",
                    new IOException("Corrupted event store: " + eventStore));
            eventStoreRepairer.repair();
        }
    }
}
