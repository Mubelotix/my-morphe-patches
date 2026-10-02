# Spotify Ad System - Reverse Engineering Notes

## App Info

- Package: `com.spotify.music`
- Version: `9.1.62.1601`
- Version code: `143403975`

## Architecture Overview

Spotify does NOT use standard HTTP for ad delivery. Ads flow through **Esperanto** - an internal RPC framework with `sp://` Cosmos protocol transport.

```
┌─────────────────────────────────────────────────────────┐
│ Ad Delivery Pipeline                                    │
│                                                         │
│  InStream (streaming)    Ads (request/response)         │
│  ┌──────────────────┐    ┌──────────────────┐           │
│  │ SubBreakChanged  │    │ GetAds           │           │
│  │ (push notified)  │───▶│ (fetch ad data)  │           │
│  └────────┬─────────┘    └────────┬─────────┘           │
│           │                       │                     │
│           ▼                       ▼                     │
│  ┌────────────────────────────────────┐                 │
│  │ CosmosTransport / CoroutineTransport│                │
│  │ sp://esperanto/Ads/GetAds          │                 │
│  │ sp://esperanto/InStream/SubBreak.. │                 │
│  └────────────────────────────────────┘                 │
│           │                       │                     │
│           ▼                       ▼                     │
│  ┌────────────────────────────────────┐                 │
│  │ App UI / Playback                  │                 │
│  │ SubBreakChanged → inBreak=true     │                 │
│  │ GetAdsResponse → AdSlotEvent(AVAILABLE/PLAY/DISCARD) │
│  └────────────────────────────────────┘                 │
└─────────────────────────────────────────────────────────┘
```

## Key Classes

### Esperanto Clients

| Smali | Java | Purpose |
|-------|------|---------|
| `Lp/bn1;` | GetAds Client | Calls `spotify.ads.esperanto.proto.Ads/GetAds` |
| `Lp/hd;` | InStream Subscriber | Subscribes to `InStream/SubBreakChanged` (7067 lines) |

### Transport

| Smali | Class | Purpose |
|-------|-------|---------|
| `Lcom/spotify/esperanto/esperantocosmos/CosmosCoroutineTransport;` | Coroutine transport | Internal `sp://` protocol transport |
| `Lcom/spotify/esperanto/esperantocosmos/CosmosTransport;` | Cosmos transport | Alternative protocol transport |

### Proto Messages

All in `com.spotify.ads.esperanto.proto`:

| Class | Fields | Getters |
|-------|--------|---------|
| `GetAdsRequest` | `slotId_` (String), `targeting_` (MapField) | No instance getters (protobuf-lite) |
| `GetAdsResponse` | `adPackage_` (MapField), `error_` (String), `requestId_` (String) | `n()` → adPackage Map, `o()` → hasError boolean |
| `SubBreakChangedResponse` | `inBreak_` (boolean) | `n()` → boolean |
| `AdSlotEvent` | `eventType_` (int→un0 enum), `slotId_` (String), `format_` (int→ih0 enum), `ad_` (Ad) | `p()` → eventType, `r()` → slotId, `q()` → format, `n()` → Ad |
| `Ad` | 15 fields: adId, metadata, isDummy, coverArt, audio, video, display, clickthroughUrl, trackingEvents, slot, requestId, format, isDsaEligible, companions, verifications | `n()` → adId, `o()` → clickthroughUrl, `s()` → display list, `t()` → format, `v()` → isDummy, `w()` → requestId, `x()` → slot, etc. |

### Ad Consumers

| Smali | Purpose |
|-------|---------|
| `Lp/v9z;` | Flow collector for SubBreakChanged stream. `emit()` method at offset 0 deserializes bytes via `SubBreakChangedResponse.o()`. Wraps `inBreak` boolean and emits downstream. |
| `Lp/k561;` | Converts `AdSlotEvent` to `wn0`. Static method `n(Lcom/spotify/ads/esperanto/proto/AdSlotEvent;)Lp/wn0;` reads eventType, format, slotId, ad. |

### Event Type Enum (un0)

| Ordinal | Name | Field |
|---------|------|-------|
| 0 | AVAILABLE | `.b` |
| 1 | PLAY | `.c` |
| 2 | DISCARD | `.d` |

## Ad Data Flow (Detailed)

1. App subscribes to `InStream/SubBreakChanged` (streaming RPC)
2. When playback reaches ad break: `SubBreakChanged { inBreak: true }` pushed
3. App calls `Ads/GetAds` with slotId + targeting data
4. Response contains `map<slotId, Ad>` with:
   - `adId`, `slot`, `format` (audio/video/banner)
   - `audio[]` / `video[]` / `display[]` media lists
   - `clickthroughUrl`, `trackingEvents`, `companions[]`
5. Ads dispatched via `AdSlotEvent`:
   - `AVAILABLE` → ad fetched, ready to play
   - `PLAY` → ad started playing
   - `DISCARD` → ad removed without playing

## Embedded Ads (Home Page)

Brand ads on home page are NOT separate API calls. They are protobuf sub-fields inside home page structure responses:
- `EmbeddedAdProto$EmbeddedAd` in `com.spotify.ads.brandads.v1`
- `ImageBrandAd`, `VideoBrandAd` in `com.spotify.home.evopage.homeapi.proto`

These are not covered by the current logging patch (they use webgate Retrofit APIs on OkHttp).

## Logging Patch Strategy

### What Gets Logged

| Tag | When | Content |
|-----|------|---------|
| `SpotifyAds` `[GetAds] REQUEST >>>` | Every `GetAds` RPC call | GetAdsRequest.toString() |
| `SpotifyAds` `[InStream] SubBreakChanged >>>` | Every SubBreakChanged push | Marker only (raw event) |
| `SpotifyAds` `[AdSlot] EVENT >>>` | Every ad slot event | AdSlotEvent.toString() (includes full Ad object) |

### How to Read Logs

```bash
adb logcat -s SpotifyAds
```

Look for the `>>>` markers to see ad request parameters and slot events with full ad data.

### What's NOT Logged (Future Work)

- `GetAdsResponse` contents (response parsed inside bn1.a, no register-safe injection point at return without expanding locals)
- SubBreakChanged `inBreak` value (emitted through coroutine flow, not directly accessible from emit method entry)
- Embedded/brand ads from home page (separate OkHttp-based pipeline)

## Dead Ends

- **OkHttp interceptor approach**: Won't work. Ads use Esperanto internal RPC (`sp://` protocol), not HTTP. OkHttp is only used for webgate REST APIs (playlists, search, etc).
- **Cannot easily extract fields from GetAdsRequest**: Spotify uses protobuf-lite with reflective `dynamicMethod`. No instance getters on GetAdsRequest - fields only accessible through builder.

## Gotchas

- Obfuscated identifiers change every update. Fingerprints rely on stable properties: unique error strings, method signatures with proto types, opcode patterns.
- `v9z.emit()` is a 6491-line coroutine state machine. Custom fingerprint matches on SubBreakChangedResponse reference in instructions.
- AdSlotEvent `ad_` field may be null → `n()` falls back to `Ad.r()` (default instance).

## Future Recommendations

1. **Add response logging for GetAds**: Inject after `move-result-object p1` to log GetAdsResponse.toString(). Requires register analysis at return point.
2. **Hook SubBreakChanged inBreak directly**: In `v9z`, log the boolean value after SubBreakChangedResponse.n() call (instruction offset ~3534).
3. **Home page ad blocking**: Would require modifying webgate responses to strip `EmbeddedAd` fields from protobuf.
4. **Add block patch**: After confirming behavior through logs, create `AdsBlockPatch` using `returnEarly()` patterns on SubBreakChangedResponse (always false), AdSlotEvent with eventType DISCARD, or hooking GetAds response to return empty ad package.
