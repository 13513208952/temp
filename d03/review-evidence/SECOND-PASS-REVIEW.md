# D03 second-pass review (after dsh R49 verification)

Reviewed `temp@44f0e4e`: only README, six context dependencies and evidence/logs changed compared with first-pass `6a4b2d2`. Original D03 25 production targets were not overwritten.

## Evidence examined
- dsh JUnit result array has exactly 43 classes, 180 sum tests, zero failures/ignored; build log independently agrees with its generated JSON. This is **dsh's run**, not execution by the reviewer.
- All six `d03/context/java` copies were compared character-for-character with `ReBrowser@4f42f4c` R49 and are **identical**.
- Reported probe totals are 13,641+3,635+46 PASS. **The 46 focused probes currently lack separately committed executable source/command/result log** in the new D03 verification commit; the report is not independently reproducible as-is. Request an optional append-only artifact if the main agent relies on the 46 as proof.
- R49 context compatibility does **not** equal 25 production-source semantic merge compatibility or AGP runtime validation.

## Additional true defect fixed in second pass
- `MusicArtwork.begin(long)` returned the **latest current generation** for a stale/duplicate claim, rather than `0`. The caller could accidentally use that returned current generation to continue a superseded request. R48 main already uses `0` for the stale case. Restored the rejection signal and updated `MusicArtworkTest` for older and duplicate generations.
- `OwnedBitmap.release` now synchronized (avoids racing disposal of the same bitmap by two callers); exposed `closed/released` flags are volatile. Actual bitmap decode additionally rejects decoded dimensions exceeding `MAX_DIMENSION`, beyond pixel budget.
- These changes are **post dsh's 180-JUnit run**. Require a new actual compilation + complete suite from dsh/main before counting them PASS.

## Additional review caveats
- `MusicMediaSession` calls its speed `reportedSpeed`, but the current projection constructs 1.0f for PLAYING and 0.0f otherwise from reported *state*, rather than obtaining an actual speed field from `MusicState`. Do not describe it as independently reported playback speed without native speed evidence.
- `SitePermissionsActivity.Groups` groups by host/partition *labels*, not canonical origin+context handles; this group is presentation-only and may never authorize/revoke.
- The `MusicArtwork` D03 `decodeOwned` API differs from main R49's `decode`; add the ownership wrapper surgically, not by replacing all R48 work wholesale.
- Android/Gecko real callbacks, View/MotionEvent, native close behavior, manifest registration and main-project AGP integration remain unexecuted.

This report and the source/test modifications are in temp only; production `ReBrowser` is unchanged.
