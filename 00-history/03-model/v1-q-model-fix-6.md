Updated todo list

你的領域理解很關鍵——它直接決定了 `device↔schema` 這個環該**斷哪一邊**。讓我先看 `DeviceTemplateService` 為什麼反向依賴 `DeviceRepository`。 

Read [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot/schema/service/DeviceTemplateService.java)

完全吻合你的領域模型。關鍵發現：`DeviceTemplateService`（schema）**只有一處**反向依賴 `device`——在 `deleteDeviceType()` 裡：

```java
long deviceCount = deviceRepository.countByDeviceType(deviceType);
if (deviceCount > 0) throw ... "尚有 N 個設備使用此類型，無法刪除";
```

這是一個「**刪除前的使用中防護**」（referential integrity guard）。

## 你的模型如何決定斷環方向

你描述的領域關係是：

```
schema (device template)  ← 定義層／藍圖
   ↑ 讀取        ↑ 讀取         ↑ 讀取
 device      telemetry      event-rule
```

schema 是**被多方消費的定義來源**（device 驗證屬性、telemetry 驗證格式、event-rule 從 schema 設定規則）。所以：

- ✅ **保留 `device → schema`**（消費者→定義，自然方向）
- ❌ **斷掉 `schema → device`**（定義層不該回頭依賴消費者）

> 這也修正了舊報告「schema 宜**併入** device」的建議——你的模型顯示 schema 是 telemetry／event-rule 也要共用的**獨立基礎模組**，不該併進 device，而應維持為更低的定義層、被上層單向依賴。

## 修法（沿用 2C 的 Port/Adapter）

唯一要處理的就是那個「使用中檢查」：

| 動作 | 內容 |
|---|---|
| 新增 port | `common/device/port/DeviceTypeUsageGuard.java`：`long countDevicesOfType(String deviceType)` |
| device 提供 adapter | `device/service/DeviceTypeUsageAdapter.java` implements，包 `deviceRepository.countByDeviceType` |
| schema 改用 port | `DeviceTemplateService` 把 `DeviceRepository` 欄位換成 `DeviceTypeUsageGuard`，`deleteDeviceType` 改呼叫 port |

效果：`schema → device` 1→**0**，`device→schema` 保留 ⟹ **device↔schema 環斷開**，且 schema 成為乾淨的定義層。

先確認 `countByDeviceType` 簽章與沒有其他 schema→device 引用，再動手。 

Ran terminal command:  echo "### schema → device 全部引用"; grep -rn "import com\.taipei\.iot\.device\." schema/; echo; echo "### countByDeviceType 簽章"; grep -rn "countByDeviceType" device/repository/DeviceRepository.java; echo; echo "### common/device 是否已存在"; ls -d common/device 2>/dev/null || echo "(無，需新建)"

確認無誤——只有一處，且只用在 `deleteDeviceType`。動手實作。 

Created [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot/common/device/port/DeviceTypeUsageGuard.java)

Created [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot/device/service/DeviceTypeUsageAdapter.java)

Replacing 11 lines with 11 lines in [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot/schema/service/DeviceTemplateService.java)


```

```
Now 

Created 4 todos

check for existing tests on `DeviceTemplateService` that may mock `DeviceRepository`. 

Ran terminal command:  cd /home/kevin/workspaces/side-project/IoT-forge/backend && grep -rln "DeviceTemplateService\|DeviceTypeUsageGuard" src/test/ 2>/dev/null

No existing tests reference it. Format, rebuild, and verify the cycle is broken.   

Ran terminal command:  mvn spring-javaformat:apply -q && mvn -q clean test-compile 2>&1 | tail -5 && echo "=== COMPILE OK ===" && cd src/main/java/com/taipei/iot && echo "schema→device: $(grep -rl 'import com\.taipei\.iot\.device\.' schema/ 2>/dev/null | wc -l)  (was 1)"; echo "device→schema: $(grep -rl 'import com\.taipei\.iot\.schema\.' device/ 2>/dev/null | wc -l)  (保留)"

`schema → device` 已歸零，`device → schema` 保留。Run the architecture test to confirm no regression.    

Ran terminal command:  cd /home/kevin/workspaces/side-project/IoT-forge/backend && mvn test -Dtest='LayeredArchitectureTest' 2>&1 | grep -iE "Tests run:|BUILD|cycle|Slice (device|schema)" | head -15

ArchUnit 3/3 pass。Let me update the assessment report to mark device↔schema fixed and revise the schema layering guidance per your domain model.     

Read [](file:///home/kevin/workspaces/side-project/IoT-forge/00-history/03-model/module-boundary-assessment.md#98-98), lines 98 to 132

Replacing 2 lines with 2 lines in [](file:///home/kevin/workspaces/side-project/IoT-forge/00-history/03-model/module-boundary-assessment.md)


```

```
Now add the 2D subsection and revise the schema-layering guidance. Let me find the 2C subsection end and the schema status lines. 

Read [](file:///home/kevin/workspaces/side-project/IoT-forge/00-history/03-model/module-boundary-assessment.md#136-136), lines 136 to 160

Replacing 3 lines with 17 lines in [](file:///home/kevin/workspaces/side-project/IoT-forge/00-history/03-model/module-boundary-assessment.md)


```

```
Now revise the schema-subordinate guidance in the module status list and roadmap to match the definition-layer model.  

Replacing 4 lines with 4 lines in [](file:///home/kevin/workspaces/side-project/IoT-forge/00-history/03-model/module-boundary-assessment.md)


```

```
完成 **2D：device↔schema 環已斷**，並把你的領域模型寫進報告。

## 本次成果

**新增 1 個 port + 1 個 adapter**
- DeviceTypeUsageGuard.java（common 定義）← DeviceTypeUsageAdapter.java（device 實作）

**改 1 個檔**：DeviceTemplateService.java 把 `DeviceRepository` 換成 `DeviceTypeUsageGuard`，`deleteDeviceType` 的「使用中防護」改走 port。

## 驗證

| 項目 | 結果 |
|---|---|
| `schema → device` import | 1 → **0** |
| `device → schema`（消費者→定義，合法）| 1 保留 |
| `mvn clean test-compile` | BUILD SUCCESS |
| `LayeredArchitectureTest` | 3 passed |

## 你的領域模型如何反映在架構上

報告已更新：schema 從舊版的「⚠️ 宜併入 device」改為 **「✅ 裝置型別定義層，被 device / telemetry / event-rule 單向消費，維持獨立模組」**。這為你日後的 telemetry 與 event-rule 鋪好路——它們都會 `→ schema`（單向讀定義），不會再製造環。

```
        schema (定義層)
       ↑      ↑       ↑
   device  telemetry  event-rule   ← 未來都單向消費
```

## 剩下最後一個環

`device ↔ dispatch`（2/2）— 工單關聯裝置（`dispatch→device`，合理）↔ 裝置統計反查工單（`device→schema` 的 `DeviceService.getStats()` 用 `WorkOrderRepository.countOpenWorkOrders`）。建議方向：**斷 `device→dispatch`**，把裝置的「未結工單數」統計改由 dispatch 提供（port）或事件投影。

要我接著修這最後一個環嗎？修完後問題二、四的循環依賴就全數清零，可考慮把 ArchUnit 的 `no_cyclic_dependencies` 收緊。

Starting: *Set up event rules based on schema* (4/4)

Made changes.