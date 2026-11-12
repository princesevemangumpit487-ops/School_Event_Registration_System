package com.scc.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Prints data as a bordered console table, for example:
 * <pre>
 * +----+-----------+
 * | ID | Event     |
 * +----+-----------+
 * | 1  | SCC DAYS  |
 * +----+-----------+
 * </pre>
 * Plain ASCII borders are used so the table looks the same in every console.
 */
public class TablePrinter {
    private final String[] headers;
    private final int[] maxWidths; // 0 = no limit; a longer cell wraps onto extra lines
    private final List<String[]> rows = new ArrayList<>();

    public TablePrinter(String... headers) {
        this.headers = headers;
        this.maxWidths = new int[headers.length];
    }

    // Limits one column's width. Longer text wraps onto the next line inside the same row.
    public TablePrinter maxWidth(int column, int width) {
        maxWidths[column] = width;
        return this;
    }

    public TablePrinter addRow(Object... cells) {
        String[] row = new String[headers.length];
        for (int i = 0; i < headers.length; i++) {
            row[i] = i < cells.length && cells[i] != null ? String.valueOf(cells[i]) : "";
        }
        rows.add(row);
        return this;
    }

    public void print() {
        // Split every cell into wrapped lines first, so column widths are known.
        List<String[][]> wrapped = new ArrayList<>();
        int[] widths = new int[headers.length];
        for (int c = 0; c < headers.length; c++) {
            widths[c] = headers[c].length();
        }
        for (String[] row : rows) {
            String[][] cells = new String[headers.length][];
            for (int c = 0; c < headers.length; c++) {
                List<String> lines = wrap(row[c], maxWidths[c]);
                cells[c] = lines.toArray(new String[0]);
                for (String line : lines) {
                    widths[c] = Math.max(widths[c], line.length());
                }
            }
            wrapped.add(cells);
        }

        String border = border(widths);
        System.out.println(border);
        System.out.println(formatLine(headers, widths));
        System.out.println(border);
        for (String[][] cells : wrapped) {
            int height = 1;
            for (String[] cell : cells) {
                height = Math.max(height, cell.length);
            }
            for (int i = 0; i < height; i++) {
                String[] line = new String[headers.length];
                for (int c = 0; c < headers.length; c++) {
                    line[c] = i < cells[c].length ? cells[c][i] : "";
                }
                System.out.println(formatLine(line, widths));
            }
        }
        System.out.println(border);
    }

    /** Prints a two-column "Field | Value" table for one record (details, profile, etc.). */
    public static void printFields(Object... fieldsAndValues) {
        TablePrinter table = new TablePrinter("Field", "Value");
        for (int i = 0; i + 1 < fieldsAndValues.length; i += 2) {
            table.addRow(fieldsAndValues[i], fieldsAndValues[i + 1]);
        }
        table.print();
    }

    private static String border(int[] widths) {
        StringBuilder sb = new StringBuilder("+");
        for (int w : widths) {
            sb.append("-".repeat(w + 2)).append('+');
        }
        return sb.toString();
    }

    private static String formatLine(String[] cells, int[] widths) {
        StringBuilder sb = new StringBuilder("|");
        for (int c = 0; c < widths.length; c++) {
            sb.append(' ').append(String.format("%-" + widths[c] + "s", cells[c])).append(" |");
        }
        return sb.toString();
    }

    // Splits text into lines of at most "width" characters, breaking at spaces when possible.
    private static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        if (width <= 0 || text.length() <= width) {
            lines.add(text);
            return lines;
        }
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            while (word.length() > width) { // a single very long word is cut
                if (line.length() > 0) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                lines.add(word.substring(0, width));
                word = word.substring(width);
            }
            if (line.length() > 0 && line.length() + 1 + word.length() > width) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0 || lines.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }
}