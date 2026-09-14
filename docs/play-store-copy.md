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
• Swipe a card left to delete it
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
• Dark mode (true black) and haptic feedback
• In English, Simplified Chinese, Traditional Chinese, Japanese, German, French and Spanish

SUBFLOW PRO
The free plan tracks up to 5 subscriptions and shows a banner ad. Pro gives you:
• Unlimited subscriptions
• Reminders 3 and 7 days before renewal
• The full spending analysis and CSV export
• No ads, ever
Choose monthly, annual or a one-time lifetime purchase, all billed through Google Play. Subscriptions can be cancelled at any time.

SubFlow tracks and reminds; it doesn't cancel anything for you. To cancel, go to the service's account page. For well-known services, the app opens it for you.
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
• 向左滑动卡片即可删除
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
• 支持深色模式（纯黑）和触感反馈
• 界面支持简体中文、繁体中文、英语、日语、德语、法语和西班牙语

【SubFlow Pro】
免费版可记录 5 个订阅，并显示横幅广告。升级 Pro 可以：
• 不限订阅数量
• 增加提前 3 天和 7 天的续费提醒
• 解锁完整的支出分析和 CSV 导出
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
• 向左滑動卡片即可刪除
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
• 支援深色模式（純黑）與觸覺回饋
• 介面支援繁體中文、簡體中文、英文、日文、德文、法文與西班牙文

【SubFlow Pro】
免費版可記錄 5 筆訂閱，並顯示橫幅廣告。升級 Pro 可以：
• 不限訂閱數量
• 增加提前 3 天與 7 天的續訂提醒
• 解鎖完整的支出分析與 CSV 匯出
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
• Karte nach links wischen zum Löschen
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
• Dunkelmodus (echtes Schwarz) und haptisches Feedback
• Auf Deutsch, Englisch, Französisch, Spanisch, Japanisch sowie in vereinfachtem und traditionellem Chinesisch

SUBFLOW PRO
Die kostenlose Version erfasst bis zu 5 Abos und zeigt ein Werbebanner. Mit Pro bekommst du:
• Unbegrenzt viele Abos
• Erinnerungen 3 und 7 Tage vor der Verlängerung
• Die komplette Auswertung und den CSV-Export
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
• Balayez une carte vers la gauche pour la supprimer
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
• Mode sombre (noir intégral) et retour haptique
• Disponible en français, anglais, allemand, espagnol, japonais, chinois simplifié et chinois traditionnel

SUBFLOW PRO
La version gratuite suit jusqu’à 5 abonnements et affiche une bannière publicitaire. Avec Pro :
• Des abonnements illimités
• Des rappels 3 et 7 jours avant le renouvellement
• L’analyse complète des dépenses et l’export CSV
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
• Desliza una tarjeta a la izquierda para eliminarla
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
• Modo oscuro (negro puro) y respuesta háptica
• En español, inglés, alemán, francés, japonés, chino simplificado y chino tradicional

SUBFLOW PRO
El plan gratuito registra hasta 5 suscripciones y muestra un banner publicitario. Con Pro tienes:
• Suscripciones ilimitadas
• Avisos 3 y 7 días antes de la renovación
• El análisis de gastos completo y la exportación CSV
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
• カードを左にスワイプして削除
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
• ダークモード（純黒）と触覚フィードバックに対応
• 日本語、英語、ドイツ語、フランス語、スペイン語、簡体字中国語、繁体字中国語に対応

【SubFlow Pro】
無料プランでは 5 件までサブスクを記録でき、バナー広告が表示されます。Pro にアップグレードすると：
• サブスクを無制限に登録
• 更新 3 日前・7 日前の通知
• すべての支出分析と CSV 書き出し
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
| Opening the service's account page | `cancellationUrlFor()`: known services get their own page; everything else gets Google Play's subscriptions page, so the copy says "常見服務" |

**Deliberately not claimed:** cloud sync. The release build has no backend,
so the feature isn't there.

---

## Other Console items for this release

- [ ] **versionCode 7 / versionName 1.3.2**. 6 is used up.
- [ ] **Release notes**: paste the whole 1.3.2 block, all languages.
- [ ] **Screenshot `05-settings.png` replaced (2026-09-14).** It shows the
      Preferences page, whose header changed in #36: the back button and the
      title are now on one row. Upload it in place of the old one. Captured the
      same way as the rest (1080×2160, English, USD, light, airplane mode). The
      other five are unchanged. Still no widget screenshot.
- [ ] **Contact details: switch to `bin-tech@outlook.com`.** The app's Contact
      support and the hosted privacy policy and terms already use it, but two
      Console fields entered on 2026-08-28 still say `zmtzwb@gmail.com`: Main
      store listing → 详细联系信息 (email), and the IARC content-rating contact.
- [ ] **Store listing text: no change from 1.3.1.** Since v1.3.1 only
      `SettingsScreen.kt` and docs changed, and no listing names the support
      address or describes the Settings header. If the earlier listing work is
      still open — replacing the en-US text and adding the six translations
      under Main store listing → Manage translations — it still applies.
- [ ] **Data safety: no changes.** Re-checked since v1.3.1: no manifest or
      dependency change. The support address is a mailto link the user chooses
      to open; the app sends nothing itself.

### How the screenshots were taken

So the next set matches rather than drifting. On the Pixel_8a emulator:

- `adb shell wm size 1080x2160` — the capture is then already at the ratio Play
  accepts, with nothing cropped away.
- `cmd locale set-app-locales <pkg> --locales en-US`, home currency USD.
- Seed three active subscriptions and one paused, **not four** — at four the
  "one slot left on the free plan" card fills half the screen. One of the three
  is a running trial, which is what puts trial tracking in the frame.
- **Airplane mode on.** Otherwise the AdMob *test* banner renders into the
  bottom of every shot, and a Play Billing error snackbar covers a row.
- The app's own Appearance setting drives dark mode, not `cmd uimode night` —
  the setting overrides the system.
