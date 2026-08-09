package com.urlshortener.url_shortener.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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

@Service
public class ThumbnailService {

    private static final Logger log = LoggerFactory.getLogger(ThumbnailService.class);
    private static final int THUMB_SIZE = 300;

    private final UserRepository userRepository;

    @Value("${app.thumbnail.dir:uploads/thumbnails}")
    private String thumbnailDir;

    public ThumbnailService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Generate (or regenerate) the thumbnail for one user.
     * @return the stored thumbnail path, or null if there was nothing to do.
     */
    public String generateThumbnail(Integer userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.warn("thumbnail: user id={} not found", userId);
            return null;
        }
        if (user.getImageFile() == null) {
            log.warn("thumbnail: user id={} has no image to process", userId);
            return null;
        }

        try {
            // load in memory + resize to 300x300
            BufferedImage thumb = resize(user.getImageFile(), THUMB_SIZE, THUMB_SIZE);

            // save as a NEW image file on disk
            Path dir = Paths.get(thumbnailDir);
            Files.createDirectories(dir);
            Path file = dir.resolve("thumb_" + user.getId() + ".png");
            ImageIO.write(thumb, "png", file.toFile());

            // store the LINK in the DB
            String link = file.toString();
            user.setImageThumbnail(link);
            userRepository.save(user);

            log.info("thumbnail generated for user id={} -> {}", userId, link);
            return link;
        } catch (Exception e) {
            log.error("thumbnail generation failed for user id={}", userId, e);
            throw new RuntimeException("thumbnail generation failed for user " + userId, e);
        }
    }

    private BufferedImage resize(byte[] source, int width, int height) throws IOException {
        BufferedImage original = ImageIO.read(new ByteArrayInputStream(source));
        if (original == null) {
            throw new IOException("unreadable image (unsupported format? note: ImageIO "
                    + "does not support WebP without a plugin)");
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