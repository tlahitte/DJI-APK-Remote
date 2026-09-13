package dev.djiremote.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.djiremote.camera.ExposurePreset
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable fun ExposureWheels(preset: ExposurePreset, onShutter: (Int) -> Unit, onIso: (Int) -> Unit) {
    val accent = Color(0xffFF5D64)
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color(0xff1b2532)),
        border = BorderStroke(1.dp, accent.copy(alpha = .4f))) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("EXPERIMENTAL · GLOBAL PRESET", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Wheel("Shutter speed", ExposurePreset.shutterDenominators, preset.shutterDenominator, { "1/$it" }, onShutter, Modifier.weight(1f))
                Wheel("ISO", ExposurePreset.isoValues, preset.iso, { it.toString() }, onIso, Modifier.weight(1f))
            }
            Text("Not sent to cameras", color = Color(0xffffca87), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text("Saved on this phone. Experimental choices—not confirmed camera settings. Availability depends on firmware and frame rate.",
                color = Color(0xffadbdcf), fontSize = 11.sp)
        }
    }
}
@Composable private fun Wheel(label: String, values: List<Int>, value: Int, format: (Int) -> String, onSelected: (Int) -> Unit, modifier: Modifier) {
    val list = rememberLazyListState(initialFirstVisibleItemIndex = values.indexOf(value).coerceAtLeast(0))
    val scope = rememberCoroutineScope()
    val update by rememberUpdatedState(onSelected)
    val selectedValue by rememberUpdatedState(value)
    LaunchedEffect(list, values) {
        snapshotFlow {
            if (list.isScrollInProgress) null else {
                val info = list.layoutInfo
                val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
                info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - center) }?.index
            }
        }.distinctUntilChanged().collect { index ->
            if (index != null && values[index] != selectedValue) update(values[index])
        }
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = Color(0xffadbdcf), fontSize = 12.sp)
        Box(Modifier.fillMaxWidth().height(180.dp).semantics { stateDescription = "$label ${format(value)}, local preset only" }, contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().height(36.dp).background(Color(0xffFF5D64).copy(alpha = .12f), RoundedCornerShape(10.dp))
                .border(1.dp, Color(0xffFF5D64).copy(alpha = .5f), RoundedCornerShape(10.dp)))
            LazyColumn(state = list, contentPadding = PaddingValues(vertical = 72.dp),
                flingBehavior = rememberSnapFlingBehavior(list), modifier = Modifier.fillMaxSize()) {
                itemsIndexed(values, key = { _, item -> item }) { index, item ->
                    Box(Modifier.fillMaxWidth().height(36.dp).clickable { scope.launch { list.animateScrollToItem(index) } }, contentAlignment = Alignment.Center) {
                        Text(format(item), fontSize = if (item == value) 21.sp else 16.sp,
                            fontWeight = if (item == value) FontWeight.Bold else FontWeight.Normal,
                            color = if (item == value) Color(0xffFF5D64) else Color(0xff879bb2))
                    }
                }
            }
        }
    }
}
