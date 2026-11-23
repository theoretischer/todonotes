package com.earendil.todonotes.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * Runde Checkbox (M14g): exakt der Stil der Aufgaben-Liste —
 * Icon Outline.Circle (leer, gedämpft) / Filled.CheckCircle
 * (angekreuzt, Akzent), 22dp. Ersetzt die eckige M3-Checkbox
 * überall (Dialoge, Notiz-Checklisten), damit die Form überall
 * identisch ist.
 */
@Composable
fun RoundCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 22.dp
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .let { m ->
                if (onCheckedChange != null) {
                    m.clickable(
                        role = Role.Checkbox,
                    onClickLabel = if (checked) "Als unerledigt markieren" else "Als erledigt markieren"
                    ) { onCheckedChange(!checked) }
                } else m
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (checked) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
            contentDescription = null,
            tint = if (checked) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(size)
        )
    }
}