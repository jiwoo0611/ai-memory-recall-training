package com.konkuk.memory.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class NameBackfillTest {
    @Test void backfillsExistingTrainingsInAlbumOrderAndSeedsTheNextCounter(){
        var source=new DriverManagerDataSource("jdbc:h2:mem:names"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
        Flyway.configure().dataSource(source).target("13").load().migrate();
        var db=new JdbcTemplate(source);
        db.update("INSERT INTO users(id,email,password_hash,name,created_at,updated_at) VALUES (101,'names@example.com','fixture','이름',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        db.update("INSERT INTO albums(id,user_id,title,created_at,updated_at) VALUES (201,101,'부산',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),(202,101,'서울',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        db.update("INSERT INTO photos(id,user_id,album_id,storage_key,original_filename,content_type,size_bytes,created_at,updated_at) VALUES (301,101,201,'busan','busan.jpg','image/jpeg',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),(302,101,202,'seoul','seoul.jpg','image/jpeg',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        db.update("INSERT INTO training_sessions(id,photo_id,status,started_at,created_at,updated_at) VALUES (44,301,'IN_PROGRESS',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),(47,302,'IN_PROGRESS',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),(51,301,'IN_PROGRESS',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        Flyway.configure().dataSource(source).load().migrate();
        assertThat(db.queryForList("SELECT album_sequence FROM training_sessions ORDER BY id",Long.class)).containsExactly(1L,1L,2L);
        assertThat(db.queryForObject("SELECT next_training_number FROM albums WHERE id=201",Long.class)).isEqualTo(2L);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM training_sessions",Integer.class)).isEqualTo(3);
    }
}
