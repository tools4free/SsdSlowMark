package tools4free.ssm;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static java.util.Locale.US;
import static tools4free.ssm.SsdSlowMark.echo;

public class TestWriter extends TestCase {
    List<File> createdFiles = new ArrayList<>(500);

    public TestWriter(Config config) {
        super("Write", config, new File(config.out), null);
    }

    @Override
    public void run() {
        if( !doWaitFor() )
            return;

        root = new File(config.out);
        File[] files = root.listFiles();

        // cleanup from previous run
        if( files != null ) {
            for( File file : files ) {
                String fn = file.getName();
                if( fn.contains("file-") && fn.endsWith(".bin") )
                    file.delete();
            }
        }

        byte[] data = new byte[config.bs * SsdSlowMark.KB];
        Random rnd = new Random();
        float blockSizeMb = config.bs / (float)SsdSlowMark.KB;
        long fileSizeLim = (long)config.fs * SsdSlowMark.MB;

        for( int i = 0; i < data.length; i++ ) {
            data[i] = (byte)rnd.nextInt();
        }

        File file = null;

        echo("Files writer:");
        echo("  File count: %s", config.fc);
        echo("  File size: %s MB", config.fs);
        echo("  Block size: %.1f MB", config.bs / 1024.0);
        echo("  Root dir: %s", root.getAbsolutePath());
        echo("  Disk model: %s", diskModel);
        echo("--------------------------------------");

        startTime = System.currentTimeMillis();
        try {
            root.mkdirs();
            blocks = new float[config.fc * (1 + (int)(((long)config.fs * SsdSlowMark.MB) / (config.bs * SsdSlowMark.KB)))];
            for( int i = 1; !stop && i <= config.fc; i++ ) {
                long fileStarted = System.nanoTime();
                long fileMB = 0;
                long freeSpace = root.getFreeSpace();

                if( freeSpace - fileSizeLim < SsdSlowMark.GB ) {
                    echo("  Abort, free space: %.1f", freeSpace / (float)SsdSlowMark.GB);
                    break;
                }

                file = new File(root, String.format(US, "file-%06d.bin", i));
                try( FileOutputStream fos = new FileOutputStream(file) ) {
                    createdFiles.add(file);

                    for( long fs = 0; !stop && fs < fileSizeLim; fs += data.length ) {
                        long started = System.nanoTime();
                        {
                            fos.write(data);
                            fos.flush();
                            fos.getFD().sync();
                        }
                        long finished = System.nanoTime();

                        float sec = (finished - started) / SsdSlowMark.NANO_SEC;
                        float perfMb = blockSizeMb / sec;

                        blocks[cBlocks++] = perfMb;
                        fileMB += data.length;
                    }
                }

                long fileFinished = System.nanoTime();
                float fileSec = (fileFinished - fileStarted) / SsdSlowMark.NANO_SEC;
                float filePerfMb = fileMB / fileSec / SsdSlowMark.MB;

                echo("Write: %s = %.1f MB/s", file, filePerfMb);
            }
        }
        catch( Exception e ) {
            System.err.println("Failed to write to: " + file);
            e.printStackTrace();
        }

        stopTime = System.currentTimeMillis();
        elapsedMs = stopTime - startTime;

        echo("Write test complete");
        echo("");
        echo("");

        finished = true;
    }

    public void cleanup() {
        for( File file : createdFiles ) {
            file.delete();
        }
    }
}
