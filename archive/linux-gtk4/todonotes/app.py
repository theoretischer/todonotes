"""TodoNotes — GTK4 + libadwaita Anwendung.

Hauptfenster mit 4 Tabs: Aufgaben · Gewohnheiten · Notizen · Verlauf.
"""
from __future__ import annotations

import logging
import os
import sys
from pathlib import Path

import gi

gi.require_version("Gtk", "4.0")
gi.require_version("Adw", "1")
from gi.repository import Adw, Gio, Gtk  # noqa: E402

from . import config, db, sync  # noqa: E402
from .ui.todos_view import TodosView  # noqa: E402
from .ui.habits_view import HabitsView  # noqa: E402
from .ui.notes_view import NotesView  # noqa: E402
from .ui.history_view import HistoryView  # noqa: E402
from .ui.settings_view import SettingsView  # noqa: E402

log = logging.getLogger("todonotes.app")


def _data_dir() -> str:
    """~/.local/share/todonotes/"""
    if os.environ.get("XDG_DATA_HOME"):
        return str(Path(os.environ["XDG_DATA_HOME"]) / "todonotes")
    return str(Path.home() / ".local" / "share" / "todonotes")


def _config_dir() -> str:
    """~/.config/todonotes/"""
    if os.environ.get("XDG_CONFIG_HOME"):
        return str(Path(os.environ["XDG_CONFIG_HOME"]) / "todonotes")
    return str(Path.home() / ".config" / "todonotes")


class TodoNotesApp(Adw.Application):
    def __init__(self):
        super().__init__(
            application_id="com.earendil.todonotes",
            flags=Gio.ApplicationFlags.DEFAULT_FLAGS,
        )
        self._window = None

    def do_activate(self):
        if self._window is None:
            self._window = MainWindow(application=self)
        self._window.present()

    def do_startup(self):
        Adw.Application.do_startup(self)
        # DB + Config initialisieren
        db.init_db(str(Path(_data_dir()) / "todonotes.db"))
        config.init_config(_config_dir())
        log.info("TodoNotes gestartet. DB=%s", db.db_path())


class MainWindow(Adw.ApplicationWindow):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.set_title("TodoNotes")
        self.set_default_size(900, 700)

        # Hauptlayout: ViewStack mit Bottom Navigation (wie Android Tabs)
        builder = Gtk.Builder()
        # Wir bauen das UI im Code (kein .ui-File nötig für dieses Layout)

        # ViewStack: hält die 4 Tab-Inhalte
        view_stack = Adw.ViewStack()

        # Views erstellen
        self.todos_view = TodosView()
        self.habits_view = HabitsView()
        self.notes_view = NotesView()
        self.history_view = HistoryView()

        view_stack.add_titled(
            self.todos_view, "todos", "Aufgaben"
        )
        view_stack.add_titled(
            self.habits_view, "habits", "Gewohnheiten"
        )
        view_stack.add_titled(
            self.notes_view, "notes", "Notizen"
        )
        view_stack.add_titled(
            self.history_view, "history", "Verlauf"
        )

        # ViewSwitcherBar (Bottom-Bar wie Android NavigationBar)
        switcher_bar = Adw.ViewSwitcherBar()
        switcher_bar.set_stack(view_stack)
        switcher_bar.set_reveal(True)

        # Header-Bar mit Sync-Button
        header = Adw.HeaderBar()
        sync_button = Gtk.Button(label="Sync")
        sync_button.set_icon_name("view-refresh-symbolic")
        sync_button.connect("clicked", self._on_sync_clicked)
        header.pack_end(sync_button)

        settings_button = Gtk.Button.new_from_icon_name("open-menu-symbolic")
        settings_button.connect("clicked", self._on_settings_clicked)
        header.pack_end(settings_button)

        # Box: Header oben, Content Mitte, SwitcherBar unten
        box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL)
        box.append(header)
        box.append(view_stack)
        box.append(switcher_bar)

        self.set_content(box)

        # Erste Daten laden
        self.refresh_all()

    def _on_sync_clicked(self, button):
        """Sync im Hintergrund ausführen."""
        # TODO: threading (GTK ist nicht thread-safe → GLib.idle_add)
        success = sync.sync()
        if success:
            self.refresh_all()
        else:
            self._show_toast(f"Sync fehlgeschlagen: {config.last_sync_result()}")

    def _on_settings_clicked(self, button):
        win = SettingsView(self)
        win.present()

    def _show_toast(self, message: str):
        toast = Adw.Toast.new(message)
        # TODO: ToastOverlay

    def refresh_all(self):
        """Alle Views aktualisieren (nach Sync oder Datenänderung)."""
        self.todos_view.refresh()
        self.habits_view.refresh()
        self.notes_view.refresh()
        self.history_view.refresh()


def main():
    logging.basicConfig(level=logging.INFO, format="%(name)s: %(message)s")
    app = TodoNotesApp()
    return app.run(sys.argv)


if __name__ == "__main__":
    main()
