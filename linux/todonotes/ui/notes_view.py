"""Notizen-Tab — Ordner + Notiz-Liste mit Breadcrumb-Navigation.

Features:
  - Breadcrumb-Navigation (Wurzel → Unterordner)
  - Ordner: erstellen, umbenennen, löschen (Kontext-Menü / Rechtsklick)
  - Notizen: erstellen, öffnen (Editor), löschen
  - FAB: neue Notiz erstellen
"""
from __future__ import annotations

import gi

gi.require_version("Gtk", "4.0")
gi.require_version("Adw", "1")
from gi.repository import Adw, Gio, Gtk  # noqa: E402

from .. import note_repo  # noqa: E402
from .note_editor import NoteEditorWindow  # noqa: E402


class NotesView(Gtk.Box):
    def __init__(self):
        super().__init__(orientation=Gtk.Orientation.VERTICAL)
        self._current_folder: str | None = None
        self._breadcrumb: list[tuple[str, str]] = []  # (folder_id, name)

        # Breadcrumb-Bar
        self._breadcrumb_box = Gtk.Box(
            orientation=Gtk.Orientation.HORIZONTAL, spacing=4
        )
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

        # FAB-Menu (neue Notiz / neuer Ordner)
        add_btn = Gtk.MenuButton()
        add_btn.set_icon_name("list-add-symbolic")
        add_btn.add_css_class("circular")
        add_btn.add_css_class("suggested-action")
        add_btn.set_tooltip_text("Neu")

        menu = Gio.Menu()
        menu.append("Neue Notiz", "notes.new-note")
        menu.append("Neuer Ordner", "notes.new-folder")
        menu.append("Neuer Chat", "notes.new-chat")
        add_btn.set_menu_model(menu)

        # Actions
        self._actions = Gio.SimpleActionGroup()
        act_note = Gio.SimpleAction.new("new-note", None)
        act_note.connect("activate", self._on_new_note)
        self._actions.insert(act_note)
        act_folder = Gio.SimpleAction.new("new-folder", None)
        act_folder.connect("activate", self._on_new_folder)
        self._actions.insert(act_folder)
        act_chat = Gio.SimpleAction.new("new-chat", None)
        act_chat.connect("activate", self._on_new_chat)
        self._actions.insert(act_chat)
        self.insert_action_group("notes", self._actions)

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
            row = self._create_folder_row(folder)
            self._list_box.append(row)

        # Dann Notizen
        for note in notes:
            row = self._create_note_row(note)
            self._list_box.append(row)

    def _create_folder_row(self, folder: dict) -> Gtk.Widget:
        row = Adw.ActionRow()
        row.set_title(folder["name"])
        icon = Gtk.Image.new_from_icon_name("folder-symbolic")
        row.add_prefix(icon)
        row.add_suffix(Gtk.Image.new_from_icon_name("go-next-symbolic"))
        row.set_activatable(True)
        row.connect("activated", self._on_folder_clicked, folder["id"], folder["name"])

        # Kontext-Menü (Umbenennen / Löschen)
        menu_btn = Gtk.MenuButton()
        menu_btn.set_icon_name("view-more-symbolic")
        menu_btn.add_css_class("flat")
        menu = Gio.Menu()
        menu.append("Umbenennen", f"folder.rename::{folder['id']}")
        menu.append("Löschen", f"folder.delete::{folder['id']}")
        menu_btn.set_menu_model(menu)
        # Actions für diesen Ordner (in eigene Gruppe)
        row.insert_action_group(
            "folder",
            _folder_actions(folder["id"], self),
        )
        row.add_suffix(menu_btn)

        return row

    def _create_note_row(self, note: dict) -> Gtk.Widget:
        row = Adw.ActionRow()
        title = note["title"] or "Ohne Titel"
        row.set_title(title)

        if note.get("type") == "CHAT":
            icon = Gtk.Image.new_from_icon_name("user-available-symbolic")
            row.set_subtitle("Chat-Notiz")
        else:
            icon = Gtk.Image.new_from_icon_name("document-symbolic")
            # Preview: erste Zeile des Body
            body = note.get("bodyJson", "")
            if body:
                first_line = body.split("\n")[0][:60]
                if first_line:
                    row.set_subtitle(first_line)
        row.add_prefix(icon)
        row.add_suffix(Gtk.Image.new_from_icon_name("go-next-symbolic"))
        row.set_activatable(True)
        row.connect("activated", self._on_note_clicked, note["id"], note.get("type", "NOTE"))
        return row

    # ── Navigation ─────────────────────────────────────────────

    def _on_breadcrumb_root(self, button):
        self._current_folder = None
        self._breadcrumb = []
        self.refresh()

    def _on_breadcrumb_click(self, button, folder_id):
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

    def _on_note_clicked(self, row, note_id, note_type):
        root = self.get_root()
        if note_type == "CHAT":
            # TODO: ChatView (E6)
            pass
        else:
            editor = NoteEditorWindow(root, note_id, is_new=False)
            editor.present()

    # ── FAB Actions ────────────────────────────────────────────

    def _on_new_note(self, action, param):
        note = note_repo.create_note(
            folder_id=self._current_folder, title=note_repo.default_note_title()
        )
        root = self.get_root()
        editor = NoteEditorWindow(root, note["id"], is_new=True)
        editor.present()
        editor.connect("close-request", self._on_editor_closed)
        # Refresh nur wenn Editor geschlossen
        self.refresh()

    def _on_new_chat(self, action, param):
        note_repo.create_chat_note(folder_id=self._current_folder, title="Neuer Chat")
        self.refresh()

    def _on_new_folder(self, action, param):
        dialog = Adw.MessageDialog(
            transient_for=self.get_root(),
            heading="Neuer Ordner",
            body="Name eingeben:",
        )
        entry = Gtk.Entry()
        entry.set_placeholder_text("Ordnername …")
        entry.connect("activate", lambda e: dialog.response("create"))
        dialog.set_extra_child(entry)
        dialog.add_response("cancel", "Abbrechen")
        dialog.add_response("create", "Erstellen")
        dialog.set_response_appearance(
            "create", Adw.ResponseAppearance.SUGGESTED
        )

        def on_response(d, response):
            if response == "create":
                name = entry.get_text().strip()
                if name:
                    note_repo.create_folder(self._current_folder, name)
                    self.refresh()

        dialog.connect("response", on_response)
        dialog.present()

    def _on_editor_closed(self, window):
        self.refresh()


def _folder_actions(folder_id: str, view: NotesView) -> Gio.SimpleActionGroup:
    """Erstellt eine Action-Gruppe für einen einzelnen Ordner."""
    group = Gio.SimpleActionGroup()

    def on_rename(action, param):
        folder = note_repo.get_folder(folder_id)
        if not folder:
            return
        dialog = Adw.MessageDialog(
            transient_for=view.get_root(),
            heading="Ordner umbenennen",
            body="Neuer Name:",
        )
        entry = Gtk.Entry()
        entry.set_text(folder["name"])
        entry.connect("activate", lambda e: dialog.response("ok"))
        dialog.set_extra_child(entry)
        dialog.add_response("cancel", "Abbrechen")
        dialog.add_response("ok", "OK")

        def on_resp(d, resp):
            if resp == "ok":
                name = entry.get_text().strip()
                if name:
                    note_repo.rename_folder(folder_id, name)
                    view.refresh()

        dialog.connect("response", on_resp)
        dialog.present()

    def on_delete(action, param):
        note_repo.soft_delete_folder(folder_id)
        view.refresh()

    # Actions mit String-Parameter für folder_id
    act_rename = Gio.SimpleAction.new("rename", None)
    act_rename.connect("activate", on_rename)
    group.insert(act_rename)

    act_delete = Gio.SimpleAction.new("delete", None)
    act_delete.connect("activate", on_delete)
    group.insert(act_delete)

    return group
