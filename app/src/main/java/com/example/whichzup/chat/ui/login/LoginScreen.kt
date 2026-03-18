package com.example.whichzup.chat.ui.login

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.whichzup.chat.domain.model.AuthState

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onNavigateToChatList: () -> Unit
) {
    val email by viewModel.email.collectAsStateWithLifecycle()
    val password by viewModel.password.collectAsStateWithLifecycle()
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val passwordResetState by viewModel.passwordResetState.collectAsStateWithLifecycle()

    // Handle navigation side-effect when authentication is successful
    LaunchedEffect(authState) {
        if (authState is AuthState.Authenticated) {
            onNavigateToChatList()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Welcome to WhichZup",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        OutlinedTextField(
            value = email,
            onValueChange = viewModel::onEmailChange,
            label = { Text("Email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = viewModel::onPasswordChange,
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (authState is AuthState.Loading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = { viewModel.login() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Login")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = { viewModel.register() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Register")
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = { viewModel.resetPassword() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Forgot Password?")
            }
        }

        // Auth Error State
        if (authState is AuthState.Error) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = (authState as AuthState.Error).message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // Password Reset States
        when (passwordResetState) {
            is PasswordResetState.Loading -> {
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
            is PasswordResetState.Success -> {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = (passwordResetState as PasswordResetState.Success).message,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            is PasswordResetState.Error -> {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = (passwordResetState as PasswordResetState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            PasswordResetState.Idle -> { /* Do nothing */ }
        }
    }
}