Updated todo list

需要引入兩個新 permission 讓三個畫面權限完全分離。先確認 controller 的 API guards： 

Read [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot/dispatch/controller/WorkOrderController.java#35-35), lines 35 to 135

現況問題：兩個新 permission 缺乏精確分工。需要引入 `WORK_ORDER_DISPATCH` 和 `WORK_ORDER_EXECUTE`，並修正 API guards 和選單。     

Created [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/main/resources/db/migration/V14__refine_work_order_permissions.sql)

更新 API guards：       

Replacing 15 lines with 15 lines in [](file:///home/kevin/workspaces/side-project/IoT-forge/backend/src/main/java/com/taipei/iot/dispatch/controller/WorkOrderController.java)


```

```


Ran terminal command:  mvn spring-javaformat:apply -q && mvn compile -q 2>&1 | tail -5

編譯通過。完整的權限矩陣（修正後）：

| 操作 | API Guard | DEPT_USER | DEPT_ADMIN | OPERATOR | ADMIN |
|---|---|:---:|:---:|:---:|:---:|
| 查詢工單列表 | `WORK_ORDER_VIEW` | ✓ | ✓ | ✓ | ✓ |
| 建立工單 | `WORK_ORDER_MANAGE` | ✓ | ✓ | ✓ | ✓ |
| **指派工單** | `WORK_ORDER_DISPATCH` | ✗ | ✓ | ✗ | ✓ |
| **到場打卡** | `WORK_ORDER_EXECUTE` | ✗ | ✗ | ✓ | ✓ |
| **完成施工** | `WORK_ORDER_EXECUTE` | ✗ | ✗ | ✓ | ✓ |
| **核准工單** | `WORK_ORDER_APPROVE` | ✓ | ✗ | ✗ | ✓ |
| **駁回工單** | `DISPATCH` 或 `APPROVE` | ✓ | ✓ | ✗ | ✓ |

| 選單頁面 | permission_code | DEPT_USER | DEPT_ADMIN | OPERATOR |
|---|---|:---:|:---:|:---:|
| 所有工單 | `WORK_ORDER_VIEW` | ✓ | ✓ | ✓ |
| **待審核** | `WORK_ORDER_DISPATCH` | ✗ | ✓ | ✗ |
| **施工任務** | `WORK_ORDER_EXECUTE` | ✗ | ✗ | ✓ |
| 待驗證 | `WORK_ORDER_APPROVE` | ✓ | ✗ | ✗ |

Created 4 todos

Made changes.