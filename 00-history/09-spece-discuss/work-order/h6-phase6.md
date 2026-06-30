> 是預設先從ROLE_OPERATOR找到一個帳號做指派，但是ROLE_OPERATOR內的每一個帳號都可以看到該筆工單，並且回覆？


### 改動摘要

**後端 — 施工任務改為全體 OPERATOR 可見**

| 檔案 | 變更 |
|---|---|
| WorkOrderRepository.java | 新增 `findByStatusIn()` — 只看狀態不看指派者 |
| WorkOrderService.java | `listMyTasks()` 改用 `findByStatusIn()`；`startWork()` 新增 `userId` 參數，到場打卡時自動接手任務（更新 `assignedTo`） |
| WorkOrderController.java | `startWork` 傳入當前使用者 ID |

**前端 — 施工任務頁面**

```
施工任務 (/dispatch/my-tasks)
──────────────────────────────────────────
ID  | 設備 | 狀態 | 描述    | 指派對象    | 指派的時間    | 操作
────┼────┼────┼────────┼───────────┼───────────┼───────────
101 | 1   | 待施工 | 路燈不亮 | tech01    | 06-25 10:00 | [到場打卡]
102 | 2   | 施工中 | 電線斷裂 | tech02    | 06-25 11:00 | [完成維修]
103 | 3   | 待施工 | 控制箱故障 | 待接手    | -          | [到場打卡] ← 任何人都可打卡接手
```

### 完整流程

```
通報 → 審核 → 派工 → 施工任務看板 → 到場打卡(接手) → 完成維修
                                      ↑
                             所有 OPERATOR 都看得到
                             誰先打卡誰接手
```

Made changes.