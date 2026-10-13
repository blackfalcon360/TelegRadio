# Telegradio 📻

An internet-radio app in the style of telegradio.com (MiniRadio): 50,000+ stations from every country. Portrait only.

- **📡 Tuner:** a radio dial. Drag it and you hear the **static hiss between stations** (made on the phone, no sound file), stop on a station and it tunes in. Prev / next buttons, heart, and a source picker (World, your saved stations, Pakistan, India, Saudi Arabia, UAE, UK, USA, Turkey, Afghanistan, Bangladesh, or any country).
- **🔎 Stations:** search by name with the same tuning static while it looks. Quick filters (Popular, Pakistan, Urdu, News, Quran, Islamic, Pop, Hindi, Arabic, Sports, Jazz); keeps loading more as you scroll.
- **🌍 Countries:** every country with its flag and station count, searchable.
- **🎼 Genres:** 33 genres (News & Talk, Sports, Quran, Islamic, Pop, Rock, Hip-Hop, Jazz, Classical, Latin, Metal, EDM, K-Pop …).
- **♥ Saved:** your favourite stations, stored on the phone.
- **Mini player + full "Now Playing" screen:** station logo, live song title when the station sends it, dancing equalizer, play/pause, heart, share, **sleep timer** (15 min – 1.5 h).
- Keeps playing in the background with a notification (pause / stop). Back button sends the app to the background instead of stopping the radio.
- Plays MP3, AAC, Ogg and HLS streams (ExoPlayer).

Station data comes from the open Radio Browser database over the internet; needs internet to play.
Brought to you By: Black Falcon 🦅

## Build
Push to GitHub -> Actions -> "Build APK" -> download `Telegradio-APK`.
Local: `gradle assembleDebug` (JDK 17, Gradle 8.7+). Needs Android 6.0 (API 23) or newer.
