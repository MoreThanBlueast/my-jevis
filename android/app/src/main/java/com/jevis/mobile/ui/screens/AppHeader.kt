package com.jevis.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jevis.mobile.ui.JevisBlue

@Composable
fun AppHeader(title: String, action: (@Composable BoxScope.() -> Unit)? = null) {
    Box(
        Modifier.fillMaxWidth().background(JevisBlue, RoundedCornerShape(18.dp)).padding(18.dp)
    ) {
        Text(title, color = Color.White, style = MaterialTheme.typography.headlineSmall)
        action?.invoke(this)
    }
}
