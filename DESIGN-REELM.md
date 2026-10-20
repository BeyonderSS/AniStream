# Reelm design direction ("Dusk Projector")

Replaces the Nocturne/Lamborghini look (user: black too harsh, wants a softer, different theme). Applies to phone AND TV.

**Subject:** a personal streaming hub for anime, movies, series and live TV, mostly used in the evening on a couch (TV) or in bed (phone).
**Audience:** one viewer or a household, not power users.
**Primary job:** get back into what you were watching, or pick something new, in as few presses as possible.

## Palette (soft, not black)
| Token | Hex | Role |
|---|---|---|
| dusk (base) | `#15171F` | app background: blue-slate night, never pure black |
| slate (raised) | `#1D2029` | cards, rails, sheets |
| haze (overlay) | `#272B37` | pressed/focused surfaces, dialogs |
| projector (accent) | `#F2B45A` | the warm lamp light. Primary action, focus ring and progress only. Text on it: `#1A1408` |
| mist (ink) | `#E9EAF0` | text. Hierarchy by opacity 100 / 72 / 52 / 32 % |
| signal (state) | `#7FD1B9` | only "downloaded / live / new episode"; failure uses `#F07C6C` |

Shadows are tinted with dusk (`#0A0C12` at 40–60%), never grey. A focused or primary element gets a soft amber light, as if a projector beam lands on it (blurred `projector` at 25–35%), never a hard outline.

## Type
- **Display:** Bricolage Grotesque (bundled TTF, SemiBold/Bold). Characterful, slightly condensed, used for titles and hero names in **sentence/title case**.
- **Text:** Figtree (bundled TTF, Regular/Medium/SemiBold) for body, labels and buttons.
- Scale (phone): 30 / 22 / 16 / 13 sp. Scale (TV, viewed at 3 m): 48 / 32 / 22 / 18 sp.
- **No all-caps labels and no tracked-out eyebrows.** Meta lines use small rounded chips or plain spacing instead of middle-dot strings.

## Shape and motion
- Radius hierarchy: posters 12dp, cards 18dp, sheets/dialogs 24dp, buttons and chips fully round. Not one radius for everything.
- Motion answers actions: 180–260 ms, emphasized easing (`CubicBezier(0.2, 0, 0, 1)`). On TV a focused poster scales 1.0 → 1.08 with the amber light fading in.
- **The one memorable moment, "the projector":** on TV (and the phone home hero) the full-screen backdrop crossfades (400 ms) to the art of whatever is focused, under a dusk gradient. Everything else stays quiet.
- Respect reduced motion: no scaling or crossfades, focus shown by the amber light alone.

## TV layout (Compose for TV, D-pad first)
```
┌──────┬──────────────────────────────────────────────┐
│ rail │  backdrop of focused title (crossfade)       │
│ ⌂    │  Title in Bricolage 48                       │
│ 🔍   │  2014 · chips · rating        [ Play ] [ i ] │
│ ▶    │                                              │
│ ⭐   │  Continue watching  ▢▢▢▢▢ (16:9, progress)   │
│ ⬇    │  Popular movies     ▯▯▯▯▯▯▯ (2:3 posters)    │
│      │  Anime trending     ▯▯▯▯▯▯▯                  │
└──────┴──────────────────────────────────────────────┘
```
- Left rail: icons only. It expands with labels when focused (Home, Search, Movies & TV, Anime, Live TV, Library, Settings).
- Rows are horizontal lists with focus restoration (returning to a row lands on the last focused item). Left-aligned content with a 58 dp TV overscan safe margin.
- **Detail:** backdrop + left column (title, synopsis ≤ 3 lines, Play/Resume as the first focus, then favourite) + seasons/episodes as focusable rows.
- **Player:** OK = play/pause; left/right = seek ±10 s, with hold to accelerate; up = sub/dub/quality/source panel; down = episode strip; back hides the controls first. Controls auto-hide after 4 s.
- **Search:** an on-screen keyboard grid plus voice search if the device has it; results in a poster grid.
