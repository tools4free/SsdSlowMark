package tools4free.ssm;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

import static tools4free.ssm.SsdSlowMark.*;

public class TestReader extends TestCase {

    public TestReader(Config config, TestCase waitFor) {
        super("Read", config, new File(config.in), waitFor);
    }

    int cAllBlocksTmp = 0;
    int cBlocksTmp = 0;
    List<float[]> allBlocksTmp = new ArrayList<>();
    float[] blocksTmp = new float[1024];

    @Override
    public void run() {
        if( !doWaitFor() )
            return;

        int blockSize = config.bs * KB;
        float blockSizeMb = config.bs / (float)KB;
        byte[] data = new byte[blockSize];

        echo("Files reader:");
        echo("  Root dir: %s", root.getAbsolutePath());
        echo("  Disk model: %s", diskModel);
        echo("--------------------------------------");

        allBlocksTmp.add(blocksTmp);
        startTime = System.currentTimeMillis();
        try {
            Files.walkFileTree(root.toPath(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                    if( stop )
                        return FileVisitResult.TERMINATE;

                    if( attrs.isDirectory() )
                        return FileVisitResult.CONTINUE;

                    long fileSize = attrs.size();
                    if( fileSize < blockSize )
                        return FileVisitResult.CONTINUE;

                    if( !Files.exists(path) )
                        // deleted on the way
                        return FileVisitResult.CONTINUE;

                    long fileStarted = System.nanoTime();
                    long fileMB = 0;

                    try( FileInputStream fis = new FileInputStream(path.toFile()) ) {
                        long pos = 0;
                        while( pos + data.length <= fileSize ) {
                            long started = System.nanoTime();
                            {
                                pos += fis.read(data);
                                fileMB += data.length;
                            }
                            long finished = System.nanoTime();

                            if( cBlocksTmp >= blocksTmp.length ) {
                                blocksTmp = new float[1024];
                                allBlocksTmp.add(blocksTmp);
                                cBlocksTmp = 0;
                            }

                            float sec = (finished - started) / NANO_SEC;
                            float perfMb = blockSizeMb / sec;

                            blocksTmp[cBlocksTmp++] = perfMb;
                            cAllBlocksTmp++;
                        }
                    }
                    catch( Exception e ) {
                        return FileVisitResult.CONTINUE;
                    }

                    long fileFinished = System.nanoTime();
                    float fileSec = (fileFinished - fileStarted) / NANO_SEC;
                    float filePerfMb = fileMB / fileSec / MB;

                    echo("Read: %s = %.1f MB/s", path.toAbsolutePath(), filePerfMb);

                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        catch( IOException e ) {
            e.printStackTrace();
        }
        stopTime = System.currentTimeMillis();
        elapsedMs = stopTime - startTime;

        int pos = 0;

        this.blocks = new float[cAllBlocksTmp];
        this.cBlocks = cAllBlocksTmp;
        for( int i = 0; i < allBlocksTmp.size(); i++ ) {
            float[] blocki = allBlocksTmp.get(i);

            if( blocki != blocksTmp )
                System.arraycopy(blocki, 0, this.blocks, pos, blocki.length);
            else
                System.arraycopy(blocksTmp, 0, this.blocks, pos, cBlocksTmp);

            pos += blocki.length;
        }


        SsdSlowMark.echo("Read test complete");
        SsdSlowMark.echo("");
        SsdSlowMark.echo("");

        finished = true;
    }
}
