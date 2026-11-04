package com.benchfirewatch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.benchfirewatch.model.TemperatureScale

@Composable
fun InfoTip(detail: String, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = modifier.size(40.dp)) {
        Icon(Icons.Outlined.Info, contentDescription = "More about this", tint = chromeMuted())
    }
    if (open) {
        HoldPagerWhileShown()
        AlertDialog(
            onDismissRequest = { open = false },
            containerColor = chromePanel(),
            textContentColor = chromePaper(),
            text = { Text(detail) },
            confirmButton = {
                TextButton(onClick = { open = false }) { Text("OK") }
            },
        )
    }
}

@Composable
fun ToggleRow(
    title: String,
    defaultOn: Boolean,
    detail: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .weight(1f)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onChecked),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = chromePaper())
                Text(
                    if (defaultOn) "Default on" else "Default off",
                    color = chromeMuted(),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(checked = checked, onCheckedChange = null)
        }
        InfoTip(detail)
    }
}

@Composable
fun NumberRow(
    label: String,
    unit: String,
    defaultText: String,
    detail: String,
    value: Float,
    min: Float,
    max: Float,
    generation: Int,
    whole: Boolean = false,
    onValue: (Float) -> Unit,
) {
    var text by remember(label, generation) { mutableStateOf(trimNumber(value)) }
    val parsed = if (whole) acceptedWholeNumber(text, min.toInt(), max.toInt())?.toFloat() else acceptedNumber(text, min, max)
    OutlinedTextField(
        value = text,
        onValueChange = { next ->
            text = next
            val accepted = if (whole) {
                acceptedWholeNumber(next, min.toInt(), max.toInt())?.toFloat()
            } else {
                acceptedNumber(next, min, max)
            }
            if (accepted != null) onValue(accepted)
        },
        modifier = Modifier.fillMaxWidth().keepSwipeOffPager(),
        label = { Text(label) },
        supportingText = { Text("Default $defaultText") },
        isError = parsed == null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (whole) KeyboardType.Number else KeyboardType.Decimal,
            imeAction = ImeAction.Next,
        ),
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (unit.isNotEmpty()) Text(unit, color = chromeMuted(), style = MaterialTheme.typography.labelLarge)
                InfoTip(detail)
            }
        },
    )
}

@Composable
fun TextFieldRow(
    label: String,
    summary: String,
    detail: String,
    value: String,
    hide: Boolean = false,
    onValue: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        modifier = Modifier.fillMaxWidth().keepSwipeOffPager(),
        label = { Text(label) },
        supportingText = { Text(summary) },
        singleLine = true,
        visualTransformation = if (hide) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        trailingIcon = { InfoTip(detail) },
    )
}

@Composable
fun SegmentChoice(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().keepSwipeOffPager()) {
        options.forEachIndexed { index, (id, label) ->
            SegmentedButton(
                selected = selected == id,
                onClick = { onSelect(id) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                icon = {},
            ) {
                Text(label, maxLines = 1, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun TemperatureRow(
    label: String,
    unit: String,
    defaultCelsius: Float,
    detail: String,
    celsius: Float,
    fahrenheit: Boolean,
    generation: Int,
    absolute: Boolean = true,
    rate: Boolean = false,
    onCelsius: (Float) -> Unit,
) {
    val scaleAbsolute = absolute && !rate
    val shown = TemperatureScale.toDisplay(celsius, fahrenheit, scaleAbsolute)
    val defaultShown = TemperatureScale.toDisplay(defaultCelsius, fahrenheit, scaleAbsolute)
    val (min, max) = TemperatureScale.sliderRange(fahrenheit, absolute, rate)
    var text by remember(label, generation, fahrenheit) { mutableStateOf(trimNumber(shown.toFloat())) }
    val parsed = TemperatureScale.acceptedTemperature(text)
    val displayUnit = if (fahrenheit) unit.replace("°C", "°F") else unit
    val outside = parsed != null && (parsed < min.toDouble() || parsed > max.toDouble())
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = text,
            onValueChange = { next ->
                text = next
                val accepted = TemperatureScale.acceptedTemperature(next)
                if (accepted != null) onCelsius(TemperatureScale.fromDisplay(accepted, fahrenheit, scaleAbsolute))
            },
            modifier = Modifier.fillMaxWidth().keepSwipeOffPager(),
            label = { Text(label) },
            supportingText = {
                Text(
                    if (outside) {
                        "Default ${trimNumber(defaultShown.toFloat())}. Outside the slider. The typed number is saved."
                    } else {
                        "Default ${trimNumber(defaultShown.toFloat())}. Type any number."
                    },
                )
            },
            isError = parsed == null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (displayUnit.isNotEmpty()) {
                        Text(displayUnit, color = chromeMuted(), style = MaterialTheme.typography.labelLarge)
                    }
                    InfoTip(detail)
                }
            },
        )
        Slider(
            value = TemperatureScale.sliderThumb(shown.toFloat(), min, max),
            onValueChange = { next ->
                val stored = TemperatureScale.fromDisplay(next.toDouble(), fahrenheit, scaleAbsolute)
                text = trimNumber(TemperatureScale.toDisplay(stored, fahrenheit, scaleAbsolute).toFloat())
                onCelsius(stored)
            },
            valueRange = min..max,
            modifier = Modifier.fillMaxWidth().keepSwipeOffPager(),
        )
    }
}

fun trimNumber(value: Float): String {
    val rounded = kotlin.math.round(value * 1000f) / 1000f
    return if (rounded == rounded.toInt().toFloat()) rounded.toInt().toString() else rounded.toString()
}
