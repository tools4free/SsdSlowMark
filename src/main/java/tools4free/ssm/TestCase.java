package tools4free.ssm;

import java.io.File;

import static tools4free.ssm.SsdSlowMark.si;

public class TestCase extends Thread  {
    String testKind;
    Config config;
    TestCase waitFor;
    boolean stop;

    File root;
    long freeSpaceAtStart;
    long totalSpace;

    boolean finished;
    float[] blocks;
    int cBlocks;

    long startTime;
    long stopTime;
    long elapsedMs;

    String diskModel;
    float dataSizeGb;
    ResultsWriter.Pctls pctls;

    public TestCase(String testKind, Config config, File root, TestCase waitFor) {
        super(testKind);
        this.testKind = testKind;
        this.config = config;
        this.root = root;
        this.waitFor = waitFor;

        freeSpaceAtStart = SsdSlowMark.si.diskFreeSpace(root);
        totalSpace = SsdSlowMark.si.diskTotalSpace(root);
        diskModel = si.getDriveModel(root, "Unknown Model");
    }

    boolean doWaitFor() {
        if( waitFor != null ) {
            while( !waitFor.finished ) {
                try {
                    Thread.sleep(10);
                }
                catch( InterruptedException e ) {
                    return false;
                }
            }
        }
        return true;
    }

}
