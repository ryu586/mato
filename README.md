mato
Minecraft Aim Trainer (Paper 1.21.4)

Paper 1.21.4向けに作成した、FPS風エイム練習用プラグインです。

主な機能
木のクワを銃として使用
セミオート / フルオート切り替え
ADS（エイム）機能
リロード機能（30発仕様）
リコイル・集弾率
スコアシステム
レベルアップするターゲット
ターゲットHPバー表示
ターゲットの左右移動
ターゲットの上下移動
ターゲットの円形移動
ターゲットのランダム移動
ターゲットの停止
/lv target <名前> <数字> コマンドでターゲットレベル変更
/move target <名前> <パターン> コマンドでターゲットの移動パターン変更
CLASSIC / TIMEゲームモード
TIMEモードの60秒タイマー
TIMEモード終了時の最終スコア表示
プレイヤー参加時に武器を自動配布
クワ本来の耕作・攻撃動作を無効化
複数のターゲットを同時に生成可能
ターゲットごとに名前を設定可能
ターゲットの名前をHPバーの上に表示
名前を指定してターゲットを削除可能
/targetname で現在存在するターゲットの名前を確認可能
ターゲット移動時に角度（Yaw / Pitch）を変更しない
推奨設定

より良い体験のため、専用リソースパックを server.properties の resource-pack に設定することを推奨します。

https://drive.google.com/uc?export=download&id=1KlMGPJNyXb3je84Qgw4iuX_-r9f_tbe2

※設定しなくてもプラグインは動作しますが、木のクワは銃の見た目ではなく通常の木のクワとして表示されます。

操作方法
操作	内容
左クリック	射撃
右クリック	ADS切り替え
Fキー	セミ/フルオート切り替え
Shift	リロード
石のクワ右クリック	スコアリセット
金のクワ右クリック	ターゲットレベルリセット
レベルシステム
ターゲット撃破でレベルアップ
HPはレベルに応じて増加
Lv50以降はターゲットの移動速度も上昇
ターゲットのレベルが上がると数秒間ターゲットが消えます
ターゲット移動

/move コマンドでターゲットごとの移動パターンを変更できます。

移動パターン
/move target <名前> left_right
/move target <名前> up_down
/move target <名前> circle
/move target <名前> random
/move target <名前> stop
各パターン
left_right
ターゲットが左右に移動します。
up_down
ターゲットが上下に移動します。
circle
ターゲットが円形に移動します。
random
ターゲットがランダムな動きをします。
stop
ターゲットがその場で停止します。
ゲームモード
/mode classic
/mode time
CLASSIC

通常のエイム練習モードです。

時間制限なしでターゲットを撃ち続け、スコアを伸ばします。

TIME

60秒間で獲得したスコアを競うモードです。

TIMEモードでは残り時間がアクションバーに表示されます。

残り時間が0秒になるとゲームが終了し、最終スコアが表示されます。

ターゲット管理機能
ターゲット生成
/spawn target <名前>

指定した名前のターゲットを生成します。

複数のターゲットを同時に生成することができます。

例：

/spawn target a
/spawn target b
/spawn target c
ターゲット一覧
/targetname

現在存在しているターゲットの名前をチャットに表示します。

例：

a
b
c
ターゲット削除
/delete target <名前>

指定した名前のターゲットを削除します。

例：

/delete target a
ターゲットレベル変更
/lv target <名前> <数字>

指定したターゲットのレベルを変更します。

例：

/lv target a 50
ターゲット移動変更
/move target <名前> <パターン>

指定した名前のターゲットだけ移動パターンを変更します。

例：

/move target a random
/move target b circle
/move target c stop
ターゲットの名前表示

ターゲットには生成時に指定した名前が設定されます。

名前はターゲットのHPバーの上に表示されます。

例：

a
Lv.1
██████████
10/10

複数のターゲットを生成した場合、それぞれのターゲットに別々の名前を設定できます。

ターゲットの角度

ターゲットを移動させても、ターゲットの角度（Yaw / Pitch）は変更されません。

移動処理では位置だけを変更し、角度はそのまま維持します。

タブ補完

各コマンドにはタブ補完が対応しています。

例：

/spawn target <名前>
/delete target <名前>
/lv target <名前> <数字>
/move target <名前> <パターン>
/mode <classic|time>

/move では使用可能な移動パターンもタブ補完されます。

left_right
up_down
circle
random
stop
コマンド一覧
コマンド	内容
/spawn target <名前>	指定した名前のターゲットを生成
/targetname	現在存在するターゲットの名前を表示
/delete target <名前>	指定したターゲットを削除
/lv target <名前> <数字>	指定したターゲットのレベルを変更
/move target <名前> <パターン>	指定したターゲットの移動パターンを変更
/mode classic	CLASSICモードに変更
/mode time	TIMEモードに変更
動作環境
Minecraft Java Edition 1.21.4
Paper 1.21.4
Java 21
プレイ動画

https://youtu.be/kxJ1gUecgjI

※アップデート後の動画はめんどくさいので撮ってません。

その他

※このプラグインは個人制作・学習目的で開発したものです。
