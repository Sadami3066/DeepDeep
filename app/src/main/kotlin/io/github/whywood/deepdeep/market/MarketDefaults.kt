package io.github.whywood.deepdeep.market

object MarketDefaults {
    const val PAGE_BASE_URL = "https://sadami3066.github.io/deepdeep-market/"
    const val REGISTRY_URL = PAGE_BASE_URL + "registry.json"
    const val SIGNATURE_SUFFIX = ".sig"
    const val KEY_ID = "ec256:db63256431bd66db"
    const val PUBLIC_KEY_DER_BASE64 =
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEEycLAgCQiJ8JZqgHDTJdV5GqJobXPr6zfbr4GyL0UlXo19QnEtTfBLHo3uMaVHUVDkX67nDhkwqdp94JbIGONA=="
    const val CACHE_TTL_MS = 6L * 60L * 60L * 1000L
    const val MAX_REGISTRY_BYTES = 2L * 1024L * 1024L
    const val MAX_ENTRIES = 512
    const val MAX_PLUGIN_BYTES = 50L * 1024L * 1024L
    const val MAX_SKILL_BYTES = 20L * 1024L * 1024L
    const val MAX_ICON_BYTES = 128L * 1024L
    const val MARKET_DIR = "market"
}
