package com.nicsi.store.testutil;

import com.nicsi.store.common.idempotency.IdempotentPost;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/store/test")
public class IdempotencyTestController {
    
    @PostMapping("/idempotent-action")
    @IdempotentPost
    public ResponseEntity<String> testAction() {
        return ResponseEntity.ok("ok");
    }
}
