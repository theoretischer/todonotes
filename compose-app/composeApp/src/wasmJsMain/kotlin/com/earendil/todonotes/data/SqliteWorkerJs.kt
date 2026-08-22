@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@file:JsModule("./sqlite-worker.js")

package com.earendil.todonotes.data

import org.w3c.dom.Worker

/**
 * External-Deklaration für die JS-Funktion in sqlite-worker.js,
 * die den SQLite-Web-Worker erstellt.
 */
external fun createSqliteWorker(): Worker
