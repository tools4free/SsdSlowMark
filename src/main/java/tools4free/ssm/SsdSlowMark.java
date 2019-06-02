package tools4free.ssm;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Enumeration;
import java.util.Formatter;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import static java.util.Locale.US;

public class SsdSlowMark {

    final static int KB = 1024;
    final static int MB = 1024 * KB;
    final static int GB = 1024 * MB;
    final static float NANO_SEC = 1_000_000_000;
    final static long testStart = System.currentTimeMillis();

    private static Config config;
    private static TestWriter writer;
    private static TestReader reader;
    private static ResultsWriter output;

    static SysInfo si;
    static String ssmVersion;
    static String javaVersion;
    static String versionInfo;

    private static boolean resultsWritten;
    private static boolean statsWriting;

    public static void main(String[] args) throws IOException {
        si = new SysInfo();
        versionInfo = versionInfo();
        echo(versionInfo);
        echo("");

        config = new Config().fromArgs(args);
        switch( config.test ) {
            case "agg":
                new ResultsAggregator(config).run();
                return;
        }

        output = new ResultsWriter(config);

        if( config.test.contains("w") )
            (writer = new TestWriter(config)).start();

        if( config.test.contains("r") ) {
            (reader = new TestReader(config, writer)).start();
        }


        new Thread(SsdSlowMark::progressMonitor).start();

        Thread shutdownHandler = new Thread(SsdSlowMark::onShutdown);
        shutdownHandler.setName("Shutdown handler");
        Runtime.getRuntime().addShutdownHook(shutdownHandler);

        try { Thread.sleep(100); } catch( InterruptedException e ) { e.printStackTrace(); }

        System.out.println("Press <ENTER> to abort and generate report ...");
        System.out.println("");

        new BufferedReader(new InputStreamReader(System.in)).readLine();

        if( writer != null )
            writer.stop = true;
        if( reader != null )
            reader.stop = true;

        waitFinished();
        writeResults();
    }

    public static void echo(String message) {
        System.out.println(message);
    }

    public static void echo(String format, Object... args) {
        echo(new Formatter(US).format(format, args).toString());
    }

    static String versionInfo() {
        ssmVersion = ssmVersion();
        javaVersion = javaVersion();
        return "SsdSlowMark v" + ssmVersion
                    + ", CPU: " + si.cpuModel
                    + ", MB: " + si.motherBoard
                    + ", OS: " + si.osVersion
                    + ", Java: " + javaVersion;
    }

    private static String ssmVersion() {
        try {
            Enumeration<URL> resources = SsdSlowMark.class.getClassLoader().getResources("META-INF/MANIFEST.MF");
            while( resources.hasMoreElements() ) {
                Manifest manifest = new Manifest(resources.nextElement().openStream());
                Attributes attrs = manifest.getMainAttributes();
                String mainClass = attrs.getValue("Main-Class");
                if( mainClass == null || !mainClass.contains(SsdSlowMark.class.getSimpleName()) )
                    continue;

                String version = attrs.getValue("Version");
                return version;
            }
        }
        catch( IOException E ) {
            // handle
        }

        return "???";
    }

    private static String javaVersion() {
        String javaVer = System.getProperty("java.runtime.version");
        if( javaVer == null )
            javaVer = System.getProperty("java.version");
        return javaVer != null ? javaVer : "<unknown java version>";
    }


    private static boolean writeResults() {
        if( statsWriting ) {
            while( !resultsWritten ) {
                try { Thread.sleep(10); } catch( InterruptedException e ) { /* ok */ }
            }
            return false;
        }

        statsWriting = true;
        try {
            File rptDir = null;
            if( writer != null && writer.finished )
                rptDir = output.writeTestResults(writer);
            if( reader != null && reader.finished )
                rptDir = output.writeTestResults(reader);

            output.writeSummary(versionInfo, rptDir, reader, writer);
        }
        catch( Exception e ) {
            e.printStackTrace();
        }

        if( "rw".equals(config.test) )
            writer.cleanup();

        resultsWritten = true;

        return true;
    }

    private static void onShutdown() {
        writeResults();
    }

    private static void progressMonitor() {
        waitFinished();
        if( writeResults() )
            System.exit(0);
    }

    private static void waitFinished() {
        while( (writer != null) && !writer.finished || (reader != null) && !reader.finished ) {
            try { Thread.sleep(10); } catch( InterruptedException e ) { break; }
        }
    }

    static void exit(int code, String message) {
        System.err.println(message);
        System.exit(code);
        throw new IllegalStateException("aborted");
    }
}
