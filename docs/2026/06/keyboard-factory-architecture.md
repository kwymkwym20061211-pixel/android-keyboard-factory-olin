# キーボードファクトリー アーキテクチャ決定事項 (2026-06)

## 背景

このアプリは、Android単体での操作だけでカスタムキーボードIME（インプットメソッド）アプリを作成し、設定したアプリ名・キー配列でAPKを生成してDownloadsフォルダに書き出す「キーボードアプリのジェネレーター」。複数のキーボードプロジェクトをSQLite(Room)で管理し、名前で検索・再編集できる。

## 確定した方針

- **APK生成方式**: 端末上でaapt/d8等のフルビルドは行わない。あらかじめビルド済みの汎用IMEテンプレートAPKをZIPレベルで差し替える方式を採用。
  - `io.github.reandroid:ARSCLib`（Apache-2.0, Maven Central）でAndroidManifestのバイナリXMLと`resources.arsc`を正規の構造で読み書きし、package名とアプリラベルを書き換える。
  - `com.android.tools.build:apksig`（Google Maven, AGPと同バージョン系列で配布）で v1/v2/v3 署名をオンデバイスで実行する。
  - 署名鍵は`AndroidKeyStore`で`KeyGenParameterSpec`に証明書情報を渡して生成し、自己署名証明書込みのキーペアをOS側に作らせる（BouncyCastle等の追加証明書ライブラリは不要）。
- **フォント入手**: 端末内の`.ttf`/`.otf`ファイルをファイルピッカー(SAF)で取り込み可能にする（bundled-onlyではない）。
- **フォント設定単位**: キーボードプロジェクト全体のデフォルトフォント＋ページごとの上書き。キー単位の指定は行わない。
- **再エクスポート時の署名/識別子**: キーボードプロジェクトごとに固定の`applicationId`と署名鍵を保持し、再エクスポートは「アプリの更新」として上書きインストールできるようにする。
- **実装言語**: Kotlin（既存の素のJavaテンプレートから切り替え）。

## モジュール構成

```
:app                  factory本体。Room DB、エディタUI、フォント取込、ラスタライズ、
                       APKパッチ/署名/書き出し。
:keyboard-engine       (com.android.library) :app と :keyboard-template の両方が依存する
                       共有コア。レイアウトのドメインモデル、JSONスキーマの(de)serialize、
                       Canvasベースのグリッド描画+結合キーの当たり判定View。
:keyboard-template     (com.android.application) 生成物の素になる薄いIME本体。
                       :app のビルド時に assembleRelease され、:app/src/main/assets に
                       コピーされてテンプレートAPKとして同梱される。
```

## データモデル（Room）

- `KeyboardProjectEntity(id, name, applicationId?, signingKeyAlias?, defaultFontUri?, createdAt, updatedAt, lastExportedAt?)`
- `PageEntity(id, projectId, pageIndex, rows≤32, cols≤32, fontUriOverride?)`
- `KeyCellEntity(id, pageId, row, col, ownerCellId?, role, text?)`
  - 結合キーは「最も左上のセルが本体」というユーザー指定の正規化ルールを採用。本体だけが`role`/`text`を持ち、結合された他のセルは`ownerCellId`で本体を指す。

## エクスポート用JSONスキーマ（`assets/keyboard_layout.json`）

```json
{
  "schemaVersion": 1,
  "pages": [
    {
      "rows": 4, "cols": 10,
      "keys": [
        { "ownedCells": [[0,0]], "role": "CHAR", "text": "a", "image": "key_images/p0_0_0.png" },
        { "ownedCells": [[0,1],[0,2],[1,1],[1,2]], "role": "ENTER", "text": null, "image": null }
      ]
    }
  ]
}
```

`role`が`CHAR`の時だけラスタライズ済みPNGを持つ。それ以外の役割（ENTER/DELETE/PAGE_NEXT/PAGE_PREV/NONE）は`:keyboard-template`組み込みの固定アイコンを使う。

## Gradle上の技術的な裏付け（2026-06-27時点で確認済みのバージョン）

このリポジトリは AGP 9.2.1 / Gradle 9.4.1。AGP 9系はビルトインKotlinサポートがデフォルト有効で、`org.jetbrains.kotlin.android`プラグインの明示適用は不要。annotation processingはkapt非対応のためKSPを使う。

| ライブラリ | バージョン | 配布元 |
|---|---|---|
| Kotlin Gradle Plugin（ビルトイン経由） | 2.3.10 | (AGP同梱) |
| KSP | 2.3.9 | mavenCentral |
| org.jetbrains.kotlin.plugin.serialization | 2.3.10 | mavenCentral |
| androidx.room | 2.8.4 | google |
| kotlinx-serialization-json | 1.11.0 | mavenCentral |
| kotlinx-coroutines-android | 1.11.0 | mavenCentral |
| io.github.reandroid:ARSCLib | 1.3.8 | mavenCentral |
| com.android.tools.build:apksig | 9.2.1 | google |
| androidx.core:core-ktx | 1.18.0 | google |

### 既知のハマりどころ

- AGP 9のビルトインKotlinはKGP 2.3.10に固定されている（9.0時点の案内では2.2.10だったが、9.2で2.3.10に更新された）。KSP/kotlinx.serializationプラグインのバージョンはこれに合わせる必要がある。
- KSPがビルトインKotlinと組み合わさると、古いバージョン（kotlinバージョン接頭辞付きの命名、例: `2.2.10-2.0.2`）では `Using kotlin.sourceSets DSL to add Kotlin sources is not allowed with built-in Kotlin` で構成エラーになる（[google/ksp#2729](https://github.com/google/ksp/issues/2729)）。プレーンなバージョン番号体系に変わった2.3.x系（今回は2.3.9）を使うことで解消した。
- `androidx.core:core-ktx`は1.19.0だとcompileSdk 37を要求してくるため、compileSdk 36.1環境では1.18.0を使う。

## 今後の運用

新たに大きなアーキテクチャ決定が発生した場合は、その月の`docs/<YYYY>/<MM>/`ディレクトリに追記または新規ファイルを追加する。詳細な実装計画は `/home/user/.claude/plans/wondrous-singing-ocean.md` を参照。
