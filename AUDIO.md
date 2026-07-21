# Audio and music

**Author:** cocomelonc<br>
**Copyright:** © 2026 cocomelonc (Zhassulan Zhussupov)<br>
**License:** MIT

Kitten Kingdoms contains no recorded, sampled, or third-party audio. Its
building-completed, technology-unlocked, and population-growth chimes are
synthesized at runtime by `AudioEngine.java`. The quiet 12-second kingdom
theme is an original composition synthesized and looped at runtime by
`MusicEngine.java`.

Both the audio code and the composition encoded by its note data are original
project work and are available under the repository's [MIT License](LICENSE).
They may be used, modified, and distributed under that license.

Background music plays only while a kingdom is active. It stops on pause,
title, app backgrounding, and view destruction, and it uses Android Audio
Focus so calls, accessibility tools, and other media can take priority.
