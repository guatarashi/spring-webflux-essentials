package br.com.atarashi.webflux.integration;

import br.com.atarashi.webflux.domain.Anime;
import br.com.atarashi.webflux.repository.AnimeRepository;
import br.com.atarashi.webflux.util.AnimeCreator;
import br.com.atarashi.webflux.util.WebTestClientUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.blockhound.BlockHound;
import reactor.blockhound.BlockingOperationError;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureWebTestClient
public class AnimeControllerIT {

    private final static String REGULAR_USER = "carlos";
    private final static String ADMIN_USER = "gustavo";

    @MockBean
    private AnimeRepository animeRepositoryMock;

    @Autowired
    private WebTestClient client;

    private final Anime anime = AnimeCreator.createValidAnime();

    @BeforeEach
    public void setUp() {
        when(animeRepositoryMock.findAll())
                .thenReturn(Flux.just(anime));

        when(animeRepositoryMock.findById(anyInt()))
                .thenReturn(Mono.just(anime));

        when(animeRepositoryMock.save(AnimeCreator.createAnimeToBeSaved()))
                .thenReturn(Mono.just(anime));

        when(animeRepositoryMock
                .saveAll(List.of(AnimeCreator.createAnimeToBeSaved(), AnimeCreator.createAnimeToBeSaved())))
                .thenReturn(Flux.just(anime, anime));

        when(animeRepositoryMock.delete(any(Anime.class)))
                .thenReturn(Mono.empty());

        when(animeRepositoryMock.save(AnimeCreator.createValidAnime()))
                .thenReturn(Mono.empty());
    }

    @BeforeAll
    public static void blockHoundSetup() {
        BlockHound.install(builder -> builder.allowBlockingCallsInside("java.util.UUID", "randomUUID"));
    }

    @Test
    public void blockHoundWorks() {
        FutureTask<?> task = new FutureTask<>(() -> {
            Thread.sleep(0);
            return "";
        });

        Schedulers.parallel().schedule(task);

        try {
            task.get(10, TimeUnit.SECONDS);
            Assertions.fail("should fail");
        } catch (Exception e) {
            assertTrue(e.getCause() instanceof BlockingOperationError);
        }
    }

    @Test
    @DisplayName("listAll returns unauthorized when user is not authenticated")
    public void listAll_ReturnUnauthorized_WhenUserIsNotAuthenticated() {
        client
            .get()
            .uri("/animes")
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("listAll returns forbidden when user is successfully authenticated and does have role ADMIN")
    @WithUserDetails(REGULAR_USER)
    public void listAll_ReturnForbidden_WhenUserDoesNotHaveRoleAdmin() {
        client
            .get()
            .uri("/animes")
            .exchange()
            .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("listAll returns a flux of anime when user successfully authenticated and has role ADMIN")
    @WithUserDetails(ADMIN_USER)
    public void listAll_ReturnFluxOfAnime_WhenSuccessful() {
        client
            .get()
            .uri("/animes")
            .exchange()
            .expectStatus().is2xxSuccessful()
            .expectBody()
            .jsonPath("$.[0].id").isEqualTo(anime.getId())
            .jsonPath("$.[0].name").isEqualTo(anime.getName());
    }

    @Test
    @DisplayName("listAll returns a flux of anime when user successfully authenticated and has role ADMIN")
    @WithUserDetails(ADMIN_USER)
    public void listAll_Flavor2_ReturnFluxOfAnime_WhenSuccessful() {
        client
            .get()
            .uri("/animes")
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(Anime.class)
            .hasSize(1)
            .contains(anime);
    }

    @Test
    @DisplayName("findById returns Mono with anime when it exists and user successfully authenticated and has role USER")
    @WithUserDetails(REGULAR_USER)
    public void findById_ReturnMonoAnime_WhenSuccessful() {
        client
            .get()
            .uri("/animes/{id}", 1)
            .exchange()
            .expectStatus().isOk()
            .expectBody(Anime.class)
            .isEqualTo(anime);
}

    @Test
    @DisplayName("findById returns Mono error when anime does not exist and user successfully authenticated and has role USER")
    @WithUserDetails(REGULAR_USER)
    public void findById_ReturnMonoError_WhenEmptyMonoIsReturned() {
        when(animeRepositoryMock.findById(anyInt()))
                .thenReturn(Mono.empty());

        client
            .get()
            .uri("/animes/{id}", 1)
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.status").isEqualTo(404)
            .jsonPath("$.developerMessage").isEqualTo("A ResponseStatusException Happened");
    }

    @Test
    @DisplayName("save creates an anime when successful and user successfully authenticated and has role ADMIN")
    @WithUserDetails(ADMIN_USER)
    public void save_CreatesAnime_WhenSuccessful() {
        Anime animeToBeSaved = AnimeCreator.createAnimeToBeSaved();

        client
            .post()
            .uri("/animes")
            .contentType(MediaType.APPLICATION_JSON)
            .body(BodyInserters.fromValue(animeToBeSaved))
            .exchange()
            .expectStatus().isCreated()
            .expectBody(Anime.class)
            .isEqualTo(anime);
    }

    @Test
    @DisplayName("saveBatch creates a list of anime when successful and user successfully authenticated and has role ADMIN")
    @WithUserDetails(ADMIN_USER)
    public void saveBatch_CreatesListOfAnime_WhenSuccessful() {
        Anime animeToBeSaved = AnimeCreator.createAnimeToBeSaved();

        client
            .post()
            .uri("/animes/batch")
            .contentType(MediaType.APPLICATION_JSON)
            .body(BodyInserters.fromValue(List.of(animeToBeSaved, animeToBeSaved)))
            .exchange()
            .expectStatus().isCreated()
            .expectBodyList(Anime.class)
            .hasSize(2)
            .contains(anime);
    }

    @Test
    @DisplayName("saveBatch returns Mono error when one of the objects in the list contains null or empty name and user successfully authenticated and has role ADMIN")
    @WithUserDetails(ADMIN_USER)
    public void saveBatch_ReturnsError_WhenContainsInvalidName() {
        Anime animeToBeSaved = AnimeCreator.createAnimeToBeSaved();

        when(animeRepositoryMock
            .saveAll(anyIterable()))
            .thenReturn(Flux.just(anime, anime.withName("")));

        client
            .post()
            .uri("/animes/batch")
            .contentType(MediaType.APPLICATION_JSON)
            .body(BodyInserters.fromValue(List.of(animeToBeSaved, animeToBeSaved)))
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.status").isEqualTo(400);
    }

    @Test
    @DisplayName("save returns mono error with bad request when name is empty and user successfully authenticated and has role ADMIN")
    @WithUserDetails(ADMIN_USER)
    public void save_ReturnsError_WhenNameIsEmpty() {
        Anime animeToBeSaved = AnimeCreator.createAnimeToBeSaved().withName("");

        client
            .post()
            .uri("/animes")
            .contentType(MediaType.APPLICATION_JSON)
            .body(BodyInserters.fromValue(animeToBeSaved))
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.status").isEqualTo(400);
    }

    @Test
    @DisplayName("delete removes the anime successful and user successfully authenticated and has role ADMIN")
    @WithUserDetails(ADMIN_USER)
    public void delete_RemovesAnime_WhenSuccessful() {
        client
            .delete()
            .uri("/animes/{id}", 1)
            .exchange()
            .expectStatus().isNoContent();
    }

    @Test
    @DisplayName("delete returns Mono error when anime does not exist and user successfully authenticated and has role ADMIN")
    @WithUserDetails(ADMIN_USER)
    public void delete_ReturnMonoError_WhenEmptyMonoIsReturned() {
        when(animeRepositoryMock.findById(anyInt()))
                .thenReturn(Mono.empty());

        client
            .delete()
            .uri("/animes/{id}", 1)
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.status").isEqualTo(404)
            .jsonPath("$.developerMessage").isEqualTo("A ResponseStatusException Happened");
    }

    @Test
    @DisplayName("update save updated anime and returns empty mono when successful and user successfully authenticated and has role ADMIN")
    @WithUserDetails(ADMIN_USER)
    public void update_SaveUpdateAnime_WhenSuccessful() {
        client
            .put()
            .uri("/animes/{id}", 1)
            .contentType(MediaType.APPLICATION_JSON)
            .body(BodyInserters.fromValue(anime))
            .exchange()
            .expectStatus().isNoContent();
    }

    @Test
    @DisplayName("update returns MOno error when anime does exist and user successfully authenticated and has role ADMIN")
    @WithUserDetails(ADMIN_USER)
    public void update_ReturnMonoError_WhenEmptyMonoIsReturned() {
        when(animeRepositoryMock.findById(anyInt()))
            .thenReturn(Mono.empty());

        client
            .put()
            .uri("/animes/{id}", 1)
            .contentType(MediaType.APPLICATION_JSON)
            .body(BodyInserters.fromValue(anime))
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.status").isEqualTo(404)
            .jsonPath("$.developerMessage").isEqualTo("A ResponseStatusException Happened");
    }
}
