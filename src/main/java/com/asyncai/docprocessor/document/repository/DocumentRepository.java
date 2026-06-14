package com.asyncai.docprocessor.document.repository;

import com.asyncai.docprocessor.document.domain.Document;
import com.asyncai.docprocessor.document.domain.DocumentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * WHY extend JpaRepository<Document, UUID>?
 * Spring Data generates the implementation at startup. You get
 * findById, save, delete, findAll(Pageable) etc. for free.
 *
 * IMPORTANT: Always use Optional<T> for single-entity lookups.
 * Never return null — it leads to NullPointerExceptions.
 * Null safety is enforced at compile time with Optional.
 *
 * WHY custom queries for some operations?
 * Spring Data method names work for simple cases. For anything
 * involving joins, aggregates, or native DB features (like pgvector),
 * use @Query with JPQL or nativeQuery=true.
 */
@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    /**
     * Find a document by ID scoped to a specific user.
     * WHY include userId? Security. Never allow user A to fetch user B's document
     * just because they know the UUID. Always scope queries to the authenticated user.
     */
    Optional<Document> findByIdAndUserId(UUID id, String userId);

    /**
     * Paginated listing for a user's documents.
     * WHY Pageable? Never return unbounded lists. A user with 10,000 documents
     * would kill your DB if you SELECT * FROM documents WHERE user_id = ?
     */
    Page<Document> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    /**
     * Find documents by status — used by a recovery job to retry stuck documents.
     */
    List<Document> findByStatus(DocumentStatus status);

    /**
     * Bulk status update — more efficient than loading entities one by one.
     * WHY @Modifying? Required for any @Query that modifies data (UPDATE/DELETE).
     * WHY @Transactional? UPDATE queries must run in a transaction. Without it,
     * Spring throws a TransactionRequiredException.
     *
     * COMMON MISTAKE: Forgetting @Modifying — you'll get:
     * "org.springframework.dao.InvalidDataAccessApiUsageException:
     *  Executing an update/delete query requires a transaction"
     */
    @Modifying
    @Query("UPDATE Document d SET d.status = :status WHERE d.id = :id")
    int updateStatus(@Param("id") UUID id, @Param("status") DocumentStatus status);

    /**
     * Count documents by status for dashboard metrics.
     */
    @Query("SELECT d.status, COUNT(d) FROM Document d WHERE d.userId = :userId GROUP BY d.status")
    List<Object[]> countByStatusForUser(@Param("userId") String userId);
}
