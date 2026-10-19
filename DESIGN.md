# AniStream design spec — "Nocturne" (Lamborghini-inspired)

> **Design system source:** adapted from the Lamborghini DESIGN.md in VoltAgent/awesome-design-md (full reference in `design-reference-lamborghini.md`). This REPLACES the earlier violet look. Where the sections below mention violet/`Accent.main`, read gold.

## Nocturne rules (override everything below on conflict)
- **Canvas is true black** `#000000` (`Bg.base`). Elevation by surface layering only: `#000000` → `#181818` (`Bg.raised`, cards) → `#202020` (`Bg.overlay`, sheets/dialogs/pressed). No gradients except image scrims (black).
- **Gold `#FFC000` (`Accent.main`) is the ONLY chromatic colour** and is used *exclusively* for: the primary CTA of a screen, the selected state (nav item, segmented option, chip, current episode), progress/seek bars. Text/icons ON gold are **black** (`Accent.on`). Pressed gold = `#917300` (`Accent.pressed`). Inline highlighted text = `#FFCE3E`. Star scores use gold too (no separate amber). `Accent.live` and `Accent.danger` stay only for done/failed states.
- **No glows, no coloured shadows, no drop shadows.** Depth = surface layering + 1dp `Ink.hairline` borders.
- **Zero border radius everywhere** (`Radius.*` are all sharp): buttons, cards, posters, chips, sheets, dialogs, tiles, search field. Only exceptions: switch/toggle tracks, the seek-bar thumb, and progress dots may be round.
- **Typography**: one family. Display/headings use the condensed grotesk (`AppType.title`/`heading` use `sans-serif-condensed`), **ALL CAPS** (apply `.uppercase()` at the call site), weight Normal or Medium, tight line height. Body stays mixed case in the regular family. Labels/badges/buttons/tab labels: **uppercase caption with +0.96sp tracking** (`AppType.label`). Still max 4 sizes.
- **Buttons**: Primary = gold rectangle, black uppercase label, 52dp tall, no radius. Secondary = transparent ghost with 1dp white border at 50% opacity, white uppercase label (pressed: 8% white fill). Tertiary = uppercase text button in `Ink.body`.
- **Neutrals**: Pure white headings, Smoke `#F5F5F5`, Steel `#969696` secondary, Ash `#7D7D7D` metadata. Use `Ink.*`.
- **Iconography**: thin/outlined feel — prefer `Icons.Outlined.*` (or Rounded where no outlined variant), white; selected gold.
- **Imagery** is the hero: full-bleed, cinematic 16:9 or taller, black scrims for legibility. Posters keep 2:3 with sharp corners and a hairline border.
- **Hexagon motif (optional flourish)**: the player's centre play/pause button may sit inside a hexagon outline (draw with a GenericShape) — nice brand echo, do it only if it stays crisp.

---

## Original structure spec (layout/UX still applies)

Anime streaming app. Users come to (1) resume what they're watching, (2) find something new, (3) watch with zero friction. It should feel cinematic, calm and premium (Crunchyroll / Netflix / Apple TV), never like a scraped website. **The artwork is the hero; chrome stays quiet.**

Tokens live in `core/ui/src/main/java/com/anistream/core/ui/theme/Design.kt` (`Space`, `Radius`, `Ink`, `Accent`, `Bg`, `AppType`). Use them; don't invent new hex values, sizes or dp.

## Rules
- **Colour 60/30/10.** `Bg.base` background (60), raised surfaces + white ink (30), `Accent.main` violet only for primary CTA, selected state, progress and active nav (10). No rainbow chips. `Accent.star` only for score stars, `Accent.live` only for "airing/completed", `Accent.danger` only for failed/delete.
- **Text hierarchy by opacity** (`Ink.high/body/muted/faint`), not by colour.
- **Type: four sizes only**: `AppType.title` 28, `heading` 20, `body` 15, `caption` 12. Two weights (Normal, SemiBold). Times, episode numbers, file sizes use `AppType.numeric` (tabular digits).
- **8pt grid.** Screen gutter `Space.lg` 16dp. Related items 8 to 12dp apart; next group 24dp; sections 32dp. Card padding 16dp.
- **Shapes.** Posters/tiles `Radius.md` 12dp, cards `Radius.lg` 16dp, hero/dialogs `Radius.xl`, buttons/chips `Radius.pill`. Cards get a 1dp `Ink.hairline` border instead of heavy shadows. Primary CTA gets a soft violet glow (`Accent.glow`, blurred shadow, `spotColor`/`ambientColor` tinted violet), never a grey shadow.
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
