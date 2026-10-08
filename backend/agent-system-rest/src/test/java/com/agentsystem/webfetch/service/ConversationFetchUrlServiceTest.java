package com.agentsystem.webfetch.service;

import com.agentsystem.webfetch.entity.ConversationFetchUrl;
import com.agentsystem.webfetch.repository.ConversationFetchUrlRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationFetchUrlServiceTest {

    @Mock ConversationFetchUrlRepository repository;
    @InjectMocks ConversationFetchUrlService service;

    @Test
    void loadHashes_noConversation_skipsRepository() {
        assertThat(service.loadHashes(null)).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void loadHashes_repositoryFails_returnsEmpty() {
        when(repository.findUrlHashesByConversationId("conv-1")).thenThrow(new RuntimeException("db down"));

        assertThat(service.loadHashes("conv-1")).isEmpty();
    }

    @Test
    void save_storesHashesOfNewUrlsOnly() {
        String known = UrlNormalizer.hash("https://known.example/");

        service.save("conv-1", Set.of(known), List.of("https://known.example/", "https://new.example/a"));

        ArgumentCaptor<ConversationFetchUrl> captor = ArgumentCaptor.forClass(ConversationFetchUrl.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getConversationId()).isEqualTo("conv-1");
        assertThat(captor.getValue().getUrlHash()).isEqualTo(UrlNormalizer.hash("https://new.example/a"));
    }

    @Test
    void save_duplicateInsertFailure_continuesWithRemainingUrls() {
        when(repository.save(any())).thenThrow(new RuntimeException("duplicate key")).thenReturn(null);

        service.save("conv-1", Set.of(), List.of("https://a.example/", "https://b.example/"));

        verify(repository, times(2)).save(any());
    }

    @Test
    void save_noConversation_skipsRepository() {
        service.save(null, Set.of(), List.of("https://a.example/"));
        verifyNoInteractions(repository);
    }
}
