package com.codeguard.security;

import com.codeguard.common.ResourceNotFoundException;
import com.codeguard.github.GithubInstallation;
import com.codeguard.repository.*;
import com.codeguard.user.UserAccount;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RepositoryAccessTest {
    private final RepositoryGrantStore grants = mock(RepositoryGrantStore.class);
    private final RepositoryAccess access = new RepositoryAccess(grants);

    @Test void doesNotRevealMissingOrInaccessibleRepositories() {
        when(grants.validGrant(any(), any(), any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> access.read(UUID.randomUUID(), UUID.randomUUID())).isInstanceOf(ResourceNotFoundException.class);
    }
    @Test void onlyWritersAndAdminsCanChangePolicy() {
        var user = new UserAccount(1, "dev", null);
        var repo = new CodeRepository(2, new GithubInstallation(3, "team"), "team/repo", true, "Java");
        var grant = new RepositoryGrant(user, repo, RepositoryGrant.Permission.READ, Instant.now().plusSeconds(300));
        when(grants.validGrant(any(), any(), any())).thenReturn(Optional.of(grant));
        assertThatThrownBy(() -> access.write(user.getId(), repo.getId())).isInstanceOf(AccessDeniedException.class);
        grant.refresh(RepositoryGrant.Permission.WRITE, Instant.now().plusSeconds(300));
        assertThat(access.write(user.getId(), repo.getId())).isSameAs(repo);
    }
}
