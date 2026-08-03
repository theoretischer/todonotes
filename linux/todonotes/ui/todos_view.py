"""Aufgaben-Tab — Liste offener Todos mit Checkbox, FAB zum Erstellen."""
from __future__ import annotations

import gi

gi.require_version("Gtk", "4.0")
gi.require_version("Adw", "1")
from gi.repository import Adw, GObject, Gtk  # noqa: E402

from .. import todo_repo, util  # noqa: E402


class TodosView(Gtk.Box):
    """Hauptansicht: Liste offener Todos."""

    def __init__(self):
        super().__init__(orientation=Gtk.Orientation.VERTICAL)
        self._list_box = Gtk.ListBox()
        self._list_box.set_selection_mode(Gtk.SelectionMode.NONE)
        self._list_box.add_css_class("boxed-list")

        scrolled = Gtk.ScrolledWindow()
        scrolled.set_child(self._list_box)
        scrolled.set_vexpand(True)

        # FAB: Todo erstellen
        add_btn = Gtk.Button.new_from_icon_name("list-add-symbolic")
        add_btn.add_css_class("circular")
        add_btn.add_css_class("suggested-action")
        add_btn.set_tooltip_text("Neue Aufgabe")
        add_btn.connect("clicked", self._on_add_clicked)

        # FAB rechts unten über Overlay
        overlay = Gtk.Overlay()
        overlay.set_child(scrolled)
        fab_box = Gtk.Box(
            orientation=Gtk.Orientation.HORIZONTAL,
            halign=Gtk.Align.END,
            valign=Gtk.Align.END,
        )
        fab_box.set_margin_end(16)
        fab_box.set_margin_bottom(16)
        fab_box.append(add_btn)
        overlay.add_overlay(fab_box)

        self.append(overlay)

    def refresh(self):
        """Liste neu aufbauen."""
        # Alle Kinder entfernen
        child = self._list_box.get_first_child()
        while child:
            next_child = child.get_next_sibling()
            self._list_box.remove(child)
            child = next_child

        todos = todo_repo.fetch_open_todos()
        if not todos:
            self._show_empty_state()
            return

        for todo in todos:
            row = self._create_todo_row(todo)
            self._list_box.append(row)

    def _show_empty_state(self):
        row = Adw.ActionRow()
        row.set_title("Keine offenen Aufgaben")
        row.set_subtitle("Tippe auf + um eine neue zu erstellen")
        self._list_box.append(row)

    def _create_todo_row(self, todo: dict) -> Gtk.Widget:
        row = Adw.ActionRow()
        row.set_title(todo["title"])
        if todo.get("notes"):
            row.set_subtitle(todo["notes"])
        if todo.get("dueAt"):
            row.add_suffix(
                Gtk.Label(label=util.format_date(todo["dueAt"]))
            )

        # Checkbox zum Abhaken
        check = Gtk.CheckButton()
        check.set_tooltip_text("Erledigen")
        check.connect("toggled", self._on_complete_toggled, todo["id"])
        row.add_prefix(check)

        return row

    def _on_complete_toggled(self, check, todo_id):
        todo_repo.complete_todo(todo_id)
        self.refresh()

    def _on_add_clicked(self, button):
        dialog = CreateTodoDialog(self.get_root())
        dialog.present()

    def do_refresh_after_dialog(self):
        self.refresh()


class CreateTodoDialog(Adw.MessageDialog):
    """Einfacher Dialog zum Erstellen eines Todos (nur Titel)."""

    def __init__(self, parent):
        super().__init__(transient_for=parent)
        self.set_heading("Neue Aufgabe")
        self.set_body("Titel eingeben:")

        self._entry = Gtk.Entry()
        self._entry.set_placeholder_text("Aufgabe …")
        self._entry.connect("activate", self._on_create)
        self.set_extra_child(self._entry)

        self.add_response("cancel", "Abbrechen")
        self.add_response("create", "Erstellen")
        self.set_response_enabled("create", True)
        self.set_response_appearance("create", Adw.ResponseAppearance.SUGGESTED)
        self.connect("response", self._on_response)

    def _on_create(self, entry):
        self.response("create")

    def _on_response(self, dialog, response):
        if response == "create":
            title = self._entry.get_text().strip()
            if title:
                todo_repo.create_todo(title)
                # Parent View refreshen
                root = self.get_transient_for()
                if hasattr(root, "todos_view"):
                    root.todos_view.refresh()
                elif hasattr(root, "do_refresh_after_dialog"):
                    root.do_refresh_after_dialog()
