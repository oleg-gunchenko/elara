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
package org.tools4j.elara.chronicle;

import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.wire.WireType;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.tools4j.elara.log.MessageLog.AppendContext;
import org.tools4j.elara.log.MessageLog.Appender;
import org.tools4j.elara.log.MessageLog.Handler;
import org.tools4j.elara.log.MessageLog.Poller;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.tools4j.elara.log.MessageLog.Handler.Result.POLL;

/**
 * Unit test for {@link ChronicleMessageLog}.
 */
class ChronicleMessageLogTest {

    @Test
    public void appendAndPoll(final TestInfo testInfo) {
        //given
        final ChronicleMessageLog messageLog = chronicleMessageLog(testInfo);

        //when + then
        final DirectBuffer[] messages = append(messageLog);
        pollAndAssert(messageLog.poller(), messages);
        pollAndAssert(messageLog.poller(), messages);
        pollAndAssert(messageLog.poller("remember-position"), messages);
        pollAndAssert(messageLog.poller("remember-position"));
    }

    @Test
    public void appending(final TestInfo testInfo) {
        //given
        final ChronicleMessageLog messageLog = chronicleMessageLog(testInfo);

        //when + then
        final DirectBuffer[] messages = appending(messageLog);
        pollAndAssert(messageLog.poller(), messages);
        pollAndAssert(messageLog.poller(), messages);
    }

    @Test
    public void appendingFromMultipleThreads(final TestInfo testInfo) throws InterruptedException {
        //given
        final int nThreads = 5;
        final ChronicleMessageLog messageLog = chronicleMessageLog(testInfo);
        ConcurrentLinkedQueue<DirectBuffer> messageQueue = new ConcurrentLinkedQueue<>();

        //when
        final Thread[] appenders = new Thread[nThreads];
        for (int i = 0; i < nThreads; i++) {
            final Runnable r = () -> {
                messageQueue.addAll(Arrays.asList(appending(messageLog)));
                sleep(20);//given other threads chance for interleaved appending
                messageQueue.addAll(Arrays.asList(appending(messageLog)));
            };
            appenders[i] = new Thread(null, r, "appender-" + i);
            appenders[i].start();
        }
        for (int i = 0; i < nThreads; i++) {
            appenders[i].join(5000);
        }

        //then
        final DirectBuffer[] messages = messageQueue.toArray(new DirectBuffer[0]);
        poll(messageLog.poller(), false, messages);
        poll(messageLog.poller(), false, messages);
    }

    @Test
    public void appendingWithAbort(final TestInfo testInfo) {
        //given
        final ChronicleMessageLog messageLog = chronicleMessageLog(testInfo);

        //when + then
        final DirectBuffer[] messages = appendingWithAbort(messageLog);
        pollAndAssert(messageLog.poller(), messages);
    }

    private DirectBuffer[] append(final ChronicleMessageLog messageLog) {
        //given
        final DirectBuffer[] messages = new DirectBuffer[]{
                message("Hi!"),
                message("Hello world!"),
                message("A somewhat longer message"),
                message("Peter and Paul"),
                message("a^2 + b^2 = c^2"),
        };
        final Appender appender = messageLog.appender();

        //when
        for (final DirectBuffer message : messages) {
            appender.append(message, 0, message.capacity());
        }

        //then
        return messages;
    }

    private DirectBuffer[] appending(final ChronicleMessageLog messageLog) {
        //given
        final DirectBuffer[] messages = new DirectBuffer[]{
                message("Hi!"),
                message("Hello world!"),
                message("A somewhat longer message"),
                message("Peter and Paul"),
                message("a^2 + b^2 = c^2"),
        };
        final Appender appender = messageLog.appender();

        //when
        for (final DirectBuffer message : messages) {
            try (final AppendContext context = appender.appending()) {
                context.buffer().putBytes(0, message, 0, message.capacity());
                context.commit(message.capacity());
            }
        }

        //then
        return messages;
    }

    private DirectBuffer[] appendingWithAbort(final ChronicleMessageLog messageLog) {
        //given
        final DirectBuffer[] messages = new DirectBuffer[]{
                message("Hi!"),
                message("Hello world!"),
                message("A somewhat longer message"),
                message("Peter and Paul"),
                message("a^2 + b^2 = c^2"),
        };
        final List<DirectBuffer> committed = new ArrayList<>();
        final Appender appender = messageLog.appender();

        //when
        for (int i = 0; i < messages.length; i++) {
            final DirectBuffer message = messages[i];
            try (final AppendContext context = appender.appending()) {
                context.buffer().putBytes(0, message, 0, message.capacity());
                if (i % 2 == 0) {
                    context.commit(message.capacity());
                    committed.add(message);
                } //else abort
            }
        }

        //then
        return committed.toArray(new DirectBuffer[0]);
    }

    private void pollAndAssert(final Poller poller,
                               final DirectBuffer... messages) {
        poll(poller, true, messages);
    }

    private void poll(final Poller poller,
                      final boolean assertMessage,
                      final DirectBuffer... messages) {
        //given
        final MessageCaptor messageCaptor = new MessageCaptor();

        //when
        for (int i = 0; i < messages.length; i++) {
            final int p = poller.poll(messageCaptor.reset());

            //then
            assertEquals(1, p, "polled");
            if (assertMessage) {
                assertEquals(0, messages[i].compareTo(messageCaptor.get()), "messages[" + i + "] compared");
            }
        }

        //when
        final int p = poller.poll(messageCaptor.reset());

        //then
        assertEquals(0, p, "polled");
        assertNull(messageCaptor.get(), "polled message");
    }

    private ChronicleMessageLog chronicleMessageLog(final TestInfo testInfo) {
        final String fileName = testInfo.getTestClass().get().getSimpleName() + "_" + testInfo.getTestMethod().get().getName();
        final String path = "build/chronicle/" + fileName + ".cq4";
        delete(path);
        final ChronicleQueue cq = ChronicleQueue.singleBuilder()
                .path(path)
                .wireType(WireType.BINARY_LIGHT)
                .build();
        return new ChronicleMessageLog(cq);
    }

    private static void delete(final String path) {
        final File dir = new File(path);
        final File[] files = dir.isDirectory() ? dir.listFiles() : null;
        for (final File file : files == null ? new File[0] : files) {
            file.delete();
        }
        dir.delete();
        if (dir.exists()) {
            throw new IllegalStateException("Exists: " + dir.getAbsolutePath());
        }
    }
    private static DirectBuffer message(final String msg) {
        final MutableDirectBuffer buffer = new ExpandableArrayBuffer(msg.length());
        buffer.putStringWithoutLengthAscii(0, msg);
        return buffer;
    }

    private static class MessageCaptor implements Handler {
        private MutableDirectBuffer buffer;

        MessageCaptor reset() {
            buffer = null;
            return this;
        }

        DirectBuffer get() {
            return buffer;
        }

        @Override
        public Result onMessage(final DirectBuffer message) {
            buffer = new ExpandableArrayBuffer(message.capacity());
            buffer.putBytes(0, message, 0, message.capacity());
            return POLL;
        }
    }

    private static void sleep(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (final InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}