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

package io.orabel.orabelandroid.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.orabel.orabelandroid.R

// Set of Material typography styles to start with
val Typography =
    Typography(
        bodyLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.5.sp,
            ),
        /* Other default text styles to override
        titleLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            letterSpacing = 0.sp
        ),
        labelSmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp
        )
         */
    )

val AppFontFamily =
    FontFamily(
        Font(
            resId = R.font.inter_regular,
            weight = FontWeight.Normal,
            style = FontStyle.Normal,
        ),
        Font(
            resId = R.font.inter_bold,
            weight = FontWeight.Bold,
            style = FontStyle.Normal,
        ),
        Font(
            resId = R.font.inter_italic,
            weight = FontWeight.Normal,
            style = FontStyle.Italic,
        ),
        Font(
            resId = R.font.inter_black,
            weight = FontWeight.Black,
            style = FontStyle.Normal,
        ),
        Font(
            resId = R.font.inter_thin,
            weight = FontWeight.Thin,
            style = FontStyle.Normal,
        ),
        Font(
            resId = R.font.inter_black,
            weight = FontWeight.Light,
            style = FontStyle.Normal,
        ),
    )
