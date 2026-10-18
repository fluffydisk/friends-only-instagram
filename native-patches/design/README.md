# Friends Only native design assets

Manrope variable font from Google Fonts, downloaded 2026-10-18:
https://github.com/google/fonts/tree/main/ofl/manrope

- `Manrope.ttf`: SHA-256 `3ae11c49db0455a3cc33e37d380f20fdb8c7f8b41dc07625c177e3d87a9d6ae6`
- `OFL-Manrope.txt`: SHA-256 `58172e0c0fac2cda8a37b348164bb55e44b0e69051e557e92b1d3f6910141f7b`

The SIL Open Font License and font are both packaged with the native APK.
The patch validates their pinned digests. Runtime uses the local asset only;
there is no font download or new network request on a phone. A system font
fallback prevents a missing/corrupt optional font from stopping native UI.

Design follows the project-local Taste `minimalist-ui` and
`redesign-existing-projects` skills. Web-only decorative patterns and animations
are excluded according to the user's minimalist, distraction-free requirement.

## Native wordmark assets

`wordmark-{xhdpi,night-xhdpi,xxhdpi,night-xxhdpi}.webp` are Friends Only text
rasters generated offline from the same bundled Manrope font, not Instagram
artwork or user images. They replace only the pinned native wordmark drawable's
four variants, retaining its ID and original 233×66 / 349×99 dimensions.
`wordmark-manifest.json` pins each file's size, SHA-256, CRC32 and dimensions;
the signed-output validator checks the four actual resource files independently.

Optional developer reproduction: install Pillow/fonttools in an isolated Python
environment, then run `python3 native-patches/design/generate_wordmark.py`.
Normal APK/CI builds use checked-in rasters and need neither dependency.
Provider/legal disclosures and native authentication are retained; this resource
replacement does not guarantee all server-rendered marks have been replaced.
