/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

package org.ossreviewtoolkit.clients.osv

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory

import io.ks3.java.`typealias`.InstantAsString

import java.util.concurrent.Executors

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient

import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * A rest API client for the Google Open Source Vulnerabilities API (OSV.dev), see https://osv.dev/.
 */
interface OsvApiClient {
    companion object {
        const val BATCH_REQUEST_MAX_SIZE = 1000

        val JSON = Json { namingStrategy = JsonNamingStrategy.SnakeCase }

        /**
         * Create an OsvApiClient instance for communicating with the given [server], optionally using a pre-built
         * OkHttp [client].
         */
        fun create(server: Server, client: OkHttpClient? = null): OsvApiClient = create(server.url, client)

        fun create(serverUrl: String? = null, client: OkHttpClient? = null): OsvApiClient {
            val converterFactory = JSON.asConverterFactory(contentType = "application/json".toMediaType())

            return Retrofit.Builder()
                .apply { client(client ?: defaultHttpClient()) }
                .baseUrl(serverUrl ?: Server.PRODUCTION.url)
                .addConverterFactory(converterFactory)
                .build()
                .create(OsvApiClient::class.java)
        }
    }

    enum class Server(val url: String) {
        /**
         * The production API server.
         */
        PRODUCTION("https://api.osv.dev"),

        /**
         * The staging API server.
         */
        STAGING("https://api-staging.osv.dev")
    }

    /**
     * Get the vulnerabilities for the package matched by the given [request].
     */
    @POST("v1/query")
    fun getVulnerabilitiesForPackage(
        @Body request: VulnerabilitiesForPackageRequest
    ): Call<VulnerabilitiesForPackageResponse>

    /**
     * Get the identifiers of the vulnerabilities for the packages matched by the respective given [request].
     * The amount of requests contained in the give [batch request][request] must not exceed [BATCH_REQUEST_MAX_SIZE].
     */
    @POST("v1/querybatch")
    fun getVulnerabilityIdsForPackages(
        @Body request: VulnerabilitiesForPackageBatchRequest
    ): Call<VulnerabilitiesForPackageBatchResponse>

    /**
     * Return the vulnerability denoted by the given [id].
     */
    @GET("v1/vulns/{id}")
    fun getVulnerabilityForId(@Path("id") id: String): Call<Vulnerability>
}

@Serializable
class VulnerabilitiesForPackageRequest private constructor(
    val commit: String? = null,
    @SerialName("package")
    val pkg: Package? = null,
    val version: String? = null
) {
    constructor(commit: String, pkg: Package? = null) : this(commit = commit, pkg = pkg, version = null)
    constructor(pkg: Package, version: String) : this(commit = null, pkg = pkg, version = version)
}

@Serializable
data class VulnerabilitiesForPackageResponse(
    @SerialName("vulns")
    val vulnerabilities: List<Vulnerability>
)

@Serializable
data class VulnerabilitiesForPackageBatchRequest(
    val queries: List<VulnerabilitiesForPackageRequest>
)

@Serializable
data class VulnerabilitiesForPackageBatchResponse(
    val results: List<IdList>
) {
    @Serializable
    data class IdList(
        @SerialName("vulns")
        val vulnerabilities: List<Id> = emptyList()
    )

    @Serializable
    data class Id(
        val id: String,
        val modified: InstantAsString
    )
}

private fun defaultHttpClient(): OkHttpClient {
    // Experimentally determined value to speed-up execution time of 1000 single vulnerability-by-id requests.
    val n = 100
    val dispatcher = Dispatcher(Executors.newFixedThreadPool(n)).apply {
        maxRequests = n
        maxRequestsPerHost = n
    }

    return OkHttpClient.Builder().dispatcher(dispatcher).build()
}
