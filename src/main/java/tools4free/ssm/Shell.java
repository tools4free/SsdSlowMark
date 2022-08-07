package tools4free.ssm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.util.Arrays.asList;
import static tools4free.ssm.SsdSlowMark.echoLn;

public class Shell {

    static List<String> exec(String... command) throws IOException {
        Runtime rt = Runtime.getRuntime();
        Process proc = rt.exec(command);
        String line;
        List<String> lines = new ArrayList<>();

        BufferedReader stdIn = new BufferedReader(new InputStreamReader(proc.getInputStream()));
        while( (line = stdIn.readLine()) != null ) {
            lines.add(line);
        }

        try {
            proc.waitFor();
            if( proc.exitValue() != 0 ) {
                echoLn(asList(command).toString());
                BufferedReader errIn = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
                while( (line = errIn.readLine()) != null ) {
                    echoLn(line);
                }

                return Collections.emptyList();
            }
        }
        catch( InterruptedException e ) {
            return Collections.emptyList();
        }

        return lines;
    }
}
