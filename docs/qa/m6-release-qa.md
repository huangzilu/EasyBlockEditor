# M6 Release QA Matrix

Date: 2026-05-25

## Goal

Validate that the MegaProjection RenderCore v2 path does not regress small or medium projections and keeps huge projections usable enough for beta release.

## Automated Gates

| Gate | Command | Status |
| --- | --- | --- |
| Unit and regression tests | `.\gradlew.bat test` | Passed |
| Full production build | `.\gradlew.bat build --console=plain` | Passed |
| Whitespace patch check | `git diff --check` | Passed |

## Local Projection Fixtures

| Class | File | Size | Purpose |
| --- | --- | ---: | --- |
| Small | `run/config/ebe/client/schematics/station1.litematic` | 33 KB | Legacy path sanity check |
| Medium | `run/config/ebe/client/schematics/八十稻羽.litematic` | 236 KB | Below default sync threshold |
| Huge | `run/config/ebe/client/schematics/Anor_Londo.litematic` | 3.9 MB | MegaProjection / LOD / memory stress |

Note: a native 512 KB fixture is not currently present in the workspace. Use the medium and huge files for boundary behavior, or add a 512 KB `.litematic/.schem/.schematic` fixture when available.

## Manual Client QA

| Area | Small 33 KB | Medium 236 KB | Huge 3.9 MB | Expected Result |
| --- | --- | --- | --- | --- |
| File open | Pending | Passed | Pending | Small/medium open through sync path; huge shows warning and progressive/LOD path |
| First visible result | Pending | Pending | Pending | Small/medium show detailed blocks quickly; huge shows useful LOD outline before detail finishes |
| Camera movement while loading | Pending | Pending | Pending | Huge remains steerable, no multi-second render thread freeze |
| Loading completion | Pending | Pending | Pending | Bottom status completes; section renderer continues refining without disappearing blocks |
| Layer visibility toggle | Pending | Pending | Pending | No full viewport reload; only changed sections update |
| Display filter / heatmap toggle | Pending | Pending | Pending | No full reload; huge heatmap uses fast section overlay and remains visible |
| Projection placement | Pending | Pending | Pending | In-world projection uses LOD for huge path and does not crash |
| Place-all admin action | Pending | Pending | Pending | Large requests are chunked/queued, no decoder crash from oversized payload |
| Auto printer finite range | Pending | Pending | Pending | Candidate planner advances by section and does not crawl one block every few seconds |
| Material chest printer | Pending | Pending | Pending | Server validates material source and NBT policy |
| Workgroup printer | Pending | Pending | Pending | Reservations are section/phase ordered and avoid duplicate assignments |

## Log Checks

| Pattern | Expected |
| --- | --- |
| `NoClassDefFoundError: kotlinx/coroutines/ExecutorsKt` | Should no longer print full stack in normal logs; fallback message is acceptable if coroutine runtime is unavailable |
| `Invalid place-all entry count` | Must not appear |
| `OutOfMemoryError` | Must not appear |
| `Computed progressive load started/finished` | Expected for huge fixtures |
| `Viewport LOD ready` | Expected after M3/M4 huge viewport path starts |

Current clean-client log note: `八十稻羽.litematic` opened as a 248,935-block model in about one second after NBT read. Because block count is high, the risk profile is `HUGE`, but the below-threshold sync path still avoided progressive chunk loading for this fixture.

## Release Decision

M6 can move to release candidate when:

| Criterion | Required |
| --- | --- |
| Build/test | All automated gates pass |
| Small/medium regression | No slower-than-before sync path regression visible to user |
| Huge usability | Huge projection can open and camera can move during loading without hard freeze |
| Printing | Place-all, auto, material chest, and workgroup printing do not crash server/network path |
| Logs | No new EBE crash stack traces after a clean client run |
