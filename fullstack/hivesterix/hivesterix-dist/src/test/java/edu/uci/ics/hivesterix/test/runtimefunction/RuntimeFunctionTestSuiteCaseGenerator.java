package edu.uci.ics.hivesterix.test.runtimefunction;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;

import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.ql.Driver;
import org.junit.Test;

import edu.uci.ics.hivesterix.common.config.ConfUtil;
import edu.uci.ics.hivesterix.test.base.AbstractHivesterixTestCase;

public class RuntimeFunctionTestSuiteCaseGenerator extends AbstractHivesterixTestCase {
    private File resultFile;
    private FileSystem dfs;

    RuntimeFunctionTestSuiteCaseGenerator(File queryFile, File resultFile) {
        super("testRuntimeFunction", queryFile);
        this.queryFile = queryFile;
        this.resultFile = resultFile;
    }

    @Test
    public void testRuntimeFunction() throws Exception {
        StringBuilder queryString = new StringBuilder();
        readFileToString(queryFile, queryString);
        String[] queries = queryString.toString().split(";");
        StringWriter sw = new StringWriter();

        HiveConf hconf = ConfUtil.getHiveConf();
        Driver driver = new Driver(hconf, new PrintWriter(sw));
        driver.init();

        dfs = FileSystem.get(ConfUtil.getJobConf());

        int i = 0;
        for (String query : queries) {
            if (i == queries.length - 1)
                break;
            driver.run(query);
            driver.clear();
            i++;
        }

        String warehouse = hconf.get("hive.metastore.warehouse.dir");
        String tableName = removeExt(resultFile.getName());
        String directory = warehouse + "/" + tableName + "/";
        String localDirectory = "tmp";

        FileStatus[] files = dfs.listStatus(new Path(directory));
        FileSystem lfs = null;
        if (files == null) {
            lfs = FileSystem.getLocal(ConfUtil.getJobConf());
            files = lfs.listStatus(new Path(directory));
        }

        File resultDirectory = new File(localDirectory + "/" + tableName);
        deleteDir(resultDirectory);
        resultDirectory.mkdir();

        for (FileStatus fs : files) {
            Path src = fs.getPath();
            if (src.getName().indexOf("crc") >= 0)
                continue;

            String destStr = localDirectory + "/" + tableName + "/" + src.getName();
            Path dest = new Path(destStr);
            if (lfs != null) {
                lfs.copyToLocalFile(src, dest);
                dfs.copyFromLocalFile(dest, new Path(directory));
            } else
                dfs.copyToLocalFile(src, dest);
        }

        File[] rFiles = resultDirectory.listFiles();
        StringBuilder sb = new StringBuilder();
        for (File r : rFiles) {
            if (r.getName().indexOf("crc") >= 0)
                continue;
            readFileToString(r, sb);
        }
        deleteDir(resultDirectory);

        writeStringToFile(resultFile, sb);
    }

    private void deleteDir(File resultDirectory) {
        if (resultDirectory.exists()) {
            File[] rFiles = resultDirectory.listFiles();
            for (File r : rFiles)
                r.delete();
            resultDirectory.delete();
        }
    }
}
