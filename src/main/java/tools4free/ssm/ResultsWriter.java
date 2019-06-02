package tools4free.ssm;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.*;
import java.nio.file.Files;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.Math.abs;
import static java.lang.Math.max;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.Instant.ofEpochMilli;
import static java.util.Locale.US;
import static tools4free.ssm.SsdSlowMark.*;

public class ResultsWriter {
    public final static DateTimeFormatter DTF_YMD_HMS = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    Config config;

    public ResultsWriter(Config config) {
        this.config = config;
    }

    public File writeTestResults(TestCase testCase) {
        String testKind = testCase.testKind;
        File tcRoot = testCase.root;
        int cBlocks = testCase.cBlocks;
        float[] blocks = testCase.blocks;
        String diskModel = si.getDriveModel(tcRoot, "Unknown Model");
        testCase.diskModel = diskModel;

        testCase.dataSizeGb = (float)(cBlocks * config.bs * (double)KB / GB);
        int iDataSizeGb = (int)testCase.dataSizeGb;

        String baseFileName = diskModel.replace(' ', '_') + "_(" + testKind + "-" + iDataSizeGb + "gb)";
        ZonedDateTime now = ZonedDateTime.ofInstant(ofEpochMilli(testStart), ZoneId.systemDefault());
        File rptDir = new File(new File(config.rpt), diskModel.replace(' ', '_') + "_" + DTF_YMD_HMS.format(now));
        rptDir.mkdirs();

        int maxWidth = config.iw - config.ip * 2;
        int chunkWidth = max(1, (cBlocks + maxWidth - 1) / maxWidth);
        int cChunks = (cBlocks + chunkWidth - 1) / chunkWidth;
        float blockSizeMb = config.bs / (float)1024;
        float offsetMb = 0;
        float allMin = Float.MAX_VALUE, allMax = 0;
        List<Chunk> chunks = new ArrayList<>(cChunks);

        File file = new File(rptDir, baseFileName + "_Chunks.csv");
        try( OutputStream fos = new FileOutputStream(file) ) {
            try( OutputStreamWriter wr = new OutputStreamWriter(fos, UTF_8) ) {
                wr.write("N,MB,min,avgMin,avg,avgMax,max\n");

                for( int n = 0, nBlc = 0; nBlc < cBlocks; nBlc++ ) {
                    float perf = blocks[nBlc];
                    float min, sum, max;
                    int nBlc0 = nBlc;

                    min = sum = max = perf;
                    for( int j = 1; j < chunkWidth && nBlc < cBlocks; j++, nBlc++ ) {
                        perf = blocks[nBlc];
                        min = Math.min(min, perf);
                        max = Math.max(max, perf);
                        sum += perf;
                    }

                    float sumMin = 0, cMin = 0, sumMax = 0, cMax = 0;
                    float avg = sum / chunkWidth;

                    for( int j = 1; j < chunkWidth && nBlc < cBlocks; j++ ) {
                        perf = blocks[nBlc0 + j];
                        if( perf > avg ) {
                            sumMax += perf;
                            cMax++;
                        }
                        if( perf < avg ) {
                            sumMin += perf;
                            cMin++;
                        }
                    }

                    float avgMin = (cMin == 0) ? avg : sumMin / cMin;
                    float avgMax = (cMax == 0) ? avg : sumMax / cMax;

                    String line = String.format(US,
                            "%s,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f\n",
                            ++n, offsetMb, min, avgMin, avg, avgMax, max);

                    wr.write(line);
                    chunks.add(new Chunk(offsetMb, min, avgMin, avg, avgMax, max));

                    allMin = Math.min(allMin, min);
                    allMax = Math.max(allMax, max);
                    offsetMb += blockSizeMb * chunkWidth;
                }
            }

            cChunks = chunks.size();
            echo("CSV report: " + file.getAbsolutePath());
        }
        catch( Exception e ) {
            System.err.println("Failed to write to " + file);
            e.printStackTrace();
        }

        // collect average performance at 1GB step
        File fileAvg = new File(rptDir, baseFileName + "_Average.csv");
        buildAverage(diskModel, fileAvg, chunks);

        // generate image
        Chart chart = new Chart(config);

        chart.generateFor(chunks, Chart.Kind.Spread, Color.DARK_GRAY, true);
        chart.renderTestSummary(testCase);
        testCase.pctls = chart.pctls;

        // Save as PNG
        File chartFile = new File(rptDir, baseFileName + "_Chart.png");
        try {
            ImageIO.write(chart.img, "png", chartFile);
            echo("CSV report: " + chartFile.getAbsolutePath());
            // Desktop.getDesktop().open(chartFile);
        }
        catch( IOException e ) {
            System.err.println("Failed to write to " + chartFile);
            e.printStackTrace();
        }

        return rptDir;
    }

    public void writeSummary(String versionInfo, File rptDir, TestCase readTest, TestCase writeTest) {
        Map<String,TestAverages> avrgs = ResultsAggregator.loadAverages(rptDir);
        Chart chart = new Chart(config);

        for( Map.Entry<String, TestAverages> entry : avrgs.entrySet() ) {
            TestAverages ta = entry.getValue();
            chart.prepareFor(ta.chunks);
        }

        chart.prepared();
        chart.allMax = (float)(Math.ceil(chart.allMax * 1.1 / 100) * 100);
        chart.allMin = 0;

        int nLine = 0;
        String diskModel = "[Unknown Model]";
        TestAverages taRead = null;
        TestAverages taWrite = null;

        for( Map.Entry<String, TestAverages> entry : avrgs.entrySet() ) {
            TestAverages ta = entry.getValue();
            Color lineColor = ta.testKind.toLowerCase().contains("write") ? Color.RED : Color.green.darker();

            diskModel = ta.diskModel;
            if( ta.testKind.toLowerCase().contains("write") ) {
                taWrite = ta;
                lineColor = Color.RED;
            }
            else {
                taRead = ta;
                lineColor = Color.GREEN.darker();
            }

            if( chart.generateFor(ta.chunks, Chart.Kind.Line, lineColor, false) ) {
                ta.pctls = chart.pctls;
                chart.addLineMarker(nLine, ta.testKind, lineColor);
                nLine++;
            }
        }

        File chartFile = new File(rptDir, diskModel.replace(' ', '_') + "_(Read-Write)_Averages.png");

        if( chart.img != null ) {
            chart.addChartTitle("Summary", diskModel);

            // Save as PNG
            try {
                ImageIO.write(chart.img, "png", chartFile);
                echo("ReadWrite Averages: " + chartFile.getAbsolutePath());
                // Desktop.getDesktop().open(chartFile);
            }
            catch( IOException e ) {
                System.err.println("Failed to write to " + chartFile + ": " + e.getMessage());
            }
        }

        String html = resourceAsString(this, "report-template.html");

        html = html.replace("{disk-model}", diskModel);
        html = html.replace("{test-size}", String.format(US, "%.0f", chart.dataSizeGb));
        html = html.replace("{version-info}", versionInfo);

        if( readTest != null )
            html = html.replace("{read-summary}", String.valueOf(readTest.pctls));
        else if( taRead != null )
            html = html.replace("{read-summary}", String.valueOf(taRead.pctls));
        else
            html = html.replace("{read-summary}", "[No read test]");

        if( writeTest != null )
            html = html.replace("{write-summary}", String.valueOf(writeTest.pctls));
        else if( taWrite != null )
            html = html.replace("{write-summary}", String.valueOf(taWrite.pctls));
        else
            html = html.replace("{write-summary}", "[No write test]");

        html = html.replace("{read-write-chart}", String.format(US, "<img src='%s'/>", chartFile.getName()));
        html = html.replace("{read-chart}",
                taRead == null ? ""
                               : String.format(US, "<img src='%s'/>", taRead.fileName.replace("Average.csv", "Chart.png")));
        html = html.replace("{write-chart}",
                taWrite == null ? ""
                                : String.format(US, "<img src='%s'/>", taWrite.fileName.replace("Average.csv", "Chart.png")));

        File summaryFile = new File(rptDir, diskModel.replace(' ', '_') + "_Summary.html");
        try {
            Files.write(summaryFile.toPath(), html.getBytes(UTF_8));
            echo("HTML report: " + summaryFile.getAbsolutePath());
            Desktop.getDesktop().open(summaryFile);
        }
        catch( IOException e ) {
            System.err.println("Failed to write to " + summaryFile + ": " + e.getMessage());
        }
    }

    public String resourceAsString(Object obj, String resourcePath) {
        Class cls = (obj instanceof Class) ? (Class)obj : obj.getClass();
        InputStream is = cls.getResourceAsStream(resourcePath);
        if( is != null ) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, UTF_8));
            return reader.lines().collect(Collectors.joining(System.lineSeparator()));
        }
        return null;
    }

    public static void buildAverage(String diskModel, File fileAvg, List<Chunk> chunks) {
        if( chunks.isEmpty() )
            return;

        Chunk last = chunks.get(chunks.size() - 1);
        int iDataSizeGb = Math.max(1, Math.round(last.offsetMb / 1024));
        StringBuilder captions = new StringBuilder(iDataSizeGb * 5);
        StringBuilder values = new StringBuilder(iDataSizeGb * 10);
        Formatter valueF = new Formatter(values);
        int cBlocksPerGB = chunks.size() / iDataSizeGb;

        captions.append("Offset GB");
        values.append(diskModel);
        for( int i = 0; i < iDataSizeGb; i++ ) {
            int midPos = i * cBlocksPerGB + cBlocksPerGB / 2;
            int from = Math.max(0, midPos - cBlocksPerGB);
            int to = Math.min(chunks.size() - 1, midPos + cBlocksPerGB);
            float sum = 0;

            for( int j = from; j <= to; j++ ) {
                sum += chunks.get(j).avg;
            }

            captions.append(',').append(i + 1);
            values.append(','); valueF.format(US, "%.1f", sum / (to - from));
        }

        captions.append('\n');
        values.append('\n');
        try( OutputStream fos = new FileOutputStream(fileAvg) ) {
            try( OutputStreamWriter wr = new OutputStreamWriter(fos, UTF_8) ) {
                wr.write(captions.toString());
                wr.write(values.toString());
            }
            echo("CSV report: " + fileAvg.getAbsolutePath());

            Pctls pctls = buildPctls(chunks);
            echo(pctls.toString());
        }
        catch( Exception e ) {
            System.err.println("Failed to write to " + fileAvg);
            e.printStackTrace();
        }
    }

    static class Pctl {
        int pos;
        float weight;
        float sizeGb;
        float value;

        @Override
        public String toString() {
            String str = String.format(US, "%.1f MB/s, %.1f%% (%.1f GB) ", value, weight, sizeGb);
            return str;
        }
    }

    static class Pctls {
        List<Pctl> avg = new ArrayList<>(3);
        Pctl max;
        Pctl min;
        float allMin;
        float allMax;

        @Override
        public String toString() {
            List<Pctl> avg = new ArrayList<>(this.avg);
            avg.sort(Comparator.comparing((Pctl pctl) -> pctl.value).reversed());

            String str =
                    "  avg.max: " + avg.get(0) + '\n' +
                    "      mid: " + avg.get(1) + '\n' +
                    "      min: " + avg.get(2) + '\n' +
                    "  typ.max: " + max + '\n' +
                    "  typ.min: " + min + '\n';
            return str;
        }
    }

    static Pctls buildPctls(List<Chunk> chunks) {
        Pctls pctls = new Pctls();

        if( chunks.size() < 2 ) {
            for( int i = 0; i < 3; i++ ) {
                pctls.avg.add(new Pctl());
            }
            return pctls;
        }

        int cChunks = chunks.size();
        float sizeGb = chunks.get(1).offsetMb * cChunks / 1024.0f;

        int[] pctlAvg = new int[101];
        int[] pctlMax = new int[101];
        int[] pctlMin = new int[101];
        float allMin = Float.MAX_VALUE, allMax = 0;

        for( int x = 0, i = 0; i < chunks.size(); x++, i++ ) {
            Chunk c = chunks.get(i);
            allMin = Math.min(allMin, c.min);
            allMax = Math.max(allMax, c.max);
        }

        float minMaxRange = allMax - allMin;
        int maxPctl = 5;
        for( int x = 0, i = 0; i < chunks.size(); x++, i++ ) {
            Chunk c = chunks.get(i);

            addPctl(pctlAvg, maxPctl, (int)(100.0 * (c.avg - allMin) / minMaxRange));
            addPctl(pctlMax, maxPctl, (int)(100.0 * (c.avgMax - allMin) / minMaxRange));
            addPctl(pctlMin, maxPctl, (int)(100.0 * (c.avgMin - allMin) / minMaxRange));
        }

        for( int i = 0; i < 3; i++ ) {
            Pctl pctl = makePctl(sizeGb, chunks, pctlAvg, allMin, minMaxRange, maxPctl);
            pctls.avg.add(pctl);
            erasePctl(pctlAvg, pctl.pos, 15);
        }

        pctls.max = makePctl(sizeGb, chunks, pctlMax, allMin, minMaxRange, maxPctl);
        pctls.min = makePctl(sizeGb, chunks, pctlMin, allMin, minMaxRange, maxPctl);

        pctls.allMin = allMin;
        pctls.allMax = allMax;

        return pctls;
    }

    private static Pctl makePctl(float sizeGb, List<Chunk> chunks, int[] pctls, float allMin, float minMaxRange, int maxPctl) {
        int posOfMaxValue = getMaxPctlPos(pctls);
        Pctl pctl = new Pctl();
        float range = minMaxRange * 0.15f;

        pctl.pos = posOfMaxValue;
        pctl.value = allMin + minMaxRange / 100 * posOfMaxValue;

        pctl.weight = 0;
        for( Chunk chunk : chunks ) {
            if( abs(chunk.avg - pctl.value) <= range )
                pctl.weight += 1;
        }
        pctl.weight = 100.0f * pctl.weight / chunks.size();
        pctl.sizeGb = sizeGb * pctl.weight / 100.0f;

        return pctl;
    }

    private static int getMaxPctlPos(int[] pctlAvg) {
        int maxPos = 0;
        int maxCount = 0;
        for( int j = 0; j < pctlAvg.length; j++ ) {
            if( pctlAvg[j] > maxCount ) {
                maxPos = j;
                maxCount = pctlAvg[j];
            }
        }
        return maxPos;
    }

    private static void addPctl(int[] pctls, int maxPtl, int pos) {
        if( pos < 0 )
            pos = 0;
        else if( pos >= pctls.length )
            pos = pctls.length - 1;

        pctls[pos] += maxPtl;
        for( int n = 1; n <= maxPtl; n++ ) {
            int pmin = pos - n;
            if( pmin >= 0 )
                pctls[pmin] += (maxPtl - n);
            int pmax = pos + n;
            if( pmax < pctls.length )
                pctls[pmax] += (maxPtl - n);
        }
    }

    private static void erasePctl(int[] pctls, int pos, int distance) {
        if( pos < 0 )
            pos = 0;
        else if( pos >= pctls.length )
            pos = pctls.length - 1;

        int eraseFrom = Math.max(0, pos - distance);
        int eraseTo = Math.min(pctls.length - 1, pos + distance);
        for( int j = eraseFrom; j <= eraseTo; j++ ) {
            pctls[j] = 0;
        }
    }

    static class Chunk {
        float offsetMb;
        float min;
        float avgMin;
        float avg;
        float avgMax;
        float max;

        public Chunk(float offsetMb, float avg) {
            this.offsetMb = offsetMb;
            this.min = this.avgMin = this.avg = this.avgMax = this.max = avg;
        }

        public Chunk(float offsetMb, float min, float avgMin, float avg, float avgMax, float max) {
            this.offsetMb = offsetMb;
            this.min = min;
            this.avgMin = avgMin;
            this.avg = avg;
            this.avgMax = avgMax;
            this.max = max;
        }
    }
}
