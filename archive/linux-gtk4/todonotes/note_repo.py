"""Folder + Note Repository — Port der Kotlin FolderRepository/NoteRepository."""
from __future__ import annotations

from typing import Any

from . import db, util


# ── Folders ─────────────────────────────────────────────────────

def fetch_folders(parent_id: str | None = None) -> list[dict[str, Any]]:
    """Ordner unter parent_id (null = Wurzel)."""
    with db.db() as conn:
        if parent_id is None:
            rows = conn.execute(
                """SELECT * FROM folders
                   WHERE parentId IS NULL AND deletedAt IS NULL
                   ORDER BY name COLLATE NOCASE"""
            ).fetchall()
        else:
            rows = conn.execute(
                """SELECT * FROM folders
                   WHERE parentId = ? AND deletedAt IS NULL
                   ORDER BY name COLLATE NOCASE""",
                (parent_id,),
            ).fetchall()
    return [dict(r) for r in rows]


def get_folder(folder_id: str) -> dict[str, Any] | None:
    return db.fetch_one("folders", folder_id)


def create_folder(parent_id: str | None, name: str) -> dict[str, Any]:
    now = util.now_millis()
    folder = {
        "id": util.new_id(),
        "parentId": parent_id,
        "name": name.strip(),
        "createdAt": now,
        "updatedAt": now,
        "deletedAt": None,
        "position": 0,
    }
    db.upsert("folders", folder)
    return folder


def rename_folder(folder_id: str, name: str) -> None:
    _update_folder_fields(folder_id, name=name.strip(), updatedAt=util.now_millis())


def soft_delete_folder(folder_id: str) -> None:
    _update_folder_fields(folder_id, deletedAt=util.now_millis(), updatedAt=util.now_millis())


def move_folder(folder_id: str, new_parent_id: str | None) -> None:
    _update_folder_fields(
        folder_id, parentId=new_parent_id, updatedAt=util.now_millis()
    )


def _update_folder_fields(folder_id: str, **fields) -> None:
    if not fields:
        return
    sets = ",".join(f"{k} = ?" for k in fields)
    vals = list(fields.values()) + [folder_id]
    with db.db() as conn:
        conn.execute(f"UPDATE folders SET {sets} WHERE id = ?", vals)


# ── Notes ───────────────────────────────────────────────────────

def fetch_notes(folder_id: str | None = None) -> list[dict[str, Any]]:
    """Notizen unter folder_id (null = Wurzel). Inklusive Chat-Notizen."""
    with db.db() as conn:
        if folder_id is None:
            rows = conn.execute(
                """SELECT * FROM notes
                   WHERE folderId IS NULL AND deletedAt IS NULL
                   ORDER BY updatedAt DESC"""
            ).fetchall()
        else:
            rows = conn.execute(
                """SELECT * FROM notes
                   WHERE folderId = ? AND deletedAt IS NULL
                   ORDER BY updatedAt DESC""",
                (folder_id,),
            ).fetchall()
    return [dict(r) for r in rows]


def get_note(note_id: str) -> dict[str, Any] | None:
    return db.fetch_one("notes", note_id)


def create_note(
    folder_id: str | None = None,
    title: str = "",
    body_json: str = "[]",
    note_type: str = "NOTE",
) -> dict[str, Any]:
    now = util.now_millis()
    note = {
        "id": util.new_id(),
        "folderId": folder_id,
        "type": note_type,
        "title": title,
        "bodyJson": body_json,
        "createdAt": now,
        "updatedAt": now,
        "deletedAt": None,
        "position": 0,
    }
    db.upsert("notes", note)
    return note


def create_chat_note(folder_id: str | None = None, title: str = "") -> dict[str, Any]:
    return create_note(folder_id=folder_id, title=title, note_type="CHAT")


def update_note(note_id: str, **fields) -> None:
    fields["updatedAt"] = util.now_millis()
    sets = ",".join(f"{k} = ?" for k in fields)
    vals = [db._bool_to_int(v) for v in fields.values()] + [note_id]
    with db.db() as conn:
        conn.execute(f"UPDATE notes SET {sets} WHERE id = ?", vals)


def soft_delete_note(note_id: str) -> None:
    update_note(note_id, deletedAt=util.now_millis())


def move_note(note_id: str, new_folder_id: str | None) -> None:
    update_note(note_id, folderId=new_folder_id)


def default_note_title() -> str:
    """Standard-Titel für neue Notiz (wie Android)."""
    return util.format_date_time(util.now_millis())


# ── Chat Messages ──────────────────────────────────────────────

def fetch_chat_messages(note_id: str) -> list[dict[str, Any]]:
    """Nachrichten eines Chats (älteste oben, neueste unten)."""
    with db.db() as conn:
        rows = conn.execute(
            """SELECT * FROM chat_messages
               WHERE noteId = ? AND deletedAt IS NULL
               ORDER BY position ASC, createdAt ASC""",
            (note_id,),
        ).fetchall()
    return [dict(r) for r in rows]


def send_message(
    note_id: str, text: str, quoted_message_id: str | None = None
) -> dict[str, Any]:
    now = util.now_millis()
    # position = max + 1
    with db.db() as conn:
        row = conn.execute(
            "SELECT MAX(position) as p FROM chat_messages WHERE noteId = ?", (note_id,)
        ).fetchone()
    max_pos = row["p"] if row and row["p"] is not None else 0
    msg = {
        "id": util.new_id(),
        "noteId": note_id,
        "text": text,
        "createdAt": now,
        "updatedAt": now,
        "deletedAt": None,
        "position": max_pos + 1,
        "quotedMessageId": quoted_message_id,
    }
    db.upsert("chat_messages", msg)
    # Notiz updatedAt anfassen
    update_note(note_id, updatedAt=now)
    return msg


def edit_message(message_id: str, text: str) -> None:
    now = util.now_millis()
    with db.db() as conn:
        conn.execute(
            "UPDATE chat_messages SET text = ?, updatedAt = ? WHERE id = ?",
            (text, now, message_id),
        )


def soft_delete_message(message_id: str) -> None:
    now = util.now_millis()
    with db.db() as conn:
        conn.execute(
            "UPDATE chat_messages SET deletedAt = ?, updatedAt = ? WHERE id = ?",
            (now, now, message_id),
        )
