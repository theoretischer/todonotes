"""Pydantic-Modelle für Sync-Request/Response.

Spiegeln die Android-Room-Entities 1:1 wider (Feldnamen wie in Kotlin,
camelCase). Timestamps sind Millis (Long). Booleans werden als 0/1 in
SQLite gespeichert (db.py mappt das).
"""
from __future__ import annotations

from pydantic import BaseModel, ConfigDict


class TodoDTO(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    id: str
    userId: str | None = None
    title: str
    notes: str = ""
    dueAt: int | None = None
    recurrence: str | None = None
    completedAt: int | None = None
    createdAt: int
    updatedAt: int
    deletedAt: int | None = None
    logToHistory: bool = True
    notificationStyle: int = 0    # 0=Vollbild, 1=nur Notification, 2=stumm


class HabitDTO(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    id: str
    userId: str | None = None
    title: str
    notes: str = ""
    cadenceType: str          # "DAY" | "WEEK" | "MONTH" | "YEAR" | "NDAYS"
    interval: int = 1
    resetWeekday: int | None = None
    resetAnchorDay: int | None = None
    resetAnchorMonth: int | None = None
    goalCount: int
    startDate: int
    logToHistory: bool = True
    lastLoggedPeriodStart: int | None = None
    type: str = "HABIT"       # "HABIT" | "SATISFACTION"
    currentRating: int | None = None
    position: int = 0         # Reihenfolge (Drag-Drop)
    createdAt: int
    updatedAt: int
    deletedAt: int | None = None


class HabitLogDTO(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    id: str
    userId: str | None = None
    habitId: str
    timestamp: int
    note: str = ""


class HabitHistoryEntryDTO(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    id: str
    userId: str | None = None
    habitId: str
    title: str
    cadenceLabel: str
    periodStart: int
    count: int
    goal: int
    newRating: int | None = None   # Satisfaction: Rating-Änderung (0-10)
    loggedAt: int


class FolderDTO(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    id: str
    userId: str | None = None
    parentId: str | None = None
    name: str
    createdAt: int
    updatedAt: int
    deletedAt: int | None = None


class NoteDTO(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    id: str
    userId: str | None = None
    folderId: str | None = None
    type: str = "NOTE"          # "NOTE" | "CHAT" (Block H)
    title: str = ""
    bodyJson: str = "[]"
    createdAt: int
    updatedAt: int
    deletedAt: int | None = None
    position: int = 0           # Reihenfolge im Ordner (Sync der Reorder)


class ChatMessageDTO(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    id: str
    userId: str | None = None
    noteId: str
    text: str
    createdAt: int
    updatedAt: int
    deletedAt: int | None = None
    position: int = 0
    quotedMessageId: str | None = None


class ChangesBundle(BaseModel):
    todos: list[TodoDTO] = []
    habits: list[HabitDTO] = []
    habit_logs: list[HabitLogDTO] = []
    habit_history: list[HabitHistoryEntryDTO] = []
    folders: list[FolderDTO] = []
    notes: list[NoteDTO] = []
    chat_messages: list[ChatMessageDTO] = []


class SyncRequest(BaseModel):
    last_synced_at: int = 0          # 0 = initialer Pull (alles)
    client_id: str                   # Identifikation des Clients (für Logging)
    changes: ChangesBundle = ChangesBundle()
    notify: bool = True              # false = SSE-Pull (kein Ping-Pong)


class SyncResponse(BaseModel):
    new_synced_at: int
    server_changes: ChangesBundle


# --- Auth-Models (M1: Multi-User) ---

class RegisterRequest(BaseModel):
    username: str
    password: str


class LoginRequest(BaseModel):
    username: str
    password: str


class AuthResponse(BaseModel):
    user_id: str
    token: str


class MigrateLegacyRequest(BaseModel):
    username: str
    password: str


# --- M7d-3: Setup + Profil + Admin ---

class SetupStatusResponse(BaseModel):
    admin_exists: bool
    open_registration: bool


class SetupRequest(BaseModel):
    username: str
    password: str
    display_name: str = ""


class UserProfileResponse(BaseModel):
    user_id: str
    username: str
    display_name: str = ""
    is_admin: bool
    profile_picture: str | None = None


class UpdateProfileRequest(BaseModel):
    display_name: str | None = None
    password: str | None = None


class AdminUserResponse(BaseModel):
    user_id: str
    username: str
    display_name: str = ""
    is_admin: bool
    created_at: int


class AdminCreateUserRequest(BaseModel):
    username: str
    password: str
    display_name: str = ""
    is_admin: bool = False


class UpdateSettingsRequest(BaseModel):
    open_registration: bool | None = None


class PasswordConfirmRequest(BaseModel):
    """Passwort-Bestätigung für destruktive Aktionen (Daten löschen)."""
    password: str
