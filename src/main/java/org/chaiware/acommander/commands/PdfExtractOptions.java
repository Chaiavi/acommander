package org.chaiware.acommander.commands;

public record PdfExtractOptions(Mode mode, String pageExpression, Integer pagesPerPdf, Integer knownTotalPages) {
    public enum Mode {
        ALL_PAGES_SINGLE,
        SPECIFIC_PAGES_SINGLE,
        PAGES_PER_PDF
    }

    public static PdfExtractOptions extractAll() {
        return new PdfExtractOptions(Mode.ALL_PAGES_SINGLE, null, null, null);
    }

    public PdfExtractOptions withKnownTotalPages(Integer totalPages) {
        return new PdfExtractOptions(mode, pageExpression, pagesPerPdf, totalPages);
    }
}
