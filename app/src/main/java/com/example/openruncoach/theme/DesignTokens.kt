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
  val CardContent = Comfortable
}

object OpenRunShapeTokens {
  val Compact = RoundedCornerShape(12.dp)
  val Button = RoundedCornerShape(14.dp)
  val Card = RoundedCornerShape(18.dp)
  val LargeCard = RoundedCornerShape(20.dp)
}

val OpenRunShapes = Shapes(
  extraSmall = RoundedCornerShape(8.dp),
  small = OpenRunShapeTokens.Compact,
  medium = OpenRunShapeTokens.Button,
  large = OpenRunShapeTokens.Card,
  extraLarge = OpenRunShapeTokens.LargeCard,
)
