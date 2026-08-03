"""Gewohnheiten-Tab — Liste der Habits mit +1 Button und Progress."""
from __future__ import annotations

import gi

gi.require_version("Gtk", "4.0")
gi.require_version("Adw", "1")
from gi.repository import Adw, Gtk  # noqa: E402

from .. import habit_engine, habit_repo, util  # noqa: E402


class HabitsView(Gtk.Box):
    def __init__(self):
        super().__init__(orientation=Gtk.Orientation.VERTICAL)
        self._list_box = Gtk.ListBox()
        self._list_box.set_selection_mode(Gtk.SelectionMode.NONE)
        self._list_box.add_css_class("boxed-list")

        scrolled = Gtk.ScrolledWindow()
        scrolled.set_child(self._list_box)
        scrolled.set_vexpand(True)

        # FAB
        add_btn = Gtk.Button.new_from_icon_name("list-add-symbolic")
        add_btn.add_css_class("circular")
        add_btn.add_css_class("suggested-action")
        add_btn.set_tooltip_text("Neue Gewohnheit")
        add_btn.connect("clicked", self._on_add_clicked)

        overlay = Gtk.Overlay()
        overlay.set_child(scrolled)
        fab_box = Gtk.Box(
            halign=Gtk.Align.END, valign=Gtk.Align.END,
        )
        fab_box.set_margin_end(16)
        fab_box.set_margin_bottom(16)
        fab_box.append(add_btn)
        overlay.add_overlay(fab_box)

        self.append(overlay)

    def refresh(self):
        child = self._list_box.get_first_child()
        while child:
            next_child = child.get_next_sibling()
            self._list_box.remove(child)
            child = next_child

        habits = habit_repo.fetch_habits()
        if not habits:
            row = Adw.ActionRow()
            row.set_title("Keine Gewohnheiten")
            row.set_subtitle("Tippe auf + um eine neue zu erstellen")
            self._list_box.append(row)
            return

        now = util.now_millis()
        for habit in habits:
            # Periodenwechsel prüfen + History loggen
            habit_repo.check_and_log_period_change(habit, now)
            row = self._create_habit_row(habit, now)
            self._list_box.append(row)

    def _create_habit_row(self, habit: dict, now: int) -> Gtk.Widget:
        count = habit_repo.count_since(habit["id"], habit_engine.current_period_start(habit, now))
        goal = habit["goalCount"]

        row = Adw.ActionRow()
        row.set_title(habit["title"])
        row.set_subtitle(habit_engine.cadence_label(habit))

        # Progress-Label
        progress_label = Gtk.Label(label=f"{count}/{goal}")
        progress_label.add_css_class("dim-label")
        row.add_suffix(progress_label)

        # +1 Button
        plus_btn = Gtk.Button.new_from_icon_name("list-add-symbolic")
        plus_btn.add_css_class("circular")
        plus_btn.set_tooltip_text("+1")
        plus_btn.connect("clicked", self._on_plus_clicked, habit["id"])
        row.add_suffix(plus_btn)

        # ProgressBar
        bar = Gtk.ProgressBar()
        bar.set_fraction(count / goal if goal > 0 else 0)
        row.add_suffix(bar)

        return row

    def _on_plus_clicked(self, button, habit_id):
        habit_repo.log_habit(habit_id)
        self.refresh()

    def _on_add_clicked(self, button):
        dialog = CreateHabitDialog(self.get_root())
        dialog.present()


class CreateHabitDialog(Adw.MessageDialog):
    def __init__(self, parent):
        super().__init__(transient_for=parent)
        self.set_heading("Neue Gewohnheit")
        self.set_body("Titel und Ziel eingeben:")

        box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=12)
        box.set_margin_start(12)
        box.set_margin_end(12)
        box.set_margin_top(12)
        box.set_margin_bottom(12)

        self._title_entry = Gtk.Entry()
        self._title_entry.set_placeholder_text("Gewohnheit …")
        box.append(self._title_entry)

        goal_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=8)
        goal_box.append(Gtk.Label(label="Ziel pro Tag:"))
        self._goal_spin = Gtk.SpinButton.new_with_range(1, 100, 1)
        self._goal_spin.set_value(1)
        goal_box.append(self._goal_spin)
        box.append(goal_box)

        self.set_extra_child(box)

        self.add_response("cancel", "Abbrechen")
        self.add_response("create", "Erstellen")
        self.set_response_appearance("create", Adw.ResponseAppearance.SUGGESTED)
        self.connect("response", self._on_response)

    def _on_response(self, dialog, response):
        if response == "create":
            title = self._title_entry.get_text().strip()
            goal = int(self._goal_spin.get_value())
            if title:
                now = util.now_millis()
                habit = {
                    "id": util.new_id(),
                    "title": title,
                    "notes": "",
                    "cadenceType": "DAY",
                    "interval": 1,
                    "resetWeekday": None,
                    "resetAnchorDay": None,
                    "resetAnchorMonth": None,
                    "goalCount": goal,
                    "startDate": now,
                    "logToHistory": True,
                    "lastLoggedPeriodStart": None,
                    "createdAt": now,
                    "updatedAt": now,
                    "deletedAt": None,
                }
                habit_repo.create_habit(habit)
                root = self.get_transient_for()
                if hasattr(root, "habits_view"):
                    root.habits_view.refresh()
