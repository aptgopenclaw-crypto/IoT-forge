# Taipei IoT Platform (iot-forge)

一個基於 Spring Boot 3 + Vue 3 的多租戶物聯網管理平台。

## 📋 目錄

- [功能特性](#功能特性)
- [系統架構](#系統架構)
- [技術棧](#技術棧)
- [快速開始](#快速開始)
- [開發指南](#開發指南)
- [專案結構](#專案結構)

## ✨ 功能特性

### 多租戶架構
- 完整的租戶隔離機制（TenantContext ThreadLocal）
- 租戶級別的數據範圍控制
- 支援單租戶/多租戶模式切換

### 權限管理
- JWT 認證 + Refresh Token 機制
- RBAC 角色權限控制
- LDAP / 本地雙認證源
- 管理員模擬登入功能

### 核心模組
- **設備管理**：IoT 設備註冊、模板、遙測數據 schema
- **工單調度**：工單分派、審批、任務管理
- **資產轉移**：資產轉移申請與審批流程
- **工作流程引擎**：自定義審批流程
- **公告系統**：多語言公告、置頂、已讀追蹤
- **通知中心**：Email、WebSocket 即時推送
- **審計日誌**：Envers 審計 + 登入日誌

### 前端特色
- Vue 3 + TypeScript + Vite
- Element Plus UI 組件庫
- Pinia 狀態管理
- vue-i18n 多語言（zh-TW / en）
- 響應式佈局（租戶/平台雙主題）

## 🏗️ 系統架構

```
┌─────────────────────────────────────────────────────────────┐
│                      Frontend (Vue 3)                        │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │ TenantLayout│  │PlatformLayout│  │   Dynamic Routes    │  │
│  │  (Green)    │  │   (Dark)     │  │   (Menu-driven)     │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                              ↓ HTTPS /v1/*
┌─────────────────────────────────────────────────────────────┐
│                   Backend (Spring Boot 3)                    │
│  ┌──────────────────────────────────────────────────────┐   │
│  │              Security Filter Chain                    │   │
│  │  JwtAuthenticationFilter → ScopeEnforcementFilter    │   │
│  └──────────────────────────────────────────────────────┘   │
│  ┌──────────┬──────────┬──────────┬──────────────────────┐  │
│  │   Auth   │  Tenant  │   RBAC   │    Domain Modules    │  │
│  │  Module  │  Module  │  Module  │ (Device/Dispatch/..) │  │
│  └──────────┴──────────┴──────────┴──────────────────────┘  │
│  ┌──────────────────────────────────────────────────────┐   │
│  │              TenantContext (ThreadLocal)              │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                              ↓ JPA / Hibernate
┌─────────────────────────────────────────────────────────────┐
│                     PostgreSQL + PostGIS                     │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │  Business   │  │   Envers    │  │      Flyway         │  │
│  │   Tables    │  │  Audit Log  │  │   Migrations        │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

## 🛠️ 技術棧

### Backend
| 技術 | 版本 | 說明 |
|------|------|------|
| Java | 21 | LTS 版本 |
| Spring Boot | 3.4.1 | 核心框架 |
| Spring Security | - | JWT + LDAP 認證 |
| Spring Data JPA | - | ORM + 多租戶過濾 |
| Hibernate Spatial | - | PostGIS 地理空間支持 |
| Spring Data Envers | - | 審計日誌 |
| jjwt | 0.12.6 | JWT 處理 |
| MapStruct | 1.6.3 | DTO 映射 |
| Redis | - | 緩存 / Session |
| Flyway | - | 數據庫遷移 |

### Frontend
| 技術 | 版本 | 說明 |
|------|------|------|
| Vue | 3.5+ | 核心框架 |
| TypeScript | 5.7 | 類型安全 |
| Vite | - | 構建工具 |
| Element Plus | 2.9+ | UI 組件庫 |
| Pinia | 2.3+ | 狀態管理 |
| Vue Router | 4.5+ | 路由 |
| vue-i18n | 9.14+ | 國際化 |
| Axios | 1.7+ | HTTP 客戶端 |
| Vitest | - | 單元測試 |
| Playwright | 1.60+ | E2E 測試 |

### DevOps & Tools
- **Maven**: 後端構建 / 依賴管理
- **npm**: 前端包管理
- **PostgreSQL + PostGIS**: 主數據庫
- **Redis**: 緩存層
- **MCP Tools**: codegraph（代碼圖譜）、postgres（SQL 查詢）

## 🚀 快速開始

### 環境要求
- Java 21+
- Node.js 18+
- PostgreSQL 14+ (with PostGIS)
- Redis 6+
- Maven 3.8+

### 1. 克隆倉庫
```bash
git clone <repository-url>
cd iot-forge
```

### 2. 配置環境變量
編輯 `.env` 文件：
```bash
DB_PASSWORD=your_password
JWT_SECRET=your_jwt_secret
# 其他敏感配置...
```

### 3. 啟動後端
```bash
cd backend

# 編譯並運行測試
mvn clean verify

# 跳過測試編譯
mvn clean verify -DskipTests

# 啟動應用（需先配置 PostgreSQL + Redis）
mvn spring-boot:run
```

### 4. 啟動前端
```bash
cd frontend

# 安裝依賴
npm install

# 啟動開發服務器（代理 /v1 → localhost:8080）
npm run dev

# 生產構建
npm run build
```

### 5. 訪問應用
- 前端：http://localhost:5173
- 後端 API: http://localhost:8080

## 📖 開發指南

### 後端常用命令
```bash
# 只運行單元測試（排除 integration tag）
mvn test

# 運行單一測試類
mvn test -Dtest=AnnouncementServiceTest

# 運行單一測試方法
mvn test -Dtest=AnnouncementServiceTest#createAnnouncement_shouldSucceed

# 只運行集成測試
mvn verify -DskipUTs -Dtest='*IntegrationTest' -DfailIfNoTests=false

# 代碼格式化檢查
mvn validate
```

### 前端常用命令
```bash
# 開發模式
npm run dev

# 類型檢查
npm run type-check

# 運行測試（watch 模式）
npm run test

# 運行測試（單次）
npm run test:run

# i18n 驗證
npm run lint:i18n

# 無障礙測試
npm run lint:a11y
```

### MCP 工具（開發輔助）
| 服務器 | 工具 | 用途 |
|--------|------|------|
| **codegraph** | `codegraph_explore` | 理解代碼、追蹤調用路徑、定位符號 |
| **codegraph** | `codegraph_node` | 讀取文件或獲取符號的源碼 + 調用者/被調用者 |
| **codegraph** | `codegraph_search` | 快速符號名稱搜索 |
| **codegraph** | `codegraph_callers` | 列出調用給定符號的函數 |
| **postgres** | `query` | 執行只讀 SQL 查詢 |

## 📁 專案結構

```
iot-forge/
├── backend/                 # Spring Boot 後端
│   ├── src/main/java/com/taipei/iot/
│   │   ├── auth/           # 認證模組（JWT, LDAP）
│   │   ├── tenant/         # 租戶管理
│   │   ├── rbac/           # 角色權限控制
│   │   ├── dept/           # 部門樹
│   │   ├── user/           # 用戶管理
│   │   ├── announcement/   # 公告系統
│   │   ├── audit/          # 審計日誌
│   │   ├── workflow/       # 工作流程引擎
│   │   ├── assettransfer/  # 資產轉移
│   │   ├── device/         # IoT 設備管理
│   │   ├── dispatch/       # 工單調度
│   │   ├── notification/   # 通知推送
│   │   ├── platform/       # 平台級管理
│   │   ├── common/         # 通用組件
│   │   ├── config/         # 配置類
│   │   └── setting/        # 系統設置
│   ├── src/main/resources/
│   │   ├── application.yml # 主配置文件
│   │   └── db/migration/   # Flyway 遷移腳本
│   └── pom.xml
│
├── frontend/               # Vue 3 前端
│   ├── src/
│   │   ├── api/           # API 客戶端
│   │   ├── components/    # 可複用組件
│   │   ├── layouts/       # 佈局組件
│   │   ├── locales/       # i18n 語言包
│   │   ├── router/        # 路由配置
│   │   ├── stores/        # Pinia Stores
│   │   ├── views/         # 頁面視圖
│   │   └── __tests__/     # 測試文件
│   ├── vite.config.ts
│   └── package.json
│
├── docs/                   # 文檔
│   └── superpowers/
│
├── 00-history/             # 歷史文檔
├── 01-cli/                 # CLI 工具
├── skills/                 # 技能文件
├── CLAUDE.md               # Claude Code 開發指南
├── .env                    # 環境變量（敏感信息）
└── README.md               # 本文件
```

## 🔐 安全架構

### 認證流程
1. 用戶登錄 → 驗證憑證（本地/LDAP）→ 頒發臨時 Token
2. 選擇租戶 → 交換臨時 Token 獲取 Access + Refresh JWT
3. Access Token（30 分鐘）存內存；Refresh Token（7 天）存 HttpOnly Cookie
4. 前端在 Token 過期前 60 秒自動刷新

### 多租戶隔離
- `TenantContext` ThreadLocal 持有當前租戶 ID
- JPA `TenantFilter` 自動過濾非當前租戶數據
- `@RunInSystemTenantContext` 用於跨租戶操作

### 權限範圍（Scope）
- `TENANT`: 租戶級別訪問
- `PLATFORM`: 平台級別訪問
- `IMPERSONATION`: 管理員模擬

## 📄 許可證

[請在此處添加許可證信息]

## 👥 貢獻指南

[請在此處添加貢獻指南]
