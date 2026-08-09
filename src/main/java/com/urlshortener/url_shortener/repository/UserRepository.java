package com.urlshortener.url_shortener.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.urlshortener.url_shortener.entity.User;

public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByApiKey(String apiKey);

    long countByImageFileIsNull();

    @Query("SELECT u FROM User u WHERE u.imageFile IS NOT NULL AND u.imageThumbnail IS NULL")
    List<User> findUsersNeedingThumbnail(Pageable pageable);

    @Query("SELECT u FROM User u JOIN FETCH u.tier WHERE u.apiKey = :apiKey")
    Optional<User> findByApiKeyWithTier(@Param("apiKey") String apiKey);

    // count of rows the cron still needs to process (used by seeder + job)
    @Query("SELECT COUNT(u) FROM User u WHERE u.imageFile IS NOT NULL AND u.imageThumbnail IS NULL")
    long countUsersNeedingThumbnail();

    // bounded set-based seed: give N users an image, leaving thumbnail NULL.
    // Postgres supports LIMIT inside the subquery; the DB does it all in one pass.
    @Modifying
    @Query(value = """
            UPDATE users SET image_file = :png
            WHERE id IN (
                SELECT id FROM users WHERE image_file IS NULL ORDER BY id LIMIT :count
            )
            """, nativeQuery = true)
    int seedImagesForSubset(@Param("png") byte[] png, @Param("count") int count);
}
