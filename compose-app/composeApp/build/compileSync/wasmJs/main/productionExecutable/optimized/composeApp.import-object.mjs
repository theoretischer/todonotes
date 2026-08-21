
import * as Li9za2lrby5tanM from './skiko.mjs';
import * as QGpzLWpvZGEvY29yZQ from '@js-joda/core';
import * as d2FzbTpqcy1zdHJpbmc from './composeApp.js-builtins.mjs';

const wasmJsTag = WebAssembly.JSTag;
const wasmTag = wasmJsTag ?? new WebAssembly.Tag({ parameters: ['externref'] });

// Placed here to give access to it from externals (js_code)
let wasmExports;
let require;

if (typeof process !== 'undefined' && process.release.name === 'node') {
    const module = await import(/* webpackIgnore: true */'node:module');
    const importMeta = import.meta;
    require = module.default.createRequire(importMeta.url);
}

export function setWasmExports(exports) {
    wasmExports = exports;
}

const _ref_Li9za2lrby5tanM_ = Li9za2lrby5tanM;
const _ref_QGpzLWpvZGEvY29yZQ_ = QGpzLWpvZGEvY29yZQ;

const cachedJsObjects = new WeakMap();
function getCachedJsObject(ref, ifNotCached) {
    if (typeof ref !== 'object' && typeof ref !== 'function') return ifNotCached;
    const cached = cachedJsObjects.get(ref);
    if (cached !== void 0) return cached;
    cachedJsObjects.set(ref, ifNotCached);
    return ifNotCached;
}

const js_code = {
    'kotlin.createJsError' : (message, cause) => new Error(message, { cause }),
    'kotlin.wasm.internal.jsThrow' : wasmTag === wasmJsTag ? (e) => { throw e; } : () => {},
    'kotlin.wasm.internal.getJsEmptyString' : () => '',
    'kotlin.wasm.internal.externrefToBoolean' : (ref) => Boolean(ref),
    'kotlin.wasm.internal.externrefToInt' : (ref) => Number(ref),
    'kotlin.wasm.internal.externrefToString' : (ref) => String(ref),
    'kotlin.wasm.internal.externrefEquals' : (lhs, rhs) => lhs === rhs,
    'kotlin.wasm.internal.externrefHashCode' : 
    (() => {
    const dataView = new DataView(new ArrayBuffer(8));
    function numberHashCode(obj) {
        if ((obj | 0) === obj) {
            return obj | 0;
        } else {
            dataView.setFloat64(0, obj, true);
            return (dataView.getInt32(0, true) * 31 | 0) + dataView.getInt32(4, true) | 0;
        }
    }

    const hashCodes = new WeakMap();
    function getObjectHashCode(obj) {
        const res = hashCodes.get(obj);
        if (res === undefined) {
            const POW_2_32 = 4294967296;
            const hash = (Math.random() * POW_2_32) | 0;
            hashCodes.set(obj, hash);
            return hash;
        }
        return res;
    }

    function getStringHashCode(str) {
        var hash = 0;
        for (var i = 0; i < str.length; i++) {
            var code  = str.charCodeAt(i);
            hash  = (hash * 31 + code) | 0;
        }
        return hash;
    }

    return (obj) => {
        if (obj == null) {
            return 0;
        }
        switch (typeof obj) {
            case "object":
            case "function":
                return getObjectHashCode(obj);
            case "number":
                return numberHashCode(obj);
            case "boolean":
                return obj ? 1231 : 1237;
            default:
                return getStringHashCode(String(obj)); 
        }
    }
    })(),
    'kotlin.wasm.internal.isNullish' : (ref) => ref == null,
    'kotlin.wasm.internal.getJsTrue' : () => true,
    'kotlin.wasm.internal.getJsFalse' : () => false,
    'kotlin.wasm.internal.kotlinUIntToJsNumberUnsafe' : (x) => x >>> 0,
    'kotlin.wasm.internal.kotlinULongToJsBigIntUnsafe' : (x) => x & 0xFFFFFFFFFFFFFFFFn,
    'kotlin.wasm.internal.getCachedJsObject_$external_fun' : (p0, p1) => getCachedJsObject(p0, p1),
    'kotlin.wasm.internal.itoa32_$external_fun' : (p0) => String(p0),
    'kotlin.wasm.internal.itoa64_$external_fun' : (p0) => String(p0),
    'kotlin.wasm.internal.utoa64_$external_fun' : (p0) => String(p0),
    'kotlin.wasm.internal.utoa32_$external_fun' : (p0) => String(p0),
    'kotlin.io.printlnImpl' : (message) => console.log(message),
    'kotlin.io.printError' : (error) => console.error(error),
    'kotlin.js.jsArrayGet' : (array, index) => array[index],
    'kotlin.js.jsArraySet' : (array, index, value) => { array[index] = value },
    'kotlin.js.JsArray_$external_fun' : () => new Array(),
    'kotlin.js.length_$external_prop_getter' : (_this) => _this.length,
    'kotlin.js.stackPlaceHolder_js_code' : () => (''),
    'kotlin.js.message_$external_prop_getter' : (_this) => _this.message,
    'kotlin.js.name_$external_prop_setter' : (_this, v) => _this.name = v,
    'kotlin.js.stack_$external_prop_getter' : (_this) => _this.stack,
    'kotlin.js.kotlinException_$external_prop_getter' : (_this) => _this.kotlinException,
    'kotlin.js.kotlinException_$external_prop_setter' : (_this, v) => _this.kotlinException = v,
    'kotlin.js.JsError_$external_class_instanceof' : (x) => x instanceof Error,
    'kotlin.js.JsString_$external_class_instanceof' : (x) => typeof x === 'string',
    'kotlin.js.JsString_$external_class_get' : () => JsString,
    'kotlin.random.initialSeed' : () => ((Math.random() * Math.pow(2, 32)) | 0),
    'kotlin.wasm.internal.getJsClassName' : (jsKlass) => jsKlass.name,
    'kotlin.wasm.internal.getConstructor' : (obj) => obj.constructor,
    'kotlinx.browser.window_$external_prop_getter' : () => window,
    'kotlinx.browser.document_$external_prop_getter' : () => document,
    'org.w3c.dom.length_$external_prop_getter' : (_this) => _this.length,
    'org.w3c.dom.item_$external_fun' : (_this, p0) => _this.item(p0),
    'org.w3c.dom.css.cursor_$external_prop_setter' : (_this, v) => _this.cursor = v,
    'org.w3c.dom.css.height_$external_prop_setter' : (_this, v) => _this.height = v,
    'org.w3c.dom.css.left_$external_prop_setter' : (_this, v) => _this.left = v,
    'org.w3c.dom.css.position_$external_prop_setter' : (_this, v) => _this.position = v,
    'org.w3c.dom.css.top_$external_prop_setter' : (_this, v) => _this.top = v,
    'org.w3c.dom.css.width_$external_prop_setter' : (_this, v) => _this.width = v,
    'org.w3c.dom.css.setProperty_$external_fun' : (_this, p0, p1, p2, isDefault0) => _this.setProperty(p0, p1, isDefault0 ? undefined : p2, ),
    'org.w3c.dom.css.style_$external_prop_getter' : (_this) => _this.style,
    'org.w3c.dom.events.type_$external_prop_getter' : (_this) => _this.type,
    'org.w3c.dom.events.preventDefault_$external_fun' : (_this, ) => _this.preventDefault(),
    'org.w3c.dom.events.Event_$external_class_instanceof' : (x) => x instanceof Event,
    'org.w3c.dom.events.Event_$external_class_get' : () => Event,
    'org.w3c.dom.events.key_$external_prop_getter' : (_this) => _this.key,
    'org.w3c.dom.events.code_$external_prop_getter' : (_this) => _this.code,
    'org.w3c.dom.events.ctrlKey_$external_prop_getter' : (_this) => _this.ctrlKey,
    'org.w3c.dom.events.shiftKey_$external_prop_getter' : (_this) => _this.shiftKey,
    'org.w3c.dom.events.altKey_$external_prop_getter' : (_this) => _this.altKey,
    'org.w3c.dom.events.metaKey_$external_prop_getter' : (_this) => _this.metaKey,
    'org.w3c.dom.events.KeyboardEvent_$external_class_instanceof' : (x) => x instanceof KeyboardEvent,
    'org.w3c.dom.events.KeyboardEvent_$external_class_get' : () => KeyboardEvent,
    'org.w3c.dom.events.addEventListener_$external_fun' : (_this, p0, p1, p2) => _this.addEventListener(p0, p1, p2),
    'org.w3c.dom.events.__convertKotlinClosureToJsClosure_((Js)->Unit)' : (f) => getCachedJsObject(f, (p0) => wasmExports['__callFunction_((Js)->Unit)'](f, p0)),
    'org.w3c.dom.events.addEventListener_$external_fun_1' : (_this, p0, p1) => _this.addEventListener(p0, p1),
    'org.w3c.dom.events.ctrlKey_$external_prop_getter_1' : (_this) => _this.ctrlKey,
    'org.w3c.dom.events.shiftKey_$external_prop_getter_1' : (_this) => _this.shiftKey,
    'org.w3c.dom.events.altKey_$external_prop_getter_1' : (_this) => _this.altKey,
    'org.w3c.dom.events.metaKey_$external_prop_getter_1' : (_this) => _this.metaKey,
    'org.w3c.dom.events.button_$external_prop_getter' : (_this) => _this.button,
    'org.w3c.dom.events.buttons_$external_prop_getter' : (_this) => _this.buttons,
    'org.w3c.dom.events.offsetX_$external_prop_getter' : (_this) => _this.offsetX,
    'org.w3c.dom.events.offsetY_$external_prop_getter' : (_this) => _this.offsetY,
    'org.w3c.dom.events.MouseEvent_$external_class_instanceof' : (x) => x instanceof MouseEvent,
    'org.w3c.dom.events.MouseEvent_$external_class_get' : () => MouseEvent,
    'org.w3c.dom.events.deltaX_$external_prop_getter' : (_this) => _this.deltaX,
    'org.w3c.dom.events.deltaY_$external_prop_getter' : (_this) => _this.deltaY,
    'org.w3c.dom.events.WheelEvent_$external_class_instanceof' : (x) => x instanceof WheelEvent,
    'org.w3c.dom.events.WheelEvent_$external_class_get' : () => WheelEvent,
    'org.w3c.dom.AddEventListenerOptions_js_code' : (passive, once, capture) => { return { passive, once, capture }; },
    'org.w3c.dom.devicePixelRatio_$external_prop_getter' : (_this) => _this.devicePixelRatio,
    'org.w3c.dom.requestAnimationFrame_$external_fun' : (_this, p0) => _this.requestAnimationFrame(p0),
    'org.w3c.dom.__convertKotlinClosureToJsClosure_((Double)->Unit)' : (f) => getCachedJsObject(f, (p0) => wasmExports['__callFunction_((Double)->Unit)'](f, p0)),
    'org.w3c.dom.matchMedia_$external_fun' : (_this, p0) => _this.matchMedia(p0),
    'org.w3c.dom.matches_$external_prop_getter' : (_this) => _this.matches,
    'org.w3c.dom.addListener_$external_fun' : (_this, p0) => _this.addListener(p0),
    'org.w3c.dom.MediaQueryList_$external_class_instanceof' : (x) => x instanceof MediaQueryList,
    'org.w3c.dom.MediaQueryList_$external_class_get' : () => MediaQueryList,
    'org.w3c.dom.dropEffect_$external_prop_setter' : (_this, v) => _this.dropEffect = v,
    'org.w3c.dom.setDragImage_$external_fun' : (_this, p0, p1, p2) => _this.setDragImage(p0, p1, p2),
    'org.w3c.dom.documentElement_$external_prop_getter' : (_this) => _this.documentElement,
    'org.w3c.dom.body_$external_prop_getter' : (_this) => _this.body,
    'org.w3c.dom.head_$external_prop_getter' : (_this) => _this.head,
    'org.w3c.dom.createElement_$external_fun' : (_this, p0, p1, isDefault0) => _this.createElement(p0, isDefault0 ? undefined : p1, ),
    'org.w3c.dom.createTextNode_$external_fun' : (_this, p0) => _this.createTextNode(p0),
    'org.w3c.dom.hasFocus_$external_fun' : (_this, ) => _this.hasFocus(),
    'org.w3c.dom.getElementById_$external_fun' : (_this, p0) => _this.getElementById(p0),
    'org.w3c.dom.clearTimeout_$external_fun' : (_this, p0, isDefault0) => _this.clearTimeout(isDefault0 ? undefined : p0, ),
    'org.w3c.dom.clientWidth_$external_prop_getter' : (_this) => _this.clientWidth,
    'org.w3c.dom.clientHeight_$external_prop_getter' : (_this) => _this.clientHeight,
    'org.w3c.dom.setAttribute_$external_fun' : (_this, p0, p1) => _this.setAttribute(p0, p1),
    'org.w3c.dom.getElementsByTagName_$external_fun' : (_this, p0) => _this.getElementsByTagName(p0),
    'org.w3c.dom.getBoundingClientRect_$external_fun' : (_this, ) => _this.getBoundingClientRect(),
    'org.w3c.dom.textContent_$external_prop_setter' : (_this, v) => _this.textContent = v,
    'org.w3c.dom.appendChild_$external_fun' : (_this, p0) => _this.appendChild(p0),
    'org.w3c.dom.item_$external_fun_1' : (_this, p0) => _this.item(p0),
    'org.w3c.dom.dataTransfer_$external_prop_getter' : (_this) => _this.dataTransfer,
    'org.w3c.dom.DragEvent_$external_class_instanceof' : (x) => x instanceof DragEvent,
    'org.w3c.dom.DragEvent_$external_class_get' : () => DragEvent,
    'org.w3c.dom.identifier_$external_prop_getter' : (_this) => _this.identifier,
    'org.w3c.dom.clientX_$external_prop_getter' : (_this) => _this.clientX,
    'org.w3c.dom.clientY_$external_prop_getter' : (_this) => _this.clientY,
    'org.w3c.dom.top_$external_prop_getter' : (_this) => _this.top,
    'org.w3c.dom.left_$external_prop_getter' : (_this) => _this.left,
    'org.w3c.dom.remove_$external_fun' : (_this, ) => _this.remove(),
    'org.w3c.dom.HTMLTitleElement_$external_class_instanceof' : (x) => x instanceof HTMLTitleElement,
    'org.w3c.dom.HTMLTitleElement_$external_class_get' : () => HTMLTitleElement,
    'org.w3c.dom.type_$external_prop_setter' : (_this, v) => _this.type = v,
    'org.w3c.dom.HTMLStyleElement_$external_class_instanceof' : (x) => x instanceof HTMLStyleElement,
    'org.w3c.dom.HTMLStyleElement_$external_class_get' : () => HTMLStyleElement,
    'org.w3c.dom.width_$external_prop_setter' : (_this, v) => _this.width = v,
    'org.w3c.dom.height_$external_prop_setter' : (_this, v) => _this.height = v,
    'org.w3c.dom.HTMLCanvasElement_$external_class_instanceof' : (x) => x instanceof HTMLCanvasElement,
    'org.w3c.dom.HTMLCanvasElement_$external_class_get' : () => HTMLCanvasElement,
    'org.w3c.dom.targetTouches_$external_prop_getter' : (_this) => _this.targetTouches,
    'org.w3c.dom.changedTouches_$external_prop_getter' : (_this) => _this.changedTouches,
    'org.w3c.dom.TouchEvent_$external_class_instanceof' : (x) => x instanceof TouchEvent,
    'org.w3c.dom.TouchEvent_$external_class_get' : () => TouchEvent,
    'org.w3c.dom.matches_$external_prop_getter_1' : (_this) => _this.matches,
    'org.w3c.dom.MediaQueryListEvent_$external_class_instanceof' : (x) => x instanceof MediaQueryListEvent,
    'org.w3c.dom.MediaQueryListEvent_$external_class_get' : () => MediaQueryListEvent,
    'org.w3c.performance.now_$external_fun' : (_this, ) => _this.now(),
    'org.w3c.performance.performance_$external_prop_getter' : (_this) => _this.performance,
    'kotlinx.coroutines.tryGetProcess' : () => (typeof(process) !== 'undefined' && typeof(process.nextTick) === 'function') ? process : null,
    'kotlinx.coroutines.tryGetWindow' : () => (typeof(window) !== 'undefined' && window != null && typeof(window.addEventListener) === 'function') ? window : null,
    'kotlinx.coroutines.nextTick_$external_fun' : (_this, p0) => _this.nextTick(p0),
    'kotlinx.coroutines.__convertKotlinClosureToJsClosure_(()->Unit)' : (f) => getCachedJsObject(f, () => wasmExports['__callFunction_(()->Unit)'](f, )),
    'kotlinx.coroutines.error_$external_fun' : (_this, p0) => _this.error(p0),
    'kotlinx.coroutines.console_$external_prop_getter' : () => console,
    'kotlinx.coroutines.createScheduleMessagePoster' : (process) => () => Promise.resolve(0).then(process),
    'kotlinx.coroutines.__callJsClosure_(()->Unit)' : (f, ) => f(),
    'kotlinx.coroutines.createRescheduleMessagePoster' : (window) => () => window.postMessage('dispatchCoroutine', '*'),
    'kotlinx.coroutines.subscribeToWindowMessages' : (window, process) => {
        const handler = (event) => {
            if (event.source == window && event.data == 'dispatchCoroutine') {
                event.stopPropagation();
                process();
            }
        }
        window.addEventListener('message', handler, true);
    },
    'kotlinx.coroutines.setTimeout' : (window, handler, timeout) => window.setTimeout(handler, timeout),
    'kotlinx.coroutines.clearTimeout' : (handle) => { if (typeof clearTimeout !== 'undefined') clearTimeout(handle); },
    'kotlinx.coroutines.setTimeout_$external_fun' : (p0, p1) => setTimeout(p0, p1),
    'androidx.compose.runtime.internal.weakMap_js_code' : () => (new WeakMap()),
    'androidx.compose.runtime.internal.set_$external_fun' : (_this, p0, p1) => _this.set(p0, p1),
    'androidx.compose.runtime.internal.get_$external_fun' : (_this, p0) => _this.get(p0),
    'org.jetbrains.skiko.w3c.language_$external_prop_getter' : (_this) => _this.language,
    'org.jetbrains.skiko.w3c.userAgent_$external_prop_getter' : (_this) => _this.userAgent,
    'org.jetbrains.skiko.w3c.navigator_$external_prop_getter' : (_this) => _this.navigator,
    'org.jetbrains.skiko.w3c.performance_$external_prop_getter' : (_this) => _this.performance,
    'org.jetbrains.skiko.w3c.requestAnimationFrame_$external_fun' : (_this, p0) => _this.requestAnimationFrame(p0),
    'org.jetbrains.skiko.w3c.window_$external_object_getInstance' : () => window,
    'org.jetbrains.skiko.w3c.now_$external_fun' : (_this, ) => _this.now(),
    'org.jetbrains.skiko.w3c.width_$external_prop_getter' : (_this) => _this.width,
    'org.jetbrains.skiko.w3c.height_$external_prop_getter' : (_this) => _this.height,
    'org.jetbrains.skiko.w3c.HTMLCanvasElement_$external_class_instanceof' : (x) => x instanceof HTMLCanvasElement,
    'org.jetbrains.skiko.w3c.HTMLCanvasElement_$external_class_get' : () => HTMLCanvasElement,
    'org.jetbrains.skia.impl.FinalizationRegistry_$external_fun' : (p0) => new FinalizationRegistry(p0),
    'org.jetbrains.skia.impl.register_$external_fun' : (_this, p0, p1) => _this.register(p0, p1),
    'org.jetbrains.skia.impl.unregister_$external_fun' : (_this, p0) => _this.unregister(p0),
    'org.jetbrains.skia.impl._releaseLocalCallbackScope_$external_fun' : () => _ref_Li9za2lrby5tanM_._releaseLocalCallbackScope(),
    'org.jetbrains.skiko.getNavigatorInfo' : () => navigator.userAgentData ? navigator.userAgentData.platform : navigator.platform,
    'org.jetbrains.skiko.wasm.createContext_$external_fun' : (_this, p0, p1) => _this.createContext(p0, p1),
    'org.jetbrains.skiko.wasm.makeContextCurrent_$external_fun' : (_this, p0) => _this.makeContextCurrent(p0),
    'org.jetbrains.skiko.wasm.GL_$external_object_getInstance' : () => _ref_Li9za2lrby5tanM_.GL,
    'org.jetbrains.skiko.wasm.createDefaultContextAttributes' : () => {
        return {
            alpha: 1,
            depth: 1,
            stencil: 8,
            antialias: 0,
            premultipliedAlpha: 1,
            preserveDrawingBuffer: 0,
            preferLowPowerToHighPerformance: 0,
            failIfMajorPerformanceCaveat: 0,
            enableExtensionsByDefault: 1,
            explicitSwapControl: 0,
            renderViaOffscreenBackBuffer: 0,
            majorVersion: 2,
        }
    }
    ,
    'androidx.compose.ui.text.intl.getUserPreferredLanguagesAsArray' : () => window.navigator.languages,
    'androidx.compose.ui.text.intl.parseLanguageTagToIntlLocale' : (languageTag) => new Intl.Locale(languageTag),
    'androidx.compose.ui.text.intl._language_$external_prop_getter' : (_this) => _this.language,
    'androidx.compose.ui.text.intl._baseName_$external_prop_getter' : (_this) => _this.baseName,
    'androidx.compose.ui.internal.weakMap_js_code' : () => (new WeakMap()),
    'androidx.compose.ui.internal.set_$external_fun' : (_this, p0, p1) => _this.set(p0, p1),
    'androidx.compose.ui.internal.get_$external_fun' : (_this, p0) => _this.get(p0),
    'androidx.compose.ui.platform.isSecureContext' : () => window.isSecureContext,
    'androidx.compose.ui.platform.getW3CClipboard' : () => window.navigator.clipboard,
    'androidx.compose.ui.platform.W3CTemporaryClipboard_$external_class_instanceof' : (x) => x instanceof Clipboard,
    'androidx.compose.ui.platform.W3CTemporaryClipboard_$external_class_get' : () => Clipboard,
    'androidx.compose.ui.window.isMatchMediaSupported' : () => window.matchMedia != undefined,
    'androidx.compose.ui.events.withSignal' : (signal) => ({signal: signal}),
    'androidx.compose.ui.events.AbortController_$external_fun' : () => new AbortController(),
    'androidx.compose.ui.events.signal_$external_prop_getter' : (_this) => _this.signal,
    'androidx.compose.ui.window.force_$external_prop_getter' : (_this) => _this.force,
    'kotlinx.datetime.internal.JSJoda.DateTimeFormatter_$external_class_instanceof' : (x) => x instanceof _ref_QGpzLWpvZGEvY29yZQ_.DateTimeFormatter,
    'kotlinx.datetime.internal.JSJoda.DateTimeFormatter_$external_class_get' : () => _ref_QGpzLWpvZGEvY29yZQ_.DateTimeFormatter,
    'kotlinx.datetime.internal.JSJoda.DateTimeFormatterBuilder_$external_fun' : () => new _ref_QGpzLWpvZGEvY29yZQ_.DateTimeFormatterBuilder(),
    'kotlinx.datetime.internal.JSJoda.appendOffset_$external_fun' : (_this, p0, p1) => _this.appendOffset(p0, p1),
    'kotlinx.datetime.internal.JSJoda.appendOffsetId_$external_fun' : (_this, ) => _this.appendOffsetId(),
    'kotlinx.datetime.internal.JSJoda.parseCaseInsensitive_$external_fun' : (_this, ) => _this.parseCaseInsensitive(),
    'kotlinx.datetime.internal.JSJoda.toFormatter_$external_fun' : (_this, p0) => _this.toFormatter(p0),
    'kotlinx.datetime.internal.JSJoda.STRICT_$external_prop_getter' : (_this) => _this.STRICT,
    'kotlinx.datetime.internal.JSJoda.Companion_$external_object_getInstance' : () => _ref_QGpzLWpvZGEvY29yZQ_.ResolverStyle
}

const StringConstantsProxy = new Proxy({}, {
  get(_, prop) { return prop; }
});

export { wasmTag as __TAG };

export const importObject = {
    js_code,
    intrinsics: {
        tag: wasmTag
    },
    "'": StringConstantsProxy,
    'wasm:js-string': d2FzbTpqcy1zdHJpbmc,
    './skiko.mjs': Li9za2lrby5tanM,
};
    