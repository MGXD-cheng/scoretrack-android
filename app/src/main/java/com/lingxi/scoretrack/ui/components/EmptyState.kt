package com.lingxi.scoretrack.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 统一的空状态。
 *
 * ## 为什么值得抽一个组件
 * 之前每个页面的空状态都是「两行居中的灰字」，一共十几处，每处写法都不一样：
 * 有的用 titleLarge、有的用 titleSmall、有的裹在 Card 里、有的没有边距。
 * 结果就是同一个应用里，「没有数据」这件事有五六种长相。
 *
 * ## 图标处理上的两个细节
 * 1. **图标放在一个圆形底衬里**，而不是裸放。裸图标在大片留白里显得孤零零的，
 *    加一层 `surfaceVariant` 的圆底之后，视觉上有了「一块东西」，页面不再空荡。
 * 2. **tint 只用 onSurfaceVariant，不再额外压 alpha**。很多设计会把空状态图标
 *    透明度压到 0.3 让它「退后」，但在阳光下或者低亮度屏幕上基本就看不见了 ——
 *    而这个组件的全部意义就是让人一眼看到「这里没东西是正常的」。
 *    onSurfaceVariant 本身已经是次要文字色，对比度刚好。
 *
 * @param icon 用的都是 material-icons-core 里有的图标。那个包只有 47 个图标，
 *             不是全集，写新页面时别顺手用了一个不存在的。
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(18.dp)
                    .size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (action != null) {
            Spacer(modifier = Modifier.height(20.dp))
            action()
        }
    }
}

/**
 * 紧凑版空状态，用在卡片内部（比如知识点页、图表区）。
 *
 * 不画圆形底衬 —— 卡片本身已经是一块背景了，再套一个圆会显得很啰嗦。
 */
@Composable
fun EmptyStateCompact(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}