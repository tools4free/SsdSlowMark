package tools4free.ssm;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;
import static tools4free.ssm.ResultsWriter.*;
import static tools4free.ssm.SsdSlowMark.echo;

public class ResultsAggregator {
    Config config;

    static final Pattern FN_PATTERN = Pattern.compile("^(?<model>.*)_\\((?<testKind>.*)\\)_*(?<output>[^.]*).csv$");
    public final static DecimalFormat FLOAT_FORMATTER = (DecimalFormat)NumberFormat.getInstance(Locale.US);

    public ResultsAggregator(Config config) {
        this.config = config;
    }

    public void run() {
        File in = new File(config.in);
        File out = new File(config.out);
        Map<String,StringBuilder> averages = new HashMap<>();

        echo("Running aggregation in " + in);
        File[] files = in.listFiles();
        if( files == null )
            return;

        for( File file : files ) {
            if( file.isDirectory() )
                continue;

            Matcher m = FN_PATTERN.matcher(file.getName());
            if( !m.matches() )
                continue;

            String model = m.group("model");
            String test = m.group("testKind");
            String output = m.group("output");
            switch( output ) {
                case "Chunks":
                    break;

                case "Average": {
                    File chunksFile = new File(file.getAbsolutePath().replace("Average.", "Chunks."));
                    buildAverage(model, chunksFile);
                    addAverage(averages, test, file);
                    break;
                }
            }
        }

        for( Map.Entry<String, StringBuilder> e : averages.entrySet() ) {
            Path outFile = out.toPath().resolve("All-" + e.getKey() + ".csv");
            byte[] outText = e.getValue().toString().getBytes(UTF_8);

            try {
                Files.write(outFile, outText);
            }
            catch( IOException ex ) {
                echo("Failed to write %s: %s", outFile, ex.getMessage());
            }
        }
    }

    private void addAverage(Map<String, StringBuilder> averages, String test, File file) {
        try {
            List<String> lines = Files.readAllLines(file.toPath(), UTF_8);
            StringBuilder avg = averages.computeIfAbsent(test, key -> {
                StringBuilder sb = new StringBuilder(10000);
                sb.append(lines.get(0)).append('\n');
                return sb;
            });

            avg.append(lines.get(1)).append('\n');
        }
        catch( IOException e ) {
            echo("Failed to read %s: %s", file, e.getMessage());
        }
    }

    static final Pattern COMMA = Pattern.compile(",");

    private void buildAverage(String diskModel, File file) {
        try {
            List<String> lines = Files.readAllLines(file.toPath(), UTF_8);
            List<Chunk> chunks = new ArrayList<>(lines.size());

            for( int i = 1; i < lines.size(); i++ ) {
                String line = lines.get(i);
                String[] c = COMMA.split(line);
                Chunk chunk = new Chunk(f(c[1]), f(c[2]), f(c[3]), f(c[4]), f(c[5]), f(c[6]));
                chunks.add(chunk);
            }

            String avgFileName = file.getName().replace("Chunks", "Average");
            File avgFile = new File(file.getParent(), avgFileName);

            ResultsWriter.buildAverage(diskModel, avgFile, chunks);
        }
        catch( Exception e ) {
            echo("Failed to read %s: %s", file, e.getMessage());
        }
    }

    public static Map<String,TestAverages> loadAverages(File rptDir) {
        File[] files = rptDir.listFiles();
        if( files == null )
            return Collections.emptyMap();

        Map<String,TestAverages> averages = new HashMap<>();

        for( File file : files ) {
            if( file.isDirectory() )
                continue;

            Matcher m = FN_PATTERN.matcher(file.getName());
            if( !m.matches() )
                continue;

            String testKind = m.group("testKind");
            String output = m.group("output");
            switch( output ) {
                case "Chunks":
                    break;

                case "Average": {
                    try {
                        List<String> lines = Files.readAllLines(file.toPath(), UTF_8);
                        String[] clmn = COMMA.split(lines.get(1));
                        TestAverages ta = new TestAverages();

                        ta.diskModel = clmn[0];
                        ta.testKind = testKind;
                        ta.chunks = new ArrayList<>(clmn.length);
                        ta.fileName = file.getName();
                        for( int i = 1; i < clmn.length; i++ ) {
                            ta.chunks.add(new Chunk(1024 * (i - 1), FLOAT_FORMATTER.parse(clmn[i]).floatValue()));
                        }

                        averages.put(testKind, ta);
                    }
                    catch( Exception e ) {
                        echo("Failed to read %s: %s", file, e.getMessage());
                    }
                    break;
                }
            }
        }

        return averages;
    }

    private float f(String str) throws ParseException {
        return FLOAT_FORMATTER.parse(str).floatValue();
    }
}
