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
                if( stop )
                    return false;

                try {
                    Thread.sleep(10);
                }
                catch( InterruptedException e ) {
                    return false;
                }
            }
        }
        return !stop;
    }

    protected static void printPerf(String kind, File file, long fileStarted, long fileMB,
                                    double perfMin, double perfMax) {
        printPerf(kind, file, fileStarted, fileMB, perfMin, perfMax, -1, -1);
    }

    protected static void printPerf(String kind, File file, long fileStarted, long fileMB,
                                    double perfMin, double perfMax, long nBlk, double pct) {
        long fileNow = System.nanoTime();
        float fileSec = (fileNow - fileStarted) / SsdSlowMark.NANO_SEC;
        float filePerfMb = fileMB / fileSec / SsdSlowMark.MB;

        SsdSlowMark.echo("              \r");
        SsdSlowMark.echo("%s: %s = %6.1f MB/s, min = %6.1f MB/s, max = %6.1f MB/s",
                         kind, file, filePerfMb, perfMin, perfMax);

        if( pct > 0 )
            SsdSlowMark.echo(" - #%s %.1f%%", nBlk, 100 * pct);
    }
}
