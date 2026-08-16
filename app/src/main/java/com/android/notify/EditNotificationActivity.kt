package com.android.notify

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.android.notify.ui.theme.NotifyAppTheme
import com.android.notify.util.ImageStorageHelper
import com.android.notify.util.NotificationHelper
import com.android.notify.viewmodel.NotifyViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 编辑通知 Activity
 *
 * 从通知栏的编辑按钮启动，允许用户修改通知内容。
 * 保存后通知栏实时更新。
 *
 * 优化（2026-08-16）：
 * - B4 改继承 AppCompatActivity，支持 per-app 语言切换
 * - U5 通知数据改经 ViewModel 加载（原直访 Repository 违反 MVVM 分层）
 *
 * 创建日期：2026-05-14
 * 作者：Cline
 */
class EditNotificationActivity : AppCompatActivity() {

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
    var imagePath by remember { mutableStateOf<String?>(null) }

    // 首次加载时经 ViewModel 读取通知数据（U5：避免直访 Repository 违反分层）
    LaunchedEffect(notificationId) {
        if (notificationId != -1) {
            viewModel.loadNotification(notificationId)?.let {
                title = it.title ?: ""
                content = it.content
                imagePath = it.imagePath
            }
        }
    }

    // 图片选择（新增 2026-08-16 | 图片通知）：系统照片选择器零权限，
    // 选图后复制到私有目录持久化（替换旧图时清理旧文件）
    val scope = rememberCoroutineScope()
    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val newPath = withContext(Dispatchers.IO) {
                    ImageStorageHelper.copyToPrivateStorage(context, uri)
                }
                if (newPath != null) {
                    ImageStorageHelper.deleteImage(imagePath)
                    imagePath = newPath
                }
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

            // 图片编辑区域（新增 2026-08-16 | 图片通知）
            if (imagePath != null) {
                // 当前图片预览
                val previewBitmap = remember(imagePath) {
                    ImageStorageHelper.decodeSampledBitmap(imagePath, 800)
                }
                previewBitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.action_add_image),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                // 更换 / 移除图片
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            imagePickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.action_change_image))
                    }
                    TextButton(
                        onClick = {
                            ImageStorageHelper.deleteImage(imagePath)
                            imagePath = null
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.action_remove_image))
                    }
                }
            } else {
                // 无图：添加入口
                OutlinedButton(
                    onClick = {
                        imagePickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text(stringResource(R.string.action_add_image))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 保存按钮（校验：正文与图片至少一项；纯图通知支持）
            Button(
                onClick = {
                    if (notificationId != -1 && (content.isNotBlank() || imagePath != null)) {
                        viewModel.updateNotificationContent(
                            id = notificationId,
                            title = title.ifBlank { null },
                            content = content,
                            imagePath = imagePath
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
                enabled = content.isNotBlank() || imagePath != null
            ) {
                Text(stringResource(R.string.confirm))
            }
        }
    }
}
