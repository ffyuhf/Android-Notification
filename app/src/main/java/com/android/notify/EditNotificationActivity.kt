package com.android.notify

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.android.notify.ui.theme.NotifyAppTheme
import com.android.notify.util.NotificationHelper
import com.android.notify.viewmodel.NotifyViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 编辑通知 Activity
 *
 * 从通知栏的编辑按钮启动，允许用户修改通知内容。
 * 保存后通知栏实时更新。
 *
 * 创建日期：2026-05-14
 * 作者：Cline
 */
class EditNotificationActivity : ComponentActivity() {

    private lateinit var viewModel: NotifyViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(this)[NotifyViewModel::class.java]

        val notificationId = intent.getIntExtra(NotificationHelper.EXTRA_NOTIFICATION_ID, -1)

        setContent {
            val darkMode by viewModel.darkMode.collectAsState()
            NotifyAppTheme(darkMode = darkMode) {
                EditNotificationContent(
                    viewModel = viewModel,
                    notificationId = notificationId,
                    onBack = { finish() }
                )
            }
        }
    }
}

/**
 * 编辑通知内容 Composable
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditNotificationContent(
    viewModel: NotifyViewModel,
    notificationId: Int,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // 加载通知数据
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }

    // 首次加载时读取通知数据
    if (!loaded && notificationId != -1) {
        loaded = true
        CoroutineScope(Dispatchers.IO).launch {
            val entity = viewModel.let { vm ->
                val repo = com.android.notify.data.repository.NotificationRepository.getInstance(context)
                repo.getById(notificationId)
            }
            entity?.let {
                title = it.title ?: ""
                content = it.content
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action_edit)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cancel)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // 标题输入
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.hint_notification_title)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 内容输入
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text(stringResource(R.string.hint_notification_content)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                maxLines = 10
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 保存按钮
            Button(
                onClick = {
                    if (notificationId != -1 && content.isNotBlank()) {
                        viewModel.updateNotificationContent(
                            id = notificationId,
                            title = title.ifBlank { null },
                            content = content
                        )
                        Toast.makeText(
                            context,
                            R.string.toast_notification_updated,
                            Toast.LENGTH_SHORT
                        ).show()
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = content.isNotBlank()
            ) {
                Text(stringResource(R.string.confirm))
            }
        }
    }
}
