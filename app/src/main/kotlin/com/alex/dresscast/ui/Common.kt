package com.alex.dresscast.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alex.dresscast.AppState
import com.alex.dresscast.Loc

@Composable
fun Card(pal: Pal, modifier: Modifier = Modifier, padding: Int = 16, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(pal.card)
            .padding(padding.dp),
        content = content,
    )
}

@Composable
fun Label(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
}

@Composable
fun Pill(
    text: String,
    pal: Pal,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .heightIn(min = 46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (primary) pal.accent else pal.tint)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp), tint = if (primary) pal.onAccent else pal.accent)
            Text(" ")
        }
        Text(text, color = if (primary) pal.onAccent else pal.accent, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

/** A row of options where exactly one is chosen. */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, pal: Pal, onSelect: (T) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(pal.track)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (on) pal.card else Color.Transparent)
                    .selectable(selected = on, role = Role.RadioButton) { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (on) pal.ink else pal.cardMuted, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun CityDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Type a city") },
        text = {
            OutlinedTextField(text, { text = it }, singleLine = true, placeholder = { Text("e.g. Thessaloniki") })
        },
        confirmButton = { TextButton({ onSave(text); onDismiss() }) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

/** Returns an action that asks for location permission if needed, then finds the phone's place. */
@Composable
fun rememberLocate(s: AppState): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) s.locate() else s.status = "Location permission was refused. You can type a city instead."
    }
    return {
        if (Loc.hasPerm(s.ctx)) s.locate()
        else launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }
}

fun pickTime(ctx: Context, hour: Int, minute: Int, onPick: (Int, Int) -> Unit) {
    TimePickerDialog(ctx, { _, h, m -> onPick(h, m) }, hour, minute, true).show()
}
