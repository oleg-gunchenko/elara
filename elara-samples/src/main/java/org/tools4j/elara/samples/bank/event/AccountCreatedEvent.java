/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020-2021 tools4j.org (Marco Terzer, Anton Anufriev)
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
package org.tools4j.elara.samples.bank.event;

import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;

import static java.util.Objects.requireNonNull;

public class AccountCreatedEvent implements BankEvent {
    public static final EventType TYPE = EventType.AccountCreated;

    public final String name;
    public AccountCreatedEvent(String name) {
        this.name = requireNonNull(name);
    }

    @Override
    public EventType type() {
        return TYPE;
    }

    @Override
    public DirectBuffer encode() {
        final MutableDirectBuffer buffer = new ExpandableArrayBuffer(
                Integer.BYTES + name.length());
        buffer.putStringAscii(0, name);
        return buffer;
    }

    public String toString() {
        return TYPE + "{name=" + name + "}";
    }

    public static AccountCreatedEvent decode(final DirectBuffer payload) {
        final String name = payload.getStringAscii(0);
        return new AccountCreatedEvent(name);
    }

    public static String toString(final DirectBuffer payload) {
        return decode(payload).toString();
    }
}
