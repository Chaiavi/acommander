package org.chaiware.acommander.dialog;

import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.stage.Window;
import org.chaiware.acommander.commands.PdfExtractOptions;

import java.util.Optional;
import java.util.regex.Pattern;

import static org.chaiware.acommander.dialog.OptionsDialog.positiveIntOrNull;
import static org.chaiware.acommander.dialog.OptionsDialog.tip;

/** Asks which pages Extract PDF Pages should write, and how many pages go in each output PDF. */
public final class PdfExtractDialog {
    private static final Pattern PAGE_EXPRESSION = Pattern.compile("(?i)^[0-9a-z\\s,:-]+$");

    private PdfExtractDialog() {
    }

    public static Optional<PdfExtractOptions> show(Window owner, String themeClass, String fileName, int totalPages) {
        OptionsDialog<PdfExtractOptions> dialog = new OptionsDialog<>(owner, themeClass,
                "Extract PDF Pages", "Extract", "Write the chosen pages to new PDF files.");

        Label details = new Label("File: " + fileName);
        details.setWrapText(true);
        Label totalPagesLabel = new Label("Total pages: " + totalPages);
        totalPagesLabel.setStyle("-fx-font-weight: bold;");

        ToggleGroup modeGroup = new ToggleGroup();
        RadioButton allPages = tip(new RadioButton("All Pages (One Page per PDF)"),
                "Write every page to its own PDF file.");
        RadioButton specificPages = tip(new RadioButton("Specific Pages (One Page per PDF)"),
                "Write only the pages in the expression below, each to its own PDF file.");
        RadioButton pagesPerPdf = tip(new RadioButton("Pages per PDF (Chunk into Multiple PDFs)"),
                "Split the document into PDFs of the page count below.");
        allPages.setToggleGroup(modeGroup);
        specificPages.setToggleGroup(modeGroup);
        pagesPerPdf.setToggleGroup(modeGroup);
        allPages.setSelected(true);

        TextField pageSpecField = tip(new TextField(), "Pages to extract, such as 1-4, 1,4,7 or 1:3.");
        pageSpecField.setPromptText("Examples: 10-30, 1,4,7, 1:3");
        TextField pagesPerPdfField = tip(new TextField("100"), "How many pages each output PDF gets.");
        pagesPerPdfField.setPromptText("Example: 100");
        Label hint = new Label("Use expressions like 1-4, 1,4,7, 1:3, page10-30, pages 10-30.");
        hint.setStyle("-fx-opacity: 0.75;");
        Label validationLabel = new Label();
        validationLabel.setWrapText(true);

        dialog.add(details, totalPagesLabel, new Separator(), allPages, specificPages, pageSpecField,
                pagesPerPdf, pagesPerPdfField, hint, validationLabel);

        Runnable validate = () -> {
            pageSpecField.setDisable(!specificPages.isSelected());
            pagesPerPdfField.setDisable(!pagesPerPdf.isSelected());
            String problem = null;
            String message;
            if (allPages.isSelected()) {
                message = "All pages will be extracted into one-page PDF files.";
            } else if (specificPages.isSelected()) {
                String spec = pageSpecField.getText().trim();
                problem = spec.isEmpty() ? "Enter a page expression (for example: 1,4,7 or 10-30)."
                        : !PAGE_EXPRESSION.matcher(spec).matches() ? "Page expression format is invalid." : null;
                message = "Only selected pages will be extracted.";
            } else {
                Integer pages = positiveIntOrNull(pagesPerPdfField.getText());
                problem = pages == null ? "Pages per PDF must be a positive whole number." : null;
                message = "Output will be split into PDFs of " + pages + " page(s) each.";
            }
            validationLabel.setText(problem != null ? problem : message);
            dialog.okButton().setDisable(problem != null);
        };
        modeGroup.selectedToggleProperty().addListener((obs, oldValue, newValue) -> validate.run());
        pageSpecField.textProperty().addListener((obs, oldValue, newValue) -> validate.run());
        pagesPerPdfField.textProperty().addListener((obs, oldValue, newValue) -> validate.run());
        validate.run();

        return dialog.showAndWait(() -> {
            if (allPages.isSelected()) {
                return PdfExtractOptions.extractAll();
            }
            if (specificPages.isSelected()) {
                return new PdfExtractOptions(PdfExtractOptions.Mode.SPECIFIC_PAGES_SINGLE, pageSpecField.getText().trim(), null, null);
            }
            return new PdfExtractOptions(PdfExtractOptions.Mode.PAGES_PER_PDF, null, positiveIntOrNull(pagesPerPdfField.getText()), null);
        });
    }
}
