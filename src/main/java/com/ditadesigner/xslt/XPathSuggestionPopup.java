package com.ditadesigner.xslt;

import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Window;
import org.fxmisc.richtext.CodeArea;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Lightweight JavaFX {@link Popup} that displays XPath completion suggestions
 * anchored below the XSLT editor caret.
 *
 * <h3>Usage</h3>
 * <ol>
 *   <li>Set {@link #setOnAccept} to handle insertion of the selected completion.</li>
 *   <li>Set {@link #setMatchCounter} to provide async node-count feedback.</li>
 *   <li>Call {@link #installKeyHandler} once to intercept ↑ ↓ Enter Tab Escape on the editor.</li>
 *   <li>Call {@link #show} / {@link #hide} as the user types.</li>
 * </ol>
 */
public class XPathSuggestionPopup {

    private static final int MAX_VISIBLE_ROWS = 8;
    private static final int ROW_HEIGHT_PX    = 22;
    private static final int LIST_WIDTH_PX    = 340;

    private final Popup            popup;
    private final ListView<String> listView;
    private final Label            matchLabel;

    /** Called with the accepted completion string when the user commits a selection. */
    private Consumer<String> onAccept;

    /**
     * Called with (expression, callback) to compute match counts asynchronously.
     * The implementation must invoke {@code callback} with the count (or {@code -1} on error)
     * from a background thread; this class posts the result back onto the FX thread.
     */
    private BiConsumer<String, Consumer<Integer>> matchCounter;

    public XPathSuggestionPopup() {
        listView   = new ListView<>();
        matchLabel = new Label();

        matchLabel.setMaxWidth(Double.MAX_VALUE);
        matchLabel.setStyle(
                "-fx-font-size: 10px; -fx-padding: 2 6 2 6; "
                + "-fx-text-fill: #555; -fx-background-color: #f0f0f0; "
                + "-fx-border-color: #ddd; -fx-border-width: 1 0 0 0;");

        listView.setStyle(
                "-fx-font-family: 'Consolas', 'Courier New', monospace; "
                + "-fx-font-size: 12px; -fx-background-color: #fff;");

        // Accept on double-click
        listView.setOnMouseClicked(e -> { if (e.getClickCount() == 2) acceptSelected(); });

        // Trigger async match count whenever selection changes
        listView.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, sel) -> requestMatchCount(sel));

        VBox container = new VBox(listView, matchLabel);
        container.setStyle(
                "-fx-background-color: #fff; "
                + "-fx-border-color: #aaa; -fx-border-width: 1; "
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.22), 6, 0, 0, 2);");

        popup = new Popup();
        popup.setAutoHide(true);
        popup.setHideOnEscape(true);
        popup.getContent().add(container);
    }

    // ── Configuration ─────────────────────────────────────────────────────────

    public void setOnAccept(Consumer<String> handler) {
        this.onAccept = handler;
    }

    public void setMatchCounter(BiConsumer<String, Consumer<Integer>> counter) {
        this.matchCounter = counter;
    }

    /**
     * Install a key-event filter on the editor's {@link CodeArea} so that
     * ↑ ↓ Enter Tab Escape are intercepted by the popup when it is visible.
     */
    public void installKeyHandler(CodeArea codeArea) {
        codeArea.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (!popup.isShowing()) return;
            switch (event.getCode()) {
                case DOWN   -> { selectNext();    event.consume(); }
                case UP     -> { selectPrev();    event.consume(); }
                case ENTER  -> { acceptSelected(); event.consume(); }
                case TAB    -> { acceptSelected(); event.consume(); }
                case ESCAPE -> { hide();           event.consume(); }
                default     -> { /* let editor handle */ }
            }
        });
    }

    // ── Show / hide ───────────────────────────────────────────────────────────

    /**
     * Show or refresh the popup with new suggestions, anchored at (anchorX, anchorY)
     * in screen coordinates.
     */
    public void show(Window owner, double anchorX, double anchorY, List<String> suggestions) {
        if (suggestions.isEmpty()) { hide(); return; }

        listView.getItems().setAll(suggestions);
        listView.getSelectionModel().selectFirst();

        int visible = Math.min(suggestions.size(), MAX_VISIBLE_ROWS);
        listView.setPrefHeight(visible * ROW_HEIGHT_PX + 4);
        listView.setPrefWidth(LIST_WIDTH_PX);

        if (popup.isShowing()) {
            popup.setX(anchorX);
            popup.setY(anchorY);
        } else {
            popup.show(owner, anchorX, anchorY);
        }
    }

    public void hide() {
        if (popup.isShowing()) popup.hide();
        matchLabel.setText("");
    }

    public boolean isShowing() {
        return popup.isShowing();
    }

    // ── Keyboard navigation ───────────────────────────────────────────────────

    public void selectNext() {
        int idx = listView.getSelectionModel().getSelectedIndex();
        if (idx < listView.getItems().size() - 1) {
            listView.getSelectionModel().select(idx + 1);
            listView.scrollTo(idx + 1);
        }
    }

    public void selectPrev() {
        int idx = listView.getSelectionModel().getSelectedIndex();
        if (idx > 0) {
            listView.getSelectionModel().select(idx - 1);
            listView.scrollTo(idx - 1);
        }
    }

    public void acceptSelected() {
        String sel = listView.getSelectionModel().getSelectedItem();
        if (sel != null && onAccept != null) onAccept.accept(sel);
        hide();
    }

    // ── Match count ───────────────────────────────────────────────────────────

    private void requestMatchCount(String expression) {
        matchLabel.setText("");
        if (expression == null || matchCounter == null) return;
        matchCounter.accept(expression,
                count -> Platform.runLater(() ->
                        matchLabel.setText(count < 0 ? "Matches: —" : "Matches: " + count)));
    }
}
