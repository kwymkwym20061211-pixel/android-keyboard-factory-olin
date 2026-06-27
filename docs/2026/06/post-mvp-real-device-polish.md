# MVP後の実機ポリッシュ (2026-06-27)

[mvp-completion-and-troubleshooting.md](mvp-completion-and-troubleshooting.md)でMVPに到達した後、実機で生成キーボード・ファクトリーアプリ双方を使い込んだ上で見つかった追加の不具合と対応の記録。

## 1. 生成キーボードの下段がホームバー/ジェスチャーバーに重なって押せない

- **症状**: キーボードを4段にすると、最下段がナビゲーションバー（ホームボタン等）と重なり、その部分がタップできない。Gboardと比べて全体の高さも低い。
- **原因**: `:keyboard-template`（IME本体）のtargetSdkがAndroid 15相当（35）以上のため、ウィンドウがedge-to-edge（システムバーの裏まで描画）になるのがOS側で強制される。`FixedHeightContainer`は高さは強制していたが、ナビゲーションバーの分だけ上にずらす処理が無かったため、固定高さの最下部がバーの裏に隠れていた。
- **修正**:
  - `keyboard_height`を260dp→320dpに引き上げ（Gboard比で低いという指摘に対応）。
  - `FixedHeightContainer`に`ViewCompat.setOnApplyWindowInsetsListener`でナビゲーションバーのinsetを取得する処理を追加し、`onMeasure`/`onLayout`で「コンテナ全体の高さは固定（ナビゲーションバーを含む分も確保）」「実際のキー描画Viewはinset分だけ低い高さに収めてコンテナ上端に配置」という形に変更。バーと重なる帯はキー無しの余白になる。
  - `:keyboard-template`に`androidx.core:core-ktx`を追加（`WindowInsetsCompat`用）。

## 2. キー押下時の視覚フィードバックがない

- **要望**: ボタンを押している間、キーが暗くなる標準的な反応が欲しい。
- **対応**: `KeyboardGridView`の`onTouchEvent`を`ACTION_DOWN`/`ACTION_MOVE`/`ACTION_UP`/`ACTION_CANCEL`それぞれ処理する形に変更し、押されているキー（`pressedKey`）を保持して`onDraw`で別の塗り色（`pressedFillPaint`、通常より暗いグレー）で描画するようにした。指を動かして別のキーに移動したら追従してハイライトが移る、標準的なソフトキーボードの挙動。

## 3. ファクトリーアプリ本体もステータスバー/ホームバーと重なっていた

- **原因**: 当初のAndroid Studio雛形の`MainActivity.java`には`ViewCompat.setOnApplyWindowInsetsListener`でsystemBarsのinsetをルートViewのpaddingに反映する処理があったが、画面をプロジェクト一覧・エディタ画面に作り直した際にこれを引き継いでいなかった。
- **修正**: `ProjectListActivity`・`EditorActivity`双方のルートViewに同様の`WindowInsets`→padding処理を追加。

### ハマったポイント: `androidx.activity.EdgeToEdge.enable()`が解決できない

- 上記対応の際、雛形に倣って`EdgeToEdge.enable(this)`も呼ぼうとしたが、`androidx.activity:activity-ktx:1.13.0`が依存関係としては正しく解決されている（`:app:dependencies`で確認済み、AARの中に`EdgeToEdge.class`も実在し`enable(ComponentActivity)`という公開静的メソッドも存在する）にもかかわらず、Kotlinコンパイラ（AGP9のビルトインKotlin、KGP 2.3.10）が`Unresolved reference 'EdgeToEdge'`を出す、という原因不明の問題に遭遇した。
- キャッシュ無効化・`--rerun`等を試しても解消せず、深追いを避けて**呼び出し自体を削除**した。targetSdk 35+環境ではedge-to-edge自体はOS側で強制されるため、`EdgeToEdge.enable()`は主にステータスバーアイコン色などの装飾目的であり、重なり解消の本体である`WindowInsets`→padding処理だけで実害なく解決できている。
- **再発したら**: 単体の小さいKotlinファイルで`androidx.activity.EdgeToEdge`をimportするだけの再現コードを作り、AGP9のビルトインKotlinとAARのKotlinメタデータ互換性の問題かどうかを切り分けるとよい（[google/ksp#2729](https://github.com/google/ksp/issues/2729)のような、AGP9のビルトインKotv周りの既知の相互運用バグの一種である可能性がある）。

## 検証方法

実機テストは基本的にユーザー側で実施（Claude側から`installDebug`すると失敗することがあったため、以後Claudeはビルド確認のみ行い、インストール・実機操作はユーザーに委ねる運用にした）。バグ調査がClaude側で完結する場合は、これまで通り`adb shell ime set`/`input tap`/`screencap`を使った非対話的な確認も有効（[mvp-completion-and-troubleshooting.md](mvp-completion-and-troubleshooting.md)参照）。
