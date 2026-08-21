"""Einstellungen — Server-URL, Token, Sync-Button."""
from __future__ import annotations

import gi

gi.require_version("Gtk", "4.0")
gi.require_version("Adw", "1")
from gi.repository import Adw, Gtk  # noqa: E402

from .. import config, sync  # noqa: E402


class SettingsView(Adw.PreferencesWindow):
    """Einstellungsfenster (Modal über Hauptfenster)."""

    def __init__(self, parent):
        super().__init__(transient_for=parent)
        self.set_default_size(500, 400)
        self.set_title("Einstellungen")

        page = Adw.PreferencesPage()
        page.set_title("Sync")
        page.set_icon_name("view-refresh-symbolic")

        group = Adw.PreferencesGroup()
        group.set_title("Synchronisation")
        group.set_description("Server-URL und Token für den Sync")

        # Server-URL
        self._url_row = Adw.EntryRow()
        self._url_row.set_title("Server-URL")
        self._url_row.set_text(config.server_url())
        self._url_row.set_input_purpose(Gtk.InputPurpose.URL)
        self._url_row.connect("changed", self._on_url_changed)
        group.add(self._url_row)

        # Token
        self._token_row = Adw.PasswordEntryRow()
        self._token_row.set_title("Token")
        self._token_row.set_text(config.token())
        self._token_row.connect("changed", self._on_token_changed)
        group.add(self._token_row)

        # Sync-Status
        self._status_row = Adw.ActionRow()
        self._status_row.set_title("Letzter Sync")
        self._status_row.set_subtitle("—")
        group.add(self._status_row)

        # Sync-Button
        sync_btn = Gtk.Button(label="Jetzt synchronisieren")
        sync_btn.add_css_class("suggested-action")
        sync_btn.connect("clicked", self._on_sync_clicked)
        self._status_row.add_suffix(sync_btn)

        # Health-Button
        health_btn = Gtk.Button(label="Verbindung testen")
        health_btn.connect("clicked", self._on_health_clicked)
        group.add(health_btn)

        self._health_row = Adw.ActionRow()
        self._health_row.set_title("Server-Status")
        self._health_row.set_subtitle("—")
        group.add(self._health_row)

        page.add(group)
        self.add(page)

        self._update_status()

    def _on_url_changed(self, row):
        config.set_server_url(row.get_text())

    def _on_token_changed(self, row):
        config.set_token(row.get_text())

    def _on_sync_clicked(self, button):
        button.set_label("Sync läuft …")
        button.set_sensitive(False)
        success = sync.sync()
        button.set_label("Jetzt synchronisieren")
        button.set_sensitive(True)
        self._update_status()
        if success:
            self._status_row.set_subtitle("✓ Sync erfolgreich")
        else:
            self._status_row.set_subtitle(f"✗ {config.last_sync_result()}")

    def _on_health_clicked(self, button):
        ok = sync.health()
        if ok:
            self._health_row.set_subtitle("✓ Server erreichbar")
        else:
            self._health_row.set_subtitle("✗ Nicht erreichbar")

    def _update_status(self):
        last = config.last_sync_at()
        if last:
            import datetime
            dt = datetime.datetime.fromtimestamp(last / 1000)
            self._status_row.set_subtitle(
                f"{dt.strftime('%d.%m.%Y %H:%M')} — {config.last_sync_result()}"
            )
        else:
            self._status_row.set_subtitle("Noch nie synchronisiert")
