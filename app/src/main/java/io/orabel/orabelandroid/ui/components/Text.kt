/*
 *
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 */

package io.orabel.orabelandroid.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import io.orabel.orabelandroid.ui.theme.AppFontFamily

@Composable
fun SmallLabelText(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = Color.Black,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = AppFontFamily,
        modifier = modifier,
        color = textColor,
    )
}

@Composable
fun MediumLabelText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontFamily = AppFontFamily,
        modifier = modifier,
    )
}

@Composable
fun LargeLabelText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontFamily = AppFontFamily,
        modifier = modifier,
    )
}

@Composable
fun DialogTitleText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontFamily = AppFontFamily,
        modifier = modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
}

@Composable
fun AppBarTitleText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontFamily = AppFontFamily,
        modifier = modifier,
        fontWeight = FontWeight.Bold,
    )
}
