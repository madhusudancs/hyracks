/*
 * Copyright 2009-2010 by The Regents of the University of California
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * you may obtain a copy of the License from
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.uci.ics.hyracks.dataflow.std.connectors;

import java.nio.ByteBuffer;

import edu.uci.ics.hyracks.api.comm.IConnectionDemultiplexer;
import edu.uci.ics.hyracks.api.comm.IFrameReader;
import edu.uci.ics.hyracks.api.comm.IFrameWriter;
import edu.uci.ics.hyracks.api.context.IHyracksContext;
import edu.uci.ics.hyracks.api.dataflow.IEndpointDataWriterFactory;
import edu.uci.ics.hyracks.api.dataflow.value.RecordDescriptor;
import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.api.job.JobSpecification;
import edu.uci.ics.hyracks.dataflow.common.comm.NonDeterministicFrameReader;
import edu.uci.ics.hyracks.dataflow.std.base.AbstractConnectorDescriptor;

public class MToNReplicatingConnectorDescriptor extends AbstractConnectorDescriptor {
    public MToNReplicatingConnectorDescriptor(JobSpecification spec) {
        super(spec);
    }

    private static final long serialVersionUID = 1L;

    @Override
    public IFrameWriter createSendSideWriter(IHyracksContext ctx, RecordDescriptor recordDesc, IEndpointDataWriterFactory edwFactory,
        int index, int nProducerPartitions, int nConsumerPartitions) throws HyracksDataException {
        final IFrameWriter[] epWriters = new IFrameWriter[nConsumerPartitions];
        for (int i = 0; i < nConsumerPartitions; ++i) {
            epWriters[i] = edwFactory.createFrameWriter(i);
        }
        return new IFrameWriter() {
            @Override
            public void nextFrame(ByteBuffer buffer) throws HyracksDataException {
                buffer.mark();
                for (int i = 0; i < epWriters.length; ++i) {
                    if (i != 0) {
                        buffer.reset();
                    }
                    epWriters[i].nextFrame(buffer);
                }
            }

            @Override
            public void close() throws HyracksDataException {
                for (int i = 0; i < epWriters.length; ++i) {
                    epWriters[i].close();
                }
            }

            @Override
            public void open() throws HyracksDataException {
                for (int i = 0; i < epWriters.length; ++i) {
                    epWriters[i].open();
                }
            }
        };
    }

    @Override
    public IFrameReader createReceiveSideReader(IHyracksContext ctx, RecordDescriptor recordDesc, IConnectionDemultiplexer demux,
        int index, int nProducerPartitions, int nConsumerPartitions) throws HyracksDataException {
        return new NonDeterministicFrameReader(ctx, demux);
    }
}