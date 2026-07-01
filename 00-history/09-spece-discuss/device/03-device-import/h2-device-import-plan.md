# 設備匯入功能 — 實作計畫

> **For agentic workers:** 使用 `superpowers:subagent-driven-development` 或 `superpowers:executing-plans` 依序執行各任務。步驟使用 `- [ ]` checkbox 追蹤。

**目標：** 提供用戶透過 Excel (.xlsx) 或 CSV 批次匯入設備，支援先驗證再整批寫入，並預留泛型匯入引擎供未來擴充。

**架構：** 泛型匯入引擎 `ImportOrchestrator` + `ImportStrategy<T>` 介面，第一版實作 `DeviceImportStrategy`。解析層使用 `FileParser` 工廠模式。前端以 `ImportDialog.vue` Dialog 元件呈現 4 種狀態。

**Tech Stack:** Java 21, Spring Boot 3.4.1, Apache POI 5.3.0, commons-csv 1.11.0, Vue 3, ElementPlus, Pinia

---

## 檔案結構變更

```
CREATE:
  backend/src/main/java/com/taipei/iot/import_/
    ├── ImportOrchestrator.java
    ├── ImportStrategy.java
    ├── ImportResult.java
    ├── ImportResponse.java
    ├── ImportError.java
    ├── config/
    │   └── ImportProperties.java
    └── parser/
        ├── FileParser.java
        ├── FileParserFactory.java
        ├── ExcelParser.java
        └── CsvParser.java

  backend/src/main/java/com/taipei/iot/import_/device/
    ├── DeviceImportController.java
    ├── DeviceImportStrategy.java
    └── DeviceImportRow.java

  frontend/src/views/admin/device/ImportDialog.vue

MODIFY:
  backend/src/main/java/com/taipei/iot/common/enums/ErrorCode.java      # +5 ErrorCode
  backend/src/main/java/com/taipei/iot/device/repository/ContractRepository.java  # +findByTenantIdAndContractName
  backend/src/main/resources/application.yml                            # +import: section
  frontend/src/api/device/index.ts                                       # +3 API functions
  frontend/src/types/device.ts                                           # +ImportError, ImportResponse interfaces
  frontend/src/views/admin/device/DeviceListView.vue                    # +import button
  frontend/src/locales/zh-TW.ts                                         # +i18n keys
  frontend/src/locales/en.ts                                            # +i18n keys
```

---

## Task 1: 基礎模型與設定

**Files:**
- Create: `backend/src/main/java/com/taipei/iot/import_/ImportError.java`
- Create: `backend/src/main/java/com/taipei/iot/import_/ImportResult.java`
- Create: `backend/src/main/java/com/taipei/iot/import_/ImportResponse.java`
- Create: `backend/src/main/java/com/taipei/iot/import_/config/ImportProperties.java`
- Modify: `backend/src/main/java/com/taipei/iot/common/enums/ErrorCode.java`
- Modify: `backend/src/main/resources/application.yml`

**Interfaces:**
- Produces: `ImportError` (DTO, 被所有後續任務依賴), `ImportResult<T>` (泛型容器), `ImportResponse` (API 回應), `ImportProperties` (設定繫結), ErrorCode 5 個新常數

---

- [ ] **Step 1: 建立 ImportError**

```java
// backend/src/main/java/com/taipei/iot/import_/ImportError.java
package com.taipei.iot.import_;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportError {
    private int row;
    private String field;
    private String value;
    private String message;
}
```

- [ ] **Step 2: 建立 ImportResult**

```java
// backend/src/main/java/com/taipei/iot/import_/ImportResult.java
package com.taipei.iot.import_;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

@Getter
@AllArgsConstructor
public class ImportResult<T> {
    private final List<T> validRows;
    private final List<ImportError> errors;

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public static <T> ImportResult<T> success(List<T> rows) {
        return new ImportResult<>(rows, Collections.emptyList());
    }

    public static <T> ImportResult<T> failure(List<ImportError> errors) {
        return new ImportResult<>(Collections.emptyList(), errors);
    }
}
```

- [ ] **Step 3: 建立 ImportResponse**

```java
// backend/src/main/java/com/taipei/iot/import_/ImportResponse.java
package com.taipei.iot.import_;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportResponse {
    private String entityType;
    private int totalRows;
    private int successCount;
    private List<ImportError> errors;
}
```

- [ ] **Step 4: 建立 ImportProperties**

```java
// backend/src/main/java/com/taipei/iot/import_/config/ImportProperties.java
package com.taipei.iot.import_.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "import")
public class ImportProperties {

    /** 單次匯入最大筆數 */
    private int maxRows = 500;

    /** 上傳檔案大小上限 (單位: bytes)，預設 10MB */
    private long maxFileSize = 10 * 1024 * 1024;

    /** 允許的副檔名，逗號分隔 */
    private String allowedExtensions = "xlsx,csv";

    public int getMaxRows() { return maxRows; }
    public void setMaxRows(int maxRows) { this.maxRows = maxRows; }

    public long getMaxFileSize() { return maxFileSize; }
    public void setMaxFileSize(long maxFileSize) { this.maxFileSize = maxFileSize; }

    public String getAllowedExtensions() { return allowedExtensions; }
    public void setAllowedExtensions(String allowedExtensions) { this.allowedExtensions = allowedExtensions; }
}
```

- [ ] **Step 5: 新增 ErrorCode**

在 `ErrorCode.java` 中加入：

```java
// 在 DEVICE 區段之後、IOT 區段之前
DEVICE_IMPORT_FILE_EMPTY("A200", 400, "上傳檔案為空"),
DEVICE_IMPORT_FILE_FORMAT("A201", 400, "不支援的檔案格式，僅允許 .xlsx 與 .csv"),
DEVICE_IMPORT_MAX_ROWS_EXCEEDED("A202", 400, "匯入筆數超過單次上限"),
DEVICE_IMPORT_VALIDATION_FAILED("A203", 400, "部分資料驗證未通過"),
DEVICE_IMPORT_HEADER_MISMATCH("A204", 400, "檔案標題列與範本不符"),
```

- [ ] **Step 6: 加入 application.yml 設定**

```yaml
# 在檔案末尾或其他設定區塊後加入
import:
  max-rows: 500
  max-file-size: 10485760
  allowed-extensions: xlsx,csv
```

- [ ] **Step 7: 編譯確認**

```bash
cd backend && ./mvn compile -q
```
預期：BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/taipei/iot/import_/ImportError.java \
       backend/src/main/java/com/taipei/iot/import_/ImportResult.java \
       backend/src/main/java/com/taipei/iot/import_/ImportResponse.java \
       backend/src/main/java/com/taipei/iot/import_/config/ImportProperties.java \
       backend/src/main/java/com/taipei/iot/common/enums/ErrorCode.java \
       backend/src/main/resources/application.yml
git commit -m "feat(import): add base models, ImportProperties config, and ErrorCodes"
```

---

## Task 2: FileParser 層（Excel + CSV）

**Files:**
- Create: `backend/src/main/java/com/taipei/iot/import_/parser/FileParser.java`
- Create: `backend/src/main/java/com/taipei/iot/import_/parser/FileParserFactory.java`
- Create: `backend/src/main/java/com/taipei/iot/import_/parser/ExcelParser.java`
- Create: `backend/src/main/java/com/taipei/iot/import_/parser/CsvParser.java`

**Interfaces:**
- Produces: `FileParser.parse(InputStream) → List<Map<String, String>>` — 每列為一個 Map<欄位名, 值>
- Consumes: `ImportProperties` (allowedExtensions 檢查)

---

- [ ] **Step 1: 建立 FileParser 介面**

```java
// backend/src/main/java/com/taipei/iot/import_/parser/FileParser.java
package com.taipei.iot.import_.parser;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

public interface FileParser {

    /** 解析輸入流，回傳列資料清單（第一列視為 header，不回傳） */
    List<Map<String, String>> parse(InputStream inputStream);

    /** 回傳此 parser 支援的副檔名（不含 dot） */
    String supportedExtension();
}
```

- [ ] **Step 2: 建立 FileParserFactory**

```java
// backend/src/main/java/com/taipei/iot/import_/parser/FileParserFactory.java
package com.taipei.iot.import_.parser;

import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class FileParserFactory {

    private final Map<String, FileParser> parserMap;

    public FileParserFactory(List<FileParser> parsers) {
        this.parserMap = parsers.stream()
                .collect(Collectors.toMap(FileParser::supportedExtension, p -> p));
    }

    public FileParser getParser(String extension) {
        FileParser parser = parserMap.get(extension.toLowerCase());
        if (parser == null) {
            throw new BusinessException(ErrorCode.DEVICE_IMPORT_FILE_FORMAT);
        }
        return parser;
    }
}
```

- [ ] **Step 3: 建立 ExcelParser**

```java
// backend/src/main/java/com/taipei/iot/import_/parser/ExcelParser.java
package com.taipei.iot.import_.parser;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Component
public class ExcelParser implements FileParser {

    @Override
    public String supportedExtension() {
        return "xlsx";
    }

    @Override
    public List<Map<String, String>> parse(InputStream inputStream) {
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            List<Map<String, String>> result = new ArrayList<>();

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                return result;
            }
            int colCount = headerRow.getLastCellNum();
            String[] headers = new String[colCount];
            for (int i = 0; i < colCount; i++) {
                headers[i] = getCellStringValue(headerRow.getCell(i)).toLowerCase();
            }

            for (int rowIdx = 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null) continue;
                Map<String, String> rowMap = new LinkedHashMap<>();
                for (int colIdx = 0; colIdx < colCount; colIdx++) {
                    rowMap.put(headers[colIdx], getCellStringValue(row.getCell(colIdx)));
                }
                // 略過全空列
                if (rowMap.values().stream().allMatch(v -> v == null || v.isBlank())) {
                    continue;
                }
                result.add(rowMap);
            }
            return result;
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse Excel file", e);
        }
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toLocalDate().toString();
                }
                double val = cell.getNumericCellValue();
                if (val == Math.floor(val) && !Double.isInfinite(val)) {
                    yield String.valueOf((long) val);
                }
                yield String.valueOf(val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield String.valueOf((long) cell.getNumericCellValue());
                } catch (Exception e) {
                    try {
                        yield cell.getStringCellValue();
                    } catch (Exception e2) {
                        yield cell.getCellFormula();
                    }
                }
            }
            default -> "";
        };
    }
}
```

- [ ] **Step 4: 建立 CsvParser**

```java
// backend/src/main/java/com/taipei/iot/import_/parser/CsvParser.java
package com.taipei.iot.import_.parser;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class CsvParser implements FileParser {

    @Override
    public String supportedExtension() {
        return "csv";
    }

    @Override
    public List<Map<String, String>> parse(InputStream inputStream) {
        try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
             CSVParser csvParser = new CSVParser(reader,
                     CSVFormat.DEFAULT.builder()
                             .setHeader()
                             .setSkipHeaderRecord(true)
                             .setTrim(true)
                             .setIgnoreEmptyLines(true)
                             .build())) {

            List<Map<String, String>> result = new ArrayList<>();
            Map<String, Integer> headerMap = csvParser.getHeaderMap();
            // 統一 header 為 lowercase
            Map<String, String> headerLowerMap = new HashMap<>();
            if (headerMap != null) {
                for (String h : headerMap.keySet()) {
                    headerLowerMap.put(h.toLowerCase(), h);
                }
            }

            for (CSVRecord record : csvParser) {
                Map<String, String> rowMap = new LinkedHashMap<>();
                for (Map.Entry<String, String> entry : headerLowerMap.entrySet()) {
                    rowMap.put(entry.getKey(), record.get(entry.getValue()));
                }
                // 略過全空列
                if (rowMap.values().stream().allMatch(v -> v == null || v.isBlank())) {
                    continue;
                }
                result.add(rowMap);
            }
            return result;
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse CSV file", e);
        }
    }
}
```

- [ ] **Step 5: 編譯確認**

```bash
cd backend && ./mvn compile -q
```
預期：BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/taipei/iot/import_/parser/
git commit -m "feat(import): add FileParser layer (ExcelParser + CsvParser + factory)"
```

---

## Task 3: ImportStrategy 介面 + ImportOrchestrator

**Files:**
- Create: `backend/src/main/java/com/taipei/iot/import_/ImportStrategy.java`
- Create: `backend/src/main/java/com/taipei/iot/import_/ImportOrchestrator.java`

**Interfaces:**
- Consumes: `ImportResult<T>`, `ImportError`, `ImportProperties`, `FileParserFactory`, `FileParser`
- Produces: `ImportStrategy<T>` (泛型介面), `ImportOrchestrator` (泛型引擎)

---

- [ ] **Step 1: 建立 ImportStrategy 介面**

```java
// backend/src/main/java/com/taipei/iot/import_/ImportStrategy.java
package com.taipei.iot.import_;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ImportStrategy<T> {

    /** 實體類型名稱（用於日誌、錯誤訊息、回傳 entityType） */
    String getEntityType();

    /** 預期的標題列（全小寫），用於比對檔案欄位是否正確 */
    Set<String> expectedHeaders();

    /** 將一列原始資料 Map<欄位名, 值> 映射為 DTO，僅做型別轉換不做關聯查詢 */
    T mapToDto(Map<String, String> row);

    /** 逐筆驗證 DTO（必填、格式、列舉值、device_type 存在等） */
    List<ImportError> validate(T dto, int rowNum);

    /** 批量驗證（依賴資料庫的關聯查詢、唯一性檢查） */
    List<ImportError> batchValidate(List<T> dtos);

    /** 批量寫入（單一 @Transactional） */
    void saveAll(List<T> rows);

    /** 寫入前預處理（可選） */
    default void beforeAll(List<T> rows) {}

    /** 寫入後後處理（可選） */
    default void afterAll(List<T> rows) {}
}
```

- [ ] **Step 2: 建立 ImportOrchestrator**

```java
// backend/src/main/java/com/taipei/iot/import_/ImportOrchestrator.java
package com.taipei.iot.import_;

import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.import_.config.ImportProperties;
import com.taipei.iot.import_.parser.FileParser;
import com.taipei.iot.import_.parser.FileParserFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class ImportOrchestrator {

    private final FileParserFactory fileParserFactory;
    private final ImportProperties importProperties;

    /**
     * 解析並驗證上傳檔案，回傳驗證結果。
     * 有錯誤時不回滾 — 由 caller 決定是否回 400。
     */
    public <T> ImportResult<T> parseAndValidate(MultipartFile file, ImportStrategy<T> strategy) {
        // 1. 檢查空檔案
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.DEVICE_IMPORT_FILE_EMPTY);
        }

        // 2. 解析副檔名
        String originalName = file.getOriginalFilename();
        String extension = extractExtension(originalName);

        // 3. 檢查副檔名是否允許
        String allowed = importProperties.getAllowedExtensions();
        if (!List.of(allowed.split(",")).contains(extension)) {
            throw new BusinessException(ErrorCode.DEVICE_IMPORT_FILE_FORMAT);
        }

        // 4. 解析檔案
        FileParser parser = fileParserFactory.getParser(extension);
        List<Map<String, String>> rawRows;
        try {
            rawRows = parser.parse(file.getInputStream());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.DEVICE_IMPORT_FILE_FORMAT, "無法讀取檔案內容");
        }

        // 5. 檢查筆數上限
        if (rawRows.size() > importProperties.getMaxRows()) {
            throw new BusinessException(ErrorCode.DEVICE_IMPORT_MAX_ROWS_EXCEEDED,
                    "匯入筆數 " + rawRows.size() + " 超過單次上限 " + importProperties.getMaxRows() + " 筆");
        }

        if (rawRows.isEmpty()) {
            return ImportResult.success(List.of());
        }

        // 6. 檢查標題列
        Set<String> expected = strategy.expectedHeaders();
        Set<String> actual = rawRows.get(0).keySet();
        if (!actual.containsAll(expected)) {
            throw new BusinessException(ErrorCode.DEVICE_IMPORT_HEADER_MISMATCH,
                    "缺少必要欄位：" + expected.stream().filter(h -> !actual.contains(h)).toList());
        }

        // 7. mapToDto + 逐筆驗證
        List<ImportError> allErrors = new ArrayList<>();
        List<T> dtos = new ArrayList<>();
        for (int i = 0; i < rawRows.size(); i++) {
            T dto = strategy.mapToDto(rawRows.get(i));
            dtos.add(dto);
            allErrors.addAll(strategy.validate(dto, i + 2)); // rowNum: Excel 列號 (header=1, data start=2)
        }

        // 8. 批次驗證
        allErrors.addAll(strategy.batchValidate(dtos));

        // 9. 回傳結果
        if (!allErrors.isEmpty()) {
            log.warn("Import validation failed: {} errors for {} rows", allErrors.size(), rawRows.size());
            return ImportResult.failure(allErrors);
        }
        return ImportResult.success(dtos);
    }

    /**
     * 執行匯入（寫入資料庫）。
     */
    public <T> ImportResponse execute(ImportResult<T> result, ImportStrategy<T> strategy) {
        if (result.hasErrors()) {
            return ImportResponse.builder()
                    .entityType(strategy.getEntityType())
                    .totalRows(result.getValidRows().size())
                    .successCount(0)
                    .errors(result.getErrors())
                    .build();
        }

        List<T> rows = result.getValidRows();
        strategy.beforeAll(rows);
        strategy.saveAll(rows);
        strategy.afterAll(rows);

        log.info("Import {} success: {} rows imported", strategy.getEntityType(), rows.size());
        return ImportResponse.builder()
                .entityType(strategy.getEntityType())
                .totalRows(rows.size())
                .successCount(rows.size())
                .errors(List.of())
                .build();
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            throw new BusinessException(ErrorCode.DEVICE_IMPORT_FILE_FORMAT);
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
```

- [ ] **Step 3: 編譯確認**

```bash
cd backend && ./mvn compile -q
```
預期：BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/taipei/iot/import_/ImportStrategy.java \
       backend/src/main/java/com/taipei/iot/import_/ImportOrchestrator.java
git commit -m "feat(import): add ImportStrategy interface + ImportOrchestrator engine"
```

---

## Task 4: DeviceImportRow + DeviceImportStrategy

**Files:**
- Create: `backend/src/main/java/com/taipei/iot/import_/device/DeviceImportRow.java`
- Create: `backend/src/main/java/com/taipei/iot/import_/device/DeviceImportStrategy.java`
- Modify: `backend/src/main/java/com/taipei/iot/device/repository/ContractRepository.java`

**Interfaces:**
- Consumes: `ImportStrategy<DeviceImportRow>`, `DeviceRepository`, `DeviceTemplateService`, `DeptInfoRepository`, `ContractRepository`, `CircuitRepository`, `DeviceService`
- Produces: `DeviceImportRow` (DTO), `DeviceImportStrategy` (Strategy 實作)

---

- [ ] **Step 1: 建立 DeviceImportRow DTO**

```java
// backend/src/main/java/com/taipei/iot/import_/device/DeviceImportRow.java
package com.taipei.iot.import_.device;

import com.taipei.iot.device.enums.ConnectivityType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceImportRow {
    private String deviceType;
    private String deviceCode;
    private String deviceName;
    private BigDecimal twd97X;
    private BigDecimal twd97Y;
    private BigDecimal lng;
    private BigDecimal lat;
    private BigDecimal elevation;
    private String deptName;
    private String contractName;
    private String propertyOwner;
    private LocalDate installedAt;
    private String parentDeviceCode;
    private String mountPosition;
    private ConnectivityType connectivityType;
    private String circuitNumber;

    // raw 原始值（validate 階段用於回報格式錯誤）
    private String rawInstalledAt;
    private String rawConnectivityType;
}
```

- [ ] **Step 2: 在 ContractRepository 加入 findByTenantIdAndContractName**

```java
// 在 ContractRepository.java 中加入 (在 findByFilters 之前或之後)
Optional<Contract> findByTenantIdAndContractName(String tenantId, String contractName);
```

- [ ] **Step 3: 建立 DeviceImportStrategy**

```java
// backend/src/main/java/com/taipei/iot/import_/device/DeviceImportStrategy.java
package com.taipei.iot.import_.device;

import com.taipei.iot.common.context.TenantContext;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.dept.entity.DeptInfoEntity;
import com.taipei.iot.dept.repository.DeptInfoRepository;
import com.taipei.iot.device.entity.Circuit;
import com.taipei.iot.device.entity.Contract;
import com.taipei.iot.device.entity.Device;
import com.taipei.iot.device.enums.ConnectivityType;
import com.taipei.iot.device.enums.DeviceStatus;
import com.taipei.iot.device.repository.CircuitRepository;
import com.taipei.iot.device.repository.ContractRepository;
import com.taipei.iot.device.repository.DeviceRepository;
import com.taipei.iot.import_.ImportError;
import com.taipei.iot.import_.ImportStrategy;
import com.taipei.iot.schema.service.DeviceTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceImportStrategy implements ImportStrategy<DeviceImportRow> {

    private static final Set<String> HEADERS = Set.of(
            "device_type", "device_code", "device_name", "twd97_x", "twd97_y",
            "lng", "lat", "elevation", "dept_name", "contract_name",
            "property_owner", "installed_at", "parent_device_code",
            "mount_position", "connectivity_type", "circuit_number");

    private static final int MAX_DEVICE_CODE_LENGTH = 100;
    private static final int MAX_DEVICE_NAME_LENGTH = 200;
    private static final int MAX_PROPERTY_OWNER_LENGTH = 200;
    private static final int MAX_MOUNT_POSITION_LENGTH = 50;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final DeviceRepository deviceRepository;
    private final DeviceTemplateService deviceTemplateService;
    private final DeptInfoRepository deptInfoRepository;
    private final ContractRepository contractRepository;
    private final CircuitRepository circuitRepository;

    @Override
    public String getEntityType() {
        return "device";
    }

    @Override
    public Set<String> expectedHeaders() {
        return HEADERS;
    }

    @Override
    public DeviceImportRow mapToDto(Map<String, String> row) {
        DeviceImportRow.DeviceImportRowBuilder builder = DeviceImportRow.builder()
                .deviceType(row.get("device_type"))
                .deviceCode(row.get("device_code"))
                .deviceName(row.get("device_name"))
                .deptName(row.get("dept_name"))
                .contractName(row.get("contract_name"))
                .propertyOwner(row.get("property_owner"))
                .parentDeviceCode(row.get("parent_device_code"))
                .mountPosition(row.get("mount_position"))
                .circuitNumber(row.get("circuit_number"));

        // 保留原始值供 validate 階段回報格式錯誤
        builder.rawInstalledAt(row.get("installed_at"));
        builder.rawConnectivityType(row.get("connectivity_type"));

        // 數值解析 (空值或空白視為 null，不報錯；錯誤格式在 validate 回報，mapToDto 不拋錯)
        builder.twd97X(parseBigDecimal(row.get("twd97_x")));
        builder.twd97Y(parseBigDecimal(row.get("twd97_y")));
        builder.lng(parseBigDecimal(row.get("lng")));
        builder.lat(parseBigDecimal(row.get("lat")));
        builder.elevation(parseBigDecimal(row.get("elevation")));

        // 日期解析 (空值或格式錯誤由 validate 處理，mapToDto 不拋錯)
        if (row.get("installed_at") != null && !row.get("installed_at").isBlank()) {
            try {
                builder.installedAt(LocalDate.parse(row.get("installed_at"), DATE_FORMATTER));
            } catch (DateTimeParseException ignored) {
                // validate 階段會報錯
            }
        }

        // ConnectivityType 解析
        if (row.get("connectivity_type") != null && !row.get("connectivity_type").isBlank()) {
            try {
                builder.connectivityType(ConnectivityType.valueOf(row.get("connectivity_type").toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                // validate 階段會報錯
            }
        }

        return builder.build();
    }

    @Override
    public List<ImportError> validate(DeviceImportRow dto, int rowNum) {
        List<ImportError> errors = new ArrayList<>();

        // device_code 必填
        if (dto.getDeviceCode() == null || dto.getDeviceCode().isBlank()) {
            errors.add(error(rowNum, "device_code", dto.getDeviceCode(), "device_code 為必填"));
        } else if (dto.getDeviceCode().length() > MAX_DEVICE_CODE_LENGTH) {
            errors.add(error(rowNum, "device_code", dto.getDeviceCode(), "device_code 長度不得超過 " + MAX_DEVICE_CODE_LENGTH));
        }

        // device_name 長度
        if (dto.getDeviceName() != null && dto.getDeviceName().length() > MAX_DEVICE_NAME_LENGTH) {
            errors.add(error(rowNum, "device_name", dto.getDeviceName(), "device_name 長度不得超過 " + MAX_DEVICE_NAME_LENGTH));
        }

        // device_type 必填
        if (dto.getDeviceType() == null || dto.getDeviceType().isBlank()) {
            errors.add(error(rowNum, "device_type", dto.getDeviceType(), "device_type 為必填"));
        } else {
            // device_type 存在於 device_template
            try {
                deviceTemplateService.validateDeviceTypeExists(dto.getDeviceType());
            } catch (BusinessException e) {
                errors.add(error(rowNum, "device_type", dto.getDeviceType(), "設備類型 " + dto.getDeviceType() + " 不存在於 DeviceTemplate"));
            }
        }

        // installed_at 格式檢查
        if (dto.getRawInstalledAt() != null && !dto.getRawInstalledAt().isBlank()
                && dto.getInstalledAt() == null) {
            errors.add(error(rowNum, "installed_at", dto.getRawInstalledAt(),
                    "安裝日期格式應為 YYYY-MM-DD，收到「" + dto.getRawInstalledAt() + "」"));
        }

        // connectivity_type 合法值檢查
        if (dto.getRawConnectivityType() != null && !dto.getRawConnectivityType().isBlank()
                && dto.getConnectivityType() == null) {
            errors.add(error(rowNum, "connectivity_type", dto.getRawConnectivityType(),
                    "連線方式「" + dto.getRawConnectivityType() + "」不在允許值中 (NONE, DIRECT, GATEWAY, WIFI, LORAWAN, NB_IOT, LTE)"));
        }

        // property_owner 長度
        if (dto.getPropertyOwner() != null && dto.getPropertyOwner().length() > MAX_PROPERTY_OWNER_LENGTH) {
            errors.add(error(rowNum, "property_owner", dto.getPropertyOwner(), "property_owner 長度不得超過 " + MAX_PROPERTY_OWNER_LENGTH));
        }

        // mount_position 長度
        if (dto.getMountPosition() != null && dto.getMountPosition().length() > MAX_MOUNT_POSITION_LENGTH) {
            errors.add(error(rowNum, "mount_position", dto.getMountPosition(), "mount_position 長度不得超過 " + MAX_MOUNT_POSITION_LENGTH));
        }

        return errors;
    }

    @Override
    public List<ImportError> batchValidate(List<DeviceImportRow> dtos) {
        String tenantId = TenantContext.getCurrentTenantId();
        List<ImportError> errors = new ArrayList<>();

        // ── device_code 檔案內重複 ──
        Map<String, List<Integer>> codeRowMap = new LinkedHashMap<>();
        for (int i = 0; i < dtos.size(); i++) {
            String code = dtos.get(i).getDeviceCode();
            if (code != null && !code.isBlank()) {
                codeRowMap.computeIfAbsent(code, k -> new ArrayList<>()).add(i + 2);
            }
        }
        for (Map.Entry<String, List<Integer>> entry : codeRowMap.entrySet()) {
            if (entry.getValue().size() > 1) {
                String msg = "設備代碼 " + entry.getKey() + " 在檔案中重複（第 " + entry.getValue() + " 列）";
                for (int rowNum : entry.getValue()) {
                    errors.add(error(rowNum, "device_code", entry.getKey(), msg));
                }
            }
        }

        // ── device_code 租戶內唯一 ──
        Set<String> existingCodes = codeRowMap.keySet().stream()
                .filter(code -> deviceRepository.findByTenantIdAndDeviceCode(tenantId, code).isPresent())
                .collect(Collectors.toSet());
        for (Map.Entry<String, List<Integer>> entry : codeRowMap.entrySet()) {
            if (existingCodes.contains(entry.getKey())) {
                for (int rowNum : entry.getValue()) {
                    errors.add(error(rowNum, "device_code", entry.getKey(), "設備代碼 " + entry.getKey() + " 已存在於此租戶"));
                }
            }
        }

        // ── dept_name 存在 ──
        Set<String> deptNames = dtos.stream()
                .map(DeviceImportRow::getDeptName)
                .filter(n -> n != null && !n.isBlank())
                .collect(Collectors.toSet());
        Map<String, Boolean> deptExistsMap = new HashMap<>();
        for (String name : deptNames) {
            deptExistsMap.put(name, deptInfoRepository.findByTenantIdAndDeptName(tenantId, name).isPresent());
        }
        for (int i = 0; i < dtos.size(); i++) {
            String name = dtos.get(i).getDeptName();
            if (name != null && !name.isBlank()) {
                Boolean exists = deptExistsMap.get(name);
                if (Boolean.FALSE.equals(exists)) {
                    errors.add(error(i + 2, "dept_name", name, "部門名稱「" + name + "」對應不到任何部門"));
                }
            }
        }

        // ── contract_name 存在 ──
        Set<String> contractNames = dtos.stream()
                .map(DeviceImportRow::getContractName)
                .filter(n -> n != null && !n.isBlank())
                .collect(Collectors.toSet());
        Map<String, Boolean> contractExistsMap = new HashMap<>();
        for (String name : contractNames) {
            contractExistsMap.put(name, contractRepository.findByTenantIdAndContractName(tenantId, name).isPresent());
        }
        for (int i = 0; i < dtos.size(); i++) {
            String name = dtos.get(i).getContractName();
            if (name != null && !name.isBlank()) {
                Boolean exists = contractExistsMap.get(name);
                if (Boolean.FALSE.equals(exists)) {
                    errors.add(error(i + 2, "contract_name", name, "合約名稱「" + name + "」對應不到任何契約"));
                }
            }
        }

        // ── circuit_number 存在 ──
        Set<String> circuitNumbers = dtos.stream()
                .map(DeviceImportRow::getCircuitNumber)
                .filter(n -> n != null && !n.isBlank())
                .collect(Collectors.toSet());
        Map<String, Boolean> circuitExistsMap = new HashMap<>();
        for (String number : circuitNumbers) {
            circuitExistsMap.put(number, circuitRepository.findByTenantIdAndCircuitNumber(tenantId, number).isPresent());
        }
        for (int i = 0; i < dtos.size(); i++) {
            String number = dtos.get(i).getCircuitNumber();
            if (number != null && !number.isBlank()) {
                Boolean exists = circuitExistsMap.get(number);
                if (Boolean.FALSE.equals(exists)) {
                    errors.add(error(i + 2, "circuit_number", number, "迴路編號「" + number + "」對應不到任何迴路"));
                }
            }
        }

        // ── parent_device_code 存在 ──
        Set<String> parentCodes = dtos.stream()
                .map(DeviceImportRow::getParentDeviceCode)
                .filter(c -> c != null && !c.isBlank())
                .collect(Collectors.toSet());
        Map<String, Boolean> parentExistsMap = new HashMap<>();
        for (String code : parentCodes) {
            parentExistsMap.put(code, deviceRepository.findByTenantIdAndDeviceCode(tenantId, code).isPresent());
        }
        for (int i = 0; i < dtos.size(); i++) {
            String code = dtos.get(i).getParentDeviceCode();
            if (code != null && !code.isBlank()) {
                Boolean exists = parentExistsMap.get(code);
                if (Boolean.FALSE.equals(exists)) {
                    errors.add(error(i + 2, "parent_device_code", code, "父設備代碼「" + code + "」不存在"));
                }
                // 父設備代碼非自身
                if (code.equals(dtos.get(i).getDeviceCode())) {
                    errors.add(error(i + 2, "parent_device_code", code, "父設備代碼與自身 device_code 相同"));
                }
            }
        }

        return errors;
    }

    @Override
    @Transactional
    public void saveAll(List<DeviceImportRow> rows) {
        String tenantId = TenantContext.getCurrentTenantId();

        // 批次查詢關聯資料
        Map<String, Long> deptMap = loadDeptMap(tenantId, rows);
        Map<String, Long> contractMap = loadContractMap(tenantId, rows);
        Map<String, Long> circuitMap = loadCircuitMap(tenantId, rows);
        Map<String, Long> parentDeviceMap = loadParentDeviceMap(tenantId, rows);

        List<Device> devices = rows.stream()
                .map(row -> Device.builder()
                        .tenantId(tenantId)
                        .deviceType(row.getDeviceType())
                        .deviceCode(row.getDeviceCode())
                        .deviceName(row.getDeviceName())
                        .twd97X(row.getTwd97X())
                        .twd97Y(row.getTwd97Y())
                        .lng(row.getLng())
                        .lat(row.getLat())
                        .elevation(row.getElevation())
                        .deptId(deptMap.get(row.getDeptName()))
                        .contractId(contractMap.get(row.getContractName()))
                        .propertyOwner(row.getPropertyOwner())
                        .status(DeviceStatus.ACTIVE)
                        .installedAt(row.getInstalledAt())
                        .parentDeviceId(parentDeviceMap.get(row.getParentDeviceCode()))
                        .mountPosition(row.getMountPosition())
                        .connectivityType(row.getConnectivityType())
                        .circuitId(circuitMap.get(row.getCircuitNumber()))
                        .build())
                .toList();

        deviceRepository.saveAll(devices);
    }

    // ── 內部輔助方法 ──

    private Map<String, Long> loadDeptMap(String tenantId, List<DeviceImportRow> rows) {
        Set<String> names = rows.stream()
                .map(DeviceImportRow::getDeptName)
                .filter(n -> n != null && !n.isBlank())
                .collect(Collectors.toSet());
        Map<String, Long> map = new HashMap<>();
        for (String name : names) {
            deptInfoRepository.findByTenantIdAndDeptName(tenantId, name)
                    .ifPresent(dept -> map.put(name, dept.getDeptId()));
        }
        return map;
    }

    private Map<String, Long> loadContractMap(String tenantId, List<DeviceImportRow> rows) {
        Set<String> names = rows.stream()
                .map(DeviceImportRow::getContractName)
                .filter(n -> n != null && !n.isBlank())
                .collect(Collectors.toSet());
        Map<String, Long> map = new HashMap<>();
        for (String name : names) {
            contractRepository.findByTenantIdAndContractName(tenantId, name)
                    .ifPresent(contract -> map.put(name, contract.getId()));
        }
        return map;
    }

    private Map<String, Long> loadCircuitMap(String tenantId, List<DeviceImportRow> rows) {
        Set<String> numbers = rows.stream()
                .map(DeviceImportRow::getCircuitNumber)
                .filter(n -> n != null && !n.isBlank())
                .collect(Collectors.toSet());
        Map<String, Long> map = new HashMap<>();
        for (String number : numbers) {
            circuitRepository.findByTenantIdAndCircuitNumber(tenantId, number)
                    .ifPresent(circuit -> map.put(number, circuit.getId()));
        }
        return map;
    }

    private Map<String, Long> loadParentDeviceMap(String tenantId, List<DeviceImportRow> rows) {
        Set<String> codes = rows.stream()
                .map(DeviceImportRow::getParentDeviceCode)
                .filter(c -> c != null && !c.isBlank())
                .collect(Collectors.toSet());
        Map<String, Long> map = new HashMap<>();
        for (String code : codes) {
            deviceRepository.findByTenantIdAndDeviceCode(tenantId, code)
                    .ifPresent(device -> map.put(code, device.getId()));
        }
        return map;
    }

    private static BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return null; // validate 階段不攔截此類錯誤，由上層處理
        }
    }

    private static ImportError error(int rowNum, String field, String value, String message) {
        return ImportError.builder()
                .row(rowNum)
                .field(field)
                .value(value == null ? "" : value)
                .message(message)
                .build();
    }
}
```

- [ ] **Step 4: 編譯確認**

```bash
cd backend && ./mvn compile -q
```
預期：BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/taipei/iot/import_/device/DeviceImportRow.java \
       backend/src/main/java/com/taipei/iot/import_/device/DeviceImportStrategy.java \
       backend/src/main/java/com/taipei/iot/device/repository/ContractRepository.java
git commit -m "feat(import): add DeviceImportStrategy with full validation"
```

---

## Task 5: DeviceImportController（匯入 + 範本 + 錯誤報告）

**Files:**
- Create: `backend/src/main/java/com/taipei/iot/import_/device/DeviceImportController.java`

**Interfaces:**
- Consumes: `ImportOrchestrator`, `DeviceImportStrategy`, `ImportProperties`
- Produces: REST endpoints

---

- [ ] **Step 1: 建立 Controller**

```java
// backend/src/main/java/com/taipei/iot/import_/device/DeviceImportController.java
package com.taipei.iot.import_.device;

import com.taipei.iot.common.response.BaseResponse;
import com.taipei.iot.import_.ImportError;
import com.taipei.iot.import_.ImportOrchestrator;
import com.taipei.iot.import_.ImportResponse;
import com.taipei.iot.import_.ImportResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/auth/devices/import")
@RequiredArgsConstructor
@Tag(name = "Device Import", description = "設備批次匯入（Excel/CSV）")
public class DeviceImportController {

    private static final List<String> TEMPLATE_HEADERS = List.of(
            "device_type", "device_code", "device_name", "twd97_x", "twd97_y",
            "lng", "lat", "elevation", "dept_name", "contract_name",
            "property_owner", "installed_at", "parent_device_code",
            "mount_position", "connectivity_type", "circuit_number");

    private static final List<String> ERROR_REPORT_HEADERS = List.of(
            "列", "欄位", "原始值", "錯誤說明");

    private final ImportOrchestrator importOrchestrator;
    private final DeviceImportStrategy deviceImportStrategy;

    @PostMapping
    @PreAuthorize("hasAuthority('DEVICE_MANAGE')")
    @Operation(summary = "匯入設備", description = "上傳 Excel (.xlsx) 或 CSV 檔案，先驗證全部再整批寫入")
    public BaseResponse<ImportResponse> importDevices(@RequestParam("file") MultipartFile file) {
        ImportResult<DeviceImportRow> result = importOrchestrator.parseAndValidate(file, deviceImportStrategy);

        if (result.hasErrors()) {
            return BaseResponse.failure(
                    com.taipei.iot.common.enums.ErrorCode.DEVICE_IMPORT_VALIDATION_FAILED,
                    ImportResponse.builder()
                            .entityType("device")
                            .totalRows(result.getValidRows().size() + result.getErrors().size())
                            .successCount(0)
                            .errors(result.getErrors())
                            .build());
        }

        ImportResponse response = importOrchestrator.execute(result, deviceImportStrategy);
        return BaseResponse.success(response);
    }

    @GetMapping("/template")
    @PreAuthorize("hasAuthority('DEVICE_MANAGE')")
    @Operation(summary = "下載匯入範本", description = "回傳含標題列的 .xlsx 或 .csv 範本")
    public ResponseEntity<byte[]> downloadTemplate(
            @RequestParam(defaultValue = "xlsx") String format) throws IOException {

        if ("csv".equalsIgnoreCase(format)) {
            return downloadCsvTemplate();
        }
        return downloadXlsxTemplate();
    }

    @PostMapping("/error-report")
    @PreAuthorize("hasAuthority('DEVICE_MANAGE')")
    @Operation(summary = "下載錯誤報告", description = "將驗證錯誤包裝為 CSV 提供下載")
    public ResponseEntity<byte[]> downloadErrorReport(@RequestBody ErrorReportRequest request) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8))) {
            // BOM for Excel compatibility
            baos.write(0xEF);
            baos.write(0xBB);
            baos.write(0xBF);

            writer.println(String.join(",", ERROR_REPORT_HEADERS));
            for (ImportError err : request.errors()) {
                writer.println(csvEscape(err.getRow()) + ","
                        + csvEscape(err.getField()) + ","
                        + csvEscape(err.getValue()) + ","
                        + csvEscape(err.getMessage()));
            }
            writer.flush();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"import-error-report.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(baos.toByteArray());
    }

    private ResponseEntity<byte[]> downloadXlsxTemplate() throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("設備匯入範本");
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < TEMPLATE_HEADERS.size(); i++) {
                headerRow.createCell(i).setCellValue(TEMPLATE_HEADERS.get(i));
            }
            // 第二列填入範例資料（選擇性）
            Row exampleRow = sheet.createRow(1);
            exampleRow.createCell(0).setCellValue("STREET_LIGHT");
            exampleRow.createCell(1).setCellValue("SL-TEMPLATE-001");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"device-import-template.xlsx\"")
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(baos.toByteArray());
        }
    }

    private ResponseEntity<byte[]> downloadCsvTemplate() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8))) {
            baos.write(0xEF);
            baos.write(0xBB);
            baos.write(0xBF);

            writer.println(String.join(",", TEMPLATE_HEADERS));
            writer.println("STREET_LIGHT,SL-TEMPLATE-001");
            writer.flush();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"device-import-template.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(baos.toByteArray());
    }

    private static String csvEscape(Object value) {
        if (value == null) return "";
        String s = value.toString();
        // CSV injection prevention: prefix =/+/-/@ with tab
        if (!s.isEmpty() && (s.charAt(0) == '=' || s.charAt(0) == '+' || s.charAt(0) == '-' || s.charAt(0) == '@')) {
            s = "\t" + s;
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    /**
     * Request DTO for error report generation.
     */
    public record ErrorReportRequest(
            String originalFileName,
            List<String> headers,
            List<List<String>> rows,
            List<ImportError> errors
    ) {}
}
```

- [ ] **Step 2: 編譯確認**

```bash
cd backend && ./mvn compile -q
```
預期：BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/taipei/iot/import_/device/DeviceImportController.java
git commit -m "feat(import): add DeviceImportController (import + template + error-report)"
```

---

## Task 6: 後端測試

**Files:**
- Create: `backend/src/test/java/com/taipei/iot/import_/parser/ExcelParserTest.java`
- Create: `backend/src/test/java/com/taipei/iot/import_/parser/CsvParserTest.java`
- Create: `backend/src/test/java/com/taipei/iot/import_/device/DeviceImportStrategyTest.java`
- Create: `backend/src/test/java/com/taipei/iot/import_/device/DeviceImportControllerTest.java`

**Interfaces:**
- Consumes: `ExcelParser`, `CsvParser`, `DeviceImportStrategy`, `DeviceImportController`, `ImportOrchestrator`

---

- [ ] **Step 1: ExcelParserTest**

```java
// backend/src/test/java/com/taipei/iot/import_/parser/ExcelParserTest.java
package com.taipei.iot.import_.parser;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExcelParserTest {

    private final ExcelParser parser = new ExcelParser();

    @Test
    void parse_shouldReturnRows() throws IOException {
        byte[] content = createExcel(
                new String[]{"device_code", "device_name", "device_type"},
                new String[]{"SL-001", "燈具1", "STREET_LIGHT"},
                new String[]{"SL-002", "燈具2", "STREET_LIGHT"}
        );

        List<Map<String, String>> rows = parser.parse(new ByteArrayInputStream(content));
        assertEquals(2, rows.size());
        assertEquals("SL-001", rows.get(0).get("device_code"));
        assertEquals("燈具1", rows.get(0).get("device_name"));
    }

    @Test
    void parse_emptySheet_shouldReturnEmptyList() throws IOException {
        byte[] content = createExcel(new String[]{"device_code"}, new String[]{});
        List<Map<String, String>> rows = parser.parse(new ByteArrayInputStream(content));
        assertTrue(rows.isEmpty());
    }

    @Test
    void supportedExtension_shouldReturnXlsx() {
        assertEquals("xlsx", parser.supportedExtension());
    }

    private byte[] createExcel(String[] headers, String[]... data) throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Sheet1");
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }
            for (int r = 0; r < data.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < data[r].length; c++) {
                    row.createCell(c).setCellValue(data[r][c]);
                }
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);
            return baos.toByteArray();
        }
    }
}
```

- [ ] **Step 2: CsvParserTest**

```java
// backend/src/test/java/com/taipei/iot/import_/parser/CsvParserTest.java
package com.taipei.iot.import_.parser;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CsvParserTest {

    private final CsvParser parser = new CsvParser();

    @Test
    void parse_shouldReturnRows() {
        String csv = "device_code,device_name,device_type\nSL-001,燈具1,STREET_LIGHT\nSL-002,燈具2,STREET_LIGHT";
        List<Map<String, String>> rows = parser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
        assertEquals(2, rows.size());
        assertEquals("SL-001", rows.get(0).get("device_code"));
        assertEquals("STREET_LIGHT", rows.get(0).get("device_type"));
    }

    @Test
    void parse_emptyContent_shouldReturnEmptyList() {
        String csv = "device_code\n";
        List<Map<String, String>> rows = parser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
        assertTrue(rows.isEmpty());
    }

    @Test
    void supportedExtension_shouldReturnCsv() {
        assertEquals("csv", parser.supportedExtension());
    }
}
```

- [ ] **Step 3: DeviceImportStrategyTest**

```java
// backend/src/test/java/com/taipei/iot/import_/device/DeviceImportStrategyTest.java
package com.taipei.iot.import_.device;

import com.taipei.iot.common.context.TenantContext;
import com.taipei.iot.dept.repository.DeptInfoRepository;
import com.taipei.iot.device.repository.CircuitRepository;
import com.taipei.iot.device.repository.ContractRepository;
import com.taipei.iot.device.repository.DeviceRepository;
import com.taipei.iot.import_.ImportError;
import com.taipei.iot.schema.service.DeviceTemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceImportStrategyTest {

    @Mock private DeviceRepository deviceRepository;
    @Mock private DeviceTemplateService deviceTemplateService;
    @Mock private DeptInfoRepository deptInfoRepository;
    @Mock private ContractRepository contractRepository;
    @Mock private CircuitRepository circuitRepository;

    private DeviceImportStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new DeviceImportStrategy(
                deviceRepository, deviceTemplateService,
                deptInfoRepository, contractRepository, circuitRepository);
    }

    @Test
    void mapToDto_shouldMapAllFields() {
        Map<String, String> row = Map.of(
                "device_type", "STREET_LIGHT",
                "device_code", "SL-001",
                "device_name", "測試燈具",
                "lng", "121.5",
                "lat", "25.0"
        );

        DeviceImportRow dto = strategy.mapToDto(row);
        assertEquals("STREET_LIGHT", dto.getDeviceType());
        assertEquals("SL-001", dto.getDeviceCode());
        assertEquals("測試燈具", dto.getDeviceName());
        assertEquals(0, dto.getTwd97X().intValue()); // null -> null
    }

    @Test
    void validate_missingRequiredFields_shouldReturnErrors() {
        Map<String, String> row = Map.of(
                "device_type", "",
                "device_code", "",
                "device_name", ""
        );
        DeviceImportRow dto = strategy.mapToDto(row);

        List<ImportError> errors = strategy.validate(dto, 2);
        assertFalse(errors.isEmpty());

        // device_code 必填
        assertTrue(errors.stream().anyMatch(e ->
                e.getField().equals("device_code") && e.getMessage().contains("必填")));
        // device_type 必填
        assertTrue(errors.stream().anyMatch(e ->
                e.getField().equals("device_type") && e.getMessage().contains("必填")));
    }

    @Test
    void validate_deviceTypeNotExists_shouldReturnError() {
        doThrow(new com.taipei.iot.common.exception.BusinessException(
                com.taipei.iot.common.enums.ErrorCode.DEVICE_TYPE_NOT_DEFINED))
                .when(deviceTemplateService).validateDeviceTypeExists("UNKNOWN"));

        Map<String, String> row = Map.of(
                "device_type", "UNKNOWN",
                "device_code", "SL-001"
        );
        DeviceImportRow dto = strategy.mapToDto(row);

        List<ImportError> errors = strategy.validate(dto, 2);
        assertTrue(errors.stream().anyMatch(e ->
                e.getField().equals("device_type") && e.getMessage().contains("不存在")));
    }

    @Test
    void validate_validRow_shouldReturnNoErrors() {
        doNothing().when(deviceTemplateService).validateDeviceTypeExists("STREET_LIGHT");

        Map<String, String> row = Map.of(
                "device_type", "STREET_LIGHT",
                "device_code", "SL-001"
        );
        DeviceImportRow dto = strategy.mapToDto(row);

        List<ImportError> errors = strategy.validate(dto, 2);
        assertTrue(errors.isEmpty());
    }

    @Test
    void expectedHeaders_shouldContainAllRequired() {
        assertTrue(strategy.expectedHeaders().contains("device_type"));
        assertTrue(strategy.expectedHeaders().contains("device_code"));
    }

    @Test
    void getEntityType_shouldReturnDevice() {
        assertEquals("device", strategy.getEntityType());
    }
}
```

- [ ] **Step 4: DeviceImportControllerTest**

```java
// backend/src/test/java/com/taipei/iot/import_/device/DeviceImportControllerTest.java
package com.taipei.iot.import_.device;

import com.taipei.iot.common.response.BaseResponse;
import com.taipei.iot.import_.ImportError;
import com.taipei.iot.import_.ImportOrchestrator;
import com.taipei.iot.import_.ImportResponse;
import com.taipei.iot.import_.ImportResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DeviceImportController.class)
@Import(DeviceImportControllerTest.TestConfig.class)
class DeviceImportControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ImportOrchestrator importOrchestrator;
    @MockitoBean private DeviceImportStrategy deviceImportStrategy;

    @TestConfiguration
    static class TestConfig {
        @Bean
        DeviceImportController controller(
                ImportOrchestrator orchestrator,
                DeviceImportStrategy strategy) {
            return new DeviceImportController(orchestrator, strategy);
        }
    }

    @Test
    void importDevices_success_shouldReturn200() throws Exception {
        when(importOrchestrator.parseAndValidate(any(), any()))
                .thenReturn(ImportResult.success(List.of()));
        when(importOrchestrator.execute(any(), any()))
                .thenReturn(ImportResponse.builder()
                        .entityType("device").totalRows(2).successCount(2).errors(List.of())
                        .build());

        MockMultipartFile file = new MockMultipartFile(
                "file", "devices.xlsx", MediaType.MULTIPART_FORM_DATA_VALUE,
                "fake-content".getBytes());

        mockMvc.perform(multipart("/v1/auth/devices/import").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body.successCount").value(2));
    }

    @Test
    void importDevices_validationFailure_shouldReturn400() throws Exception {
        when(importOrchestrator.parseAndValidate(any(), any()))
                .thenReturn(ImportResult.failure(List.of(
                        ImportError.builder().row(3).field("device_code").value("").message("必填").build())));

        MockMultipartFile file = new MockMultipartFile(
                "file", "devices.xlsx", MediaType.MULTIPART_FORM_DATA_VALUE,
                "fake-content".getBytes());

        mockMvc.perform(multipart("/v1/auth/devices/import").file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void downloadTemplate_shouldReturnXlsx() throws Exception {
        mockMvc.perform(get("/v1/auth/devices/import/template")
                        .param("format", "xlsx"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"device-import-template.xlsx\"" ));
    }

    @Test
    void downloadTemplate_shouldReturnCsv() throws Exception {
        mockMvc.perform(get("/v1/auth/devices/import/template")
                        .param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"device-import-template.csv\""));
    }
}
```

- [ ] **Step 5: 執行測試**

```bash
cd backend && ./mvn test -Dtest="DeviceImportStrategyTest,ExcelParserTest,CsvParserTest,DeviceImportControllerTest"
```
預期：全部 PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/test/java/com/taipei/iot/import_/
git commit -m "test(import): add unit tests for parser, strategy, and controller"
```

---

## Task 7: 前端 Types + API

**Files:**
- Modify: `frontend/src/types/device.ts`
- Modify: `frontend/src/api/device/index.ts`

**Interfaces:**
- Produces: `ImportError`, `ImportResponse` types, `importDevices()`, `downloadImportTemplate()`, `downloadErrorReport()` API functions

---

- [ ] **Step 1: 加入 types**

在 `frontend/src/types/device.ts` 末尾加入：

```typescript
// ── Import ────────────────────────────────────────────────────────────

export interface ImportError {
  row: number
  field: string
  value: string
  message: string
}

export interface ImportResponse {
  entityType: string
  totalRows: number
  successCount: number
  errors: ImportError[]
}
```

- [ ] **Step 2: 加入 API functions**

在 `frontend/src/api/device/index.ts` 末尾加入：

```typescript
// ── Import ────────────────────────────────────────────────────────────

export const importDevices = (file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  return axiosIns.post<unknown, BaseResponse<ImportResponse>>(
    '/auth/devices/import',
    formData,
    { headers: { 'Content-Type': 'multipart/form-data' } }
  )
}

export const downloadImportTemplate = (format: 'xlsx' | 'csv' = 'xlsx') => {
  return axiosIns.get('/auth/devices/import/template', {
    params: { format },
    responseType: 'blob',
  })
}

export const downloadErrorReport = (payload: {
  originalFileName: string
  headers: string[]
  rows: string[][]
  errors: ImportError[]
}) => {
  return axiosIns.post('/auth/devices/import/error-report', payload, {
    responseType: 'blob',
  })
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/types/device.ts frontend/src/api/device/index.ts
git commit -m "feat(import): add frontend types and API for device import"
```

---

## Task 8: 前端 ImportDialog 元件

**Files:**
- Create: `frontend/src/views/admin/device/ImportDialog.vue`
- Modify: `frontend/src/views/admin/device/DeviceListView.vue`
- Modify: `frontend/src/locales/zh-TW.ts`
- Modify: `frontend/src/locales/en.ts`

**Interfaces:**
- Consumes: `importDevices`, `downloadImportTemplate`, `downloadErrorReport` from API
- Produces: ElementPlus Dialog component with 4 states (select file, loading, success, error)

---

- [ ] **Step 1: 建立 ImportDialog.vue**

```vue
<!-- frontend/src/views/admin/device/ImportDialog.vue -->
<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { importDevices, downloadImportTemplate, downloadErrorReport } from '@/api/device'
import type { ImportError, ImportResponse } from '@/types/device'

const { t } = useI18n()

const visible = defineModel<boolean>('visible', { default: false })
const emit = defineEmits<{ imported: [] }>()

// ── States ──
type DialogState = 'select' | 'loading' | 'success' | 'error'
const state = ref<DialogState>('select')
const selectedFile = ref<File | null>(null)
const importResult = ref<ImportResponse | null>(null)

// ── Computed ──
const canImport = computed(() => selectedFile.value !== null)
const fileName = computed(() => selectedFile.value?.name ?? '')
const fileSize = computed(() => {
  if (!selectedFile.value) return ''
  const bytes = selectedFile.value.size
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
})

// ── Methods ──
function handleFileChange(file: File | null) {
  selectedFile.value = file
}

function reset() {
  state.value = 'select'
  selectedFile.value = null
  importResult.value = null
}

function handleClose() {
  visible.value = false
  reset()
}

async function handleImport() {
  if (!selectedFile.value) return
  state.value = 'loading'
  try {
    const res = await importDevices(selectedFile.value)
    if (res.errorCode === '00000' && res.body) {
      importResult.value = res.body
      if (res.body.errors.length > 0) {
        state.value = 'error'
      } else {
        state.value = 'success'
        emit('imported')
      }
    } else {
      // 後端回 400 時 axios interceptor 會拋錯，但若 BaseResponse 有 errorCode 則在此處理
      state.value = 'error'
    }
  } catch (err: any) {
    // 嘗試從 error response 取出 body
    if (err?.response?.data?.body) {
      importResult.value = err.response.data.body
    } else {
      importResult.value = {
        entityType: 'device',
        totalRows: 0,
        successCount: 0,
        errors: [{ row: 0, field: '', value: '', message: err?.message || '匯入失敗' }],
      }
    }
    state.value = 'error'
  }
}

async function handleDownloadTemplate(format: 'xlsx' | 'csv') {
  try {
    const res = await downloadImportTemplate(format)
    const blob = res as unknown as Blob
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `device-import-template.${format}`
    a.click()
    URL.revokeObjectURL(url)
  } catch {
    ElMessage.error(t('import.downloadTemplateFailed'))
  }
}

async function handleDownloadErrorReport() {
  if (!importResult.value) return
  try {
    const blob = await downloadErrorReport({
      originalFileName: fileName.value,
      headers: [],
      rows: [],
      errors: importResult.value.errors,
    }) as unknown as Blob
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'import-error-report.csv'
    a.click()
    URL.revokeObjectURL(url)
  } catch {
    ElMessage.error(t('import.downloadReportFailed'))
  }
}

function handleDone() {
  handleClose()
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="t('import.title')"
    width="600px"
    destroy-on-close
    @close="handleClose"
  >
    <!-- State 1: Select File -->
    <template v-if="state === 'select'">
      <div class="import-dropzone">
        <el-upload
          drag
          :auto-upload="false"
          :show-file-list="false"
          accept=".xlsx,.csv"
          :on-change="(uploadFile) => handleFileChange(uploadFile.raw || null)"
        >
          <div v-if="!selectedFile">
            <el-icon :size="48" color="#909399"><UploadFilled /></el-icon>
            <p>{{ t('import.dropHint') }}</p>
            <p class="import-hint">{{ t('import.formatHint') }}</p>
          </div>
          <div v-else>
            <el-icon :size="48" color="#409eff"><Document /></el-icon>
            <p class="import-filename">{{ fileName }}</p>
            <p class="import-hint">{{ fileSize }}</p>
          </div>
        </el-upload>
      </div>
      <div class="import-actions">
        <el-button text @click="handleDownloadTemplate('xlsx')">
          📥 {{ t('import.downloadTemplate') }}
        </el-button>
      </div>
    </template>

    <!-- State 2: Loading -->
    <template v-else-if="state === 'loading'">
      <div class="import-loading">
        <el-progress :percentage="100" :stroke-width="6" striped striped-flow />
        <p>{{ t('import.validating') }}</p>
      </div>
    </template>

    <!-- State 3: Success -->
    <template v-else-if="state === 'success' && importResult">
      <div class="import-result import-success">
        <el-icon :size="48" color="#67c23a"><CircleCheckFilled /></el-icon>
        <p class="import-result-text">
          {{ t('import.successMessage', { count: importResult.successCount }) }}
        </p>
      </div>
    </template>

    <!-- State 4: Error -->
    <template v-else-if="state === 'error' && importResult">
      <div class="import-result import-error">
        <el-icon :size="48" color="#f56c6c"><WarningFilled /></el-icon>
        <p class="import-result-text">{{ t('import.errorMessage', { count: importResult.errors.length }) }}</p>
        <div class="import-error-table">
          <el-table :data="importResult.errors" max-height="300" size="small">
            <el-table-column prop="row" :label="t('import.colRow')" width="60" />
            <el-table-column prop="field" :label="t('import.colField')" width="130" />
            <el-table-column prop="value" :label="t('import.colValue')" width="150">
              <template #default="{ row }">
                <el-tag v-if="!row.value" type="info" size="small">(empty)</el-tag>
                <span v-else>{{ row.value }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="message" :label="t('import.colMessage')" min-width="180" />
          </el-table>
        </div>
      </div>
    </template>

    <template #footer>
      <template v-if="state === 'select'">
        <el-button @click="handleClose">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :disabled="!canImport" @click="handleImport">
          {{ t('import.startBtn') }}
        </el-button>
      </template>
      <template v-else-if="state === 'loading'">
        <el-button disabled>{{ t('import.importing') }}</el-button>
      </template>
      <template v-else-if="state === 'success'">
        <el-button type="primary" @click="handleDone">{{ t('common.confirm') }}</el-button>
      </template>
      <template v-else-if="state === 'error'">
        <el-button @click="handleDownloadErrorReport">
          📥 {{ t('import.downloadReport') }}
        </el-button>
        <el-button type="primary" @click="handleClose">{{ t('common.close') }}</el-button>
      </template>
    </template>
  </el-dialog>
</template>

<style scoped>
.import-dropzone {
  text-align: center;
  padding: 16px 0;
}
.import-hint {
  font-size: 12px;
  color: #909399;
  margin-top: 8px;
}
.import-filename {
  font-weight: 600;
  margin-top: 8px;
}
.import-actions {
  text-align: center;
  margin-top: 12px;
}
.import-loading {
  text-align: center;
  padding: 40px 0;
}
.import-loading p {
  margin-top: 16px;
  color: #909399;
}
.import-result {
  text-align: center;
  padding: 24px 0;
}
.import-result-text {
  margin-top: 12px;
  font-size: 16px;
}
.import-error-table {
  margin-top: 16px;
  text-align: left;
}
</style>
```

- [ ] **Step 2: 修改 DeviceListView.vue，加入匯入按鈕**

在 `<script setup>` 區塊開頭引入 ImportDialog：

```typescript
// 在 DeviceListView.vue <script setup> 加入
import ImportDialog from './ImportDialog.vue'
const importDialogVisible = ref(false)

function handleImported() {
  ElMessage.success('設備匯入成功')
  fetchList()
}
```

在 `<template>` 的 header-actions div 內，新增匯入按鈕：

```vue
<!-- 在 header-actions 區塊，原本的新增按鈕旁 -->
<el-button @click="importDialogVisible = true">
  {{ t('import.importBtn') }}
</el-button>
```

在 template 末尾（detail-drawer 之後），加入 ImportDialog：

```vue
<!-- 在 DeviceListView.vue template 末尾 -->
<ImportDialog v-model:visible="importDialogVisible" @imported="handleImported" />
```

- [ ] **Step 3: i18n 語系**

在 `frontend/src/locales/zh-TW.ts` 的 `device:` 區塊後（或在檔案中合適處）加入：

```typescript
import: {
  importBtn: '匯入',
  title: '匯入設備',
  dropHint: '拖曳檔案至此處，或點擊選擇檔案',
  formatHint: '支援 .xlsx, .csv 格式，單次最多 500 筆',
  downloadTemplate: '下載匯入範本',
  startBtn: '開始匯入',
  importing: '匯入中...',
  validating: '正在驗證資料，請稍候...',
  successMessage: '成功匯入 {count} 筆設備',
  errorMessage: '資料驗證未通過，共 {count} 筆錯誤，請修正後重新上傳',
  downloadReport: '下載錯誤報告',
  colRow: '列',
  colField: '欄位',
  colValue: '原始值',
  colMessage: '錯誤說明',
  downloadTemplateFailed: '下載範本失敗',
  downloadReportFailed: '下載錯誤報告失敗',
},
```

在 `frontend/src/locales/en.ts` 加入對應英文：

```typescript
import: {
  importBtn: 'Import',
  title: 'Import Devices',
  dropHint: 'Drag & drop file here, or click to select',
  formatHint: 'Supports .xlsx, .csv, max 500 rows per import',
  downloadTemplate: 'Download Template',
  startBtn: 'Start Import',
  importing: 'Importing...',
  validating: 'Validating data, please wait...',
  successMessage: 'Successfully imported {count} devices',
  errorMessage: 'Validation failed, {count} errors found. Please fix and re-upload.',
  downloadReport: 'Download Error Report',
  colRow: 'Row',
  colField: 'Field',
  colValue: 'Original Value',
  colMessage: 'Error Message',
  downloadTemplateFailed: 'Failed to download template',
  downloadReportFailed: 'Failed to download error report',
},
```

- [ ] **Step 4: 型別檢查**

```bash
cd frontend && npm run type-check
```
預期：No type errors

- [ ] **Step 5: Commit**

```bash
git add frontend/src/views/admin/device/ImportDialog.vue \
       frontend/src/views/admin/device/DeviceListView.vue \
       frontend/src/locales/zh-TW.ts \
       frontend/src/locales/en.ts
git commit -m "feat(import): add ImportDialog component with i18n support"
```

---

## 自檢清單

| 需求 | 涵蓋的 Task |
|---|---|
| R1: Excel/CSV 支援 | Task 2 (ExcelParser, CsvParser) |
| R2: 同步處理 | Task 3 (ImportOrchestrator) |
| R3: 可設定筆數上限 | Task 1 (ImportProperties), Task 3 (檢查) |
| R4: 先驗證全部 → 一次回報 | Task 3 (parseAndValidate), Task 5 (Controller) |
| R5: 欄位對應查詢 | Task 4 (DeviceImportStrategy: batchValidate + loadMap) |
| R6: device_type 驗證 | Task 4 (validate → deviceTemplateService) |
| R7: 租戶繼承 | Task 4 (TenantContext.getCurrentTenantId) |
| R8: 不包含 attributes | Task 4 (DeviceImportRow 無 attributes 欄位) |
| R9: 下載範本 | Task 5 (GET /template) |
| R10: 下載錯誤報告 | Task 5 (POST /error-report) |
| R11: 泛型架構 | Task 3 (ImportStrategy + ImportOrchestrator) |

---

## 執行選擇

計畫已儲存於 `00-history/09-spece-discuss/device/03-device-import/h2-device-import-plan.md`。

有兩種執行方式：

**1. Subagent-Driven（推薦）** — 一個 task 一個子 agent，每次 task 完成後 review，快速迭代

**2. Inline Execution** — 在此 session 中依序執行，批次 checkpoint review

你偏好哪一種？
