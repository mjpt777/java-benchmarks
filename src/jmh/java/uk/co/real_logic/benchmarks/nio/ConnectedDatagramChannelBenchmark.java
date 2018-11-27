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
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.TimeUnit;

import static uk.co.real_logic.benchmarks.nio.Configuration.DATAGRAM_LENGTH;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.SECONDS)
public class ConnectedDatagramChannelBenchmark
{
    @Param({ "1", "2" })
    int sourceCount;

    @State(Scope.Thread)
    @AuxCounters(AuxCounters.Type.OPERATIONS)
    public static class ReceiveCounters
    {
        public int receiveException = 0;
        public int receiveFail = 0;
        public int receiveSuccess = 0;
    }

    @State(Scope.Thread)
    @AuxCounters(AuxCounters.Type.OPERATIONS)
    public static class WriteCounters
    {
        public int writeException = 0;
        public int writeFail = 0;
        public int writeSuccess = 0;
    }

    @State(Scope.Thread)
    public static class ThreadState
    {
        final InetSocketAddress address = new InetSocketAddress("127.0.0.1", 7777);
        final ByteBuffer receiveBuffer = ByteBuffer.allocateDirect(4096);
        DatagramChannel receiveChannel = null;
        final ByteBuffer sendBuffer = ByteBuffer.allocateDirect(4096);
        DatagramChannel[] sendChannels = new DatagramChannel[0];
        int sendChannelIndex = 0;

        @Setup
        public void setup(final ThreadParams threadParams, final BenchmarkParams benchmarkParams)
            throws IOException
        {
            if (threadParams.getThreadIndex() == 0)
            {
                receiveChannel = DatagramChannel.open();
                receiveChannel.bind(address);
                receiveChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
                receiveChannel.setOption(StandardSocketOptions.SO_RCVBUF, Configuration.SOCKET_BUFFER_LENGTH * 2);
                receiveChannel.configureBlocking(false);
            }
            else
            {
                final int sourceCount = Integer.parseInt(benchmarkParams.getParam("sourceCount"));
                sendChannels = new DatagramChannel[sourceCount];

                for (int i = 0; i < sourceCount; i++)
                {
                    sendChannels[i] = DatagramChannel.open();
                    sendChannels[i].connect(address);
                    sendChannels[i].setOption(StandardSocketOptions.SO_SNDBUF, Configuration.SOCKET_BUFFER_LENGTH);
                    sendChannels[i].configureBlocking(false);
                }
            }
        }

        @TearDown
        public void tearDown()
        {
            CloseHelper.close(receiveChannel);
            for (final DatagramChannel channel : sendChannels)
            {
                CloseHelper.close(channel);
            }
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
            if (null != sourceSocket && buffer.position() == DATAGRAM_LENGTH)
            {
                receiveCounters.receiveSuccess++;
            }
            else
            {
                receiveCounters.receiveFail++;
            }
        }
        catch (final IOException ignore)
        {
            receiveCounters.receiveException++;
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @Group("channel")
    public void write(final ThreadState state, final WriteCounters writeCounters)
    {
        try
        {
            final DatagramChannel sendChannel = state.sendChannels[state.sendChannelIndex];
            final ByteBuffer buffer = state.sendBuffer;
            buffer.clear().limit(DATAGRAM_LENGTH);

            final int bytesWritten = sendChannel.write(buffer);
            if (DATAGRAM_LENGTH == bytesWritten)
            {
                writeCounters.writeSuccess++;
                if (++state.sendChannelIndex >= state.sendChannels.length)
                {
                    state.sendChannelIndex = 0;
                }
            }
            else
            {
                writeCounters.writeFail++;
            }
        }
        catch (final IOException ignore)
        {
            writeCounters.writeException++;
        }
    }
}
