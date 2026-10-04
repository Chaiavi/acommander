---
description: "Use when adding or editing UI text in JavaFX: buttons, labels, column headers, dialog titles, menu items, Command Palette entries, tooltips. Covers Title Case and one-sentence tooltips."
applyTo:
  - "src/main/java/org/chaiware/acommander/Commander.java"
  - "src/main/java/org/chaiware/acommander/dialog/**"
  - "src/main/java/org/chaiware/acommander/palette/**"
  - "src/main/resources/**/*.fxml"
  - "config/apps.json"
---
# UI Text Rules

## Every control that can carry a tooltip gets one

- One sentence. Say what it does or what the value means, not what the control is.
- Java: `button.setTooltip(new Tooltip("Copies the selected files to the other pane."));`
- FXML: `<tooltip><Tooltip text="Stops the running external tool."/></tooltip>`
- A new control without a tooltip is unfinished. When you edit an existing control, add its missing tooltip.

## Titles and labels are Title Case; sentences are not

- Title Case: window/dialog titles, button text, field labels, column headers, tab and menu text, `apps.json` `label`.
  Example: `Split a Large File`, `Open Terminal Here`.
- Keep minor words lowercase (a, an, the, of, to, in, on, for, and, or) unless first or last.
- Sentence case: tooltips, descriptions, status and error messages, prompts. Example: `Running external command...`.
