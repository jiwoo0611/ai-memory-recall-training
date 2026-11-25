package com.konkuk.memory.service;

import com.konkuk.memory.dto.TrainingSessionSummaryResponse;
import com.konkuk.memory.entity.*;
import com.konkuk.memory.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(classes=DelayedRecallIntegrationTest.TestApp.class)
@ActiveProfiles("test")
@Transactional
@org.springframework.test.context.TestExecutionListeners(listeners={
    org.springframework.test.context.web.ServletTestExecutionListener.class,
    org.springframework.test.context.support.DependencyInjectionTestExecutionListener.class,
    org.springframework.test.context.transaction.TransactionalTestExecutionListener.class
},mergeMode=org.springframework.test.context.TestExecutionListeners.MergeMode.REPLACE_DEFAULTS)
class TrainingSessionNamingIntegrationTest {
    @Autowired UserRepository users;
    @Autowired AlbumRepository albums;
    @Autowired PhotoRepository photos;
    @Autowired TrainingSessionRepository sessions;
    @Autowired PlatformTransactionManager transactions;

    User owner(){return users.saveAndFlush(new User(UUID.randomUUID()+"@example.com","fixture","이름 확인"));}
    Photo photo(User owner,Album album){return photos.saveAndFlush(new Photo(owner,album,UUID.randomUUID().toString(),"photo.jpg","image/jpeg",1));}

    @Test void numbersAllPhotosTogetherWithinEachAlbumAndStartsOtherAlbumsAtOne(){
        var owner=owner();
        var busan=albums.saveAndFlush(new Album(owner,"부산"));
        var seoul=albums.saveAndFlush(new Album(owner,"서울"));
        var first=sessions.saveAndFlush(new TrainingSession(photo(owner,busan)));
        var other=sessions.saveAndFlush(new TrainingSession(photo(owner,seoul)));
        var second=sessions.saveAndFlush(new TrainingSession(photo(owner,busan)));
        assertThat(first.getDisplayName()).isEqualTo("부산 1");
        assertThat(second.getDisplayName()).isEqualTo("부산 2");
        assertThat(other.getDisplayName()).isEqualTo("서울 1");
        assertThat(TrainingSessionSummaryResponse.from(second).displayName()).isEqualTo("부산 2");
        assertThat(TrainingSessionSummaryResponse.from(second).photo().albumTitle()).isEqualTo("부산");
    }
    @Test void deletionDoesNotRenumberOrReuseAnEarlierNumber(){
        var owner=owner();var album=albums.saveAndFlush(new Album(owner,"부산"));var photo=photo(owner,album);
        var first=sessions.saveAndFlush(new TrainingSession(photo));
        var second=sessions.saveAndFlush(new TrainingSession(photo));
        sessions.delete(first);sessions.flush();
        var third=sessions.saveAndFlush(new TrainingSession(photo));
        assertThat(second.getDisplayName()).isEqualTo("부산 2");
        assertThat(third.getDisplayName()).isEqualTo("부산 3");
    }
    @Test void albumRenameUpdatesTheTitleWhileKeepingTheAssignedNumber(){
        var owner=owner();var album=albums.saveAndFlush(new Album(owner,"부산"));
        var session=sessions.saveAndFlush(new TrainingSession(photo(owner,album)));
        album.changeTitle("부산 여행");albums.saveAndFlush(album);
        assertThat(session.getDisplayName()).isEqualTo("부산 여행 1");
    }
    @Test
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void concurrentSessionCreationGetsDistinctConsecutiveNumbers() throws Exception {
        var owner=owner();var album=albums.saveAndFlush(new Album(owner,"동시 생성"));var photo=photo(owner,album);
        var executor=Executors.newFixedThreadPool(4);
        try {
            List<Callable<Long>> tasks=new ArrayList<>();
            for(int i=0;i<8;i++) tasks.add(()->new TransactionTemplate(transactions).execute(status->{
                var reference=photos.findById(photo.getId()).orElseThrow();
                return sessions.saveAndFlush(new TrainingSession(reference)).getAlbumSequence();
            }));
            List<Long> numbers=new ArrayList<>();
            for(var result:executor.invokeAll(tasks))numbers.add(result.get(20,TimeUnit.SECONDS));
            assertThat(numbers).containsExactlyInAnyOrder(1L,2L,3L,4L,5L,6L,7L,8L);
        } finally {
            executor.shutdownNow();
            new TransactionTemplate(transactions).executeWithoutResult(status->{
                sessions.deleteAll(sessions.findAllByPhoto_Album_IdAndPhoto_User_Id(album.getId(),owner.getId()));sessions.flush();
                photos.deleteById(photo.getId());photos.flush();albums.deleteById(album.getId());albums.flush();users.deleteById(owner.getId());
            });
        }
    }
}
