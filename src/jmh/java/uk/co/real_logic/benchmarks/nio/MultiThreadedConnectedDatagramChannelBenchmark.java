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
package uk.co.real_logic.benchmarks.nio;

import org.agrona.CloseHelper;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.ThreadParams;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.SECONDS)
public class MultiThreadedConnectedDatagramChannelBenchmark
{
    private static final int SOCKET_BUFFER_LENGTH = 2 * 1024 * 1024;
    private static final int DATAGRAM_LENGTH = 1024;

    @Param({ "2" })
    int sourceCount;

    @State(Scope.Thread)
    @AuxCounters(AuxCounters.Type.OPERATIONS)
    public static class ReceiveCounters
    {
        public int receiveExceptions = 0;
        public int receiveFails = 0;
        public int receiveSuccesses = 0;
    }

    @State(Scope.Thread)
    @AuxCounters(AuxCounters.Type.OPERATIONS)
    public static class WriteCounters
    {
        public int writeExceptions = 0;
        public int writeFails = 0;
        public int writeSuccesses = 0;
    }

    @State(Scope.Thread)
    public static class ThreadState
    {
        final InetSocketAddress address = new InetSocketAddress("127.0.0.1", 7777);
        final ByteBuffer receiveBuffer = ByteBuffer.allocateDirect(4096);
        DatagramChannel receiveChannel = null;
        final ByteBuffer sendBufferOne = ByteBuffer.allocateDirect(4096);
        DatagramChannel sendChannelOne = null;
        final ByteBuffer sendBufferTwo = ByteBuffer.allocateDirect(4096);
        DatagramChannel sendChannelTwo = null;

        @Setup
        public void setup(final ThreadParams threadParams, final BenchmarkParams benchmarkParams)
            throws IOException
        {
            switch (threadParams.getThreadIndex())
            {
                case 0:
                    receiveChannel = DatagramChannel.open();
                    receiveChannel.bind(address);
                    receiveChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
                    receiveChannel.setOption(StandardSocketOptions.SO_RCVBUF, SOCKET_BUFFER_LENGTH);
                    receiveChannel.configureBlocking(false);
                    break;

                case 1:
                    sendChannelOne = DatagramChannel.open();
                    sendChannelOne.connect(address);
                    sendChannelOne.setOption(StandardSocketOptions.SO_SNDBUF, SOCKET_BUFFER_LENGTH);
                    sendChannelOne.configureBlocking(false);
                    break;

                case 2:
                    sendChannelTwo = DatagramChannel.open();
                    sendChannelTwo.connect(address);
                    sendChannelTwo.setOption(StandardSocketOptions.SO_SNDBUF, SOCKET_BUFFER_LENGTH);
                    sendChannelTwo.configureBlocking(false);
                    break;
            }
        }

        @TearDown
        public void teardown()
        {
            CloseHelper.close(receiveChannel);
            CloseHelper.close(sendChannelOne);
            CloseHelper.close(sendChannelTwo);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @Group("channel")
    public void receive(final ThreadState state, final ReceiveCounters receiveCounters)
    {
        try
        {
            final ByteBuffer buffer = state.receiveBuffer;
            buffer.clear();

            final SocketAddress sourceSocket = state.receiveChannel.receive(buffer);
            if (null != sourceSocket)
            {
                receiveCounters.receiveSuccesses++;
            }
            else
            {
                receiveCounters.receiveFails++;
            }
        }
        catch (final IOException ignore)
        {
            receiveCounters.receiveExceptions++;
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @Group("channel")
    public void writeOne(final ThreadState state, final WriteCounters writeCounters)
    {
        try
        {
            final ByteBuffer buffer = state.sendBufferOne;
            buffer.clear().limit(DATAGRAM_LENGTH);

            final int bytesWritten = state.sendChannelOne.write(buffer);
            if (DATAGRAM_LENGTH == bytesWritten)
            {
                writeCounters.writeSuccesses++;
            }
            else
            {
                writeCounters.writeFails++;
            }
        }
        catch (final IOException ignore)
        {
            writeCounters.writeExceptions++;
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @Group("channel")
    public void writeTwo(final ThreadState state, final WriteCounters writeCounters)
    {
        try
        {
            final ByteBuffer buffer = state.sendBufferTwo;
            buffer.clear().limit(DATAGRAM_LENGTH);

            final int bytesWritten = state.sendChannelTwo.write(buffer);
            if (DATAGRAM_LENGTH == bytesWritten)
            {
                writeCounters.writeSuccesses++;
            }
            else
            {
                writeCounters.writeFails++;
            }
        }
        catch (final IOException ignore)
        {
            writeCounters.writeExceptions++;
        }
    }
}
