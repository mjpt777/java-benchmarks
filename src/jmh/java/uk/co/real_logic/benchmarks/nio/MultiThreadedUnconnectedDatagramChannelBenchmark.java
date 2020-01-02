/*
 * Copyright 2018-2020 Real Logic Limited.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
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

import static uk.co.real_logic.benchmarks.nio.Configuration.DATAGRAM_LENGTH;
import static uk.co.real_logic.benchmarks.nio.Configuration.SOCKET_BUFFER_LENGTH;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.SECONDS)
public class MultiThreadedUnconnectedDatagramChannelBenchmark
{
    @Param({ "2" })
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
    public static class SendCounters
    {
        public int sendException = 0;
        public int sendFail = 0;
        public int sendSuccess = 0;
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
                    receiveChannel.setOption(StandardSocketOptions.SO_RCVBUF, SOCKET_BUFFER_LENGTH * 2);
                    receiveChannel.configureBlocking(false);
                    break;

                case 1:
                    sendChannelOne = DatagramChannel.open();
                    sendChannelOne.setOption(StandardSocketOptions.SO_SNDBUF, SOCKET_BUFFER_LENGTH);
                    sendChannelOne.configureBlocking(false);
                    break;

                case 2:
                    sendChannelTwo = DatagramChannel.open();
                    sendChannelTwo.setOption(StandardSocketOptions.SO_SNDBUF, SOCKET_BUFFER_LENGTH);
                    sendChannelTwo.configureBlocking(false);
                    break;
            }
        }

        @TearDown
        public void tearDown()
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
    public void sendOne(final ThreadState state, final SendCounters sendCounters)
    {
        try
        {
            final ByteBuffer buffer = state.sendBufferOne;
            buffer.clear().limit(DATAGRAM_LENGTH);

            final int bytesWritten = state.sendChannelOne.send(buffer, state.address);
            if (DATAGRAM_LENGTH == bytesWritten)
            {
                sendCounters.sendSuccess++;
            }
            else
            {
                sendCounters.sendFail++;
            }
        }
        catch (final IOException ignore)
        {
            sendCounters.sendException++;
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @Group("channel")
    public void sendTwo(final ThreadState state, final SendCounters sendCounters)
    {
        try
        {
            final ByteBuffer buffer = state.sendBufferTwo;
            buffer.clear().limit(DATAGRAM_LENGTH);

            final int bytesWritten = state.sendChannelTwo.send(buffer, state.address);
            if (DATAGRAM_LENGTH == bytesWritten)
            {
                sendCounters.sendSuccess++;
            }
            else
            {
                sendCounters.sendFail++;
            }
        }
        catch (final IOException ignore)
        {
            sendCounters.sendException++;
        }
    }
}
