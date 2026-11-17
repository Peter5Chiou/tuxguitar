使用方法:

1. 在simpleinput目錄中，執行\
   SimpleInput2AmpleGuitar.bat

2. 把程式產生的 xxx\_ag.mid 拖到reaper的軌道中

3. add fx to track, 選 vst3i ample guitar

4. 設定ample guitar
   
   1. 選擇strummer
   2. eable strummer mode(藍色)
   3. set  switch chord mode to select mode(白色)
   4. 在strum time 按右鍵，選 14 control(變藍色),  再按下方 "打勾"
   5. 在strum time 隨便調一下，這時你再按右鍵，會發現 14 control  的controller欄位多了 s-strum time
   6. 把程式產生的 xxx.chords 匯入chord bank 中

5. 回到reaper 中就可播放了

---

## 其他工具

### StrumSeqImporter（Ample Guitar `.strumseq` → SimpleInput `.strum`）

```
javac -encoding UTF-8 -cp "../common/TuxGuitar-simpleinput/target/classes;../common/TuxGuitar-lib/target/classes" -d tools tools/StrumSeqImporter.java

java -cp "../common/TuxGuitar-simpleinput/target/classes;../common/TuxGuitar-lib/target/classes;tools" StrumSeqImporter <input.strumseq> [--chords "G,C,Am,F"] [--bars N] [--out out.strum] [--validate]
```

- `--chords`：和弦進行（逗號分隔，循環使用），預設 `G`。
- `--bars N`：pattern 重複 N 小節。
- `--validate`：用真正的 `SimpleInputParser` 驗證產出。
- Note 對照與限制見 `../README.md` 的「Ample Guitar Strummer 14 列對照」。

### StrumSeqToSymbols（只取 SimpleInput 符號字串，純 JDK 免 classpath）

把ample guitar .strumseq 檔 → simpleinput 格式

```
java tools/StrumSeqToSymbols.java <input.strumseq> [--name 名稱] [--full] [--no-rests] [--velocity]
```

- 無參數：只印符號字串，例如 `mu md:321 md X X u d:321 d z8`
- `--name 名稱`：印成 `@名稱 = ...`（僅限單一小節）
- `--full`：加上 `M:` / `L:` / `Q:` 標頭
- `--no-rests`：省略休止符 `z`
- `--velocity`：加上 `!ppp`\~`!fff` 力度

或

seq2SimpleInput.bat

### DumpMidi（印出 MIDI 的 PPQ / NoteOn / NoteOff / CC / Meta）

```
java tools/DumpMidi.java <file.mid>
```

### BuildCheck（`.strum` → TGSong，印出弦/品位/DeadNote/PalmMute/刷弦方向）

```
java -cp "../common/TuxGuitar-simpleinput/target/classes;../common/TuxGuitar-lib/target/classes;tools" BuildCheck <file.strum>
```

### ParserProbe（印出 parser 解析後的事件 IR，除錯用）

```
java -cp "../common/TuxGuitar-simpleinput/target/classes;../common/TuxGuitar-lib/target/classes;tools" ParserProbe <file.strum>
```
