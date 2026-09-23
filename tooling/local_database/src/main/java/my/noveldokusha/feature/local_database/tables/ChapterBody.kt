package my.noveldokusha.feature.local_database.tables

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class ChapterBody(
    @PrimaryKey val url: String,
    val body: String,
    // === Cloud-sync bookkeeping (added in DB v35) ===
    // updatedAt — epoch-millis of the last LOCAL write to this row.
    // For chapter bodies, the body itself is content-addressable (same URL
    // = same content) so updatedAt is mostly cosmetic; it's still useful
    // to detect "phone A redownloaded this chapter after phone B did"
    // edge cases during merge.
    val updatedAt: Long = 0L,
    // syncStatus — "SYNCED" or "NOT_SYNCED".
    val syncStatus: String = SyncStatus.NOT_SYNCED,
)
