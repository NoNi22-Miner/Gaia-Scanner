package com.gaiascanner.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.gaiascanner.app.R
import com.gaiascanner.app.core.MarginOption
import com.gaiascanner.app.core.PageSizeOption

@Composable
fun pageSizeLabel(option: PageSizeOption): String = stringResource(
    when (option) {
        PageSizeOption.FIT -> R.string.page_size_fit
        PageSizeOption.A4 -> R.string.page_size_a4
        PageSizeOption.LETTER -> R.string.page_size_letter
    },
)

@Composable
fun marginLabel(option: MarginOption): String = stringResource(
    when (option) {
        MarginOption.NONE -> R.string.margin_none
        MarginOption.SMALL -> R.string.margin_small
        MarginOption.LARGE -> R.string.margin_large
    },
)
