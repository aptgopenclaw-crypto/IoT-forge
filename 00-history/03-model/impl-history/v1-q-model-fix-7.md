Updated todo list

修最後一個環：`device↔dispatch`。先看 `DeviceService.getStats()` 怎麼用到 dispatch。 

Read [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot/device/service/DeviceService.java#1-1), lines 1 to 90

只有 `getStats()` 一處用到 dispatch（計算未結工單數作為 `openFaults`）。`dispatch→device` 是 L4→L3 合法向下，要斷的是 `device→dispatch`。先確認簽章與其他引用。  

Ran terminal command:  cd /home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot && echo "### device → dispatch 全部引用"; grep -rn "import com\.taipei\.iot\.dispatch\." device/; echo; echo "### countOpenWorkOrders 簽章"; grep -rn "countOpenWorkOrders" dispatch/repository/WorkOrderRepository.java; echo; echo "### 測試是否 mock WorkOrderRepository 於 DeviceService"; grep -rln "DeviceService" ../../../../../test/java/com/taipei/iot/ 2>/dev/null

確認：只有 `DeviceService` 引用 dispatch，且「未結」語意（排除 COMPLETED/CLOSED）本就屬於 dispatch。讓 port 不帶狀態參數，把這個知識留在 dispatch 內。  

Created [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot/common/dispatch/port/OpenWorkOrderCounter.java)

Created [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot/dispatch/service/OpenWorkOrderCounterAdapter.java)

Replacing 15 lines with 13 lines in [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot/device/service/DeviceService.java)


```

```


Created 2 todos

Ran terminal command:  cd /home/kevin/workspaces/side-project/IoT-forge/backend && mvn spring-javaformat:apply -q && mvn -q clean test-compile 2>&1 | tail -5 && echo "=== COMPILE OK ===" && cd src/main/java/com/taipei/iot && echo "device→dispatch: $(grep -rl 'import com\.taipei\.iot\.dispatch\.' device/ 2>/dev/null | wc -l)  (was 2 imports/1 file)"; echo "dispatch→device: $(grep -rl 'import com\.taipei\.iot\.device\.' dispatch/ 2>/dev/null | wc -l)  (L4→L3 合法保留)"

`device → dispatch` 歸零。現在所有循環應已清零——重新凍結 ArchUnit store 並收緊 `no_cyclic_dependencies`。先看目前測試內容。  

Read [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/test/java/com/taipei/iot/architecture/LayeredArchitectureTest.java)

Run ArchUnit — FreezingArchRule auto-prunes resolved violations on a passing run. Then I'll check what cycles remain in the store.   

Ran terminal command:  cd /home/kevin/workspaces/side-project/IoT-forge/backend && mvn test -Dtest='LayeredArchitectureTest' 2>&1 | grep -iE "Tests run:|BUILD" | tail -3; echo "=== 剩餘凍結的環 (cycle store) ==="; grep -oE "Cycle detected: Slice [a-z]+ -> .*Slice [a-z]+" archunit_store/2ee19343-1d9c-4df6-b434-240f387065a4 2>/dev/null | sed 's/\\\//g' | head; echo "=== 用 grep 抓 Slice 名稱對 ==="; grep -oE "Slice [a-z]+ ->" archunit_store/2ee19343-1d9c-4df6-b434-240f387065a4 | sort -u

Store auto-pruned — 唯一剩下的環是 `dept ↔ user`（L2↔L2，不在問題二/四範圍內）。檢查 rule 1 的 layer 違規 store。  

Ran terminal command:  echo "=== layer 違規 store 剩餘項 (依來源類別) ==="; grep -oE "com\.taipei\.iot\.(audit|config)\.[a-zA-Z.]+" archunit_store/a6514eb5-248d-46b0-aec1-5b54a834e3ed | grep -oE "iot\.(audit|config)" | sort | uniq -c; echo; echo "=== 完整摘要 ==="; grep -oE "^(Constructor|Field|Method) <com\.taipei\.iot\.[a-z]+\." archunit_store/a6514eb5-248d-46b0-aec1-5b54a834e3ed | grep -oE "iot\.[a-z]+" | sort | uniq -c

All 問題二 + 問題四 **cycles cleared**. Remaining frozen items are now accurate to document:
- **Cycles**: only `dept ↔ user` (L2↔L2, separate issue)
- **Layer violations**: `audit→dept`, `config→auth` (upward L1→L2, not cycles)

The test comments still reference the now-resolved `auth↔tenant`. Let me update them to reflect reality.   

Replacing 20 lines with 22 lines in [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/test/java/com/taipei/iot/architecture/LayeredArchitectureTest.java)

Compacted conversation
```

```
Now update the assessment report for 2E. Let me read the relevant sections. 

Searched for regex `device.*dispatch|2E|2D|問題四` (`**/00-history/03-model/module-boundary-assessment.md`), 16 results

Read [](file:///home/kevin/workspaces/side-project/IoT-forge/00-history/03-model/module-boundary-assessment.md#144-144), lines 144 to 235

Now update the report for 2E. Let me apply all edits.    

Replacing 14 lines with 26 lines in [](file:///home/kevin/workspaces/side-project/IoT-forge/00-history/03-model/module-boundary-assessment.md)


```

```
Now format the Java test file and run final verification.    

Ran terminal command:  mvn spring-javaformat:apply -q 2>&1 | tail -2; mvn test -Dtest='LayeredArchitectureTest' 2>&1 | grep -iE "Tests run:|BUILD" | tail -2

2E 完成。總結：

**`device ↔ dispatch` 環已斷（2E）**
- 新增 `common/dispatch/port/OpenWorkOrderCounter.java`（埠）與 `dispatch/service/OpenWorkOrderCounterAdapter.java`（dispatch 提供實作，持有 `WorkOrderRepository` 與 `WorkOrderStatus` 語意）。
- `DeviceService.getStats()` 改依賴埠，移除對 `dispatch` 的 import。
- 驗證：`device→dispatch` import 歸零；`dispatch→device` 保留 1（合法消費方向）。

**ArchUnit 治理**
- `LayeredArchitectureTest` 3/3 通過。FreezingArchRule 在成功跑後**自動清除**已解決的 `device↔schema`、`device↔dispatch` 凍結項。
- 過時註解已更正（不再提 `auth↔tenant`）。

**目前凍結庫剩餘（真實未解項）**
- 環：僅 `dept ↔ user`（L2↔L2，屬獨立議題，不在問題二/四範圍）。
- 分層違規：`audit→dept`、`config→auth`（向上依賴，非環）。

**狀態**：問題二（平台底層 7 環）+ 問題四（device↔schema、device↔dispatch）**全部循環已斷**。報告 module-boundary-assessment.md 已更新（§4 加 2E 子節、§6 標示全解、矩陣 device→dispatch 歸零、§7/§8 同步）。

唯一可再收尾的是 `dept↔user`——若要把 `no_cyclic_dependencies` 改為嚴格（移除凍結），需先反轉 `DeptService.deleteDept()` 對 user 儲存庫的依賴。要我接著處理嗎？

Completed: *Break upward dependency in DeviceService* (1/2)

Made changes.