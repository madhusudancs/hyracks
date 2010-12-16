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
package edu.uci.ics.hyracks.control.nc;

import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.uci.ics.hyracks.api.comm.Endpoint;
import edu.uci.ics.hyracks.api.dataflow.OperatorDescriptorId;
import edu.uci.ics.hyracks.api.dataflow.OperatorInstanceId;
import edu.uci.ics.hyracks.control.nc.job.profiling.CounterContext;
import edu.uci.ics.hyracks.control.nc.runtime.OperatorRunnable;

public class Stagelet {
    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(Stagelet.class.getName());

    private final Joblet joblet;

    private final UUID stageId;

    private final int attempt;

    private final Map<OperatorInstanceId, OperatorRunnable> honMap;

    private final CounterContext stageletCounterContext;

    private List<Endpoint> endpointList;

    private boolean started;

    private volatile boolean abort;

    private final Set<OperatorInstanceId> pendingOperators;

    public Stagelet(Joblet joblet, UUID stageId, int attempt, String nodeId) throws RemoteException {
        this.joblet = joblet;
        this.stageId = stageId;
        this.attempt = attempt;
        pendingOperators = new HashSet<OperatorInstanceId>();
        started = false;
        honMap = new HashMap<OperatorInstanceId, OperatorRunnable>();
        stageletCounterContext = new CounterContext(joblet.getJobId() + "." + stageId + "." + nodeId);
    }

    public void setOperator(OperatorDescriptorId odId, int partition, OperatorRunnable hon) {
        honMap.put(new OperatorInstanceId(odId, partition), hon);
    }

    public Map<OperatorInstanceId, OperatorRunnable> getOperatorMap() {
        return honMap;
    }

    public CounterContext getStageletCounterContext() {
        return stageletCounterContext;
    }

    public void setEndpointList(List<Endpoint> endpointList) {
        this.endpointList = endpointList;
    }

    public List<Endpoint> getEndpointList() {
        return endpointList;
    }

    public synchronized void start() throws Exception {
        if (started) {
            throw new Exception("Joblet already started");
        }
        started = true;
        notifyAll();
    }

    public synchronized void abort() {
        this.abort = true;
        for (OperatorRunnable r : honMap.values()) {
            r.abort();
        }
    }

    public void installRunnable(final OperatorInstanceId opIId) {
        pendingOperators.add(opIId);
        final OperatorRunnable hon = honMap.get(opIId);
        joblet.getExecutor().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    waitUntilStarted();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    return;
                }
                if (abort) {
                    return;
                }
                try {
                    LOGGER.log(Level.INFO, joblet.getJobId() + ":" + stageId + ":" + opIId.getOperatorId() + ":"
                            + opIId.getPartition() + "(" + hon + ")" + ": STARTED");
                    hon.run();
                    LOGGER.log(Level.INFO, joblet.getJobId() + ":" + stageId + ":" + opIId.getOperatorId() + ":"
                            + opIId.getPartition() + "(" + hon + ")" + ": FINISHED");
                    notifyOperatorCompletion(opIId);
                } catch (Exception e) {
                    LOGGER.log(Level.INFO, joblet.getJobId() + ":" + stageId + ":" + opIId.getOperatorId() + ":"
                            + opIId.getPartition() + "(" + hon + ")" + ": ABORTED");
                    e.printStackTrace();
                    // DO NOT UNCOMMENT THE FOLLOWING LINE.
                    // The failure of an operator triggers a re-attempt of the job at the CC. If the failure was non-transient,
                    // this will lead to an infinite number of attempts since there is no upper bount yet on how many times
                    // a job is retried.

                    // notifyOperatorFailure(opIId);
                }
            }
        });
    }

    protected synchronized void notifyOperatorCompletion(OperatorInstanceId opIId) {
        pendingOperators.remove(opIId);
        if (pendingOperators.isEmpty()) {
            try {
                Map<String, Long> stats = new TreeMap<String, Long>();
                dumpProfile(stats);
                joblet.notifyStageletComplete(stageId, attempt, stats);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    protected synchronized void notifyOperatorFailure(OperatorInstanceId opIId) {
        abort();
        try {
            joblet.notifyStageletFailed(stageId, attempt);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private synchronized void waitUntilStarted() throws InterruptedException {
        while (!started && !abort) {
            wait();
        }
    }

    public void dumpProfile(Map<String, Long> counterDump) {
        stageletCounterContext.dump(counterDump);
    }
}