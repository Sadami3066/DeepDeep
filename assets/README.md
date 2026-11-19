# Asset sources

- [Brand assets](branding/README.md): app icon resources, wordmark rules, and bundled fonts.
- [Milo Modern icon set](milo-modern-icons-200-revised/README.md): 200 glyphs in regular and semibold weights. The app uses the regular weight; the icon catalog used by the build lives in [design/icons](../design/icons/README.md).
- [Icon catalog](../design/icons/README.md): the single source for app UI icons, the generator, and category previews. Runtime drawables are generated from it.
- [Provider icons](pi-provider-logos/README.md): model-provider logos with upstream sources, licenses, SHA-256 manifests, and a reproducible download script.
- [Vendor logos](vendor-logos/README.md): service and vendor logos with sources, licenses, and a regeneration script.

Original and candidate artwork stays here; only the exported files actually used by a module are added to that module. Raster files (`*.png`, `*.jpg`, `*.webp`) are kept locally and ignored by git; the source notes, manifests, licenses, and regeneration scripts are committed.
