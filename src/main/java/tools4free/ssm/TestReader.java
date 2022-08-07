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

import static java.lang.Math.max;
import static java.lang.Math.min;
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
        if( !doWaitFor() ) {
            finished = true;
            return;
        }

        int blockSize = config.bs * KB;
        float blockSizeMb = config.bs / (float)KB;
        byte[] data = new byte[blockSize];

        echoLn("Files reader:");
        echoLn("  Root dir: %s", root.getAbsolutePath());
        echoLn("  Disk model: %s", diskModel);
        echoLn("--------------------------------------");

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
                    File file = path.toFile();
                    float perfMin = Float.MAX_VALUE, perfMax = Float.MIN_VALUE;
                    long echoAfter = System.currentTimeMillis() + 100;

                    try( FileInputStream fis = new FileInputStream(file) ) {
                        long pos = 0;
                        for( long n = 1; pos + data.length <= fileSize; n++ ) {
                            if( stop )
                                return FileVisitResult.TERMINATE;

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
                            float perfBlock = blockSizeMb / sec;
                            long now = System.currentTimeMillis();

                            blocksTmp[cBlocksTmp++] = perfBlock;
                            cAllBlocksTmp++;
                            perfMin = min(perfMin, perfBlock);
                            perfMax = max(perfMax, perfBlock);
                            if( now > echoAfter ) {
                                echoAfter = now + 100;
                                printPerf("Read", file, fileStarted, fileMB, perfMin, perfMax, n, pos / (double)fileSize);
                            }
                        }
                    }
                    catch( Exception e ) {
                        return FileVisitResult.CONTINUE;
                    }

                    printPerf("Read", file, fileStarted, fileMB, perfMin, perfMax);
                    echoLn("                       ");

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

        echoLn("Read test complete");
        echoLn("");
        echoLn("");

        finished = true;
    }
}
