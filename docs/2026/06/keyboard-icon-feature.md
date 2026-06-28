# 生成キーボードアプリへのアイコン設定機能

## Context

生成される(`:keyboard-template`がベースの)キーボードアプリは、これまで`android:icon`未設定でAndroidのデフォルトプレースホルダーアイコンに委ねていた([dictionary-feature-design.md](dictionary-feature-design.md)の「既知のトレードオフ」で「アイコンのプロジェクト別カスタマイズは別タスクとして扱う」と明示していた件、本ドキュメントで対応)。今回、プロジェクトごとに端末内の画像を1枚選んで生成アプリのアイコンに設定できるようにする。

仕様(ユーザー指定): 渡された画像の横幅・縦幅のうち短い方を一辺とする正方形を、中央を中心になるべく切り捨てを減らして切り出し、そのまま(マスク等の追加加工なしで)アイコンにする。

## 確定事項

- **保存方式**: `defaultFontPath`と同じ規約。Room側はファイルパス文字列(`KeyboardProjectEntity.iconPath: String?`)のみを持ち、実体はimport直後に`filesDir/icons/`へコピー(正確には切り出し済みの正方形PNG)して保存する。DBにBLOBは持たない。
- **DBマイグレーション**: 実機に既存プロジェクトが乗っている時期のため、`version`を1→2に上げ、`Migration(1, 2)`(`ALTER TABLE keyboard_project ADD COLUMN iconPath TEXT`)を明示的に追加した。過去の`defaultFontUri`→`defaultFontPath`のような無断改名(version固定のまま)は今回は踏襲しない。
- **画像選択**: フォントの`OpenDocument`ではなく`ActivityResultContracts.PickVisualMedia`(Photo Picker)を採用。画像専用・ストレージ権限不要で、用途に合っている。
- **切り出し・解像度**: `ImageDecoder`でデコード→`min(width, height)`を一辺とする中央クロップ→一辺が512pxを超える場合のみ512pxへ縮小して保存(「解像度高め」の要望を満たしつつ、実機のメモリ・ストレージを無制限に使わないための上限)。PNG保存。
- **生成アプリ側のアイコンリソース**: `keyboard-template`はmipmapリソースを一切持っていなかったため、`res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png`(48/72/96/144/192px、単色プレースホルダー)を新規追加し、`AndroidManifest.xml`の`<application>`に`android:icon="@mipmap/ic_launcher"`を追加した。アイコンを設定しないプロジェクトでも生成アプリは必ず何らかのアイコンを持つ。
- **書き出し時の差し替え**: `TemplateApkPatcher`がpre-bake済みの`mipmap/ic_launcher`の各密度バケットを、ARSCLibの`ApkModule.listResFiles()`/`removeInputSource`+`add(ByteInputSource(...))`(既存の`extraAssets`注入と同じ仕組み)で上書きする。各バケットのリサイズ後ピクセルサイズは、密度→pxの対応表をコードに持たず、テンプレートに既に入っているプレースホルダー画像自体のサイズを読み取って決める(プレースホルダーの構成を変えてもコード側の修正が要らない)。
- **スコープ外**: adaptive icon(foreground/background分離・マスク)は今回未対応。「そのままアイコンにする」という指定どおり単純な正方形画像のみを採用し、マスク表現は各ランチャーのデフォルト挙動に委ねる。

## 変更ファイル一覧

- `app/.../data/KeyboardProjectEntity.kt` — `iconPath: String?`追加。
- `app/.../data/KeyboardFactoryDatabase.kt` — `version=2`、`MIGRATION_1_2`追加。
- `app/.../data/KeyboardProjectRepository.kt` — `setIconPath(projectId, iconPath)`追加。
- `app/.../ui/EditorViewModel.kt` — `setIconPath(path)`追加。
- `app/.../icon/IconImporter.kt`(新規) — `FontImporter`と対の構造。`import(context, uri, fileName): File`。
- `app/.../ui/EditorActivity.kt` — プロジェクト設定ダイアログに「App icon」セクション(プレビュー`ImageView`+Pick/Clear)を追加、`PickVisualMedia`のlauncher登録。
- `app/src/main/res/layout/dialog_project_settings.xml` / `values/strings.xml` — UI文言・レイアウト追加。
- `app/.../export/TemplateApkPatcher.kt` — `iconSourceFile: File?`パラメータ追加、`replaceLauncherIcon`実装。
- `app/.../export/KeyboardExportPipeline.kt` — `project.iconPath`を`TemplateApkPatcher.patch`に渡す。
- `keyboard-template/src/main/res/mipmap-*/ic_launcher.png`(新規)、`keyboard-template/src/main/AndroidManifest.xml` — プレースホルダーアイコン+`android:icon`追加。
