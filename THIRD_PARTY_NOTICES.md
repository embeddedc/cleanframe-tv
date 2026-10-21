# Third-party notices

The root MIT licence covers CleanFrame's original code and documentation and the CleanFrame TV port. Third-party material retains the terms below. Keep these notices and the adjacent licence texts when sharing source or a build.

## Fonts

| Material | Terms and source record |
| --- | --- |
| Manrope | SIL Open Font License 1.1; `app/assets/manrope-OFL.txt` |
| Geist and Newsreader | SIL Open Font License 1.1; licences beside the files in `app/assets/fonts/`, with exact sources and hashes in `app/assets/fonts/SOURCES.json` |
| Optional PP Neue Montreal, PP Mori and PP Pangram Sans | Proprietary. Upstream's tooling can use them from an ignored `.local/` folder in the browser preview only. They are not in this repository or the APK. See [Pangram's licensing information](https://pangrampangram.com/pages/eula). |

## Icons and trademarks

| Material | Provenance and terms |
| --- | --- |
| Simple Icons service marks, including Jellyfin and Twitch | CC0; exact revisions and adaptations in `app/assets/icons/SOURCES.json` and `ADDITIONAL-SOURCES.json`. The CC0 text is retained beside the icons. |
| Disney+, ABC and SBS marks | [Custom Brand Icons](https://github.com/elax46/custom-brand-icons), MIT. The retained licence is `app/assets/icons/CUSTOM-BRAND-ICONS-LICENSE.txt`. Exact revisions, source files and extraction/recolour notes are in `SOURCES.json`. |
| Nintendo Switch glyph | An original simplified drawing of two controllers made for CleanFrame TV (MIT), not Nintendo's logo. |
| Art, apps, settings, home and photo outlines | Original CleanFrame drawings, covered by the project's MIT licence. |
| App icons of installed apps | Read from the TV at runtime and shown only there; not distributed. |

Brand marks identify services. Their owners retain trademark rights; this project claims no endorsement or affiliation. The older Australian TV icon archive's GPL text is retained for historical provenance only; no current icon uses that archive.

## Photographs and derived materials

The fifteen bundled photo pages were individually checked on 20 October 2026. Each displayed its credited author and free use under the [Unsplash License](https://unsplash.com/license). The [asset source register](docs/asset-sources.md) links every page. Runtime paths are in `app/assets/wallpapers/SOURCES.json`.

Photographers retain their rights. Wallpapers, thumbnails, panel backgrounds and material fields derive from the credited photographs and retain their terms. The Unsplash licence permits copying, modification and distribution subject to its restrictions, including on selling unmodified images and building a competing image service.

**Bing photo of the day** images are not part of the APK. When a user selects that source, each TV downloads them from Microsoft's Bing service for use as its own wallpaper and shows each photo's copyright caption in Photo mode. Their rights stay with the photographers and agencies named in those captions.

The screenshot `docs/images/cleanframe-tv-remote-buttons.jpg` was taken on the reference TV and shows, blurred and in the preview, a Bing photo of the day: *View of the Wroclaw Market Square from the Witches' Bridge in Wroclaw, Poland* (© Piotr Mitelski/Shutterstock). It is included only to illustrate the feature; the photographer retains all rights.

## Android libraries

The APK includes:

| Library | Licence |
| --- | --- |
| AndroidX WebKit (`androidx.webkit:webkit`) and the AndroidX libraries it depends on | Apache License 2.0 |
| Kotlin standard library | Apache License 2.0 |

Build and test only (not in the APK): Android Gradle Plugin and Gradle wrapper (Apache License 2.0), JUnit 4 (Eclipse Public License 1.0), JSON-java `org.json` (public domain).

## Development dependencies

Development dependencies, such as Playwright (Apache License 2.0), retain the licences in their npm packages. They are not copied into the interface or the APK.
