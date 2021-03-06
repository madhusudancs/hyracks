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
package edu.uci.ics.hyracks.dataflow.std.group.aggregators;

import java.io.DataOutput;

import edu.uci.ics.hyracks.api.comm.IFrameTupleAccessor;
import edu.uci.ics.hyracks.api.context.IHyracksTaskContext;
import edu.uci.ics.hyracks.api.dataflow.value.RecordDescriptor;
import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.dataflow.common.comm.io.ArrayTupleBuilder;
import edu.uci.ics.hyracks.dataflow.std.group.AggregateState;
import edu.uci.ics.hyracks.dataflow.std.group.IAggregatorDescriptor;
import edu.uci.ics.hyracks.dataflow.std.group.IAggregatorDescriptorFactory;
import edu.uci.ics.hyracks.dataflow.std.group.IFieldAggregateDescriptor;
import edu.uci.ics.hyracks.dataflow.std.group.IFieldAggregateDescriptorFactory;

/**
 *
 */
public class MultiFieldsAggregatorFactory implements IAggregatorDescriptorFactory {

    private static final long serialVersionUID = 1L;
    private final IFieldAggregateDescriptorFactory[] aggregatorFactories;
    private int[] keys;

    public MultiFieldsAggregatorFactory(int[] keys, IFieldAggregateDescriptorFactory[] aggregatorFactories) {
        this.keys = keys;
        this.aggregatorFactories = aggregatorFactories;
    }

    public MultiFieldsAggregatorFactory(IFieldAggregateDescriptorFactory[] aggregatorFactories) {
        this.aggregatorFactories = aggregatorFactories;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * edu.uci.ics.hyracks.dataflow.std.aggregations.IAggregatorDescriptorFactory
     * #createAggregator(edu.uci.ics.hyracks.api.context.IHyracksTaskContext,
     * edu.uci.ics.hyracks.api.dataflow.value.RecordDescriptor,
     * edu.uci.ics.hyracks.api.dataflow.value.RecordDescriptor)
     */
    @Override
    public IAggregatorDescriptor createAggregator(IHyracksTaskContext ctx, RecordDescriptor inRecordDescriptor,
            RecordDescriptor outRecordDescriptor, final int[] keyFields, final int[] keyFieldsInPartialResults)
            throws HyracksDataException {

        final IFieldAggregateDescriptor[] aggregators = new IFieldAggregateDescriptor[aggregatorFactories.length];
        for (int i = 0; i < aggregators.length; i++) {
            aggregators[i] = aggregatorFactories[i].createAggregator(ctx, inRecordDescriptor, outRecordDescriptor);
        }

        if (this.keys == null) {
            this.keys = keyFields;
        }

        return new IAggregatorDescriptor() {

            @Override
            public void reset() {
                for (int i = 0; i < aggregators.length; i++) {
                    aggregators[i].reset();
                }
            }

            @Override
            public void outputPartialResult(ArrayTupleBuilder tupleBuilder, IFrameTupleAccessor accessor, int tIndex,
                    AggregateState state) throws HyracksDataException {
                DataOutput dos = tupleBuilder.getDataOutput();

                int tupleOffset = accessor.getTupleStartOffset(tIndex);
                for (int i = 0; i < aggregators.length; i++) {
                    int fieldOffset = accessor.getFieldStartOffset(tIndex, keys.length + i);
                    aggregators[i].outputPartialResult(dos, accessor.getBuffer().array(),
                            fieldOffset + accessor.getFieldSlotsLength() + tupleOffset,
                            ((AggregateState[]) state.state)[i]);
                    tupleBuilder.addFieldEndOffset();
                }

            }

            @Override
            public void outputFinalResult(ArrayTupleBuilder tupleBuilder, IFrameTupleAccessor accessor, int tIndex,
                    AggregateState state) throws HyracksDataException {
                DataOutput dos = tupleBuilder.getDataOutput();

                int tupleOffset = accessor.getTupleStartOffset(tIndex);
                for (int i = 0; i < aggregators.length; i++) {
                    if (aggregators[i].needsBinaryState()) {
                        int fieldOffset = accessor.getFieldStartOffset(tIndex, keys.length + i);
                        aggregators[i].outputFinalResult(dos, accessor.getBuffer().array(),
                                tupleOffset + accessor.getFieldSlotsLength() + fieldOffset,
                                ((AggregateState[]) state.state)[i]);
                    } else {
                        aggregators[i].outputFinalResult(dos, null, 0, ((AggregateState[]) state.state)[i]);
                    }
                    tupleBuilder.addFieldEndOffset();
                }
            }

            @Override
            public void init(ArrayTupleBuilder tupleBuilder, IFrameTupleAccessor accessor, int tIndex,
                    AggregateState state) throws HyracksDataException {
                DataOutput dos = tupleBuilder.getDataOutput();

                for (int i = 0; i < aggregators.length; i++) {
                    aggregators[i].init(accessor, tIndex, dos, ((AggregateState[]) state.state)[i]);
                    if (aggregators[i].needsBinaryState()) {
                        tupleBuilder.addFieldEndOffset();
                    }
                }
            }

            @Override
            public AggregateState createAggregateStates() {
                AggregateState[] states = new AggregateState[aggregators.length];
                for (int i = 0; i < states.length; i++) {
                    states[i] = aggregators[i].createState();
                }
                return new AggregateState(states);
            }

            @Override
            public void close() {
                for (int i = 0; i < aggregators.length; i++) {
                    aggregators[i].close();
                }
            }

            @Override
            public void aggregate(IFrameTupleAccessor accessor, int tIndex, IFrameTupleAccessor stateAccessor,
                    int stateTupleIndex, AggregateState state) throws HyracksDataException {
                if (stateAccessor != null) {
                    int stateTupleOffset = stateAccessor.getTupleStartOffset(stateTupleIndex);
                    int fieldIndex = 0;
                    for (int i = 0; i < aggregators.length; i++) {
                        if (aggregators[i].needsBinaryState()) {
                            int stateFieldOffset = stateAccessor.getFieldStartOffset(stateTupleIndex, keys.length
                                    + fieldIndex);
                            aggregators[i].aggregate(accessor, tIndex, stateAccessor.getBuffer().array(),
                                    stateTupleOffset + stateAccessor.getFieldSlotsLength() + stateFieldOffset,
                                    ((AggregateState[]) state.state)[i]);
                            fieldIndex++;
                        } else {
                            aggregators[i].aggregate(accessor, tIndex, null, 0, ((AggregateState[]) state.state)[i]);
                        }
                    }
                } else {
                    for (int i = 0; i < aggregators.length; i++) {
                        aggregators[i].aggregate(accessor, tIndex, null, 0, ((AggregateState[]) state.state)[i]);
                    }
                }
            }
        };
    }
}
