Updated todo list

簡短結論：**不建議**把 `l0/l1/l2…` 加進 package 名稱。理由如下。

## 為什麼不建議

**1. 層級是「推導出來的視角」，不是穩定的身分**
報告裡的 L0–L4 是你**用依賴方向反推**出來的分析框架。它會隨重構而變動——例如 `schema` 將來併入 `device`、`auth/user` 若合併成 Identity 域，層級歸屬就變了。把它寫死進 package 名（`com.taipei.iot.l2.auth`），等於把「當下的分析快照」固化成「實體結構」，日後一旦調整就要大規模改 import，成本高於收益。

**2. 違反你自己訂的 YAGNI / 避免過度設計原則**
README 立場是「追求合理邊界，但警惕過度設計」。`lN` 前綴**不會幫編譯器或執行期擋掉任何違規依賴**——它只是視覺提示。真正能擋的是工具（見下），加前綴是「看起來很有架構」但實質防護為零的裝飾。

**3. 數字會誤導**
層級是「整個模組」的粗略定位，但實際依賴是「class 對 class」。例如 `common`（L0）裡某個 class 反而被誤放，光看 package 名 `l0.common` 反而讓人以為「一定乾淨」，掩蓋真實違規。

## 更好的做法：用 ArchUnit 把分層「斷言」出來

報告 §8 已經提到了——**這才是正解**。層級規則寫成測試，而非 package 名：

```java
@AnalyzeClasses(packages = "com.taipei.iot")
class LayeredArchitectureTest {

    @ArchTest
    static final ArchRule layer_dependencies_are_respected =
        layeredArchitecture().consideringOnlyDependenciesInLayers()
            .layer("L0_common").definedBy("..common..")
            .layer("L1_platform").definedBy("..tenant..", "..audit..", "..setting..", "..config..")
            .layer("L2_identity").definedBy("..auth..", "..user..", "..rbac..", "..dept..")
            .layer("L3_domain").definedBy("..announcement..", "..device..", "..schema..", "..workflow..", "..notification..")
            .layer("L4_facade").definedBy("..platform..", "..assettransfer..", "..dispatch..")
            // L0 不得依賴任何上層
            .whereLayer("L1_platform").mayOnlyBeAccessedByLayers("L2_identity", "L3_domain", "L4_facade")
            .whereLayer("L2_identity").mayOnlyBeAccessedByLayers("L3_domain", "L4_facade")
            .whereLayer("L3_domain").mayOnlyBeAccessedByLayers("L4_facade");

    @ArchTest
    static final ArchRule common_has_no_business_dependencies =
        noClasses().that().resideInAPackage("..common..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "..tenant..", "..auth..", "..user..", "..workflow..", "..dept..", "..audit..");

    @ArchTest
    static final ArchRule no_cyclic_dependencies =
        slices().matching("com.taipei.iot.(*)..").should().beFreeOfCycles();
}
```

這個方案的優勢：
- **層級定義集中在一處**（測試檔），調整層級只改這一個檔，不動任何 import。
- **CI 自動回歸**：你剛完成的 問題一/2A/2B/問題三 成果會被鎖住，未來誰重新長出 `user→auth` 或 `common→business` 的環，build 直接 fail。
- **零結構成本**：package 維持現在的領域命名（`com.taipei.iot.auth`），語意最直覺。

## 什麼情況才考慮在 package 反映層級？

只有當層級會變成**強制的物理邊界**時才值得——例如拆成 Gradle/Maven 多模組（`iot-common`、`iot-platform`、`iot-domain` 各自獨立 artifact），用編譯依賴強制單向。但這對你目前單體後端是**過度設計**，報告 §8 也明確不建議走微模組/微服務。

---

**建議**：維持現有領域 package 命名，改用一支 ArchUnit 測試把 L0–L4 規則與「禁止雙向依賴」斷言起來。需要的話我可以直接幫你建立這支測試（含 `pom.xml` 加 `com.tngtech.archunit:archunit-junit5` 依賴），並用它驗證你目前所有已修成果是否真的乾淨。