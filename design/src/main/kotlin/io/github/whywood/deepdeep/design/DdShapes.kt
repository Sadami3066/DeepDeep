package io.github.whywood.deepdeep.design

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

object DdRadius {
    val Small = 8.dp
    val Indicator = 11.dp
    val Medium = 12.dp
    val Segmented = 14.dp
    val Button = 15.dp
    val Large = 16.dp
    val Card = 18.dp
    val Row = 20.dp
    val Sheet = 28.dp
}

object DdShapes {
    val Pill: Shape = CircleShape
    val Small: Shape = RoundedCornerShape(DdRadius.Small)
    val Medium: Shape = RoundedCornerShape(DdRadius.Medium)
    val Segmented: Shape = RoundedCornerShape(DdRadius.Segmented)
    val Button: Shape = RoundedCornerShape(DdRadius.Button)
    val Large: Shape = RoundedCornerShape(DdRadius.Large)
    val Card: Shape = RoundedCornerShape(DdRadius.Card)
    val Row: Shape = RoundedCornerShape(DdRadius.Row)
    val Sheet: Shape = RoundedCornerShape(DdRadius.Sheet)
    val SheetTop: Shape = RoundedCornerShape(topStart = DdRadius.Sheet, topEnd = DdRadius.Sheet)
}
