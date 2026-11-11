package com.example.openruncoach.ui.health

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.openruncoach.theme.OpenRunCoachTheme

@Preview(name="Checkout light",widthDp=390,heightDp=844,uiMode=Configuration.UI_MODE_NIGHT_NO)
@Composable private fun CheckoutLight() { OpenRunCoachTheme { RunCheckoutContent(RunDetailPreviewData.state(),RunDetailPreviewData.clock) } }
@Preview(name="Checkout dark",widthDp=390,heightDp=844,uiMode=Configuration.UI_MODE_NIGHT_YES)
@Composable private fun CheckoutDark() { OpenRunCoachTheme { RunCheckoutContent(RunDetailPreviewData.state(),RunDetailPreviewData.clock) } }
@Preview(name="Checkout 130%",widthDp=390,heightDp=844,fontScale=1.3f)
@Composable private fun Checkout130() { OpenRunCoachTheme { RunCheckoutContent(RunDetailPreviewData.state(),RunDetailPreviewData.clock) } }
@Preview(name="Checkout 200%",widthDp=390,heightDp=844,fontScale=2f)
@Composable private fun Checkout200() { OpenRunCoachTheme { RunCheckoutContent(RunDetailPreviewData.state(),RunDetailPreviewData.clock) } }
