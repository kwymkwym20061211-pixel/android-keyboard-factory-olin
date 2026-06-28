# 生成キーボードアプリへの辞書機能追加

## Context

生成される(`:keyboard-template`がベースの)キーボードアプリに、Japanese IME風の「ユーザー辞書」機能を追加する。現状`:keyboard-template`はIMEサービス1つだけで、起動可能なActivityが無い(アプリ一覧からは開けない)。今回追加するのは:

1. 辞書の管理画面(起動用Activity) — 辞書のCRUD、複数辞書の切替、CSVでのimport/export。
2. キーボード本体(`GeneratedKeyboardService`)に辞書を組み込み、入力中に読みの前方一致で変換候補を出して確定できるようにする(ユーザー確認済み: 管理画面だけでなく入力中の変換まで対応)。

スキーマ・CSVのエスケープ規則・名前衝突時の拒否・前方一致のみ等は全てユーザー指定済み。実装は`:keyboard-template`モジュール内に閉じる(既存の`:app`/`:keyboard-engine`は変更しない)。

## 確定事項の確認(再掲)

- 辞書は複数、`dictionaries(id, name)` / `words(id, dictionaryId, reading, target)` の2テーブル(Room)。
- 管理画面: 画面上部に表示中辞書名をドロップダウン的に表示(タップで他の辞書に切替)、右上の三線ボタンからexport/import。さらに辞書の新規作成(空辞書を名前付きで)・削除も画面上で可能。
- 前方一致のみの単純なSQL `LIKE 'prefix%'`。品詞区別なし。
- CSV: UTF-8固定。1行 = `reading,target`。`,`は`\,`、`\`は`\\`でエスケープ。`\`の次の1文字は常にリテラルとして扱う(末尾の単独`\`はフォーマットエラー)。このエスケープはCSV変換時のみの話で、アプリ内DB/編集UIでは生の文字列をそのまま保持・表示する。
- 辞書名 = CSVファイル名から`.csv`拡張子を除いたもの。importはファイル形式チェック・名前衝突チェックの両方を通らないと全体を拒否(部分importしない)。
- IME本体: 入力中はCHARキーで「読み」バッファに追記し、**全辞書をまとめて**前方一致検索した候補を候補ストリップに表示。タップで確定。「現在表示中の辞書」という概念は管理画面の編集対象選択のみに使い、入力中の検索対象には影響しない。

## 既知のトレードオフ(実装前に明示しておく)

- `:app`の`TemplateApkPatcher`は`assets/`配下とmanifestのpackage名/labelしかパッチしない。`res/mipmap`やアイコンは一切パッチ対象外。つまりLauncher Activityを追加しても、生成される全てのキーボードアプリは同じ(デフォルトの)アイコンになる。今回は`android:icon`を設定せず、Androidのデフォルトプレースホルダーアイコンに委ねる(新規アイコン素材を用意しない、最小リスク)。アイコンのプロジェクト別カスタマイズは別タスクとして扱う。
- `dictionaries`/`words`にはユーザー指定の列に加え、Roomの都合上`id`(自動採番PK)を追加する。既存の`KeyboardProjectEntity`等と同じ慣習。
- `words.dictionaryId`には`ForeignKey(... onDelete = CASCADE)`を付与し、辞書削除時に所属する単語も自動削除されるようにする(ユーザー要望の「削除もできる」を実装する上で必要な追加仕様)。
- 候補ストリップは常に一定の高さを確保する(候補が無い時も空のまま表示領域は残す)。`FixedHeightContainer`の内部測定ロジックは触らず、渡す子Viewと合計高さだけを変える(既知の脆弱箇所のため変更しない: CLAUDE.md記載の通り)。

## 変更ファイル一覧

### Gradle設定
- `keyboard-template/build.gradle.kts`: `alias(libs.plugins.ksp)`追加。依存追加: `room-runtime`/`room-ktx`/`ksp(room-compiler)`/`appcompat`/`material`/`recyclerview`/`lifecycle-viewmodel-ktx`/`activity-ktx`/`kotlinx-coroutines-android`/`constraintlayout`。`buildFeatures { viewBinding = true }`を追加。(全て`gradle/libs.versions.toml`に既存のalias、`:app/build.gradle.kts`と同じ書き方)

### CSVエスケープ/パース(純Kotlin、Android依存なし)
新規パッケージ `android.keyboard.template.dictionary.csv`:
- `DictionaryCsv.kt` — object。
  - `fun encode(rows: List<Pair<String,String>>): String` — 各フィールドの`\`→`\\`、`,`→`\,`をエスケープ(`\`を先に処理)し`,`結合、行は`\n`結合。
  - `sealed class ParseResult { data class Success(rows: List<Pair<String,String>>); data class Error(lineNumber: Int, message: String) }`
  - `fun decode(content: String): ParseResult` — `content.lines()`で分割、空行はスキップ、各行を1文字ずつ走査してエスケープを解釈しつつ`,`で分割する`splitEscaped`を内部実装。フィールド数が2でない・どちらかが空・末尾が単独`\`、のいずれかでその行番号(1始まり)を伴う`Error`を返す。
  - `fun dictionaryNameFromFileName(fileName: String): String?` — 大文字小文字無視で`.csv`判定、除去後trimなしの空文字なら`null`。
  - `fun invalidFileNameChar(name: String): Char?` — 辞書名はexport時に`<name>.csv`というファイル名そのものになるため、ファイル名として使えない文字が含まれていないかを検査する。対象は`\ / : * ? " < > |`と制御文字(0x00–0x1F)。最初に見つかった不正文字を返す(無ければ`null`)。Windows/Android両方で問題が起きないよう、両OSの禁止文字の和集合を採用。
- `DictionaryCsvTest.kt`(`src/test/java/android/keyboard/template/dictionary/csv/`) — `keyboard-engine`の`ShapeNormalizerTest.kt`と同じJVM unit testの形式。ケース: 基本round-trip、`,`/`\`のエスケープ往復、末尾`\`エラー、列数不正エラー、空フィールドエラー、空行スキップ、ファイル名の拡張子判定(大文字小文字・空名)、`invalidFileNameChar`の検出/非検出。

### SAF/MediaStore IOヘルパー(Android依存、薄いラッパー、テストなし — `DownloadsWriter`/`FontImporter`と同様の方針)
- `DictionaryCsvIo.kt`(同パッケージ):
  - `fun displayName(context, uri): String?` — `ContentResolver.query`で`OpenableColumns.DISPLAY_NAME`を取得(`:app`側にはこの仕組みが無いため新規実装)。
  - `fun readText(context, uri): String` — `openInputStream`→`readBytes().toString(Charsets.UTF_8)`。
  - `fun writeToDownloads(context, displayName, content): Uri` — `DownloadsWriter.kt`(`app/.../export/DownloadsWriter.kt`)と同じ`ContentValues`+`MediaStore.Downloads`方式、mime `text/csv`。

### Room(`:app`の`data`パッケージと同じ規約)
新規パッケージ `android.keyboard.template.dictionary.data`:
- `DictionaryEntity.kt` — `@Entity(tableName="dictionaries", indices=[Index("name", unique=true)])`、`id: Long`(PK autoGenerate)、`name: String`。
- `WordEntity.kt` — `@Entity(tableName="words", foreignKeys=[...dictionaryId→dictionaries.id, onDelete=CASCADE], indices=[Index(["dictionaryId","reading"])])`、`id`、`dictionaryId: Long`、`reading: String`、`target: String`。
- `DictionaryDao.kt` — insert/update/delete、`findByName(name): DictionaryEntity?`(suspend)、`getById`、`observeAll(): Flow<List<DictionaryEntity>>`(name順)。
- `WordDao.kt` — insert/insertAll/update/delete、`observeForDictionary(dictionaryId): Flow<List<WordEntity>>`(id順)、`getAllForDictionary(dictionaryId): List<WordEntity>`(export用、suspend)、`searchByPrefixAcrossAll(escapedPrefix: String, limit: Int): List<WordEntity>`(suspend、`WHERE reading LIKE :escapedPrefix || '%' ESCAPE '\' ORDER BY reading LIMIT :limit`、辞書ID指定なし=全辞書横断)。
- `DictionaryDatabase.kt` — `KeyboardFactoryDatabase.kt`と同じ`@Volatile`/`synchronized`シングルトン、DB名`keyboard_dictionary.db`。
- `DictionaryRepository.kt` — バリデーション込みのビジネスロジック:
  - `observeDictionaries()` / `observeWords(id)`
  - `createDictionary(name): Result<Long>` — trim後空文字チェック→`DictionaryCsv.invalidFileNameChar`でファイル名として使えない文字が無いかチェック(export時に`<name>.csv`になるため)→`findByName`で重複チェック、エラーメッセージは日本語。
  - `deleteDictionary(dictionary)`
  - `addWord(dictionaryId, reading, target): Result<Long>` — 両方空白チェック。
  - `updateWord(word)` / `deleteWord(word)`
  - `importCsv(fileName, content): Result<Long>` — `DictionaryCsv.dictionaryNameFromFileName`→`invalidFileNameChar`チェック(SAFのDISPLAY_NAMEは提供元次第で不正文字を含み得るため念のため再検証)→`findByName`衝突チェック→`DictionaryCsv.decode`→いずれか失敗で`Result.failure`、全て通れば`db.withTransaction { 辞書insert→単語insertAll }`。
  - `exportCsv(dictionaryId): String` — `getAllForDictionary`→`DictionaryCsv.encode`。
  - `candidatesForPrefix(prefix, limit=20): List<WordEntity>` — `%`/`_`/`\`をエスケープしてから`searchByPrefixAcrossAll`に渡す(SQLのLIKEワイルドカード文字をユーザー入力の読みがそのまま含み得るため)。空prefixなら空リストを即返す。

### 管理画面UI
新規パッケージ `android.keyboard.template.dictionary.ui`、`ProjectListActivity`/`ProjectListViewModel`/`ProjectListAdapter`と同じ規約:
- `DictionaryViewModel.kt`(`AndroidViewModel`) — `dictionaries: StateFlow`、選択中辞書ID(`MutableStateFlow<Long?>`、辞書一覧が変わって選択中が無効になったら先頭へ自動フォールバック)、`words: StateFlow`(`flatMapLatest`)。create/delete/addWord/updateWord/deleteWord/importCsv/exportCsvForSelectedの各メソッド。
- `DictionaryActivity.kt` — ViewBinding、`ViewCompat.setOnApplyWindowInsetsListener`でedge-to-edgeパディング(CLAUDE.md記載の必須対応)。
  - 上部バー: `Spinner`(辞書名一覧、選択で`viewModel.selectDictionary`) + 「+」`ImageButton`(新規辞書名ダイアログ→`createDictionary`、失敗時`AlertDialog`でエラー表示) + 三線`ImageButton`(`PopupMenu`: エクスポート/インポート/この辞書を削除)。
  - 削除は確認`AlertDialog`を経由。
  - 単語一覧: `RecyclerView`+`WordAdapter`(`ListAdapter`+`DiffUtil`、行タップで編集ダイアログ、行内削除ボタンで`deleteWord`)。
  - `FloatingActionButton`で単語追加ダイアログ(読み/変換先の2`EditText`、両方`singleLine`でCSV側の改行考慮を不要にする)。
  - 辞書0件時はリスト/FABを隠し、案内文言を表示。
  - インポート: `registerForActivityResult(ActivityResultContracts.OpenDocument())`を`arrayOf("*/*")`で起動(既存`pickFontLauncher`と同方式)→`DictionaryCsvIo.displayName`+`readText`→`viewModel.importCsv(fileName, content)`→失敗は`AlertDialog`でメッセージ表示、成功でSpinnerが新辞書に切替。
  - エクスポート: `lifecycleScope.launch`で`viewModel.exportCsvForSelected()`→`DictionaryCsvIo.writeToDownloads(this, "$name.csv", csv)`→`Toast`で成功通知(`EditorActivity.runExport`と同系統の見せ方)。
- `WordAdapter.kt` — `ProjectListAdapter.kt`と同型。
- レイアウト: `activity_dictionary.xml`(上部バー+RecyclerView+FAB+空状態View)、`item_word.xml`、`dialog_edit_word.xml`(読み/変換先、新規作成と編集を共用)、`dialog_create_dictionary.xml`(辞書名1つ)。
- `res/values/strings.xml`・`dimens.xml`に文言/寸法追加、`res/values/themes.xml`新規(`app/.../themes.xml`と同じ`Theme.Material3.DayNight.NoActionBar`系)。

### Manifest
- `<activity android:name=".dictionary.ui.DictionaryActivity" android:exported="true" android:theme="@style/...">`にMAIN/LAUNCHER intent-filterを追加。`android:label`は明示せず`<application>`側を継承(既存の`<service>`と同じ理由、CLAUDE.md既知の罠)。`android:icon`も付けない(上記トレードオフの通り)。

### `GeneratedKeyboardService` / 候補ストリップ統合
- 新規メンバ: `composingReading = StringBuilder()`、`serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())`(`onCreate`で生成、`onDestroy`で`cancel()`)、`candidateJob: Job?`、`repository = DictionaryRepository(DictionaryDatabase.getInstance(applicationContext))`、`candidateContainer: LinearLayout`(候補チップを積む)。
- `onCreateInputView()`: 縦`LinearLayout`を作り、上段に`HorizontalScrollView`(固定高さ`R.dimen.candidate_strip_height`、中に`candidateContainer`)、下段に既存`KeyboardGridView`(`weight=1`)を配置。`FixedHeightContainer`へは合計高さ`keyboard_height + candidate_strip_height`を渡し、`setContent(縦LinearLayout)`する。`FixedHeightContainer`自体は無改修。
- `handleKeyTapped`:
  - `CHAR`: `composingReading.append(key.text.orEmpty())` → `ic.setComposingText(composingReading, 1)` → `updateCandidates(ic)`。
  - `DELETE`: バッファが空でなければ最後の1文字を削除し`setComposingText`/`updateCandidates`(空になったら`ic.setComposingText("", 1)`して候補クリア)。空なら既存の`ic.deleteSurroundingText(1,0)`。
  - `ENTER`: バッファが空でなければ`ic.commitText(composingReading, 1)`して確定・クリア(読みをそのまま確定)。空なら既存の改行commit。
  - `PAGE_NEXT`/`PAGE_PREV`/`NONE`: 変更なし。
  - `onFinishInput()`をoverrideし、`currentInputConnection?.finishComposingText()`+バッファ/候補クリア。
- `updateCandidates(ic)`: `candidateJob?.cancel()`、空バッファなら`candidateContainer.removeAllViews()`して終了。非空なら`serviceScope.launch { repository.candidatesForPrefix(...) }`→結果を`candidateContainer`に`TextView`チップとして再構築、各チップに`setOnClickListener`で`ic.commitText(word.target, 1)`+バッファ/候補クリア。
- 新規dimen `candidate_strip_height`(48dp程度)を`dimens.xml`に追加。

## 実装順序と確認方法

1. Gradle設定変更 → `:keyboard-template:assembleDebug`で依存解決のみ確認。
2. `DictionaryCsv`+テスト → `:keyboard-template:testDebugUnitTest`(最もロジックが細かい部分を最速で固める)。
3. Room関連一式 → `:keyboard-template:assembleDebug`(KSPが`:keyboard-template`で初導入になるため、UIコードを足す前に単独で通しておく)。
4. `DictionaryCsvIo` → assembleのみ確認。
5. 管理画面UI一式(Activity/ViewModel/Adapter/レイアウト/menu/strings/dimens/theme/manifest) → `:keyboard-template:assembleDebug`(ViewBinding生成含む)。
6. `GeneratedKeyboardService`/候補ストリップ統合 → 既存稼働コードへの変更なので最後に分離して実施 → `:keyboard-template:assembleDebug`。
7. 最終確認: `:keyboard-template:assembleDebug` `:keyboard-template:testDebugUnitTest` に加え、`:keyboard-template:assembleRelease`(`:app`の`copyKeyboardTemplateApk`タスクが実際に消費するバリアント)、最後に`:app:assembleDebug`が通ることまで確認。ビルドログは`tmp/`配下に出力しバックグラウンド実行する(CLAUDE.md記載の規約)。
8. 実機での動作確認(辞書CRUD、CSV import/export往復、入力中の候補表示・確定)はユーザー側で実施(`adb install`/`installDebug`はClaude側で実行しない、既存の運用ルール通り)。
