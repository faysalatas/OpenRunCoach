package com.example.openruncoach.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.openruncoach.R

val BricolageGrotesque = FontFamily(
  Font(R.font.bricolagegrotesque_600, FontWeight.SemiBold),
  Font(R.font.bricolagegrotesque_700, FontWeight.Bold),
)

val Inter = FontFamily(
  Font(R.font.inter_400, FontWeight.Normal),
  Font(R.font.inter_500, FontWeight.Medium),
  Font(R.font.inter_600, FontWeight.SemiBold),
)

/** Exact named styles from redesign section 2.2; numbers use tabular figures. */
object OpenRunType {
  private fun display(size: Int, line: Int) = TextStyle(
    fontFamily = BricolageGrotesque,
    fontWeight = FontWeight.Bold,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = 0.sp,
    fontFeatureSettings = "tnum",
  )

  private fun text(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = Inter,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = 0.sp,
    fontFeatureSettings = "tnum",
  )

  val heroDisplay = display(56, 56)
  val pageTitle = display(44, 46)
  val subTitle = display(40, 44)
  val heroMetric = display(34, 38)
  val bigNumber = display(38, 42)
  val miniValue = display(28, 32)
  val cardTitleLg = display(20, 24)
  val title = text(16, 22, FontWeight.SemiBold)
  val body = text(16, 24)
  val bodySm = text(15, 22)
  val meta = text(14, 20)
  val caption = text(13, 19)
  val eyebrow = text(12, 16, FontWeight.SemiBold).copy(letterSpacing = 1.6.sp)
}

/** Existing MaterialTheme callers share the bundled fonts and named styles. */
val Typography = Typography(
  displayLarge = OpenRunType.heroDisplay,
  displayMedium = OpenRunType.pageTitle,
  displaySmall = OpenRunType.subTitle,
  headlineLarge = OpenRunType.heroMetric,
  headlineMedium = OpenRunType.bigNumber,
  headlineSmall = OpenRunType.miniValue,
  titleLarge = OpenRunType.cardTitleLg,
  titleMedium = OpenRunType.title,
  titleSmall = OpenRunType.title,
  bodyLarge = OpenRunType.body,
  bodyMedium = OpenRunType.bodySm,
  bodySmall = OpenRunType.caption,
  labelLarge = OpenRunType.meta.copy(fontWeight = FontWeight.SemiBold),
  labelMedium = OpenRunType.caption.copy(fontWeight = FontWeight.Medium),
  labelSmall = OpenRunType.eyebrow,
)
