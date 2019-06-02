package tools4free.ssm;

import org.junit.Test;

import java.io.File;

import static tools4free.ssm.SsdSlowMark.versionInfo;

public class ResultsWriterTest {

    // @Test
    public void writeSummary() {
        File rptDir = new File("./_testResults_01");
        ResultsWriter rw = new ResultsWriter(new Config());

        rw.writeSummary(versionInfo(), rptDir, null, null);
    }
}