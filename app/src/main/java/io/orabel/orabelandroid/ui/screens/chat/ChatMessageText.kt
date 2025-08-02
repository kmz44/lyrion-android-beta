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

package io.orabel.orabelandroid.ui.screens.chat

import android.graphics.Color
import android.text.Spanned
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun ChatMessageText(
    message: Spanned,
    modifier: Modifier = Modifier,
    textSize: Float,
    textColor: Int,
) {
    AndroidView(
        modifier = modifier,
        factory = {
            val textView = TextView(it)
            textView.textSize = textSize
            textView.setTextColor(textColor)
            textView.setTextIsSelectable(true)
            textView.highlightColor = Color.YELLOW
            textView
        },
        update = { it.text = message },
    )
}
