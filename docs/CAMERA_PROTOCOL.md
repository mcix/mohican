# mohican camera protocol

How dp-3 drives the Canon camera through mohican, and how to read the live view.

Two protocols exist side by side:

- **v1 `CANON_*`** — what protofoto uses today. 25 message types, no request/response
  correlation. Unchanged and still supported.
- **v2 `CAMERA_*`** — one command envelope, correlated by request id, structured
  errors. Use this for anything new.

Both are dispatched onto workers — see [Threading](#7-threading) — so `POST /mohicanreq`
always returns immediately regardless of protocol.

Both drive the same camera through the same driver. Picking a protocol does not pick a driver.

---

## 1. Architecture

```
dp-3 frontend (browser)                    operator's PC
  ProtoFotoView.js         ──HTTPS POST──►  mohican
  CanonLiveView.jsx        ◄──STOMP/WS───     :8008 https  /  :8007 http
        │                                         │
        │  MJPEG (plain HTTP GET)                 ▼
        └────────────────────────────►   active camera driver
                                          ├── sdk       canon-sdk-java (JNA)
                                          ├── jni       InspectorCamera.dll
                                          └── simulate  no hardware
```

mohican runs on the operator's machine — the browser talks to `localhost`, not to
deltaproto.com. `MOHICAN_HOSTNAME` in `frontend/src/components/frontend/oldui/bom/BomMohican.js`.

### Only one driver holds the camera

EDSDK permits a single session per body, so the drivers are alternatives, not peers.
`CameraDriverRegistry` owns them, builds them lazily on first activation, and stops
the outgoing one before starting the incoming one.

| id | driver | live view | notes |
|---|---|---|---|
| `sdk` | canon-sdk-java (JNA) | yes | Pure Java, works on macOS and Windows |
| `jni` | InspectorCamera.dll | **no** | **Default.** Windows only. What production runs today |
| `simulate` | none | yes (synthetic) | no hardware needed |

Switch it three ways:

- `camera.driver=sdk\|jni\|simulate` in `configuration.properties`. **Defaults to `jni`**
  so a deploy never silently moves production onto a different driver; set `sdk`
  explicitly. `camera.simulate=true` still forces `simulate`.
- the **Camera driver** dropdown in the mohican window
- the `selectDriver` op of the v2 protocol

Switching is live — no restart.

---

## 2. Transport

Identical for both protocols.

**Send a command** — `POST` to `/mohicanreq`, body `{"type": ..., "value": ...}`:

```js
await fetch('https://localhost:8008/mohicanreq', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ type: 'CAMERA_COMMAND', value: { id: 'c-1', op: 'takePhoto' } }),
});
```

The HTTP response is **empty and meaningless** — it only confirms the command was
accepted. All results come back over the WebSocket.

**Receive results** — STOMP over WebSocket, endpoint `/mohican` (also `/sock`, `/`),
topic `/topic/mohican/`. Every push is `{type, value}` and dp-3 dispatches it straight
into Redux, which is why the reducer in `frontend/src/lib/reducers/bom.ts` has cases
named after message types.

---

## 3. v2 protocol — `CAMERA_*`

### Request

```json
{ "type": "CAMERA_COMMAND",
  "value": { "id": "c-17", "op": "setSetting", "args": { "setting": "iso", "value": 72 } } }
```

| field | required | meaning |
|---|---|---|
| `id` | recommended | echoed back on the result. Omit only for fire-and-forget |
| `op` | **yes** | operation name, see below |
| `args` | per-op | operation arguments |

### Result

Pushed as `CAMERA_RESULT`, always exactly one per command:

```json
{ "type": "CAMERA_RESULT",
  "value": { "id": "c-17", "op": "setSetting", "ok": true,
             "driver": "sdk", "data": { … }, "errorCode": null, "errorMessage": null } }
```

On failure `ok` is `false`, `data` is null, and `errorCode` is one of:

| code | meaning |
|---|---|
| `BAD_REQUEST` | malformed envelope, unknown op, missing/invalid argument |
| `UNSUPPORTED` | the active driver cannot do this (e.g. live view on `jni`) |
| `NO_IMAGES` | the card is empty |
| `IMAGE_UNAVAILABLE` | the file could not be read off the card |
| `EDSDK_<n>` | the camera returned EDSDK error `<n>` |
| `CAMERA_ERROR` | anything else; see `errorMessage` |

A non-zero EDSDK code is reported as a **failed** result, not a successful one carrying
an error number — the trap v1 fell into with `CANON_PHOTO_STATUS: "ERROR: 5"`.

### Events

Unsolicited, no `id`:

```json
{ "type": "CAMERA_EVENT", "value": { "event": "pictureTaken", "driver": "sdk" } }
```

`event` is one of `connected`, `disconnected`, `pictureTaken`, `driverChanged`.
`driverChanged` additionally carries `ready` (whether the newly-selected driver came up).

`/topic/mohican/` is a **broadcast** — every connected tab sees every `CAMERA_RESULT`,
including results for requests it did not send. Make request ids unique per client
(a random per-page prefix) so one tab cannot resolve another tab's promise.

### Operations

#### Introspection and drivers

| op | args | data |
|---|---|---|
| `status` | — | `{driver, drivers[], ready, liveViewSupported, liveViewMjpeg, liveViewJpeg}` where `drivers[]` is `[{id, label, active, liveView}]` |
| `listDrivers` | — | `{drivers: [{id, label, active, liveView}]}` |
| `selectDriver` | `driver` | full status plus `started` (did it come up ready) |

`selectDriver` returns `ok: true` with `started: false` when the driver was selected but
could not come up — e.g. asking for `jni` on a Mac. That is a successful switch to a
non-working driver, which is exactly what the operator asked for and needs to see.

#### Session

| op | args | data |
|---|---|---|
| `openSession` / `closeSession` / `reinitialize` | — | `{code: 0}` |
| `findCamera` | — | `{found: true\|false}` |

#### Capture

| op | args | data |
|---|---|---|
| `takePhoto` | — | `{code: 0}` |
| `listImages` | — | `{images: ["IMG_0001.JPG", …]}` |
| `getImage` | `name` | `{name, bytes, data}` — `data` is base64 |
| `getLastImage` | — | same |
| `formatCard` | — | `{code: 0}` — **erases the card** |

#### Settings

`getSettings` returns all five properties at once:

```json
{ "iso":          { "current": {"value": 72, "label": "100"},
                    "options": [{"value": 72, "label": "100"}, …] },
  "aperture":     { … }, "shutterSpeed": { … },
  "quality":      { … }, "whiteBalance": { … } }
```

`setSetting` takes `{setting, value}` where `setting` is one of `iso`, `aperture`,
`shutterSpeed`, `quality`, `whiteBalance` and `value` is the **numeric `value`** from the
options list. Returns `{setting, current: {value, label}}` — the echoed setting name plus
the value read back from the camera afterwards.

> v1 encodes both the current value and the options as `"<n>:<label>"` strings and makes
> the client do `parseInt(v.split(':')[0])`. v2 splits them for you. The number means
> different things per driver — on `sdk` it is the real EDSDK property value, on `jni` an
> ordinal into a C++ lookup table — so **always** send back a `value` you received from
> `getSettings`, never a hard-coded constant.

#### Focus bracketing

`setFocusStacking`, all args optional with the defaults shown:

| arg | default | meaning |
|---|---|---|
| `version` | `3` | **must stay 3** — lower versions shoot the bracket but the camera does not composite it |
| `focusShiftFunction` | `1` | 0 = off, 1 = on |
| `shootingNumber` | `5` | frames in the bracket, 2–999 |
| `stepWidth` | `4` | focus step, 1–10 |
| `exposureSmoothing` | `1` | 0 = off, 1 = on |
| `focusStackingFunction` | `1` | in-camera depth compositing |
| `focusStackingTrimming` | `1` | crop the composite to the common area |
| `reserved` | `0` | flash interval |

The camera stacks the burst itself and writes **one composite JPEG** to the card. That is
why capture always reads back off the card rather than streaming to the host.

#### Auto focus

`autoFocus` focuses on a point of the live view.

| arg | meaning |
|---|---|
| `x` | horizontal position, **0..1** across the live view frame |
| `y` | vertical position, **0..1** down the frame |

Fractions, not pixels: the camera's EVF coordinate system is neither the JPEG's
pixel size nor the same across bodies, so mohican reads it and scales for you.
Out-of-range values are rejected rather than clamped.

**Live view must be running** — the AF frame is a live-view concept, and without
EVF there is no coordinate system to place it in. Otherwise you get
`Live view must be running to focus on a point`.

Returns:

```json
{ "focused": true, "code": 0, "reason": null,
  "x": 0.42, "y": 0.61,
  "afResultSignalled": true, "afResultRaw": 0,
  "coordinateSystem": { "width": 1024, "height": 680 },
  "point": { "x": 430, "y": 415 } }
```

Use **`focused`** to drive the UI. It comes from the return code of
`EdsSendCommand(DoEvfAf, ON)`, which is the only signal whose meaning EDSDK
documents. `afResultRaw` is the payload of the `kEdsStateEvent_AfResult` event,
reported verbatim because the API reference lists that event as "not specified" —
its polarity is unverified, so it is exposed rather than interpreted. If it turns
out to be reliable on our bodies, `focused` can start using it.

`status` reports `autoFocusSupported`. Only the `sdk` and `simulate` drivers can
do this; `jni` cannot.

> The simulator deliberately reports failure near the frame edges (outside
> 0.1–0.9) so the UI's failed-focus state is reachable without a camera.

#### Live view

`liveViewStatus` returns `{liveViewSupported, liveViewMjpeg, liveViewJpeg}`. The frames
themselves are **not** carried on this protocol — see below.

### Example: capture

```js
const send = (op, args) => fetch(`${MOHICAN}/mohicanreq`, {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ type: 'CAMERA_COMMAND', value: { id: nextId(), op, args } }),
});

// with a Map<id, {resolve, reject}> filled in by your CAMERA_RESULT handler:
await call('takePhoto');
const { name, data } = await call('getLastImage');   // data is base64 JPEG
```

Compare with v1, which needs a five-state machine plus a watchdog because nothing
correlates a push to a request.

---

## 4. Live view

Live view is served over **plain HTTP**, not the WebSocket — a JSON channel cannot carry
15 fps of JPEG without wrecking it.

| endpoint | content type | use |
|---|---|---|
| `GET /api/camera/liveview.mjpg` | `multipart/x-mixed-replace; boundary=frame` | everything except iOS |
| `GET /api/camera/liveview.jpg` | `image/jpeg` | one frame; poll for iOS Safari |

### Reading the stream

The browser renders MJPEG natively — no decode loop, no canvas:

```jsx
<img src="https://localhost:8008/api/camera/liveview.mjpg"
     crossOrigin="anonymous" />
```

Setting `src` opens the stream and starts live view on the camera. Clearing it
(`img.src = ""`) closes the connection, which is what releases the session. There is no
"stop" command — the HTTP connection *is* the session handle.

`crossOrigin="anonymous"` is needed only if you want to sample pixels (the reference
component does, to detect a frozen feed). CORS is already open for the dp-3 origins.

### iOS Safari

iOS paints only the first frame of an MJPEG stream and never refreshes. Detect it and
poll the single-frame endpoint instead:

```js
const isIOS = /iPad|iPhone|iPod/.test(navigator.userAgent) && !window.MSStream;
setInterval(() => { img.src = `${base}/api/camera/liveview.jpg?t=${Date.now()}`; }, 200);
```

### Behaviour worth knowing

- **Sessions are ref-counted.** Several tabs share one EDSDK live view; the camera only
  leaves EVF mode when the last viewer disconnects.
- **~15 fps**, deliberately capped so one tab does not eat a core.
- **The first ~2 s produce nothing.** EDSDK needs a moment after EVF output flips to PC.
  The `.jpg` endpoint answers `204 No Content` during that window — not an error.
- **Capture interrupts it.** `takePhoto` pauses live view for the shot, and reading the
  card requires a session refresh, after which live view is restored automatically. Expect
  a visible blip.
- **`503` means the active driver has no live view** — i.e. `jni` is selected. Check
  `liveViewSupported` from `status` before offering the control.
- **Stop the stream when the tab is hidden.** Otherwise a backgrounded tab keeps decoding
  frames and holding the session. The reference component does this on `visibilitychange`.

Reference implementation: `frontend/src/components/frontend/oldui/bom/CanonLiveView.jsx` in dp-3.

---

## 5. v1 protocol — `CANON_*` (legacy)

Still supported, unchanged. Documented so existing code stays readable.

**Commands** (client → mohican)

| type | value | effect |
|---|---|---|
| `INIT_CAMERA`, `CANON_OPEN_SESSION`, `CANON_CLOSE_SESSION`, `CANON_REINITIALIZE` | — | session lifecycle |
| `CANON_TAKE_PHOTO` | — | replies `CANON_PHOTO_STATUS` |
| `CANON_GET_IMAGE_INFO` | — | replies `CANON_GET_IMAGE_INFO` |
| `CANON_GET_IMAGE` | filename | replies `CANON_GET_IMAGE` or `CANON_GET_IMAGE_ERROR` |
| `CANON_GET_LAST_IMAGE` | — | same |
| `CANON_FORMAT` | — | erases the card |
| `CANON_GET_ALL_SETTINGS` | — | pushes all four `*_OPTIONS` |
| `CANON_GET_ALL_CURRENT_SETTINGS` | — | pushes all four `*_CURRENT` |
| `CANON_GET_ALL_{ISO,APERTURE,SHUTTERSPEED,QUALITY}` | — | pushes one `*_OPTIONS` |
| `CANON_GET_CURRENT_{…}` | — | pushes one `*_CURRENT` |
| `CANON_SET_{ISO,APERTURE,SHUTTERSPEED,QUALITY}` | int | no reply |
| `CANON_SET_FOCUS_STACKING` | object | no reply |

**Pushes** (mohican → client)

`CANON_PHOTO_STATUS` (`"OK"` / `"ERROR: <n>"`), `CANON_GET_IMAGE_INFO` (`String[]`),
`CANON_GET_IMAGE` (`{name, data}`), `CANON_GET_IMAGE_ERROR` (string),
`CANON_{ISO,APERTURE,SHUTTERSPEED,QUALITY}_OPTIONS` (`String[]` of `"<n>:<label>"`),
`CANON_{…}_CURRENT` (one `"<n>:<label>"`).

### Known sharp edges

- **No correlation.** A `CANON_GET_IMAGE` push cannot be tied to the request that caused
  it. Clients must run a state machine and time each step out.
- **`CANON_SET_*` never replies.** Success is indistinguishable from a dropped message.
- **A current-value string starting with `camera`** means the driver could not read the
  property. `CANON_GET_ALL_CURRENT_SETTINGS` silently skips those, so the client sees
  nothing at all rather than an error.
- **`CANON_SET_*` and friends are fire-and-forget.** Nothing tells you the camera accepted
  the value; you have to read it back with `CANON_GET_CURRENT_*`.

---

## 6. Threading

Every command is handed to a worker and the delivering thread returns immediately.
There is **one single-threaded worker per category**:

| worker | handles |
|---|---|
| `mohican-motion` | `GOTO`, `GOTO_PCB`, `BRAKE`, `HOME`, `ZERO`, `SET_POSITION`, `SET_SPEED`, `SET_ACCELERATION`, `SET_CURRENT`, `SET_MIN_MAX_POSITION`, `SET_PCB_POSITION`, `SET_PLANKJE_POSITION`, `POSITION_TIMEOUT` |
| `mohican-camera` | everything `CANON_*`, `CAMERA_*` and `INIT_CAMERA` |
| `mohican-device` | `MESSAGE`, `GET_VERSION`, anything unrecognised |

Two properties this buys, both of which matter:

- **Order is preserved within a category.** Each is a single thread, never a pool — a
  `GOTO` followed by a `BRAKE` executes in that order. Sending 30 motion commands
  concurrently still runs them one at a time, in arrival order.
- **Categories cannot block each other.** A capture takes seconds; motor commands run
  on their own thread and are unaffected.

`BRAKE` is safe to queue because motion handlers do not block on movement —
`MotorController.goTo()` appends a string to the serial writer's queue and returns.
**If a motion handler is ever made blocking, this has to be revisited**, since `BRAKE`
would then sit behind the move it is meant to stop.

v1 and v2 deliberately share the camera worker: they drive the same driver, and on
separate threads a `CANON_TAKE_PHOTO` could interleave with a v2 `getLastImage`
mid-operation.

Queues are unbounded — a command is never silently dropped. Depth past 50 is logged as
a warning, which in practice means a handler is wedged.

---

## 7. Integrating into dp-3

1. **Live view first — it is independent.** `CanonLiveView.jsx` already exists and is
   mounted in `ProjectStepTakeFoto.js` and `ProtoFotoView.js`. It needs no protocol work;
   it just needs mohican running with a driver that supports it.
2. **Gate the control on capability**, not on connection: `status` → `liveViewSupported`.
   With `jni` selected the endpoints answer `503`.
3. **New UI on v2.** Build a small client that keeps a `Map<id, promise>` and resolves it
   from the `CAMERA_RESULT` handler, then `await call('takePhoto')`. The existing
   `withImageTaking.js` state machine exists only to work around v1's lack of correlation.
4. **Leave v1 alone** while migrating. Both protocols hit the same driver, so a v2 screen
   and the current protofoto page can coexist against one mohican.
5. **Surface the driver.** `CAMERA_EVENT`/`driverChanged` fires whenever it changes,
   including from the mohican window, so dp-3 can show which driver is live without polling.

### Settings values are driver-specific

The integer in a settings option means different things on `sdk` and `jni`. Re-read
`getSettings` after `selectDriver` and never persist those integers as if they were
portable — store the label if you need to remember a choice across drivers.
