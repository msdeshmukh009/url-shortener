CREATE INDEX idx_url_shortener_user_id_created_at 
ON url_shortener (user_id, created_at DESC);