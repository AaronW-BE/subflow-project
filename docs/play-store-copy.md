# Play Store copy

Text that goes into Play Console, kept here so it is versioned alongside the
code it describes. Every claim below was checked against the code at the time
of writing; when the code changes, re-check the copy.

Play limits: release notes 500 characters per language; title 30; short
description 80; full description 4000.

**Every upload adds a release-notes section here, newest first.** The steps
are in `.claude/skills/play-release/SKILL.md`, and
`python tools/check_play_copy.py` must pass before anything is pasted into
the Console. Uploaded builds are tagged `v<versionName>`, so "what changed
since the last upload" is `git log <last tag>..main`.

---

## 1.5.0 (versionCode 9): release notes

Changes since 1.4.0 (versionCode 8):

Not in this upload: pt-BR, ko-KR. (Portuguese and Korean were added after
1.5.0 went up; their first release notes are 1.6.0's.)

| Change | Source |
|---|---|
| Import subscriptions from a CSV file, with a preview before anything is written | BIN-65, PR #68, #71 |
| Long-press to choose several subscriptions; pause, resume, delete, change category or reminder together | BIN-66, PR #72, #73 |
| A dashboard filter chip for every category in use; Finance, Gaming, Education, Lifestyle and News translated | BIN-69, BIN-70; PR #69, #70 |
| What's new sheet after an update, with Send feedback | BIN-60, PR #63 |
| Restoring a backup keeps to the free plan's limit (the rest come in paused) | BIN-64, PR #67 |
| Portrait only | BIN-63, PR #66 |
| Not user-visible: CI, lint clean-up, UI tests | BIN-61, BIN-62, BIN-67; PR #64, #65, #74 |

A minor release: CSV import and multi-select are both new things to find. The
paths use each app's own words: *Settings → Data → Import CSV* is 设置 → 数据 →
导入 CSV · 設定 → 資料 → 匯入 CSV · *Einstellungen → Daten → CSV importieren* ·
*Réglages → Données → Importer un CSV* · *Ajustes → Datos → Importar CSV* ·
設定 → データ → CSV をインポート. The restore limit and portrait lock are left
out of the notes: neither is something to go and find.

The in-app what's-new lines (`whats_new_item_1..3`) were rewritten for this
release from the first three bullets.

```
<en-US>
• Import subscriptions from a CSV file: Settings → Data → Import CSV. You see what will be added before anything changes.
• Long-press a subscription to select several, then pause, resume, delete, or change their category or reminder at once.
• The home screen now has a filter for every category you use.
• After an update, a short What’s new appears, with a quick way to send feedback.
</en-US>
<zh-CN>
• 可以从 CSV 文件导入订阅：设置 → 数据 → 导入 CSV。写入前会先预览将添加哪些内容。
• 长按订阅即可多选，然后一次性暂停、恢复、删除，或更改分类和提醒。
• 首页会为你用到的每个分类显示筛选标签，所有分类名称都已有中文。
• 更新后会显示简短的“更新内容”，并可直接发送反馈。
</zh-CN>
<zh-TW>
• 可以從 CSV 檔案匯入訂閱：設定 → 資料 → 匯入 CSV。寫入前會先預覽將新增哪些內容。
• 長按訂閱即可多選，然後一次暫停、恢復、刪除，或變更分類與提醒。
• 首頁會為你用到的每個分類顯示篩選標籤，所有分類名稱都已有中文。
• 更新後會顯示簡短的「更新內容」，並可直接傳送意見。
</zh-TW>
<zh-HK>
• 可以從 CSV 檔案匯入訂閱：設定 → 資料 → 匯入 CSV。寫入前會先預覽將新增哪些內容。
• 長按訂閱即可多選，然後一次暫停、恢復、刪除，或變更分類與提醒。
• 首頁會為你用到的每個分類顯示篩選標籤，所有分類名稱都已有中文。
• 更新後會顯示簡短的「更新內容」，並可直接傳送意見。
</zh-HK>
<de-DE>
• Importiere Abos aus einer CSV-Datei: Einstellungen → Daten → CSV importieren. Du siehst vorher, was hinzugefügt wird.
• Halte ein Abo gedrückt, um mehrere auszuwählen – dann pausieren, fortsetzen, löschen oder Kategorie und Erinnerung gemeinsam ändern.
• Für jede Kategorie, die du nutzt, gibt es jetzt einen Filter, und alle Kategorien sind übersetzt.
• Nach einem Update zeigt „Was ist neu“ die Änderungen, mit „Feedback senden“.
</de-DE>
<fr-FR>
• Importez des abonnements depuis un CSV : Réglages → Données → Importer un CSV. Vous voyez ce qui sera ajouté avant tout changement.
• Appuyez longuement sur un abonnement pour en sélectionner plusieurs, puis les mettre en pause, reprendre, supprimer ou changer leur catégorie ou rappel.
• Chaque catégorie utilisée a son filtre, et toutes sont traduites.
• Après une mise à jour, « Nouveautés » résume les changements.
</fr-FR>
<es-ES>
• Importa suscripciones desde un CSV: Ajustes → Datos → Importar CSV. Ves lo que se añadirá antes de cambiar nada.
• Mantén pulsada una suscripción para seleccionar varias y pausarlas, reanudarlas, eliminarlas o cambiar su categoría o recordatorio a la vez.
• Cada categoría que usas tiene su filtro, y todas están traducidas.
• Tras una actualización, «Novedades» resume los cambios, con «Enviar comentarios».
</es-ES>
<ja-JP>
• CSV ファイルからサブスクをインポートできます：設定 → データ → CSV をインポート。追加される内容を事前に確認できます。
• サブスクを長押しすると複数選択でき、停止・再開・削除やカテゴリ・通知の変更をまとめて行えます。
• 使っているカテゴリごとにフィルタが表示され、すべてのカテゴリ名が日本語になりました。
• アップデート後に「新着情報」が表示され、そこからフィードバックを送れます。
</ja-JP>
```

---

## 1.4.0 (versionCode 8): release notes

Changes since 1.3.2 (versionCode 7):

| Change | Source |
|---|---|
| Swipe right to pause or resume a subscription; swipe left still deletes | BIN-41, PR #42 |
| Resuming a cancelled trial restarts it as a paid subscription | BIN-49, PR #50 |
| Analytics: the Monthly/Annual switch changes every figure; shares add up to 100%; labelled figures | BIN-43, BIN-50, BIN-51; PR #44, #52, #53 |
| CSV export opens in Excel with accents and CJK text intact (UTF-8 BOM) | BIN-52, PR #54 |
| Restore keeps the best plan owned; buying an owned plan says "Pro restored" | BIN-54, PR #56 |
| An ended trial says "Trial ended" | BIN-55, PR #57 |
| Home: compact free-plan card when nearly full; no billing error at launch | BIN-57, BIN-59, PR #59, #61 |
| Polish: wide screens, large text, narrow phones, German labels, Save button, reminder chips, collapsed bar, add button, category row, empty filters, Settings subtitles, back to top, undo time, onboarding Skip, add-form Pro labels, paywall screen-reader labels | BIN-38–40, 42, 44–47, 53, 56, 58; PR #39–41, #43, #45–48, #51, #55, #58, #60 |
| Not user-visible: resolved-trial write normalisation, tests | BIN-48, PR #49 |

A minor release: swipe-to-pause is a new gesture to go and find. The terms are
each app's own: *Pause/Resume* is 暂停/恢复 · 暫停/恢復 · *pausieren/fortsetzen* ·
*mettre en pause/reprendre* · *pausar/reanudar* · 停止/再開; *Undo* is 撤销 · 復原 ·
*Widerrufen* · *Annuler* · *Deshacer* · 元に戻す; the Analytics switch is
按月/按年 · 依月/依年 · *Monatlich/Jährlich* · *Mensuel/Annuel* · *Mensual/Anual* ·
月間/年間.

```
<en-US>
• Swipe a subscription right to pause it, and right again to resume. Swiping left still deletes it, with Undo.
• Analytics: switching between Monthly and Annual now changes every figure, and category shares always add up to 100%.
• CSV reports now open in Excel with accents and other characters intact.
• Resuming a cancelled trial now restarts it as a paid subscription.
• Clearer layouts with large text, on small phones and on tablets.
</en-US>
<zh-CN>
• 向右滑动订阅即可暂停，再向右滑动即可恢复；向左滑动仍是删除，并可撤销。
• 分析：在“按月”和“按年”之间切换时，所有数字都会随之变化，各分类占比加起来始终是 100%。
• 导出的 CSV 报表用 Excel 打开时，重音字母和中文不再乱码。
• 恢复一个已取消的试用时，会直接按付费订阅恢复。
• 大字体、小屏手机和平板上的排版更清晰。
</zh-CN>
<zh-TW>
• 將訂閱向右滑動即可暫停，再向右滑動即可恢復；向左滑動仍是刪除，並可復原。
• 分析：在「依月」和「依年」之間切換時，所有數字都會跟著變動，各分類占比加總一定是 100%。
• 匯出的 CSV 報表用 Excel 開啟時，重音字母和中文不再變成亂碼。
• 恢復已取消的試用時，會直接以付費訂閱恢復。
• 在大字體、小螢幕手機和平板上，版面更清楚。
</zh-TW>
<zh-HK>
• 將訂閱向右滑動即可暫停，再向右滑動即可恢復；向左滑動仍是刪除，並可復原。
• 分析：在「依月」和「依年」之間切換時，所有數字都會跟著變動，各分類占比加總一定是 100%。
• 匯出的 CSV 報表用 Excel 開啟時，重音字母和中文不再變成亂碼。
• 恢復已取消的試用時，會直接以付費訂閱恢復。
• 在大字體、小螢幕手機和平板上，版面更清楚。
</zh-HK>
<de-DE>
• Wische ein Abo nach rechts, um es zu pausieren, und erneut nach rechts, um es fortzusetzen. Nach links löschst du es weiterhin – mit „Widerrufen“.
• Analyse: „Monatlich“/„Jährlich“ ändert jetzt alle Zahlen, und die Kategorieanteile ergeben immer 100 %.
• CSV-Berichte zeigen in Excel jetzt Umlaute korrekt an.
• Eine gekündigte Testphase wird beim Fortsetzen jetzt als bezahltes Abo fortgesetzt.
• Klareres Layout bei großer Schrift, auf kleinen Handys und Tablets.
</de-DE>
<fr-FR>
• Balayez un abonnement vers la droite pour le mettre en pause, puis encore pour le reprendre. Vers la gauche, il est toujours supprimé, avec « Annuler ».
• Analyse : « Mensuel »/« Annuel » change désormais tous les chiffres, et les parts par catégorie font toujours 100 %.
• Les rapports CSV s’ouvrent dans Excel avec les accents intacts.
• Reprendre un essai résilié le reprend désormais en abonnement payant.
• Mise en page plus claire en grand texte, sur petits téléphones et tablettes.
</fr-FR>
<es-ES>
• Desliza una suscripción a la derecha para pausarla y otra vez para reanudarla. A la izquierda se sigue eliminando, con «Deshacer».
• Análisis: «Mensual»/«Anual» ahora cambia todas las cifras, y las partes por categoría siempre suman 100 %.
• Los informes CSV se abren en Excel con los acentos intactos.
• Al reanudar una prueba cancelada, se reanuda como suscripción de pago.
• Diseño más claro con texto grande, en móviles pequeños y en tablets.
</es-ES>
<ja-JP>
• サブスクを右にスワイプすると停止、もう一度右にスワイプすると再開できます。左スワイプは引き続き削除で、「元に戻す」も使えます。
• 分析：「月間」と「年間」を切り替えると、すべての数値が切り替わるようになりました。カテゴリの割合は常に合計 100% です。
• CSV レポートを Excel で開いても、日本語やアクセント記号が文字化けしなくなりました。
• 解約したトライアルを再開すると、有料のサブスクとして再開されます。
• 大きな文字、小さな画面、タブレットでのレイアウトを改善しました。
</ja-JP>
```

---

## 1.3.2 (versionCode 7): release notes

Changes since 1.3.1 (versionCode 6):

| Change | Source |
|---|---|
| Settings pages: back button and page title on one row | BIN-36, PR #36 |
| Contact support writes to bin-tech@outlook.com | BIN-37, PR #37 |

A patch release: a tighter header and a new support address, nothing new to go
and find. The terms are each app's own: *Settings* is 设置 / 設定 / *Einstellungen* /
*Réglages* / *Ajustes* / 設定, and *Contact support* is 联系支持 / 聯絡客服 /
*Support kontaktieren* / *Contacter le support* / *Contactar con soporte* /
サポートに連絡.

```
<en-US>
• Settings pages now show the back button and the page title on one row, leaving more room for the settings themselves.
• Contact support now writes to our new address, bin-tech@outlook.com.
</en-US>
<zh-CN>
• 设置里的各个页面，返回按钮和标题现在在同一行，腾出更多空间显示设置项。
• “联系支持”现在会发邮件到新地址 bin-tech@outlook.com。
</zh-CN>
<zh-TW>
• 設定裡的各個頁面，返回按鈕和標題現在位於同一列，騰出更多空間顯示設定項目。
• 「聯絡客服」現在會寄信到新地址 bin-tech@outlook.com。
</zh-TW>
<zh-HK>
• 設定裡的各個頁面，返回按鈕和標題現在位於同一列，騰出更多空間顯示設定項目。
• 「聯絡客服」現在會寄信到新地址 bin-tech@outlook.com。
</zh-HK>
<de-DE>
• In den Einstellungen stehen Zurück-Taste und Seitentitel jetzt in einer Zeile – so bleibt mehr Platz für die Einstellungen selbst.
• „Support kontaktieren“ schreibt jetzt an unsere neue Adresse bin-tech@outlook.com.
</de-DE>
<fr-FR>
• Dans les Réglages, le bouton retour et le titre de la page sont désormais sur une seule ligne, ce qui laisse plus de place aux réglages.
• « Contacter le support » écrit désormais à notre nouvelle adresse, bin-tech@outlook.com.
</fr-FR>
<es-ES>
• En Ajustes, el botón de volver y el título de la página ahora están en una sola fila, lo que deja más espacio para los ajustes.
• «Contactar con soporte» ahora escribe a nuestra nueva dirección, bin-tech@outlook.com.
</es-ES>
<ja-JP>
• 設定の各ページで、戻るボタンとページタイトルが1行にまとまり、設定項目を表示できる領域が広がりました。
• 「サポートに連絡」の宛先が新しいアドレス bin-tech@outlook.com になりました。
</ja-JP>
```

---

## 1.3.1 (versionCode 6): release notes

Changes since 1.3.0 (versionCode 5):

| Change | Source |
|---|---|
| The widget updates straight away after an edit, even several in a row | BIN-33, PR #30 |
| The widget uses the language chosen in the app, not the phone's | BIN-34, PR #32 |
| The launcher's widget picker shows a preview instead of the app icon | BIN-35, PR #34 |

Two fixes and a small improvement to the widget, so still a patch release. "Language" is each app's own word for the setting
(语言 / 語言 / *Sprache* / *langue* / *idioma* / 言語). The widget terms are the ones the 1.3.0 notes introduced:
桌面小组件 / 主畫面小工具 / *Widget für den Startbildschirm* / *widget de l'écran
d'accueil* / *widget de la pantalla de inicio* / ホーム画面ウィジェット.

```
<en-US>
• Fixed: the home screen widget now updates straight away after you edit, pause or resume a subscription, even when you make several changes in a row.
• Fixed: the home screen widget now uses the language you chose in the app, not your phone's.
• The widget list on your home screen now shows a preview of the SubFlow widget.
</en-US>
<zh-CN>
• 修复：连续编辑、暂停或恢复订阅时，桌面小组件现在会立即更新。
• 修复：桌面小组件现在使用你在应用中选择的语言，而不是手机的系统语言。
• 添加桌面小组件时，列表里现在会显示 SubFlow 小组件的预览。
</zh-CN>
<zh-TW>
• 修正：連續編輯、暫停或恢復訂閱時，主畫面小工具現在會立即更新。
• 修正：主畫面小工具現在會使用你在應用程式中選擇的語言，而不是手機的系統語言。
• 新增主畫面小工具時，清單中現在會顯示 SubFlow 小工具的預覽。
</zh-TW>
<zh-HK>
• 修正：連續編輯、暫停或恢復訂閱時，主畫面小工具現在會立即更新。
• 修正：主畫面小工具現在會使用你在應用程式中選擇的語言，而不是手機的系統語言。
• 新增主畫面小工具時，清單中現在會顯示 SubFlow 小工具的預覽。
</zh-HK>
<de-DE>
• Behoben: Das Widget auf dem Startbildschirm aktualisiert sich jetzt sofort, wenn du ein Abo bearbeitest, pausierst oder fortsetzt – auch bei mehreren Änderungen hintereinander.
• Behoben: Das Widget auf dem Startbildschirm verwendet jetzt die Sprache, die du in der App gewählt hast, nicht die deines Geräts.
• Beim Hinzufügen von Widgets zeigt die Liste jetzt eine Vorschau des SubFlow-Widgets.
</de-DE>
<fr-FR>
• Corrigé : le widget de l'écran d'accueil se met désormais à jour immédiatement quand vous modifiez, mettez en pause ou reprenez un abonnement, même après plusieurs changements d'affilée.
• Corrigé : le widget de l'écran d'accueil utilise désormais la langue choisie dans l'app, et non celle de votre téléphone.
• La liste des widgets de l'écran d'accueil affiche désormais un aperçu du widget SubFlow.
</fr-FR>
<es-ES>
• Corregido: el widget de la pantalla de inicio ahora se actualiza al instante cuando editas, pausas o reanudas una suscripción, incluso tras varios cambios seguidos.
• Corregido: el widget de la pantalla de inicio ahora usa el idioma que elegiste en la app, no el de tu móvil.
• La lista de widgets de la pantalla de inicio ahora muestra una vista previa del widget de SubFlow.
</es-ES>
<ja-JP>
• 修正：登録を続けて編集・停止・再開したときも、ホーム画面ウィジェットがすぐに更新されるようになりました。
• 修正：ホーム画面ウィジェットが、端末の言語ではなくアプリで選んだ言語で表示されるようになりました。
• ホーム画面にウィジェットを追加するとき、一覧に SubFlow ウィジェットのプレビューが表示されるようになりました。
</ja-JP>
```

---

## 1.3.0 (versionCode 5): release notes

Changes since 1.2.0 (versionCode 4):

| Change | Source |
|---|---|
| Pause a subscription instead of deleting it | BIN-29, PR #25 |
| Home screen widget: monthly total and the renewals coming up | BIN-30, PR #26; BIN-32, PR #28 |
| Currency names in the interface language | BIN-28, PR #24 |
| *Not in the notes:* store screenshots and README | BIN-31, PR #27 |

Paste the whole block into **Release notes**; the Console accepts every tag
in the one box. `zh-HK` reuses the Taiwan text. The terms are the app's own:
*Paused* / 已暂停 / 已暫停 / *Pausiert* / *En pause* / *En pausa* / 停止中, and
Japanese calls a subscription 登録 on the detail screen.

```
<en-US>
• New: pause a subscription you've stopped paying for instead of deleting it. It keeps its details, sends no reminders and stays out of your totals.
• New: a home screen widget with your monthly total and the renewals coming up.
• Currency names now appear in your language.
</en-US>
<zh-CN>
• 新增：不再付费的订阅可以暂停，而不必删除。已暂停的订阅会保留记录，不再提醒，也不计入总支出。
• 新增：桌面小组件，显示每月总支出和即将到来的续费。
• 货币名称现在会以你使用的语言显示。
</zh-CN>
<zh-TW>
• 新增：不再付費的訂閱可以暫停，不必刪除。已暫停的訂閱會保留紀錄、不再提醒，也不會計入總支出。
• 新增：主畫面小工具，顯示每月總支出與即將到來的續費。
• 貨幣名稱現在會以你使用的語言顯示。
</zh-TW>
<zh-HK>
• 新增：不再付費的訂閱可以暫停，不必刪除。已暫停的訂閱會保留紀錄、不再提醒，也不會計入總支出。
• 新增：主畫面小工具，顯示每月總支出與即將到來的續費。
• 貨幣名稱現在會以你使用的語言顯示。
</zh-HK>
<de-DE>
• Neu: Pausiere ein Abo, das du nicht mehr bezahlst, statt es zu löschen. Es behält seine Daten, erinnert dich nicht mehr und zählt nicht zu deinen Summen.
• Neu: Ein Widget für den Startbildschirm mit deinen monatlichen Gesamtausgaben und den anstehenden Verlängerungen.
• Währungsnamen erscheinen jetzt in deiner Sprache.
</de-DE>
<fr-FR>
• Nouveau : mettez en pause un abonnement que vous ne payez plus, au lieu de le supprimer. Il garde ses informations, n'envoie plus de rappel et sort de vos totaux.
• Nouveau : un widget pour l'écran d'accueil, avec votre dépense mensuelle totale et les prochains renouvellements.
• Les noms des devises s'affichent désormais dans votre langue.
</fr-FR>
<es-ES>
• Nuevo: pausa una suscripción que ya no pagas en lugar de eliminarla. Conserva sus datos, no envía avisos y no cuenta en tus totales.
• Nuevo: un widget para la pantalla de inicio con tu gasto mensual total y las próximas renovaciones.
• Los nombres de las monedas ahora aparecen en tu idioma.
</es-ES>
<ja-JP>
• 支払いをやめた登録を、削除せずに停止できるようになりました。停止中の登録は内容を残したまま、通知が届かず、合計にも含まれません。
• ホーム画面ウィジェットを追加しました。月あたりの合計とこれからの更新を表示します。
• 通貨名がお使いの言語で表示されるようになりました。
</ja-JP>
```

---

## 1.2.0 (versionCode 4): release notes

Changes since 1.1.0 (versionCode 3):

| Change | Source |
|---|---|
| Traditional Chinese, chosen automatically on TW/HK/MO devices | BIN-27, PR #22 |
| Settings split into four pages | BIN-21, PR #21 |
| Home currency pinned to the device region, no longer moved by the interface language | BIN-25, PR #20 |
| Rate date shown under the currency picker | BIN-22, PR #19 |

**Which languages to paste.** Paste the whole block below into **Release
notes** in Play Console. It recognises every language tag in the one box
(confirmed in the Console on 2026-09-10). The box pre-fills a tag only for
languages the store listing already has, so on an English-only listing it
shows just `<en-US>`, but the other tags are still accepted. `zh-HK` reuses
the Taiwan text.

The page names in each language are the ones the app itself shows, e.g.
German *Allgemein*, not *Einstellungen*, because that is the tab's name.

```
<en-US>
• New: Traditional Chinese. Phones set to Chinese (Taiwan, Hong Kong or Macau) get it automatically.
• Settings is now four short pages: Preferences, Notifications, Data and About.
• Your home currency no longer changes when you switch the app's language, and new installs start in your region's currency.
• The currency picker now shows when exchange rates were last updated.
</en-US>
<zh-CN>
• 新增繁体中文。系统语言为中文（台湾、香港、澳门）的手机会自动使用繁体。
• 设置页重新整理为四个分页：偏好设置、通知、数据、关于。
• 切换应用语言后，本币不会再跟着改变；新安装时会按所在地区选择本币。
• 选择本币时可以看到汇率的更新日期。
</zh-CN>
<zh-TW>
• 新增繁體中文。系統語言為中文（台灣、香港、澳門）的手機會自動使用繁體。
• 設定頁重新整理為四個分頁：偏好設定、通知、資料、關於。
• 切換應用程式語言後，主要貨幣不會再跟著改變；新安裝時會依所在地區選擇主要貨幣。
• 選擇主要貨幣時可以看到匯率的更新日期。
</zh-TW>
<zh-HK>
• 新增繁體中文。系統語言為中文（台灣、香港、澳門）的手機會自動使用繁體。
• 設定頁重新整理為四個分頁：偏好設定、通知、資料、關於。
• 切換應用程式語言後，主要貨幣不會再跟著改變；新安裝時會依所在地區選擇主要貨幣。
• 選擇主要貨幣時可以看到匯率的更新日期。
</zh-HK>
<de-DE>
• Neu: Traditionelles Chinesisch. Geräte, die auf Chinesisch (Taiwan, Hongkong oder Macau) eingestellt sind, nutzen es automatisch.
• Die Einstellungen sind jetzt vier kurze Seiten: Allgemein, Mitteilungen, Daten und Über.
• Deine Hauptwährung ändert sich nicht mehr, wenn du die Sprache der App wechselst. Neue Installationen starten mit der Währung deiner Region.
• Die Währungsauswahl zeigt jetzt, wann die Wechselkurse zuletzt aktualisiert wurden.
</de-DE>
<fr-FR>
• Nouveau : chinois traditionnel. Les téléphones réglés en chinois (Taïwan, Hong Kong ou Macao) l'utilisent automatiquement.
• Les réglages tiennent désormais en quatre pages : Préférences, Notifications, Données et À propos.
• Votre devise principale ne change plus quand vous changez la langue de l'app, et une nouvelle installation démarre dans la devise de votre région.
• Le choix de la devise indique la date de mise à jour des taux de change.
</fr-FR>
<es-ES>
• Nuevo: chino tradicional. Los teléfonos configurados en chino (Taiwán, Hong Kong o Macao) lo usan automáticamente.
• Los ajustes ahora son cuatro páginas cortas: Preferencias, Notificaciones, Datos y Acerca de.
• Tu moneda principal ya no cambia al cambiar el idioma de la app, y las instalaciones nuevas empiezan con la moneda de tu región.
• El selector de moneda muestra cuándo se actualizaron los tipos de cambio.
</es-ES>
<ja-JP>
• 繁体字中国語に対応しました。中国語（台湾・香港・マカオ）に設定された端末では自動的に表示されます。
• 設定を「環境設定」「通知」「データ」「アプリについて」の4ページに整理しました。
• アプリの言語を切り替えても基準通貨が変わらなくなりました。新規インストール時は地域の通貨から始まります。
• 通貨の選択画面に為替レートの更新日を表示するようにしました。
</ja-JP>
```

---

## Store listings

One listing per language. Add each under **Main store listing → Manage
translations**, then paste its three fields. Hong Kong (`zh-HK`) uses the zh-TW
text.

**With the 1.6.0 upload (first build with Portuguese and Korean):** add the
pt-BR and ko-KR listings below, and add both languages to the language line
of every other listing (*In English, … and Spanish*). Until that build is live, the other
listings must not claim it.

All seven listings say the same thing. zh-TW was written first from the
features in the code (see *What each claim rests on*), and the others
translate it. Terms match what each language's app UI shows (German
*Hauptwährung*, French *devise principale*, Japanese *基準通貨*, Simplified
Chinese *本币*). The address matches the app too: du in German, vous in French,
tú in Spanish, です/ます in Japanese.

The en-US listing already exists in the Console, and its text isn't in this
repo. The en-US version below matches the others. Use it to replace the
current one if that one is out of date (see the last section: the 2026-08-28
draft said 10 currencies). The title is unchanged.

### en-US

**Title** (≤30)

```
SubFlow: Subscription Tracker
```

**Short description** (≤80)

```
All your subscriptions in one place, with a reminder before every charge.
```

**Full description** (≤4000)

```
Netflix, Spotify, cloud storage, the gym… what do your subscriptions actually add up to each month? SubFlow keeps them in one place, turns them into a real monthly total, and reminds you before you're charged, while there's still time to cancel what you no longer use.

TRACK EVERY SUBSCRIPTION
• 34 popular services built in, with their logos: pick one and it's added
• Or create your own, with a custom name, color and icon
• Weekly, monthly, quarterly and yearly billing, all converted into monthly and yearly totals
• Sort by renewal date, price or name; filter by category and search
• Swipe a card left to delete it, or right to pause it
• Long-press to select several and pause, resume, delete or recategorize them at once
• Pause a subscription you've stopped paying for: it keeps its details but drops out of your totals and reminders
• A home screen widget shows your monthly total and the renewals coming up

A REMINDER BEFORE YOU'RE CHARGED
• A reminder 1 day before each renewal (Pro adds 3 and 7 days)
• If your phone missed a reminder while it was idle, opening the app catches it up

TRACK FREE TRIALS
• Mark a subscription as a free trial, with its end date and the price that follows
• Get reminded before the trial ends, on every plan
• When it ends, record whether it became paid, you cancelled it, or it was extended

MANY CURRENCIES, ONE TOTAL
• 40 currencies: record each subscription in the currency it's billed in
• Everything is converted into your home currency at live exchange rates. Offline, the last rates fetched are used, or built-in rates if the app has never been online

SPENDING ANALYSIS
• Monthly total, yearly total and average per day
• Pro: 6-month spending history, breakdown by category, spend map, a month-by-month billing forecast, and CSV export

YOUR DATA STAYS ON YOUR PHONE
• No account needed: everything is stored on your device
• Export a JSON backup and restore it at any time
• Import subscriptions from a CSV file — your own export or a spreadsheet — with a preview before anything is added
• Dark mode (true black) and haptic feedback
• In English, Simplified Chinese, Traditional Chinese, Japanese, German, French and Spanish

SUBFLOW PRO
The free plan tracks up to 5 subscriptions and shows a banner ad. Pro gives you:
• Unlimited subscriptions
• Reminders 3 and 7 days before renewal
• The full spending analysis and CSV export
• A monthly budget, with a meter on your total and a heads-up before a subscription takes you over
• No ads, ever
Choose monthly, annual or a one-time lifetime purchase, all billed through Google Play. Subscriptions can be cancelled at any time.

SubFlow tracks and reminds; it doesn't cancel anything for you. To cancel, go to the service's account page. For well-known services, the app opens it for you.
```

### pt-BR Português (Brasil)

Added with BIN-75, for the first build that ships `values-pt` (1.6.0). Add it
in the Console with that upload, not before: until then the app itself would
not be in Portuguese. Address is você, as in the app; terms match its UI
(*assinatura*, *cobrança*, *moeda principal*, *teste grátis*).

**Título do app** (≤30)

```
SubFlow: Gerenciar Assinaturas
```

**Descrição curta** (≤80)

```
Todas as suas assinaturas em um só lugar, com um aviso antes de cada cobrança.
```

**Descrição completa** (≤4000)

```
Netflix, Spotify, armazenamento na nuvem, academia… quanto suas assinaturas somam de verdade por mês? O SubFlow reúne todas em um só lugar, mostra o total mensal real e avisa antes da cobrança, enquanto ainda dá tempo de cancelar o que você não usa mais.

ACOMPANHE TODAS AS ASSINATURAS
• 34 serviços populares já incluídos, com logo: escolha um e pronto
• Ou crie o seu, com nome, cor e ícone próprios
• Cobrança semanal, mensal, trimestral e anual, tudo convertido em totais por mês e por ano
• Ordene por data de renovação, preço ou nome; filtre por categoria e busque
• Deslize um cartão para a esquerda para excluir, ou para a direita para pausar
• Toque e segure para selecionar várias e pausar, retomar, excluir ou mudar a categoria de uma vez
• Pause uma assinatura que você parou de pagar: os dados ficam, mas ela sai dos totais e dos lembretes
• Um widget na tela inicial mostra o total do mês e as próximas renovações

UM AVISO ANTES DA COBRANÇA
• Um lembrete 1 dia antes de cada renovação (o Pro inclui 3 e 7 dias)
• Se o celular perdeu um lembrete enquanto estava parado, abrir o app recupera o aviso

ACOMPANHE TESTES GRÁTIS
• Marque uma assinatura como teste grátis, com a data de término e o preço que vem depois
• Receba um lembrete antes do fim do teste, em todos os planos
• Quando terminar, registre se virou pago, se você cancelou ou se foi estendido

VÁRIAS MOEDAS, UM SÓ TOTAL
• 40 moedas: registre cada assinatura na moeda em que é cobrada
• Tudo é convertido para a sua moeda principal com cotações atualizadas. Offline, usa as últimas cotações baixadas, ou cotações embutidas se o app nunca esteve online

ANÁLISE DE GASTOS
• Total mensal, total anual e média por dia
• Pro: histórico de 6 meses, gastos por categoria, mapa de gastos, previsão de cobranças mês a mês e exportação em CSV

SEUS DADOS FICAM NO SEU CELULAR
• Não precisa de conta: tudo fica salvo no aparelho
• Exporte um backup em JSON e restaure quando quiser
• Importe assinaturas de um arquivo CSV — sua própria exportação ou uma planilha — com uma prévia antes de adicionar qualquer coisa
• Modo escuro (preto de verdade) e resposta tátil
• Em português, inglês, chinês simplificado, chinês tradicional, japonês, alemão, francês e espanhol

SUBFLOW PRO
O plano gratuito acompanha até 5 assinaturas e mostra um banner de anúncio. O Pro oferece:
• Assinaturas ilimitadas
• Lembretes 3 e 7 dias antes da renovação
• A análise de gastos completa e a exportação em CSV
• Um orçamento mensal, com um medidor no seu total e um aviso antes que uma assinatura passe do limite
• Sem anúncios, nunca
Escolha mensal, anual ou uma compra vitalícia única, tudo cobrado pelo Google Play. As assinaturas podem ser canceladas a qualquer momento.

O SubFlow acompanha e avisa; ele não cancela nada por você. Para cancelar, acesse a página da conta no serviço. Nos serviços mais conhecidos, o app abre essa página para você.
```

### ko-KR 한국어

Added with BIN-76, for the first build that ships `values-ko`. Add it in the
Console with that upload, not before. Sentences in 해요체 as in the app; terms
match its UI (*구독*, *결제*, *갱신*, *기본 통화*, *무료 체험*).

**앱 이름** (≤30)

```
SubFlow: 구독 관리 및 결제 알림
```

**간단한 설명** (≤80)

```
모든 구독을 한곳에서. 결제되기 전에 알려 드려요.
```

**자세한 설명** (≤4000)

```
Netflix, Spotify, 클라우드 저장공간, 헬스장… 구독료를 다 합치면 한 달에 얼마일까요? SubFlow는 구독을 한곳에 모아 실제 월 합계를 보여 주고, 결제되기 전에 알려 드려요. 더 이상 쓰지 않는 구독을 해지할 시간이 아직 있을 때요.

모든 구독을 관리
• 인기 서비스 34개가 로고와 함께 준비되어 있어요. 고르기만 하면 추가돼요
• 이름, 색상, 아이콘을 직접 정해 만들 수도 있어요
• 매주, 매월, 분기별, 매년 결제를 모두 월·연 합계로 환산
• 갱신일, 가격, 이름순 정렬, 카테고리 필터와 검색
• 카드를 왼쪽으로 밀면 삭제, 오른쪽으로 밀면 일시정지
• 길게 눌러 여러 개를 선택하고 한 번에 일시정지, 다시 시작, 삭제, 카테고리 변경
• 결제를 멈춘 구독은 일시정지: 정보는 남기고 합계와 알림에서만 빠져요
• 홈 화면 위젯으로 이번 달 합계와 다가오는 갱신을 확인

결제 전에 알림
• 매 갱신 하루 전에 알림 (Pro는 3일 전·7일 전 추가)
• 휴대폰이 대기 중이라 알림을 놓쳤다면 앱을 열 때 다시 알려 드려요

무료 체험 관리
• 구독을 무료 체험으로 표시하고 종료일과 이후 가격을 기록
• 체험이 끝나기 전에 알림, 모든 플랜에서 제공
• 끝나면 유료 전환, 해지, 연장 중 어떻게 됐는지 기록

여러 통화, 하나의 합계
• 40개 통화: 실제로 결제되는 통화로 각 구독을 기록
• 모두 실시간 환율로 기본 통화로 환산돼요. 오프라인일 때는 마지막으로 받은 환율을, 한 번도 온라인이었던 적이 없으면 내장 환율을 사용해요

지출 분석
• 월 합계, 연 합계, 하루 평균
• Pro: 6개월 지출 추이, 카테고리별 분석, 지출 지도, 월별 결제 예측, CSV 내보내기

데이터는 휴대폰에
• 계정 필요 없음: 모든 데이터는 기기에 저장돼요
• JSON 백업을 내보내고 언제든 복원
• CSV 파일(직접 내보낸 파일이나 스프레드시트)에서 구독을 가져오기, 추가 전에 미리보기 제공
• 다크 모드(완전한 검은색)와 햅틱 피드백
• 한국어, 영어, 중국어 간체, 중국어 번체, 일본어, 독일어, 프랑스어, 스페인어, 포르투갈어 지원

SUBFLOW PRO
무료 플랜은 구독 5개까지 관리할 수 있고 배너 광고가 표시돼요. Pro에서는:
• 구독 무제한
• 갱신 3일 전·7일 전 알림
• 전체 지출 분석과 CSV 내보내기
• 월 예산: 합계 옆에 예산 게이지를 보여 주고, 구독으로 예산을 넘기 전에 알려 드려요
• 광고 없음
월간, 연간, 평생 1회 구매 중에서 고를 수 있으며 모두 Google Play로 결제돼요. 구독은 언제든 해지할 수 있어요.

SubFlow는 관리하고 알려 드릴 뿐, 대신 해지하지는 않아요. 해지하려면 해당 서비스의 계정 페이지로 가세요. 잘 알려진 서비스는 앱에서 바로 열어 드려요.
```

### zh-CN 中文（简体）

**应用名称**（≤30）

```
SubFlow：订阅管理与续费提醒
```

**简短说明**（≤80）

```
集中管理所有订阅，扣费前提醒你取消，一眼看清每月到底花了多少。
```

**完整说明**（≤4000）

```
Netflix、Spotify、云存储、健身房……每个月的订阅费加起来到底有多少？SubFlow 帮你把所有订阅集中在一处，折算成真实的每月总额，并在扣费之前提醒你，让你还来得及取消用不到的服务。

【集中管理所有订阅】
• 内置 34 个热门服务，自带图标，选中即可添加
• 也可以自定义服务名称、颜色和图标
• 支持每周、每月、每季度、每年四种计费周期，自动折算成每月和每年支出
• 按续费日期、价格或名称排序，可按分类筛选和搜索
• 向左滑动卡片即可删除，向右滑动即可暂停
• 长按可多选，一次性暂停、恢复、删除或更改分类
• 不再付费的订阅可以暂停：记录保留，但不再提醒，也不计入总支出
• 桌面小组件，随时查看每月总支出和即将到来的续费

【扣费前提醒你】
• 续费前 1 天提醒（Pro 可再增加提前 3 天和 7 天）
• 即使手机有一段时间没能发出提醒，打开应用时也会补发错过的提醒

【追踪免费试用】
• 把订阅标记为免费试用，记下试用结束日期和之后的价格
• 试用结束前提醒你，所有版本都包含
• 试用结束后，可以记录它是转为付费、已取消，还是延长了试用

【多币种，统一折算】
• 支持 40 种货币，每个订阅都可以用实际扣费的币种记录
• 按实时汇率统一折算成你的本币；无法联网时沿用最近一次获取的汇率，从未联网过则使用内置汇率

【支出分析】
• 每月总支出、年度支出折算、每天平均
• Pro：近 6 个月支出趋势、分类占比、支出面积图、未来每月账单预测，以及 CSV 报表导出

【你的数据只留在你的手机上】
• 无需注册账号，所有订阅数据都存储在本机
• 可导出 JSON 备份，随时从备份恢复
• 可从 CSV 文件导入订阅（本应用导出的文件或自己做的表格），添加前会先预览
• 支持深色模式（纯黑）和触感反馈
• 界面支持简体中文、繁体中文、英语、日语、德语、法语和西班牙语

【SubFlow Pro】
免费版可记录 5 个订阅，并显示横幅广告。升级 Pro 可以：
• 不限订阅数量
• 增加提前 3 天和 7 天的续费提醒
• 解锁完整的支出分析和 CSV 导出
• 月度预算：总支出旁显示进度条，订阅将超出预算时提前提醒
• 永久去除广告
提供月付、年付和买断三种方案，均通过 Google Play 付款，订阅方案可随时取消。

SubFlow 是记录与提醒工具，不会替你取消任何服务。要取消订阅，请前往该服务的账户页面；常见服务可以直接从应用中打开。
```

### zh-TW 中文（台灣）, also used for zh-HK

**應用程式名稱**（≤30）

```
SubFlow：訂閱管理與續訂提醒
```

**簡短說明**（≤80）

```
集中管理所有訂閱，扣款前提醒你取消，一眼看清每月真正花了多少。
```

**完整說明**（≤4000）

```
Netflix、Spotify、雲端空間、健身房……每個月的訂閱費用加起來到底有多少？SubFlow 幫你把所有訂閱集中在一個地方，換算成真實的每月總額，並在扣款之前提醒你，讓你還來得及取消用不到的服務。

【集中管理所有訂閱】
• 內建 34 項熱門服務，附官方標誌，選好就能直接加入
• 也可以自訂服務名稱、顏色與圖示
• 支援每週、每月、每季、每年四種計費週期，自動換算成每月與每年支出
• 依續訂日期、價格或名稱排序，並可依類別篩選與搜尋
• 向左滑動卡片即可刪除，向右滑動即可暫停
• 長按可多選，一次暫停、恢復、刪除或變更分類
• 不再付費的訂閱可以暫停：保留紀錄，但不再提醒，也不計入總支出
• 主畫面小工具，隨時查看每月總支出與即將到來的續訂

【扣款前提醒你】
• 續訂前 1 天提醒（Pro 版可再增加提前 3 天與 7 天）
• 即使裝置有一段時間沒有執行，打開應用程式時也會補發錯過的提醒

【追蹤免費試用】
• 將訂閱標記為免費試用，記下試用結束日與之後的價格
• 試用結束前提醒你，所有版本都包含此功能
• 試用到期後，可以記錄它是轉為付費、已取消，還是延長了試用

【多幣別，統一換算】
• 支援 40 種貨幣，每筆訂閱可用實際扣款的貨幣記錄
• 依即時匯率統一換算成你的主要貨幣；無法連線時沿用最近一次取得的匯率，從未連線過則使用內建匯率

【支出分析】
• 每月總支出、年度支出換算、每日平均
• Pro 版：近 6 個月支出趨勢、類別佔比、支出面積圖、未來每月帳單預測，以及 CSV 報表匯出

【你的資料留在你的手機上】
• 不需要註冊帳號，所有訂閱資料都儲存在本機
• 可匯出 JSON 備份，並隨時從備份還原
• 可從 CSV 檔案匯入訂閱（本 App 匯出的檔案或自己做的試算表），新增前會先預覽
• 支援深色模式（純黑）與觸覺回饋
• 介面支援繁體中文、簡體中文、英文、日文、德文、法文與西班牙文

【SubFlow Pro】
免費版可記錄 5 筆訂閱，並顯示橫幅廣告。升級 Pro 可以：
• 不限訂閱數量
• 增加提前 3 天與 7 天的續訂提醒
• 解鎖完整的支出分析與 CSV 匯出
• 每月預算：總支出旁顯示進度條，訂閱將超出預算時事先提醒
• 永久移除廣告
提供月繳、年繳與一次買斷方案，皆透過 Google Play 付款，訂閱方案可隨時取消。

SubFlow 是記錄與提醒工具，不會替你取消任何服務。要取消訂閱，請前往該服務的帳號頁面；常見服務可以直接從應用程式開啟。
```

### de-DE Deutsch

**Titel** (≤30)

```
SubFlow: Abos & Erinnerungen
```

**Kurzbeschreibung** (≤80)

```
Alle Abos an einem Ort – mit einer Erinnerung vor jeder Abbuchung.
```

**Vollständige Beschreibung** (≤4000)

```
Netflix, Spotify, Cloud-Speicher, Fitnessstudio … was kosten deine Abos eigentlich zusammen im Monat? SubFlow sammelt sie an einem Ort, rechnet sie in eine echte Monatssumme um und erinnert dich vor der Abbuchung – solange Kündigen noch nichts kostet.

ALLE ABOS IM BLICK
• 34 beliebte Dienste mit Logo schon eingebaut: auswählen, fertig
• Oder eigene Dienste mit Name, Farbe und Symbol anlegen
• Wöchentliche, monatliche, vierteljährliche und jährliche Abrechnung, umgerechnet in Monats- und Jahressummen
• Nach Verlängerungsdatum, Preis oder Name sortieren, nach Kategorie filtern und suchen
• Karte nach links wischen zum Löschen, nach rechts zum Pausieren
• Lange drücken, um mehrere auszuwählen und gemeinsam zu pausieren, fortzusetzen, zu löschen oder neu einzuordnen
• Ein Abo, das du nicht mehr bezahlst, pausieren: Die Daten bleiben, aber es zählt nicht mehr zu den Summen und erinnert dich nicht
• Ein Widget für den Startbildschirm zeigt deine Monatssumme und die anstehenden Verlängerungen

ERINNERUNG VOR DER ABBUCHUNG
• Eine Erinnerung 1 Tag vor jeder Verlängerung (Pro ergänzt 3 und 7 Tage)
• Hat dein Gerät eine Erinnerung verpasst, holt die App sie beim Öffnen nach

KOSTENLOSE TESTPHASEN
• Ein Abo als Testphase markieren, mit Enddatum und dem Preis danach
• Erinnerung vor dem Ende der Testphase, in jeder Version
• Danach festhalten, ob es kostenpflichtig wurde, du gekündigt hast oder die Testphase verlängert wurde

VIELE WÄHRUNGEN, EINE SUMME
• 40 Währungen: jedes Abo in der Währung erfassen, in der es abgebucht wird
• Alles wird zu aktuellen Kursen in deine Hauptwährung umgerechnet. Offline gelten die zuletzt geladenen Kurse, ohne jede Verbindung die eingebauten

AUSGABEN AUSWERTEN
• Monatssumme, Jahressumme und Durchschnitt pro Tag
• Pro: Verlauf der letzten 6 Monate, Aufteilung nach Kategorie, Ausgabenkarte, Rechnungsprognose Monat für Monat und CSV-Export

DEINE DATEN BLEIBEN AUF DEINEM GERÄT
• Kein Konto nötig: alles wird auf deinem Gerät gespeichert
• JSON-Backup exportieren und jederzeit wiederherstellen
• Abos aus einer CSV-Datei importieren – dem eigenen Export oder einer Tabelle –, mit Vorschau, bevor etwas hinzukommt
• Dunkelmodus (echtes Schwarz) und haptisches Feedback
• Auf Deutsch, Englisch, Französisch, Spanisch, Japanisch sowie in vereinfachtem und traditionellem Chinesisch

SUBFLOW PRO
Die kostenlose Version erfasst bis zu 5 Abos und zeigt ein Werbebanner. Mit Pro bekommst du:
• Unbegrenzt viele Abos
• Erinnerungen 3 und 7 Tage vor der Verlängerung
• Die komplette Auswertung und den CSV-Export
• Ein Monatsbudget mit Anzeige bei deiner Summe und einem Hinweis, bevor ein Abo dich darüber bringt
• Keine Werbung, nie
Monatlich, jährlich oder als Einmalkauf, abgerechnet über Google Play. Abos sind jederzeit kündbar.

SubFlow erfasst und erinnert, kündigt aber nichts für dich. Zum Kündigen gehst du auf die Kontoseite des jeweiligen Dienstes; bei bekannten Diensten öffnet die App sie direkt.
```

### fr-FR Français

**Titre** (≤30)

```
SubFlow : suivi d’abonnements
```

**Description courte** (≤80)

```
Tous vos abonnements au même endroit, avec un rappel avant chaque prélèvement.
```

**Description complète** (≤4000)

```
Netflix, Spotify, stockage en ligne, salle de sport… combien vous coûtent vraiment vos abonnements chaque mois ? SubFlow les réunit au même endroit, les convertit en un vrai total mensuel et vous prévient avant le prélèvement, quand résilier ne coûte encore rien.

TOUS VOS ABONNEMENTS
• 34 services populaires intégrés, avec leur logo : choisissez, c’est ajouté
• Ou créez votre propre service, avec son nom, sa couleur et son icône
• Facturation hebdomadaire, mensuelle, trimestrielle ou annuelle, convertie en totaux mensuels et annuels
• Tri par date de renouvellement, prix ou nom ; filtre par catégorie et recherche
• Balayez une carte vers la gauche pour la supprimer, vers la droite pour la mettre en pause
• Appui long pour en sélectionner plusieurs et les mettre en pause, reprendre, supprimer ou changer de catégorie en une fois
• Mettez en pause un abonnement que vous ne payez plus : ses informations restent, mais il sort des totaux et des rappels
• Un widget pour l’écran d’accueil affiche votre total mensuel et les prochains renouvellements

UN RAPPEL AVANT LE PRÉLÈVEMENT
• Un rappel 1 jour avant chaque renouvellement (Pro ajoute 3 et 7 jours)
• Si votre téléphone a manqué un rappel, l’app le rattrape à l’ouverture

LES ESSAIS GRATUITS
• Marquez un abonnement comme essai gratuit, avec sa date de fin et le prix qui suivra
• Un rappel avant la fin de l’essai, dans toutes les versions
• À la fin, notez s’il est devenu payant, si vous l’avez résilié ou s’il a été prolongé

PLUSIEURS DEVISES, UN SEUL TOTAL
• 40 devises : saisissez chaque abonnement dans la devise où il est facturé
• Tout est converti dans votre devise principale aux taux du jour. Hors ligne, l’app utilise les derniers taux récupérés, ou ses taux intégrés si elle ne s’est jamais connectée

ANALYSE DES DÉPENSES
• Total mensuel, total annuel et moyenne par jour
• Pro : historique sur 6 mois, répartition par catégorie, carte des dépenses, prévision de facturation mois par mois et export CSV

VOS DONNÉES RESTENT SUR VOTRE TÉLÉPHONE
• Aucun compte nécessaire : tout est enregistré sur votre appareil
• Exportez une sauvegarde JSON et restaurez-la à tout moment
• Importez des abonnements depuis un fichier CSV – votre export ou un tableur – avec un aperçu avant tout ajout
• Mode sombre (noir intégral) et retour haptique
• Disponible en français, anglais, allemand, espagnol, japonais, chinois simplifié et chinois traditionnel

SUBFLOW PRO
La version gratuite suit jusqu’à 5 abonnements et affiche une bannière publicitaire. Avec Pro :
• Des abonnements illimités
• Des rappels 3 et 7 jours avant le renouvellement
• L’analyse complète des dépenses et l’export CSV
• Un budget mensuel, avec une jauge sur votre total et un avertissement avant qu’un abonnement ne le dépasse
• Aucune publicité
Mensuel, annuel ou achat unique à vie, facturé via Google Play. Les abonnements sont résiliables à tout moment.

SubFlow suit et rappelle, mais ne résilie rien à votre place. Pour résilier, rendez-vous sur la page de compte du service ; pour les services connus, l’app l’ouvre directement.
```

### es-ES Español

**Título** (≤30)

```
SubFlow: tus suscripciones
```

**Descripción breve** (≤80)

```
Todas tus suscripciones en un solo sitio, con un aviso antes de cada cobro.
```

**Descripción completa** (≤4000)

```
Netflix, Spotify, almacenamiento en la nube, el gimnasio… ¿cuánto suman de verdad tus suscripciones cada mes? SubFlow las reúne en un solo sitio, las convierte en un total mensual real y te avisa antes del cobro, cuando cancelar todavía no cuesta nada.

TODAS TUS SUSCRIPCIONES
• 34 servicios populares incluidos, con su logo: elige uno y listo
• O crea el tuyo con nombre, color e icono propios
• Facturación semanal, mensual, trimestral o anual, convertida en totales mensuales y anuales
• Ordena por fecha de renovación, precio o nombre; filtra por categoría y busca
• Desliza una tarjeta a la izquierda para eliminarla o a la derecha para pausarla
• Mantén pulsado para seleccionar varias y pausarlas, reanudarlas, eliminarlas o cambiar su categoría a la vez
• Pausa una suscripción que ya no pagas: conserva sus datos, pero deja de contar en los totales y de avisarte
• Un widget para la pantalla de inicio muestra tu total mensual y las próximas renovaciones

UN AVISO ANTES DEL COBRO
• Un aviso 1 día antes de cada renovación (Pro añade 3 y 7 días)
• Si tu móvil se saltó un aviso, la app lo recupera al abrirla

PRUEBAS GRATUITAS
• Marca una suscripción como prueba gratuita, con su fecha de fin y el precio posterior
• Aviso antes de que termine la prueba, en todas las versiones
• Al terminar, anota si pasó a ser de pago, si la cancelaste o si se amplió

VARIAS MONEDAS, UN SOLO TOTAL
• 40 monedas: registra cada suscripción en la moneda en que se cobra
• Todo se convierte a tu moneda principal con tipos de cambio actualizados. Sin conexión se usan los últimos tipos descargados, o los integrados si la app nunca se ha conectado

ANÁLISIS DE GASTOS
• Total mensual, total anual y media al día
• Pro: historial de 6 meses, desglose por categoría, mapa de gastos, previsión de facturación mes a mes y exportación CSV

TUS DATOS SE QUEDAN EN TU MÓVIL
• Sin cuenta: todo se guarda en tu dispositivo
• Exporta una copia de seguridad JSON y restáurala cuando quieras
• Importa suscripciones desde un archivo CSV –tu exportación o una hoja de cálculo– con una vista previa antes de añadir nada
• Modo oscuro (negro puro) y respuesta háptica
• En español, inglés, alemán, francés, japonés, chino simplificado y chino tradicional

SUBFLOW PRO
El plan gratuito registra hasta 5 suscripciones y muestra un banner publicitario. Con Pro tienes:
• Suscripciones ilimitadas
• Avisos 3 y 7 días antes de la renovación
• El análisis de gastos completo y la exportación CSV
• Un presupuesto mensual, con un indicador junto a tu total y un aviso antes de que una suscripción lo supere
• Sin publicidad, nunca
Mensual, anual o pago único de por vida, cobrado a través de Google Play. Las suscripciones se pueden cancelar en cualquier momento.

SubFlow registra y avisa, pero no cancela nada por ti. Para cancelar, ve a la página de cuenta del servicio; en los servicios más conocidos, la app la abre directamente.
```

### ja-JP 日本語

**タイトル**（≤30）

```
SubFlow：サブスク管理と更新通知
```

**簡単な説明**（≤80）

```
サブスクをまとめて管理。請求の前にお知らせするので、使っていないサービスを解約できます。
```

**詳しい説明**（≤4000）

```
Netflix、Spotify、クラウドストレージ、ジム……毎月のサブスク代、合計でいくらになっているか把握していますか？SubFlow はすべてのサブスクを一か所にまとめて実際の月額合計に換算し、請求の前にお知らせします。使っていないサービスを、まだ費用がかからないうちに解約できます。

【サブスクをまとめて管理】
• 人気サービス 34 件をロゴ付きで収録。選ぶだけで追加できます
• 名前・色・アイコンを自由に設定できるカスタムサービスにも対応
• 週・月・四半期・年ごとの請求に対応し、月額と年額に自動で換算
• 更新日・金額・名前で並べ替え、カテゴリでの絞り込みと検索も可能
• カードを左にスワイプで削除、右にスワイプで停止
• 長押しで複数選択し、停止・再開・削除やカテゴリ変更をまとめて実行
• 支払いをやめたサブスクは削除せずに停止できます。記録は残したまま、通知と合計の対象から外れます
• ホーム画面ウィジェットで、月額合計とこれからの更新をいつでも確認できます

【請求の前にお知らせ】
• 更新の 1 日前に通知（Pro では 3 日前・7 日前も追加できます）
• 端末が通知を出せなかった場合も、アプリを開いたときに見逃した通知をお知らせします

【無料トライアルも管理】
• サブスクを無料トライアルとして登録し、終了日とその後の料金を記録
• トライアル終了前に通知。すべてのプランで使えます
• 終了後は、有料に移行したか、解約したか、延長したかを記録できます

【複数の通貨を、ひとつの合計に】
• 40 種類の通貨に対応。サブスクごとに実際に請求される通貨で記録できます
• 最新の為替レートで基準通貨に換算。オフライン時は最後に取得したレートを、一度も接続していない場合は内蔵のレートを使います

【支出分析】
• 月額合計、年額換算、1 日あたりの金額
• Pro：直近 6 か月の推移、カテゴリ別の内訳、支出マップ、今後の請求予測、CSV 書き出し

【データは端末の中だけに】
• アカウント登録は不要。データはすべて端末に保存されます
• JSON 形式でバックアップを書き出し、いつでも復元できます
• CSV ファイルからサブスクをインポート（このアプリの書き出しファイルや自作の表）。追加前にプレビューで確認できます
• ダークモード（純黒）と触覚フィードバックに対応
• 日本語、英語、ドイツ語、フランス語、スペイン語、簡体字中国語、繁体字中国語に対応

【SubFlow Pro】
無料プランでは 5 件までサブスクを記録でき、バナー広告が表示されます。Pro にアップグレードすると：
• サブスクを無制限に登録
• 更新 3 日前・7 日前の通知
• すべての支出分析と CSV 書き出し
• 月の予算：合計の横に進捗バーを表示し、予算を超えそうなときに事前にお知らせ
• 広告なし
月額・年額・買い切りの 3 プランから選べます。お支払いは Google Play で行われ、サブスクリプションはいつでも解約できます。

SubFlow は記録と通知のためのアプリで、サービスの解約を代行することはありません。解約は各サービスのアカウントページから行ってください。主要なサービスはアプリから直接開けます。
```

What each claim rests on:

| Claim | Where |
|---|---|
| 34 preset services | `defaultLocalPresets()` in `SubscriptionRepository.kt` |
| Free limit of 5 | `FREE_TIER_LIMIT = 5` |
| 1-day reminder free, 3 and 7 days Pro | `ReminderLead` |
| Trial reminders on every version | `settings_trial_leads_sub`; BIN-15 |
| Catch-up of missed reminders | BIN-23 |
| 40 currencies, live rates, last-fetched cache, then a built-in fallback | `SupportedCurrencies`; `ExchangeRateRepository` (prefs cache); `fallbackRatesToUSD` |
| Pro analytics | the four `ProGate`s in `AnalyticsScreen.kt` + gated CSV export |
| No account, stored locally | release build has `BACKEND_ENABLED = false` |
| 7 languages | `SupportedLanguages` |
| Totals and daily average free | outside the `ProGate`s (`item(key = "metrics")`) |
| Pause instead of delete; out of totals and reminders; every plan | `Subscription.isActive`, `SubscriptionRepository.setActive` (no `ProGate`); BIN-29 |
| Home screen widget: monthly total and upcoming renewals; every plan | `widget/SubFlowWidget.kt`, `summariseForWidget` (not gated); BIN-30, BIN-32 |
| Swipe right to pause; long-press multi-select (pause, resume, delete, category); every plan | `SwipeableSubscriptionCard` StartToEnd, `DashboardScreen` selection mode (not gated); BIN-41, BIN-66 |
| CSV import with a preview; every plan | `domain/util/CsvImport.kt`, `ImportPreviewSheet`, Settings → Data (not gated); BIN-65 |
| Monthly budget with a meter and an over-budget heads-up; Pro | `PreferencesManager.monthlyBudget`, `domain/util/Budget.kt`, shown only when `isPro`; BIN-68 |
| Opening the service's account page | `cancellationUrlFor()`: known services get their own page; everything else gets Google Play's subscriptions page, so the copy says "常見服務" |

**Deliberately not claimed:** cloud sync. The release build has no backend,
so the feature isn't there.

---

## Other Console items for this release

- [ ] **versionCode 9 / versionName 1.5.0**. 8 is used up.
- [ ] **Release notes**: paste the whole 1.5.0 block, all languages.
- [ ] **Track: closed testing.** Production access was refused, so this goes
      to the closed test the reapplication depends on. It is the first build
      whose what's-new sheet testers will see on updating, and the sheet's
      Send feedback writes to bin-tech@outlook.com.
- [ ] **Still owed from 1.4.0: billing on a real device** (PR #56, #61), with
      a license tester - restore with two products owned, buy an owned plan,
      and the billing-unavailable message only on the paywall.
- [ ] **Store listing text: no change needed.** Re-checked every row of
      *What each claim rests on*: 34 presets, free limit 5, 1-day reminder
      free with 3/7 on Pro, 40 currencies, four `ProGate`s plus gated CSV
      export, 7 languages, `BACKEND_ENABLED` false in release. CSV import and
      multi-select are not named in any listing, so nothing is contradicted;
      adding them to the full descriptions is optional.
- [ ] **Data safety: no changes.** The manifest gains only the portrait lock.
      CSV import reads a file the user picks through the system picker - no
      permission, nothing sent anywhere. The new dependencies (Robolectric,
      Compose UI test) are test-only and not in the release build.
- [ ] **Screenshots: optional refresh.** `01-dashboard` and
      `02-dashboard-dark` would now show only the category chips in use.
      Nothing in the current shots is false.

## Store listing refresh (BIN-73)

Not tied to a build: the listing text and screenshots can be updated in the
Console at any time, and the change goes through Play review on its own.

- [ ] **Full descriptions, all seven languages.** Paste each *Full
      description* above. New since the last upload: swipe right to pause,
      long-press multi-select, CSV import, and the monthly budget in the Pro
      list. Titles and short descriptions are unchanged. Each new claim has a
      row in *What each claim rests on*.
- [ ] **Phone screenshots: replace all six with the eight below**, in this
      order. Play shows the first three in search results, so the hero, trial
      tracking and analytics go first.

| # | File | Shows | Plan |
|---|---|---|---|
| 1 | `01-dashboard.png` | Total, trial badge, free-plan meter, swipe hint | Free |
| 2 | `02-trial-detail.png` | Trial countdown, price after trial | Free |
| 3 | `03-analytics.png` | 6-month trend, category split | Pro |
| 4 | `04-add-budget.png` | Preset filled in, over-budget warning, 3/7-day reminders | Pro |
| 5 | `05-multi-select.png` | Two selected, pause/delete, category/reminder menu | Free |
| 6 | `06-csv-import.png` | Import preview, duplicate skipped | Free |
| 7 | `07-services.png` | Preset picker with logos | Free |
| 8 | `08-dashboard-dark.png` | Dark (OLED) theme | Free |

The same English screenshots serve every language, as before.

### How the screenshots were taken

So the next set matches rather than drifting. On a Pixel 8a emulator
(1080x2400, 420 dpi, Android 15):

- **Use an emulator of its own.** `emulator-5554` is shared with another
  project's session, which brought its own app to the front partway through
  the 2026-10-21 shoot. The set was taken on a separate AVD,
  `Pixel_8a_Shots`, started with `-port 5580`.
- `adb shell wm size 1080x2160`: the capture is then already at the ratio
  Play accepts, with nothing cropped away.
- `cmd locale set-app-locales <pkg> --locales en-US`, home currency USD.
- Seed three active subscriptions and one paused, **not four**. At four, the
  "one slot left on the free plan" card fills half the screen. One of the
  three is a running trial, which is what puts trial tracking in the frame:
  Perplexity Pro (trial, 4 days left, then $20), Spotify $11.99 and Netflix
  $15.49, plus Adobe Creative Cloud paused.
- **Airplane mode on.** Otherwise the AdMob *test* banner renders into the
  bottom of every shot, and a Play Billing error snackbar covers a row.
- **SystemUI demo mode** for the status bar: clock 9:41, full battery, no
  notification icons
  (`settings put global sysui_demo_allowed 1`, then
  `am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0941`
  and the same for `battery` and `notifications`). Exit it with
  `-e command exit`.
- Dark mode comes from the app's own Appearance setting, not
  `cmd uimode night`, because the setting overrides the system.
- **Pro shots (3 and 4).** The emulator has no Play Billing, so for these
  `val isPro` in `SubFlowNavGraph` was temporarily set to `true`, marked
  `TEMP-`, then reverted. `grep -rl 'TEMP-\|isPro || true' android/app/src/`
  printed nothing before the commit.
  - Shot 4: monthly budget $40, then Disney+ picked in the add form.
  - Shot 3: the analytics shot is the only one on a larger set, eight
    subscriptions with staggered start dates. With three, the trend line is
    flat. A Pro user has no limit of five, so that set is one a Pro user
    could have.
- **CSV import (6).** The file was three rows with headers
  `Name,Amount,Currency,Cycle,Category,FirstBillDate`: Netflix, already
  tracked, so it is skipped; Disney+; and ChatGPT Plus. Five rows would make
  the preview say two come in paused under the free limit. That is true, but
  it is not what this shot is selling.
