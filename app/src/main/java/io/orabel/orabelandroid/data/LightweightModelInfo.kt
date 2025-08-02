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

package io.orabel.orabelandroid.data

/**
 * Clase para almacenar información sobre modelos ligeros recomendados
 * para dispositivos con recursos limitados
 */
data class LightweightModelInfo(
    val name: String,
    val url: String,
    val description: String,
    val sizeInMB: Long,
    val licenseInfo: String = "",
    val termsOfUse: String = ""
)
