package com.example.openruncoach.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object OpenRunSpacing {
  val Micro = 4.dp
  val Small = 8.dp
  val Compact = 12.dp
  val Standard = 16.dp
  val Comfortable = 20.dp
  val Section = 24.dp
  val Major = 32.dp

  val ScreenHorizontal = Comfortable
  val CardContent = Standard
  val CardGap = 14.dp
  val GridGap = Compact
  val MiniCardVertical = 14.dp
  val PillMinHeight = 48.dp
  val ChipHeight = 32.dp
  val ProgressHeight = 10.dp
  val TouchTargetMin = 48.dp
  val Elevation = 0.dp
}

object OpenRunShapeTokens {
  val Compact = RoundedCornerShape(12.dp)
  val Button = RoundedCornerShape(26.dp)
  val Card = RoundedCornerShape(20.dp)
  val LargeCard = RoundedCornerShape(20.dp)
  val Row = RoundedCornerShape(16.dp)
  val Chip = RoundedCornerShape(16.dp)
  val Input = RoundedCornerShape(14.dp)
  val Progress = RoundedCornerShape(5.dp)
}

val OpenRunShapes = Shapes(
  extraSmall = RoundedCornerShape(8.dp),
  small = OpenRunShapeTokens.Compact,
  medium = OpenRunShapeTokens.Button,
  large = OpenRunShapeTokens.Card,
  extraLarge = OpenRunShapeTokens.LargeCard,
)
