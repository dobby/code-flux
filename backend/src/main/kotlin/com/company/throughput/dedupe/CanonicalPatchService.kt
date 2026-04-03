package com.company.throughput.dedupe

import org.springframework.stereotype.Service
import java.time.OffsetDateTime

data class CanonicalCommitCandidate(
    val commitSha: String,
    val authoredAt: OffsetDateTime,
    val committedAt: OffsetDateTime,
)

data class CanonicalCommitDecision(
    val canonicalCommitSha: String,
    val isCanonical: Boolean,
    val isDuplicatePatch: Boolean,
)

@Service
class CanonicalPatchService {
    fun chooseCanonical(candidates: List<CanonicalCommitCandidate>): CanonicalCommitCandidate {
        require(candidates.isNotEmpty()) { "At least one candidate is required for canonicalization" }
        return candidates.minWithOrNull(
            compareBy<CanonicalCommitCandidate>({ it.authoredAt.toInstant() }, { it.committedAt.toInstant() }, { it.commitSha }),
        ) ?: error("Unable to canonicalize patch candidates")
    }
}
