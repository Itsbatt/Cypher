package com.cypher.music.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.cypher.music.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cypher.music.data.User
import com.cypher.music.ui.theme.IosDarkBorder
import com.cypher.music.ui.theme.IosDarkCard
import com.cypher.music.ui.theme.IosDarkPill
import com.cypher.music.ui.theme.IosDarkTextSecondary
import com.cypher.music.ui.utils.AppHaptics

@Composable
fun AuthScreen(
    users: List<User>,
    onLogin: (String) -> Boolean,
    onRegister: (username: String, displayName: String, avatarColor: Long) -> User?,
    accentColor: Color = Color(0xFFFA243C),
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    var selectedTab by remember { mutableIntStateOf(if (users.isEmpty()) 1 else 0) } // 0 = Entrar, 1 = Registrarse

    var loginUsername by remember { mutableStateOf("") }
    var regUsername by remember { mutableStateOf("") }
    var regDisplayName by remember { mutableStateOf("") }
    var selectedAvatarColor by remember { mutableLongStateOf(0xFFFA243C) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val avatarPalette = listOf(
        0xFFFA243C, // Rojo
        0xFFFF6D00, // Naranja
        0xFFFFB300, // Oro
        0xFF10B981, // Verde
        0xFF00E5FF, // Cian
        0xFF2979FF, // Azul
        0xFF8B5CF6  // Púrpura
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .statusBarsPadding()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App Branding Header
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "Cypher Logo",
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(22.dp))
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Cypher",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )

                Text(
                    text = "Sesiones y perfiles locales 100% privados",
                    color = IosDarkTextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 28.dp)
                )
            }

            // Existing Profiles (1-tap Login)
            if (users.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp)
                    ) {
                        Text(
                            text = "PERFILES EN ESTE DISPOSITIVO",
                            color = IosDarkTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )

                        users.forEach { user ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .shadow(2.dp, RoundedCornerShape(16.dp))
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(IosDarkCard)
                                    .border(1.dp, IosDarkBorder, RoundedCornerShape(16.dp))
                                    .clickable {
                                        AppHaptics.actionClick()
                                        onLogin(user.username)
                                    }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(Color(user.avatarColor)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Person,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = user.displayName,
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "@${user.username}",
                                        color = IosDarkTextSecondary,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Icon(
                                    imageVector = Icons.Filled.ArrowForward,
                                    contentDescription = "Entrar",
                                    tint = accentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Tab Switcher Pill: "Entrar" / "Crear cuenta"
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(IosDarkCard)
                        .border(1.dp, IosDarkBorder, RoundedCornerShape(14.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selectedTab == 0) IosDarkPill else Color.Transparent)
                            .clickable {
                                AppHaptics.actionClick()
                                selectedTab = 0
                                errorMessage = null
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Entrar",
                            color = if (selectedTab == 0) Color.White else IosDarkTextSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selectedTab == 1) IosDarkPill else Color.Transparent)
                            .clickable {
                                AppHaptics.actionClick()
                                selectedTab = 1
                                errorMessage = null
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Crear cuenta",
                            color = if (selectedTab == 1) Color.White else IosDarkTextSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
            }

            // Form Area
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(IosDarkCard)
                        .border(1.dp, IosDarkBorder, RoundedCornerShape(18.dp))
                        .padding(20.dp)
                ) {
                    if (selectedTab == 0) {
                        // Login Mode
                        Text(
                            text = "Nombre de usuario",
                            color = IosDarkTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(IosDarkPill)
                                .border(1.dp, IosDarkBorder, RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            if (loginUsername.isEmpty()) {
                                Text(
                                    text = "Ej. usuario_1",
                                    color = IosDarkTextSecondary.copy(alpha = 0.5f),
                                    fontSize = 15.sp
                                )
                            }
                            BasicTextField(
                                value = loginUsername,
                                onValueChange = {
                                    loginUsername = it
                                    errorMessage = null
                                },
                                textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                                cursorBrush = SolidColor(accentColor),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                val clean = loginUsername.trim().lowercase()
                                if (clean.isBlank()) {
                                    errorMessage = "Escribe un nombre de usuario"
                                    return@Button
                                }
                                val ok = onLogin(clean)
                                if (!ok) {
                                    errorMessage = "El usuario «$clean» no existe en este dispositivo"
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text(text = "Iniciar sesión", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        // Register Mode
                        Text(
                            text = "Nombre de usuario (ID)",
                            color = IosDarkTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(IosDarkPill)
                                .border(1.dp, IosDarkBorder, RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            if (regUsername.isEmpty()) {
                                Text(
                                    text = "Ej. alex",
                                    color = IosDarkTextSecondary.copy(alpha = 0.5f),
                                    fontSize = 15.sp
                                )
                            }
                            BasicTextField(
                                value = regUsername,
                                onValueChange = {
                                    regUsername = it
                                    errorMessage = null
                                },
                                textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                                cursorBrush = SolidColor(accentColor),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Nombre para mostrar",
                            color = IosDarkTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(IosDarkPill)
                                .border(1.dp, IosDarkBorder, RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            if (regDisplayName.isEmpty()) {
                                Text(
                                    text = "Ej. Alex García",
                                    color = IosDarkTextSecondary.copy(alpha = 0.5f),
                                    fontSize = 15.sp
                                )
                            }
                            BasicTextField(
                                value = regDisplayName,
                                onValueChange = { regDisplayName = it },
                                textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                                cursorBrush = SolidColor(accentColor),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Color de perfil",
                            color = IosDarkTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            avatarPalette.forEach { colorVal ->
                                val isSelected = selectedAvatarColor == colorVal
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Color(colorVal))
                                        .border(
                                            width = if (isSelected) 2.5.dp else 0.dp,
                                            color = Color.White,
                                            shape = CircleShape
                                        )
                                        .clickable { selectedAvatarColor = colorVal },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                val u = regUsername.trim().lowercase()
                                if (u.isBlank()) {
                                    errorMessage = "Introduce un nombre de usuario"
                                    return@Button
                                }
                                val d = regDisplayName.trim().ifEmpty { u }
                                val res = onRegister(u, d, selectedAvatarColor)
                                if (res == null) {
                                    errorMessage = "Error al crear la cuenta"
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text(text = "Crear cuenta y entrar", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Error Message
                    AnimatedVisibility(visible = errorMessage != null) {
                        errorMessage?.let { msg ->
                            Text(
                                text = msg,
                                color = Color(0xFFFF453A),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 12.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
