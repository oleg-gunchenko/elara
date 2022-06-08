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
package org.tools4j.elara.plugin.metrics;

import org.agrona.MutableDirectBuffer;
import org.tools4j.elara.step.AgentStep;
import org.tools4j.elara.store.MessageStore.Appender;
import org.tools4j.elara.store.MessageStore.AppendingContext;
import org.tools4j.elara.time.TimeSource;

import static java.util.Objects.requireNonNull;
import static org.tools4j.elara.plugin.metrics.FlyweightMetricsStoreEntry.writeCounter;
import static org.tools4j.elara.plugin.metrics.FlyweightMetricsStoreEntry.writeFrequencyMetricsHeader;

public class FrequencyMetricsWriterStep implements AgentStep {

    private final TimeSource timeSource;
    private final MetricsConfig configuration;
    private final MetricsState state;
    private final long interval;
    private final Appender appender;
    private long repetition;
    private long lastWriteTime;

    public FrequencyMetricsWriterStep(final TimeSource timeSource,
                                      final MetricsConfig configuration,
                                      final MetricsState state) {
        this.timeSource = requireNonNull(timeSource);
        this.configuration = requireNonNull(configuration);
        this.state = requireNonNull(state);
        if (configuration.frequencyMetrics().isEmpty()) {
            throw new IllegalArgumentException("Configuration contains no frequency metrics");
        }
        this.interval = configuration.frequencyMetricInterval();
        if (interval <= 0) {
            throw new IllegalArgumentException("configuration.frequencyMetricInterval() must be positive: " + interval);
        }
        this.appender = requireNonNull(configuration.frequencyMetricsStore(), "configuration.frequencyMetricsStore()")
                .appender();
    }

    @Override
    public int doWork() {
        int workDone = 0;
        if (repetition == 0) {
            state.clearFrequencyMetrics();
            workDone++;
        }
        final long time = timeSource.currentTime();
        if (time - lastWriteTime >= interval || repetition == 0) {
            writeMetrics(time);
            state.clearFrequencyMetrics();
            lastWriteTime = time;
            repetition++;
            workDone++;
        }
        //NOTE: - we always perform some work by checking the time
        //      - returning always true would essentially enforce busy spinning and disable any idle strategy
        //      - a reasonably configured idle strategy should never cause any serious metrics logging problems
        return workDone;
    }

    private void writeMetrics(final long time) {
        final short choice = FrequencyMetric.choice(configuration.frequencyMetrics());
        final int count = FrequencyMetric.count(choice);
        try (final AppendingContext context = appender.appending()) {
            final MutableDirectBuffer buffer = context.buffer();
            //NOTE: our repetition is intentionally a long so we can also use the sign bit before overflow
            final int headerLen = writeFrequencyMetricsHeader(choice, (int)repetition, interval, time, buffer, 0);
            int offset = headerLen;
            for (int i = 0; i < count; i++) {
                final FrequencyMetric metric = FrequencyMetric.metric(choice, i);
                final long counter = state.counter(metric);
                offset += writeCounter(counter, buffer, offset);
            }
            final int length = offset;
            context.commit(length);
        }
    }
}
