# ページ削除機能の追加 (2026-07-02)

エディタ画面でページを追加できるのに削除できないという不足機能の対応。

## 変更の概要

ページナビゲーションバー（◀ Page / Page ▶ / + Add page / ⚙）に、現在開いているページを削除するボタン（ゴミ箱アイコン）を追加した。

## 変更ファイル

### `app/src/main/res/layout/activity_editor.xml`

歯車ボタン（`pageSettingsButton`）の左隣に `deletePageButton`（`ic_menu_delete`）を追加。

### `app/src/main/res/values/strings.xml` / `values-ja/strings.xml`

- `page_delete_content_description`：ボタンのコンテンツ説明（アクセシビリティ用）
- `page_delete_confirm`：確認ダイアログの本文

### `data/KeyboardProjectRepository.kt`

`deletePage(pageId: Long): Boolean` を追加。

- ページが1枚しかない場合は削除せず `false` を返す（キーボードは最低1ページ必要）。
- 削除時は Room の `@Delete` を呼ぶだけ。`KeyCellEntity` には `pageId` への `ForeignKey(onDelete = CASCADE)` が設定済みなので、セルはDBレベルで自動削除される。

### `ui/EditorViewModel.kt`

`deleteCurrentPage(onResult: (Boolean) -> Unit)` を追加。

- 削除が成功した場合、`currentPageIndex` を新しいページ数の範囲内に収める（最後のページを消した場合は1つ前に戻る。それ以外は同じインデックスのままで、後続のページが自動的に繰り上がる）。
- `pages.value.size` は削除前の値を使って計算する（削除後の Flow 更新は非同期のため）。

### `ui/EditorActivity.kt`

- `deletePageButton` に `showDeletePageDialog()` をバインド。
- `showDeletePageDialog()`：確認 AlertDialog を表示し、OKなら `viewModel.deleteCurrentPage` を呼ぶ。
- ページ数監視のコレクター内で `binding.deletePageButton.isEnabled = pages.size > 1` を追加。ページが1枚のときはボタンをグレーアウト。
