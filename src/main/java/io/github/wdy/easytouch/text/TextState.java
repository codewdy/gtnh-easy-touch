package io.github.wdy.easytouch.text;

/** Immutable UTF-16 text and selection, shared between the game and native UI threads. */
final class TextState {
    final String text;
    final int anchor, caret;

    TextState(String text, int anchor, int caret) {
        this.text = text;
        this.anchor = Math.max(0, Math.min(text.length(), anchor));
        this.caret = Math.max(0, Math.min(text.length(), caret));
    }

    boolean same(TextState other) {
        return other != null && text.equals(other.text) && anchor == other.anchor && caret == other.caret;
    }
}
