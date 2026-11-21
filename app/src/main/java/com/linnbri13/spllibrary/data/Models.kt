package com.linnbri13.spllibrary.data

enum class FormatFilter(val label: String) {
    PHYSICAL_BOOKS("Physical Books"),
    ALL_PHYSICAL("All Physical"),
    ALL_FORMATS("All Formats")
}

enum class NewlyAcquiredFilter(val label: String, val apiValue: String?) {
    ANY("Any Time", null),
    PAST_30_DAYS("New (30d)", "PAST_30_DAYS"),
    PAST_90_DAYS("New (90d)", "PAST_90_DAYS"),
    PAST_180_DAYS("New (6m)", "PAST_180_DAYS")
}

enum class SortOption(val label: String, val apiValue: String) {
    RELEVANCY("Relevance", "relevancy"),
    NEWLY_ACQUIRED("Newly Acquired", "newly_acquired"),
    PUBLISHED_DATE("Pub Date", "published_date")
}

data class Branch(
    val code: String,
    val name: String
)

data class BookResult(
    val metadataId: String,
    val title: String,
    val subtitle: String?,
    val authors: List<String>,
    val format: String,
    val formatLabel: String,
    val publicationDate: String?,
    val callNumber: String?,
    val description: String?,
    val jacketUrl: String?,
    val bibType: String?,
    val status: String?,
    val availableCopies: Int,
    val totalCopies: Int,
    val heldCopies: Int,
    val onOrderCopies: Int,
    val holdable: Boolean,
    val catalogUrl: String
) {
    val authorLine: String
        get() = authors.joinToString("; ").ifBlank { "Unknown author" }

    val isPhysical: Boolean
        get() = bibType.equals("PHYSICAL", ignoreCase = true)
}

data class SearchPage(
    val page: Int,
    val totalPages: Int,
    val totalCount: Int,
    val items: List<BookResult>
)

data class CopyAvailability(
    val branchName: String,
    val callNumber: String?,
    val status: String,
    val collection: String?
)

data class HoldItem(
    val holdId: String,
    val metadataId: String,
    val title: String,
    val author: String?,
    val format: String?,
    val formatLabel: String,
    val materialType: String?,
    val status: String,
    val position: Int?,
    val pickupBranchName: String?,
    val pickupBranchCode: String?,
    val placedDate: String?,
    val expiryDate: String?,
    val jacketUrl: String?,
    val catalogUrl: String
) {
    val isReady: Boolean
        get() = status.equals("READY_FOR_PICKUP", ignoreCase = true)

    val isInTransit: Boolean
        get() = status.equals("IN_TRANSIT", ignoreCase = true)

    val isPhysical: Boolean
        get() = materialType.equals("PHYSICAL", ignoreCase = true)
}

data class HoldsResult(
    val holds: List<HoldItem>,
    val physicalUsed: Int? = null,
    val physicalLimit: Int? = null
)

data class CheckoutItem(
    val checkoutId: String,
    val metadataId: String?,
    val title: String,
    val author: String?,
    val format: String?,
    val formatLabel: String,
    val materialType: String?,
    val dueDate: String?,
    val status: String?,
    val timesRenewed: Int,
    val canRenew: Boolean,
    val jacketUrl: String?,
    val catalogUrl: String?
)

data class PlaceHoldOutcome(
    val holdId: String?,
    val position: Int?,
    val status: String?,
    val pickupBranchName: String?
)

fun formatCodeToLabel(code: String?): String = when (code?.uppercase()) {
    "BK" -> "Book"
    "EBOOK" -> "eBook"
    "EAUDIOBOOK" -> "eAudiobook"
    "GRAPHIC_NOVEL" -> "Graphic Novel"
    "GRAPHIC_NOVEL_DOWNLOAD" -> "eGraphic Novel"
    "LARGEPRINT", "LPRINT" -> "Large Print"
    "BOOK_CD", "AB" -> "Audiobook CD"
    "DVD" -> "DVD"
    "BLURAY" -> "Blu-ray"
    "MUSIC_CD" -> "Music CD"
    "MAGAZINE", "MAG" -> "Magazine"
    "VIDEO_ONLINE" -> "Streaming Video"
    null, "" -> "Item"
    else -> code.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
}
