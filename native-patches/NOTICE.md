# Friends Only native patch work

Copyright (C) 2026 Friends Only contributors.

This native patch module is licensed under the GNU General Public License
version 3. See [LICENSE](LICENSE), copied verbatim from the upstream project.
It is separate from the pre-existing WebView Android application in this
repository.

## Adapted source

Selected bytecode fingerprint/injection and native resource-name filtering
approaches are adapted from **FeurStagram**:

- Source: <https://github.com/jean-voila/FeurStagram>
- Reviewed source commit: `3e02bcc99e874b5195b90e0a1748a4a218f822ba`
- Copyright (C) Feurstagram contributors.
- Upstream license: GNU General Public License v3.0.

Friends Only modifies that work into its own small patch set and native
extension: scoped tab visibility, local settings, and exact Reels/Explore
collection-request suppression. We do not distribute upstream’s prepatched
Instagram APKs or include its clone, updater, debug bridge, broader feed
filtering, or signature-check bypass patches in this module.

## Preserved upstream acknowledgements

FeurStagram’s NOTICE attributes these GPLv3 works:

- **Morphe** — <https://morphe.software>. Patcher and tooling. Following the
  upstream notice concerning GPLv3 Section 7c, the Morphe name is used here
  descriptively for compatibility only. Friends Only is independent and does
  not imply Morphe authorship, endorsement, or affiliation.
- **Piko** — <https://github.com/crimera/piko>. Upstream identifies some
  Instagram patch approaches, notably network blocking and cloning, as adapted
  from Piko. Friends Only retains that acknowledgement; its initial patch set
  does not include a clone patch.

Tools downloaded during the build retain their respective licenses. This
module’s GPL license does not grant rights to Instagram binaries or branding.
Instagram remains the property/trademark of its respective owner. No Meta or
Instagram affiliation or approval is claimed.
