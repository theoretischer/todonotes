"""Notizen-Tab — Ordner + Notiz-Liste mit Breadcrumb-Navigation."""
from __future__ import annotations

import gi

gi.require_version("Gtk", "4.0")
gi.require_version("Adw", "1")
from gi.repository import Adw, Gtk  # noqa: E402

from .. import note_repo  # noqa: E402


class NotesView(Gtk.Box):
    def __init__(self):
        super().__init__(orientation=Gtk.Orientation.VERTICAL)
        self._current_folder: str | None = None
        self._breadcrumb: list[tuple[str, str]] = []  # (folder_id, name)

        # Breadcrumb-Bar
        self._breadcrumb_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=4)
        self._breadcrumb_box.set_margin_start(12)
        self._breadcrumb_box.set_margin_top(8)
        self._breadcrumb_box.set_margin_bottom(8)
        self.append(self._breadcrumb_box)

        # Listen
        self._list_box = Gtk.ListBox()
        self._list_box.set_selection_mode(Gtk.SelectionMode.NONE)
        self._list_box.add_css_class("boxed-list")

        scrolled = Gtk.ScrolledWindow()
        scrolled.set_child(self._list_box)
        scrolled.set_vexpand(True)

        # FAB
        add_btn = Gtk.Button.new_from_icon_name("list-add-symbolic")
        add_btn.add_css_class("circular")
        add_css_class_safe(add_btn, "suggested-action")
        add_btn.set_tooltip_text("Neue Notiz")
        add_btn.connect("clicked", self._on_add_clicked)

        overlay = Gtk.Overlay()
        overlay.set_child(scrolled)
        fab_box = Gtk.Box(halign=Gtk.Align.END, valign=Gtk.Align.END)
        fab_box.set_margin_end(16)
        fab_box.set_margin_bottom(16)
        fab_box.append(add_btn)
        overlay.add_overlay(fab_box)

        self.append(overlay)

    def refresh(self):
        self._refresh_breadcrumb()
        self._refresh_list()

    def _refresh_breadcrumb(self):
        child = self._breadcrumb_box.get_first_child()
        while child:
            nxt = child.get_next_sibling()
            self._breadcrumb_box.remove(child)
            child = nxt

        # Root crumb
        root_btn = Gtk.Button(label="Notizen")
        root_btn.add_css_class("flat")
        root_btn.connect("clicked", self._on_breadcrumb_root)
        self._breadcrumb_box.append(root_btn)

        for fid, name in self._breadcrumb:
            sep = Gtk.Label(label="›")
            sep.add_css_class("dim-label")
            self._breadcrumb_box.append(sep)
            btn = Gtk.Button(label=name)
            btn.add_css_class("flat")
            btn.connect("clicked", self._on_breadcrumb_click, fid)
            self._breadcrumb_box.append(btn)

    def _refresh_list(self):
        child = self._list_box.get_first_child()
        while child:
            nxt = child.get_next_sibling()
            self._list_box.remove(child)
            child = nxt

        folders = note_repo.fetch_folders(self._current_folder)
        notes = note_repo.fetch_notes(self._current_folder)

        if not folders and not notes:
            row = Adw.ActionRow()
            row.set_title("Keine Notizen")
            row.set_subtitle("Tippe auf + um eine neue zu erstellen")
            self._list_box.append(row)
            return

        # Ordner zuerst
        for folder in folders:
            row = Adw.ActionRow()
            row.set_title(folder["name"])
            row.add_suffix(
                Gtk.Image.new_from_icon_name("go-next-symbolic")
            )
            row.set_activatable(True)
            row.connect("activated", self._on_folder_clicked, folder["id"], folder["name"])
            self._list_box.append(row)

        # Dann Notizen
        for note in notes:
            row = Adw.ActionRow()
            row.set_title(note["title"] or "Ohne Titel")
            if note.get("type") == "CHAT":
                row.set_subtitle("Chat-Notiz")
            row.add_suffix(
                Gtk.Image.new_from_icon_name("go-next-symbolic")
            )
            row.set_activatable(True)
            row.connect("activated", self._on_note_clicked, note["id"])
            self._list_box.append(row)

    def _on_breadcrumb_root(self, button):
        self._current_folder = None
        self._breadcrumb = []
        self.refresh()

    def _on_breadcrumb_click(self, button, folder_id):
        # Breadcrumb bis zu folder_id abschneiden
        idx = next(
            (i for i, (fid, _) in enumerate(self._breadcrumb) if fid == folder_id),
            None,
        )
        if idx is not None:
            self._breadcrumb = self._breadcrumb[: idx + 1]
            self._current_folder = folder_id
            self.refresh()

    def _on_folder_clicked(self, row, folder_id, folder_name):
        self._breadcrumb.append((folder_id, folder_name))
        self._current_folder = folder_id
        self.refresh()

    def _on_note_clicked(self, row, note_id):
        # TODO: Notiz-Editor öffnen (E5)
        note = note_repo.get_note(note_id)
        if note and note.get("type") == "CHAT":
            # TODO: ChatView öffnen (E6)
            pass
        else:
            # TODO: NoteEditor öffnen (E5)
            pass

    def _on_add_clicked(self, button):
        note_repo.create_note(folder_id=self._current_folder, title=note_repo.default_note_title())
        self.refresh()


def add_css_class_safe(widget, cls):
    try:
        widget.add_css_class(cls)
    except Exception:
        pass
