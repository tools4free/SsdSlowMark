package tools4free.ssm;

import tools4free.ssm.ResultsWriter.Chunk;
import tools4free.ssm.ResultsWriter.Pctl;
import tools4free.ssm.ResultsWriter.Pctls;

import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;

import static java.lang.Math.abs;
import static java.util.Locale.US;
import static javax.swing.SwingConstants.*;
import static tools4free.ssm.ResultsWriter.buildPctls;
import static tools4free.ssm.SsdSlowMark.GB;

public class Chart {
    Config config;
    BufferedImage img;
    Graphics2D g;
    Stroke defaultStroke;

    boolean prepared;
    boolean framed;
    Pctls pctls;
    float dataSizeGb;

    int cMaxChunks;
    float allMax = Float.MIN_VALUE;
    float allMin = 0;

    int w;
    int h;
    int top;
    int left;
    int btm;
    int right;

    enum Kind {
        Spread,
        Line
    }

    public Chart(Config config) {
        this.config = config;
    }

    void prepareFor(List<Chunk> chunks) {
        pctls = buildPctls(chunks);
        int pad = config.ip;
        w = config.iw;
        h = config.ih;

        allMax = Math.max(allMax, pctls.allMax);
        allMin = Math.min(allMin, pctls.allMin);
        top = pad;
        left = pad;
        btm = h - pad;
        right = w - pad / 2;
    }

    void prepared() {
        this.prepared = true;
    }

    void frame() {
        img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        g = img.createGraphics();
        defaultStroke = g.getStroke();

        g.setColor(Color.white);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.GRAY);
        g.drawLine(left, top, left, btm);
        g.drawLine(left, btm, right, btm);

        g.setColor(Color.GRAY);
        drawLabel(g, left - 4, top,  RIGHT, BOTTOM, "MB/s");
        drawLabel(g, left - 4, top,  RIGHT,    TOP, String.format(US, "%.1f", allMax));
        drawLabel(g, left - 4, btm,  RIGHT, BOTTOM, String.format(US, "%.1f", allMin));
        drawLabel(g, left + 4, btm,   LEFT,    TOP, String.format(US, "%.1f", 0.0));

        framed = true;
    }

    boolean generateFor(List<Chunk> chunks, Kind kind, Color lineColor, boolean withMinMaxPctls) {
        int cChunks = chunks.size();
        if( cChunks < 2 )
            return false;

        dataSizeGb = chunks.get(1).offsetMb * cChunks / 1024.0f;
        pctls = buildPctls(chunks);

        if( !prepared )
            prepareFor(chunks);

        if( !framed )
            frame();

        int ch = btm - top;
        float minMaxRange = allMax - allMin;
        float xScale = (right - left) / (float)cChunks;

        if( xScale < 1.2 ) {
            xScale = 1.0f;
        }
        else {
            xScale = (int)(xScale * 10) / 10.0f;
        }

        Color o = Color.ORANGE;
        Color clrMinMax = new Color(o.getRed(), o.getGreen(), o.getBlue(), 80);

        switch( kind ) {
            case Spread:
                for( int x = 0, i = 0; i < chunks.size(); x++, i++ ) {
                    Chunk c = chunks.get(i);
                    int yTop = btm - (int)(ch * (c.max - allMin) / minMaxRange);
                    int yTopA = btm - (int)(ch * (c.avgMax - allMin) / minMaxRange);
                    int yMid = btm - (int)(ch * (c.avg - allMin) / minMaxRange);
                    int yBtm = btm - (int)(ch * (c.min - allMin) / minMaxRange);
                    int yBtmA = btm - (int)(ch * (c.avgMin - allMin) / minMaxRange);
                    int sx = left + 1 + (int)(x * xScale);

                    g.setColor(clrMinMax);
                    g.drawLine(sx, yTop, sx, yBtm);

                    g.setColor(Color.ORANGE);
                    g.drawLine(sx, yTopA, sx, yBtmA);

                    g.setColor(Color.RED);
                    g.drawLine(sx, yMid - 3, sx, yMid + 3);
                }
                break;

            case Line:
                Path2D.Double path = null;

                for( int x = 0, i = 0; i < chunks.size(); x++, i++ ) {
                    Chunk c = chunks.get(i);
                    int y = btm - (int)(ch * (c.avg - allMin) / minMaxRange);
                    int sx = left + 1 + (int)(x * xScale);

                    if( path == null ) {
                        path = new Path2D.Double();
                        path.moveTo(sx, y);
                    }
                    else {
                        path.lineTo(sx, y);
                    }
                }

                if( path != null ) {
                    g.setColor(lineColor);
                    g.setStroke(new BasicStroke(5, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                    g.setStroke(defaultStroke);
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.draw(path);
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                    g.setStroke(defaultStroke);
                }

                break;
        }

        Stroke dotted = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{2}, 0);

        g.setStroke(dotted);
        for( int i = 1; i <= 10; i++ ) {
            int xGb = left + (int)(i * cChunks / 10 * xScale);

            g.setColor(Color.LIGHT_GRAY);
            g.drawLine(xGb, top, xGb, btm);

            double iGb = dataSizeGb * i / 10;
            String label = String.format(US, "%.1f", iGb);
            if( i == 10 )
                label += " GB";
            g.setColor(Color.DARK_GRAY);
            drawLabel(g, xGb, btm, CENTER, TOP, label);
        }
        g.setStroke(defaultStroke);

        // draw 3 most typical percentiles
        Stroke dashed = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[] {9}, 0);
        float _allMin = allMin;
        BiConsumer<Float,Color> pctrlRenderer = (pctlValue, clr) -> {
            int yMid = btm - (int)(ch * (pctlValue - allMin) / minMaxRange);
            g.setColor(clr != null ? clr : Color.LIGHT_GRAY);
            g.drawLine(left, yMid, right, yMid);

            g.setColor(clr != null ? clr : Color.DARK_GRAY);
            drawLabel(g, left - 4, yMid, RIGHT, CENTER, String.format(US, "%.1f", pctlValue));
        };

        g.setStroke(dashed);

        Color pctlClr = lineColor;
        List<Pctl> avgPctls = new ArrayList<>(pctls.avg);
        float lastPctl = Float.MAX_VALUE;

        avgPctls.sort(Comparator.comparing((Pctl pctl) -> pctl.value).reversed());
        for( Pctl pctl : avgPctls ) {
            if( pctl.weight > 3.3 ) {
                float diffVsPrevPct = 100.0f * abs(pctl.value - lastPctl) / allMax;

                if( diffVsPrevPct > 5 ) {
                    lastPctl = pctl.value;
                    pctrlRenderer.accept(pctl.value, pctlClr);
                }
            }
            pctlClr = pctlClr.brighter();
        }

        if( withMinMaxPctls ) {
            pctrlRenderer.accept(pctls.max.value, new Color(0, 196, 0));
            pctrlRenderer.accept(pctls.min.value, new Color(196, 0, 0));
        }

        g.setStroke(defaultStroke);
        return true;
    }

    void renderTestSummary(TestCase test) {
        // output drive summary
        int secAll = (int)(test.elapsedMs / 1000);
        int elapsed = secAll;
        int sec = elapsed % 60; elapsed = elapsed / 60;
        int min = elapsed % 60; elapsed = elapsed / 60;
        int hr = elapsed;

        String contextSummary = String.format(US, "Free %s/%s GB, %s, data: %.1f GB, time: %d:%02d:%02d (%d sec)",
                test.freeSpaceAtStart / GB, test.totalSpace / GB,
                test.root.getAbsolutePath(), test.dataSizeGb,
                hr, min, sec, secAll);

        g.setColor(Color.RED);
        addChartTitle(test.testKind, test.diskModel);

        drawLabel(g,  left + 4, top - 32, LEFT,  BOTTOM, "SsdSlowMark v" + SsdSlowMark.ssmVersion);
        drawLabel(g, right - 1, top - 16, RIGHT, BOTTOM,
                "CPU: " + SsdSlowMark.si.cpuModel
                + ", MB: " + SsdSlowMark.si.motherBoard);
        drawLabel(g, right - 1, top - 32, RIGHT, BOTTOM,
                "OS: " + SsdSlowMark.si.osVersion
                + ", Java: " + SsdSlowMark.javaVersion);
        drawLabel(g, right - 1, top - 48, RIGHT, BOTTOM, contextSummary);
    }

    void addLineMarker(int nLine, String lineMarker, Color lineColor) {
        g.setColor(lineColor);
        drawLabel(g, right - 4, top - 16 + nLine * 20, RIGHT, BOTTOM, lineMarker);
    }

    void addChartTitle(String testKind, String diskModel) {
        drawLabel(g, left - 4, top - 16, RIGHT, BOTTOM, testKind);
        drawLabel(g, left + 4, top - 16, LEFT, BOTTOM, diskModel);
    }

    private static void drawLabel(Graphics g, int x, int y, int xStick, int yStick, String label) {
        FontMetrics fontMetrics = g.getFontMetrics();
        int w = fontMetrics.stringWidth(label);
        int h = fontMetrics.getHeight();

        switch( xStick ) {
            case CENTER: x -= (w / 2); break;
            case RIGHT:  x -= w; break;
        }

        switch( yStick ) {
            case CENTER: y += (h / 2); break;
            case TOP:    y += h; break;
        }

        g.drawString(label, x, y);
    }
}
