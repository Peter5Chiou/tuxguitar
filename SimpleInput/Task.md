# SimpleInput → TuxGuitar Plugin 開發任務清單

> 本檔是工作交接用筆記。工作區請開在 `G:\App\tuxguitar`（repo 根目錄）。
> 本檔位置：`G:\App\tuxguitar\SimpleInput\Task.md`
> 狀態：研究階段完成，尚未寫任何程式碼。下次 session 從「§5 待辦清單」開始。

---

## 0. 專案目標

使用者用簡單文字格式（`.strum`）寫吉他刷法譜，做成 **TuxGuitar 的匯入 plugin**：
使用者從 TuxGuitar 選單執行匯入 → plugin 讀 `.strum` → 解析/驗證 → 轉成 `TGSong` →
**直接載入 TuxGuitar 主視窗**（和弦圖顯示、播放、編輯全部由 TuxGuitar 現成機制處理，不用自己寫 UI/播放器）。

### 原始範例檔（規格基準）

```
% 標頭
M:4/4
L:1/8
Q:1/4=90
K:C

% 定義區（可選）
@basic  = d2u2d2u2
@island = d2 u d u d2 u
@arp    = t (23) 1 (23) 1 (23) 1

% 譜面內容
G(2) @basic | C(2) @island G(2) @basic | Am(4) @arp | F(2) @basic C(2) @basic ||
```

---

## 1. 使用者環境（已確認）

| 項目 | 值 |
|---|---|
| OS | Windows 11 x64 |
| JDK | 24.0.1，`C:\Program Files\Java\jdk-24` |
| Maven | 3.9.16，`G:\App\apache-maven-3.9.16` |
| TuxGuitar 版本 | 9.99-SNAPSHOT（Maven parent：`app.tuxguitar:tuxguitar-pom`，位於 `desktop/pom.xml`） |

**建置指令（使用者提供）：**
```
cd G:\App\tuxguitar\desktop\build-scripts\tuxguitar-windows-swt-x86_64
mvn package -DskipTests
```

---

## 2. 已完成的研究（重要發現，皆已讀原始碼確認）

### 2.1 repo 結構

- `common/`：TuxGuitar-lib（核心模型）、TuxGuitar-gtp（GP1~GP5 讀寫）、TuxGuitar-ptb（PowerTab 匯入，**最佳 plugin 模板**）、TuxGuitar-midi、TuxGuitar-gpx 等
- `desktop/`：主程式 `TuxGuitar/` 與各 UI/系統 plugin；parent pom 在 `desktop/pom.xml`
- `desktop/build-scripts/tuxguitar-windows-swt-x86_64/`：Windows 發行版組裝模組（使用者的建置入口）

### 2.2 匯入機制（核心，已確認）

**介面定義**（`common/TuxGuitar-lib/src/main/java/app/tuxguitar/io/base/`，注意 lib 的 source 目錄是 `src/main/java`）：

- `TGSongImporter.java`：
  ```java
  public interface TGSongImporter extends TGSongStreamProvider {
      String getImportName();
  }
  ```
- `TGSongStreamProvider.java`：`String getProviderId()` + `TGSongStream openStream(TGSongStreamContext context)`
- `TGSongStream.java`：`void process() throws TGFileFormatException;`（在 process 內產生 TGSong 並放進 context）
- `TGSongReader.java`：`void read(TGSongReaderHandle handle)`（另一條路，GTP 系用這個）
- 同目錄還有：`TGSongReaderHandle/Helper`、`TGFileFormatManager`（註冊中心，細節待看）、`TGFileFormat(Detector)`

**載入流程**（`desktop/TuxGuitar/src/app/tuxguitar/app/action/impl/file/TGImportSongAction.java`，已讀完整檔）：

```java
public class TGImportSongAction extends TGSongStreamActionBase {
    public static final String NAME = "action.song.import";
    protected void processAction(TGActionContext context){
        TGSongStreamProvider streamProvider = context.getAttribute(ATTRIBUTE_PROVIDER);
        TGSongStreamContext streamContext = this.findSongStreamContext(context);
        TGSongStream stream = streamProvider.openStream(streamContext);
        stream.process();
        context.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_SONG,
            streamContext.getAttribute(TGDocumentContextAttributes.ATTRIBUTE_SONG));
        TGActionManager.getInstance(getContext()).execute(TGLoadSongAction.NAME, context);
    }
}
```

→ 結論：**plugin 只需提供 `TGSongStreamProvider`，把組好的 `TGSong` 放進 `TGSongStreamContext`（key = `TGDocumentContextAttributes.ATTRIBUTE_SONG`），之後 `TGLoadSongAction` 會自動把歌載進主視窗**（顯示+播放全免費）。

- `TGSongStreamActionBase.java`（同目錄）：提供常數 `ATTRIBUTE_PROVIDER`、`ATTRIBUTE_CONTEXT`，以及 `findSongStreamContext()`
- `desktop/.../action/installer/TGActionConfigMap.java` 有 import `TGImportSongAction`（action 註冊處，選單如何列出 importer 待確認）

### 2.3 GP5 參考實作（`common/TuxGuitar-gtp/src/app/tuxguitar/io/gtp/`）

- `GP5OutputStream.java`：version 字串 `"FICHIER GUITAR PRO v5.00"`、`getFileFormat()` 回傳 `new TGFileFormat("Guitar Pro 5", "application/x-gtp", new String[]{"gp5"})`
- 可學到的模型細節：song/track/measure/beat/note/channel 全在 `app.tuxguitar.song.models.*`；另有 `TGChord`、`TGStroke`、`TGPickStroke`、`TGVelocities`（**刷弦方向或許可用 PickStroke/Stroke 表達，待研究**）
- plugin 註冊方式參考：`GTPPlugin.java`、`GP5InputStreamPlugin.java`（`createInputStream(TGContext)`、`createFileFormatDetector(TGContext)`，throw `TGPluginException`）

### 2.4 模組 pom 模板（`common/TuxGuitar-gtp/pom.xml`，已讀完整檔）

```xml
<parent>
    <artifactId>tuxguitar-pom</artifactId>
    <groupId>app.tuxguitar</groupId>
    <version>9.99-SNAPSHOT</version>
    <relativePath>../../desktop/</relativePath>
</parent>
<artifactId>tuxguitar-gtp</artifactId>
<packaging>jar</packaging>
<build>
    <sourceDirectory>src</sourceDirectory>   <!-- gtp 用 src/；lib 用預設 src/main/java -->
    <resources><resource><directory>share</directory></resource></resources>
</build>
<dependencies>
    <dependency><groupId>${project.groupId}</groupId><artifactId>tuxguitar-lib</artifactId><scope>provided</scope></dependency>
    <dependency><groupId>${project.groupId}</groupId><artifactId>tuxguitar-gm-utils</artifactId><scope>provided</scope></dependency>
</dependencies>
```

### 2.5 之前搜尋工具異常的原因

原 workspace 只開了 `G:\App\tuxguitar\SimpleInput`，grep/file_search 只搜得到 workspace 內的檔案，
所以對 repo 其他目錄的搜尋全部回空（`list_dir` 用絕對路徑仍可用）。
**重開 workspace 到 `G:\App\tuxguitar` 後應恢復正常**，重啟後先驗證：grep `TGImportSongAction` 應有多筆結果。

### 2.6 重大發現：改走 TGSongReader 路線（比 TGSongImporter 更簡單）

- 全 repo **沒有任何 TGSongImporter 實作**（只有介面與 addImporter，無人呼叫）。
- `FileMenuItem.addImporters()`（desktop/TuxGuitar/.../menu/impl/FileMenuItem.java:208）把 **readers 和 importers 一起列進 File→Import 子選單**（readers 在前）。
- reader 路線附贈檔案選擇對話框（`createOpenFileActionProcessor(fileFormat)`），不用自己處理 stream context。
- **決策：照 PTB 模式實作 `TGSongReader` + `TGSongReaderPlugin`**（PTInputStreamPlugin extends TGSongReaderPlugin；PTInputStream implements TGSongReader，read(handle) 內 `handle.setSong(...)`）。
- Plugin 註冊 = ServiceLoader：
  - `share/META-INF/services/app.tuxguitar.util.plugin.TGPlugin` 內容一行：plugin 類別全名
  - `share/META-INF/tuxguitar-simpleinput.info`：plugin.name/description/author/version
- 匯出器範例（ImageExporterPlugin/ImageExporter）可參考命名慣例，但我們用 reader 路線。

### 2.7 模組組裝（windows-swt-x86_64）

- `desktop/build-scripts/tuxguitar-windows-swt-x86_64/pom.xml`：
  - `<modules>` 加 `<module>../../../common/TuxGuitar-simpleinput</module>`（約 47 行附近）
  - `<artifactItems>` PLUGINS 區加 tuxguitar-simpleinput → `share/plugins/tuxguitar-simpleinput.jar`（約 258 行 ptb 附近）
- `desktop/pom.xml` `<dependencyManagement>` 加 tuxguitar-simpleinput（ptb 條目附近，約 201 行）。
- 模組 pom 仿 `common/TuxGuitar-ptb/pom.xml`（parent tuxguitar-pom 9.99-SNAPSHOT、relativePath ../../desktop/、sourceDirectory src、deps: lib+gm-utils provided）。

---

## 3. 規格重點（使用者原始需求，已整理）

### 3.1 標頭
- `M:` 拍號（必要，如 4/4、3/4、6/8）
- `L:` 基本時值單位（必要，如 1/8、1/16）
- `Q:` 速度（可選，如 `Q:1/4=90`）
- `K:` 調性（可選）
- `%` 開頭為註解

### 3.2 定義區與譜面
- `@名稱 = 內容` 定義可重用刷法；譜面中寫 `@名稱` 引用（需遞迴展開？—待確認是否允許巢狀）
- `|` 小節線；一小節可多和弦（`C2 G2`）
- 第一小節可弱起（不滿拍），最後小節補齊；**弱起+末小節合計必須等於完整拍數**（使用者已確認要檢查）
- 中間小節總時值必須剛好等於 `M:` 拍數，否則報錯（錯誤訊息要指出**第幾小節、什麼問題**）

### 3.3 事件符號
| 符號 | 意思 |
|---|---|
| `d` | 下刷（預設刷該和弦所有發音弦） |
| `u` | 上刷 |
| `t` | 彈根音 |
| `1`~`6` | 彈指定弦（見 3.5 映射） |
| `(23)`、`(123)` | 同時彈多根弦 |
| `-` 或 `z` | 休止符 |
| `x` | 悶音（可選） |

### 3.4 時值
- 數字接在符號後＝時值倍數：`d2`＝×2；`d/2`＝÷2（ABC 風格）
- 指定弦用冒號：`d:123`＝下刷 1、2、3 弦基本時值；`d2:123`＝×2；`u2:32`＝上刷 3、2 弦 ×2

### 3.5 弦編號映射（使用者習慣與標準相反，**必須照做**）

| 使用者寫 | 實際弦 | 備註 |
|---|---|---|
| 3 | 1 | 最細弦（高音 e） |
| 2 | 2 | |
| 1 | 3 | |
| 4 | 4 | |
| 5 | 5 | |
| 6 | 6 | 最粗弦 |

→ 只有 1 和 3 對調，其餘相同。映射：`user 1→3, 2→2, 3→1, 4→4, 5→5, 6→6`。

### 3.6 和弦
- 支援常見和弦（C、G、Am、F、D、Em… 與簡單變體）
- 需內建指法表（常用開放和弦＋簡單封閉和弦），含**根音在哪根弦**（給 `t` 用）
- `d`/`u` 未指定弦時＝刷該和弦所有發音弦

---

## 4. 規格疑點（**下次 session 要先問使用者**）

1. **小節時值計算矛盾（重要）**：範例 `G2 @basic`，若照字面相加＝G2(2 單位)＋d2u2d2u2(8 單位)＝10 單位 > 4/4 的 8 單位，會過拍。`Am4 @arp` 同樣 4＋7＝11 > 8。可能語意：
   - (a) 和弦記號只設定「目前和弦」，時值全由後面的 pattern 提供（`G2` 的 2 不計時？）
   - (b) 和弦事件與 pattern 第一個事件合併（G2 取代 @basic 的第一個 d2 → 2+6=8 ✓，但 Am4 @arp 仍對不上）
   - (c) 範例本身有誤
   - (d) 和弦時值＝和弦持續長度，pattern 事件平分這段時間（@basic 4 個事件 ÷ G2... 對不上）
   → **必須請使用者澄清正確語意**（附上算術）。
2. `x` 悶音第一版要不要做？
3. `d:123` 指定弦＝只刷這幾根，還是「根音＋這幾根」？
4. 刷弦的 d/u 要不要在時間上微錯開（模擬 strum），第一版可先同時發音＋用 `TGPickStroke`/`TGStroke` 標方向（模型存在，用法待查）。
5. `@` 定義是否允許巢狀引用（@ 內再引用 @）？

---

## 5. 待辦清單（依序執行）

- [x] **0.** 搜尋驗證 OK；§4 疑點已全部澄清（見 §8）
- [x] **1.** 規格文件 `SimpleInput/docs/SPEC.md` 已建立
- [x] **2.** PTB 結構研究完成（見 §2.6 新發現）
- [x] **3.** pom modules / 組裝方式確認（見 §2.7）
- [x] **4.** 建立 Maven 模組 `common/TuxGuitar-simpleinput/`（pom 仿 §2.4；source 目錄建議跟 lib 一樣用 `src/main/java`）
- [x] **5.** Parser：Lexer → Parser → Validator，模組化；友善錯誤（第幾行/第幾小節/問題描述）
- [x] **6.** 和弦字典（開放和弦+簡單封閉、根音弦、發音弦）＋ §3.5 弦編號映射
- [x] **7.** 轉換器：事件 → `TGFactory` 建 `TGSong/TGTrack/TGMeasure/TGBeat/TGNote/TGString/TGDuration/TGTempo/TGTimeSignature`；和弦圖顯示＝在 beat 掛 `TGChord`（用法對照 GP5 讀寫碼）；`z`→休止 beat；`t`→根音單音
- [x] **8.** Plugin 進入點：實作 `TGSongReader` + `TGSongReaderPlugin`（改走 reader 路線，見 §2.6）
- [x] **9.** 測試範例 `.strum` 檔數個（`SimpleInput/examples/`）+ parser 單元測試（`SimpleInput/tools/ParserTest.java`，全數通過）
- [x] **10.** 建置（§1 指令）BUILD SUCCESS；jar 已組裝至 `share/plugins/tuxguitar-simpleinput.jar`。**剩：實機驗證**（啟動 TuxGuitar → File→Import→SimpleInput Strum → 開 demo1.strum → 檢查和弦圖/播放）
- [ ] **11.**（可選）`x` 悶音、strum 微延遲、PickStroke 標示

---

## 8. 規格疑點解答（使用者 2026-09-08 確認）

1. **小節時值語意**（2026-09-08 二次澄清定案）：和弦數字＝**拍數**（以 M: 分母為一拍）；事件數字＝**基礎單位數**（L: 決定 1 單位時值，4/4+L:1/8 時 1 單位=半拍）。pattern 總基礎單位數**必須剛好等於**和弦拍數換算的單位數，不合即報錯（不自動縮放）。例：4/4、L:1/8 → `G4 d2u2d2u2` ✓（8單位）、`G4 dudu` ✗（4單位）。和弦無數字→pattern 總長決定；和弦無 pattern→一次 d 刷 N 拍。
2. `x` 悶音：第一版不做（保留字，出現報「未支援」）。
3. `d:123`：只刷指定的弦（非根音+指定）。
4. 刷弦時間：同時發音，方向交給 TuxGuitar（TGStroke 標示）。
5. `@` 巢狀：不允許。

---

## 9. 關鍵檔案索引（下次直接讀）

```
G:\App\tuxguitar\common\TuxGuitar-lib\src\main\java\app\tuxguitar\io\base\TGSongImporter.java
G:\App\tuxguitar\common\TuxGuitar-lib\src\main\java\app\tuxguitar\io\base\TGSongStreamProvider.java
G:\App\tuxguitar\common\TuxGuitar-lib\src\main\java\app\tuxguitar\io\base\TGSongStream.java
G:\App\tuxguitar\common\TuxGuitar-lib\src\main\java\app\tuxguitar\io\base\TGSongReader.java
G:\App\tuxguitar\common\TuxGuitar-lib\src\main\java\app\tuxguitar\io\base\TGFileFormatManager.java
G:\App\tuxguitar\desktop\TuxGuitar\src\app\tuxguitar\app\action\impl\file\TGImportSongAction.java
G:\App\tuxguitar\desktop\TuxGuitar\src\app\tuxguitar\app\action\impl\file\TGSongStreamActionBase.java
G:\App\tuxguitar\desktop\TuxGuitar\src\app\tuxguitar\app\action\installer\TGActionConfigMap.java
G:\App\tuxguitar\common\TuxGuitar-gtp\src\app\tuxguitar\io\gtp\  (GP5InputStream/OutputStream、GTPPlugin 等)
G:\App\tuxguitar\common\TuxGuitar-gtp\pom.xml                    (模組 pom 模板)
G:\App\tuxguitar\common\TuxGuitar-ptb\                           (匯入 plugin 最佳模板，尚未細看)
G:\App\tuxguitar\desktop\pom.xml                                 (parent pom / modules，尚未細看)
G:\App\tuxguitar\desktop\build-scripts\tuxguitar-windows-swt-x86_64\  (建置組裝，尚未細看)
G:\App\tuxguitar\common\TuxGuitar-lib\src\main\java\app\tuxguitar\song\models\  (TGSong 等模型)
```

模型清單（從 GP5OutputStream import 已見）：`TGSong, TGTrack, TGMeasure, TGMeasureHeader, TGBeat, TGVoice, TGNote, TGNoteEffect, TGString, TGChord, TGDuration, TGTempo, TGTimeSignature, TGDivisionType, TGColor, TGMarker, TGText, TGStroke, TGPickStroke, TGVelocities, TGChannel` + `effects.TGEffectBend/Grace/Harmonic/Trill/TremoloBar/TremoloPicking`

---

## 7. 決策紀錄

| 決策 | 結論 |
|---|---|
| 輸出方式 | ~~外部產生 .gp5~~ → **TuxGuitar plugin 直接載入主視窗**（使用者提出並確認，較簡單且和弦圖/播放現成） |
| 實作語言 | Java（TuxGuitar plugin），不用 Python |
| 弦編號 | 照 §3.5 映射（1↔3 對調） |
| 弱起檢查 | 弱起+末小節合計=完整拍數，要檢查 |
| 專案位置 | `G:\App\tuxguitar\SimpleInput\`（文件+測試檔）；plugin 模組放 `common/TuxGuitar-simpleinput/` |
