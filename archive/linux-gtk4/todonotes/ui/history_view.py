"""Verlauf-Tab — Erledigte Todos + Habit-History."""
from __future__ import annotations

import gi

gi.require_version("Gtk", "4.0")
gi.require_version("Adw", "1")
from gi.repository import Adw, Gtk  # noqa: E402

from .. import habit_repo, todo_repo, util  # noqa: E402


class HistoryView(Gtk.Box):
    def __init__(self):
        super().__init__(orientation=Gtk.Orientation.VERTICAL)
        self._list_box = Gtk.ListBox()
        self._list_box.set_selection_mode(Gtk.SelectionMode.NONE)
        self._list_box.add_css_class("boxed-list")

        scrolled = Gtk.ScrolledWindow()
        scrolled.set_child(self._list_box)
        scrolled.set_vexpand(True)
        self.append(scrolled)

    def refresh(self):
        child = self._list_box.get_first_child()
        while child:
            nxt = child.get_next_sibling()
            self._list_box.remove(child)
            child = nxt

        # Erledigte Todos
        completed = todo_repo.fetch_completed_todos()
        if completed:
            header = Adw.PreferencesGroup()
            header.set_title("Erledigte Aufgaben")
            self._list_box.append(header)
            for todo in completed:
                row = Adw.ActionRow()
                row.set_title(todo["title"])
                if todo.get("completedAt"):
                    row.set_subtitle(
                        f"Erledigt: {util.format_date(todo['completedAt'])}"
                    )
                self._list_box.append(row)

        # Habit-History
        history = habit_repo.fetch_history()
        if history:
            header2 = Adw.PreferencesGroup()
            header2.set_title("Gewohnheiten-Verlauf")
            self._list_box.append(header2)
            for entry in history:
                row = Adw.ActionRow()
                row.set_title(entry["title"])
                row.set_subtitle(
                    f"{entry['count']}/{entry['goal']} · {entry['cadenceLabel']} · "
                    f"{util.format_date(entry['loggedAt'])}"
                )
                self._list_box.append(row)

        if not completed and not history:
            row = Adw.ActionRow()
            row.set_title("Kein Verlauf")
            row.set_subtitle("Erledigte Aufgaben und Gewohnheiten erscheinen hier")
            self._list_box.append(row)
