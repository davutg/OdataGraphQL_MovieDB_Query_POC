CREATE TABLE directors (id bigint PRIMARY KEY, name varchar(120) NOT NULL);
CREATE TABLE movies (
 id bigint PRIMARY KEY, title varchar(200) NOT NULL, release_year integer NOT NULL,
 rating double precision NOT NULL CHECK (rating BETWEEN 0 AND 10),
 genre varchar(40) NOT NULL, director_id bigint NOT NULL REFERENCES directors(id)
);
CREATE INDEX ix_movies_genre_id ON movies(genre, id);
CREATE INDEX ix_movies_year_id ON movies(release_year, id);
CREATE INDEX ix_movies_rating_id ON movies(rating DESC, id);
CREATE INDEX ix_movies_director_id ON movies(director_id, id);
INSERT INTO directors VALUES (1,'Christopher Nolan'),(2,'Frank Darabont'),(3,'Bong Joon-ho'),(4,'Hayao Miyazaki'),(5,'Denis Villeneuve');
-- Familiar titles; ratings below are demo values, not an IMDb dataset or feed.
INSERT INTO movies VALUES
 (1,'Inception',2010,8.8,'Sci-Fi',1),(2,'Interstellar',2014,8.7,'Sci-Fi',1),
 (3,'The Dark Knight',2008,9.0,'Action',1),(4,'The Shawshank Redemption',1994,9.3,'Drama',2),
 (5,'The Green Mile',1999,8.6,'Drama',2),(6,'Parasite',2019,8.5,'Drama',3),
 (7,'Spirited Away',2001,8.6,'Animation',4),(8,'Dune',2021,8.0,'Sci-Fi',5),
 (9,'Arrival',2016,7.9,'Sci-Fi',5),(10,'Oppenheimer',2023,8.3,'Drama',1);
-- Enough reproducible synthetic rows for meaningful planner experiments.
INSERT INTO movies(id,title,release_year,rating,genre,director_id)
SELECT n, 'Demo Movie ' || n, 1980 + (n % 45)::integer,
 (50 + n % 50)::double precision / 10,
 CASE WHEN n % 1000 = 0 THEN 'RareGenre' ELSE (ARRAY['Drama','Sci-Fi','Action','Animation','Comedy'])[(n%5)::integer+1] END,
 (n%5)+1 FROM generate_series(11,100010) n;
ANALYZE directors;
ANALYZE movies;
