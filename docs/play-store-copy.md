# Play Store copy

Text that goes into Play Console, kept here so it is versioned alongside the
code it describes. Every claim below was checked against the code at the time
of writing; when the code changes, re-check the copy.

Play limits: release notes 500 characters per language; title 30; short
description 80; full description 4000.

---

## 1.2.0 (versionCode 4): release notes

Changes since 1.1.0 (versionCode 3):

| Change | Source |
|---|---|
| Traditional Chinese, chosen automatically on TW/HK/MO devices | BIN-27, PR #22 |
| Settings split into four pages | BIN-21, PR #21 |
| Home currency pinned to the device region, no longer moved by the interface language | BIN-25, PR #20 |
| Rate date shown under the currency picker | BIN-22, PR #19 |

Paste the whole block below into **Release notes** in Play Console. It
accepts every language in one box. `zh-HK` reuses the Taiwan text; delete
any language the listing does not have.

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

## Store listing: 中文（台灣） zh-TW, new

The app now ships Traditional Chinese, so the listing needs a zh-TW
version too. Hong Kong (`zh-HK`) can use the same text. The existing en-US
listing isn't recorded in this repo, so this one was written from the
features in the code, not translated from it. Compare it with the en-US
listing so the two don't contradict each other.

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
| Opening the service's account page | `cancellationUrlFor()`: known services get their own page; everything else gets Google Play's subscriptions page, so the copy says "常見服務" |

**Deliberately not claimed:** cloud sync. The release build has no backend,
so the feature isn't there.

---

## Other Console items for this release

- [ ] **versionCode 4 / versionName 1.2.0**. 3 is used up.
- [ ] **Release notes**: paste the block above.
- [ ] **Add 中文（台灣） listing language** (optionally 中文（香港） too) with the text above.
- [ ] **Screenshots are out of date.** `play-assets/screenshots/` was captured on 2026-08-28.
      `05-settings.png` shows the old single-page Settings, and none of them show trial
      tracking. Play falls back to the default language's screenshots for zh-TW, so
      Traditional Chinese screenshots aren't required but would look better. Keep the
      2:1 limit: 1080×2160 is accepted, while the Pixel 8a's native 1080×2400 is rejected.
- [ ] **Check the existing en-US listing.** The 2026-08-28 draft named **10** currencies
      and avoided the phrase "live exchange rates". Since 2026-09-02 there are **40**
      currencies and the rates are fetched live. If the listing still says the old
      version, update it.
- [ ] **Data safety: no changes.** 1.2.0 collects nothing new. The language and currency
      choices are stored only on the device, and the exchange-rate request already
      existed before this release.
