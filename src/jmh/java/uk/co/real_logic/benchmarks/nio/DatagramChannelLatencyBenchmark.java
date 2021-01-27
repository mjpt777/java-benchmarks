/*
 * Copyright 2018-2021 Real Logic Limited.
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
import org.agrona.hints.ThreadHints;
import org.openjdk.jmh.annotations.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class DatagramChannelLatencyBenchmark
{
    static final int DATAGRAM_LENGTH = 8;

    final CountDownLatch echoReady = new CountDownLatch(1);
    final AtomicBoolean running = new AtomicBoolean(true);
    Thread echoThread;
    final InetSocketAddress address = new InetSocketAddress("127.0.0.1", 7777);
    DatagramChannel receiveChannel = null;
    final ByteBuffer sendBuffer = ByteBuffer.allocateDirect(4096);
    final DatagramChannel sendChannels[] = new DatagramChannel[2];
    int sendChannelIndex = 0;

    @Setup(Level.Trial)
    public void setup() throws Exception
    {
        echoThread = new Thread(() ->
        {
            try
            {
                receiveChannel = DatagramChannel.open();
                receiveChannel.bind(address);
                receiveChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
                receiveChannel.configureBlocking(false);
                final ByteBuffer buffer = ByteBuffer.allocateDirect(4096);

                echoReady.countDown();

                while (running.get())
                {
                    buffer.clear();
                    final SocketAddress socketAddress = receiveChannel.receive(buffer);
                    if (null == socketAddress)
                    {
                        continue;
                    }

                    buffer.flip();
                    if (DATAGRAM_LENGTH != receiveChannel.send(buffer, socketAddress))
                    {
                        throw new IllegalStateException("failed to send response");
                    }
                }
            }
            catch (final IOException ex)
            {
                throw new RuntimeException(ex);
            }
        });

        echoThread.setName("echo-thread");
        echoThread.setDaemon(true);
        echoThread.start();

        for (int i = 0; i < sendChannels.length; i++)
        {
            sendChannels[i] = DatagramChannel.open();
            sendChannels[i].connect(address);
            sendChannels[i].configureBlocking(false);
        }

        echoReady.await();
    }

    @TearDown(Level.Trial)
    public void tearDown() throws Exception
    {
        running.set(false);
        echoThread.join();

        CloseHelper.close(receiveChannel);
        for (final DatagramChannel channel : sendChannels)
        {
            CloseHelper.close(channel);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    public void singleSourceRoundTrip() throws IOException
    {
        final DatagramChannel channel = sendChannels[sendChannelIndex];
        sendBuffer.clear().limit(DATAGRAM_LENGTH);
        while (DATAGRAM_LENGTH != channel.write(sendBuffer))
        {
            ThreadHints.onSpinWait();
        }

        sendBuffer.clear();
        while (null == channel.receive(sendBuffer))
        {
            ThreadHints.onSpinWait();
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    public void dualSourceRoundTrip() throws IOException
    {
        final DatagramChannel channel = sendChannels[sendChannelIndex];
        sendBuffer.clear().limit(DATAGRAM_LENGTH);
        while (DATAGRAM_LENGTH != channel.write(sendBuffer))
        {
            ThreadHints.onSpinWait();
        }

        sendBuffer.clear();
        while (null == channel.receive(sendBuffer))
        {
            ThreadHints.onSpinWait();
        }

        sendChannelIndex = sendChannelIndex == 0 ? 1 : 0;
    }
}
