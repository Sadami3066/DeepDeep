package io.github.whywood.deepdeep.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdSpacing
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors

@Composable
fun AgreementScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = DdSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DdIconButton(
                icon = DdIcons.ArrowLeft,
                onClick = onBack,
                contentDescription = stringResource(R.string.settings_back),
                tint = colors.ink,
                iconSize = 21.dp,
            )
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DdSpacing.ScreenHorizontal),
        ) {
            Text(
                text = stringResource(R.string.settings_privacy_agreement),
                style = DdType.pageTitle,
                color = colors.ink,
            )
            Spacer(Modifier.height(DdSpacing.Xl))
            Text(
                text = stringResource(R.string.agreement_intro),
                style = DdType.bodySmall,
                color = colors.inkSecondary,
            )
            Spacer(Modifier.height(DdSpacing.Xxl))
            AgreementSection(
                title = R.string.agreement_data_title,
                paragraphs = listOf(
                    R.string.agreement_data_local,
                    R.string.agreement_data_provider,
                    R.string.agreement_data_connectors,
                    R.string.agreement_data_tracking,
                    R.string.agreement_data_uninstall,
                ),
            )
            Spacer(Modifier.height(DdSpacing.Xxl))
            DdHairline()
            Spacer(Modifier.height(DdSpacing.Xxl))
            AgreementSection(
                title = R.string.agreement_terms_title,
                paragraphs = listOf(
                    R.string.agreement_terms_lawful,
                    R.string.agreement_terms_responsibility,
                ),
            )
            Spacer(Modifier.height(DdSpacing.Xxl))
            DdHairline()
            Spacer(Modifier.height(DdSpacing.Xxl))
            AgreementSection(
                title = R.string.agreement_disclaimer_title,
                paragraphs = listOf(
                    R.string.agreement_disclaimer_as_is,
                    R.string.agreement_disclaimer_liability,
                    R.string.agreement_disclaimer_interruption,
                    R.string.agreement_disclaimer_output,
                ),
            )
            Spacer(Modifier.height(DdSpacing.Xxl))
            DdHairline()
            Spacer(Modifier.height(DdSpacing.Xxl))
            AgreementSection(
                title = R.string.agreement_license_title,
                paragraphs = listOf(
                    R.string.agreement_license_apache,
                    R.string.agreement_license_source,
                ),
            )
            Spacer(Modifier.height(DdSpacing.Huge))
        }
    }
}

@Composable
private fun AgreementSection(
    title: Int,
    paragraphs: List<Int>,
) {
    val colors = deepDeepColors()
    Text(
        text = stringResource(title),
        style = DdType.heading,
        color = colors.ink,
    )
    Spacer(Modifier.height(DdSpacing.Md))
    paragraphs.forEachIndexed { index, paragraph ->
        if (index > 0) {
            Spacer(Modifier.height(DdSpacing.Md))
        }
        Text(
            text = stringResource(paragraph),
            style = DdType.body,
            color = colors.inkSecondary,
        )
    }
}
