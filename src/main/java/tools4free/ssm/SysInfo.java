package tools4free.ssm;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static java.util.Arrays.asList;

public class SysInfo {

    List<Supplier<Boolean>> sources = asList(this::readWmic, this::readLinux);

    // Letter -> physical disk number|ID C: -> 0, D: -> 1
    Map<String,String> letterToDisk = new HashMap<>();
    // "physical disk number|ID" -> "Disk model"
    Map<String,String> driveModels = new HashMap<>();

    String osVersion;
    String cpuModel;
    String motherBoard;

    public SysInfo() {
        for( Supplier<Boolean> source : sources ) {
            if( source.get() )
                break;
        }
    }

    private Boolean readWmic() {
        try {
            String osName = System.getProperty("os.name");
            if( !osName.contains("Windows") )
                return false;

            List<String> lines = Shell.exec("cmd.exe", "/c", "ver");
            osVersion = "???";
            for( String line : lines ) {
                line = line.trim();
                if( !line.isEmpty() ) {
                    line = line.replace("Microsoft ", "")
                               .replace("Version ", "")
                               .replace("[", "")
                               .replace("]", "");
                    osVersion = line;
                }
            }

            //
            // Read mapping "C:" -> "0" (drive index)
            //
            List<String> part2letter = Shell.exec("wmic.exe", "partition", "assoc", "/resultclass:win32_logicaldisk");
            String curDisk = "";

            for( String line : part2letter ) {
                // \\<host-name>\ROOT\CIMV2:Win32_DiskPartition.DeviceID="Disk #0, Partition #0"
                if( line.contains("Win32_DiskPartition") ) {
                    String marker = "Disk #";
                    int pos1 = line.indexOf(marker) + marker.length();
                    int pos2 = line.indexOf(",", pos1 + 1);
                    curDisk = line.substring(pos1, pos2);
                }
                else if( line.contains("Win32_LogicalDisk") ) {
                    String marker = "DeviceID=\"";
                    int pos1 = line.indexOf(marker) + marker.length();
                    int pos2 = line.indexOf("\"", pos1 + 1);
                    String letter = line.substring(pos1, pos2);

                    letterToDisk.put(letter.toUpperCase(), curDisk);
                }
            }

            //
            // Read names of the drives
            //
            List<String> diskDrives = Shell.exec("wmic.exe", "diskdrive", "list", "brief", "/format:list");

            for( String line : diskDrives ) {
                // DeviceID=\\.\PHYSICALDRIVE0
                if( line.contains("PHYSICALDRIVE") ) {
                    String marker = "PHYSICALDRIVE";
                    int pos1 = line.indexOf(marker) + marker.length();
                    curDisk = line.substring(pos1);
                }
                else if( line.contains("Model=") ) {
                    int pos1 = line.indexOf('=') + 1;
                    String model = line.substring(pos1);

                    driveModels.put(curDisk, model);
                }
            }


            // Name of the CPU
            List<String> cpuInfo = Shell.exec("wmic.exe", "cpu", "get", "Name", "/format:list");
            cpuModel = getProperty(cpuInfo, '=', "Name");
            cpuModel = nws(cpuModel.replace("Intel", "")
                                   .replace("Core", "")
                                   .replace("Processor", "")
                                   .replace("(R)", "")
                                   .replace("(TM)", ""));

            // Name of the CPU
            List<String> baseboardInfo = Shell.exec("wmic.exe", "baseboard", "get", "Product", "/format:list");
            motherBoard = getProperty(baseboardInfo, '=', "Product");

            return true;
        }
        catch( Exception e ) {
            e.printStackTrace();
            return false;
        }
    }

    private Boolean readLinux() {
        try {
            String osName = System.getProperty("os.name");
            if( !osName.contains("Linux") )
                return false;

            // Name of the CPU
            List<String> osInfo = Shell.exec("cat", "/etc/os-release");
            osVersion = getProperty(osInfo, '=', "PRETTY_NAME");

            // Name of the CPU
            List<String> cpuInfo = Shell.exec("cat", "/proc/cpuinfo");
            cpuModel = getProperty(cpuInfo, ':', "model name");
            cpuModel = nws(cpuModel.replace("Intel", "")
                                   .replace("Core", "")
                                   .replace("Processor", "")
                                   .replace("(R)", "")
                                   .replace("(TM)", ""));

            // Name of the CPU
            List<String> board_name = Shell.exec("cat", "/sys/devices/virtual/dmi/id/board_name");
            motherBoard = board_name.isEmpty() ? "???" : board_name.get(0);

            return true;
        }
        catch( Exception e ) {
            e.printStackTrace();
            return false;
        }
    }

    private final static Pattern WS = Pattern.compile("\\s+");
    private static String nws(String str) {
        return WS.matcher(str).replaceAll(" ").trim();
    }

    private String getProperty(List<String> cpuInfo, char sep, String name) {
        for( String line : cpuInfo ) {
            if( !line.startsWith(name) )
                continue;

            int posSep = line.indexOf(sep, name.length());
            if( posSep == -1 )
                continue;

            String blank = line.substring(name.length(), posSep).trim();
            if( !blank.isEmpty())
                continue;

            String value = line.substring(posSep + 1).trim();
            if( value.startsWith("\"") && value.endsWith("\"") )
                value = value.substring(1, value.length() - 2);

            return value;
        }

        return "???";
    }

    public static String letterOf(File path) {
        String absPath = path.getAbsolutePath();
        if( absPath.charAt(1) == ':' ) {
            // via Windows letter
            String driveLetter = absPath.substring(0, 2).toUpperCase();
            return driveLetter;
        }
        return null;
    }

    public String getDriveModel(File path, String defaultName) {
        String driveLetter = letterOf(path);
        if( driveLetter != null ) {
            String diskId = letterToDisk.get(driveLetter);
            if( diskId != null ) {
                String driveModel = driveModels.get(diskId);
                if( driveModel != null )
                    return driveModel;
            }
        }

        return defaultName;
    }

    public long diskFreeSpace(File path) {
        AtomicLong space = new AtomicLong(-1);
        iterDisks(path, (File root) -> space.addAndGet(root.getFreeSpace()));
        if( space.get() < 0 )
            return path.getFreeSpace();
        return space.get();
    }

    public long diskTotalSpace(File path) {
        AtomicLong space = new AtomicLong(-1);
        iterDisks(path, (File root) -> space.addAndGet(root.getTotalSpace()));
        if( space.get() < 0 )
            return path.getTotalSpace();
        return space.get();
    }

    private void iterDisks(File path, Consumer<File> processor) {
        String driveLetter = letterOf(path);
        if( driveLetter != null ) {
            String diskId = letterToDisk.get(driveLetter);
            if( diskId != null ) {
                for( Map.Entry<String, String> e : letterToDisk.entrySet() ) {
                    if( diskId.equals(e.getValue()) ) {
                        File root = new File(e.getKey());
                        processor.accept(root);
                    }
                }
            }
        }
    }
}
