"""Notiz-Editor — Vollbild-Fenster mit Titel + Body (TextView).

Der Body ist Plain-Text mit Listen-Präfixen + Markdown-Inline-Styles.
GTK TextView zeigt den Roh-Text (inkl. Präfixe) — einfacher als
zeilenweises Compose-Modell, aber Sync-kompatibel.

Toolbar unten: Fett/Kursiv/Unterstrichen, Bullet/Ordered/Checkbox/Arrow.
Beim Schließen (Back) wird automatisch gespeichert.
"""
from __future__ import annotations

import gi

gi.require_version("Gtk", "4.0")
gi.require_version("Adw", "1")
from gi.repository import Adw, Gdk, Gtk, Pango  # noqa: E402

from .. import note_repo, note_text_body as ntb, util  # noqa: E402
from ..note_text_body import InlineStyle, ListType  # noqa: E402


# ── TextTag-Namen ─────────────────────────────────────────────────
_TAG_BOLD = "style-bold"
_TAG_ITALIC = "style-italic"
_TAG_UNDERLINE = "style-underline"
_TAG_HIDDEN = "marker-hidden"  # unsichtbar (Markdown-Marker)


class NoteEditorWindow(Adw.ApplicationWindow):
    """Vollbild-Notiz-Editor."""

    def __init__(self, parent, note_id: str, is_new: bool = False):
        super().__init__(transient_for=parent)
        self.set_title("Notiz")
        self.set_default_size(800, 600)
        self.set_modal(True)

        self._note_id = note_id
        self._is_new = is_new
        self._note = note_repo.get_note(note_id)
        self._saving = False

        # Header-Bar
        header = Adw.HeaderBar()
        back_btn = Gtk.Button.new_from_icon_name("go-previous-symbolic")
        back_btn.set_tooltip_text("Zurück")
        back_btn.connect("clicked", self._on_back)
        header.pack_start(back_btn)

        # Toolbar unten (Format-Bar)
        toolbar = self._build_toolbar()

        # Titel
        self._title_entry = Gtk.Entry()
        self._title_entry.set_placeholder_text("Titel")
        self._title_entry.set_text(self._note.get("title", "") if self._note else "")
        self._title_entry.add_css_class("title")
        self._title_entry.connect("changed", self._on_title_changed)

        # Body TextView
        self._buffer = Gtk.TextBuffer()
        self._setup_tags()
        body_text = ""
        if self._note:
            body_text = self._note.get("bodyJson", "")
            # Falls alter JSON-Body → migrieren (vereinfacht: nur plain text)
            body_text = ntb.migrate_if_needed(body_text)
        self._buffer.set_text(body_text)
        self._buffer.connect("changed", self._on_body_changed)

        self._text_view = Gtk.TextView(buffer=self._buffer)
        self._text_view.set_wrap_mode(Gtk.WrapMode.WORD_CHAR)
        self._text_view.set_vexpand(True)
        self._text_view.set_top_margin(16)
        self._text_view.set_left_margin(16)
        self._text_view.set_right_margin(16)
        self._text_view.set_bottom_margin(16)

        # TextView in ScrolledWindow
        scrolled = Gtk.ScrolledWindow()
        scrolled.set_child(self._text_view)
        scrolled.set_vexpand(True)

        # Layout
        content = Gtk.Box(orientation=Gtk.Orientation.VERTICAL)
        content.append(header)
        # Titel mit Padding
        title_box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL)
        title_box.set_margin_start(16)
        title_box.set_margin_end(16)
        title_box.set_margin_top(8)
        title_box.append(self._title_entry)
        content.append(title_box)
        content.append(scrolled)
        content.append(toolbar)

        self.set_content(content)

        # Fokus auf Titel bei neuer Notiz
        if is_new:
            self._title_entry.grab_focus()

        # Tags anwenden nach erstem Laden
        self._apply_tags()

    def _build_toolbar(self) -> Gtk.Box:
        """Format-Leiste: B/I/U + Listen + Checkbox + Arrow."""
        bar = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=4)
        bar.set_margin_start(12)
        bar.set_margin_end(12)
        bar.set_margin_top(6)
        bar.set_margin_bottom(6)
        bar.add_css_class("toolbar")

        buttons: list[tuple[str, str, object]] = [
            ("format-text-bold-symbolic", "Fett", InlineStyle.BOLD),
            ("format-text-italic-symbolic", "Kursiv", InlineStyle.ITALIC),
            ("format-text-underline-symbolic", "Unterstrichen", InlineStyle.UNDERLINE),
        ]
        for icon, tooltip, style in buttons:
            btn = Gtk.Button.new_from_icon_name(icon)
            btn.set_tooltip_text(tooltip)
            btn.add_css_class("flat")
            btn.connect("clicked", self._on_inline_style, style)
            bar.append(btn)

        # Separator
        sep = Gtk.Separator(orientation=Gtk.Orientation.VERTICAL)
        bar.append(sep)

        list_buttons: list[tuple[str, str, object]] = [
            ("view-list-bullet-symbolic", "Aufzählung", ListType.BULLET),
            ("view-list-ordered-symbolic", "Nummerierung", ListType.ORDERED),
            ("object-select-symbolic", "Checkbox", ListType.CHECKBOX),
            ("format-indent-more-symbolic", "Pfeil-Liste", ListType.ARROW),
        ]
        for icon, tooltip, lt in list_buttons:
            btn = Gtk.Button.new_from_icon_name(icon)
            btn.set_tooltip_text(tooltip)
            btn.add_css_class("flat")
            btn.connect("clicked", self._on_list_type, lt)
            bar.append(btn)

        # Separator
        sep2 = Gtk.Separator(orientation=Gtk.Orientation.VERTICAL)
        bar.append(sep2)

        # Checkbox-Toggle (aktuelle Zeile)
        check_toggle = Gtk.Button.new_from_icon_name("checkbox-checked-symbolic")
        check_toggle.set_tooltip_text("Checkbox umschalten")
        check_toggle.add_css_class("flat")
        check_toggle.connect("clicked", self._on_toggle_checkbox)
        bar.append(check_toggle)

        return bar

    def _on_title_changed(self, entry):
        self._save()

    def _setup_tags(self):
        """Registriert TextTags für Inline-Styles + Marker-Ausblendung."""
        # Bold
        tag = self._buffer.create_tag(_TAG_BOLD)
        tag.set_property("weight", Pango.Weight.BOLD)
        # Italic
        tag = self._buffer.create_tag(_TAG_ITALIC)
        tag.set_property("style", Pango.Style.ITALIC)
        # Underline
        tag = self._buffer.create_tag(_TAG_UNDERLINE)
        tag.set_property("underline", Pango.Underline.SINGLE)
        # Hidden (Markdown-Marker unsichtbar machen)
        tag = self._buffer.create_tag(_TAG_HIDDEN)
        tag.set_property("invisible", True)

    def _apply_tags(self):
        """Analysiert den Buffer-Text und wendet Inline-Style-Tags an.

        Markdown-Marker (** * __) werden mit dem 'hidden'-Tag unsichtbar
        gemacht, der Inhalt dazwischen bekommt bold/italic/underline.
        Die Marker bleiben im Buffer (fürs Speichern/Sync), sind aber
        für den Nutzer nicht sichtbar.

        Nutzt ntb._build_visible für überlappungsfreie Match-Erkennung
        (wie im Kotlin _build_visible: bold schlägt italic bei ** etc.).
        """
        if self._saving:
            return
        self._saving = True

        buf = self._buffer
        # Alle Tags entfernen
        start, end = buf.get_bounds()
        buf.remove_all_tags(start, end)

        full_text = buf.get_text(start, end, False)
        if not full_text:
            self._saving = False
            return

        # _build_visible liefert überlappungsfreie Matches mit
        # removed_raw (Marker-Positionen) + styles (Inhalt-Positionen
        # im sichtbaren Text). Wir brauchen aber Raw-Positionen.
        # → Wir replizieren die Match-Sammlung aus _build_visible hier,
        # um Raw-Offsets für TextTags zu haben.
        matches = ntb._collect_matches(full_text)

        for m in matches:
            style_name = m.style
            open_len = len(ntb._marker(m.style))
            # Öffnende Marker verstecken
            self._apply_tag_range(_TAG_HIDDEN, m.start, m.start + open_len)
            # Schließende Marker verstecken
            self._apply_tag_range(_TAG_HIDDEN, m.end - open_len, m.end)
            # Inhalt = Style-Tag
            if style_name == InlineStyle.BOLD:
                self._apply_tag_range(_TAG_BOLD, m.start + open_len, m.end - open_len)
            elif style_name == InlineStyle.ITALIC:
                self._apply_tag_range(_TAG_ITALIC, m.start + open_len, m.end - open_len)
            elif style_name == InlineStyle.UNDERLINE:
                self._apply_tag_range(_TAG_UNDERLINE, m.start + open_len, m.end - open_len)

        self._saving = False

    def _apply_tag_range(self, tag_name: str, start_offset: int, end_offset: int):
        """Wendet einen TextTag auf den Offset-Bereich an."""
        if end_offset <= start_offset:
            return
        start_iter = self._buffer.get_iter_at_offset(start_offset)
        end_iter = self._buffer.get_iter_at_offset(end_offset)
        self._buffer.apply_tag_by_name(tag_name, start_iter, end_iter)

    def _on_body_changed(self, buffer):
        if self._saving:
            return
        self._save()
        self._apply_tags()

    def _save(self):
        """Speichert Titel + Body in die DB."""
        if not self._note_id:
            return
        title = self._title_entry.get_text().strip()
        start, end = self._buffer.get_bounds()
        body = self._buffer.get_text(start, end, False)
        self._saving = True
        note_repo.update_note(self._note_id, title=title, bodyJson=body)
        self._saving = False

    def _on_back(self, button):
        self._save()
        self.close()

    # ── Inline-Styles auf Auswahl anwenden ─────────────────────

    def _on_inline_style(self, button, style: InlineStyle):
        """Togglet B/I/U auf die aktuelle Auswahl im TextView."""
        buf = self._buffer
        sel = buf.get_selection_bounds()
        if not sel:
            # Keine Auswahl → nichts tun (oder ganzer Cursor-Wort?)
            return
        start_iter, end_iter = sel
        start = start_iter.get_offset()
        end = end_iter.get_offset()

        full_text = buf.get_text(buf.get_start_iter(), buf.get_end_iter(), False)
        new_text, new_start, new_end = ntb.toggle_inline_style(
            full_text, start, end, style
        )

        # Buffer aktualisieren
        self._saving = True
        buf.set_text(new_text)
        # Selektion neu setzen
        new_sel_start = buf.get_iter_at_offset(new_start)
        new_sel_end = buf.get_iter_at_offset(new_end)
        buf.select_range(new_sel_start, new_sel_end)
        self._saving = False
        self._save()

    # ── Listen-Typ auf aktuelle Zeile setzen ───────────────────

    def _on_list_type(self, button, lt: ListType):
        """Setzt den Listen-Prefix der aktuellen Zeile auf lt (Toggle)."""
        buf = self._buffer
        insert = buf.get_iter_at_mark(buf.get_insert())
        line_start = insert.copy()
        line_start.set_line_offset(0)
        line_end = line_start.copy()
        if not line_end.ends_line():
            line_end.forward_to_line_end()

        line_text = buf.get_text(line_start, line_end, False)
        current_type = ntb.detect_list_type(line_text)

        # Toggle: gleicher Typ → Prefix entfernen
        if current_type == lt:
            new_content = ntb.strip_prefix(line_text)
        else:
            content = ntb.strip_prefix(line_text)
            if lt == ListType.BULLET:
                new_content = ntb.BULLET_PREFIX + content
            elif lt == ListType.ORDERED:
                new_content = "1. " + content
            elif lt == ListType.CHECKBOX:
                new_content = ntb.CHECKBOX_OPEN + content
            elif lt == ListType.ARROW:
                new_content = ntb.ARROW_PREFIX + content
            else:
                new_content = content

        # Zeile im Buffer ersetzen
        self._saving = True
        buf.begin_user_action()
        buf.delete(line_start, line_end)
        buf.insert(line_start, new_content)
        buf.end_user_action()
        self._saving = False
        self._save()

    def _on_toggle_checkbox(self, button):
        """Toggelt [ ] ↔ [x] auf der aktuellen Zeile."""
        buf = self._buffer
        insert = buf.get_iter_at_mark(buf.get_insert())
        line_start = insert.copy()
        line_start.set_line_offset(0)

        # Offset der Zeile im gesamten Buffer
        line_offset = line_start.get_offset()
        full_text = buf.get_text(
            buf.get_start_iter(), buf.get_end_iter(), False
        )

        new_text = ntb.toggle_checkbox(full_text, line_offset)
        if new_text != full_text:
            self._saving = True
            buf.set_text(new_text)
            self._saving = False
            self._save()


def migrate_if_needed(body_json: str) -> str:
    """Stub — wird in note_text_body.migrate_if_needed behandelt."""
    return ntb.migrate_if_needed(body_json)
