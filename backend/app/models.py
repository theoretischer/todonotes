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
    title: str
    notes: str = ""
    dueAt: int | None = None
    recurrence: str | None = None
    completedAt: int | None = None
    createdAt: int
    updatedAt: int
    deletedAt: int | None = None
    logToHistory: bool = True


class HabitDTO(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    id: str
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
    createdAt: int
    updatedAt: int
    deletedAt: int | None = None


class HabitLogDTO(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    id: str
    habitId: str
    timestamp: int
    note: str = ""


class HabitHistoryEntryDTO(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    id: str
    habitId: str
    title: str
    cadenceLabel: str
    periodStart: int
    count: int
    goal: int
    loggedAt: int


class FolderDTO(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    id: str
    parentId: str | None = None
    name: str
    createdAt: int
    updatedAt: int
    deletedAt: int | None = None


class NoteDTO(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    id: str
    folderId: str | None = None
    type: str = "NOTE"          # "NOTE" | "CHAT" (Block H)
    title: str = ""
    bodyJson: str = "[]"
    createdAt: int
    updatedAt: int
    deletedAt: int | None = None


class ChatMessageDTO(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    id: str
    noteId: str
    text: str
    createdAt: int
    updatedAt: int
    deletedAt: int | None = None
    position: int = 0


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


class SyncResponse(BaseModel):
    new_synced_at: int
    server_changes: ChangesBundle
