在 Linux 上啟動 PostgreSQL，請依照下列步驟操作：

1. 如果你是用 Ubuntu/Debian，請在終端機輸入：
```
sudo systemctl start postgresql
```
2. 若要確認 PostgreSQL 是否已啟動，請輸入：
```
sudo systemctl status postgresql
```
3. 若要讓 PostgreSQL 開機自動啟動，請輸入：
```
sudo systemctl enable postgresql
```

如果你是用其他 Linux 發行版，指令可能略有不同，例如：
- CentOS/RHEL：`sudo systemctl start postgresql-14`（版本號依實際安裝為主）

如尚未安裝 PostgreSQL，可用下列指令安裝（以 Ubuntu 為例）：
```
sudo apt update
sudo apt install postgresql
```

請依你的系統版本調整指令。

mvn spring-boot:run -Dspring-boot.run.profiles=test