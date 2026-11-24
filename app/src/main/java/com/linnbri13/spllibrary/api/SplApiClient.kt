package com.linnbri13.spllibrary.api

import com.linnbri13.spllibrary.data.BookResult
import com.linnbri13.spllibrary.data.Branch
import com.linnbri13.spllibrary.data.CheckoutItem
import com.linnbri13.spllibrary.data.CopyAvailability
import com.linnbri13.spllibrary.data.FormatFilter
import com.linnbri13.spllibrary.data.HoldItem
import com.linnbri13.spllibrary.data.HoldsResult
import com.linnbri13.spllibrary.data.NewlyAcquiredFilter
import com.linnbri13.spllibrary.data.PlaceHoldOutcome
import com.linnbri13.spllibrary.data.SearchPage
import com.linnbri13.spllibrary.data.SessionStore
import com.linnbri13.spllibrary.data.SortOption
import com.linnbri13.spllibrary.data.formatCodeToLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class SplApiException(message: String, val httpCode: Int? = null) : Exception(message)

private class MemoryCookieJar : CookieJar {
    private val store = ConcurrentHashMap<String, MutableMap<String, Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val hostMap = store.getOrPut(url.host) { ConcurrentHashMap() }
        for (cookie in cookies) {
            hostMap[cookie.name] = cookie
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val result = mutableListOf<Cookie>()
        for ((_, cookiesByName) in store) {
            for (cookie in cookiesByName.values) {
                if (cookie.expiresAt >= now && cookie.matches(url)) {
                    result.add(cookie)
                }
            }
        }
        return result
    }

    fun findCookie(name: String): String? {
        for ((_, cookiesByName) in store) {
            val c = cookiesByName[name]
            if (c != null && c.value.isNotBlank()) return c.value
        }
        return null
    }

    fun clear() {
        store.clear()
    }
}

class SplApiClient(private val sessionStore: SessionStore) {
    private val library = "seattle"
    private val catalogOrigin = "https://$library.bibliocommons.com"
    private val gatewayBase = "https://gateway.bibliocommons.com/v2/libraries/$library"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val cookieJar = MemoryCookieJar()
    private val authMutex = Mutex()

    private val http = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private val userAgent =
        "Mozilla/5.0 (Linux; Android 15; Pixel 9 Pro Fold) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    fun clearCookies() {
        cookieJar.clear()
    }

    suspend fun login(username: String, pin: String): Long = withContext(Dispatchers.IO) {
        authMutex.withLock {
            loginLocked(username, pin)
        }
    }

    private suspend fun loginLocked(username: String, pin: String): Long {
        cookieJar.clear()
        val loginUrl = "$catalogOrigin/user/login?destination=x"
        val getReq = Request.Builder()
            .url(loginUrl)
            .header("User-Agent", userAgent)
            .get()
            .build()

        val html = http.newCall(getReq).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw SplApiException("Could not load SPL login page (HTTP ${resp.code})", resp.code)
            }
            resp.body?.string() ?: ""
        }

        val csrfToken = extractCsrfToken(html)
            ?: throw SplApiException("Could not find login token on SPL page. Please try again.")

        val formBody = FormBody.Builder()
            .add("utf8", "✓")
            .add("authenticity_token", csrfToken)
            .add("name", username.trim())
            .add("user_pin", pin.trim())
            .add("commit", "Log In")
            .build()

        val postReq = Request.Builder()
            .url("$catalogOrigin/user/login")
            .header("User-Agent", userAgent)
            .header("Origin", catalogOrigin)
            .header("Referer", loginUrl)
            .post(formBody)
            .build()

        http.newCall(postReq).execute().use { resp ->
            // Consume body so cookies are processed
            resp.body?.string()
        }

        val accessToken = cookieJar.findCookie("bc_access_token")
        val sessionId = cookieJar.findCookie("session_id")
        if (accessToken.isNullOrBlank() || sessionId.isNullOrBlank()) {
            throw SplApiException("Sign in failed. Check your SPL card number / username and PIN.")
        }

        val accountId = sessionId.substringAfterLast("-").toLongOrNull()?.plus(1L)
            ?: throw SplApiException("Unexpected session format from SPL.")

        sessionStore.username = username.trim()
        sessionStore.pin = pin.trim()
        sessionStore.saveSession(accessToken, sessionId, accountId)
        return accountId
    }

    private suspend fun refreshSessionIfNeeded(failedToken: String?) {
        authMutex.withLock {
            if (!sessionStore.accessToken.isNullOrBlank() && sessionStore.accessToken != failedToken) {
                return@withLock
            }
            if (!sessionStore.hasCredentials) {
                throw SplApiException("SPL session expired. Please sign in again.")
            }
            loginLocked(sessionStore.username, sessionStore.pin)
        }
    }

    suspend fun getBranches(): List<Branch> = withContext(Dispatchers.IO) {
        val root = gatewayGet("$gatewayBase/branches".toHttpUrl(), requireAuth = false)
        val branchesObj = root["entities"]?.obj?.get("branches")?.obj ?: return@withContext emptyList()
        branchesObj.entries.mapNotNull { (key, value) ->
            val obj = value.obj ?: return@mapNotNull null
            val code = obj.str("code") ?: key
            val name = obj.str("name") ?: return@mapNotNull null
            Branch(code = code, name = name)
        }.sortedWith(
            compareBy<Branch> { it.name.contains("Locker", ignoreCase = true) }
                .thenBy { it.name.lowercase() }
        )
    }

    suspend fun search(
        query: String,
        formatFilter: FormatFilter,
        newlyAcquired: NewlyAcquiredFilter,
        sortOption: SortOption,
        page: Int = 1
    ): SearchPage = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        val effectiveQuery = if (trimmed.isEmpty()) "a" else trimmed
        val urlBuilder = "$gatewayBase/bibs/search".toHttpUrl().newBuilder()
            .addQueryParameter("query", effectiveQuery)
            .addQueryParameter("searchType", "keyword")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sort", sortOption.apiValue)

        when (formatFilter) {
            FormatFilter.PHYSICAL_BOOKS -> urlBuilder.addQueryParameter("f_FORMAT", "BK")
            FormatFilter.ALL_PHYSICAL -> urlBuilder.addQueryParameter("f_CIRC", "CIRC")
            FormatFilter.ALL_FORMATS -> Unit
        }

        newlyAcquired.apiValue?.let {
            urlBuilder.addQueryParameter("f_NEWLY_ACQUIRED", it)
        }

        val root = gatewayGet(urlBuilder.build(), requireAuth = false)
        val catalogSearch = root["catalogSearch"]?.obj
        val pagination = catalogSearch?.get("pagination")?.obj
        val totalCount = pagination?.int("count") ?: 0
        val totalPages = pagination?.int("pages") ?: 1
        val currentPage = pagination?.int("page") ?: page

        val bibsMap = root["entities"]?.obj?.get("bibs")?.obj ?: JsonObject(emptyMap())
        val orderedIds = catalogSearch?.get("results")?.arr
            ?.mapNotNull { it.obj?.str("representative") }
            ?.ifEmpty { bibsMap.keys.toList() }
            ?: bibsMap.keys.toList()

        val items = orderedIds.mapNotNull { bibId ->
            val bibObj = bibsMap[bibId]?.obj ?: return@mapNotNull null
            parseBook(bibId, bibObj)
        }

        SearchPage(
            page = currentPage,
            totalPages = totalPages,
            totalCount = totalCount,
            items = items
        )
    }

    suspend fun getAvailability(bibId: String): List<CopyAvailability> = withContext(Dispatchers.IO) {
        val url = "$gatewayBase/bibs/$bibId/availability".toHttpUrl()
        val root = gatewayGet(url, requireAuth = false)
        val itemsArr = root["entities"]?.obj?.get("bibItems")?.obj?.values
            ?: return@withContext emptyList()

        itemsArr.mapNotNull { el ->
            val obj = el.obj ?: return@mapNotNull null
            val branchName = obj["branch"]?.obj?.str("name")
                ?: obj.str("branchName")
                ?: return@mapNotNull null
            val status = obj["availability"]?.obj?.str("status")
                ?: obj.str("status")
                ?: "UNKNOWN"
            CopyAvailability(
                branchName = branchName,
                callNumber = obj.str("callNumber"),
                status = status,
                collection = obj.str("collection")
            )
        }.sortedWith(
            compareByDescending<CopyAvailability> { it.status.equals("AVAILABLE", ignoreCase = true) }
                .thenBy { it.branchName.lowercase() }
        )
    }

    suspend fun listHolds(): HoldsResult = withContext(Dispatchers.IO) {
        ensureAuthenticated()
        val url = "$gatewayBase/holds".toHttpUrl().newBuilder()
            .addQueryParameter("accountId", sessionStore.accountId.toString())
            .addQueryParameter("size", "100")
            .build()

        val root = gatewayGet(url, requireAuth = true)
        val entities = root["entities"]?.obj
        val holdsObj = entities?.get("holds")?.obj ?: JsonObject(emptyMap())
        val bibsObj = entities?.get("bibs")?.obj ?: JsonObject(emptyMap())

        val holds = holdsObj.entries.mapNotNull { (holdId, value) ->
            val h = value.obj ?: return@mapNotNull null
            val metadataId = h.str("metadataId") ?: ""
            val briefInfo = bibsObj[metadataId]?.obj?.get("briefInfo")?.obj
            val pickup = h["pickupLocation"]?.obj
            val format = briefInfo?.str("format")
            val author = briefInfo?.get("authors")?.arr?.firstOrNull()?.strVal
            HoldItem(
                holdId = holdId,
                metadataId = metadataId,
                title = h.str("bibTitle") ?: briefInfo?.str("title") ?: "Untitled",
                author = author,
                format = format,
                formatLabel = formatCodeToLabel(format),
                materialType = h.str("materialType"),
                status = h.str("status") ?: "UNKNOWN",
                position = h.int("holdsPosition"),
                pickupBranchName = pickup?.str("name"),
                pickupBranchCode = pickup?.str("code"),
                placedDate = h.str("holdPlacedDate"),
                expiryDate = h.str("expiryDate"),
                jacketUrl = extractJacketUrl(briefInfo),
                catalogUrl = "$catalogOrigin/v2/record/$metadataId"
            )
        }.sortedWith(
            compareBy<HoldItem>(
                {
                    when {
                        it.isReady -> 0
                        it.isInTransit -> 1
                        it.position != null -> 2
                        else -> 3
                    }
                },
                { it.position ?: Int.MAX_VALUE },
                { it.title.lowercase() }
            )
        )

        val physQuota = root["borrowing"]?.obj
            ?.get("summaries")?.obj
            ?.get("holds")?.obj
            ?.get("quotas")?.obj
            ?.get("PHYSICAL")?.obj

        HoldsResult(
            holds = holds,
            physicalUsed = physQuota?.int("used") ?: holds.count { it.isPhysical },
            physicalLimit = physQuota?.int("limit")
        )
    }

    suspend fun placePhysicalHold(bibId: String, branchCode: String): PlaceHoldOutcome =
        withContext(Dispatchers.IO) {
            ensureAuthenticated()
            val url = "$gatewayBase/holds".toHttpUrl().newBuilder()
                .addQueryParameter("locale", "en-US")
                .build()

            val payload = buildJsonObject {
                put("metadataId", bibId)
                put("materialType", "PHYSICAL")
                put("accountId", sessionStore.accountId)
                put("enableSingleClickHolds", false)
                put("materialParams", buildJsonObject {
                    put("branchId", branchCode)
                    put("expiryDate", JsonNull)
                    put("errorMessageLocale", "en-US")
                })
            }

            val root = gatewayMutation("POST", url, payload.toString())
            val failures = root["failures"]?.obj
            if (failures != null && failures.isNotEmpty()) {
                val msg = failures.values.firstOrNull()?.let { el ->
                    (el as? JsonPrimitive)?.contentOrNull ?: el.obj?.str("message")
                } ?: "Could not place hold"
                throw SplApiException(msg)
            }
            val holdsObj = root["entities"]?.obj?.get("holds")?.obj
            val firstEntry = holdsObj?.entries?.firstOrNull()
            val holdObj = firstEntry?.value?.obj
            PlaceHoldOutcome(
                holdId = firstEntry?.key,
                position = holdObj?.int("holdsPosition"),
                status = holdObj?.str("status"),
                pickupBranchName = holdObj?.get("pickupLocation")?.obj?.str("name")
            )
        }

    suspend fun cancelHold(holdId: String, metadataId: String) = withContext(Dispatchers.IO) {
        ensureAuthenticated()
        val url = "$gatewayBase/holds".toHttpUrl().newBuilder()
            .addQueryParameter("locale", "en-US")
            .build()

        val payload = buildJsonObject {
            put("accountId", sessionStore.accountId)
            put("metadataIds", buildJsonArray { add(JsonPrimitive(metadataId)) })
            put("holdIds", buildJsonArray { add(JsonPrimitive(holdId)) })
            put("errorMessageLocale", "en-US")
        }

        val root = gatewayMutation("DELETE", url, payload.toString())
        val failures = root["failures"]?.obj
        if (failures != null && failures.isNotEmpty()) {
            val msg = failures.values.firstOrNull()?.strVal ?: "Could not cancel hold"
            throw SplApiException(msg)
        }
    }

    suspend fun listCheckouts(): List<CheckoutItem> = withContext(Dispatchers.IO) {
        ensureAuthenticated()
        val url = "$gatewayBase/checkouts".toHttpUrl().newBuilder()
            .addQueryParameter("accountId", sessionStore.accountId.toString())
            .addQueryParameter("size", "100")
            .build()

        val root = gatewayGet(url, requireAuth = true)
        val entities = root["entities"]?.obj
        val checkoutsObj = entities?.get("checkouts")?.obj ?: JsonObject(emptyMap())
        val bibsObj = entities?.get("bibs")?.obj ?: JsonObject(emptyMap())

        checkoutsObj.entries.mapNotNull { (checkoutId, value) ->
            val c = value.obj ?: return@mapNotNull null
            val metadataId = c.str("metadataId")
            val briefInfo = metadataId?.let { bibsObj[it]?.obj?.get("briefInfo")?.obj }
            val format = briefInfo?.str("format")
            val actions = c["actions"]?.arr?.mapNotNull { it.strVal } ?: emptyList()
            CheckoutItem(
                checkoutId = c.str("checkoutId") ?: checkoutId,
                metadataId = metadataId,
                title = c.str("bibTitle") ?: briefInfo?.str("title") ?: "Untitled",
                author = briefInfo?.get("authors")?.arr?.firstOrNull()?.strVal,
                format = format,
                formatLabel = formatCodeToLabel(format),
                materialType = c.str("materialType"),
                dueDate = c.str("dueDate"),
                status = c.str("status"),
                timesRenewed = c.int("timesRenewed") ?: 0,
                canRenew = actions.any { it.equals("renew", ignoreCase = true) },
                jacketUrl = extractJacketUrl(briefInfo),
                catalogUrl = metadataId?.let { "$catalogOrigin/v2/record/$it" }
            )
        }.sortedWith(
            compareBy<CheckoutItem>({ it.dueDate == null }, { it.dueDate ?: "9999-99-99" }, { it.title.lowercase() })
        )
    }

    suspend fun renewCheckout(checkoutId: String): String? = withContext(Dispatchers.IO) {
        ensureAuthenticated()
        val url = "$gatewayBase/checkouts".toHttpUrl().newBuilder()
            .addQueryParameter("locale", "en-US")
            .build()

        val payload = buildJsonObject {
            put("accountId", sessionStore.accountId)
            put("checkoutIds", buildJsonArray { add(JsonPrimitive(checkoutId)) })
            put("renew", true)
        }

        val root = gatewayMutation("PATCH", url, payload.toString())
        val failures = root["failures"]?.obj
        if (failures != null && failures.isNotEmpty()) {
            val msg = failures.values.firstOrNull()?.strVal ?: "Could not renew checkout"
            throw SplApiException(msg)
        }
        root["entities"]?.obj?.get("checkouts")?.obj?.get(checkoutId)?.obj?.str("dueDate")
    }

    private suspend fun ensureAuthenticated() {
        if (sessionStore.isLoggedIn) return
        authMutex.withLock {
            if (sessionStore.isLoggedIn) return@withLock
            if (sessionStore.hasCredentials) {
                loginLocked(sessionStore.username, sessionStore.pin)
            } else {
                throw SplApiException("Please sign in on the Account tab first.")
            }
        }
    }

    private suspend fun gatewayGet(url: HttpUrl, requireAuth: Boolean, retryOnAuth: Boolean = true): JsonObject {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "application/json")
            .get()

        val usedToken = sessionStore.accessToken
        if (requireAuth) {
            attachAuthHeaders(builder)
        }

        val (code, body) = executeCall(builder.build())
        if (requireAuth && retryOnAuth && (code == 401 || code == 403) && sessionStore.hasCredentials) {
            refreshSessionIfNeeded(usedToken)
            return gatewayGet(url, requireAuth = true, retryOnAuth = false)
        }

        if (code !in 200..299) {
            throw SplApiException(extractErrorMessage(body, code), code)
        }

        return parseJsonObject(body)
    }

    private suspend fun gatewayMutation(
        method: String,
        url: HttpUrl,
        jsonBody: String,
        retryOnAuth: Boolean = true
    ): JsonObject {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "application/json")
            .header("Origin", catalogOrigin)
            .header("Referer", "$catalogOrigin/")
            .method(method, jsonBody.toRequestBody(jsonMediaType))

        val usedToken = sessionStore.accessToken
        attachAuthHeaders(builder)

        val (code, body) = executeCall(builder.build())
        if (retryOnAuth && (code == 401 || code == 403) && sessionStore.hasCredentials) {
            refreshSessionIfNeeded(usedToken)
            return gatewayMutation(method, url, jsonBody, retryOnAuth = false)
        }

        if (code !in 200..299) {
            throw SplApiException(extractErrorMessage(body, code), code)
        }

        return parseJsonObject(body)
    }

    private fun attachAuthHeaders(builder: Request.Builder) {
        sessionStore.accessToken?.let { builder.header("X-Access-Token", it) }
        sessionStore.sessionId?.let { builder.header("X-Session-Id", it) }
    }

    private fun executeCall(request: Request): Pair<Int, String> {
        try {
            http.newCall(request).execute().use { resp ->
                return resp.code to (resp.body?.string() ?: "")
            }
        } catch (e: IOException) {
            throw SplApiException("Network error: ${e.message ?: "check your connection"}")
        }
    }

    private fun parseJsonObject(body: String): JsonObject {
        if (body.isBlank()) return JsonObject(emptyMap())
        return try {
            json.parseToJsonElement(body).jsonObject
        } catch (e: Exception) {
            throw SplApiException("Unexpected response from SPL gateway.")
        }
    }

    private fun extractErrorMessage(body: String, httpCode: Int): String {
        try {
            val obj = json.parseToJsonElement(body).jsonObject
            obj["error"]?.obj?.str("message")?.let { return it }
            obj.str("message")?.let { return it }
            obj["errors"]?.arr?.firstOrNull()?.obj?.str("message")?.let { return it }
        } catch (_: Exception) {
            // Fall through to HTTP status message
        }
        return when (httpCode) {
            401, 403 -> "SPL session expired. Please sign in again."
            404 -> "Item not found in SPL catalog."
            else -> "SPL request failed (HTTP $httpCode)"
        }
    }

    private fun extractCsrfToken(html: String): String? {
        val p1 = Regex("""<input[^>]+name=["']authenticity_token["'][^>]+value=["']([^"']+)["']""")
        val p2 = Regex("""<input[^>]+value=["']([^"']+)["'][^>]+name=["']authenticity_token["']""")
        val p3 = Regex("""<meta[^>]+name=["']csrf-token["'][^>]+content=["']([^"']+)["']""")
        return p1.find(html)?.groupValues?.getOrNull(1)
            ?: p2.find(html)?.groupValues?.getOrNull(1)
            ?: p3.find(html)?.groupValues?.getOrNull(1)
    }

    private fun parseBook(bibId: String, bibObj: JsonObject): BookResult {
        val brief = bibObj["briefInfo"]?.obj
        val avail = bibObj["availability"]?.obj
        val format = brief?.str("format") ?: ""
        val bibType = avail?.str("bibType")
        val circType = avail?.str("circulationType")
        val libraryUseOnly = avail?.bool("libraryUseOnly") ?: false
        val authors = brief?.get("authors")?.arr?.mapNotNull { it.strVal } ?: emptyList()

        return BookResult(
            metadataId = bibId,
            title = brief?.str("title") ?: "Untitled",
            subtitle = brief?.str("subtitle"),
            authors = authors,
            format = format,
            formatLabel = formatCodeToLabel(format),
            publicationDate = brief?.str("publicationDate"),
            callNumber = brief?.str("callNumber"),
            description = brief?.str("description"),
            jacketUrl = extractJacketUrl(brief),
            bibType = bibType,
            status = avail?.str("status"),
            availableCopies = avail?.int("availableCopies") ?: 0,
            totalCopies = avail?.int("totalCopies") ?: 0,
            heldCopies = avail?.int("heldCopies") ?: 0,
            onOrderCopies = avail?.int("onOrderCopies") ?: 0,
            holdable = bibType.equals("PHYSICAL", ignoreCase = true) &&
                !libraryUseOnly &&
                !circType.equals("NON_CIRC", ignoreCase = true),
            catalogUrl = "$catalogOrigin/v2/record/$bibId"
        )
    }

    private fun extractJacketUrl(briefInfo: JsonObject?): String? {
        val jacket = briefInfo?.get("jacket")?.obj ?: return null
        return jacket.str("local_url")
            ?: jacket.str("medium")
            ?: jacket.str("large")
            ?: jacket.str("small")
    }
}

private val JsonElement.obj: JsonObject?
    get() = this as? JsonObject

private val JsonElement.arr: JsonArray?
    get() = this as? JsonArray

private val JsonElement.strVal: String?
    get() = (this as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

private fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

private fun JsonObject.int(key: String): Int? =
    (this[key] as? JsonPrimitive)?.intOrNull

private fun JsonObject.bool(key: String): Boolean? =
    (this[key] as? JsonPrimitive)?.booleanOrNull
