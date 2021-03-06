package edu.uci.ics.hyracks.algebricks.runtime.base;

import java.io.Serializable;

import edu.uci.ics.hyracks.algebricks.common.exceptions.AlgebricksException;

public interface ICopySerializableAggregateFunctionFactory extends Serializable {
    public ICopySerializableAggregateFunction createAggregateFunction() throws AlgebricksException;
}
