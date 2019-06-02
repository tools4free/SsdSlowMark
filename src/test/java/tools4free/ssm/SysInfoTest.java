package tools4free.ssm;

import org.junit.Test;

import java.io.File;
import java.util.Map;

public class SysInfoTest {

    // @Test
    public void driveToDeviceName() {
        SysInfo pi = new SysInfo();
        for( Map.Entry<String, String> e : pi.driveModels.entrySet() ) {
            System.out.println(e.getKey() + ": " + e.getValue());
        }

        for( Map.Entry<String, String> e : pi.letterToDisk.entrySet() ) {
            System.out.println(e.getKey() + ": " + pi.driveModels.get(e.getValue()));
        }

        System.out.println("C: " + pi.diskFreeSpace(new File("C:")));
        System.out.println("C: " + pi.diskTotalSpace(new File("C:")));
    }
}