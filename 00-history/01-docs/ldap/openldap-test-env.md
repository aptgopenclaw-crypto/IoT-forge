# OpenLDAP 測試環境設定

## 前提

- Docker 已安裝並可執行（需要 sudo 或用戶在 docker group）
- 測試用 domain：`DC=example,DC=tw`

---

## 啟動 OpenLDAP Container

```bash
docker run -d \
  --name openldap \
  -p 389:389 \
  -e LDAP_ORGANISATION="Example Org" \
  -e LDAP_DOMAIN="example.tw" \
  -e LDAP_ADMIN_PASSWORD="admin123" \
  osixia/openldap:latest
```

驗證是否正常：
```bash
docker exec openldap ldapsearch \
  -x -H ldap://localhost:389 \
  -D "cn=admin,DC=example,DC=tw" \
  -w admin123 \
  -b "DC=example,DC=tw" "(objectclass=*)" dn
```

---

## 建立測試用戶

Alice 的 DN 為 `mail=alice@example.tw,ou=Users,DC=example,DC=tw`（以 **mail 屬性**為 RDN）：

```bash
cat > test-user.ldif << 'EOF'
dn: ou=Users,DC=example,DC=tw
objectClass: organizationalUnit
ou: Users

dn: mail=alice@example.tw,ou=Users,DC=example,DC=tw
objectClass: inetOrgPerson
objectClass: top
cn: Alice Chen
sn: Chen
mail: alice@example.tw
userPassword: alice123
EOF

ldapadd \
  -x -H ldap://localhost:389 \
  -D "cn=admin,DC=example,DC=tw" \
  -w admin123 \
  -f test-user.ldif
```

---

## 驗證 Bind 是否成功

模擬系統的 direct bind（使用完整 DN + 密碼）：

```bash
ldapwhoami \
  -x -H ldap://localhost:389 \
  -D "mail=alice@example.tw,ou=Users,DC=example,DC=tw" \
  -w alice123
```

預期輸出：
```
dn:mail=alice@example.tw,ou=Users,dc=example,dc=tw
```

---

## 查看 LDAP 用戶資料

```bash
ldapsearch \
  -x -H ldap://localhost:389 \
  -D "cn=admin,DC=example,DC=tw" -w admin123 \
  -b "ou=Users,DC=example,DC=tw" "(mail=alice@example.tw)"
```

---

## 常見問題

### `ldapadd` 回傳 exit code 1

原因通常是 ou=Users 已存在：
```
adding new entry "ou=Users,DC=example,DC=tw"
ldap_add: Already exists (68)
```
解法：移除 ldif 中 `ou=Users` 的部分，只新增用戶。

### DN 格式問題

`user_dn_pattern` 的 `{0}` 會被 `email` 取代，確認 LDAP 裡的 RDN 與 pattern 一致：

| LDAP entry DN | 對應 pattern |
|---------------|-------------|
| `mail=alice@example.tw,ou=Users,...` | `mail={0},ou=Users,...` |
| `uid=alice,ou=Users,...` | `uid={0},ou=Users,...` |
| `CN=Alice Chen,OU=Users,...` | 需要 search，不適用 direct bind |

---

## 停止 / 清除測試環境

```bash
docker stop openldap && docker rm openldap
```
