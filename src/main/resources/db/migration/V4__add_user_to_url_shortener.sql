ALTER TABLE url_shortener 
ADD COLUMN user_id INTEGER REFERENCES users(id);