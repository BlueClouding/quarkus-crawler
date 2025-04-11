package org.acme.service;

import org.acme.entity.Movie;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped // 或者其他合适的 scope
@Transactional
public class MovieService {

    public void saveOrUpdateMovie(Movie movie) {
        if (movie.id != null) {
            Movie existingMovie = Movie.findById(movie.id);
            if (existingMovie != null) {
                existingMovie.setCode(movie.getCode());
                existingMovie.setCoverImageUrl(movie.getCoverImageUrl());
                existingMovie.setDescription(movie.getDescription());
                existingMovie.setDirector(movie.getDirector());
                existingMovie.setDownloadUrlsInfo(movie.getDownloadUrlsInfo());
                existingMovie.setDuration(movie.getDuration());
                existingMovie.setLikes(movie.getLikes());
                existingMovie.setLink(movie.getLink());
                existingMovie.setMagnets(movie.getMagnets());
                existingMovie.setMaker(movie.getMaker());
                existingMovie.setOriginalId(movie.getOriginalId());
                existingMovie.setPreviewVideoUrl(movie.getPreviewVideoUrl());
                existingMovie.setReleaseDate(movie.getReleaseDate());
                existingMovie.setSeries(movie.getSeries());
                existingMovie.setStatus(movie.getStatus());
                existingMovie.setThumbnail(movie.getThumbnail());
                existingMovie.setTitle(movie.getTitle());
                existingMovie.setUpdatedAt(movie.getUpdatedAt());
                existingMovie.setWatchUrlsInfo(movie.getWatchUrlsInfo());
            } else {
                Movie.persist(movie); 
            }
        } else {
            Movie.persist(movie); 
        }
    }
}
