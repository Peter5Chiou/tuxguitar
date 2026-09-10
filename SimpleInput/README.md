# SimpleInput — TuxGuitar 刷法譜匯入 plugin

用簡單文字格式（`.strum`）寫吉他刷法譜，透過 TuxGuitar 的 **File → Import → SimpleInput Strum** 匯入，
直接在 TuxGuitar 顯示五線譜/六線譜、和弦圖、刷弦方向，並可播放與編輯。

## 檔案格式（.strum）

```
% 註解（% 到行尾）

M:4/4        % 必要，拍號（分母定義一拍的時值）
L:1/8        % 必要，基礎時值單位
Q:1/4=90     % 可選，速度 BPM（以四分音符記）
K:C          % 可選，調性

@basic  = d2u2d2u2              % 定義可重用刷法（不可巢狀引用）
@arp    = t (23) 1 (23) 1 (23) 1 (23)

G(4) @basic | C(4) @island | Am(4) @arp | F(4) @basic C(4) @basic ||
```

### 標頭

| 標頭 | 必要 | 說明 |
|---|---|---|
| `M:` | ✓ | 拍號。**分母定義一拍**：4/4 = 四分音符一拍；6/8 = 八分音符一拍 |
| `L:` | ✓ | 基礎時值單位。4/4 + `L:1/8` → 1 基礎單位 = 半拍（每拍 2 單位） |
| `Q:` | | 速度，`Q:1/4=90` = 四分音符 90 BPM。省略時預設 90 |
| `K:` | | 調性（目前僅記錄，不影響輸出） |

### 時值規則

- **和弦後括號數字 N ＝ N 拍**（以 `M:` 分母為一拍）。`G(4)` = G 和弦 4 拍；`Em9(4)` = Em9 和弦 4 拍。和弦名稱中的數字不代表拍數。
- **事件後數字 ＝ 基礎單位數**。`d2` = 下刷 2 個基礎單位。
- pattern 事件的總基礎單位數**必須剛好等於**和弦拍數換算的單位數：
	- 4/4 + `L:1/8`（每拍 2 單位）：`G(4) d2u2d2u2` = 4拍×2 = 8 單位 ✓
  - `G(4) dudu` = 只有 4 單位 ≠ 8 → **錯誤**（錯誤訊息會指出行號與小節）
- 和弦後沒括號拍數：持續時間由 pattern 總長決定（`G d2u2d2u2` = 8 單位）。
- 和弦後沒 pattern：需要使用括號指定拍數，例如 `G(4)` 代表一次下刷全和弦，持續 4 拍。
- **弱起**：第一小節可不足拍，但弱起 + 末小節合計必須等於整小節單位數。
- 指定把位:
    和弦名稱@把位(拍數)，例如:
    Em@3(4)      % Em 按型從第3品開始，持續4拍
    Em@7(2)      % Em 按型從第7品開始，持續2拍
    指定把位會將該和弦按型的所有非悶弦品位整體平移；把位範圍為 1~24。

### 事件符號

| 符號 | 意思 |
|---|---|
| `d` | 下刷。未指定弦＝刷和弦所有發音弦；`d:45` 只刷 4、5 弦 |
| `u` | 上刷，同上 |
| `t` | 彈和弦根音（單音） |
| `x` | 短刷：根音 + 根音旁靠高音側一根弦（根音第 5 弦 → 刷 5、4 弦） |
| `1`~`6` | 彈單一弦；和弦圖的 `x` 不限制指法，未指定品位時使用空弦 |
| `(23)` | 同時彈 2、3 弦；和弦圖的 `x` 不限制指法。可接 `*N`/`/N` 調整時值，如 `(6321)*2`；括號內數字可用空格分隔，如 `(6 3 2 1)*2`。括號內每根弦可指定品位，如 `(6 3=4 2=5 1=3)*2`（弦 3 按 4 品、弦 2 按 5 品、弦 1 按 3 品，弦 6 用和弦原品位） |
| `2=3` | 強制品位：第 2 弦強制按第 3 品（覆寫和弦原按法） |
| `z` 或 `-` | 休止 |

數字可接在 `d`/`u` 後：`d2` = 下刷 2 單位；指定弦用冒號：`d2:45` = 下刷 2 單位只刷 4、5 弦。刷法指定到和弦圖的 `x` 弦時，會輸出悶音。

事件後可用 `*N` 或 `/N` 調整時值：`2` = 第 2 弦基本時值、`2*2` = 時值乘 2、`2/2` = 時值除 2、`d/2` = 下刷半個基本單位。短刷支援 `x2` 與 `x*2`。
強制品位使用等號，品位在時值之前，例如 `2=3*2` = 第 2 弦按第 3 品，時值乘 2。

### 弦編號（特殊習慣）

只有 **1 和 3 對調**，其餘與物理弦號相同：

| 檔案寫 | 實際弦 |
|---|---|
| 1 | 3 |
| 2 | 2 |
| 3 | 1（最細，高音 e） |
| 4 | 4 |
| 5 | 5 |
| 6 | 6（最粗） |

### 和弦

內建開放和弦：C D E Em Am A A7 D7 E7 G G7 F Cadd9 Dm Asus4 Dsus4 B7。
未內建的和弦自動以**封閉和弦推導**（E 形/A 形，取把位較低者），支援 major/minor/7 與升降半音（F#m、Bb、C#m…）。
未識別的和弦名 → 匯入錯誤。

### 錯誤報告

錯誤格式為 `第 N 行、第 M 小節：描述`，任何錯誤會中止匯入。

## 安裝

`mvn package` 產生的 `tuxguitar-simpleinput.jar` 已自動組裝進發行版的 `share/plugins/`。
手動安裝：把 jar 複製到 TuxGuitar 發行版的 `share/plugins/`（或使用者外掛目錄），重啟 TuxGuitar。

## 使用

1. 啟動 TuxGuitar
2. **File → Import → SimpleInput Strum**
3. 選擇 `.strum` 檔

## 建置

```
cd G:\App\tuxguitar\desktop\build-scripts\tuxguitar-windows-swt-x86_64
mvn package -DskipTests
```

產出：`target\tuxguitar-9.99-SNAPSHOT-windows-swt-x86_64\`（含 `share\plugins\tuxguitar-simpleinput.jar`）。
注意：建置前需關閉執行中的 TuxGuitar，否則 jar 被鎖住會失敗。

## 開發環境

- JDK 24（`C:\Program Files\Java\jdk-24`）、Maven 3.9.16（`G:\App\apache-maven-3.9.16`）
- 原始碼：`common/TuxGuitar-simpleinput/src/app/tuxguitar/io/simpleinput/`
  - `SimpleInputParser.java` — Lexer/Parser/Validator（時值檢查、@展開、弱起）
  - `SimpleInputChordDictionary.java` — 和弦字典 + 封閉推導（低把位優先）
  - `SimpleInputSongBuilder.java` — IR → TGSong（TGBeat 用 preciseStart）
  - `SimpleInputReader.java` / `SimpleInputReaderPlugin.java` — TGSongReader 進入點
- 離線測試：`SimpleInput/tools/`（`ParserTest.java`、`BuildTest.java`、`ReaderTest.java`）
- 規格細節：`SimpleInput/docs/SPEC.md`

## 版本

- v1.0：基本刷法/和弦/弱起檢查
- v1.1：`x` 短刷、強制品位 `弦=品`、事件時值乘除、封閉和弦低把位優先、和弦圖修正
