package io.github.ffyuhf.notify

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import io.github.ffyuhf.notify.ui.component.AdaptiveImage
import io.github.ffyuhf.notify.ui.theme.NotifyAppTheme
import io.github.ffyuhf.notify.util.ImageStorageHelper
import io.github.ffyuhf.notify.util.NotificationHelper
import io.github.ffyuhf.notify.viewmodel.NotifyViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 编辑通知 Activity
 *
 * 从历史记录进入，允许用户修改通知标题、正文与图片。
 * 保存后通知栏实时更新。通知数据经 ViewModel 加载（MVVM 分层）。
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

    // 首次加载时经 ViewModel 读取通知数据（避免直访 Repository 违反分层）
    LaunchedEffect(notificationId) {
        if (notificationId != -1) {
            viewModel.loadNotification(notificationId)?.let {
                title = it.title ?: ""
                content = it.content
                imagePath = it.imagePath
            }
        }
    }

    // 图片选择：系统照片选择器（Photo Picker）零权限，选图后复制到
    // 私有目录持久化（替换旧图时清理旧文件）
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
                },
                // 顶栏配色与三页统一
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                // 可滚动：有图时自适应图片（最高320dp）会压缩弹性输入框至高度0
                // 无法输入；滚动后图片/输入框/按钮在超屏时均可到达
                .verticalScroll(rememberScrollState())
        ) {
            // 标题输入
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.hint_notification_title)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 内容输入：滚动容器内 weight 无意义且会被上方自适应图片压缩至
            // 高度0，用 heightIn 保证最小输入区
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text(stringResource(R.string.hint_notification_content)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                maxLines = 10,
                shape = MaterialTheme.shapes.medium
            )

            if (imagePath != null) {
                // 图片与内容输入框分隔
                Spacer(modifier = Modifier.height(12.dp))
                // 图片预览：按图片宽高比自适应高度完整显示（限高 320dp 防长图占屏），
                // 按显示区物理像素 1:1 解码（视觉原图）
                AdaptiveImage(
                    path = imagePath,
                    maxHeight = 320.dp,
                    cornerRadius = 12.dp,
                    contentDescription = stringResource(R.string.action_add_image)
                )
                // 图片与按钮行分隔
                Spacer(modifier = Modifier.height(8.dp))
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
