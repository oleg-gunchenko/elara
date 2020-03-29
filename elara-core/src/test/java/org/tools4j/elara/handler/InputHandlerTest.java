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
package org.tools4j.elara.handler;

import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tools4j.elara.command.Command;
import org.tools4j.elara.event.EventType;
import org.tools4j.elara.flyweight.FlyweightCommand;
import org.tools4j.elara.input.Input;
import org.tools4j.elara.time.TimeSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link InputHandler}
 */
@ExtendWith(MockitoExtension.class)
public class InputHandlerTest {

    @Mock
    private TimeSource timeSource;
    @Mock
    private Input input;

    private List<Command> commandLog;

    //under test
    private InputHandler inputHandler;

    @BeforeEach
    public void init() {
        commandLog = new ArrayList<>();
        inputHandler = new InputHandler(timeSource, input, command -> {
            final MutableDirectBuffer buffer = new ExpandableArrayBuffer();
            command.writeTo(buffer, 0);
            commandLog.add(new FlyweightCommand().init(buffer, 0));
        }, new ExpandableArrayBuffer(), new FlyweightCommand());
    }

    @Test
    public void shouldAppendDefaultTypeCommand() {
        //given
        final long commandTime = 9988776600001L;
        final int inputId = 1;
        final long seq = 22;
        when(input.id()).thenReturn(inputId);
        final String text = "Hello world!!!";
        final int offset = 77;
        final DirectBuffer message = message(text, offset);
        final int length = message.capacity() - offset;

        //when
        when(timeSource.currentTime()).thenReturn(commandTime);
        inputHandler.onMessage(seq, message, offset, length);

        //then
        assertEquals(1, commandLog.size(), "commandLog.size");
        assertCommand(inputId, seq, commandTime, EventType.APPLICATION, text, commandLog.get(0));
    }

    @Test
    public void shouldAppendCommandWithType() {
        //given
        final long commandTime = 9988776600001L;
        final int inputId = 1;
        final long seq = 22;
        final int type = 12345;
        when(input.id()).thenReturn(inputId);
        final String text = "Hello world!!!";
        final int offset = 77;
        final DirectBuffer message = message(text, offset);
        final int length = message.capacity() - offset;

        //when
        when(timeSource.currentTime()).thenReturn(commandTime);
        inputHandler.onMessage(seq, type, message, offset, length);

        //then
        assertCommand(inputId, seq, commandTime, type, text, commandLog.get(0));
    }

    private void assertCommand(final int inputId,
                               final long seq,
                               final long commandTime,
                               final int tpye,
                               final String text,
                               final Command command) {
        final int payloadSize = Integer.BYTES + text.length();
        assertEquals(inputId, command.id().input(), "command.id.input");
        assertEquals(seq, command.id().sequence(), "command.id.sequence");
        assertEquals(commandTime, command.time(), "command.time");
        assertFalse(command.isAdmin(), "command.isAdmin");
        assertTrue(command.isApplication(), "command.isApplication");
        assertEquals(tpye, command.type(), "command.type");
        final DirectBuffer payload = command.payload();
        assertEquals(payloadSize, payload.capacity(), "command.payload.capacity");
        assertEquals(text, payload.getStringAscii(0), "command.payload.text");
    }

    private static DirectBuffer message(final String text, final int offset) {
        final int length = offset + Integer.BYTES + text.length();
        final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer(length);
        buffer.putStringAscii(offset, text);
        return buffer;
    }
}
