package org.example.bookvault;
import java.io.IOException;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/books")
@CrossOrigin// tighten this to your BookVault frontend origin in production
public class BookRagController {

    private final BookRagService service;

    public BookRagController(BookRagService service) {
        this.service = service;
    }

    /** POST /api/books/upload  (multipart field name: "file") -> { bookId, summary } */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "The file is empty."));
        }
        String bookId = service.ingest(file);
        String summary = service.summarize(bookId);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("bookId", bookId, "summary", summary));
    }

    /** GET /api/books/{bookId}/summary */
    @GetMapping("/{bookId}/summary")
    public Map<String, String> summary(@PathVariable String bookId) {
        return Map.of("bookId", bookId, "summary", service.summarize(bookId));
    }

    /** POST /api/books/{bookId}/ask   body: { "question": "..." } */
    @PostMapping("/{bookId}/ask")
    public Map<String, String> ask(@PathVariable String bookId, @RequestBody Map<String, String> body) {
        String question = body.getOrDefault("question", "").trim();
        if (question.isEmpty()) {
            throw new IllegalArgumentException("The question must not be empty.");
        }
        return Map.of("bookId", bookId, "answer", service.ask(bookId, question));
    }

    /** DELETE /api/books/{bookId} removes the book's vectors from Pinecone. */
    @DeleteMapping("/{bookId}")
    public ResponseEntity<Void> delete(@PathVariable String bookId) {
        service.delete(bookId);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}