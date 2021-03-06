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

package edu.uci.ics.pregelix.core.driver;

import java.io.File;
import java.io.FilenameFilter;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

import edu.uci.ics.hyracks.api.client.HyracksConnection;
import edu.uci.ics.hyracks.api.client.IHyracksClientConnection;
import edu.uci.ics.hyracks.api.exceptions.HyracksException;
import edu.uci.ics.hyracks.api.job.JobFlag;
import edu.uci.ics.hyracks.api.job.JobId;
import edu.uci.ics.hyracks.api.job.JobSpecification;
import edu.uci.ics.pregelix.api.job.PregelixJob;
import edu.uci.ics.pregelix.core.base.IDriver;
import edu.uci.ics.pregelix.core.jobgen.JobGen;
import edu.uci.ics.pregelix.core.jobgen.JobGenInnerJoin;
import edu.uci.ics.pregelix.core.jobgen.JobGenOuterJoin;
import edu.uci.ics.pregelix.core.jobgen.JobGenOuterJoinSingleSort;
import edu.uci.ics.pregelix.core.jobgen.JobGenOuterJoinSort;
import edu.uci.ics.pregelix.core.jobgen.clusterconfig.ClusterConfig;
import edu.uci.ics.pregelix.core.util.Utilities;
import edu.uci.ics.pregelix.dataflow.util.IterationUtils;

@SuppressWarnings("rawtypes")
public class Driver implements IDriver {
    private static final Log LOG = LogFactory.getLog(Driver.class);
    private JobGen jobGen;
    private boolean profiling;

    private String applicationName;
    private IHyracksClientConnection hcc;

    private Class exampleClass;

    public Driver(Class exampleClass) {
        this.exampleClass = exampleClass;
    }

    @Override
    public void runJob(PregelixJob job, String ipAddress, int port) throws HyracksException {
        runJob(job, Plan.OUTER_JOIN, ipAddress, port, false);
    }

    @Override
    public void runJob(PregelixJob job, Plan planChoice, String ipAddress, int port, boolean profiling)
            throws HyracksException {
        applicationName = exampleClass.getSimpleName() + UUID.randomUUID();
        try {
            /** add hadoop configurations */
            URL hadoopCore = job.getClass().getClassLoader().getResource("core-site.xml");
            if (hadoopCore != null) {
                job.getConfiguration().addResource(hadoopCore);
            }
            URL hadoopMapRed = job.getClass().getClassLoader().getResource("mapred-site.xml");
            if (hadoopMapRed != null) {
                job.getConfiguration().addResource(hadoopMapRed);
            }
            URL hadoopHdfs = job.getClass().getClassLoader().getResource("hdfs-site.xml");
            if (hadoopHdfs != null) {
                job.getConfiguration().addResource(hadoopHdfs);
            }
            ClusterConfig.loadClusterConfig(ipAddress, port);

            LOG.info("job started");
            long start = System.currentTimeMillis();
            long end = start;
            long time = 0;

            this.profiling = profiling;

            switch (planChoice) {
                case INNER_JOIN:
                    jobGen = new JobGenInnerJoin(job);
                    break;
                case OUTER_JOIN:
                    jobGen = new JobGenOuterJoin(job);
                    break;
                case OUTER_JOIN_SORT:
                    jobGen = new JobGenOuterJoinSort(job);
                    break;
                case OUTER_JOIN_SINGLE_SORT:
                    jobGen = new JobGenOuterJoinSingleSort(job);
                    break;
                default:
                    jobGen = new JobGenInnerJoin(job);
            }

            if (hcc == null)
                hcc = new HyracksConnection(ipAddress, port);

            URLClassLoader classLoader = (URLClassLoader) exampleClass.getClassLoader();
            List<File> jars = new ArrayList<File>();
            URL[] urls = classLoader.getURLs();
            for (URL url : urls)
                if (url.toString().endsWith(".jar"))
                    jars.add(new File(url.getPath()));
            installApplication(jars);

            start = System.currentTimeMillis();
            FileSystem dfs = FileSystem.get(job.getConfiguration());
            dfs.delete(FileOutputFormat.getOutputPath(job), true);
            runCreate(jobGen);
            runDataLoad(jobGen);
            end = System.currentTimeMillis();
            time = end - start;
            LOG.info("data loading finished " + time + "ms");
            int i = 1;
            boolean terminate = false;
            do {
                start = System.currentTimeMillis();
                runLoopBodyIteration(jobGen, i);
                end = System.currentTimeMillis();
                time = end - start;
                LOG.info("iteration " + i + " finished " + time + "ms");
                terminate = IterationUtils.readTerminationState(job.getConfiguration(), jobGen.getJobId())
                        || IterationUtils.readForceTerminationState(job.getConfiguration(), jobGen.getJobId());
                i++;
            } while (!terminate);

            start = System.currentTimeMillis();
            runHDFSWRite(jobGen);
            runCleanup(jobGen);
            end = System.currentTimeMillis();
            time = end - start;
            LOG.info("result writing finished " + time + "ms");
            LOG.info("job finished");
        } catch (Exception e) {
            throw new HyracksException(e);
        }
    }

    private void runCreate(JobGen jobGen) throws Exception {
        try {
            JobSpecification treeCreateSpec = jobGen.generateCreatingJob();
            execute(treeCreateSpec);
        } catch (Exception e) {
            throw e;
        }
    }

    private void runDataLoad(JobGen jobGen) throws Exception {
        try {
            JobSpecification bulkLoadJobSpec = jobGen.generateLoadingJob();
            execute(bulkLoadJobSpec);
        } catch (Exception e) {
            throw e;
        }
    }

    private void runLoopBodyIteration(JobGen jobGen, int iteration) throws Exception {
        try {
            JobSpecification loopBody = jobGen.generateJob(iteration);
            execute(loopBody);
        } catch (Exception e) {
            throw e;
        }
    }

    private void runHDFSWRite(JobGen jobGen) throws Exception {
        try {
            JobSpecification scanSortPrintJobSpec = jobGen.scanIndexWriteGraph();
            execute(scanSortPrintJobSpec);
        } catch (Exception e) {
            throw e;
        }
    }

    private void runCleanup(JobGen jobGen) throws Exception {
        try {
            JobSpecification[] cleanups = jobGen.generateCleanup();
            runJobArray(cleanups);
        } catch (Exception e) {
            throw e;
        }
    }

    private void runJobArray(JobSpecification[] jobs) throws Exception {
        for (JobSpecification job : jobs) {
            execute(job);
        }
    }

    private void execute(JobSpecification job) throws Exception {
        job.setUseConnectorPolicyForScheduling(false);
        JobId jobId = hcc
                .startJob(job, profiling ? EnumSet.of(JobFlag.PROFILE_RUNTIME) : EnumSet.noneOf(JobFlag.class));
        hcc.waitForCompletion(jobId);
    }

    public void installApplication(List<File> jars) throws Exception {
        Set<String> allJars = new TreeSet<String>();
        for (File jar : jars) {
            allJars.add(jar.getAbsolutePath());
        }
        long start = System.currentTimeMillis();
        File appZip = Utilities.getHyracksArchive(applicationName, allJars);
        long end = System.currentTimeMillis();
        LOG.info("jar packing finished " + (end - start) + "ms");

        start = System.currentTimeMillis();
        // TODO: Fix this step to use Yarn
        //hcc.createApplication(applicationName, appZip);
        end = System.currentTimeMillis();
        LOG.info("jar deployment finished " + (end - start) + "ms");
    }
}

class FileFilter implements FilenameFilter {
    private String ext;

    public FileFilter(String ext) {
        this.ext = "." + ext;
    }

    public boolean accept(File dir, String name) {
        return name.endsWith(ext);
    }
}
