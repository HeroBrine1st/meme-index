package ru.herobrine1st.memeindex.server.repository

import io.github.smyrgeorge.sqlx4k.CrudRepository
import io.github.smyrgeorge.sqlx4k.QueryExecutor
import io.github.smyrgeorge.sqlx4k.annotation.Id
import io.github.smyrgeorge.sqlx4k.annotation.Query
import io.github.smyrgeorge.sqlx4k.annotation.Repository
import io.github.smyrgeorge.sqlx4k.annotation.Table
import ru.herobrine1st.memeindex.shared.model.Vector

@Table("file")
data class SearchHit(
    @Id
    val id: Int,
    val path: String,
    val text: String?,
    val textSimilarity: Double?,
    val textVectorSimilarity: Double?,
    val imageVectorSimilarity: Double?,
    val totalCount: Int,
)

@Repository
interface SearchRepository : CrudRepository<SearchHit> {
    @Query(
        "WITH image_vector_matches AS (" +
                "    SELECT file_id, 1 - (image_embedding <=> :queryVector::vector) AS score " +
                "    FROM embedding " +
                "    WHERE model_name = :model AND :vectorImage = TRUE " +
                "    ORDER BY image_embedding <=> :queryVector::vector " +
                "    LIMIT 100" +
                "), text_vector_matches AS (" +
                "    SELECT file_id, 1 - (text_embedding <=> :queryVector::vector) AS score" +
                "    FROM embedding" +
                "    WHERE model_name = :model AND text_embedding IS NOT NULL AND :vectorText = TRUE" +
                "    ORDER BY text_embedding <=> :queryVector::vector" +
                "    LIMIT 100" +
                "), fts_matches AS (" +
                "    SELECT file_id, ts_rank_cd(text_search_vector, websearch_to_tsquery('simple', :q), 32) AS score" +
                "    FROM embedding" +
                "    WHERE model_name = :model AND text_search_vector @@ websearch_to_tsquery('simple', :q) AND :fullText = TRUE" +
                "    ORDER BY score DESC" +
                "    LIMIT 100" +
                ")" +
                "SELECT f.id, f.path, e.text, fts.score AS text_similarity, tv.score AS text_vector_similarity, iv.score AS image_vector_similarity, COUNT(*) OVER() AS total_count " +
                "FROM file f JOIN embedding e ON e.file_id = f.id " +
                "LEFT JOIN fts_matches fts ON e.file_id = fts.file_id " +
                "LEFT JOIN text_vector_matches tv ON e.file_id = tv.file_id " +
                "LEFT JOIN image_vector_matches iv ON e.file_id = iv.file_id " +
                "WHERE e.model_name = :model AND (fts.file_id IS NOT NULL OR tv.file_id IS NOT NULL OR iv.file_id IS NOT NULL) " +
                "ORDER BY COALESCE(fts.score, 0) DESC, COALESCE(tv.score, 0) DESC, COALESCE(iv.score, 0) DESC",
        checkSchema = false,
    )
    suspend fun findAllBySearchCriteria(
        context: QueryExecutor,
        queryVector: Vector,
        q: String,
        vectorImage: Boolean,
        vectorText: Boolean,
        fullText: Boolean,
        model: String,
    ): Result<List<SearchHit>>
}
