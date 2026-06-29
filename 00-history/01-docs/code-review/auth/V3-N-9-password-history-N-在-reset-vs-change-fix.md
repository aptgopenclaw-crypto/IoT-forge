**N-9 password history N for reset vs change — fixed.** Here's the summary:

### Problem
`PasswordValidator.checkNotRecentlyUsed()` used the same `historyCount` for all flows: admin-initiated resets (`resetPassword`, `forceChangePassword`) and user self-initiated changes (`changePassword`). Checking against the full history N on admin resets was unnecessarily restrictive since the admin action is already a controlled operation.

### Solution: `historyCountForReset` (separate, lower N for reset flows)

**New model field** in `PasswordPolicy`:
- `historyCountForReset` — default `1`, min `0` (can disable)
- `describe()` updated to mention both counts when they differ

**New policy key** in `PasswordPolicyKey`:
- `HISTORY_COUNT_FOR_RESET("password.history_count_for_reset", INT, "1", 0)`

**New resolver entry** in `PasswordPolicyResolver.doResolve()`:
- Resolves the new key via the same tenant→platform→default fallback

**New API** in `PasswordValidator`:
- `checkNotRecentlyUsedForReset(tenantId, userId, rawPassword)` — uses `policy.getHistoryCountForReset()`
- Existing `checkNotRecentlyUsed()` unchanged (still uses full `historyCount`)

**Callers updated:**
| Flow                                    | Before                            | After                                     |
| --------------------------------------- | --------------------------------- | ----------------------------------------- |
| `AuthServiceImpl.resetPassword()`       | `checkNotRecentlyUsed(null, ...)` | `checkNotRecentlyUsedForReset(null, ...)` |
| `AuthServiceImpl.forceChangePassword()` | `checkNotRecentlyUsed(id, ...)`   | `checkNotRecentlyUsedForReset(id, ...)`   |
| `UserSelfService.changePassword()`      | `checkNotRecentlyUsed(id, ...)`   | unchanged (full history)                  |

**DTO & frontend**: `PasswordPolicyDto` and frontend `PasswordPolicyDto` type both gained `historyCountForReset`.

### Files changed (7 backend + 1 frontend)
- `PasswordPolicyKey.java` — new enum entry
- `PasswordPolicy.java` — new field + describe update
- `PasswordPolicyResolver.java` — new resolution
- `PasswordValidator.java` — new method
- `PasswordPolicyDto.java` — new field
- `PasswordPolicyService.java` — toDto mapping
- `AuthServiceImpl.java` — caller updates
- `frontend/src/types/passwordPolicy.ts` — type update