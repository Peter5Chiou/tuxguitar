使用方法:

1. 在simpleinput目錄中，執行

    java -cp "../common/TuxGuitar-simpleinput/target/classes;../common/TuxGuitar-lib/target/classes;tools" AmpleGuitarExporter %1 output

1. 在reaper 新增 midi item,  然後把那個midi item delete 掉

2. 把程式產生的 xxx_ag.mid 拖到那個新增的軌道中

3. add fx to track, 選 vst3i ample guitar

4. 設定ample guitar
   
   1. 選擇strummer
   
   2. eable strummer mode(藍色)
   
   3. set  switch chord mode to select mode(白色)
   
   4. 在strum time 按右鍵，選 14 control(變藍色),  再按下方 "打勾"
   
   5. 在strum time 隨便調一下，這時你再按右鍵，會發現 14 control  的controller欄位多了 s-strum time 
   
   6. 把程式產生的 xxx.chords 匯入chord bank 中

5. 回到reaper 中就可播放了


