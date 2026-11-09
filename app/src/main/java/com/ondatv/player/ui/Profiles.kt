package com.ondatv.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.ondatv.player.R
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ondatv.player.AppViewModel
import com.ondatv.player.data.Profile

/** Avatar colours: top and bottom of a soft vertical gradient. */
private val palette = listOf(
    Color(0xFF5B8DEF) to Color(0xFF3A5BC7),
    Color(0xFFF2994A) to Color(0xFFD9632B),
    Color(0xFF27AE60) to Color(0xFF1B7F45),
    Color(0xFFEB5757) to Color(0xFFB83246),
    Color(0xFF9B51E0) to Color(0xFF6C2FB0),
    Color(0xFF2DC4C4) to Color(0xFF1A8C99),
    Color(0xFFF2C94C) to Color(0xFFD19A1F),
    Color(0xFF828A99) to Color(0xFF545B69),
)

@Composable
fun Avatar(profile: Profile, size: Dp, modifier: Modifier = Modifier) {
    val picture = avatarRes(profile.avatar)
    if (picture != null) {
        Image(
            painterResource(picture), contentDescription = profile.name,
            contentScale = ContentScale.Crop, modifier = modifier.size(size).clip(CircleShape),
        )
        return
    }
    val (top, bottom) = palette[profile.color.mod(palette.size)]
    Box(
        modifier.size(size).clip(CircleShape).background(Brush.verticalGradient(listOf(top, bottom))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            profile.name.trim().take(1).uppercase().ifEmpty { "?" },
            color = Color.White, fontSize = (size.value * 0.44f).sp, fontWeight = FontWeight.Bold,
        )
    }
}

/** The bundled avatar pictures (res/drawable-nodpi, WebP 256 px, transparent outside the circle). */
private val avatars = intArrayOf(
    R.drawable.avatar_01, R.drawable.avatar_02, R.drawable.avatar_03, R.drawable.avatar_04,
    R.drawable.avatar_05, R.drawable.avatar_06, R.drawable.avatar_07, R.drawable.avatar_08,
    R.drawable.avatar_09, R.drawable.avatar_10, R.drawable.avatar_11, R.drawable.avatar_12,
    R.drawable.avatar_13, R.drawable.avatar_14, R.drawable.avatar_15, R.drawable.avatar_16,
    R.drawable.avatar_17, R.drawable.avatar_18, R.drawable.avatar_19, R.drawable.avatar_20,
    R.drawable.avatar_21, R.drawable.avatar_22, R.drawable.avatar_23, R.drawable.avatar_24,
)

private fun avatarRes(n: Int): Int? = avatars.getOrNull(n - 1)

/** Grid of every avatar plus the coloured initial (0); the chosen one has a white ring. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AvatarPicker(preview: Profile, selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        (0..avatars.size).forEach { n ->
            val interaction = remember { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()
            val ring = when {
                n == selected -> Modifier.border(3.dp, Color.White, CircleShape)
                focused -> Modifier.border(2.dp, Color(0xB3FFFFFF), CircleShape)
                else -> Modifier
            }
            Avatar(
                preview.copy(avatar = n), 44.dp,
                Modifier.then(ring).clickable(interactionSource = interaction, indication = null) { onSelect(n) },
            )
        }
    }
}

/** Round avatar button used at the top-left of the Home screen. */
@Composable
fun AvatarButton(vm: AppViewModel, onClick: () -> Unit) {
    vm.profiles.collectAsStateWithLifecycle().value
    vm.profileId.collectAsStateWithLifecycle().value
    Box(
        Modifier.size(44.dp).clip(CircleShape).border(1.dp, Onda.glassBorder, CircleShape)
            .focusIntoContent().clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Avatar(vm.currentProfile(), 42.dp)
    }
}

/**
 * "Quem esta assistindo?": pick, add or edit profiles.
 * [locked] blocks leaving without choosing (app start with a PIN-protected profile).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfilesScreen(vm: AppViewModel, locked: Boolean, openSettings: () -> Unit, onDone: () -> Unit) {
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val currentId by vm.profileId.collectAsStateWithLifecycle()
    var editMode by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Profile?>(null) }
    var adding by remember { mutableStateOf(false) }
    // A PIN check, then what to do once it is right.
    var pinFor by remember { mutableStateOf<Pair<Profile, () -> Unit>?>(null) }

    BackHandler(enabled = locked) { }

    fun withPin(p: Profile, action: () -> Unit) {
        if (p.pin.isNullOrEmpty()) action() else pinFor = p to action
    }

    Column(
        Modifier.fillMaxSize().background(Onda.listBackground).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!locked) {
                GlassCircle(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Voltar", onDone)
            } else {
                Spacer(Modifier.size(44.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(
                if (editMode) "Concluído" else "Editar",
                color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(RoundedCornerShape(22.dp)).background(Onda.glass)
                    .clickable { editMode = !editMode }.padding(horizontal = 18.dp, vertical = 11.dp),
            )
        }
        Text(
            if (editMode) "Gerenciar perfis" else "Quem está assistindo?",
            color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 36.dp, bottom = 32.dp),
        )
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            profiles.forEach { p ->
                ProfileTile(p, selected = p.id == currentId && !editMode, editMode = editMode) {
                    if (editMode) {
                        withPin(p) { editing = p }
                    } else {
                        withPin(p) { vm.selectProfile(p.id); onDone() }
                    }
                }
            }
            if (profiles.size < 8) {
                Column(
                    Modifier.width(104.dp).clickable { adding = true },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(96.dp).clip(CircleShape).background(Onda.glass)
                            .border(1.dp, Onda.glassBorder, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(40.dp))
                    }
                    Text("Adicionar", color = Onda.textDim, fontSize = 15.sp, modifier = Modifier.padding(top = 10.dp))
                }
            }
        }
        // Not while the app waits for a profile PIN: settings hold the list and the parental PIN.
        if (!locked && !editMode) {
            val interaction = remember { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()
            val shape = RoundedCornerShape(26.dp)
            Row(
                Modifier.padding(top = 44.dp).width(220.dp).height(52.dp).clip(shape).background(Onda.glass)
                    .border(if (focused) 3.dp else 1.dp, if (focused) Color.White else Onda.glassBorder, shape)
                    .clickable(interactionSource = interaction, indication = null, onClick = openSettings),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Settings, null, tint = Color.White, modifier = Modifier.size(22.dp))
                Text(
                    "Definições", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }
        Spacer(Modifier.height(48.dp))
    }

    if (adding) {
        ProfileDialog(
            profile = null, canDelete = false, defaultColor = profiles.size,
            onSave = { vm.saveProfile(it); adding = false },
            onDelete = {},
            onDismiss = { adding = false },
        )
    }
    editing?.let { p ->
        ProfileDialog(
            profile = p, canDelete = profiles.size > 1, defaultColor = p.color,
            onSave = { vm.saveProfile(it); editing = null },
            onDelete = { vm.deleteProfile(p.id); editing = null },
            onDismiss = { editing = null },
        )
    }
    pinFor?.let { (p, action) ->
        PinDialog(p, onOk = { pinFor = null; action() }, onDismiss = { pinFor = null })
    }
}

@Composable
private fun ProfileTile(p: Profile, selected: Boolean, editMode: Boolean, onClick: () -> Unit) {
    Column(Modifier.width(104.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Avatar(
                p, 96.dp,
                Modifier.then(if (selected) Modifier.border(3.dp, Color.White, CircleShape) else Modifier),
            )
            if (editMode) {
                Box(
                    Modifier.size(96.dp).clip(CircleShape).background(Color(0x80000000)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Edit, "Editar", tint = Color.White, modifier = Modifier.size(32.dp)) }
            }
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(p.name, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!p.pin.isNullOrEmpty()) {
                Icon(Icons.Filled.Lock, "Com PIN", tint = Onda.textFaint, modifier = Modifier.padding(start = 4.dp).size(14.dp))
            }
        }
    }
}

@Composable
private fun ProfileDialog(
    profile: Profile?,
    canDelete: Boolean,
    defaultColor: Int,
    onSave: (Profile) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(profile?.name.orEmpty()) }
    var color by remember { mutableStateOf(profile?.color ?: defaultColor) }
    var avatar by remember { mutableStateOf(profile?.avatar ?: 0) }
    var pin by remember { mutableStateOf(profile?.pin.orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val pinOk = pin.isEmpty() || (pin.length == 4 && pin.all { it.isDigit() })
    val preview = Profile(profile?.id ?: "", name.ifBlank { "?" }, color, null, avatar)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (profile == null) "Novo perfil" else "Editar perfil") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Avatar(preview, 72.dp, Modifier.align(Alignment.CenterHorizontally))
                OutlinedTextField(name, { name = it.take(20) }, label = { Text("Nome") }, singleLine = true)
                Text("Avatar", color = Onda.textFaint, fontSize = 13.sp)
                AvatarPicker(preview, avatar) { avatar = it }
                if (avatar == 0) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    palette.indices.forEach { i ->
                        Avatar(
                            preview.copy(color = i), 30.dp,
                            Modifier.clickable { color = i }
                                .then(if (i == color) Modifier.border(2.dp, Color.White, CircleShape) else Modifier),
                        )
                    }
                }
                OutlinedTextField(
                    pin, { v -> pin = v.filter { it.isDigit() }.take(4) },
                    label = { Text("PIN de 4 dígitos (opcional)") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = !pinOk,
                    supportingText = { Text(if (pinOk) "Deixe vazio para não pedir PIN" else "Use 4 números") },
                )
                if (profile != null && canDelete) {
                    TextButton(onClick = { confirmDelete = true }) { Text("Apagar perfil", color = Color(0xFFFF8A80)) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && pinOk, onClick = {
                onSave(Profile(profile?.id ?: "", name.trim(), color, pin.ifEmpty { null }, avatar))
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )

    if (confirmDelete && profile != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Apagar \"${profile.name}\"?") },
            text = { Text("Os favoritos, o histórico e o Continuar assistindo deste perfil serão apagados.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Apagar") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun PinDialog(p: Profile, onOk: () -> Unit, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Avatar(p, 56.dp) },
        title = { Text("PIN de ${p.name}") },
        text = {
            OutlinedTextField(
                pin,
                { v ->
                    pin = v.filter { it.isDigit() }.take(4)
                    wrong = false
                    if (pin.length == 4) {
                        if (pin == p.pin) onOk() else { wrong = true; pin = "" }
                    }
                },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                isError = wrong,
                supportingText = { if (wrong) Text("PIN incorreto") },
            )
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
