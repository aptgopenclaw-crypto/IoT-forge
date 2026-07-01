Created 7 todos

Let me first inspect the failing tests and their corresponding service files.

Read [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/test/java/com/taipei/iot/auth/service/AuthServiceTest.java#1-1), lines 1 to 80

Read [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/test/java/com/taipei/iot/auth/service/AuthServiceTest.java#80-80), lines 80 to 170

Read [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot/auth/service/impl/AuthServiceImpl.java#1-1), lines 1 to 80

Searched for regex `private final|private final|eventPublisher|ApplicationEvent` (`**/backend/src/main/java/com/taipei/iot/auth/service/impl/AuthServiceImpl.java`), 20 results

Searched for regex `private final` (`**/backend/src/main/java/com/taipei/iot/auth/service/impl/AuthServiceImpl.java`), 19 results

Now let me read the remaining failing test files to understand all the issues.

Read [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/test/java/com/taipei/iot/user/service/UserAdminServiceTest.java#1-1), lines 1 to 60

Read [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/test/java/com/taipei/iot/user/service/UserAdminServiceTest.java#60-60), lines 60 to 100

Searched for text `private final` (`**/backend/src/main/java/com/taipei/iot/user/service/UserAdminService.java`), 12 results

Searched for text `private final` (`**/backend/src/main/java/com/taipei/iot/tenant/service/TenantAdminService.java`), 5 results

Read [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/test/java/com/taipei/iot/tenant/service/TenantAdminServiceSeedSettingsTest.java#1-1), lines 1 to 60

Searched for text `TenantAuthConfigProvisioner` (`**/backend/src/main/java/com/taipei/iot/tenant/service/TenantAdminService.java`), 4 results

Read [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/test/java/com/taipei/iot/audit/job/AuditPurgeJobTest.java#1-1), lines 1 to 60

Read [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot/audit/job/AuditPurgeJob.java#1-1), lines 1 to 30

Read [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot/audit/job/AuditPurgeJob.java#30-30), lines 30 to 80

Read [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/test/java/com/taipei/iot/audit/async/AuditAsyncWriterTest.java#1-1), lines 1 to 60

Read [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot/audit/async/AuditAsyncWriter.java#1-1), lines 1 to 50

Read [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot/common/auth/port/PasswordPolicyProvider.java#1-1), lines 1 to 20

Read [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot/common/audit/port/UserDisplayInfoProvider.java)

Read [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot/common/auth/port/TenantAuthConfigProvisioner.java#1-1), lines 1 to 20

Read [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot/common/tenant/TenantIdProvider.java#1-1), lines 1 to 15

Searched for text `eventPublisher|ApplicationEvent` (`**/backend/src/main/java/com/taipei/iot/user/service/UserAdminService.java`), no results

Read [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/test/java/com/taipei/iot/tenant/service/TenantAdminServiceSeedSettingsTest.java#60-60), lines 60 to 120

Read [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/test/java/com/taipei/iot/audit/async/AuditAsyncWriterTest.java#60-60), lines 60 to 88

Read [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot/common/audit/port/UserDisplayInfo.java)

Searched for text `class TurnstileService` (`**/backend/src/main/java/com/taipei/iot/auth/service`), no results

Searched for text `TurnstileService` (`**/backend/src/main/java/com/taipei/iot`), no results

Searched for text `TurnstileService`, 20 results

Read [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/test/java/com/taipei/iot/audit/job/AuditPurgeJobTest.java#60-60), lines 60 to 122

Now I have a complete picture of all 5 failing tests. Let me fix them all.

Starting: *Fix AuthServiceTest (eventPublisher null)* (2/7)

Let me first read the exact areas to edit in each file.

Read [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/test/java/com/taipei/iot/auth/service/AuthServiceTest.java#80-80), lines 80 to 100

Read [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/test/java/com/taipei/iot/auth/service/AuthServiceTest.java#100-100), lines 100 to 139

Now I have all the info. Let me fix all 5 test files simultaneously.




```

```

```

```

```

```

```

```

```

```
Now I need to also convert the `TenantEntity` usage in the first test method of `AuditPurgeJobTest`, since `TenantEntity` import was removed.

Read [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/test/java/com/taipei/iot/audit/job/AuditPurgeJobTest.java#53-53), lines 53 to 68

Read [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/test/java/com/taipei/iot/audit/job/AuditPurgeJobTest.java#38-38), lines 38 to 55




```

```
Now let me run these 5 test classes to verify they pass.

Ran terminal command:  mvn test -Dtest='AuthServiceTest,UserAdminServiceTest,TenantAdminServiceSeedSettingsTest,AuditPurgeJobTest,AuditAsyncWriterTest' 2>&1 | grep -iE "Tests run:|BUILD (SUCCESS|FAILURE)" | tail -10

Ran terminal command:  grep -A5 "ERROR\|FAILURE" target/surefire-reports/TEST-com.taipei.iot.tenant.service.TenantAdminServiceSeedSettingsTest.xml 2>/dev/null | head -20

Ran terminal command:  cat target/surefire-reports/TEST-com.taipei.iot.tenant.service.TenantAdminServiceSeedSettingsTest.xml 2>/dev/null | head -80

Ran terminal command:  grep -E "testcase|error|failure|type=" target/surefire-reports/TEST-com.taipei.iot.tenant.service.TenantAdminServiceSeedSettingsTest.xml | head -20

Read [](file:///home/kevin/.config/Code/User/workspaceStorage/7634e41f9babc7c26259ebacf2d5d251/GitHub.copilot-chat/chat-session-resources/dbd9a946-3a2e-4e11-b6f5-20a8bd5204e9/call_00_NI0S5ZICdTWHIcHTG30g4924__vscode-1782778559665/content.txt#1-1), lines 1 to 30

Ran terminal command:  grep "<error message=" target/surefire-reports/TEST-com.taipei.iot.tenant.service.TenantAdminServiceSeedSettingsTest.xml | head -10

Another missing port mock. Let me check what `TenantAdminService` needs.

Read [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot/tenant/service/TenantAdminService.java#20-20), lines 20 to 45

Read [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/test/java/com/taipei/iot/tenant/service/TenantAdminServiceSeedSettingsTest.java#1-1), lines 1 to 40




```

```





```

```


Ran terminal command:  mvn test -Dtest='TenantAdminServiceSeedSettingsTest' 2>&1 | grep -iE "Tests run:|BUILD (SUCCESS|FAILURE)" | tail -5

Ran terminal command:  grep -A3 "<failure\|<error" target/surefire-reports/TEST-com.taipei.iot.tenant.service.TenantAdminServiceSeedSettingsTest.xml | head -10

Read [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/test/java/com/taipei/iot/tenant/service/TenantAdminServiceSeedSettingsTest.java#130-130), lines 130 to 166

Read [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot/common/user/port/TenantAdminProvisioner.java)




```

```


Ran terminal command:  mvn test -Dtest='TenantAdminServiceSeedSettingsTest' 2>&1 | grep -iE "Tests run:|BUILD (SUCCESS|FAILURE)" | tail -3

All 5 pass. Let me now run all 5 together to confirm.

Starting: *Run full test suite to verify* (7/7)

Ran terminal command:  mvn test -Dtest='AuthServiceTest,UserAdminServiceTest,TenantAdminServiceSeedSettingsTest,AuditPurgeJobTest,AuditAsyncWriterTest' 2>&1 | grep -iE "Tests run:|BUILD (SUCCESS|FAILURE)" | tail -10

All 72 green. Now the full suite.

Ran terminal command:  mvn test 2>&1 | grep -iE "Tests run:|BUILD (SUCCESS|FAILURE)" | tail -5

全量 **1293 測試全部通過** ✅

Completed: *Run full test suite to verify* (7/7)

已完成 5 個遺留測試修復，全量 **1293 測試** 0 failure 0 error。

### 各檔案修正摘要

| 測試類別 | 根因 | 修法 |
|---|---|---|
| `AuthServiceTest` | `AuthServiceImpl` 新增 `ApplicationEventPublisher`、`TurnstileService` 兩個 field，測試缺對應 `@Mock` | 補上這兩個 mock |
| `UserAdminServiceTest` | Service 改用 `common.auth.port.PasswordPolicyProvider`，測試仍 mock 舊的 `auth.policy.PasswordPolicyResolver`（不同類別） | 修正 mock 型別為 `PasswordPolicyProvider` |
| `TenantAdminServiceSeedSettingsTest` | Service 改用 `TenantAdminProvisioner`、`TenantAuthConfigProvisioner` 兩個 port，測試缺對應 mock；`verify(userRepository.save())` 已不再適用 | 補 `TenantAdminProvisioner` mock；將 verify 改為 `tenantAdminProvisioner.provisionTenantAdmin()` |
| `AuditPurgeJobTest` | Service 改用 `TenantIdProvider` port 取代直接使用 `TenantRepository` | 換 mock 為 `TenantIdProvider`，stubbing 改 `findEnabledTenantIds()` |
| `AuditAsyncWriterTest` | Service 改用 `UserDisplayInfoProvider` port 取代 `UserRepository` | 換 mock 為 `UserDisplayInfoProvider`，stubbing 改 `findByUserId()` 回傳 `UserDisplayInfo` |

這些都是先前重構引入 Port/Adapter 時，對應測試未同步更新的遺留問題。

Made changes.