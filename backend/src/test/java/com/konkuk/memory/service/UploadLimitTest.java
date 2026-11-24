package com.konkuk.memory.service;

import com.konkuk.memory.exception.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UploadLimitTest {
    private MockMultipartFile jpeg(long size) {
        return new MockMultipartFile("files", "photo.jpg", "image/jpeg", new byte[]{(byte)255,(byte)216,(byte)255}) {
            @Override public long getSize() { return size; }
        };
    }
    @Test void acceptsFormerlyRejectedPhotoAndExactBoundary() {
        var validator=new ImageValidationService();
        assertThatCode(()->validator.validate(jpeg(11L*1024*1024))).doesNotThrowAnyException();
        assertThatCode(()->validator.validate(jpeg(30L*1024*1024))).doesNotThrowAnyException();
    }
    @Test void rejectsOneByteAboveBoundaryAs413() {
        assertThatThrownBy(()->new ImageValidationService().validate(jpeg(30L*1024*1024+1)))
            .isInstanceOfSatisfying(ApiException.class,e->{
                assertThat(e.getStatus().value()).isEqualTo(413);
                assertThat(e.getMessage()).contains("30MB");
            });
    }
    @Test void configuredParserAndValidatorUseSameLimit() {
        var validator=new ImageValidationService();
        org.springframework.test.util.ReflectionTestUtils.setField(validator,"maxFileSize",org.springframework.util.unit.DataSize.ofMegabytes(20));
        assertThatCode(()->validator.validate(jpeg(20L*1024*1024))).doesNotThrowAnyException();
        assertThatThrownBy(()->validator.validate(jpeg(20L*1024*1024+1))).hasMessageContaining("20MB");
    }
    @RestController static class ParserFailure {
        @PostMapping("/oversize") void fail() { throw new MaxUploadSizeExceededException(100L*1024*1024); }
    }
    @Test void parserFailureReturnsActionableJsonInsteadOfGeneric500() throws Exception {
        var mvc=MockMvcBuilders.standaloneSetup(new ParserFailure()).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/oversize")).andExpect(status().isPayloadTooLarge())
            .andExpect(jsonPath("code").value("UPLOAD_TOO_LARGE"))
            .andExpect(jsonPath("message").value(org.hamcrest.Matchers.containsString("사진 수를 줄여")));
    }
}
