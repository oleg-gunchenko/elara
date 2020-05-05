/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2020 tools4j.org (Marco Terzer, Anton Anufriev)
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
package org.tools4j.elara.output;

import org.agrona.DirectBuffer;
import org.agrona.ExpandableDirectByteBuffer;
import org.agrona.MutableDirectBuffer;
import org.tools4j.elara.flyweight.FlyweightCommand;
import org.tools4j.elara.flyweight.FrameDescriptor;
import org.tools4j.elara.input.Input;
import org.tools4j.elara.input.SequenceGenerator;
import org.tools4j.elara.log.MessageLog;
import org.tools4j.elara.time.TimeSource;

import static java.util.Objects.requireNonNull;
import static org.tools4j.elara.plugin.timer.TimerCommandDescriptor.TIMER_PAYLOAD_SIZE;

public class DefaultCommandLoopback implements CommandLoopback {

    private final MessageLog.Appender commandLogAppender;
    private final TimeSource timeSource;
    private final SequenceGenerator adminSequenceGenerator;

    private final MutableDirectBuffer buffer = new ExpandableDirectByteBuffer(
            FrameDescriptor.HEADER_LENGTH + TIMER_PAYLOAD_SIZE);
    private final FlyweightCommand flyweightCommand = new FlyweightCommand();

    public DefaultCommandLoopback(final MessageLog.Appender commandLogAppender,
                                  final TimeSource timeSource,
                                  final SequenceGenerator adminSequenceGenerator) {
        this.commandLogAppender = requireNonNull(commandLogAppender);
        this.timeSource = requireNonNull(timeSource);
        this.adminSequenceGenerator = requireNonNull(adminSequenceGenerator);
    }

    @Override
    public void enqueueCommand(final int type, final DirectBuffer command, final int offset, final int length) {
        flyweightCommand.init(buffer, 0, Input.LOOPBACK_ID, adminSequenceGenerator.nextSequence(), type,
                timeSource.currentTime(), command, offset, length);
        try (final MessageLog.AppendContext context = commandLogAppender.appending()) {
            final int written = flyweightCommand.writeTo(context.buffer(), 0);
            context.commit(written);
        } finally {
            flyweightCommand.reset();
        }
    }
}
