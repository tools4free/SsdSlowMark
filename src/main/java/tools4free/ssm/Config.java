package tools4free.ssm;

class Config {
    String test  = "rw";        // r | w | rw
    int bs       = 4 * 1024;    // KB, block size
    int fs       = 1 * 1024;    // MB, size of one output file
    int fc       = 50;          // number of generated files
    String out   = "_data";     // directory to generate output files
    String in    = "_data";     // directory to read input file
    int    rd    = 4096;        // MB, for rw test - delay to start reading own file

    String rpt   = "./";        // base name for output folder
    int    iw    = 800;         // px, width of the output image
    int    ih    = 600;         // px, height of the output image
    int    ip    = 60;          // px, padding of the image

    Config fromArgs(String[] args) {
        for( String arg : args ) {
            int pos = arg.indexOf('=');
            if( pos == -1 )
                SsdSlowMark.exit(1, "Invalid arg: " + arg);

            String name = arg.substring(0, pos);
            String value = arg.substring(pos + 1);

            switch( name ) {
                case "test":    test = value; break;
                case "bs":      bs = Integer.parseInt(value); break;
                case "fs":      fs = Integer.parseInt(value); break;
                case "fc":      fc = Integer.parseInt(value); break;
                case "out":     out = value; break;
                case "in":      in = value; break;

                case "rpt":     rpt = value; break;
                case "iw":      iw = Integer.parseInt(value); break;
                case "ih":      ih = Integer.parseInt(value); break;
                case "ip":      ip = Integer.parseInt(value); break;

                default:        SsdSlowMark.exit(1, "Unsupported arg: " + value);
            }
        }

        if( bs < 4 || bs > (1024 * 1024) )
            SsdSlowMark.exit(1, "Invalid bs: " + bs);

        if( fs < 1 || fs > (32 * 1024) )
            SsdSlowMark.exit(1, "Invalid fs: " + fs);

        if( fc < 1 || fc > (10000) )
            SsdSlowMark.exit(1, "Invalid fc: " + fc);

        if( rd < 1 || rd > (1024 * 1024) )
            SsdSlowMark.exit(1, "Invalid rd: " + bs);

        switch( test ) {
            case "r":
            case "rw":
            case "w":
            case "agg":
                break;
            default:    SsdSlowMark.exit(1, "Unsupported test: " + test);
        }

        return this;
    }
}
