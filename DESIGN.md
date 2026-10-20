# Reelm design spec ("Dusk Projector")

> **Authoritative direction:** [`DESIGN-REELM.md`](DESIGN-REELM.md). It replaces the earlier black/gold "Nocturne" look (the old Lamborghini reference stays in `design-reference-lamborghini.md` for history only). On any conflict below, DESIGN-REELM.md wins.

## Reelm rules (override everything below on conflict)
- **Canvas is soft dusk, never black**: `Bg.base` `#15171F` → `Bg.raised` `#1D2029` (cards, rails, sheets) → `Bg.overlay` `#272B37` (pressed, selected segments, dialogs). Depth comes from surface layering plus soft dusk-tinted shadows (`Modifier.duskShadow`, `#0A0C12`), never grey.
- **Projector amber `#F2B45A` (`Accent.main`) is the only accent**, for the primary action, focus and progress. Text on amber is `#1A1408` (`Accent.on`). Only the primary CTA gets the soft amber light (`Modifier.amberGlow`). Selected chips use a mist pill with dusk text; selected segments/nav use a haze pill.
- **Signal** `#7FD1B9` (`Accent.live`) only for downloaded / live / new; `#F07C6C` (`Accent.danger`) only for failure.
- **Ink** is mist `#E9EAF0` by opacity: `Ink.high/body/muted/faint` = 100 / 72 / 52 / 32 %.
- **Type**: Bricolage Grotesque (display, `AppType.title`/`heading`) and Figtree (text), bundled in `core/ui/src/main/res/font` (OFL, licences in `core/ui/licenses`). Phone scale 30 / 22 / 16 / 13. Sentence or title case only: **no ALL CAPS, no tracked eyebrows, no middle-dot meta strings** (use `MetaChips` or spacing).
- **Radius hierarchy**: `Radius.sm` 8 (small inputs), `Radius.md` 12 (posters, tiles), `Radius.lg` 18 (cards), `Radius.xl` 24 (sheets, dialogs), `Radius.pill` (buttons, chips).
- **Motion**: `Motion.quick()` 180 ms / `Motion.standard()` 260 ms with emphasized easing; the home hero backdrop crossfades (`Motion.PROJECTOR`, 400 ms) between items.
- **Player**: a clean circular amber play/pause button (the hexagon is gone).

---

## Original structure spec (layout/UX still applies)

Anime streaming app. Users come to (1) resume what they're watching, (2) find something new, (3) watch with zero friction. It should feel cinematic, calm and premium (Crunchyroll / Netflix / Apple TV), never like a scraped website. **The artwork is the hero; chrome stays quiet.**

Tokens live in `core/ui/src/main/java/com/anistream/core/ui/theme/Design.kt` (`Space`, `Radius`, `Ink`, `Accent`, `Bg`, `AppType`). Use them; don't invent new hex values, sizes or dp.

## Rules
- **Colour 60/30/10.** `Bg.base` background (60), raised surfaces + white ink (30), `Accent.main` amber only for primary CTA, selected state, progress and active nav (10). No rainbow chips. `Accent.star` only for score stars, `Accent.live` only for "airing/completed", `Accent.danger` only for failed/delete.
- **Text hierarchy by opacity** (`Ink.high/body/muted/faint`), not by colour.
- **Type: four sizes only**: `AppType.title` 30, `heading` 22, `body` 16, `caption` 13. Two weights (Normal, SemiBold). Times, episode numbers, file sizes use `AppType.numeric` (tabular digits).
- **8pt grid.** Screen gutter `Space.lg` 16dp. Related items 8 to 12dp apart; next group 24dp; sections 32dp. Card padding 16dp.
- **Shapes.** Posters/tiles `Radius.md` 12dp, cards `Radius.lg` 18dp, hero/dialogs `Radius.xl`, buttons/chips `Radius.pill`. Cards get a 1dp `Ink.hairline` border instead of heavy shadows. Primary CTA gets a soft amber glow (`Modifier.amberGlow`), never a grey shadow.
- **Posters are 2:3**, landscape/continue cards 16:9. Every image has a `Bg.raised` placeholder and a crossfade (Coil `crossfade(true)`). Titles under posters: max 2 lines, `AppType.captionStrong`.
- **Thumb zone.** Primary action of a screen sits in the bottom third or in a sticky bottom bar. Tap targets ≥ 48dp.
- **Every screen has loading (shimmer skeletons shaped like the content, not a spinner), empty (illustrative icon + one line + CTA) and error (message + Retry) states.**
- **Motion.** 200 to 300ms, `FastOutSlowIn`. Press feedback (scale 0.97) on cards. Crossfade between loading/content. Nothing gratuitous.
- **Insets.** The outer NavGraph Scaffold already applies system-bar and bottom-nav padding. Inner screen Scaffolds must use `contentWindowInsets = WindowInsets(0)` and top bars must not add the status-bar inset twice (today there is a ~60dp empty band above every screen title: fix it). Detail and Player draw edge to edge behind the status bar.
- Icons: Material Icons **Rounded** set (`Icons.Rounded.*`), 24dp, `Ink.body`; selected = `Accent.main`.

## Screens
- **Home**: Full-bleed hero pager of Trending (auto-advance 6s, 3:4 artwork with `Bg.scrim` bottom gradient, title in `AppType.title`, meta line "TV · Sub 28 · Dub 28" if known, two buttons: primary "Watch" (play icon, violet pill with glow) + secondary "Details" (12% white pill); page dots). Then Continue Watching (16:9 cards using the poster cropped, violet progress bar at the bottom edge, "Ep 3 · 12 min left"-style caption, play button overlay), then poster rows. Section headers: `AppType.heading` + a quiet "See all" text button (no wrapping). Top: app wordmark left, search + genres icons right, transparent over the hero.
- **Anime detail**: Edge-to-edge backdrop (poster blurred 24dp, darkened with `Bg.scrim`) behind status bar; poster (2:3, 120dp wide, `Radius.md`) + title + meta chips (type, duration, rating, status) + score with star. Synopsis 3 lines with "More". Genres as neutral outline chips. Seasons as horizontal cards. **Episodes**: grid of number tiles (5 per row) with range tabs (1–100, 101–200 …) when > 100 episodes; watched tiles dimmed with a check, current/next tile violet outline. Recommendations poster row. **Sticky bottom bar** (thumb zone) with full-width primary "▶ Continue Ep N" / "▶ Play Ep 1" + favourite icon button (heart fills violet with a small scale bounce).
- **Player** (the peak moment, see below).
- **Search**: Never blank. Empty query shows Recent searches (removable chips + "Clear") and "Popular right now" grid (from `browseCategory("most-popular")`). Typing shows a 3-column poster grid with type badge; filter chips neutral, selected = `Accent.soft` fill + violet text.
- **Schedule**: Day selector of 7 pills showing weekday + date number (today labelled "Today"). Entries as a timeline: time column (`AppType.numeric`, 56dp) | dot + vertical line | card (title, "Episode N"). Entries whose time has passed today are dimmed; the next upcoming one gets an "Up next" violet badge. Empty day state.
- **Favorites**: 3-column poster grid, sort as a bottom sheet or menu, long-press → remove with undo snackbar. Empty state: heart icon, "Nothing saved yet", CTA "Discover anime" → Home.
- **Downloads**: Header summary "3 episodes · 1.2 GB". Group by anime (poster thumb + title header, episodes beneath). Each row: "Episode 2", quality + size caption, state: progress bar with % (tabular) while downloading, check for done, red "Failed · Retry" when failed (retry = delete + user re-downloads from player, or call a retry if available). Swipe to delete with confirm/undo. Tap done → offline player. Empty state CTA.
- **Genre browse**: Genre picker as a 2-column grid of tiles (soft tinted background from a hash of the name at 12% alpha, name in `AppType.bodyStrong`); hide junk genres whose name contains ",". List view = 3-column poster grid with infinite scroll + skeleton.
- **Bottom nav**: 5 items, `Bg.base` at 95% with a hairline top border, labels `AppType.caption`, selected = violet icon + violet label + small violet pill indicator.

## Player
- **Custom controls overlay** (hide PlayerView's default controller, `useController = false`) drawn in Compose over the video: tap toggles controls (auto-hide after 3s while playing); top row back + "Title · Ep N" + settings; centre: rewind 10s, big play/pause (64dp, filled), forward 10s; **double-tap left/right half seeks ∓10s** with a ripple "−10s / +10s" bubble; bottom: time `numeric` "12:04 / 23:50", a slim violet seek bar (buffered track at 30% white) and a **fullscreen** toggle. Loading = centred spinner over the last frame.
- Use `PlayerView` with **TextureView** (inflate a tiny XML layout with `app:surface_type="texture_view"`) to avoid stale-frame artifacts when quality changes.
- **Fullscreen/landscape**: rotating to landscape or tapping fullscreen sets `requestedOrientation = SENSOR_LANDSCAPE`, hides system bars (WindowInsetsControllerCompat, BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE), video fills screen; back exits fullscreen first. Restore on dispose.
- Portrait below the video: title block (`heading` + caption meta), a **segmented Sub | Dub** control, a quality pill ("1080p ▾" → bottom sheet listing qualities with a check), download icon button with progress-aware state (after tap: "Downloading" snackbar). **Up next card** (poster thumb 16:9, "Up next · Episode 3", play) and a horizontal **episode strip** of number tiles (current = violet fill, watched = dimmed) for jumping.
- **End of episode**: a "Next episode in 5" countdown overlay with Cancel and Play-now (peak-end rule), instead of jumping instantly. The last episode shows a "You finished <title>" end card with a "Back to details" button.
- Keep all the existing behaviour (referer data source, subtitles, dub→sub fallback, progress saving, resume, downloads).
