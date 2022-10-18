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
package org.tools4j.elara.stream.tcp.impl;

import org.agrona.LangUtil;
import org.tools4j.elara.stream.MessageReceiver;

import java.nio.channels.SelectionKey;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

class TcpReceiver implements MessageReceiver {

    private final Supplier<? extends TcpEndpoint> endpointSupplier;

    TcpReceiver(final Supplier<? extends TcpEndpoint> endpointSupplier) {
        this.endpointSupplier = requireNonNull(endpointSupplier);
    }

    @Override
    public int poll(final Handler handler) {
        final TcpEndpoint endpoint = endpointSupplier.get();
        if (endpoint == null) {
            return 0;
        }
        try {
            final int readyOps = endpoint.receive(handler);
            if (readyOps >= 0) {
                return (readyOps & SelectionKey.OP_READ) == 0 ? 0 : 1;
            }
            //we must have dropped a received message that was too large for our buffer
            //FIXME log
            return 1;
        } catch (final Exception e) {
            //FIXME log or handle
            LangUtil.rethrowUnchecked(e);
            throw e;
        }
    }

    @Override
    public boolean isClosed() {
        return null == endpointSupplier.get();
    }

    @Override
    public void close() {
        final TcpEndpoint endpoint = endpointSupplier.get();
        if (endpoint != null) {
            endpoint.close();
        }
    }
}
