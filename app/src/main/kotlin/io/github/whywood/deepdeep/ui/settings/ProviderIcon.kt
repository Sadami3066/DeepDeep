package io.github.whywood.deepdeep.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.deepDeepColors

internal val ProviderDrawables: Map<String, Int> = mapOf(
    "amazon-bedrock" to R.drawable.provider_amazon_bedrock,
    "ant-ling" to R.drawable.provider_ant_ling,
    "anthropic" to R.drawable.provider_anthropic,
    "azure" to R.drawable.provider_azure,
    "baseten" to R.drawable.provider_baseten,
    "cerebras" to R.drawable.provider_cerebras,
    "cloudflare-ai-gateway" to R.drawable.provider_cloudflare_ai_gateway,
    "cloudflare-workers-ai" to R.drawable.provider_cloudflare_workers_ai,
    "deepseek" to R.drawable.provider_deepseek,
    "fireworks" to R.drawable.provider_fireworks,
    "github-copilot" to R.drawable.provider_github_copilot,
    "google" to R.drawable.provider_google,
    "google-vertex" to R.drawable.provider_google_vertex,
    "groq" to R.drawable.provider_groq,
    "huggingface" to R.drawable.provider_huggingface,
    "kimi-coding" to R.drawable.provider_kimi_coding,
    "meta" to R.drawable.provider_meta,
    "minimax" to R.drawable.provider_minimax,
    "minimax-cn" to R.drawable.provider_minimax_cn,
    "mistral" to R.drawable.provider_mistral,
    "moonshotai" to R.drawable.provider_moonshotai,
    "moonshotai-cn" to R.drawable.provider_moonshotai_cn,
    "nvidia" to R.drawable.provider_nvidia,
    "openai" to R.drawable.provider_openai,
    "openai-codex" to R.drawable.provider_openai_codex,
    "opencode" to R.drawable.provider_opencode,
    "opencode-go" to R.drawable.provider_opencode_go,
    "openrouter" to R.drawable.provider_openrouter,
    "qwen-token-plan" to R.drawable.provider_qwen_token_plan,
    "qwen-token-plan-cn" to R.drawable.provider_qwen_token_plan_cn,
    "qwen-token-plan-individual" to R.drawable.provider_qwen_token_plan_individual,
    "radius" to R.drawable.provider_radius,
    "together" to R.drawable.provider_together,
    "typesafe" to R.drawable.provider_typesafe,
    "vercel-ai-gateway" to R.drawable.provider_vercel_ai_gateway,
    "xai" to R.drawable.provider_xai,
    "xiaomi" to R.drawable.provider_xiaomi,
    "xiaomi-token-plan-ams" to R.drawable.provider_xiaomi_token_plan_ams,
    "xiaomi-token-plan-cn" to R.drawable.provider_xiaomi_token_plan_cn,
    "xiaomi-token-plan-sgp" to R.drawable.provider_xiaomi_token_plan_sgp,
    "zai" to R.drawable.provider_zai,
    "zai-coding-cn" to R.drawable.provider_zai_coding_cn,
)

@Composable
fun ProviderIcon(
    providerId: String,
    name: String,
    size: Dp = 36.dp,
    iconSize: Dp = 22.dp,
) {
    val drawable = ProviderDrawables[providerId]
    if (drawable == null) {
        InitialBadge(name, size)
        return
    }
    val colors = deepDeepColors()
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.wash),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(drawable),
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            contentScale = ContentScale.Fit,
        )
    }
}
