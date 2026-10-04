package org.chaiware.acommander.helpers;

import org.chaiware.acommander.model.FileItem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Pane sort order: folders first, then the sort column, then natural name order; ".." always stays on top. */
public final class PaneSorter {
    public enum SortColumn {NAME, SIZE, MODIFIED}

    public record SortState(SortColumn column, boolean ascending) {
        public static final SortState DEFAULT = new SortState(SortColumn.NAME, true);

        /** Clicking the active column flips its order; another column starts ascending, Modified newest first. */
        public SortState toggle(SortColumn clicked) {
            return clicked == column ? new SortState(column, !ascending) : new SortState(clicked, clicked != SortColumn.MODIFIED);
        }
    }

    private PaneSorter() {
    }

    public static List<FileItem> sort(List<FileItem> items, SortState state) {
        List<FileItem> sorted = new ArrayList<>(items.size());
        items.stream().filter(PaneSorter::isParentEntry).findFirst().ifPresent(sorted::add);
        items.stream().filter(item -> !isParentEntry(item)).sorted(comparator(state)).forEach(sorted::add);
        return sorted;
    }

    static Comparator<FileItem> comparator(SortState state) {
        Comparator<FileItem> byName = (left, right) -> compareNaturalNames(left.getPresentableFilename(), right.getPresentableFilename());
        Comparator<FileItem> byColumn = switch (state.column()) {
            case NAME -> byName;
            case SIZE -> Comparator.comparingLong(item -> item.isDirectory() ? 0L : item.getSizeInBytes());
            case MODIFIED -> Comparator.comparingLong(PaneSorter::modified);
        };
        if (!state.ascending()) {
            byColumn = byColumn.reversed();
        }
        return Comparator.comparing(FileItem::isDirectory).reversed().thenComparing(byColumn).thenComparing(byName);
    }

    /** Case-insensitive, digit runs compared as numbers: file2 before file10, file2 before file02. */
    static int compareNaturalNames(String left, String right) {
        if (left == null || right == null) {
            if (left == right) {
                return 0;
            }
            return left == null ? -1 : 1;
        }

        int leftIndex = 0;
        int rightIndex = 0;

        while (leftIndex < left.length() && rightIndex < right.length()) {
            char leftChar = left.charAt(leftIndex);
            char rightChar = right.charAt(rightIndex);

            if (Character.isDigit(leftChar) && Character.isDigit(rightChar)) {
                int leftDigitsStart = leftIndex;
                int rightDigitsStart = rightIndex;

                while (leftIndex < left.length() && Character.isDigit(left.charAt(leftIndex))) {
                    leftIndex++;
                }
                while (rightIndex < right.length() && Character.isDigit(right.charAt(rightIndex))) {
                    rightIndex++;
                }

                int leftNonZero = leftDigitsStart;
                while (leftNonZero < leftIndex && left.charAt(leftNonZero) == '0') {
                    leftNonZero++;
                }
                int rightNonZero = rightDigitsStart;
                while (rightNonZero < rightIndex && right.charAt(rightNonZero) == '0') {
                    rightNonZero++;
                }

                int leftSignificantLength = leftIndex - leftNonZero;
                int rightSignificantLength = rightIndex - rightNonZero;
                if (leftSignificantLength != rightSignificantLength) {
                    return Integer.compare(leftSignificantLength, rightSignificantLength);
                }

                for (int i = 0; i < leftSignificantLength; i++) {
                    char leftDigit = left.charAt(leftNonZero + i);
                    char rightDigit = right.charAt(rightNonZero + i);
                    if (leftDigit != rightDigit) {
                        return Character.compare(leftDigit, rightDigit);
                    }
                }

                int leftRunLength = leftIndex - leftDigitsStart;
                int rightRunLength = rightIndex - rightDigitsStart;
                if (leftRunLength != rightRunLength) {
                    return Integer.compare(leftRunLength, rightRunLength);
                }
                continue;
            }

            char leftLower = Character.toLowerCase(leftChar);
            char rightLower = Character.toLowerCase(rightChar);
            if (leftLower != rightLower) {
                return Character.compare(leftLower, rightLower);
            }

            leftIndex++;
            rightIndex++;
        }

        int lengthCompare = Integer.compare(left.length(), right.length());
        if (lengthCompare != 0) {
            return lengthCompare;
        }
        return left.compareTo(right);
    }

    private static long modified(FileItem item) {
        if (item.getLastModified() != null) {
            return item.getLastModified();
        }
        return item.getFile() != null ? item.getFile().lastModified() : 0L;
    }

    private static boolean isParentEntry(FileItem item) {
        return "..".equals(item.getPresentableFilename());
    }
}
