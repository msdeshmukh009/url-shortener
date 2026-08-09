package com.urlshortener.url_shortener.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.urlshortener.url_shortener.entity.User;
import com.urlshortener.url_shortener.repository.UserRepository;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Thumbnail cron for a LARGE table (millions of rows).
 *
 * The critical change vs the naive version: we NEVER load all pending users at
 * once. Loading millions of rows — each carrying full image bytes — would blow
 * up heap memory. Instead each run pulls a bounded BATCH (e.g. 100), processes
 * it, and returns. The next run picks up the next 100. Over many runs the whole
 * backlog drains, but memory stays flat and bounded.
 *
 * Because the query filters on `imageThumbnail IS NULL`, processed rows drop out
 * automatically, so "page 0, size 100" every time always returns the next
 * unprocessed batch — no offset bookkeeping needed.
 */
@Component
public class ThumbnailGenerationJob {

    private static final Logger log = LoggerFactory.getLogger(ThumbnailGenerationJob.class);
    private static final int THUMB_SIZE = 300;

    private final UserRepository userRepository;

    @Value("${app.thumbnail.dir:uploads/thumbnails}")
    private String thumbnailDir;

    // how many users to process per run — tune to your heap/CPU
    @Value("${app.thumbnail.batch-size:100}")
    private int batchSize;

    public ThumbnailGenerationJob(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Scheduled(fixedDelay = 300_000)
    public void generateMissingThumbnails() {
        Pageable batch = PageRequest.of(0, batchSize); // always the first N still-NULL rows
        List<User> pending = userRepository.findUsersNeedingThumbnail(batch);

        if (pending.isEmpty()) {
            log.debug("thumbnail job: nothing to do");
            return;
        }
        log.info("thumbnail job: processing batch of {} (of a possibly large backlog)", pending.size());

        int done = 0, failed = 0;
        for (User user : pending) {
            try {
                BufferedImage thumb = resize(user.getImageFile(), THUMB_SIZE, THUMB_SIZE);

                Path dir = Paths.get(thumbnailDir);
                Files.createDirectories(dir);
                Path file = dir.resolve("thumb_" + user.getId() + ".png");
                ImageIO.write(thumb, "png", file.toFile());

                user.setImageThumbnail(file.toString());
                // free the source bytes reference for GC as soon as we're done
                userRepository.save(user);
                done++;
            } catch (Exception e) {
                failed++;
                log.error("thumbnail failed for user id={}", user.getId(), e);
                // mark it so a permanently-bad image doesn't block the batch forever
                markFailed(user);
            }
        }
        log.info("thumbnail batch finished: {} done, {} failed", done, failed);
    }

    /**
     * If an image is corrupt/unreadable it will fail every run and keep coming
     * back in the query — an infinite retry loop that stalls the whole backlog.
     * Set a sentinel so it's excluded next time. (Adjust to your schema — a
     * separate status column is cleaner than overloading the path.)
     */
    private void markFailed(User user) {
        try {
            user.setImageThumbnail("FAILED");
            userRepository.save(user);
        } catch (Exception e) {
            log.error("could not mark user id={} as failed", user.getId(), e);
        }
    }

    private BufferedImage resize(byte[] source, int width, int height) throws IOException {
        BufferedImage original = ImageIO.read(new ByteArrayInputStream(source));
        if (original == null) {
            throw new IOException("unreadable image (unsupported format?)");
        }
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(original, 0, 0, width, height, null);
        g.dispose();
        return resized;
    }
}