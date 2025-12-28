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

package io.orabel.orabelandroid

import android.content.Context
import io.orabel.orabelandroid.data.HealthDiaryRepository
import io.orabel.orabelandroid.data.UserMedicalProfileRepository
import io.orabel.orabelandroid.health.HealthReportGenerator
import io.orabel.orabelandroid.health.LocalHealthServer
import io.orabel.orabelandroid.translation.TranslationRepository
import io.orabel.orabelandroid.tts.TtsRepository
import io.orabel.orabelandroid.stt.OfflineSttRepository
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
@ComponentScan("io.orabel.orabelandroid")
class KoinAppModule

@Single
fun provideTranslationRepository(): TranslationRepository {
    return TranslationRepository()
}

@Single
fun provideTtsRepository(context: Context): TtsRepository {
    return TtsRepository(context)
}

@Single
fun provideSttRepository(context: Context): OfflineSttRepository {
    return OfflineSttRepository(context)
}

@Single
fun provideHealthDiaryRepository(context: Context): HealthDiaryRepository {
    return HealthDiaryRepository(context)
}

@Single
fun provideHealthReportGenerator(
    healthDiaryRepository: HealthDiaryRepository,
    userMedicalProfileRepository: UserMedicalProfileRepository
): HealthReportGenerator {
    return HealthReportGenerator(healthDiaryRepository, userMedicalProfileRepository)
}

@Single
fun provideLocalHealthServer(context: Context): LocalHealthServer {
    return LocalHealthServer(context)
}

@Single
fun provideUserMedicalProfileBox(): io.objectbox.Box<io.orabel.orabelandroid.data.UserMedicalProfile> {
    return io.orabel.orabelandroid.data.ObjectBoxStore.store.boxFor(io.orabel.orabelandroid.data.UserMedicalProfile::class.java)
}

@Single
fun provideBoxStore(): io.objectbox.BoxStore {
    return io.orabel.orabelandroid.data.ObjectBoxStore.store
}
