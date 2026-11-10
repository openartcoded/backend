package tech.artcoded.websitev2.pages.dossier.report;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

/**
 * A bar chart with its geometry computed: the templates only draw the svg (no javascript in a pdf, and the same chart
 * in the jinja and the FreeMarker templates). Coordinates are in the svg viewBox {@code 0 0 width height}.
 */
@Value
@Builder
@AllArgsConstructor
public class BarChart {
    public static final int WIDTH = 700;
    public static final int HEIGHT = 180;
    static final int LEFT = 52;
    static final int RIGHT = 6;
    static final int TOP = 16;
    static final int BOTTOM = 24;
    static final double MAX_BAR_WIDTH = 34;

    String title;
    String subtitle;
    int width;
    int height;
    /** x of the start and of the end of the plot area (the gridlines) */
    int plotLeft;
    int plotRight;
    /** nothing to show (no value above 0): the template shows a message instead */
    boolean empty;
    List<Gridline> gridlines;
    List<Bar> bars;
    List<AxisLabel> axisLabels;
    List<LegendItem> legend;

    @Value
    public static class Gridline {
        double y;
        double labelY;
        String label;
    }

    @Value
    public static class Bar {
        double x;
        double y;
        double width;
        double height;
        String color;
        /** the compact value above the bar, empty when the chart has too many bars for it */
        String valueLabel;
        double labelX;
        double labelY;
        /** tooltip */
        String title;
    }

    @Value
    public static class AxisLabel {
        double x;
        double y;
        String text;
        /** the label of the category the report is about, shown in bold */
        boolean highlighted;
    }

    @Value
    public static class LegendItem {
        String name;
        String color;
    }

    /** values in the order of the categories, null where there is no value (not drawn) */
    public record Series(String name, String color, List<BigDecimal> values) {
    }

    /**
     * @param highlighted
     *            index of the category to show in bold, -1 for none
     * @param showValues
     *            the value above each bar (for charts with few bars)
     */
    public static BarChart of(String title, String subtitle, List<String> categories, List<Series> series,
            int highlighted, boolean showValues) {
        var legend = series.stream().map(s -> new LegendItem(s.name(), s.color())).toList();
        var max = series.stream().flatMap(s -> s.values().stream()).filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue).max().orElse(0);
        if (categories.isEmpty() || max <= 0) {
            return new BarChart(title, subtitle, WIDTH, HEIGHT, LEFT, WIDTH - RIGHT, true, List.of(), List.of(), List.of(), legend);
        }
        var step = niceStep(max / 4);
        var top = step * Math.ceil(max / step);
        var plotWidth = WIDTH - LEFT - RIGHT;
        var plotHeight = HEIGHT - TOP - BOTTOM;
        var baseline = TOP + plotHeight;

        var gridlines = new ArrayList<Gridline>();
        for (var i = 0; i <= Math.round(top / step); i++) {
            var y = baseline - (i * step / top) * plotHeight;
            gridlines.add(new Gridline(round(y), round(y + 3), ReportFormat.compact(BigDecimal.valueOf(i * step))));
        }

        var groupWidth = (double) plotWidth / categories.size();
        var barWidth = Math.min(MAX_BAR_WIDTH, groupWidth * 0.76 / series.size());
        var gap = barWidth > 6 ? 2 : 0.5;
        var bars = new ArrayList<Bar>();
        var axisLabels = new ArrayList<AxisLabel>();
        for (var c = 0; c < categories.size(); c++) {
            var groupStart = LEFT + c * groupWidth + (groupWidth - barWidth * series.size()) / 2;
            for (var s = 0; s < series.size(); s++) {
                var serie = series.get(s);
                var value = c < serie.values().size() ? serie.values().get(c) : null;
                if (value == null) {
                    continue;
                }
                var barHeight = Math.max(0, value.doubleValue()) / top * plotHeight;
                var x = groupStart + s * barWidth;
                var y = baseline - barHeight;
                bars.add(new Bar(round(x), round(y), round(barWidth - gap), round(barHeight), serie.color(),
                        showValues ? ReportFormat.compact(value) : "", round(x + (barWidth - gap) / 2), round(y - 3),
                        "%s %s: %s".formatted(serie.name(), categories.get(c), ReportFormat.money(value))));
            }
            axisLabels.add(new AxisLabel(round(LEFT + c * groupWidth + groupWidth / 2), HEIGHT - 8, categories.get(c),
                    c == highlighted));
        }
        return new BarChart(title, subtitle, WIDTH, HEIGHT, LEFT, WIDTH - RIGHT, false, gridlines, bars, axisLabels, legend);
    }

    /** 1, 2 or 5 times a power of ten, the smallest above raw */
    static double niceStep(double raw) {
        if (raw <= 0) {
            return 1;
        }
        var magnitude = Math.pow(10, Math.floor(Math.log10(raw)));
        var fraction = raw / magnitude;
        var nice = fraction <= 1 ? 1 : fraction <= 2 ? 2 : fraction <= 5 ? 5 : 10;
        return nice * magnitude;
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
