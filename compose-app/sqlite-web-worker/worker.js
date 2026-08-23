/**
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License");
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import sqlite3InitModule from '@sqlite.org/sqlite-wasm';

let sqlite3 = null;
// Perf: opfs-sahpool VFS (3-4x schneller als der alte "opfs"-VFS, der
// pro VFS-Call eine Thread-Grenze via Atomics überqueren muss — gemessen
// ~900ms pro Commit). SAHPool verwaltet die DB-Dateien direkt über
// SyncAccessHandles ohne separaten Proxy-Thread.
// null = SAHPool nicht verfügbar → Fallback auf OpfsDb (alter VFS).
let sahpool = null;

// Maps to track active database connections and prepared statements by their unique IDs.
const databases = new Map(); // stores databaseId -> SQLiteDbObject
const statements = new Map(); // stores statementId -> SQLiteStatementObject

// Counters to generate unique IDs for new database connections and statements.
let nextDatabaseId = 0;
let nextStatementId = 0;

function openRequest(id, requestData) {
    try {
        const newDatabaseId = nextDatabaseId++;
        let newDatabase;
        if (sahpool) {
            newDatabase = new sahpool.OpfsSAHPoolDb(requestData.fileName);
        } else if (sqlite3.oo1 && sqlite3.oo1.OpfsDb) {
            // Fallback: alter OPFS-VFS (langsamer, aber überall verfügbar wo OpfsDb geht).
            newDatabase = new sqlite3.oo1.OpfsDb(requestData.fileName);
            // Perf: Defaults (journal_mode=DELETE, synchronous=FULL) sind extrem
            // langsam auf OPFS — ~900ms pro Commit gemessen.
            newDatabase.exec("PRAGMA synchronous=NORMAL;");
        } else {
            // OPFS gar nicht verfügbar (z.B. Firefox privat / fehlende
            // COOP/COEP-Header) → klassischer JS-WebSQL-artiger Speicher:
            // best effort mit "kvvfs" (localStorage, ~5MB Limit).
            console.warn('[sqlite-worker] OPFS nicht verfügbar — kvvfs (localStorage, ~5MB).');
            newDatabase = new sqlite3.oo1.DB(requestData.fileName, 'kvvfs');
        }
        databases.set(newDatabaseId, newDatabase);
        postMessage({'id': id, data: {'databaseId': newDatabaseId}});
    } catch (error) {
        postMessage({'id': id, error: error.message});
    }
}

function prepareRequest(id, requestData) {
    try {
        const newStatementId = nextStatementId++;
        const resultData = {
            'statementId': newStatementId,
            'parameterCount': 0,
            'columnNames': []
        };
        const database = databases.get(requestData.databaseId);
        if (!database) {
            postMessage({'id': id, error: "Invalid database ID: " + requestData.databaseId});
            return;
        }
        const statement = database.prepare(requestData.sql);
        statements.set(newStatementId, statement);
        resultData.parameterCount = sqlite3.capi.sqlite3_bind_parameter_count(statement);
        for (let i = 0; i < statement.columnCount; i++) {
            resultData.columnNames.push(sqlite3.capi.sqlite3_column_name(statement, i));
        }
        postMessage({'id': id, data: resultData});
    } catch (error) {
        postMessage({'id': id, error: error.message});
    }
}

function stepRequest(id, requestData) {
    const statement = statements.get(requestData.statementId);
    if (!statement) {
        postMessage({'id': id, error: "Invalid statement ID: " + requestData.statementId});
        return;
    }
    try {
        const resultData = {
            'rows': [],
            'columnTypes': []
        };
        statement.reset();
        statement.clearBindings();
        for (let i = 0; i < requestData.bindings.length; i++) {
            statement.bind(i + 1, requestData.bindings[i]);
        }
        while (statement.step()) {
            if (!resultData.columnTypes.length) {
                for (let i = 0; i < statement.columnCount; i++) {
                    resultData.columnTypes.push(sqlite3.capi.sqlite3_column_type(statement, i));
                }
            }
            resultData.rows.push(statement.get([]));
        }
        postMessage({'id': id, data: resultData});
    } catch (error) {
        postMessage({'id': id, error: error.message});
    }
}

function closeRequest(id, requestData) {
    if (requestData.statementId !== undefined && requestData.statementId != null) {
        const statement = statements.get(requestData.statementId);
        if (!statement) {
            postMessage({'id': id, error: "Invalid statement ID: " + requestData.statementId});
            return;
        }
        try {
            statement.finalize();
            statements.delete(requestData.statementId);
        } catch (error) {
            postMessage({'id': id, error: error.message});
        }
    }

    if (requestData.databaseId !== undefined && requestData.databaseId != null) {
        const database = databases.get(requestData.databaseId);
        if (!database) {
            postMessage({'id': id, error: "Invalid database ID: " + requestData.databaseId});
            return;
        }
        try {
            database.close();
            databases.delete(requestData.databaseId);
        } catch (error) {
            postMessage({'id': id, error: error.message});
        }
    }
}

// A map that links command names (strings) to their respective handler functions.
const commandMap = {
    'open': openRequest,
    'prepare': prepareRequest,
    'step': stepRequest,
    'close': closeRequest,
};

function handleMessage(e) {
    const requestMsg = e.data;
    if (!Object.hasOwn(requestMsg, 'data') && requestMsg.data == null) {
        postMessage(
            {'id': requestMsg.id, 'error': "Invalid request, missing 'data'."}
        );
        return;
    }
    if (!Object.hasOwn(requestMsg.data, 'cmd') && requestMsg.data.cmd == null) {
        postMessage(
            {'id': requestMsg.id, 'error': "Invalid request, missing 'cmd'."}
        );
        return;
    }
    const command = requestMsg.data.cmd;
    const requestHandler = commandMap[command];
    if (requestHandler) {
        requestHandler(requestMsg.id, requestMsg.data);
    } else {
        postMessage(
            {'id': requestMsg.id, 'error': "Invalid request, unknown command: '" + command + "'."}
        );
    }
}

const messageQueue = [];
onmessage = (e) => {
    if (!sqlite3) {
        messageQueue.push(e);
    } else {
        handleMessage(e);
    }
};

/**
 * Migriert eine bestehende DB-Datei des alten "opfs"-VFS (liegt im
 * OPFS-Root unter <fileName>) in den SAHPool (sofern dort noch keine
 * Datei mit diesem Namen existiert). Einmalige Migration beim Start.
 */
async function migrateLegacyDbToSahpool(fileName) {
    if (sahpool.getFileNames().includes(fileName)) return; // schon migriert
    const dh = await navigator.storage.getDirectory();
    let file;
    try {
        const fh = await dh.getFileHandle(fileName);
        file = await fh.getFile();
    } catch (e) {
        return; // keine Legacy-DB vorhanden
    }
    if (file.size === 0) return;
    const bytes = new Uint8Array(await file.arrayBuffer());
    if (bytes.length === 0) return;
    sahpool.importDb(fileName, bytes);
    console.log('[sqlite-worker] Legacy-DB migriert nach opfs-sahpool:', fileName,
        '(' + bytes.length + ' bytes)');
    // Alte Datei im OPFS-Root entfernen — sie wird ab jetzt ignoriert.
    // (Backup verbleibt bis zum naechsten Reload im Dateisystem? Nein —
    // removeEntry ist endgueltig. Aber die Daten sind ja jetzt im Pool,
    // und ein fehlgeschlagener Import haette geworfen bevor wir hier ankommen.)
    try {
        await dh.removeEntry(fileName);
    } catch (e) { /* egal */ }
}

sqlite3InitModule().then(async instance => {
    sqlite3 = instance;
    try {
        sahpool = await sqlite3.installOpfsSAHPoolVfs();
    } catch (e) {
        console.warn('[sqlite-worker] opfs-sahpool nicht verfügbar, '
            + 'Fallback auf langsameren opfs-VFS:', e);
        sahpool = null;
    }
    if (sahpool) {
        // Einmalige Migration: bestehende User-Daten in den Pool übernehmen.
        try {
            await migrateLegacyDbToSahpool('todonotes.db');
        } catch (e) {
            console.warn('[sqlite-worker] Legacy-Migration fehlgeschlagen:', e);
        }
    }
    while (messageQueue.length > 0) {
        handleMessage(messageQueue.shift());
    }
});
