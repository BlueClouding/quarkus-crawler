package org.acme.scheduler;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.acme.entity.Movie;
import org.acme.enums.MovieStatus;
import org.acme.service.MovieDetailCrawlerService;
import org.jboss.logging.Logger;

@ApplicationScoped
public class MovieDetailCrawlerWorker {

    private static final Logger logger = Logger.getLogger(MovieDetailCrawlerWorker.class);

    @Inject
    MovieDetailCrawlerService movieDetailCrawlerService;

    private volatile boolean running = true;

    @PostConstruct
    void onStart() {
        Thread workerThread = new Thread(this::runWorker, "MovieDetailCrawlerWorker");
        workerThread.setDaemon(false);
        workerThread.start();
    }

    private void runWorker() {
        logger.info("Movie detail crawler worker started.");

        while (running) {
            try {
                boolean processed = processOneMovie();
                if (!processed) {
                    // 没有数据可处理，休眠一段时间再尝试
                    Thread.sleep(5000); // 5秒
                }
            } catch (Exception e) {
                logger.error("Error in movie detail crawler worker: " + e.getMessage(), e);
                try {
                    Thread.sleep(3000); // 出错后稍作等待
                } catch (InterruptedException ignored) {
                }
            }
        }

        logger.info("Movie detail crawler worker stopped.");
    }

    @Transactional
    public boolean processOneMovie() {
        Movie movie = Movie.find("status = ?1 order by id", MovieStatus.NEW.getValue())
                           .firstResult();

        if (movie == null) {
            return false;
        }

        try {
            logger.infof("Processing movie: %s", movie.getCode());

            movie = movieDetailCrawlerService.processMovie(movie);

            // 推荐在 service 中修改状态，此处仅示例
            movie.setStatus(MovieStatus.PROCESSING.getValue());
            movie.persist();

            logger.infof("Successfully processed movie: %s", movie.getCode());
        } catch (Exception e) {
            logger.errorf("Failed to process movie %s: %s", movie.getCode(), e.getMessage());
            movie.setStatus(MovieStatus.FAILED.getValue());
            movie.persist();
        }

        return true;
    }
}
