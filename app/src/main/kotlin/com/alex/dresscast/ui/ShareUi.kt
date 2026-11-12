package com.alex.dresscast.ui

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alex.dresscast.AppState
import com.alex.dresscast.L.t
import com.alex.dresscast.ShareCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(s: AppState, pal: Pal, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val fc = s.forecast ?: return
    val p = s.prefs
    var style by remember { mutableIntStateOf(p.shareStyle) }
    var city by remember { mutableStateOf(p.shareCity) }
    val bmp = remember(style, city, fc) { runCatching { ShareCard.render(ctx, fc, p, style, city) }.getOrNull() }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = pal.card) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(t("Share", "Κοινοποίηση"), color = pal.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = Fonts.display)
            if (bmp != null) Image(
                bmp.asImageBitmap(), t("Preview of the picture to share", "Προεπισκόπηση της εικόνας"),
                Modifier.fillMaxWidth().aspectRatio(ShareCard.W / ShareCard.H.toFloat()).clip(RoundedCornerShape(24.dp)),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    Triple(t("Weather", "Καιρού"), Color(0xFF8EC0EE), Color(0xFFF6F1E8)),
                    Triple(t("Light", "Φωτεινό"), Color(0xFFFFFFFF), Color(0xFFF3F5F8)),
                    Triple(t("Dark", "Σκούρο"), Color(0xFF1B2433), Color(0xFF273244)),
                ).forEachIndexed { i, (label, a, b) ->
                    val on = style == i
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                            .clickable(role = Role.RadioButton, onClickLabel = label) { style = i; p.shareStyle = i },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(14.dp))
                                .background(Brush.verticalGradient(0f to a, 0.45f to a, 0.45f to b, 1f to b))
                                .border(if (on) 2.5.dp else 1.5.dp, if (on) pal.accent else pal.track, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.TopEnd,
                        ) {
                            if (on) Box(Modifier.padding(4.dp).size(18.dp).clip(CircleShape).background(pal.accent), contentAlignment = Alignment.Center) {
                                Icon(Ico.check, null, Modifier.size(12.dp), tint = pal.onAccent)
                            }
                        }
                        Text(label, color = if (on) pal.accent else pal.cardMuted, fontSize = 12.sp,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
            SettingRow(t("Show my city", "Να φαίνεται η πόλη"), null, pal) {
                Switch(city, { city = it; p.shareCity = it })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (Build.VERSION.SDK_INT >= 29) Pill(t("Save", "Αποθήκευση"), pal, Modifier.weight(1f)) {
                    val ok = bmp != null && ShareCard.save(ctx, bmp)
                    Toast.makeText(ctx, if (ok) t("Saved to Pictures/Clymo", "Αποθηκεύτηκε στις Εικόνες/Clymo") else t("Couldn't save the picture", "Δεν αποθηκεύτηκε η εικόνα"), Toast.LENGTH_SHORT).show()
                }
                Pill(t("Share", "Κοινοποίηση"), pal, Modifier.weight(1f), primary = true) {
                    if (bmp != null) ShareCard.share(ctx, bmp)
                }
            }
        }
    }
}
