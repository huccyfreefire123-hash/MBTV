package com.ondatv.player.ui

import android.widget.Toast
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ondatv.player.AppViewModel
import com.ondatv.player.BuildConfig
import com.ondatv.player.data.DEFAULT_PROFILE
import com.ondatv.player.data.Profile
import com.ondatv.player.data.Source
import com.ondatv.player.data.SourceType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------- Definições ----------

/** Colours of the settings screen: flat AMOLED, without gradients, glow or strong shadows. */
private object SettingsColors {
    val background = Color.Black
    val block = Color(0xFF141414)
    val divider = Color(0xFF262626)
    val text = Color(0xFFF5F5F5)
    val textSecondary = Color(0xFF8E8E8E)
    val accent = Color(0xFF179EF8)
    val danger = Color(0xFFE5484D)
    val warning = Color(0xFFF2A93B)
}

/** The settings screen with its state and dialogs; [SettingsContent] only draws it. */
@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit, openProfiles: () -> Unit) {
    val sources by vm.sources.collectAsStateWithLifecycle()
    vm.profileId.collectAsStateWithLifecycle().value
    vm.profiles.collectAsStateWithLifecycle().value
    val loading by vm.loading.collectAsStateWithLifecycle()
    val parentalOn by vm.parentalOn.collectAsStateWithLifecycle()
    val pinCreated by vm.pinCreated.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var adding by remember { mutableStateOf<SourceType?>(null) }
    var removing by remember { mutableStateOf<Source?>(null) }
    var editing by remember { mutableStateOf<Source?>(null) }
    val message by vm.message.collectAsStateWithLifecycle()
    var askPinToToggle by remember { mutableStateOf(false) }
    var changingPin by remember { mutableStateOf(false) }
    var creatingPin by remember { mutableStateOf(false) }

    SettingsContent(
        profile = vm.currentProfile(),
        // One list at a time.
        source = sources.firstOrNull(),
        loading = loading,
        error = message,
        parentalOn = parentalOn,
        pinCreated = pinCreated,
        version = BuildConfig.VERSION_NAME,
        onBack = onBack,
        onProfile = openProfiles,
        onRefresh = { vm.refresh() },
        onEditSource = { editing = sources.firstOrNull() },
        onRemoveSource = { removing = it },
        onAddSource = { adding = it },
        // Without a PIN of the user yet, it is created first.
        onToggleParental = { if (pinCreated) askPinToToggle = true else creatingPin = true },
        onPin = { if (pinCreated) changingPin = true else creatingPin = true },
    )

    // Only reachable while there is no list; addSource refuses a second one anyway.
    adding?.takeIf { sources.isEmpty() }?.let { type ->
        SourceDialog(type, onDismiss = { adding = null }) {
            vm.addSource(it)
            adding = null
        }
    }
    // The same form with what the list has now; saving keeps profiles, favourites and progress.
    editing?.let { current ->
        SourceDialog(current.type, initial = current, onDismiss = { editing = null }) {
            vm.updateSource(it)
            editing = null
        }
    }
    removing?.let { s ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Excluir \"${s.name}\"?") },
            text = {
                Text(
                    "Canais, filmes e séries dessa lista vão sumir do app. " +
                        "Os perfis, os favoritos e o progresso também são apagados."
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.removeSource(s.id); removing = null }) {
                    Text("Excluir", color = SettingsColors.danger)
                }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancelar") } },
        )
    }
    if (creatingPin) {
        CreatePinDialog(vm, onDone = {
            creatingPin = false
            Toast.makeText(context, "Senha criada", Toast.LENGTH_SHORT).show()
        }, onDismiss = { creatingPin = false })
    }
    if (askPinToToggle) {
        PinPad(
            title = if (parentalOn) "Desativar proteção" else "Ativar proteção",
            subtitle = "Digite a senha atual do Controle Parental",
            check = { vm.setParentalEnabled(!parentalOn, it) },
            onSuccess = {
                askPinToToggle = false
                Toast.makeText(context, if (parentalOn) "Proteção desativada" else "Proteção ativada", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { askPinToToggle = false },
        )
    }
    if (changingPin) {
        ChangePinDialog(vm, onDone = {
            changingPin = false
            Toast.makeText(context, "Senha alterada com sucesso", Toast.LENGTH_SHORT).show()
        }, onDismiss = { changingPin = false })
    }
}

/** What the settings screen shows, from plain values, so it can be drawn in a preview. */
@Composable
private fun SettingsContent(
    profile: Profile,
    source: Source?,
    loading: Boolean,
    // Why the list could not be loaded (wrong address or login, no connection); null when it loaded.
    error: String?,
    parentalOn: Boolean,
    pinCreated: Boolean,
    version: String,
    onBack: () -> Unit,
    onProfile: () -> Unit,
    onRefresh: () -> Unit,
    onEditSource: () -> Unit,
    onRemoveSource: (Source) -> Unit,
    onAddSource: (SourceType) -> Unit,
    onToggleParental: () -> Unit,
    onPin: () -> Unit,
) {
    val c = SettingsColors
    Column(
        Modifier.fillMaxSize().background(c.background).verticalScroll(rememberScrollState()).statusBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = c.text, modifier = Modifier.size(22.dp))
            }
            Text(
                "Definições", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, modifier = Modifier.padding(start = 4.dp),
            )
        }
        // Same height with or without the bar, so the blocks do not jump while a list loads.
        Box(Modifier.fillMaxWidth().height(4.dp)) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxSize(), color = c.accent, trackColor = c.divider)
        }

        Block(Modifier.padding(top = 4.dp)) {
            Row(
                Modifier.fillMaxWidth().settingsClickable(onProfile).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProfileAvatar(profile)
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(
                        profile.name, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text("Trocar perfil", color = c.textSecondary, fontSize = 13.sp)
                }
                Chevron()
            }
        }

        SectionTitle("Lista")
        Block {
            if (source != null) {
                SettingRow(Icons.AutoMirrored.Filled.List, source.name, bold = true, status = { SourceStatus(source) })
                if (error != null && !loading) {
                    Text(
                        error, color = c.danger, fontSize = 13.sp, lineHeight = 18.sp,
                        modifier = Modifier.padding(start = 56.dp, end = 16.dp, bottom = 12.dp),
                    )
                }
                RowDivider()
                SettingRow(
                    Icons.Filled.Refresh, "Atualizar lista", subtitle = if (loading) "Atualizando..." else null,
                    onClick = onRefresh, trailing = { Chevron() },
                )
                RowDivider()
                SettingRow(Icons.Filled.Edit, "Editar endereço da lista", onClick = onEditSource, trailing = { Chevron() })
                RowDivider()
                SettingRow(
                    Icons.Filled.Delete, "Excluir lista", color = c.danger, iconTint = c.danger,
                    onClick = { onRemoveSource(source) },
                )
            } else {
                SettingRow(Icons.AutoMirrored.Filled.List, "Nenhuma lista cadastrada", subtitle = "Escolha como conectar")
                RowDivider()
                SettingRow(Icons.Filled.Add, "Adicionar lista M3U", onClick = { onAddSource(SourceType.M3U) }, trailing = { Chevron() })
                RowDivider()
                SettingRow(Icons.Filled.Add, "Adicionar Xtream Codes", onClick = { onAddSource(SourceType.XTREAM) }, trailing = { Chevron() })
            }
        }

        SectionTitle("Controle parental")
        Block {
            SettingRow(
                Icons.Filled.Lock, "Bloquear conteúdo adulto",
                subtitle = when {
                    !pinCreated -> "Crie uma senha para ativar"
                    parentalOn -> "Pede senha para abrir"
                    else -> "Desativado: abre sem senha"
                },
                onClick = onToggleParental,
                trailing = {
                    // Only shows the state: the whole row is what takes the tap (and the remote's focus).
                    Switch(
                        checked = pinCreated && parentalOn, onCheckedChange = null, enabled = pinCreated,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = c.accent, checkedThumbColor = Color.White,
                            uncheckedTrackColor = c.divider, uncheckedThumbColor = c.textSecondary,
                            uncheckedBorderColor = c.divider,
                            disabledUncheckedTrackColor = c.divider,
                            disabledUncheckedThumbColor = c.textSecondary.copy(alpha = 0.5f),
                            disabledUncheckedBorderColor = c.divider,
                        ),
                    )
                },
            )
            RowDivider()
            SettingRow(null, if (pinCreated) "Alterar senha" else "Criar senha", onClick = onPin, trailing = { Chevron() })
        }

        SectionTitle("Sobre")
        Block {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Versão", color = c.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Text(version, color = c.textSecondary, fontSize = 16.sp)
            }
        }
        Text(
            "O MBTV é só um player: não fornece canais, filmes nem assinaturas. " +
                "Use apenas listas que você tem direito de assistir.",
            color = c.textSecondary, fontSize = 12.sp, lineHeight = 17.sp,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp),
        )
        // Room for the floating tab bar.
        Spacer(Modifier.navigationBarsPadding().height(120.dp))
    }
}

/** A group of options: rounded block with a side margin. */
@Composable
private fun Block(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SettingsColors.block),
        content = content,
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text, color = SettingsColors.textSecondary, fontSize = 13.sp,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp),
    )
}

/** Line between two rows of a block, starting where the text starts. */
@Composable
private fun RowDivider() {
    HorizontalDivider(color = SettingsColors.divider, thickness = 1.dp, modifier = Modifier.padding(start = 56.dp))
}

@Composable
private fun Chevron() {
    Icon(
        Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
        tint = SettingsColors.textSecondary, modifier = Modifier.size(22.dp),
    )
}

/**
 * One option: icon column (22 dp, kept empty when there is no icon so the texts line up), title,
 * an optional second line ([subtitle], or [status] for something richer) and what goes at the end.
 */
@Composable
private fun SettingRow(
    icon: ImageVector?,
    title: String,
    subtitle: String? = null,
    color: Color = SettingsColors.text,
    iconTint: Color = SettingsColors.textSecondary,
    bold: Boolean = false,
    onClick: (() -> Unit)? = null,
    status: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.settingsClickable(onClick) else Modifier)
            .heightIn(min = 56.dp).padding(start = 18.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(22.dp)) {
            if (icon != null) Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(
                title, color = color, fontSize = 16.sp,
                fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            when {
                status != null -> status()
                subtitle != null -> Text(subtitle, color = SettingsColors.textSecondary, fontSize = 13.sp)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/** A tap with the usual ripple; with a TV remote or a keyboard, the row with the focus gets an outline. */
@Composable
private fun Modifier.settingsClickable(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    return this
        .then(if (focused) Modifier.border(2.dp, SettingsColors.accent, RoundedCornerShape(12.dp)) else Modifier)
        .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
}

/** The picture chosen for the profile, or its initial on the accent colour. */
@Composable
private fun ProfileAvatar(profile: Profile) {
    if (profile.avatar != 0) {
        Avatar(profile, 44.dp)
        return
    }
    Box(Modifier.size(44.dp).clip(CircleShape).background(SettingsColors.accent), contentAlignment = Alignment.Center) {
        Text(
            profile.name.trim().take(1).uppercase().ifEmpty { "?" },
            color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
        )
    }
}

private val expiryDate = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))

/**
 * Second line of the list: its kind and, when the server reports an end date, "Válida até" in
 * grey or, once it has passed, "Venceu em" with an amber dot.
 */
@Composable
private fun SourceStatus(source: Source) {
    val c = SettingsColors
    val kind = if (source.type == SourceType.M3U) "M3U" else "Xtream"
    val expiresAt = source.expiresAt
    if (expiresAt == null) {
        Text(kind, color = c.textSecondary, fontSize = 13.sp)
        return
    }
    val date = expiryDate.format(Date(expiresAt * 1000))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(kind + DOT, color = c.textSecondary, fontSize = 13.sp)
        if (expiresAt * 1000 <= System.currentTimeMillis()) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(c.warning))
            Text("Venceu em $date", color = c.warning, fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(start = 5.dp))
        } else {
            Text("Válida até $date", color = c.textSecondary, fontSize = 13.sp, maxLines = 1)
        }
    }
}

@Preview(widthDp = 360, heightDp = 800)
@Composable
private fun SettingsPreview() {
    OndaTheme {
        SettingsContent(
            profile = Profile(DEFAULT_PROFILE, "Eu", 0, null),
            source = Source("1", "megabyte", SourceType.M3U, url = "http://exemplo.com/lista.m3u", expiresAt = 1_794_268_800),
            loading = false,
            error = null,
            parentalOn = true,
            pinCreated = true,
            version = "0.2.1",
            onBack = {}, onProfile = {}, onRefresh = {}, onEditSource = {}, onRemoveSource = {},
            onAddSource = {}, onToggleParental = {}, onPin = {},
        )
    }
}

// ---------- Diálogos ----------

@Composable
private fun ChangePinDialog(vm: AppViewModel, onDone: () -> Unit, onDismiss: () -> Unit) {
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    @Composable
    fun PinField(value: String, label: String, onChange: (String) -> Unit) {
        OutlinedTextField(
            value, { v -> onChange(v.filter { it.isDigit() }.take(8)); error = null },
            label = { Text(label) }, singleLine = true, modifier = Modifier.formField(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Alterar senha") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PinField(current, "Senha atual") { current = it }
                PinField(new, "Nova senha (4 a 8 números)") { new = it }
                PinField(confirm, "Confirmar nova senha") { confirm = it }
                error?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 14.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when (vm.changeParentalPin(current, new, confirm)) {
                    AppViewModel.PinChange.OK -> onDone()
                    AppViewModel.PinChange.WRONG_CURRENT -> error = "Senha atual incorreta. Tente novamente."
                    AppViewModel.PinChange.TOO_SHORT -> error = "A nova senha deve ter de 4 a 8 números."
                    AppViewModel.PinChange.MISMATCH -> error = "A confirmação não corresponde à nova senha."
                }
            }) { Text("Salvar nova senha") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/**
 * The form of a list. With [initial] it edits that list (same fields, already filled in); without,
 * it adds one. The keyboard's action key goes to the next field and closes the keyboard on the last.
 */
@Composable
private fun SourceDialog(type: SourceType, initial: Source? = null, onDismiss: () -> Unit, onSave: (Source) -> Unit) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var url by remember { mutableStateOf(initial?.url.orEmpty()) }
    var epg by remember { mutableStateOf(initial?.epgUrl.orEmpty()) }
    var server by remember { mutableStateOf(initial?.server.orEmpty()) }
    var user by remember { mutableStateOf(initial?.username.orEmpty()) }
    var pass by remember { mutableStateOf(initial?.password.orEmpty()) }
    val valid = if (type == SourceType.M3U) url.isNotBlank() else server.isNotBlank() && user.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    initial == null -> if (type == SourceType.M3U) "Lista M3U" else "Xtream Codes"
                    type == SourceType.M3U -> "Editar lista M3U"
                    else -> "Editar Xtream Codes"
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    name, { name = it }, label = { Text("Nome (opcional)") }, singleLine = true, modifier = Modifier.formField(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                if (type == SourceType.M3U) {
                    OutlinedTextField(
                        url, { url = it }, label = { Text("Endereço da lista (http...)") }, singleLine = true, modifier = Modifier.formField(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    )
                    OutlinedTextField(
                        epg, { epg = it }, label = { Text("Guia EPG XMLTV (opcional)") }, singleLine = true, modifier = Modifier.formField(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    )
                } else {
                    OutlinedTextField(
                        server, { server = it }, label = { Text("Servidor (http://host:porta)") }, singleLine = true, modifier = Modifier.formField(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    )
                    OutlinedTextField(
                        user, { user = it }, label = { Text("Usuário") }, singleLine = true, modifier = Modifier.formField(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    )
                    OutlinedTextField(
                        pass, { pass = it }, label = { Text("Senha") }, singleLine = true, modifier = Modifier.formField(),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                val fallback = if (type == SourceType.M3U) "Minha lista" else server.substringAfter("://").substringBefore('/')
                // Editing keeps the list's id (and with it the favourites and progress).
                onSave(
                    (initial ?: Source(id = "", name = "", type = type)).copy(
                        name = name.ifBlank { fallback },
                        url = url.trim(), epgUrl = epg.trim(),
                        server = server.trim(), username = user.trim(), password = pass,
                    )
                )
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
