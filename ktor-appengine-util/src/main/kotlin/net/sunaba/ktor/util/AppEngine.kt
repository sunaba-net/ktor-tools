package net.sunaba.ktor.util

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.appengine.v1.Appengine
import com.google.api.services.cloudresourcemanager.v3.CloudResourceManager
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import java.util.*
import kotlin.coroutines.CoroutineContext


object AppEngine : CoroutineScope {

    private val job = SupervisorJob()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + job

    /**
     *  インスタンスIDが取得できたらサービス環境と判断する
     */
    val isServiceEnv: Boolean by lazy { Env.GAE_INSTANCE.value.isNotEmpty() }

    val isLocalEnv: Boolean = !isServiceEnv

    //https://cloud.google.com/appengine/docs/standard/java11/runtime
    enum class Env(val key: String) {
        GAE_APPLICATION("GAE_APPLICATION"),
        GAE_DEPLOYMENT_ID("GAE_DEPLOYMENT_ID"),
        GAE_ENV("GAE_ENV"),
        GAE_INSTANCE("GAE_INSTANCE"),
        GAE_MEMORY_MB("GAE_MEMORY_MB"),
        GAE_RUNTIME("GAE_RUNTIME"),
        GAE_SERVICE("GAE_SERVICE"),
        GAE_VERSION("GAE_VERSION"),
        GOOGLE_CLOUD_PROJECT("GOOGLE_CLOUD_PROJECT"),
        NODE_ENV("NODE_ENV"),
        PORT("PORT");

        val value: String by lazy {
            System.getenv(key) ?: ""
        }
    }

    /**
     * @see https://cloud.google.com/compute/docs/storing-retrieving-metadata?hl=ja
     */
    enum class MetaPath(val path: String) {
        NUMERIC_PROJECT_ID("/computeMetadata/v1/project/numeric-project-id"),
        PROJECT_ID("/computeMetadata/v1/project/project-id"),
        ZONE("/computeMetadata/v1/instance/zone"),
        SERVICE_ACCOUNT_DEFAULT_ALIASES("/computeMetadata/v1/instance/service-accounts/default/aliases"),
        SERVICE_ACCOUNT_DEFAULT("/computeMetadata/v1/instance/service-accounts/default/"),
        SERVICE_ACCOUNT_DEFAULT_SCOPES("/computeMetadata/v1/instance/service-accounts/default/scopes");

        val value: String by lazy {
            metaData[this] ?: ""
        }
    }

    val metaData: Map<MetaPath, String> by lazy {
        runBlocking {
            HttpClient().use { client ->
                MetaPath.values().map {
                    it to async {
                        client.get("http://metadata.google.internal${it.path}") {
                            io.ktor.http.headers {
                                append("Metadata-Flavor", "Google")
                            }
                        }.bodyAsText()
                    }
                }.map { it.first to it.second.await() }.toMap()
            }
        }
    }

    val currentLocation: String? by lazy {
        getLocation(Env.GOOGLE_CLOUD_PROJECT.value)
    }

    //https://cloud.google.com/tasks/docs/tutorial-gcf
    // Note that two locations, called europe-west and us-central in App Engine commands, are called, respectively, europe-west1 and us-central1 in Cloud Tasks commands.
    val currentQueueLocation: String? = when (currentLocation) {
        "europe-west" -> "europe-west1"
        "us-central" -> "us-central1"
        else -> currentLocation
    }

    fun getLocation(projectId: String = Env.GOOGLE_CLOUD_PROJECT.value): String {
        val appengine = Appengine.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance(),
            HttpCredentialsAdapter(GoogleCredentials.getApplicationDefault())
        ).build()
        return appengine.apps().get(projectId).execute().locationId
    }

    fun isOwner(projectId: String = Env.GOOGLE_CLOUD_PROJECT.value, email: String?) =
        getOwners(projectId).contains("user:${email}")

    fun getOwners(projectId: String = Env.GOOGLE_CLOUD_PROJECT.value): List<String> {
        val resource = CloudResourceManager.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance(),
            HttpCredentialsAdapter(GoogleCredentials.getApplicationDefault())
        ).build()

        return resource.projects().getIamPolicy("projects/${projectId}", null).execute().bindings
            .filter { it.role == "roles/owner" }.flatMap { it.members }
    }

    /**
     * メールアドレスがキーで、ロールのSetがバリューのマップを返す
     * @param projectId プロジェクトID
     * @param userOnly プレフィックスが"user:"で始まるアカウントのみを対象とする
     * @return メールアドレスがキーで、ロールのSetがバリューのマップを返す
     */
    fun getRoles(
        projectId: String = Env.GOOGLE_CLOUD_PROJECT.value,
        userOnly: Boolean = true
    ): Map<String, Set<String>> {
        val resource = CloudResourceManager.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance(),
            HttpCredentialsAdapter(GoogleCredentials.getApplicationDefault())
        ).build()

        return resource.projects().getIamPolicy("projects/${projectId}", null).execute().bindings
            .flatMap { binding ->
                binding.members.stream().filter { !userOnly || it.startsWith("user:") }
                    .map { it!! to binding.role!! }.toList()
            }
            .groupBy({ it.first }, { it.second }).map { it.key to it.value.toSet() }.toMap()
    }
}