/*
 * Copyright 2018 Real Logic Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.co.real_logic.benchmarks;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.ThreadParams;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.SECONDS)
public class UnconnectedDatagramChannelBenchmark
{
    private static final int SOCKET_BUFFER_LENGTH = 2 * 1024 * 1024;
    private static final int DATAGRAM_LENGTH = 64;

    @Param({ "1", "2" })
    int sourceCount;

    @State(Scope.Thread)
    @AuxCounters(AuxCounters.Type.OPERATIONS)
    public static class ReceiveCounters
    {
        public int failedReceives = 0;
        public int receiveExceptions = 0;
    }

    @State(Scope.Thread)
    @AuxCounters(AuxCounters.Type.OPERATIONS)
    public static class SendCounters
    {
        public int failedSends = 0;
        public int sendExceptions = 0;
    }

    @State(Scope.Thread)
    public static class ThreadState
    {
        final InetSocketAddress address = new InetSocketAddress("127.0.0.1", 7777);
        final ByteBuffer receiveBuffer = ByteBuffer.allocateDirect(4096);
        DatagramChannel receiveChannel = null;
        final ByteBuffer sendBuffer = ByteBuffer.allocateDirect(4096);
        DatagramChannel sendChannels[] = null;
        int sendChannelIndex = 0;

        @Setup
        public void setup(final ThreadParams threadParams, final BenchmarkParams benchmarkParams)
        {
            try
            {
                if (threadParams.getThreadIndex() == 0)
                {
                    receiveChannel = DatagramChannel.open();
                    receiveChannel.bind(address);
                    receiveChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
                    receiveChannel.setOption(StandardSocketOptions.SO_RCVBUF, SOCKET_BUFFER_LENGTH);
                    receiveChannel.configureBlocking(false);
                }
                else
                {
                    final int sourceCount = Integer.parseInt(benchmarkParams.getParam("sourceCount"));
                    sendChannels = new DatagramChannel[sourceCount];

                    for (int i = 0; i < sourceCount; i++)
                    {
                        sendChannels[i] = DatagramChannel.open();
                        sendChannels[i].setOption(StandardSocketOptions.SO_SNDBUF, SOCKET_BUFFER_LENGTH);
                        sendChannels[i].configureBlocking(false);
                    }
                }
            }
            catch (final IOException ex)
            {
                throw new RuntimeException(ex);
            }
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @Group("channel")
    public SocketAddress receive(final ThreadState state, final ReceiveCounters receiveCounters)
    {
        try
        {
            final ByteBuffer buffer = state.receiveBuffer;
            buffer.clear();

            final SocketAddress sourceSocket = state.receiveChannel.receive(buffer);
            if (null == sourceSocket)
            {
                receiveCounters.failedReceives++;
            }

            return sourceSocket;
        }
        catch (final IOException ignore)
        {
            receiveCounters.receiveExceptions++;
            return null;
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @Group("channel")
    public void send(final ThreadState state, final SendCounters sendCounters)
    {
        try
        {
            final DatagramChannel sendChannel = state.sendChannels[state.sendChannelIndex];
            final ByteBuffer buffer = state.sendBuffer;
            buffer.clear().position(DATAGRAM_LENGTH);

            final int bytesWritten = sendChannel.send(buffer, state.address);
            if (0 == bytesWritten)
            {
                sendCounters.failedSends++;
            }
            else
            {
                if (++state.sendChannelIndex >= state.sendChannels.length)
                {
                    state.sendChannelIndex = 0;
                }
            }
        }
        catch (final IOException ignore)
        {
            sendCounters.sendExceptions++;
        }
    }
}
